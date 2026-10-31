package com.eric.toolbox.modules.pan.engine

import com.eric.toolbox.modules.pan.core.CloudType
import com.eric.toolbox.modules.pan.core.Dates
import com.eric.toolbox.modules.pan.core.Http
import com.eric.toolbox.modules.pan.model.EngineResult
import com.eric.toolbox.modules.pan.model.PanItem
import com.eric.toolbox.modules.pan.model.SearchOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.URLEncoder
import kotlin.random.Random

/**
 * TA搜（ataw）：SSR 搜索页 + 公开详情 REST，两段式。
 *
 * 流程：
 *   1. 分别用 b=quark / ali / sharepan 抓三次搜索页，用正则捞资源 id
 *   2. 并发（最多 2 个）调 /api/v1/public/resources/{id} 拿真实链接
 *
 * 关键坑：
 *  - 三个 biz 是分别搜的，任何一个失败都不能影响其它两个
 *  - 详情并发不能超过 2，且要加随机间隔，否则很快被风控
 *  - isDeleted=true 的直接丢
 */
class AtawEngine(
    private val base: String = DEFAULT_BASE,
) : PanEngine {

    override val name: String = "ataw"

    override suspend fun search(keyword: String, opts: SearchOptions): EngineResult {
        val perBiz = maxOf(3, minOf(opts.limitPerCloud, 12))
        val hints = LinkedHashMap<String, String>() // id -> biz
        val bizErrors = mutableListOf<String>()

        for (biz in BIZ) {
            try {
                val qs = "q=${URLEncoder.encode(keyword, "UTF-8")}&b=$biz"
                val html = Http.getText("$base/?$qs", opts.timeoutMs)
                var count = 0
                for (m in RESOURCE_RE.findAll(html)) {
                    val id = m.groupValues[1]
                    val b = m.groupValues[2].lowercase()
                    hints.putIfAbsent(id, b)
                    if (++count >= perBiz) break
                }
            } catch (e: Exception) {
                bizErrors.add("$biz: ${e.message ?: e.javaClass.simpleName}")
            }
        }

        if (hints.isEmpty()) {
            return EngineResult(
                name, emptyList(), 0,
                error = bizErrors.joinToString("; ").ifBlank { "搜索页未取到资源" },
            )
        }

        val targets = hints.entries.take(maxOf(1, minOf(opts.limitPerCloud, 15)))
        val sem = Semaphore(2)

        val items = coroutineScope {
            targets.map { (id, biz) ->
                async {
                    sem.withPermit {
                        delay(Random.nextLong(200L, 800L))
                        runCatching { detail(id, biz) }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll().flatten()
        }

        return EngineResult(name, items, hints.size)
    }

    private suspend fun detail(id: String, bizHint: String): List<PanItem> {
        val json = Http.getJson("$base/api/v1/public/resources/$id", 25_000)
        if (json.optBoolean("isDeleted", false)) return emptyList()

        val title = json.optString("title").trim()
        val desc = json.optString("description").trim()
        val short = if (desc.length > 80) desc.take(80) + "…" else desc
        val note = if (short.isEmpty()) title else "$title：$short"
        val dt = Dates.normalize(
            json.optString("submitTime").ifBlank { json.optString("updateTime") },
        )
        val biz = json.optString("botBiz").ifBlank { bizHint }.lowercase()

        val pairs = mutableListOf<Pair<String, String>>()
        json.optJSONArray("links")?.let { arr ->
            for (i in 0 until arr.length()) {
                val lk = arr.optJSONObject(i) ?: continue
                val u = lk.optString("url").trim()
                if (u.isEmpty()) continue
                pairs.add(lk.optString("platform").lowercase() to u)
            }
        }
        if (pairs.isEmpty()) {
            val single = json.optString("link").trim()
            if (single.isNotEmpty()) pairs.add(biz to single)
        }

        return pairs.filter { CloudType.isShareUrl(it.second) }.map { (plat, url) ->
            val p = plat.ifBlank { biz }
            val src = if (p in BIZ_CLOUD) p else biz
            PanItem(
                cloud = atawCloud(p, url),
                url = url,
                note = note,
                source = "ataw:${src.ifBlank { "unknown" }}",
                datetime = dt,
            )
        }
    }

    private fun atawCloud(biz: String, url: String): String = when (biz) {
        "quark" -> "quark"
        "ali" -> "aliyun"
        else -> CloudType.fromUrl(url) // sharepan 是聚合，只能靠 URL 判断真身
    }

    companion object {
        const val DEFAULT_BASE = "https://so.ataw.top"

        private val BIZ = listOf("quark", "ali", "sharepan")
        private val BIZ_CLOUD = setOf("quark", "ali", "sharepan")

        private val RESOURCE_RE = Regex("""/resources/(\d+)\?b=(quark|ali|sharepan)""", RegexOption.IGNORE_CASE)
    }
}

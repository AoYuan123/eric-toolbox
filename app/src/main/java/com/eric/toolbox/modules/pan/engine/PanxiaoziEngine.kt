package com.eric.toolbox.modules.pan.engine

import com.eric.toolbox.modules.pan.core.CloudType
import com.eric.toolbox.modules.pan.core.Dates
import com.eric.toolbox.modules.pan.core.Http
import com.eric.toolbox.modules.pan.core.LdJson
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
 * 盘小子（pan.xiaozi.cc）：SSR 搜索页的 ld+json 拿资源列表 → 再抓详情页取网盘直链。
 * 对应 Python `search_panxiaozi`。
 *
 * 关键坑：
 *  - 搜索页本身没有链接，必须进详情页，所以这是个「N+1 请求」源，慢
 *  - 详情并发上限 2 + 随机间隔，否则必被风控
 *  - 详情页里 xiaozi.cc 自己的链接要排除，否则每条结果都变成详情页地址
 *  - 详情页一条外链都没有时，退化成「指向详情页」的一条 others 结果（保留可点性）
 */
class PanxiaoziEngine(
    private val base: String = DEFAULT_BASE,
) : PanEngine {

    override val name: String = "panxiaozi"

    override suspend fun search(keyword: String, opts: SearchOptions): EngineResult {
        val url = "$base/resource?q=" + URLEncoder.encode(keyword, "UTF-8")
        val html = Http.getText(url, opts.timeoutMs)

        // 1. 搜索页：ItemList → [{name, url}]
        val found = ArrayList<Pair<String, String>>()
        for (block in LdJson.blocks(html)) {
            if (!block.optString("@type").equals("ItemList", ignoreCase = true)) continue
            val arr = block.optJSONArray("itemListElement") ?: continue
            for (i in 0 until arr.length()) {
                val el = arr.optJSONObject(i) ?: continue
                val u = el.optString("url")
                if (u.contains("/resource/")) {
                    found.add(el.optString("name").trim() to u)
                }
            }
        }
        if (found.isEmpty()) return EngineResult(name, emptyList(), 0)

        // 2. 详情页（并发 2）
        val targets = found.take(maxOf(1, minOf(opts.engineItemLimit, 10)))
        val sem = Semaphore(DETAIL_WORKERS)
        val items = coroutineScope {
            targets.map { (n, u) ->
                async {
                    sem.withPermit {
                        delay(Random.nextLong(DETAIL_DELAY_MIN, DETAIL_DELAY_MAX))
                        runCatching { detail(n, u, opts) }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll().flatten()
        }
        return EngineResult(name, items, found.size)
    }

    private suspend fun detail(name: String, pageUrl: String, opts: SearchOptions): List<PanItem> {
        val page = Http.getText(pageUrl, 25_000)

        var desc = ""
        var dtRaw = ""
        var genre = ""
        for (block in LdJson.blocks(page)) {
            if (block.optString("@type").equals("CreativeWork", ignoreCase = true)) {
                desc = block.optString("description").trim()
                dtRaw = block.optString("dateModified")
                genre = block.optString("genre").trim()
                break
            }
        }

        val note = if (genre.isEmpty()) name else "$name［$genre］"
        val short = if (desc.length > 80) desc.take(80) + "…" else desc
        val noteFull = if (short.isEmpty()) note else "$note：$short"
        val dt = Dates.normalize(dtRaw)

        val rows = ArrayList<PanItem>()
        val seen = HashSet<String>()
        for (m in CloudType.URL_REGEX.findAll(page)) {
            val u = m.value
            if (u.contains("xiaozi.cc")) continue
            if (!seen.add(u)) continue
            if (!CloudType.isShareUrl(u)) continue
            val c = CloudType.fromUrl(u)
            if (c == "others") continue
            rows.add(
                PanItem(
                    cloud = c,
                    url = u,
                    note = noteFull,
                    source = "panxiaozi",
                    datetime = dt,
                    detail = pageUrl,
                ),
            )
            if (rows.size >= 6) break
        }

        // 一条外链都没抓到：退化成指向详情页本身的一条，至少用户能点进去看
        if (rows.isEmpty()) {
            rows.add(
                PanItem(
                    cloud = "others",
                    url = pageUrl,
                    note = note,
                    source = "panxiaozi",
                    datetime = dt,
                ),
            )
        }
        return rows
    }

    companion object {
        const val DEFAULT_BASE = "https://pan.xiaozi.cc"

        /** 同站详情并发上限，别调高 */
        private const val DETAIL_WORKERS = 2
        private const val DETAIL_DELAY_MIN = 200L
        private const val DETAIL_DELAY_MAX = 800L
    }
}

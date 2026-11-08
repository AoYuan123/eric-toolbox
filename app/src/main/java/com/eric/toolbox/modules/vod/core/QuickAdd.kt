package com.eric.toolbox.modules.vod.core

import android.content.Context
import com.eric.toolbox.modules.vod.model.ConfigStats
import com.eric.toolbox.modules.vod.model.SiteType
import com.eric.toolbox.modules.vod.model.Subscription
import com.eric.toolbox.modules.vod.model.VodSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/**
 * 「粘一条链接就能用」的全部逻辑。
 *
 * 用户手里那条链接可能是三种完全不同的东西，界面上不该让他先理解 type 概念：
 *
 *  1. 标准苹果CMS接口（…/api.php/provide/vod）—— 直接就能搜能播，收成站点。
 *  2. 整份 TVBox / 影视仓配置 —— 里面混着 Spider 脚本和标准接口，
 *     把能直连的挑出来逐个实测，能用的列给用户勾选。
 *  3. Spider 脚本或普通网页 —— 说清为什么不行，别让人以为是 App 坏了。
 *
 * 为什么不支持 Spider：影视仓那类 App 内置 JS 运行时，把远程 .js 下载下来执行，
 * 脚本里写着搜索地址怎么拼、网页怎么解析。要在本 App 复现，等于让配置作者
 * 在我们进程里跑任意代码，且需要额外塞一个 JS 引擎。这条线从 v1.0.10 起就没开过。
 */
object QuickAdd {

    /** 手动添加的站点统一收在这一条本地订阅里，避免每加一个就多一条订阅 */
    const val MANUAL_SUB_ID = "manual"
    private const val MANUAL_SUB_NAME = "手动添加"

    /** 一条链接被消化后的三种结局 */
    sealed class Result {
        /** 它本身就是标准接口，可以立刻加成站点 */
        data class Direct(
            val site: VodSite,
            val hits: Int,
            val cats: Int,
            val ms: Long,
            val xml: Boolean,
        ) : Result()

        /** 它是整份配置，里面榨出了候选，逐个实测过 */
        data class Harvest(
            val candidates: List<Cand>,
            val stats: ConfigStats,
        ) : Result() {
            val okCount: Int get() = candidates.count { it.ok }
        }

        /** 什么都吃不下 */
        data class Failed(val reason: String, val hint: String = "") : Result()
    }

    /** 配置里榨出来的一个候选站，带实测结论 */
    data class Cand(
        val site: VodSite,
        val ok: Boolean,
        val hits: Int = 0,
        val cats: Int = 0,
        val ms: Long = 0,
        val err: String = "",
    )

    // ------------------------------------------------------------ 嗅探主流程

    suspend fun sniff(rawInput: String): Result = withContext(Dispatchers.IO) {
        val url = normalize(rawInput) ?: return@withContext Result.Failed(
            "地址格式不对",
            "要完整地址，比如 https://xxx.com/api.php/provide/vod",
        )

        val t0 = System.currentTimeMillis()
        val body = try {
            VodHttp.get(url, timeoutMs = 15_000)
        } catch (e: Exception) {
            return@withContext Result.Failed(
                VodHttp.messageOf(e),
                "先确认这个域名在浏览器里能打开；有些站点需要代理",
            )
        }
        val cost = System.currentTimeMillis() - t0

        // 顺序有讲究：先认标准接口（含 XML），再判网页。
        // 反过来的话，XML 接口里带 <html> 字样的会被误判成网页。
        readCms(body)?.let {
            return@withContext Result.Direct(
                site = siteOf(url, if (it.xml) SiteType.XML else SiteType.JSON),
                hits = it.hits,
                cats = it.cats,
                ms = cost,
                xml = it.xml,
            )
        }

        if (looksLikeHtml(body)) {
            return@withContext Result.Failed(
                "这是个网页，不是接口",
                "影视仓里「接口设置」填的是配置文件的地址；这里要的是 api.php/provide/vod 那种数据接口",
            )
        }

        // 整份配置：图种、Base64、带 // 注释的都能解
        val cfg = runCatching { ConfigParser.parse(body, MANUAL_SUB_ID, MANUAL_SUB_NAME) }.getOrNull()
        if (cfg != null && cfg.stats.total > 0) {
            if (cfg.sites.isEmpty()) return@withContext Result.Harvest(emptyList(), cfg.stats)
            return@withContext Result.Harvest(probeAll(cfg.sites), cfg.stats)
        }

        // 还有一种可能：用户给的地址本身是接口，但没带 ac 参数，返回的不是列表
        val withAc = withAcList(url)
        if (withAc != url) {
            val body2 = runCatching { VodHttp.get(withAc, timeoutMs = 15_000) }.getOrNull()
            if (body2 != null) {
                readCms(body2)?.let {
                    return@withContext Result.Direct(
                        site = siteOf(url, if (it.xml) SiteType.XML else SiteType.JSON),
                        hits = it.hits,
                        cats = it.cats,
                        ms = 0L,
                        xml = it.xml,
                    )
                }
            }
        }

        Result.Failed(
            "这个地址不像苹果CMS接口",
            "判断依据：补上 ?ac=list 之后仍然没有 list 或 class 字段。它可能是别的程序做的站，也可能是被拦了",
        )
    }

    /** 逐个实测候选，分批并发，避免十几个请求同时打出去被源站封 */
    private suspend fun probeAll(sites: List<VodSite>): List<Cand> = withContext(Dispatchers.IO) {
        sites.chunked(4).flatMap { batch ->
            batch.map { s ->
                async {
                    val t0 = System.currentTimeMillis()
                    try {
                        val body = VodHttp.get(withAcList(s.api), timeoutMs = 10_000)
                        val r = readCms(body)
                        val cost = System.currentTimeMillis() - t0
                        if (r == null) {
                            Cand(s, ok = false, ms = cost, err = "返回的不是接口数据")
                        } else {
                            Cand(s, ok = true, hits = r.hits, cats = r.cats, ms = cost)
                        }
                    } catch (e: Exception) {
                        Cand(
                            s, ok = false,
                            ms = System.currentTimeMillis() - t0,
                            err = VodHttp.messageOf(e),
                        )
                    }
                }
            }.awaitAll()
        }
    }

    // -------------------------------------------------------------- 手动站点

    fun manualSites(ctx: Context): List<VodSite> {
        val sub = ConfigRepo.subscriptions(ctx).firstOrNull { it.id == MANUAL_SUB_ID } ?: return emptyList()
        if (sub.rawJson.isBlank()) return emptyList()
        return runCatching { ConfigParser.parse(sub.rawJson, MANUAL_SUB_ID, MANUAL_SUB_NAME) }
            .getOrNull()?.sites.orEmpty()
    }

    /** 加一个站点；同 key 视为改名而不是重复添加 */
    fun addSite(ctx: Context, site: VodSite) {
        val list = manualSites(ctx).toMutableList()
        val i = list.indexOfFirst { it.key == site.key }
        if (i >= 0) list[i] = site else list.add(site)
        persist(ctx, list)
    }

    fun removeSite(ctx: Context, key: String) {
        persist(ctx, manualSites(ctx).filterNot { it.key == key })
    }

    fun renameSite(ctx: Context, key: String, name: String) {
        val list = manualSites(ctx)
        val i = list.indexOfFirst { it.key == key }
        if (i < 0 || name.isBlank()) return
        persist(ctx, list.toMutableList().also { it[i] = it[i].copy(name = name.trim()) })
    }

    private fun persist(ctx: Context, sites: List<VodSite>) {
        // 全删光就把订阅本身也清掉，别在列表里留个空壳
        if (sites.isEmpty()) {
            ConfigRepo.remove(ctx, MANUAL_SUB_ID)
            return
        }
        val arr = JSONArray()
        sites.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("key", s.key)
                    put("name", s.name)
                    put("type", s.type)
                    put("api", s.api)
                    put("searchable", true)
                },
            )
        }
        val json = JSONObject().apply {
            put("sites", arr)
            put("name", MANUAL_SUB_NAME)
        }.toString()
        ConfigRepo.upsert(
            ctx,
            Subscription(
                id = MANUAL_SUB_ID,
                name = MANUAL_SUB_NAME,
                url = "",
                rawJson = json,
                enabled = true,
                updatedAt = System.currentTimeMillis(),
                lastError = "",
            ),
        )
    }

    // ------------------------------------------------------------------ 工具

    /** 判断响应体的类型。返回 null 表示不是苹果CMS接口 */
    internal class Cms(val hits: Int, val cats: Int, val xml: Boolean)

    internal fun readCms(text: String): Cms? {
        val s = text.trimStart()
        if (s.startsWith("{")) {
            return try {
                val o = JSONObject(s)
                val list = o.optJSONArray("list")
                val cls = o.optJSONArray("class")
                // 只有 list 或 class 之一存在才算接口；其它 JSON 一律不当
                if (list == null && cls == null) null
                else Cms(list?.length() ?: 0, cls?.length() ?: 0, xml = false)
            } catch (e: Exception) {
                null
            }
        }
        if (s.startsWith("<")) {
            val n = Regex("<video[\\s>]").findAll(s).count()
            // 空结果也算合法响应：接口通、只是没数据，不能判成不可用
            if (n > 0 || s.contains("<list")) return Cms(n, 0, xml = true)
        }
        return null
    }

    private fun looksLikeHtml(t: String): Boolean {
        val head = t.trimStart().take(400).lowercase()
        return head.startsWith("<!doctype html") ||
            head.startsWith("<html") ||
            head.contains("<head") ||
            head.contains("<meta charset")
    }

    /**
     * 补 ac=list。有些地址自带 query（api.php/provide/vod?from=x），
     * 有些结尾就有问号，几种情况都要拼对，否则会把接口拼成 404。
     */
    fun withAcList(api: String): String {
        val base = api.trim()
        if (base.contains("ac=")) return base
        val sep = when {
            !base.contains("?") -> "?"
            base.endsWith("?") || base.endsWith("&") -> ""
            else -> "&"
        }
        return "$base$sep" + "ac=list"
    }

    /** 用户会连着空格、中文引号一起粘进来，先收拾干净 */
    private fun normalize(input: String): String? {
        var s = input.trim().trim('"', '\'', '“', '”', '‘', '’', '（', '）')
        s = s.filterNot { it.isWhitespace() }
        if (s.isEmpty()) return null
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return if (Regex("^https?://[^\\s]+$").matches(s)) s else null
    }

    /** 从接口地址推一个站点名，用户可以在列表里改名 */
    private fun siteOf(api: String, type: Int): VodSite {
        val host = runCatching { URL(api).host }.getOrNull().orEmpty().removePrefix("www.")
        val name = host.ifBlank { "新站点" }
        val key = "quick_" + Integer.toHexString(api.trim().hashCode())
        return VodSite(key = key, name = name, type = type, api = api.trim(), subId = MANUAL_SUB_ID)
    }
}

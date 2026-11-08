package com.eric.toolbox.modules.vod.core

import android.content.Context
import com.eric.toolbox.core.Store
import com.eric.toolbox.modules.vod.model.SiteType
import com.eric.toolbox.modules.vod.model.Subscription
import com.eric.toolbox.modules.vod.model.VodSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 开箱可用的源。
 *
 * 这个模块原来是纯空壳：必须自己找一份 TVBox 配置粘进来，而市面上现成的配置，
 * 点播部分基本全是 Spider（要加载远程 dex 执行），粘进来一个都用不了。
 * 所以这里带一组实测可用的标准苹果 CMS 接口，装完就能搜、能播。
 *
 * 三条原则：
 *  1. 只收 type=1 的 JSON 站。绝不为了"多给几个"就把 Spider 塞进来——那等于让 App 执行远程代码。
 *  2. 地址会轮换，所以优先拉远程清单，拉不到才用这里的内置值。
 *  3. 只播不存。不下载、不转存、不缓存任何影视内容。
 */
object BuiltinSources {

    /** 播种出去的订阅固定用这个 id，方便后续原地更新而不是越攒越多 */
    const val SUB_ID = "builtin"

    private const val KEY_SEEDED = "seeded"
    private const val KEY_FETCH_AT = "catalog_fetch_at"
    private const val REFRESH_OK_MS = 24L * 60 * 60 * 1000
    private const val REFRESH_FAIL_MS = 60L * 60 * 1000

    /**
     * 内置兜底清单。远程清单拉不到时用这一组。
     * 改这里必须重新发版，所以它只是后备，日常维护走远程清单。
     */
    val fallback: List<VodSite> = listOf(
        site("lzi", "量子资源", "https://cj.lziapi.com/api.php/provide/vod/"),
        site("dytt", "电影天堂", "https://caiji.dyttzyapi.com/api.php/provide/vod/"),
        site("jszy", "极速资源", "https://jszyapi.com/api.php/provide/vod/"),
        site("modu", "魔都资源", "https://caiji.moduapi.cc/api.php/provide/vod/"),
        site("bdzy", "百度资源", "https://api.apibdzy.com/api.php/provide/vod/"),
    )

    /**
     * 远程清单地址，按顺序尝试。
     * 国内直连 raw.githubusercontent 常常不通，所以补了两个镜像前缀。
     */
    private val CATALOG_URLS = listOf(
        "https://cdn.jsdelivr.net/gh/AoYuan123/eric-toolbox@main/vod-subscription/recommended.json",
        "https://raw.githubusercontent.com/AoYuan123/eric-toolbox/main/vod-subscription/recommended.json",
        "https://ghproxy.net/https://raw.githubusercontent.com/AoYuan123/eric-toolbox/main/vod-subscription/recommended.json",
    )

    private fun site(key: String, name: String, api: String) = VodSite(
        key = key,
        name = name,
        type = SiteType.JSON,
        api = api,
        searchable = true,
    )

    // ---------------------------------------------------------------- 播种

    /**
     * 第一次进影视模块时，如果没有订阅，就把推荐源写进去。
     *
     * 用 seeded 标记保证只播一次：用户主动删掉「推荐源」之后，
     * 下次进来不会被悄悄加回来，否则等于删不掉。
     */
    fun ensureSeeded(ctx: Context) {
        val p = Store.module(ctx, "vod")
        if (p.getBoolean(KEY_SEEDED, false)) return
        p.edit().putBoolean(KEY_SEEDED, true).apply()
        if (ConfigRepo.subscriptions(ctx).isNotEmpty()) return
        ConfigRepo.upsert(
            ctx,
            Subscription(
                id = SUB_ID,
                name = "推荐源",
                rawJson = toTvBoxJson(fallback),
                enabled = true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** 用户手动点「恢复推荐源」时用，不受 seeded 标记限制 */
    fun restore(ctx: Context) {
        ConfigRepo.upsert(
            ctx,
            Subscription(
                id = SUB_ID,
                name = "推荐源",
                rawJson = toTvBoxJson(fallback),
                enabled = true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun hasPresetSub(ctx: Context): Boolean =
        ConfigRepo.subscriptions(ctx).any { it.id == SUB_ID }

    // ------------------------------------------------------------ 远程清单

    /**
     * 拉远程清单更新推荐源。带节流：成功过就 24 小时内不再拉，失败则 1 小时后重试。
     *
     * 拉不到不是错误——内置清单还在，用户照样能看。所以这里不抛异常、不打扰用户。
     */
    suspend fun refreshCatalog(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val p = Store.module(ctx, "vod")
        val last = p.getLong(KEY_FETCH_AT, 0L)
        val gap = if (last > 0L) System.currentTimeMillis() - last else Long.MAX_VALUE
        val lastOk = p.getBoolean("catalog_ok", false)
        if (gap < (if (lastOk) REFRESH_OK_MS else REFRESH_FAIL_MS)) return@withContext false

        val fetched = fetchRemote()
        if (fetched.isNullOrEmpty()) {
            p.edit().putLong(KEY_FETCH_AT, System.currentTimeMillis())
                .putBoolean("catalog_ok", false).apply()
            return@withContext false
        }

        val exist = ConfigRepo.subscriptions(ctx).firstOrNull { it.id == SUB_ID }
        val body = toTvBoxJson(fetched)
        ConfigRepo.upsert(
            ctx,
            (exist ?: Subscription(id = SUB_ID, name = "推荐源")).copy(
                rawJson = body,
                enabled = true,
                updatedAt = System.currentTimeMillis(),
                lastError = "",
            ),
        )
        p.edit().putLong(KEY_FETCH_AT, System.currentTimeMillis())
            .putBoolean("catalog_ok", true).apply()
        true
    }

    /** 逐个镜像试，第一个能解析出站点的就用它 */
    private fun fetchRemote(): List<VodSite>? {
        for (u in CATALOG_URLS) {
            val text = runCatching { VodHttp.get(u, timeoutMs = 8000) }.getOrNull() ?: continue
            val list = parseCatalog(text)
            if (list.isNotEmpty()) return list
        }
        return null
    }

    private fun parseCatalog(text: String): List<VodSite> {
        val root = runCatching { JSONObject(text.trim()) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("sites") ?: return emptyList()
        val out = ArrayList<VodSite>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val api = o.optString("api").trim()
            if (!api.startsWith("http://") && !api.startsWith("https://")) continue
            val key = o.optString("key").trim().ifBlank { "r_$i" }
            out += site(key, o.optString("name").trim().ifBlank { key }, api)
        }
        return out
    }

    // -------------------------------------------------------------- 序列化

    /**
     * 转成 TVBox 配置格式，让推荐源和用户自己导入的配置走同一套解析与合并逻辑。
     * 不另造一套数据结构，少一条会分叉的代码路径。
     */
    fun toTvBoxJson(sites: List<VodSite>): String {
        val arr = JSONArray()
        for (s in sites) {
            arr.put(
                JSONObject()
                    .put("key", s.key)
                    .put("name", s.name)
                    .put("type", SiteType.JSON)
                    .put("api", s.api)
                    .put("searchable", 1)
                    .put("quickSearch", 1)
                    .put("filterable", 0),
            )
        }
        return JSONObject().put("sites", arr).toString()
    }
}

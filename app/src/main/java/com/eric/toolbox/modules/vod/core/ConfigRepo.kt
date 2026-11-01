package com.eric.toolbox.modules.vod.core

import android.content.Context
import com.eric.toolbox.core.Store
import com.eric.toolbox.modules.vod.model.LiveChannel
import com.eric.toolbox.modules.vod.model.ParseRule
import com.eric.toolbox.modules.vod.model.PlayHistory
import com.eric.toolbox.modules.vod.model.Subscription
import com.eric.toolbox.modules.vod.model.VodCategory
import com.eric.toolbox.modules.vod.model.VodConfig
import com.eric.toolbox.modules.vod.model.VodSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 订阅仓库：负责「订阅列表 + 配置正文 + 合并」三件事。
 *
 * 存储分区用 Store.module(ctx, "vod")，跟其它模块互不干扰，
 * 备份恢复也会自动带上。
 *
 * 为什么正文要单独缓存一份：远程订阅不能每次进页面都联网，
 * 但整份配置解析后再存又会很大且丢失原始格式，所以存原文、用时再解析。
 */
object ConfigRepo {

    private const val KEY_SUBS = "subs"
    private const val PREFIX_BODY = "body_"

    private fun p(ctx: Context) = Store.module(ctx, "vod")

    // ------------------------------------------------------------ 订阅列表

    fun subscriptions(ctx: Context): List<Subscription> {
        val txt = p(ctx).getString(KEY_SUBS, "").orEmpty()
        if (txt.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(txt)
            List(arr.length()) { Subscription.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun upsert(ctx: Context, sub: Subscription) {
        val list = subscriptions(ctx).toMutableList()
        val i = list.indexOfFirst { it.id == sub.id }
        if (i >= 0) list[i] = sub else list.add(0, sub)
        saveAll(ctx, list)
    }

    fun remove(ctx: Context, id: String) {
        saveAll(ctx, subscriptions(ctx).filterNot { it.id == id })
        p(ctx).edit().remove(PREFIX_BODY + id).apply()
    }

    private fun saveAll(ctx: Context, list: List<Subscription>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        p(ctx).edit().putString(KEY_SUBS, arr.toString()).apply()
    }

    // -------------------------------------------------------------- 配置正文

    fun bodyOf(ctx: Context, id: String): String = p(ctx).getString(PREFIX_BODY + id, "").orEmpty()

    fun putBody(ctx: Context, id: String, text: String) {
        p(ctx).edit().putString(PREFIX_BODY + id, text).apply()
    }

    /** 拉远程订阅。成功会缓存正文并清掉上次错误；失败会把错误记进订阅，方便 UI 直接显示。 */
    suspend fun fetchRemote(ctx: Context, sub: Subscription): Result<VodConfig> =
        withContext(Dispatchers.IO) {
            val outcome = runCatching {
                val text = VodHttp.get(sub.url, timeoutMs = 20_000)
                putBody(ctx, sub.id, text)
                ConfigParser.parse(text, sub.id, sub.name)
            }
            if (outcome.isSuccess) {
                upsert(ctx, sub.copy(updatedAt = System.currentTimeMillis(), lastError = ""))
            } else {
                val err = outcome.exceptionOrNull()
                upsert(ctx, sub.copy(lastError = if (err != null) VodHttp.messageOf(err) else "未知错误"))
            }
            outcome
        }

    // ---------------------------------------------------------------- 合并

    /** 校验一段配置能不能解析，返回 (站点数, 直播数)。本地导入前先过一遍，避免存进一堆垃圾。 */
    fun parseCheck(text: String): Pair<Int, Int> {
        val cfg = ConfigParser.parse(text, "", "")
        return cfg.sites.size to cfg.lives.size
    }

    /** 所有启用订阅合并后的结果。staleSubIds 是「还没拉过正文」的订阅，UI 要提示用户去刷新。 */
    data class Merged(
        val configs: List<VodConfig>,
        val sites: List<VodSite>,
        val parses: List<ParseRule>,
        val lives: List<LiveChannel>,
        val warnings: List<String>,
        val staleSubIds: List<String>,
    ) {
        val isEmpty: Boolean get() = sites.isEmpty() && lives.isEmpty()
    }

    fun merged(ctx: Context): Merged {
        val configs = ArrayList<VodConfig>()
        val stale = ArrayList<String>()
        for (s in subscriptions(ctx).filter { it.enabled }) {
            val body = if (s.isRemote) bodyOf(ctx, s.id) else s.rawJson
            if (body.isBlank()) {
                stale += s.id
                continue
            }
            val cfg = runCatching { ConfigParser.parse(body, s.id, s.name) }
                .onFailure { stale += s.id }
                .getOrNull() ?: continue
            configs += cfg
        }
        return Merged(
            configs = configs,
            sites = configs.flatMap { it.sites },
            parses = configs.flatMap { it.parses },
            lives = configs.flatMap { it.lives },
            warnings = configs.flatMap { it.warnings }.distinct(),
            staleSubIds = stale,
        )
    }
}

/**
 * 影视模块的偏好设置。
 */
object VodPrefs {
    private const val MAX_HISTORY = 60

    private fun p(ctx: Context) = Store.module(ctx, "vod")

    /** TMDB API key，用户自备。没配就退回用站点自己的海报，不影响观看。 */
    fun tmdbKey(ctx: Context): String = p(ctx).getString("tmdb_key", "").orEmpty().trim()

    fun setTmdbKey(ctx: Context, v: String) {
        p(ctx).edit().putString("tmdb_key", v.trim()).apply()
    }

    /** 站点里的某些线路需要先过一层解析器，默认开。 */
    fun useParser(ctx: Context): Boolean = p(ctx).getBoolean("use_parse", true)

    fun setUseParser(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("use_parse", v).apply()
    }

    fun lastSiteKey(ctx: Context): String = p(ctx).getString("last_site", "").orEmpty()

    fun setLastSiteKey(ctx: Context, v: String) {
        p(ctx).edit().putString("last_site", v).apply()
    }

    /** 用户手工补的分类。站点自己返回 class[] 时优先用站点的。 */
    fun customCategories(ctx: Context, siteKey: String): List<VodCategory> {
        if (siteKey.isBlank()) return emptyList()
        val txt = p(ctx).getString("cat_$siteKey", "").orEmpty()
        if (txt.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(txt)
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                VodCategory(o.optString("id"), o.optString("name"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setCustomCategories(ctx: Context, siteKey: String, list: List<VodCategory>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        p(ctx).edit().putString("cat_$siteKey", arr.toString()).apply()
    }

    /** 播放记录，新的在前 */
    fun history(ctx: Context): List<PlayHistory> {
        val txt = p(ctx).getString("history", "").orEmpty()
        if (txt.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(txt)
            List(arr.length()) { PlayHistory.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addHistory(ctx: Context, h: PlayHistory) {
        val list = history(ctx).toMutableList()
        list.removeAll { it.url == h.url }
        list.add(0, h)
        val arr = JSONArray()
        list.take(MAX_HISTORY).forEach { arr.put(it.toJson()) }
        p(ctx).edit().putString("history", arr.toString()).apply()
    }

    fun clearHistory(ctx: Context) {
        p(ctx).edit().remove("history").apply()
    }
}

package com.eric.toolbox.modules.vod.core

import android.util.Base64
import com.eric.toolbox.modules.vod.model.LiveChannel
import com.eric.toolbox.modules.vod.model.ParseRule
import com.eric.toolbox.modules.vod.model.SiteType
import com.eric.toolbox.modules.vod.model.VodConfig
import com.eric.toolbox.modules.vod.model.VodSite
import org.json.JSONArray
import org.json.JSONObject

/**
 * 解析影视配置（TVBox / 猫影视那一套 JSON 格式）。
 *
 * 一份配置长这样：
 *   { "sites":[{ "key":"xx","name":"xx","type":1,"api":"https://...","searchable":1 }],
 *     "lives":[{ "name":"直播","urls":[{ "name":"CCTV1","url":"https://..." }] }],
 *     "parses":[{ "name":"解析","url":"https://...?url=","type":0 }],
 *     "rules":{ "timeout":6000,"ua":"Mozilla/5.0" } }
 *
 * 关于 type=3 的 Spider：那需要把第三方 jar/dex 加载进进程里执行，
 * 相当于让 App 运行来路不明的代码，安全上不可接受，这里一律跳过并给用户提示。
 */
object ConfigParser {

    fun parse(raw: String, subId: String, subName: String): VodConfig {
        val root = JSONObject(prepare(raw))
        val warnings = ArrayList<String>()

        // 配置的 spider 字段：即便写了也不用，明确告知
        val spiderDeclared = root.optString("spider").trim()
        if (spiderDeclared.isNotBlank()) {
            warnings += "这份配置指定了 Spider 脚本，本 App 不使用，相关站点会被跳过"
        }

        val sites = ArrayList<VodSite>()
        val arr = root.optJSONArray("sites") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = o.optString("key").trim().ifBlank { "site_$i" }
            val type = o.optInt("type", SiteType.JSON)
            val site = VodSite(
                key = key,
                name = o.optString("name").trim().ifBlank { key },
                type = type,
                api = o.optString("api").trim(),
                ext = extOf(o),
                searchable = flag(o, "searchable", true),
                filterable = flag(o, "filterable", false),
                playable = flag(o, "playable", true),
                subId = subId,
            )
            if (!site.supported) {
                warnings += "已跳过站点「${site.name}」：${site.unsupportedReason}"
                continue
            }
            if (!site.hasApi) {
                warnings += "已跳过站点「${site.name}」：api 地址缺失或不是 http(s)"
                continue
            }
            sites += site
        }

        // 规则：超时单位是毫秒，夹到合理区间，避免被一份配置拖死整个列表
        val rules = root.optJSONObject("rules")
        val rawTimeout = rules?.optInt("timeout", 0) ?: 0
        val timeoutMs = if (rawTimeout in 3_000..60_000) rawTimeout else 15_000
        val ua = rules?.optString("ua").orEmpty().trim()
            .ifBlank { root.optString("ua").trim() }

        if (sites.isEmpty()) {
            warnings += "这份配置里没有可用的点播站点"
        }

        return VodConfig(
            subId = subId,
            subName = subName,
            sites = sites,
            parses = parseRules(root.optJSONArray("parses")),
            lives = parseLives(root.optJSONArray("lives")),
            ua = ua,
            timeoutMs = timeoutMs,
            warnings = warnings,
        )
    }

    // --------------------------------------------------------------- 解析器

    private fun parseRules(arr: JSONArray?): List<ParseRule> {
        if (arr == null) return emptyList()
        val out = ArrayList<ParseRule>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url").trim()
            if (url.isBlank()) continue
            out += ParseRule(
                name = o.optString("name").trim().ifBlank { "解析${i + 1}" },
                url = url,
                type = o.optInt("type", 0),
                ext = extOf(o),
            )
        }
        return out
    }

    // --------------------------------------------------------------- 直播源

    /**
     * lives 有两种写法：
     *   { "name":"央视频道","url":"https://...tv.m3u" }              —— 单条
     *   { "name":"央视","urls":[{ "name":"CCTV1","url":"..." }] }    —— 分组
     * 两种都要支持。
     */
    private fun parseLives(arr: JSONArray?): List<LiveChannel> {
        if (arr == null) return emptyList()
        val out = ArrayList<LiveChannel>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val group = o.optString("name").trim()
            val urls = o.optJSONArray("urls")
            if (urls != null) {
                for (j in 0 until urls.length()) {
                    val c = urls.optJSONObject(j) ?: continue
                    val u = c.optString("url").trim()
                    if (u.isBlank()) continue
                    out += LiveChannel(group, c.optString("name").trim().ifBlank { group }, u)
                }
            } else {
                val u = o.optString("url").trim()
                if (u.isNotBlank()) out += LiveChannel(o.optString("group").trim(), group, u)
            }
        }
        return out
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 大部分接口是裸 JSON，但也有把整份配置做一次 Base64 的写法。
     * 看得懂就直接返回，看不懂再试着解一层。
     */
    private fun prepare(raw: String): String {
        val s = raw.trim().trim('\uFEFF').trim()
        if (s.isBlank()) throw IllegalArgumentException("配置内容为空")
        if (s.startsWith("{") || s.startsWith("[")) return s
        val decoded = runCatching {
            String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
        if (!decoded.isNullOrBlank() && (decoded.startsWith("{") || decoded.startsWith("["))) {
            return decoded.trim()
        }
        throw IllegalArgumentException("看不懂这份配置：既不是 JSON，也不是可解出的 Base64")
    }

    /** searchable/filterable 这类开关，各家写法有 true / 1 / "0" 三种 */
    private fun flag(o: JSONObject, key: String, def: Boolean): Boolean {
        return when (val v = o.opt(key)) {
            null -> def
            is Boolean -> v
            is Int -> v != 0
            is String -> v.trim() !in setOf("0", "false", "")
            else -> def
        }
    }

    /** ext 字段可能是字符串，也可能是个对象（里面塞变态分类表），统一存成文本 */
    private fun extOf(o: JSONObject): String {
        val v = o.opt("ext") ?: return ""
        return if (v is String) v.trim() else v.toString()
    }
}

package com.eric.toolbox.modules.vod.core

import java.util.Base64
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
        val prepared = prepare(raw)
        val root = JSONObject(prepared.json)
        val warnings = ArrayList<String>()
        if (prepared.note.isNotBlank()) warnings += prepared.note

        // 配置的 spider 字段：即便写了也不用，明确告知
        val spiderDeclared = root.optString("spider").trim()
        if (spiderDeclared.isNotBlank()) {
            warnings += "这份配置指定了 Spider 脚本，本 App 不使用，相关站点会被跳过"
        }

        val sites = ArrayList<VodSite>()
        val spiderNames = ArrayList<String>()
        val badApiNames = ArrayList<String>()
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
                spiderNames += site.name
                continue
            }
            if (!site.hasApi) {
                badApiNames += site.name
                continue
            }
            sites += site
        }
        // 大配置里 Spider 动辄几十个，逐条报会把屏幕刷爆，这里汇总成一句
        if (spiderNames.isNotEmpty()) {
            val head = spiderNames.take(3).joinToString("、")
            val tail = if (spiderNames.size > 3) " 等" else ""
            warnings += "已跳过 ${spiderNames.size} 个 Spider 站点（$head$tail）：" +
                "这类站点要下载并在 App 内执行第三方代码，本 App 不支持"
        }
        if (badApiNames.isNotEmpty()) {
            warnings += "已跳过 ${badApiNames.size} 个站点：api 地址缺失或不是 http(s)"
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
     * 把一份来源不明的内容还原成可以喂给 org.json 的纯 JSON。
     *
     * 现实里接口爱把自己藏起来，目前见过三种壳，按顺序试：
     *   1. 本身就是裸 JSON（可能带 // 行注释或 C 风格块注释）
     *   2. 整份做了一次 Base64
     *   3. 图种：一张 JPEG/PNG 后面拼了 Base64。拼接处没有任何标记，
     *      但 Base64 全是 ASCII，按 UTF-8 读出来不会坏，所以直接扫可疑长段逐个试解。
     *      这是饭太硬那类导航站的标准做法 —— 你看 arts.jpeg 拿到的其实是配置文件。
     *
     * @return (还原出的 JSON, 给人看的提示)；走到最后一种会带一句提示，让用户知道发生了什么。
     */
    private fun prepare(raw: String): Prepared {
        val s = raw.trim().trim('\uFEFF').trim()
        if (s.isBlank()) throw IllegalArgumentException("配置内容为空")

        // 1) 裸 JSON
        attempt(s)?.let { return Prepared(it) }

        // 2) 整份 Base64。先抹掉换行：有些接口会把 Base64 按 76 列折行
        decodeBase64(s.replace(Regex("\\s"), ""))?.let {
            attempt(it)?.let { json -> return Prepared(json) }
        }

        // 3) 图种：图片数据后面拼的 Base64 段
        findStego(s)?.let {
            return Prepared(it, "这份配置藏在图片数据里（图种），已自动提取")
        }

        throw IllegalArgumentException(
            "看不懂这份配置：既不是 JSON，也不是可解出的 Base64，也没在图种里找到内容"
        )
    }

    private data class Prepared(val json: String, val note: String = "")

    /**
     * 候选一律「先还原、再验能用」，不能只看长得像就返回。
     *
     * 图片二进制里偶尔也能 ”配平“ 出一段假 JSON（例如 '{' 后面凑巧出现 '}'），
     * 不校验就返回的话，底下的 JSONObject(...) 会抛出一堆用户看不懂的 org.json 异常，
     * 而不是「看不懂这份配置」。宁可这条候选作废，继续试下一种。
     */
    private fun attempt(text: String): String? {
        val json = firstJsonObject(text) ?: return null
        val cooked = stripJsonComments(json)
        val usable = runCatching { JSONObject(cooked); true }.getOrElse { false }
        return if (usable) cooked else null
    }

    // ------------------------------------------------------------ 还原工具

    /**
     * 从任意文本里抠出第一个完整的 JSON 对象。
     *
     * 必须做「引号感知的括号配对」，不能简单地 indexOf('}')：
     * 站点名字里常出现中文标点外的花括号，字符串内的括号一旦被当成结构，
     * 截出来的 JSON 就是残的。
     */
    private fun firstJsonObject(s: String): String? {
        val start = s.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until s.length) {
            val c = s[i]
            if (inStr) {
                when {
                    esc -> esc = false
                    c == '\\' -> esc = true
                    c == '"' -> inStr = false
                }
                continue
            }
            when (c) {
                '"' -> inStr = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return s.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /**
     * 用 java.util 而不是 android.util：后者在 JVM 单元测试里是空壳（跟 org.json 一个德性），
     * 用 android 版这段还原逻辑就彻底测不到，只能靠真机翻车才发现问题。
     * minSdk 26 起 java.util.Base64 可用，没有兼容性代价。
     *
     * 这里用的是**严格**解码器，不是 MIME 的那个。MIME 解码器会静默跳过非法字符，
     * 副作用是「图片数据 + Base64」这种图种会被它当成一份合法 Base64 直接解掉，
     * 于是图种那条路径永远轮不到，用户也收不到「这是图种」的提示，排查时非常容易误判。
     * 严格解码器 + [findStego] 各管一种壳，层次才清楚。
     */
    private fun decodeBase64(candidate: String): String? = runCatching {
        String(Base64.getDecoder().decode(candidate), Charsets.UTF_8)
    }.getOrNull()

    /**
     * 图种提取。一段 Base64 藏在一堆二进制垃圾后面，没有任何分隔符，
     * 唯一线索就是它足够长且只含 Base64 字符。
     *
     * 注意：解码出来的头部往往有几个错位字节（取决于对齐位置），
     * 所以丢给 firstJsonObject 从第一个 '{' 开始接管，不去纠结那几个字节。
     */
    private fun findStego(s: String): String? {
        val candidates = Regex("[A-Za-z0-9+/=]{200,}").findAll(s)
        for (m in candidates) {
            val body = m.value
            // 长度不对齐要砍掉尾巴，否则解码器直接抛异常
            val aligned = body.substring(0, body.length - body.length % 4)
            val json = decodeBase64(aligned)?.let { attempt(it) }
            if (json != null) return json
        }
        return null
    }

    /**
     * 去掉 JSON 里的行注释与块注释。org.json 不认注释，但各家配置爱写，
     * 常见的是用 // 注释掉某个出问题的站点而舍不得删。
     *
     * 顺序是命门：必须**逐字符扫描并在字符串内保持原样**，绝不能先把 "//" 全局替换掉
     * —— 那样 "http://xxx" 里的 // 会被当成注释起点，整份配置当场就会被切烂。
     * （这个坑栽过一次，别再犯。）
     */
    private fun stripJsonComments(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        var inStr = false
        var esc = false
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                out.append(c)
                when {
                    esc -> esc = false
                    c == '\\' -> esc = true
                    c == '"' -> inStr = false
                }
                i++
                continue
            }
            if (c == '"') {
                inStr = true
                out.append(c)
                i++
                continue
            }
            if (c == '/' && i + 1 < s.length && s[i + 1] == '/') {
                while (i < s.length && s[i] != '\n' && s[i] != '\r') i++
                continue
            }
            if (c == '/' && i + 1 < s.length && s[i + 1] == '*') {
                i += 2
                while (i + 1 < s.length && !(s[i] == '*' && s[i + 1] == '/')) i++
                i += 2
                out.append(' ')
                continue
            }
            out.append(c)
            i++
        }
        // 注释被抹掉后常留下半拉悬空逗号：{ "a":1, // 注释\n } → {"a":1, }
        return Regex(",(\\s*[}\\]])").replace(out.toString(), "\$1")
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

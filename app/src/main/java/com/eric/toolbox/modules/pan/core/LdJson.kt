package com.eric.toolbox.modules.pan.core

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 从 HTML 里取 `application/ld+json` 结构化数据（对应 Python 的 `ldjson_blocks`）。
 *
 * 盘小子、部分 SSR 站把资源列表放在 ld+json 里，比正则扒 HTML 稳。
 */
object LdJson {

    private val BLOCK = Regex(
        """<script type="application/ld\+json">(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    /** 逐个产出 ld+json 块；顶层是数组时自动展开 */
    fun blocks(html: String?): List<JSONObject> {
        if (html.isNullOrEmpty()) return emptyList()
        val out = ArrayList<JSONObject>()
        for (m in BLOCK.findAll(html)) {
            val raw = m.groupValues[1].trim()
            if (raw.isEmpty()) continue
            val parsed = runCatching { JSONTokener(raw).nextValue() }.getOrNull() ?: continue
            when (parsed) {
                is JSONObject -> out.add(parsed)
                is JSONArray -> for (i in 0 until parsed.length()) {
                    parsed.optJSONObject(i)?.let { out.add(it) }
                }
            }
        }
        return out
    }

    /** 按 @type 找第一个匹配块 */
    fun firstOfType(html: String?, type: String): JSONObject? =
        blocks(html).firstOrNull { it.optString("@type").equals(type, ignoreCase = true) }
}

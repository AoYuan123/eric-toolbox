package com.eric.toolbox.modules.translate

import org.json.JSONArray
import org.json.JSONObject

/** 翻译板块的纯逻辑（可单测）：判断方向、单词识别、历史记录格式。 */
object TranslateLogic {
    enum class Dir(val from: String, val to: String, val label: String) {
        AUTO("auto", "", "自动"), ZH_EN("zh", "en", "中文 → 英文"), EN_ZH("en", "zh", "英文 → 中文")
    }

    private fun hasHan(s: String) = s.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

    /** 自动方向：含汉字就译成英文，否则译成中文。返回 (from, to)。 */
    fun resolve(dir: Dir, text: String): Pair<String, String> = when (dir) {
        Dir.ZH_EN -> "zh" to "en"
        Dir.EN_ZH -> "en" to "zh"
        Dir.AUTO -> if (hasHan(text)) "zh" to "en" else "en" to "zh"
    }

    /** 是不是单个英文单词（允许连字符和撇号），是的话返回小写形式。 */
    fun singleEnglishWord(s: String): String? {
        val t = s.trim().trim { it in ".,;:!?\"'“”‘’()" }
        if (t.isEmpty() || t.length > 40 || t.any { it.isWhitespace() }) return null
        if (!t.all { it in 'a'..'z' || it in 'A'..'Z' || it == '-' || it == '\'' || it == '’' }) return null
        if (!t.first().isLetter()) return null
        return t.lowercase().replace('’', '\'')
    }

    data class Item(val src: String, val dst: String, val from: String, val to: String, val at: Long)

    fun historyToJson(list: List<Item>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("s", it.src).put("d", it.dst).put("f", it.from).put("t", it.to).put("at", it.at)) }
    }.toString()

    fun historyFromJson(s: String?): List<Item> = runCatching {
        val a = JSONArray(s ?: "[]")
        (0 until a.length()).map { val o = a.getJSONObject(it); Item(o.optString("s"), o.optString("d"), o.optString("f"), o.optString("t"), o.optLong("at")) }
    }.getOrDefault(emptyList())

    /** 新记录放最前；同一原文只留最新一条；最多 30 条。 */
    fun addHistory(list: List<Item>, item: Item, max: Int = 30): List<Item> =
        (listOf(item) + list.filter { it.src.trim() != item.src.trim() || it.to != item.to }).take(max)

    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
}

package com.eric.toolbox.modules.english

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/**
 * 背词调度（纯逻辑，可单测）。采用 SM-2 一类的间隔复习：自评「忘了 / 模糊 / 记得」，
 * 记得 → 间隔按难度系数拉长；忘了 → 10 分钟后再来、难度系数下调；新词第一次「记得」= 1 天。
 */
object EnglishLogic {
    const val AGAIN = 0
    const val HARD = 1
    const val GOOD = 2

    data class Card(
        val id: String,          // book:key
        val book: String,
        val key: String,
        val ivlDays: Double = 0.0,
        val ease: Double = 2.5,
        val due: Long = 0,
        val reps: Int = 0,
        val lapses: Int = 0,
        val last: Long = 0,
    ) { val isNew get() = reps == 0 }

    private const val MIN = 60_000L
    private const val DAY = 86_400_000L

    fun schedule(c: Card, rating: Int, now: Long): Card = when (rating) {
        AGAIN -> c.copy(ivlDays = 0.0, ease = (c.ease - 0.2).coerceAtLeast(1.3), due = now + 10 * MIN, reps = c.reps + 1, lapses = c.lapses + if (c.reps > 0) 1 else 0, last = now)
        HARD -> {
            val ivl = if (c.ivlDays < 1) 1.0 else (c.ivlDays * 1.2).coerceAtLeast(c.ivlDays + 1)
            c.copy(ivlDays = ivl, ease = (c.ease - 0.15).coerceAtLeast(1.3), due = now + (ivl * DAY).toLong(), reps = c.reps + 1, last = now)
        }
        else -> {
            val ivl = when {
                c.ivlDays < 1 && c.reps == 0 -> 1.0
                c.ivlDays < 1 -> 1.0
                c.ivlDays < 2 -> 4.0
                else -> c.ivlDays * c.ease
            }.coerceAtMost(365.0)
            c.copy(ivlDays = ivl, due = now + (ivl * DAY).toLong(), reps = c.reps + 1, last = now)
        }
    }

    /** 自评按钮上显示的“下次什么时候”。 */
    fun preview(c: Card, rating: Int, now: Long, en: Boolean): String {
        val d = schedule(c, rating, now).due - now
        return when {
            d < 60 * MIN -> if (en) "${d / MIN} min" else "${d / MIN} 分钟后"
            d < 36 * 60 * MIN -> if (en) "tomorrow" else "明天"
            else -> { val days = Math.round(d.toDouble() / DAY); if (en) "$days days" else "$days 天后" }
        }
    }

    fun toJson(c: Card): JSONObject = JSONObject().put("id", c.id).put("b", c.book).put("k", c.key).put("i", c.ivlDays).put("e", c.ease)
        .put("d", c.due).put("r", c.reps).put("l", c.lapses).put("t", c.last)

    fun fromJson(o: JSONObject) = Card(o.getString("id"), o.optString("b"), o.optString("k"), o.optDouble("i", 0.0), o.optDouble("e", 2.5),
        o.optLong("d"), o.optInt("r"), o.optInt("l"), o.optLong("t"))

    fun listToJson(list: Collection<Card>) = JSONArray().apply { list.forEach { put(toJson(it)) } }.toString()
    fun listFromJson(s: String): List<Card> = runCatching { val a = JSONArray(s); (0 until a.length()).map { fromJson(a.getJSONObject(it)) } }.getOrDefault(emptyList())

    /** 北京时间的“今天”编号（用于每日新词计数、连续天数）。 */
    fun dayNo(ms: Long): Int {
        val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")); c.timeInMillis = ms
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    /** 连续学习天数：从今天（或昨天，今天还没学时）往前数连续有记录的天。 */
    fun streak(days: Set<Int>, todayMs: Long): Int {
        var n = 0
        var t = todayMs
        if (dayNo(t) !in days) t -= DAY
        while (dayNo(t) in days) { n++; t -= DAY }
        return n
    }

    /** 选今天的卡：先到期复习（按到期时间），再按词书顺序补新词，直到今天的新词上限。 */
    fun queue(cards: Map<String, Card>, newPool: List<Card>, newToday: Int, newLimit: Int, now: Long, maxReview: Int = 200): List<Card> {
        val due = cards.values.filter { !it.isNew && it.due <= now }.sortedBy { it.due }.take(maxReview)
        val fresh = newPool.filter { cards[it.id]?.isNew != false }.take((newLimit - newToday).coerceAtLeast(0))
        return due + fresh
    }

    /** 选择题干扰项：从同一本书里取 3 个别的释义（取前半句，避免太长）。 */
    fun distractors(answer: String, pool: List<String>, seed: Int): List<String> {
        val r = java.util.Random(seed.toLong())
        return pool.filter { it != answer }.distinct().shuffled(r).take(3)
    }

    /** 拼写检查：忽略大小写、首尾空格和多余空格。 */
    fun spellOk(input: String, answer: String) = norm(input) == norm(answer)
    private fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ").trimEnd('.', '?', '!', ',')

    /** 例句挖空：把目标词（不分大小写）换成首字母 + 下划线。 */
    fun cloze(sentence: String, word: String): String? {
        val i = sentence.lowercase().indexOf(word.lowercase())
        if (i < 0) return null
        val hit = sentence.substring(i, i + word.length)
        return sentence.substring(0, i) + hit.first() + "_".repeat((hit.length - 1).coerceIn(3, 12)) + sentence.substring(i + word.length)
    }

    fun shortZh(s: String, max: Int = 26): String { val a = s.substringBefore('（').substringBefore('；').trim(); return if (a.length > max) a.take(max) + "…" else a }

    // ---------------- 口语对练 / 发音 ----------------

    data class Turn(val reply: String, val better: String, val why: String)

    /** 解析 AI 每轮返回的 JSON（{"reply","better","why"}），容忍 ```json 包裹；解析不了就整段当回复。 */
    fun parseTurn(raw: String): Turn {
        val t = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = t.indexOf('{'); val end = t.lastIndexOf('}')
        if (start >= 0 && end > start) runCatching {
            val o = JSONObject(t.substring(start, end + 1))
            val reply = o.optString("reply").trim()
            if (reply.isNotEmpty()) return Turn(reply, o.optString("better").trim(), o.optString("why").trim())
        }
        return Turn(t, "", "")
    }

    /** 跟读识别检查：目标句的每个词是否在识别结果里出现（忽略大小写和标点）。 */
    fun wordCheck(target: String, heard: String): List<Pair<String, Boolean>> {
        fun norm(w: String) = w.lowercase().trim { !it.isLetterOrDigit() }
        val heardSet = heard.split(Regex("\\s+")).map(::norm).filter { it.isNotEmpty() }.toMutableList()
        return target.split(Regex("\\s+")).filter { it.isNotBlank() }.map { w ->
            val n = norm(w)
            val ok = n.isEmpty() || heardSet.remove(n)
            w to ok
        }
    }
}

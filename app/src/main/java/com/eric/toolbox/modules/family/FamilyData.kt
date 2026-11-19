package com.eric.toolbox.modules.family

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 绳子上挂的东西只有三种：照片、视频片段、一段录音。
 *
 * 刻意不做"文字节点"——想写话就写在某张照片下面，单独一条没图的文字挂在绳上会很怪，
 * 而且会让绳子退化成日记本。声音片是例外：它本身就是"内容"，不是"配文"。
 */
enum class Kind { PHOTO, VIDEO, AUDIO }

data class Entry(
    val id: String,
    val kind: Kind,
    /** 相对 media/ 的文件名。 */
    val file: String,
    /** 当时写下的那句话，可以为空——不写也能存。 */
    val text: String = "",
    /** 挂载日期（零点毫秒）。位置即日期，所以这个字段同时决定它在绳上的顺序。 */
    val atMs: Long,
    val madeAtMs: Long = atMs,
    /** 视频/录音时长；照片为 0。 */
    val durationMs: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.name)
        put("file", file)
        put("text", text)
        put("at", atMs)
        put("made", madeAtMs)
        put("dur", durationMs)
    }

    companion object {
        fun fromJson(o: JSONObject): Entry = Entry(
            id = o.getString("id"),
            kind = runCatching { Kind.valueOf(o.getString("kind")) }.getOrDefault(Kind.PHOTO),
            file = o.getString("file"),
            text = o.optString("text", ""),
            atMs = o.optLong("at", 0L),
            madeAtMs = o.optLong("made", o.optLong("at", 0L)),
            durationMs = o.optLong("dur", 0L),
        )

        fun toJsonArray(list: List<Entry>): JSONArray = JSONArray().apply { list.forEach { put(it.toJson()) } }

        /** 单条坏数据不拖垮整张表——跳过它，其余照常显示。 */
        fun fromJsonArray(a: JSONArray): List<Entry> =
            (0 until a.length()).mapNotNull { runCatching { fromJson(a.getJSONObject(it)) }.getOrNull() }
    }
}

/**
 * 孕周与月龄。
 *
 * 孕期以预产期（EDD）为锚：孕期第 0 天 = EDD − 280 天，这是产科通用的"末次月经 + 280 天"算法。
 * 出生后改用真实出生日期算月龄，因为预产期和真正出生的日子可能差两三周。
 */
object FamilyDate {
    val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    const val GESTATION_DAYS = 280

    fun today(): LocalDate = LocalDate.now(ZONE)

    fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZONE).toLocalDate()

    fun atDay(d: LocalDate): Long = d.atStartOfDay(ZONE).toInstant().toEpochMilli()

    /** 两个日期之间的天数（b - a）。 */
    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)

    /** 孕期第 0 天；没有预产期时返回 null。 */
    fun gestationStart(edd: LocalDate?): LocalDate? = edd?.minusDays(GESTATION_DAYS.toLong())

    /** 「孕 22 周 +1」。还没到起点返回空串，由调用方决定不显示。 */
    fun gestationLabel(edd: LocalDate?, day: LocalDate): String {
        val start = gestationStart(edd) ?: return ""
        val d = ChronoUnit.DAYS.between(start, day)
        if (d < 0) return ""
        val w = d / 7
        val rest = d % 7
        return if (rest == 0L) "孕 $w 周" else "孕 $w 周 +$rest"
    }

    /** 「出生 5 个月」「2 岁 3 个月」；没出生返回空串。 */
    fun ageLabel(birth: LocalDate?, day: LocalDate): String {
        if (birth == null || day.isBefore(birth)) return ""
        val days = ChronoUnit.DAYS.between(birth, day)
        if (days < 31) return "出生 $days 天"
        val months = ChronoUnit.MONTHS.between(birth, day)
        if (months < 12) {
            val rest = ChronoUnit.DAYS.between(birth.plusMonths(months), day)
            return if (rest > 0) "${months} 个月 $rest 天" else "$months 个月"
        }
        val years = months / 12
        val m = months % 12
        return if (m == 0L) "$years 岁" else "$years 岁 $m 个月"
    }

    /** 绳子上那行小字：出生后看月龄，孕期看孕周，都没有就留空。 */
    fun stageLabel(edd: LocalDate?, birth: LocalDate?, day: LocalDate): String {
        val age = ageLabel(birth, day)
        if (age.isNotEmpty()) return age
        return gestationLabel(edd, day)
    }

    /** 11.21 */
    fun short(day: LocalDate): String = "%d.%02d".format(day.monthValue, day.dayOfMonth)

    fun short(ms: Long): String = short(dayOf(ms))

    /** 2026 年 11 月 21 日 · 周六 */
    fun long(day: LocalDate): String = "${day.year} 年 ${day.monthValue} 月 ${day.dayOfMonth} 日 · ${weekName(day)}"

    fun weekName(d: LocalDate): String = when (d.dayOfWeek) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }

    /** 距今还有几天：负数表示已经过去。 */
    fun daysFromToday(day: LocalDate): Long = ChronoUnit.DAYS.between(today(), day)

    /** 0:42 / 1:05:20 */
    fun duration(ms: Long): String {
        val total = (ms / 1000).toInt().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

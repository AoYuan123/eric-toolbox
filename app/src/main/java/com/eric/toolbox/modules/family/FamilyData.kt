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
    /**
     * 是不是实况照片：-1 还没判断过，0 是普通照片，1 是实况照片。
     * 用三态而不是 Boolean，是为了让升级前挂上去的老照片能被"补判"一次。
     */
    val live: Int = -1,
    /**
     * 手排顺序。0 = 没动过（按 atMs 排）；拖过一次之后，整条绳子会被重新编号成
     * 1000、2000、3000…（见 FamilyOrder.normalize）。
     *
     * 为什么不直接改 atMs 来排序：atMs 是"这张是哪天拍的"，是记录本身，
     * 挪一下就变成造假了。顺序是导演的事，日期是事实。
     */
    val ord: Long = 0L,
) {
    val isLive: Boolean get() = live == 1

    /** 排序用的键：动过手就听手的，没动过就按日期。 */
    val ropeKey: Long get() = if (ord > 0L) ord else atMs

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.name)
        put("file", file)
        put("text", text)
        put("at", atMs)
        put("made", madeAtMs)
        put("dur", durationMs)
        put("live", live)
        put("ord", ord)
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
            live = o.optInt("live", -1),
            ord = o.optLong("ord", 0L),
        )

        fun toJsonArray(list: List<Entry>): JSONArray = JSONArray().apply { list.forEach { put(it.toJson()) } }

        /** 单条坏数据不拖垮整张表——跳过它，其余照常显示。 */
        fun fromJsonArray(a: JSONArray): List<Entry> =
            (0 until a.length()).mapNotNull { runCatching { fromJson(a.getJSONObject(it)) }.getOrNull() }
    }
}

/**
 * 绳子上的顺序。
 *
 * 单独拎出来是因为这三步逻辑（排、按给定 id 重排、重新编号）最容易写错，
 * 而它又没有 Context 依赖，正好可以纯函数单测。
 */
object FamilyOrder {
    /** 重新编号的间隔。留出空档，将来要在两条之间插东西时不用整条重排。 */
    const val STEP = 1000L

    /** 按绳子顺序排：动过手听手的，没动过按日期；同键时用日期兜底，保证结果稳定。 */
    fun sorted(list: List<Entry>): List<Entry> =
        list.sortedWith(compareBy<Entry>({ it.ropeKey }, { it.atMs }, { it.id }))

    /**
     * 按 ids 给出的顺序重排。
     * ids 里没提到、但列表里存在的条目，按原顺序接在末尾——宁可位置不对，也不能把照片弄丢。
     */
    fun apply(list: List<Entry>, ids: List<String>): List<Entry> {
        val byId = list.associateBy { it.id }
        val out = ArrayList<Entry>(list.size)
        val used = HashSet<String>(list.size)
        for (id in ids) {
            val e = byId[id] ?: continue
            if (used.add(id)) out.add(e)
        }
        for (e in list) if (used.add(e.id)) out.add(e)
        return out
    }

    /**
     * 重新编号成 1000、2000、3000…
     * 每次落盘前都做一遍：不管中间经历了拖动、删除还是新增，
     * 存下来的顺序永远就是屏幕上的顺序。
     */
    fun normalize(list: List<Entry>): List<Entry> =
        list.mapIndexed { i, e ->
            val want = (i + 1) * STEP
            if (e.ord == want) e else e.copy(ord = want)
        }

    /** 现在是不是"完全按时间先后"排的。不是的话界面上给一个"按日期理顺"。 */
    fun isChronological(list: List<Entry>): Boolean {
        for (i in 1 until list.size) if (list[i - 1].atMs > list[i].atMs) return false
        return true
    }

    /** 新挂的一件该落在第几格：日期比它晚的第一件谁在前面，就插在它前面。 */
    fun insertIndex(list: List<Entry>, atMs: Long): Int {
        val i = list.indexOfFirst { it.atMs > atMs }
        return if (i < 0) list.size else i
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

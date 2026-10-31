package com.eric.toolbox.modules.datecalc

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 工作日判断：法定节假日 + 调休上班日（数据见 CalendarData），未覆盖的年份只按周末算。 */
object Workdays {
    private val off = HashSet<LocalDate>()
    private val work = HashSet<LocalDate>()

    init {
        for ((y, p) in CalendarData.holidays) {
            p.first.split(',').filter { it.length == 4 }.forEach { off += LocalDate.of(y, it.take(2).toInt(), it.drop(2).toInt()) }
            p.second.split(',').filter { it.length == 4 }.forEach { work += LocalDate.of(y, it.take(2).toInt(), it.drop(2).toInt()) }
        }
    }

    fun covered(year: Int) = year in CalendarData.HOLIDAY_FIRST_YEAR..CalendarData.HOLIDAY_LAST_YEAR

    fun isWorkday(d: LocalDate): Boolean = when {
        d in work -> true
        d in off -> false
        else -> d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY
    }

    /** 是否为调休上班日（周末上班）。 */
    fun isMakeupWorkday(d: LocalDate) = d in work

    /** 区间内（含两端）有哪些年份不在节假日数据覆盖范围内。 */
    fun uncoveredYears(a: LocalDate, b: LocalDate): List<Int> {
        val (lo, hi) = if (a <= b) a to b else b to a
        return (lo.year..hi.year).filter { !covered(it) }
    }
}

/** 农历（1901–2100）。 */
object Lunar {
    data class Date(val year: Int, val month: Int, val day: Int, val leap: Boolean) {
        override fun toString(): String = "${ganzhi(year)}年${if (leap) "闰" else ""}${monthName(month)}${dayName(day)}"
    }

    private class Year(val newYear: Long, val leap: Int, val lens: IntArray)

    private val years: List<Year> = CalendarData.lunar.map { line ->
        val p = line.split(',')
        Year(p[0].toLong(), p[1].toInt(), p[2].map { if (it == '1') 30 else 29 }.toIntArray())
    }

    private val GAN = "甲乙丙丁戊己庚辛壬癸"
    private val ZHI = "子丑寅卯辰巳午未申酉戌亥"
    private val MONTHS = arrayOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "腊月")
    private val TENS = arrayOf("初", "十", "廿", "三")
    private val DIGITS = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

    fun ganzhi(y: Int): String {
        val i = Math.floorMod(y - 4, 60)
        return "${GAN[i % 10]}${ZHI[i % 12]}"
    }

    fun monthName(m: Int) = MONTHS[m - 1]

    fun dayName(d: Int): String = when (d) {
        10 -> "初十"
        20 -> "二十"
        30 -> "三十"
        else -> TENS[d / 10] + DIGITS[d % 10]
    }

    /** 阳历转农历；超出 1901 年正月初一到 2100 年底的范围返回 null。 */
    fun of(d: LocalDate): Date? {
        val ed = d.toEpochDay()
        var idx = -1
        for (i in years.indices) if (years[i].newYear <= ed) idx = i else break
        if (idx < 0) return null
        val y = years[idx]
        var off = (ed - y.newYear).toInt()
        for ((i, len) in y.lens.withIndex()) {
            if (off < len) {
                // 第 i 个月：若有闰月且 i 在闰月之后，月份序号要减一
                val isLeap = y.leap > 0 && i == y.leap
                val month = if (y.leap > 0 && i >= y.leap + 1) i else if (isLeap) y.leap else i + 1
                return Date(CalendarData.LUNAR_FIRST_YEAR + idx, month, off + 1, isLeap)
            }
            off -= len
        }
        return null // 超出该农历年（只可能发生在 2100 年之后）
    }
}

object DateLogic {
    private val WEEK = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    fun weekName(d: LocalDate) = WEEK[d.dayOfWeek.value - 1]

    data class Diff(
        val days: Long,          // 带符号：结束早于开始时为负
        val weeks: Long,
        val restDays: Long,
        val months: Long,
        val monthRestDays: Long,
        val workdays: Long,
        val uncovered: List<Int>,
    )

    /**
     * 两个日期相差多少天。默认不含开始当天（与常见日期计算器一致：10-31 到 12-31 为 61 天），
     * includeStart 时多算一天。工作日按同样的区间计数。
     */
    fun diff(start: LocalDate, end: LocalDate, includeStart: Boolean): Diff {
        val forward = !end.isBefore(start)
        val (a, b) = if (forward) start to end else end to start
        var days = ChronoUnit.DAYS.between(a, b) + if (includeStart) 1 else 0
        val months = ChronoUnit.MONTHS.between(a, b)
        val monthRest = ChronoUnit.DAYS.between(a.plusMonths(months), b)
        var work = 0L
        var d = if (includeStart) a else a.plusDays(1)
        while (!d.isAfter(b)) {
            if (Workdays.isWorkday(d)) work++
            d = d.plusDays(1)
        }
        if (!forward) days = -days
        val abs = kotlin.math.abs(days)
        return Diff(days, abs / 7, abs % 7, months, monthRest, work, Workdays.uncoveredYears(a, b))
    }

    enum class Unit(val label: String, val name2: String) { DAY("日", "天"), WORKDAY("工作日", "个工作日"), WEEK("星期", "周"), MONTH("月", "个月") }

    /** 往后（n>0）或往前（n<0）推算。按月推算时，目标月没有对应日期取该月最后一天（如 1-31 加 1 个月 → 2-28/29）。 */
    fun add(base: LocalDate, n: Long, unit: Unit): LocalDate = when (unit) {
        Unit.DAY -> base.plusDays(n)
        Unit.WEEK -> base.plusWeeks(n)
        Unit.MONTH -> base.plusMonths(n)
        Unit.WORKDAY -> {
            var d = base
            var left = kotlin.math.abs(n)
            val step = if (n >= 0) 1L else -1L
            while (left > 0) {
                d = d.plusDays(step)
                if (Workdays.isWorkday(d)) left--
            }
            d
        }
    }
}

package com.eric.toolbox.modules.datecalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 期望值来源：节假日按《国务院办公厅关于2026年部分节假日安排的通知》；
 * 农历用 ICU 中国历法（Node Intl zh-CN-u-ca-chinese）逐日核对，1901–2100 年每 13 天抽样 5616 个日期全部一致。
 */
class DateLogicTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun diffMatchesPreview() {
        val r = DateLogic.diff(d("2026-10-31"), d("2026-12-31"), includeStart = false)
        assertEquals(61, r.days); assertEquals(8, r.weeks); assertEquals(5, r.restDays)
        assertEquals(2, r.months); assertEquals(0, r.monthRestDays)
        assertEquals(44, r.workdays)
        assertEquals(62, DateLogic.diff(d("2026-10-31"), d("2026-12-31"), includeStart = true).days)
        assertEquals(-61, DateLogic.diff(d("2026-12-31"), d("2026-10-31"), includeStart = false).days)
    }

    @Test fun officialHolidays2026() {
        // 春节 2/15–2/23 放假，2/14（周六）、2/28（周六）上班；国庆 10/1–10/7 放假，9/20（周日）、10/10（周六）上班
        assertFalse(Workdays.isWorkday(d("2026-02-16")))
        assertFalse(Workdays.isWorkday(d("2026-02-23")))
        assertTrue(Workdays.isWorkday(d("2026-02-14")))
        assertTrue(Workdays.isWorkday(d("2026-02-28")))
        assertFalse(Workdays.isWorkday(d("2026-10-05")))
        assertTrue(Workdays.isWorkday(d("2026-09-20")))
        assertTrue(Workdays.isWorkday(d("2026-10-10")))
        assertTrue(Workdays.isMakeupWorkday(d("2026-10-10")))
        assertFalse(Workdays.isWorkday(d("2026-10-31"))) // 普通周六
    }

    @Test fun crossYearMakeupDay() {
        // 2019 年元旦调休：2018-12-29（周六）上班（这条数据在 2019 年的通知里）
        assertTrue(Workdays.isWorkday(d("2018-12-29")))
        assertFalse(Workdays.isWorkday(d("2018-12-31")))
    }

    @Test fun addMatchesPreview() {
        val base = d("2026-10-31")
        assertEquals(d("2026-11-10"), DateLogic.add(base, 10, DateLogic.Unit.DAY))
        assertEquals(d("2026-11-13"), DateLogic.add(base, 10, DateLogic.Unit.WORKDAY))
        assertEquals(d("2027-01-09"), DateLogic.add(base, 10, DateLogic.Unit.WEEK))
        assertEquals(d("2027-08-31"), DateLogic.add(base, 10, DateLogic.Unit.MONTH))
        assertEquals(d("2026-10-21"), DateLogic.add(base, -10, DateLogic.Unit.DAY))
        assertEquals(d("2026-10-19"), DateLogic.add(base, -10, DateLogic.Unit.WORKDAY))
        assertEquals(d("2026-08-22"), DateLogic.add(base, -10, DateLogic.Unit.WEEK))
        assertEquals(d("2025-12-31"), DateLogic.add(base, -10, DateLogic.Unit.MONTH))
        // 跨国庆：9/28 往后 5 个工作日 → 10/10（周六调休上班）
        assertEquals(d("2026-10-10"), DateLogic.add(d("2026-09-28"), 5, DateLogic.Unit.WORKDAY))
        assertEquals(d("2026-02-28"), DateLogic.add(d("2026-01-31"), 1, DateLogic.Unit.MONTH))
    }

    @Test fun lunar() {
        assertEquals("丙午年九月廿二", Lunar.of(d("2026-10-31")).toString())
        assertEquals("丙午年十一月廿三", Lunar.of(d("2026-12-31")).toString())
        assertEquals("乙巳年腊月廿八", Lunar.of(d("2026-02-15")).toString())
        assertEquals("丙午年正月初七", Lunar.of(d("2026-02-23")).toString())
        assertEquals("丙午年正月初一", Lunar.of(d("2026-02-17")).toString())
        assertEquals("癸卯年闰二月初一", Lunar.of(d("2023-03-22")).toString())
        assertEquals("乙巳年闰六月初一", Lunar.of(d("2025-07-25")).toString())
        assertEquals("丁未年七月三十", Lunar.of(d("2027-08-31")).toString())
        assertNull(Lunar.of(d("1900-06-01")))
        assertNull(Lunar.of(d("2101-06-01")))
    }

    @Test fun uncoveredYears() {
        assertEquals(listOf(2027), Workdays.uncoveredYears(d("2026-12-01"), d("2027-01-31")))
        assertTrue(Workdays.uncoveredYears(d("2026-01-01"), d("2026-12-31")).isEmpty())
    }
}

package com.eric.toolbox.modules.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 时间表的日期算术。这块最容易错，而且错了不会崩，只会悄悄把 NT 排到 20 周——
 * 所以宁可把锚点、排序、筛选都钉一遍。
 */
class FamilyPlanTest {

    private val edd = LocalDate.of(2027, 2, 14)
    private val start = edd.minusDays(280) // 孕 0 周 0 天

    @Test
    fun `孕期条目按预产期倒推`() {
        val nt = FamilyPlan.ALL.first { it.key == "g_nt" }
        assertEquals(start.plusDays(12 * 7L), FamilyPlan.dueOf(nt, edd, null))

        val eddItem = FamilyPlan.ALL.first { it.key == "g_edd" }
        assertEquals(edd, FamilyPlan.dueOf(eddItem, edd, null))
    }

    @Test
    fun `出生后条目按生日算 没出生时按预产期估`() {
        val first = FamilyPlan.ALL.first { it.key == "b_2m" }
        assertEquals(edd.plusMonths(2), FamilyPlan.dueOf(first, edd, null))

        val birth = LocalDate.of(2027, 2, 28)
        assertEquals(birth.plusMonths(2), FamilyPlan.dueOf(first, edd, birth))
    }

    @Test
    fun `没有起点就没有表`() {
        assertTrue(FamilyPlan.rows(null, null, emptySet()).isEmpty())
        assertTrue(FamilyPlan.rows(edd, null, emptySet()).isNotEmpty())
    }

    @Test
    fun `整张表按日期升序 且每一条都能落到某一章`() {
        val rows = FamilyPlan.rows(edd, null, emptySet())
        assertEquals(FamilyPlan.ALL.size, rows.size)

        val sorted = rows.sortedBy { it.due }
        assertEquals(sorted.map { it.item.key }, rows.map { it.item.key })

        val grouped = FamilyPlan.groups(rows)
        assertEquals(rows.size, grouped.sumOf { it.second.size })
        assertTrue(grouped.isNotEmpty())
    }

    @Test
    fun `纸片只往前看三十天 且不追过期的`() {
        val rows = FamilyPlan.rows(edd, null, emptySet())
        val anchor = rows.first { it.item.key == "g_anomaly" }.due

        // 大排畸前 20 天：应该就是它
        val early = FamilyPlan.next(rows, anchor.minusDays(20))
        assertEquals("g_anomaly", early?.item?.key)

        // 大排畸过去 10 天：超过 7 天宽限，不再挂在纸片上
        val late = FamilyPlan.next(rows, anchor.plusDays(10))
        assertTrue(late == null || late.item.key != "g_anomaly")
    }

    @Test
    fun `划掉的不会再冒出来`() {
        val rows = FamilyPlan.rows(edd, null, emptySet())
        val anchor = rows.first { it.item.key == "g_anomaly" }.due
        val day = anchor.minusDays(2)

        val before = FamilyPlan.next(rows, day)
        assertEquals("g_anomaly", before?.item?.key)

        val done = FamilyPlan.rows(edd, null, setOf("g_anomaly"))
        assertTrue(FamilyPlan.next(done, day)?.item?.key != "g_anomaly")
    }

    @Test
    fun `通知只提必须和建议 不催知道就行的`() {
        val rows = FamilyPlan.rows(edd, null, emptySet())
        val eddRow = rows.first { it.item.key == "g_edd" }
        val bumps = FamilyPlan.bumps(rows, eddRow.due)
        assertTrue(bumps.none { it.item.key == "g_edd" })
    }

    @Test
    fun `通知命中的是当天和三日后`() {
        val rows = FamilyPlan.rows(edd, null, emptySet())
        val target = rows.first { it.item.key == "g_ogtt" }
        val hits = FamilyPlan.bumps(rows, target.due)
        assertEquals("g_ogtt", hits.first().item.key)
        assertTrue(hits.all { r -> ChronoUnit.DAYS.between(target.due, r.due) in 0L..3L })
    }

    @Test
    fun `同一件事的日期随预产期整体平移`() {
        val a = FamilyPlan.rows(edd, null, emptySet())
        val b = FamilyPlan.rows(edd.plusDays(7), null, emptySet())
        val x = a.first { it.item.key == "g_anomaly" }.due
        val y = b.first { it.item.key == "g_anomaly" }.due
        assertEquals(7L, ChronoUnit.DAYS.between(x, y))
    }

    @Test
    fun `key 不重复 写进存储不会互相覆盖`() {
        val keys = FamilyPlan.ALL.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `距离文案`() {
        val today = LocalDate.of(2026, 11, 19)
        assertEquals("今天", FamilyPlan.daysLabel(today, today))
        assertEquals("明天", FamilyPlan.daysLabel(today.plusDays(1), today))
        assertEquals("还有 5 天", FamilyPlan.daysLabel(today.plusDays(5), today))
        assertEquals("昨天", FamilyPlan.daysLabel(today.minusDays(1), today))
        assertEquals("过了 3 天", FamilyPlan.daysLabel(today.minusDays(3), today))
    }

    @Test
    fun `既没预产期也没生日时 next 不会崩`() {
        assertNull(FamilyPlan.next(emptyList(), LocalDate.of(2026, 11, 19)))
    }
}

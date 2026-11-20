package com.eric.toolbox.modules.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 绳子上的顺序。
 *
 * 这里最容易错的两件事：拖完顺序该落成什么、以及"没提到的那条会不会被弄丢"。
 * 图上摆错是小事，丢一张照片是大事——所以 apply 的兜底单独测。
 */
class FamilyOrderTest {

    private fun e(id: String, day: Int) = Entry(
        id = id,
        kind = Kind.PHOTO,
        file = "$id.jpg",
        atMs = day.toLong() * 86_400_000L,
    )

    private val a = e("a", 10)
    private val b = e("b", 11)
    private val c = e("c", 12)

    @Test
    fun `没动过手时按日期排`() {
        val out = FamilyOrder.sorted(listOf(c, a, b))
        assertEquals(listOf("a", "b", "c"), out.map { it.id })
    }

    @Test
    fun `动过手以后听手的`() {
        // b 被拖到了最前面：ord 比谁都小
        val moved = listOf(c, b.copy(ord = 500L), a)
        val out = FamilyOrder.sorted(moved)
        assertEquals(listOf("b", "c", "a"), out.map { it.id })
    }

    @Test
    fun `按给定顺序重排`() {
        val out = FamilyOrder.apply(listOf(a, b, c), listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), out.map { it.id })
    }

    @Test
    fun `ids 里漏掉的条目接在末尾而不是丢掉`() {
        val out = FamilyOrder.apply(listOf(a, b, c), listOf("c"))
        assertEquals(3, out.size)
        assertEquals("c", out[0].id)
        // 顺序次要，一条都不能少
        assertEquals(setOf("a", "b", "c"), out.map { it.id }.toSet())
    }

    @Test
    fun `ids 里有不存在的 id 时跳过`() {
        val out = FamilyOrder.apply(listOf(a, b), listOf("zzz", "b", "a"))
        assertEquals(listOf("b", "a"), out.map { it.id })
    }

    @Test
    fun `重新编号成固定的间隔`() {
        val out = FamilyOrder.normalize(listOf(c, a, b))
        assertEquals(listOf(1000L, 2000L, 3000L), out.map { it.ord })
    }

    @Test
    fun `重新编号后排序结果不变`() {
        val once = FamilyOrder.normalize(listOf(c, a, b))
        assertEquals(listOf("c", "a", "b"), FamilyOrder.sorted(once).map { it.id })
    }

    @Test
    fun `什么算按时间先后`() {
        assertTrue(FamilyOrder.isChronological(listOf(a, b, c)))
        assertFalse(FamilyOrder.isChronological(listOf(b, a)))
        // 同一天挂着两件不算乱
        assertTrue(FamilyOrder.isChronological(listOf(a, b.copy(atMs = a.atMs))))
        assertTrue(FamilyOrder.isChronological(emptyList()))
    }

    @Test
    fun `补一天时插到那天的位置`() {
        val list = listOf(a, b, c)
        // 落在 a、b 之间的一天：挂到 b 前面
        assertEquals(1, FamilyOrder.insertIndex(list, a.atMs + 3_600_000L))
        // 比谁都新就挂到最后
        assertEquals(3, FamilyOrder.insertIndex(list, e("new", 20).atMs))
        // 比谁都旧就挂到最前
        assertEquals(0, FamilyOrder.insertIndex(list, e("old", 1).atMs))
    }
}

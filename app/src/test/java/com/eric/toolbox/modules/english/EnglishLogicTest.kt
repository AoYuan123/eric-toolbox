package com.eric.toolbox.modules.english

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishLogicTest {
    private val day = 86_400_000L

    @Test
    fun goodGrowsInterval() {
        var c = EnglishLogic.Card("ip:claim", "ip", "claim")
        c = EnglishLogic.schedule(c, EnglishLogic.GOOD, 0)
        assertEquals(1.0, c.ivlDays, 1e-9); assertEquals(day, c.due)
        c = EnglishLogic.schedule(c, EnglishLogic.GOOD, c.due)
        assertEquals(4.0, c.ivlDays, 1e-9)
        c = EnglishLogic.schedule(c, EnglishLogic.GOOD, c.due)
        assertEquals(10.0, c.ivlDays, 1e-9) // 4 × 2.5
    }

    @Test
    fun againResetsAndLowersEase() {
        var c = EnglishLogic.Card("ip:x", "ip", "x", ivlDays = 10.0, reps = 3)
        c = EnglishLogic.schedule(c, EnglishLogic.AGAIN, 1000)
        assertEquals(0.0, c.ivlDays, 1e-9); assertEquals(1000 + 600_000, c.due); assertEquals(1, c.lapses)
        assertEquals(2.3, c.ease, 1e-9)
    }

    @Test
    fun queuePrefersDueThenNewWithinLimit() {
        val now = 10 * day
        val due = EnglishLogic.Card("ip:a", "ip", "a", ivlDays = 1.0, due = now - 1, reps = 1)
        val notDue = EnglishLogic.Card("ip:b", "ip", "b", ivlDays = 3.0, due = now + day, reps = 2)
        val pool = (1..20).map { EnglishLogic.Card("daily:n$it", "daily", "n$it") }
        val q = EnglishLogic.queue(mapOf(due.id to due, notDue.id to notDue), pool, newToday = 7, newLimit = 10, now = now)
        assertEquals("ip:a", q.first().id)
        assertEquals(1 + 3, q.size)
    }

    @Test
    fun streakCountsBack() {
        val now = 100 * day + 3_600_000
        val days = setOf(EnglishLogic.dayNo(now), EnglishLogic.dayNo(now - day), EnglishLogic.dayNo(now - 2 * day), EnglishLogic.dayNo(now - 4 * day))
        assertEquals(3, EnglishLogic.streak(days, now))
        assertEquals(2, EnglishLogic.streak(days - EnglishLogic.dayNo(now), now)) // 今天还没学：从昨天数
    }

    @Test
    fun clozeAndSpell() {
        assertEquals("We handle the p__________ of this application.", EnglishLogic.cloze("We handle the prosecution of this application.", "prosecution"))
        assertNull(EnglishLogic.cloze("No match here.", "claim"))
        assertTrue(EnglishLogic.spellOk("  Prior  Art ", "prior art"))
        assertFalse(EnglishLogic.spellOk("prior arts", "prior art"))
    }

    @Test
    fun booksAreClean() {
        val all = EnglishBooks.ip + EnglishBooks.daily
        assertEquals(all.size, all.map { it.key }.toSet().size) // 没有重复词条
        all.forEach { assertTrue(it.zh.isNotBlank() && it.ex.isNotBlank() && it.exZh.isNotBlank()) }
        assertTrue(EnglishBooks.ip.size >= 120)
    }
}

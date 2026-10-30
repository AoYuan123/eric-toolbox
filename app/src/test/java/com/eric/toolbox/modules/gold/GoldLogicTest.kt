package com.eric.toolbox.modules.gold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TierLogicTest {
    private val start = 850
    private val step = 10
    private val init = TierLogic.initial(start, step) // 860

    @Test fun aboveStartIsSilent() {
        val d = TierLogic.evaluate(852.0, init, start, step)
        assertNull(d.alertTier); assertEquals(init, d.newLastAlerted)
    }

    @Test fun firstBreakAlerts850() {
        val d = TierLogic.evaluate(846.9, init, start, step)
        assertEquals(850, d.alertTier); assertEquals(850, d.newLastAlerted); assertEquals(1, d.crossed)
    }

    @Test fun exactlyOnTierCounts() {
        assertEquals(850, TierLogic.evaluate(850.0, init, start, step).alertTier)
        assertEquals(830, TierLogic.evaluate(830.0, 840, start, step).alertTier)
    }

    @Test fun exampleFromSpec_826gives830() {
        assertEquals(830, TierLogic.evaluate(826.0, init, start, step).alertTier)
    }

    @Test fun sameTierNotRepeated() {
        val d = TierLogic.evaluate(845.0, 850, start, step)
        assertNull(d.alertTier); assertEquals(850, d.newLastAlerted)
    }

    @Test fun gapDownCountsCrossedTiers() {
        val d = TierLogic.evaluate(835.0, init, start, step)
        assertEquals(840, d.alertTier); assertEquals(2, d.crossed)
    }

    @Test fun oscillationAroundTierDoesNotReset() {
        // 已提醒 840，价格在 841~849 之间来回，不重置也不提醒
        for (p in listOf(841.0, 848.0, 849.9, 850.0)) {
            val d = TierLogic.evaluate(p, 840, start, step)
            assertNull(d.alertTier); assertEquals(840, d.newLastAlerted); assertFalse(d.reset)
        }
    }

    @Test fun reboundResetsToTierAbovePrice() {
        // 规格示例：P=845 → L=850；P=851 → L=860
        assertEquals(850, TierLogic.evaluate(845.0, 830, start, step).newLastAlerted)
        assertEquals(860, TierLogic.evaluate(851.0, 840, start, step).newLastAlerted)
        assertTrue(TierLogic.evaluate(845.0, 830, start, step).reset)
    }

    @Test fun afterResetCanAlertAgain() {
        val l = TierLogic.evaluate(845.0, 830, start, step).newLastAlerted // 850
        assertEquals(840, TierLogic.evaluate(839.0, l, start, step).alertTier)
    }

    @Test fun nextTierDisplay() {
        assertEquals(850, TierLogic.nextTier(init, start, step))
        assertEquals(830, TierLogic.nextTier(840, start, step))
    }
}

class SessionTest {
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    @Test fun sessions() {
        // 2026-10-30 是周五
        assertTrue(Session.isTrading(at(2026, 10, 30, 10, 0)))
        assertFalse(Session.isTrading(at(2026, 10, 30, 16, 0)))
        assertTrue(Session.isTrading(at(2026, 10, 30, 21, 0)))
        assertTrue(Session.isTrading(at(2026, 10, 31, 1, 0)))   // 周五夜盘延续到周六凌晨
        assertFalse(Session.isTrading(at(2026, 10, 31, 10, 0))) // 周六白天休市
        assertFalse(Session.isTrading(at(2026, 11, 2, 1, 0)))   // 周一凌晨（周日无夜盘）
    }
}

class ParserTest {
    // 2026-10-30 21:43 实测返回（节选）
    private val em = """{"rc":0,"data":{"total":2,"diff":[{"f1":2,"f2":84690,"f12":"AU9999","f15":85500,"f16":84100,"f18":85476,"f124":1793367808},{"f1":2,"f2":84170,"f12":"AUTD","f15":85100,"f16":84070,"f18":85458,"f124":1793367812}]}}"""
    private val sina = "var hq_str_nf_AU0=\"黄金连续,214321,849.080,849.280,842.200,0.000,843.160,843.180,843.140,0.000,861.900,2,21,183098.000,54814,沪,黄金,2026-10-30,1\";"

    @Test fun eastmoney() {
        val q = GoldSource.parseEastmoney(em)
        assertEquals(2, q.size)
        assertEquals(846.90, q[0].price, 1e-9)
        assertEquals(854.76, q[0].prevClose!!, 1e-9)
        assertEquals(1793367808000L, q[0].timeMs)
    }

    @Test fun eastmoneyDashMeansNoData() {
        val q = GoldSource.parseEastmoney("""{"data":{"diff":[{"f1":2,"f2":"-","f12":"AU9999"}]}}""")
        assertTrue(q.isEmpty())
    }

    @Test fun sinaParse() {
        val q = GoldSource.parseSina(sina)
        assertNotNull(q); assertEquals(843.14, q!!.price, 1e-9)
    }

    @Test fun verificationAndFreshness() {
        val q = GoldSource.parseEastmoney(em)
        val now = 1793367808000L + 60_000
        val ok = GoldSource.buildSnapshot(q[0], listOf(q[1]), now)
        assertTrue(ok.verified); assertTrue(ok.fresh); assertTrue(ok.usableForAlert)

        val dirty = q[0].copy(price = 910.0) // 当天遇到过的脏数据
        assertFalse(GoldSource.buildSnapshot(dirty, listOf(q[1]), now).verified)

        val stale = GoldSource.buildSnapshot(q[0], listOf(q[1]), now + 3600_000)
        assertFalse(stale.fresh); assertFalse(stale.usableForAlert)
    }
}

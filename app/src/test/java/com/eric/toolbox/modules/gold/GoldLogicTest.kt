package com.eric.toolbox.modules.gold

import com.eric.toolbox.core.RunWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
    Calendar.getInstance(BJ).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

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
        for (p in listOf(841.0, 848.0, 849.9, 850.0)) {
            val d = TierLogic.evaluate(p, 840, start, step)
            assertNull(d.alertTier); assertEquals(840, d.newLastAlerted); assertFalse(d.reset)
        }
    }

    @Test fun reboundResetsToTierAbovePrice() {
        assertEquals(850, TierLogic.evaluate(845.0, 830, start, step).newLastAlerted)
        assertEquals(860, TierLogic.evaluate(851.0, 840, start, step).newLastAlerted)
        assertTrue(TierLogic.evaluate(845.0, 830, start, step).reset)
    }

    @Test fun afterResetCanAlertAgain() {
        val l = TierLogic.evaluate(845.0, 830, start, step).newLastAlerted // 850
        assertEquals(840, TierLogic.evaluate(839.0, l, start, step).alertTier)
    }

    /** 10/30 晚的真实情况：两次查询之间大盘最低 836（22:24），当前已回到 840.09，也要提醒 840。 */
    @Test fun dipBetweenPollsIsCaught() {
        val d = TierLogic.evaluate(836.0, 850, start, step, current = 840.09)
        assertEquals(840, d.alertTier)
    }

    @Test fun nextTierDisplay() {
        assertEquals(850, TierLogic.nextTier(init, start, step))
        assertEquals(830, TierLogic.nextTier(840, start, step))
    }
}

class RiseAndIntervalTest {
    @Test fun riseFiresOnceAndRearms() {
        var r = RiseLogic.evaluate(1001.0, 1000.0, armed = true, rearm = 10.0)
        assertTrue(r.fire); assertFalse(r.armed)
        r = RiseLogic.evaluate(1005.0, 1000.0, armed = r.armed, rearm = 10.0)
        assertFalse(r.fire)
        r = RiseLogic.evaluate(995.0, 1000.0, armed = r.armed, rearm = 10.0)
        assertFalse(r.armed) // 只回落 5 元，不重新生效
        r = RiseLogic.evaluate(989.0, 1000.0, armed = r.armed, rearm = 10.0)
        assertTrue(r.armed)
        assertFalse(RiseLogic.evaluate(2000.0, 0.0, true, 10.0).fire) // 0 表示关闭
    }

    @Test fun smartInterval() {
        assertEquals(180, SmartInterval.seconds(12.0, 30))
        assertEquals(60, SmartInterval.seconds(5.0, 30))
        assertEquals(15, SmartInterval.seconds(2.0, 15))
        assertEquals(30, SmartInterval.seconds(-1.0, 30))
    }
}

class SessionTest {
    @Test fun marketSessions() {
        // 2026-10-30 是周五
        assertTrue(Session.marketTrading(at(2026, 10, 30, 10, 0)))
        assertFalse(Session.marketTrading(at(2026, 10, 30, 16, 0)))
        assertTrue(Session.marketTrading(at(2026, 10, 30, 21, 0)))
        assertTrue(Session.marketTrading(at(2026, 10, 31, 1, 0)))   // 周五夜盘延续到周六凌晨
        assertFalse(Session.marketTrading(at(2026, 10, 31, 10, 0))) // 周六白天休市
        assertFalse(Session.marketTrading(at(2026, 11, 2, 1, 0)))   // 周一凌晨（周日无夜盘）
    }

    @Test fun bankSessions() {
        assertFalse(Session.bankTrading(at(2026, 10, 30, 9, 5)))
        assertTrue(Session.bankTrading(at(2026, 10, 30, 9, 10)))
        assertTrue(Session.bankTrading(at(2026, 10, 30, 22, 29)))
        assertFalse(Session.bankTrading(at(2026, 10, 30, 22, 30)))
        assertFalse(Session.bankTrading(at(2026, 10, 31, 12, 0)))
    }

    @Test fun marketDayStart() {
        assertEquals(at(2026, 10, 30, 20, 0), Session.marketDayStart(at(2026, 10, 30, 22, 0)))
        assertEquals(at(2026, 10, 30, 20, 0), Session.marketDayStart(at(2026, 10, 31, 1, 0)))
        assertEquals(at(2026, 10, 30, 20, 0), Session.marketDayStart(at(2026, 11, 2, 10, 0))) // 周一日盘属于上周五夜盘开始的交易日
    }

    @Test fun axis() {
        assertEquals(0, Session.marketAxisMinute(at(2026, 10, 30, 20, 0)))
        assertEquals(390, Session.marketAxisMinute(at(2026, 10, 31, 2, 30)))
        assertEquals(390, Session.marketAxisMinute(at(2026, 11, 2, 9, 0)))
        assertEquals(780, Session.marketAxisMinute(at(2026, 11, 2, 15, 30)))
        assertNull(Session.marketAxisMinute(at(2026, 11, 2, 17, 0)))
    }
}

class RunWindowTest {
    private val s = 8 * 60
    private val e = 23 * 60

    @Test fun window() {
        assertTrue(RunWindow.isOpen(at(2026, 10, 30, 8, 0), s, e, true))
        assertTrue(RunWindow.isOpen(at(2026, 10, 30, 22, 59), s, e, true))
        assertFalse(RunWindow.isOpen(at(2026, 10, 30, 23, 0), s, e, true))
        assertFalse(RunWindow.isOpen(at(2026, 10, 30, 7, 59), s, e, true))
        assertFalse(RunWindow.isOpen(at(2026, 10, 31, 10, 0), s, e, true)) // 周六
        assertTrue(RunWindow.isOpen(at(2026, 10, 31, 10, 0), s, e, false))
    }

    @Test fun nextOpenSkipsWeekend() {
        assertEquals(at(2026, 11, 2, 8, 0), RunWindow.nextOpen(at(2026, 10, 30, 23, 30), s, true))
        assertEquals(at(2026, 10, 30, 8, 0), RunWindow.nextOpen(at(2026, 10, 30, 7, 0), s, true))
        assertEquals(at(2026, 10, 31, 8, 0), RunWindow.nextOpen(at(2026, 10, 30, 23, 30), s, false))
    }
}

class LedgerTest {
    @Test fun movingAverage() {
        val t = listOf(
            Trade(1, true, "2026-10-01", 10.0, 900.0, 45.0),   // 成本 9045
            Trade(2, true, "2026-10-15", 10.0, 880.0, 44.0),   // 成本 9045+8844=17889，20 克
            Trade(3, false, "2026-10-28", 5.0, 870.0, 21.75),  // 均价 894.45，卖 5 克
        )
        val h = LedgerMath.summarize(t)
        assertEquals(15.0, h.grams, 1e-9)
        assertEquals(17889.0 * 15 / 20, h.cost, 1e-6)
        assertEquals(894.45, h.avgCost, 1e-9)
        assertEquals(5 * 870.0 - 21.75 - 894.45 * 5, h.realized, 1e-6)
    }

    @Test fun sellAllClearsCost() {
        val h = LedgerMath.summarize(listOf(Trade(1, true, "2026-10-01", 2.0, 800.0, 0.0), Trade(2, false, "2026-10-02", 2.0, 820.0, 0.0)))
        assertEquals(0.0, h.grams, 1e-9); assertEquals(0.0, h.cost, 1e-9); assertEquals(40.0, h.realized, 1e-9)
    }
}

class ParserTest {
    // 2026-10-30 实测返回（节选）
    private val em = """{"rc":0,"data":{"total":2,"diff":[{"f1":2,"f2":84690,"f12":"AU9999","f15":85500,"f16":84100,"f17":85100,"f18":85476,"f124":1793367808},{"f1":2,"f2":84170,"f12":"AUTD","f15":85100,"f16":84070,"f17":85100,"f18":85458,"f124":1793367812}]}}"""
    private val sina = "var hq_str_nf_AU0=\"黄金连续,214321,849.080,849.280,842.200,0.000,843.160,843.180,843.140,0.000,861.900,2,21,183098.000,54814,沪,黄金,2026-10-30,1\";"
    private val icbc = """
        <tr height="42px">
          <td align="center" id="prodnoo_080020000521">积存金</td>
          <td align="center" id="activeprice_080020000521">836.10</td>
          <td id="updown1_080020000521" align="center"><img src="/x.png" alt="跌"></td>
          <td align="center" id="lowprice_080020000521">835.47</td>
          <td align="center" id="highprice_080020000521">860.39</td>
          <td align="center" id="regprice_080020000521">857.40</td>
          <td align="center" id="sellprice_080020000521">836.10</td>
        </tr>
        <tr height="42px"><td id="prodnoo_080020000501">如意金积存</td><td id="activeprice_080020000501">855.11</td><td>---</td><td>---</td><td>---</td><td>---</td><td>---</td><td id="sellprice_080020000501">834.74</td><td>---</td></tr>
        </tbody><tfoot><tr><td colspan="10" style="text-align:right;height:48px" id="time">更新时间:2026-10-30 22:23:34</td></tr></tfoot>
    """.trimIndent()
    private val kline = """{"rc":0,"data":{"code":"AU9999","klines":["2026-10-30 22:23,840.10,840.10,840.10,840.10","2026-10-30 22:24,836.00,836.00,836.00,836.00"]}}"""

    @Test fun eastmoney() {
        val q = GoldSource.parseEastmoney(em)
        assertEquals(2, q.size)
        assertEquals(846.90, q[0].price, 1e-9)
        assertEquals(854.76, q[0].prevClose!!, 1e-9)
        assertEquals(851.00, q[0].open!!, 1e-9)
        assertEquals(1793367808000L, q[0].timeMs)
    }

    @Test fun eastmoneyDashMeansNoData() {
        assertTrue(GoldSource.parseEastmoney("""{"data":{"diff":[{"f1":2,"f2":"-","f12":"AU9999"}]}}""").isEmpty())
    }

    @Test fun sinaParse() {
        val q = GoldSource.parseSina(sina)
        assertNotNull(q); assertEquals(843.14, q!!.price, 1e-9)
    }

    @Test fun icbcParse() {
        val b = GoldSource.parseIcbc(icbc)
        assertEquals(836.10, b.jcj!!, 1e-9)
        assertEquals(860.39, b.jcjHigh!!, 1e-9)
        assertEquals(835.47, b.jcjLow!!, 1e-9)
        assertEquals(855.11, b.ryBuy!!, 1e-9)
        assertEquals(834.74, b.rySell!!, 1e-9)
        assertEquals(at(2026, 10, 30, 22, 23) + 34_000, b.timeMs)
    }

    @Test fun klineParse() {
        val k = GoldSource.parseKlines(kline, minute = true)
        assertEquals(2, k.size); assertEquals(836.0, k[1].close, 1e-9)
        assertEquals(at(2026, 10, 30, 22, 24), k[1].timeMs)
    }

    @Test fun cleanClampsAbsurdLows() {
        val c = GoldSource.clean(listOf(Bar("2016-03", 0, 258.0, 255.7, 268.0, 181.2)))
        assertEquals(255.7 * 0.9, c[0].low, 1e-9)
        assertEquals(268.0, c[0].high, 1e-9) // 正常的高价不受影响
    }

    @Test fun verificationAndFreshness() {
        val q = GoldSource.parseEastmoney(em)
        val b = GoldSource.parseIcbc(icbc)
        val now = 1793367808000L + 60_000
        val ok = Snapshot(q[0], q[1], null, b, now, emptyList())
        assertTrue(ok.marketVerified()); assertTrue(ok.marketFresh())
        assertTrue(ok.bankVerified())

        val dirty = ok.copy(market = q[0].copy(price = 910.0)) // 当天遇到过的脏数据
        assertFalse(dirty.marketVerified())

        val stale = ok.copy(fetchedAtMs = now + 3600_000)
        assertFalse(stale.marketFresh()); assertFalse(stale.bankFresh())
    }
}

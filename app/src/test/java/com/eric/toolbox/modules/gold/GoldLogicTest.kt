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

class NightAndKlineTest {
    @Test fun nightMergeKeepsLowHighAndLast() {
        var n = NightLogic.merge(null, 838.0, 839.0, 838.5, 1L, "大盘")
        n = NightLogic.merge(n, 826.0, 830.0, 829.0, 2L, "大盘")
        n = NightLogic.merge(n, 828.0, 832.0, 829.5, 3L, "大盘")
        assertEquals(826.0, n.low, 1e-9); assertEquals(2L, n.lowT)
        assertEquals(839.0, n.high, 1e-9); assertEquals(1L, n.highT)
        assertEquals(829.5, n.last, 1e-9); assertEquals(3L, n.lastT)
    }

    @Test fun nightSummaryTiers() {
        // 已提醒到 850，夜间最低 826、现价 829：一次跨过 840、830 两档
        val d = TierLogic.evaluate(826.0, 850, 850, 10, current = 829.0)
        assertEquals(830, d.alertTier); assertEquals(2, d.crossed)
        assertEquals(listOf(840, 830), NightLogic.tiers(d.alertTier!!, d.crossed, 10))
        assertEquals(listOf(850), NightLogic.tiers(850, 1, 10))
    }

    // 格式与 10-31 实测新浪沪金主连一致。10-31 02:30、10-29、10-30 和 11-02 00:00（接口里真实出现的未来时间异常条目）是实测值，其余两条为构造数据
    private val sinaMin = """var _k=([{"d":"2026-10-31 02:29:00","o":"831.500","h":"831.700","l":"831.300","c":"831.480","p":"181480","v":"20"},{"d":"2026-10-31 02:30:00","o":"831.480","h":"831.900","l":"831.380","c":"831.620","p":"181476","v":"18"},{"d":"2026-11-02 00:00:00","o":"836.780","h":"836.900","l":"836.580","c":"836.620","p":"181301","v":"69"}]);"""
    private val sinaDay = """var _k=([{"d":"2026-09-30","o":"880.000","h":"881.000","l":"870.000","c":"875.000","p":"1","s":"0","v":"1"},{"d":"2026-10-29","o":"871.720","h":"875.160","l":"864.460","c":"867.260","p":"185349","s":"869.540","v":"184566"},{"d":"2026-10-30","o":"864.580","h":"867.960","l":"854.720","c":"855.180","p":"184420","s":"861.900","v":"237318"}]);"""

    @Test fun parseSinaMinuteDropsFutureBars() {
        val bars = KlineRepo.parseSina(sinaMin, minute = true, now = at(2026, 10, 31, 20, 20))
        assertEquals(2, bars.size)
        assertEquals("2026-10-31 02:30", bars[1].label)
        assertEquals(at(2026, 10, 31, 2, 30), bars[1].timeMs)
        assertEquals(831.62, bars[1].close, 1e-9)
    }

    @Test fun parseSinaDailyAndMonthly() {
        val d = KlineRepo.parseSina(sinaDay, minute = false, now = at(2026, 10, 31, 20, 20))
        assertEquals(3, d.size); assertEquals(855.18, d.last().close, 1e-9)
        val m = KlineRepo.monthly(d)
        assertEquals(listOf("2026-09", "2026-10"), m.map { it.label })
        val oct = m[1]
        assertEquals(871.72, oct.open, 1e-9); assertEquals(855.18, oct.close, 1e-9)
        assertEquals(875.16, oct.high, 1e-9); assertEquals(854.72, oct.low, 1e-9)
    }

    @Test fun mergeBarsOverwritesSameMinute() {
        val a = listOf(Bar("a", 1, 1.0, 1.0, 1.0, 1.0), Bar("b", 2, 2.0, 2.0, 2.0, 2.0))
        val b = listOf(Bar("b", 2, 2.0, 2.5, 2.5, 2.0), Bar("c", 3, 3.0, 3.0, 3.0, 3.0))
        val m = KlineRepo.mergeBars(a, b, 10)
        assertEquals(listOf(1L, 2L, 3L), m.map { it.timeMs }); assertEquals(2.5, m[1].close, 1e-9)
        assertEquals(2, KlineRepo.mergeBars(a, b, 2).size)
    }
}

class SgeTest {
    // 格式与 10-31 实测上金所 /graph/quotations 一致（节选并构造）：尚未发生的分钟被填了值，日期是“交易日”不是自然日
    private val q = """{"times":["20:00","20:01","20:02","02:29","02:30","09:00","15:30"],"min":833,"data":["854.76","853.0","852.5","833.2","833.0","831.0","831.0"],"max":854,"heyue":"Au99.99","now":"20:44","delaystr":"2026年11月02日 02:29:58"}"""

    @Test fun quotationsStopAtDelayTime() {
        val now = at(2026, 10, 31, 20, 44)
        val (quote, bars) = GoldSource.parseSgeQuotations(q, now)
        assertEquals(listOf("20:00", "20:01", "20:02", "02:29"), bars.map { it.label }) // 02:30 之后是填充值，不要
        assertEquals(833.2, quote!!.price, 1e-9)
        assertEquals(at(2026, 10, 31, 2, 29) + 58_000, quote.timeMs)                 // 自然日是 10-31（周六凌晨）
        assertEquals(at(2026, 10, 30, 20, 0), bars[0].timeMs)                         // 夜盘开始是前一天 20:00
        assertEquals(854.76, quote.high!!, 1e-9); assertEquals(833.2, quote.low!!, 1e-9)
    }

    @Test fun dailyFieldOrder() {
        // 10-31 实测 /graph/Dailyhq 末尾数据：[日期, 开, 收, 低, 高]
        val bars = GoldSource.parseSgeDaily("""{"time":[["2026-10-29",869.3,865.26,863.05,873.8],["2026-10-30",863.8,854.76,853.5,867]]}""")
        val b = bars.last()
        assertEquals("2026-10-30", b.label)
        assertEquals(863.8, b.open, 1e-9); assertEquals(854.76, b.close, 1e-9)
        assertEquals(853.5, b.low, 1e-9); assertEquals(867.0, b.high, 1e-9)
    }
}

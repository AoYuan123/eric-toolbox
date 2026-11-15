package com.eric.toolbox.modules.gold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/** 消息面打标规则。规则最容易错在否定句，这里把几个典型例子锁住。 */
class GoldNewsTest {

    @Test fun negationIsBearish() {
        assertEquals(Impact.BEAR, GoldNews.classify("美联储官员：通胀回落慢于预期，不急于降息").first)
        assertEquals(Impact.BEAR, GoldNews.classify("市场降息预期降温，美元指数反弹").first)
    }

    @Test fun rateCutIsBullish() {
        assertEquals(Impact.BULL, GoldNews.classify("美联储宣布降息 25 个基点").first)
        assertEquals(Impact.BULL, GoldNews.classify("地缘冲突升级，避险买盘涌入黄金").first)
    }

    @Test fun dataReleaseIsData() {
        assertEquals(Impact.DATA, GoldNews.classify("美国 10 月 PPI 同比 6.5%，预期 6.6%").first)
    }

    @Test fun deescalationOutranksConflictWord() {
        // 「冲突缓和」里同时有「冲突」（利多）和「缓和」（利空）：缓和才是方向。
        // 规则按语义优先级排序，先命中的算数，所以这条判利空。
        assertEquals(Impact.BEAR, GoldNews.classify("降息落地但地缘冲突缓和，金价冲高回落").first)
    }

    @Test fun irrelevantIsFilteredOut() {
        val a = GoldNewsItem(1000L, "某地举办马拉松比赛", "金十", "", important = false, impact = Impact.NEUTRAL)
        val b = GoldNewsItem(2000L, "美联储主席鲍威尔发表讲话", "金十", "", important = false, impact = Impact.DATA)
        val c = GoldNewsItem(3000L, "某公司发布新款手机", "见闻", "", important = true, impact = Impact.NEUTRAL)
        val merged = GoldNews.merge(listOf(a, b), listOf(c))
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.text.contains("鲍威尔") })
        assertTrue(merged.any { it.important })     // 重要快讯即使不相关也保留
        assertFalse(merged.any { it.text.contains("马拉松") })
    }

    @Test fun duplicatesAcrossSourcesAreDropped() {
        val a = GoldNewsItem(2000L, "美国10月PPI同比 6.5%，预期 6.6%，前值 6.3%", "金十", "", false, Impact.DATA)
        val b = GoldNewsItem(1999L, "美国10月PPI同比6.5%，预期6.6%，前值6.3%", "见闻", "", false, Impact.DATA)
        assertEquals(1, GoldNews.merge(listOf(a), listOf(b)).size)
    }

    @Test fun parseJin10SkipsVipAndReadsFields() {
        val body = """
            {"data":[
              {"id":1,"time":"2026-11-15 19:32:08","important":1,"data":{"content":"【金价上涨】现货黄金突破 4200 美元。"}},
              {"id":2,"time":"2026-11-15 19:20:00","important":0,"data":{"content":"","vip_title":"VIP 专享"}}
            ]}
        """.trimIndent()
        val list = GoldNews.parseJin10(body)
        assertEquals(1, list.size)
        assertEquals("金十", list[0].source)
        assertTrue(list[0].important)
        assertTrue(list[0].text.contains("现货黄金"))
    }

    @Test fun parseWscnReadsScoreAndUri() {
        val body = """
            {"data":{"items":[
              {"display_time":1794576605,"score":3,"uri":"https://wallstreetcn.com/livenews/1",
               "content":"<p>美国10月PPI同比 6.5%，预期 6.6%</p>"}
            ]}}
        """.trimIndent()
        val list = GoldNews.parseWscn(body)
        assertEquals(1, list.size)
        assertTrue(list[0].important)
        assertEquals("https://wallstreetcn.com/livenews/1", list[0].url)
        assertFalse(list[0].text.contains("<p>"))
    }
}

/** 三个低点的口径。 */
class GoldLowTest {

    private fun t(s: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            .apply { timeZone = BJ }.parse(s)!!.time

    private fun bar(label: String, at: String, low: Double) = Bar(label, t(at), low, low, low, low)

    @Test fun threeLows() {
        val now = t("2026-11-16 10:00")
        val days = listOf(
            bar("2026-11-03", "2026-11-03 15:30", 820.0),
            bar("2026-11-13", "2026-11-13 15:30", 829.98),
            bar("2026-10-20", "2026-10-20 15:30", 800.0),
        )
        val months = listOf(
            bar("2026-03-31", "2026-03-31 15:30", 780.0),
            bar("2026-11-16", "2026-11-16 10:00", 829.98),
            bar("2025-12-31", "2025-12-31 15:30", 700.0),
        )
        val q = Quote("东方财富", "AU9999", 837.0, high = 841.0, low = 833.0, timeMs = now)
        val pts = GoldLow.compute(now, q, emptyList(), days, months)
        assertEquals(3, pts.size)
        assertEquals(833.0, pts[0].price!!, 0.001)   // 当日：行情最低
        assertEquals(820.0, pts[1].price!!, 0.001)   // 本月：11 月最小
        assertEquals(780.0, pts[2].price!!, 0.001)   // 今年：3 月最低
        assertEquals("2026-03", pts[2].note)
    }

    @Test fun monthLowIncludesTodayQuote() {
        val now = t("2026-11-16 10:00")
        val days = listOf(bar("2026-11-03", "2026-11-03 15:30", 830.0))
        val q = Quote("东方财富", "AU9999", 837.0, low = 826.5, timeMs = now)
        val pts = GoldLow.compute(now, q, emptyList(), days, emptyList())
        assertEquals(826.5, pts[1].price!!, 0.001)
    }

    @Test fun missingHistoryGivesNullsNotCrashes() {
        val now = t("2026-11-16 10:00")
        val q = Quote("东方财富", "AU9999", 837.0, low = 833.0, timeMs = now)
        val pts = GoldLow.compute(now, q, emptyList(), emptyList(), emptyList())
        assertEquals(833.0, pts[0].price!!, 0.001)
        assertEquals(null, pts[1].price)
        assertEquals(null, pts[2].price)
    }
}

package com.eric.toolbox.modules.gold

/**
 * 三个"低点"：当日、本月、今年。
 *
 * 为什么不是直接把行情里的"最低"显示三次：行情接口只给当日最低，月/年最低必须
 * 从历史 K 线里算。日线只拉 120 根（≈4 个月），跨年就不够用了，所以年度最低走月线
 * （240 根，能回溯到 2006 年）。
 *
 * 三档的口径：
 * - 当日：交易日从 20:00 夜盘起算，取分钟线与行情最低价的较小者
 * - 本月：本月所有日线的最低，再把"今天现价"并进来（当月日线还没收盘时它是最新的）
 * - 今年：今年各月最低的最小值；当月还没走完，月度最低就是"至今最低"
 *
 * 价格都用大盘 Au99.99（积存金没有公开历史），界面上会注明。
 */
object GoldLow {

    data class Point(
        /** day / month / year，界面用来排序和取标题 */
        val key: String,
        val label: String,
        val price: Double?,
        /** 最低价发生的时刻；月度最低精确到日，年度最低精确到月 */
        val atMs: Long?,
        val note: String = "",
    )

    fun compute(
        now: Long,
        quote: Quote?,
        minutes: List<Bar>,
        days: List<Bar>,
        months: List<Bar>,
    ): List<Point> {
        val dayStart = Session.marketDayStart(now)

        // —— 当日 ——
        val dayCandidates = ArrayList<Pair<Double, Long?>>()
        minutes.filter { it.timeMs >= dayStart && it.low > 0 }.forEach { dayCandidates += it.low to it.timeMs }
        quote?.low?.takeIf { it > 0 }?.let { dayCandidates += it to quote.timeMs }
        val day = dayCandidates.minByOrNull { it.first }

        // —— 本月 ——
        // 只有拿到本月的日线，才把"今天的分钟低点"并进来：没有历史就宁可留空，
        // 否则会拿今天的最低价冒充本月最低，看上去"本月没更低过"，是错的。
        val monthKey = labelOf(now, 7)          // yyyy-MM
        val monthBars = days.filter { it.label.startsWith(monthKey) && it.low > 0 }
        val monthBar = monthBars.minByOrNull { it.low }
        val dayMin = dayCandidates.minByOrNull { it.first }
        val month = monthBar?.let { mb -> listOfNotNull<Pair<Double, Long?>>(mb.low to mb.timeMs, dayMin).minByOrNull { it.first } }

        // —— 今年 ——
        // 同理：年线用的是月线（240 根能回溯到 2006），月线拿不到就不给年最低。
        val yearKey = labelOf(now, 4)           // yyyy
        val yearBars = months.filter { it.label.startsWith(yearKey) && it.low > 0 }
        val yearBar = yearBars.minByOrNull { it.low }
        val year = yearBar?.let { yb -> listOfNotNull(yb.low to yb.timeMs, month).minByOrNull { it.first } }

        return listOf(
            Point("day", "当日最低", day?.first, day?.second),
            Point("month", "本月最低", month?.first, month?.second),
            Point("year", "今年最低", year?.first, year?.second,
                note = yearBar?.label?.take(7).orEmpty()),
        )
    }

    private fun labelOf(ms: Long, len: Int): String {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
            .apply { timeZone = BJ }
        return f.format(java.util.Date(ms)).take(len)
    }
}

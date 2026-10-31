package com.eric.toolbox.modules.gold

import com.eric.toolbox.core.Http
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/** 一个报价。timeMs 为行情源给出的报价时间（不是本机时间）。 */
data class Quote(
    val source: String,
    val code: String,
    val price: Double,
    val high: Double? = null,
    val low: Double? = null,
    val prevClose: Double? = null,
    val open: Double? = null,
    val timeMs: Long? = null,
)

/** 工商银行官网行情页：积存金 + 如意金积存。 */
data class BankQuote(
    val jcj: Double?,         // 积存金 实时主动积存价（与赎回价一致）
    val jcjHigh: Double?,
    val jcjLow: Double?,
    val ryBuy: Double?,       // 如意金积存 积存价
    val rySell: Double?,      // 如意金积存 赎回价
    val timeMs: Long?,        // 工行页面上的“更新时间”
)

data class Snapshot(
    val market: Quote?,       // 大盘 上海金 Au99.99（东方财富；连不上时用上金所官网延时行情）
    val td: Quote?,           // 黄金 T+D（东方财富）
    val futures: Quote?,      // 沪金连续（新浪）
    val bank: BankQuote?,
    val fetchedAtMs: Long,
    val errors: List<String>,
) {
    fun marketVerified(): Boolean = market != null && (
        (td != null && rel(td.price, market.price) <= GoldSource.TOL_TD) ||
            (futures != null && rel(futures.price, market.price) <= GoldSource.TOL_FUT)
        )

    fun marketFresh(): Boolean = market?.timeMs?.let { abs(fetchedAtMs - it) <= GoldSource.FRESH_MS } ?: false

    /** 积存金与大盘偏差在容差内才算可信。 */
    fun bankVerified(): Boolean {
        val j = bank?.jcj ?: return false
        val m = market?.price ?: td?.price ?: return false
        return rel(j, m) <= GoldSource.TOL_BANK
    }

    fun bankFresh(): Boolean = bank?.timeMs?.let { abs(fetchedAtMs - it) <= GoldSource.BANK_FRESH_MS } ?: false

    private fun rel(a: Double, b: Double) = abs(a - b) / b
}

/** 一根 K 线或一分钟价格。 */
data class Bar(val label: String, val timeMs: Long, val open: Double, val close: Double, val high: Double, val low: Double)

/**
 * 行情源（2026-10-30 已实测可用）：
 * - 大盘：东方财富 push2 接口，上海金 Au99.99（secid 118.AU9999）
 * - 校验：同接口黄金 T+D（118.AUTD，容差 1.5%）；新浪沪金连续 nf_AU0（容差 3%）
 * - 工行：官网积存金行情页（积存金、如意金积存），每 3 秒更新，9:10–22:30
 * - 走势：东方财富 push2his K 线接口（分钟 / 日 / 月）
 * - 备用（10-31 实测）：上海黄金交易所官网延时行情 /graph/quotations（当日分钟价）、/graph/Dailyhq（2016-12 起日线），
 *   都是 Au99.99 本身；东方财富连不上时用来显示大盘价和走势。
 * 这些是网站公开使用的接口，并非官方开放 API，改版后需要更新这里。
 */
object GoldSource {
    private const val EM_URL =
        "https://push2.eastmoney.com/api/qt/ulist.np/get?secids=118.AU9999,118.AUTD&fields=f1,f2,f12,f15,f16,f17,f18,f124"
    private const val SINA_URL = "https://hq.sinajs.cn/list=nf_AU0"
    private const val ICBC_URL = "https://mybank.icbc.com.cn/icbc/newperbank/perbank3/gold/goldaccrual_query_out.jsp"
    const val SGE_QUOTE = "https://www.sge.com.cn/graph/quotations"
    const val SGE_DAILY = "https://www.sge.com.cn/graph/Dailyhq"
    val SGE_HDR = mapOf("Referer" to "https://www.sge.com.cn/sjzx/yshqbg", "X-Requested-With" to "XMLHttpRequest")
    private const val KLINE = "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=118.AU9999&fields1=f1&fields2=f51,f52,f53,f54,f55&fqt=0&end=20500101"

    const val TOL_TD = 0.015
    const val TOL_FUT = 0.03
    const val TOL_BANK = 0.02
    const val FRESH_MS = 10 * 60_000L
    const val BANK_FRESH_MS = 5 * 60_000L

    private val GBK: Charset = Charset.forName("GBK")

    suspend fun fetch(nowMs: Long = System.currentTimeMillis()): Snapshot = coroutineScope {
        val em = async { runCatching { parseEastmoney(Http.get(EM_URL)) } }
        val sina = async {
            runCatching { parseSina(Http.get(SINA_URL, headers = mapOf("Referer" to "https://finance.sina.com.cn/"), charset = GBK)) }
        }
        val icbc = async { runCatching { parseIcbc(Http.get(ICBC_URL, charset = GBK)) } }
        val emR = em.await(); val sinaR = sina.await(); val icbcR = icbc.await()
        val emQ = emR.getOrNull().orEmpty()
        var market = emQ.firstOrNull { it.code == "AU9999" }
        var sgeErr: Throwable? = null
        if (market == null) {
            // 东方财富连不上：改用上金所官网延时行情（同样是 Au99.99）
            market = runCatching { sgeQuote(nowMs) }.onFailure { sgeErr = it }.getOrNull()
        }
        val errors = buildList {
            emR.exceptionOrNull()?.let { add("东方财富：${it.message ?: "网络错误"}") }
            if (market?.source == SGE_NAME) add("大盘价暂用上金所官网延时行情")
            sgeErr?.let { add("上金所：${it.message ?: "网络错误"}") }
            icbcR.exceptionOrNull()?.let { add("工商银行：${it.message ?: "网络错误"}") }
        }
        Snapshot(
            market = market,
            td = emQ.firstOrNull { it.code == "AUTD" },
            futures = sinaR.getOrNull(),
            bank = icbcR.getOrNull(),
            fetchedAtMs = nowMs,
            errors = errors,
        )
    }

    /** 东方财富：价格为整数，需除以 10^f1（小数位数）；f124 为秒级时间戳。无数据时字段为 "-"。 */
    fun parseEastmoney(body: String): List<Quote> {
        val diff = JSONObject(body).optJSONObject("data")?.optJSONArray("diff") ?: return emptyList()
        val out = mutableListOf<Quote>()
        for (i in 0 until diff.length()) {
            val o = diff.getJSONObject(i)
            val scale = 10.0.pow(o.optInt("f1", 2))
            fun num(k: String): Double? = o.opt(k).let { if (it is Number) it.toDouble() / scale else null }
            val price = num("f2") ?: continue
            if (price <= 0) continue
            val ts = o.opt("f124").let { if (it is Number) it.toLong() * 1000 else null }
            out += Quote("东方财富", o.optString("f12"), price, num("f15"), num("f16"), num("f18"), num("f17"), ts)
        }
        return out
    }

    /** 新浪：var hq_str_nf_AU0="黄金连续,时间,开,高,低,,买,卖,最新,...,日期(第17位),..." */
    fun parseSina(body: String): Quote? {
        val inner = body.substringAfter('"', "").substringBefore('"', "")
        val f = inner.split(',')
        if (f.size < 18) return null
        val price = f[8].toDoubleOrNull() ?: return null
        if (price <= 0) return null
        return Quote("新浪财经", "AU0", price, f[3].toDoubleOrNull(), f[4].toDoubleOrNull(), open = f[2].toDoubleOrNull())
    }

    /**
     * 工行官网积存金行情页（服务器直接渲染的 HTML 表格）：
     * 积存金 产品号 080020000521：activeprice / lowprice / highprice / sellprice
     * 如意金积存 产品号 080020000501：activeprice（积存价）/ sellprice（赎回价）
     * 页脚：更新时间:yyyy-MM-dd HH:mm:ss
     */
    fun parseIcbc(html: String): BankQuote {
        fun cell(id: String): Double? =
            Regex("""id="$id"[^>]*>\s*([0-9]+(?:\.[0-9]+)?)\s*<""").find(html)?.groupValues?.get(1)?.toDoubleOrNull()
        val timeText = (Regex("""id="time"[^>]*>[^<]*?(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""").find(html)
            ?: Regex("""更新时间[:：]\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""").find(html))?.groupValues?.get(1)
        val time = timeText?.let {
            runCatching { fmt("yyyy-MM-dd HH:mm:ss").parse(it)?.time }.getOrNull()
        }
        val q = BankQuote(
            jcj = cell("activeprice_080020000521"),
            jcjHigh = cell("highprice_080020000521"),
            jcjLow = cell("lowprice_080020000521"),
            ryBuy = cell("activeprice_080020000501"),
            rySell = cell("sellprice_080020000501"),
            timeMs = time,
        )
        if (q.jcj == null && q.ryBuy == null) throw java.io.IOException("页面格式变化，未找到价格")
        return q
    }

    private fun fmt(p: String) = SimpleDateFormat(p, Locale.CHINA).apply { timeZone = BJ }

    const val SGE_NAME = "上金所延时"

    suspend fun sgeQuote(nowMs: Long): Quote? =
        parseSgeQuotations(Http.postForm(SGE_QUOTE, "instid=Au99.99", SGE_HDR), nowMs).first

    suspend fun sgeMinutes(nowMs: Long): List<Bar> =
        parseSgeQuotations(Http.postForm(SGE_QUOTE, "instid=Au99.99", SGE_HDR), nowMs).second

    suspend fun sgeDaily(): List<Bar> = parseSgeDaily(Http.postForm(SGE_DAILY, "instid=Au99.99", SGE_HDR))

    /**
     * 上金所延时行情：{"times":["20:00",…,"15:30"],"data":["854.76",…],"delaystr":"2026年11月02日 02:29:58",…}
     * times/data 覆盖整个交易日（夜盘 20:00 起到次日 15:30），**尚未发生的分钟也会被填上一个值**，
     * 所以只取到 delaystr 的时刻为止。delaystr 里的日期是“交易日”（周五夜盘记作下周一），不是自然日，
     * 因此只用其中的时分秒，自然日按“不晚于现在的最近一次该时刻”推算。
     * @return 最新报价（时间为数据实际时刻）和当日有效的分钟价
     */
    fun parseSgeQuotations(body: String, nowMs: Long): Pair<Quote?, List<Bar>> {
        val o = JSONObject(body)
        val times = o.optJSONArray("times") ?: return null to emptyList()
        val data = o.optJSONArray("data") ?: return null to emptyList()
        val clock = Regex("""(\d{1,2}):(\d{2}):(\d{2})""").find(o.optString("delaystr")) ?: return null to emptyList()
        val (hh, mm, ss) = clock.destructured
        val c = java.util.Calendar.getInstance(BJ).apply {
            timeInMillis = nowMs
            set(java.util.Calendar.HOUR_OF_DAY, hh.toInt()); set(java.util.Calendar.MINUTE, mm.toInt())
            set(java.util.Calendar.SECOND, ss.toInt()); set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis > nowMs + 60_000L) add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        val lastMs = c.timeInMillis
        val lastHm = String.format(Locale.US, "%02d:%02d", hh.toInt(), mm.toInt())
        // 从 lastMs 往前按分钟倒推每个时刻的自然时间（跨夜盘时日期会正确回退）
        val n = minOf(times.length(), data.length())
        var endIdx = -1
        for (i in 0 until n) if (times.optString(i) == lastHm) endIdx = i
        if (endIdx < 0) return null to emptyList()
        val bars = ArrayList<Bar>()
        val minuteOf = { hm: String -> hm.substringBefore(':').toInt() * 60 + hm.substringAfter(':').toInt() }
        val lastMin = minuteOf(lastHm)
        val lastMinuteStart = lastMs - ss.toInt() * 1000L
        for (i in 0..endIdx) {
            val hm = times.optString(i)
            val v = data.optString(i).toDoubleOrNull() ?: continue
            if (v <= 0) continue
            var diff = lastMin - minuteOf(hm)
            if (diff < 0) diff += 24 * 60
            val t = lastMinuteStart - diff * 60_000L
            bars += Bar(hm, t, v, v, v, v)
        }
        if (bars.isEmpty()) return null to emptyList()
        val q = Quote(SGE_NAME, "AU9999", bars.last().close, bars.maxOf { it.high }, bars.minOf { it.low }, null, bars.first().open, lastMs)
        return q to bars
    }

    /** 上金所日线：{"time":[["2026-10-30",开,收,低,高],…]}（字段顺序已用 10-30 数据核对：开 863.8 收 854.76 低 853.5 高 867）。 */
    fun parseSgeDaily(body: String): List<Bar> {
        val arr = JSONObject(body).optJSONArray("time") ?: return emptyList()
        val f = fmt("yyyy-MM-dd")
        val out = ArrayList<Bar>(arr.length())
        for (i in 0 until arr.length()) {
            val a = arr.optJSONArray(i) ?: continue
            if (a.length() < 5) continue
            val d = a.optString(0)
            val t = runCatching { f.parse(d)?.time }.getOrNull() ?: continue
            val o = a.optDouble(1); val c = a.optDouble(2); val l = a.optDouble(3); val h = a.optDouble(4)
            if (listOf(o, c, l, h).any { it.isNaN() || it <= 0 }) continue
            out += Bar(d, t, o, c, h, l)
        }
        return out.sortedBy { it.timeMs }
    }

    /** K 线。klt：1=分钟，101=日，103=月。 */
    suspend fun klines(klt: Int, limit: Int): List<Bar> = parseKlines(Http.get("$KLINE&klt=$klt&lmt=$limit"), klt == 1)

    fun parseKlines(body: String, minute: Boolean): List<Bar> {
        val arr = JSONObject(body).optJSONObject("data")?.optJSONArray("klines") ?: return emptyList()
        val f = if (minute) fmt("yyyy-MM-dd HH:mm") else fmt("yyyy-MM-dd")
        val out = ArrayList<Bar>(arr.length())
        for (i in 0 until arr.length()) {
            val p = arr.getString(i).split(',')
            if (p.size < 5) continue
            val t = runCatching { f.parse(p[0])?.time }.getOrNull() ?: continue
            val o = p[1].toDoubleOrNull() ?: continue
            val c = p[2].toDoubleOrNull() ?: continue
            val h = p[3].toDoubleOrNull() ?: continue
            val l = p[4].toDoubleOrNull() ?: continue
            out += Bar(p[0], t, o, c, h, l)
        }
        return out
    }

    /**
     * 过滤明显的异常成交价：影线不允许超出开收盘价 ±10%。
     * （历史数据里个别月份的最低价离奇地低，如 2016-03 的 181.2，而当月开收盘在 255 左右。）
     */
    fun clean(bars: List<Bar>): List<Bar> = bars.map { b ->
        val hiCap = maxOf(b.open, b.close) * 1.10
        val loCap = minOf(b.open, b.close) * 0.90
        b.copy(high = minOf(b.high, hiCap), low = maxOf(b.low, loCap))
    }

    /** 月线合成年线。 */
    fun yearly(monthly: List<Bar>): List<Bar> = monthly.groupBy { it.label.take(4) }.map { (y, ms) ->
        Bar(y, ms.first().timeMs, ms.first().open, ms.last().close, ms.maxOf { it.high }, ms.minOf { it.low })
    }.sortedBy { it.timeMs }
}

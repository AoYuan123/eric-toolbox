package com.eric.toolbox.modules.gold

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.BackgroundTask
import com.eric.toolbox.core.Category
import com.eric.toolbox.core.MonitorService
import com.eric.toolbox.core.Notifier
import com.eric.toolbox.core.Store
import com.eric.toolbox.core.ToolModule
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.Mono
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/** 提醒依据。 */
enum class Basis(val key: String, val title: String) {
    MARKET("market", "大盘金价"), JCJ("jcj", "积存金"), ANY("any", "任一跌破");

    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } ?: MARKET }
}

/** 用户可调的设置。 */
data class GoldConfig(
    val enabled: Boolean,
    val basis: Basis,
    val basePrice: Double,
    val startTier: Int,
    val step: Int,
    val riseTarget: Double,
    val smart: Boolean,
    val nearSec: Int,
    val openAlert: Boolean,
)

/** 界面显示用的运行状态。 */
data class FetchStatus(val lastOkMs: Long = 0, val lastTryMs: Long = 0, val error: String? = null)

object GoldModule : ToolModule {
    override val id = "gold"
    override val name = "实时金价"
    override val category = Category.MONITOR
    override val icon: ImageVector = Icons.AutoMirrored.Filled.TrendingUp

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot
    private val _status = MutableStateFlow(FetchStatus())
    val status: StateFlow<FetchStatus> = _status

    fun prefs(ctx: Context): SharedPreferences = Store.module(ctx, id)

    // ---------------- 设置 ----------------

    fun config(ctx: Context): GoldConfig {
        val p = prefs(ctx)
        return GoldConfig(
            enabled = p.getBoolean("enabled", true),
            basis = Basis.of(p.getString("basis", "market")),
            basePrice = p.getFloat("base", 852f).toDouble(),
            startTier = p.getInt("start", 850),
            step = p.getInt("step", 10),
            riseTarget = p.getFloat("rise", 1000f).toDouble(),
            smart = p.getBoolean("smart", true),
            nearSec = p.getInt("interval", 30).let { if (it > 30) 30 else it },
            openAlert = p.getBoolean("open_alert", true),
        )
    }

    /** 保存设置；档位规则或提醒依据变了就把“已提醒档位”重置到初始状态。 */
    fun saveConfig(ctx: Context, c: GoldConfig) {
        val old = config(ctx)
        val ed = prefs(ctx).edit()
            .putBoolean("enabled", c.enabled).putString("basis", c.basis.key).putFloat("base", c.basePrice.toFloat())
            .putInt("start", c.startTier).putInt("step", c.step).putFloat("rise", c.riseTarget.toFloat())
            .putBoolean("smart", c.smart).putInt("interval", c.nearSec).putBoolean("open_alert", c.openAlert)
        if (old.startTier != c.startTier || old.step != c.step || old.basis != c.basis) {
            ed.putInt("last", TierLogic.initial(c.startTier, c.step))
        }
        if (old.riseTarget != c.riseTarget) ed.putBoolean("rise_armed", true)
        ed.apply()
        MonitorService.sync(ctx)
    }

    fun lastAlerted(ctx: Context): Int {
        val c = config(ctx)
        return prefs(ctx).getInt("last", TierLogic.initial(c.startTier, c.step))
    }

    fun resetTiers(ctx: Context) {
        val c = config(ctx)
        prefs(ctx).edit().putInt("last", TierLogic.initial(c.startTier, c.step)).apply()
    }

    // ---------------- 刷新与提醒 ----------------

    // 后台服务和界面可能同时刷新，用锁保证同一时间只判断一次，避免重复提醒
    private val lock = Mutex()

    suspend fun refresh(ctx: Context) = lock.withLock { refreshLocked(ctx) }

    private suspend fun refreshLocked(ctx: Context) {
        val now = System.currentTimeMillis()
        val snap = try {
            GoldSource.fetch(now)
        } catch (e: Exception) {
            _status.value = _status.value.copy(lastTryMs = now, error = e.message ?: "网络错误")
            return
        }
        val ok = snap.market != null || snap.bank != null
        _status.value = FetchStatus(
            lastOkMs = if (ok) now else _status.value.lastOkMs,
            lastTryMs = now,
            error = if (snap.errors.isEmpty()) null else snap.errors.joinToString("；"),
        )
        if (!ok) return
        _snapshot.value = snap
        recordBank(ctx, snap)
        evaluate(ctx, snap, now)
    }

    /** 两次查询之间的大盘最低价：用东方财富分钟线补上中间漏掉的快速下探。 */
    private suspend fun marketMinSinceLast(ctx: Context, snap: Snapshot, now: Long): Double? {
        val cur = snap.market?.price ?: return null
        val lastEval = prefs(ctx).getLong("last_eval", 0L)
        if (lastEval == 0L || now - lastEval > 60 * 60_000L) return cur
        val minutes = ((now - lastEval) / 60_000L + 2).toInt().coerceIn(2, 60)
        val bars = runCatching { GoldSource.klines(1, minutes) }.getOrNull().orEmpty()
        val closes = bars.filter { it.timeMs >= lastEval - 60_000L }.map { it.close }
        // 分钟收盘价若与当前价偏离超过 3%，视为异常数据不采用
        val sane = closes.filter { abs(it - cur) / cur <= 0.03 }
        return (sane + cur).min()
    }

    private suspend fun evaluate(ctx: Context, snap: Snapshot, now: Long) {
        val c = config(ctx)
        val p = prefs(ctx)
        val marketOk = snap.marketVerified() && snap.marketFresh() && Session.marketTrading(now)
        val bankOk = snap.bankVerified() && snap.bankFresh() && Session.bankTrading(now)

        val marketMin = if (marketOk && c.basis != Basis.JCJ) marketMinSinceLast(ctx, snap, now) else null
        val candidates = buildList {
            if (c.basis != Basis.JCJ && marketOk && marketMin != null) add(Triple("大盘", marketMin, snap.market!!.price))
            if (c.basis != Basis.MARKET && bankOk) {
                val j = snap.bank!!.jcj!!
                add(Triple("积存金", j, j))
            }
        }
        if (candidates.isEmpty()) return
        p.edit().putLong("last_eval", now).apply()

        val today = Session.dayKey(now)
        val firstToday = p.getInt("eval_day", 0) != today
        p.edit().putInt("eval_day", today).apply()

        // 下跌档位：取各依据里最低的那个价格判断
        val low = candidates.minBy { it.second }
        val last = lastAlerted(ctx)
        val d = TierLogic.evaluate(low.second, last, c.startTier, c.step, current = low.third)
        if (d.newLastAlerted != last) p.edit().putInt("last", d.newLastAlerted).apply()
        d.alertTier?.let { tier ->
            val drop = c.basePrice - low.third
            val opening = firstToday && c.openAlert
            val title = when {
                opening && d.crossed >= 2 -> "开盘提醒：已连跌 ${d.crossed} 档，跌破 $tier 元/克"
                opening -> "开盘提醒：已跌破 $tier 元/克"
                d.crossed >= 2 -> "金价跌破 $tier 元/克（连跌 ${d.crossed} 档）"
                else -> "金价跌破 $tier 元/克"
            }
            val body = buildString {
                append(String.format(Locale.CHINA, "%s %.2f", low.first, low.third))
                if (low.second < low.third - 0.005) append(String.format(Locale.CHINA, "（期间最低 %.2f）", low.second))
                snap.market?.price?.let { if (low.first != "大盘") append(String.format(Locale.CHINA, "，大盘 %.2f", it)) }
                snap.bank?.jcj?.let { if (low.first != "积存金") append(String.format(Locale.CHINA, "，积存金 %.2f", it)) }
                append(String.format(Locale.CHINA, "，较基准 %s 已%s %.2f 元。", fmtBase(c.basePrice), if (drop >= 0) "下跌" else "上涨", abs(drop)))
            }
            Notifier.alert(ctx, id, title, body)
        }

        // 上涨提醒：取各依据里最高的价格
        val high = candidates.maxBy { it.third }
        val armed = p.getBoolean("rise_armed", true)
        val r = RiseLogic.evaluate(high.third, c.riseTarget, armed, rearm = c.step.toDouble())
        if (r.armed != armed) p.edit().putBoolean("rise_armed", r.armed).apply()
        if (r.fire) {
            Notifier.alert(
                ctx, id, "金价涨到 ${fmtBase(c.riseTarget)} 元/克",
                String.format(Locale.CHINA, "%s 当前 %.2f 元/克，已达到你设置的上涨提醒价。", high.first, high.third),
            )
        }
    }

    fun fmtBase(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.CHINA, "%.2f", v)

    // ---------------- 工行价格记录（工行无公开历史，App 自己记） ----------------

    data class BankPoint(val t: Long, val jcj: Double?, val ryBuy: Double?, val rySell: Double?)

    private val _bankSeries = MutableStateFlow<List<BankPoint>>(emptyList())
    val bankSeries: StateFlow<List<BankPoint>> = _bankSeries

    fun loadBankSeries(ctx: Context): List<BankPoint> {
        val p = prefs(ctx)
        val today = Session.dayKey(System.currentTimeMillis())
        if (p.getInt("bank_day", 0) != today) return emptyList<BankPoint>().also { _bankSeries.value = it }
        val arr = runCatching { JSONArray(p.getString("bank_pts", "[]")) }.getOrElse { JSONArray() }
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONArray(i)
            BankPoint(o.getLong(0), o.optDouble(1).takeIf { !it.isNaN() }, o.optDouble(2).takeIf { !it.isNaN() }, o.optDouble(3).takeIf { !it.isNaN() })
        }
        _bankSeries.value = list
        return list
    }

    /** 记录工行价格点，并维护积存金的“昨收”（前一天最后一个价）和“今开”（当天第一个价）。 */
    private fun recordBank(ctx: Context, snap: Snapshot) {
        val b = snap.bank ?: return
        val t = b.timeMs ?: return
        val p = prefs(ctx)
        val day = Session.dayKey(t)
        val storedDay = p.getInt("bank_day", 0)
        val ed = p.edit()
        var list = if (storedDay == day) loadBankSeries(ctx) else emptyList()
        if (storedDay != day) {
            // 新的一天：把前一天最后一个积存金价格当作“昨收”
            p.getFloat("jcj_last", 0f).takeIf { it > 0 }?.let { ed.putFloat("jcj_prev", it) }
            b.jcj?.let { ed.putFloat("jcj_open", it.toFloat()) }
            ed.putInt("bank_day", day)
        }
        if (list.lastOrNull()?.t != t) {
            list = (list + BankPoint(t, b.jcj, b.ryBuy, b.rySell)).takeLast(1000)
            val arr = JSONArray()
            list.forEach { arr.put(JSONArray().put(it.t).put(it.jcj ?: JSONObject.NULL).put(it.ryBuy ?: JSONObject.NULL).put(it.rySell ?: JSONObject.NULL)) }
            ed.putString("bank_pts", arr.toString())
        }
        b.jcj?.let { ed.putFloat("jcj_last", it.toFloat()) }
        ed.apply()
        _bankSeries.value = list
    }

    fun jcjPrev(ctx: Context): Double? = prefs(ctx).getFloat("jcj_prev", 0f).takeIf { it > 0 }?.toDouble()
    fun jcjOpen(ctx: Context): Double? =
        if (prefs(ctx).getInt("bank_day", 0) == Session.dayKey(System.currentTimeMillis())) prefs(ctx).getFloat("jcj_open", 0f).takeIf { it > 0 }?.toDouble() else null

    // ---------------- 持仓交易明细 ----------------

    fun trades(ctx: Context, product: String): List<Trade> {
        val arr = runCatching { JSONArray(prefs(ctx).getString("ledger_$product", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Trade(o.getLong("id"), o.getBoolean("buy"), o.getString("date"), o.getDouble("g"), o.getDouble("p"), o.optDouble("fee", 0.0))
        }
    }

    fun saveTrades(ctx: Context, product: String, list: List<Trade>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().put("id", t.id).put("buy", t.buy).put("date", t.date).put("g", t.grams).put("p", t.price).put("fee", t.fee))
        }
        prefs(ctx).edit().putString("ledger_$product", arr.toString()).apply()
    }

    // ---------------- 后台任务 ----------------

    /** 离最近一个提醒价位的距离，用于智能刷新。 */
    private fun distance(ctx: Context): Double? {
        val s = _snapshot.value ?: return null
        val c = config(ctx)
        val price = when (c.basis) {
            Basis.MARKET -> s.market?.price
            Basis.JCJ -> s.bank?.jcj
            Basis.ANY -> listOfNotNull(s.market?.price, s.bank?.jcj).minOrNull()
        } ?: return null
        val next = TierLogic.nextTier(lastAlerted(ctx), c.startTier, c.step)
        return minOf(price - next, c.riseTarget - price)
    }

    private fun inSession(c: GoldConfig, now: Long) = when (c.basis) {
        Basis.MARKET -> Session.marketTrading(now)
        Basis.JCJ -> Session.bankTrading(now)
        Basis.ANY -> Session.marketTrading(now) || Session.bankTrading(now)
    }

    override val background: BackgroundTask = object : BackgroundTask {
        override fun isEnabled(ctx: Context) = config(ctx).enabled
        override suspend fun runOnce(ctx: Context) = refresh(ctx)

        override fun nextDelayMs(ctx: Context, nowMs: Long): Long {
            val c = config(ctx)
            if (!inSession(c, nowMs)) return 10 * 60_000L
            if (!c.smart) return c.nearSec * 1000L
            val d = distance(ctx) ?: return c.nearSec * 1000L
            return SmartInterval.seconds(d, c.nearSec) * 1000L
        }

        override fun needsPreciseTiming(ctx: Context, nowMs: Long): Boolean {
            val c = config(ctx)
            if (!inSession(c, nowMs)) return false
            if (!c.smart) return true
            return (distance(ctx) ?: 0.0) <= 3.0
        }

        override fun statusLine(ctx: Context): String? {
            val s = _snapshot.value ?: return "金价 获取中…"
            val c = config(ctx)
            val next = TierLogic.nextTier(lastAlerted(ctx), c.startTier, c.step)
            val parts = mutableListOf<String>()
            s.market?.let { parts += String.format(Locale.CHINA, "大盘 %.2f", it.price) }
            s.bank?.jcj?.let { parts += String.format(Locale.CHINA, "积存金 %.2f", it) }
            parts += "下一档 $next"
            return parts.joinToString(" · ")
        }
    }

    @Composable
    override fun HomeSummary() {
        val snap by snapshot.collectAsState()
        val s = snap
        Row {
            Text("积存金 ", color = C.Muted, fontSize = 12.sp)
            Text(s?.bank?.jcj?.let { String.format(Locale.CHINA, "%.2f", it) } ?: "--", color = C.Gold, fontSize = 16.sp, fontFamily = Mono)
        }
        Row {
            val m = s?.market
            val pc = m?.prevClose
            val color = when {
                m == null || pc == null -> C.Text
                m.price >= pc -> C.Up
                else -> C.Down
            }
            Text("大盘 ", color = C.Muted, fontSize = 12.sp)
            Text(m?.let { String.format(Locale.CHINA, "%.2f", it.price) } ?: "--", color = color, fontSize = 16.sp, fontFamily = Mono)
        }
    }

    @Composable
    override fun Screen(onBack: () -> Unit) = GoldRoot(onBack)
}

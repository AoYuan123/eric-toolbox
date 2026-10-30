package com.eric.toolbox.modules.gold

import android.content.Context
import android.content.SharedPreferences
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
import java.util.Locale

/** 用户可调的设置。 */
data class GoldConfig(
    val enabled: Boolean,
    val basePrice: Double,
    val startTier: Int,
    val step: Int,
    val intervalSec: Int,
    val daySession: Boolean,
    val nightSession: Boolean,
)

object GoldModule : ToolModule {
    override val id = "gold"
    override val name = "实时金价"
    override val category = Category.MONITOR
    override val icon: ImageVector = Icons.AutoMirrored.Filled.TrendingUp

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot

    /** 当日价格点（内存中，用于走势小图）。 */
    private val _points = MutableStateFlow<List<Pair<Long, Double>>>(emptyList())
    val points: StateFlow<List<Pair<Long, Double>>> = _points

    fun prefs(ctx: Context): SharedPreferences = Store.module(ctx, id)

    fun config(ctx: Context): GoldConfig {
        val p = prefs(ctx)
        return GoldConfig(
            enabled = p.getBoolean("enabled", true),
            basePrice = p.getFloat("base", 852f).toDouble(),
            startTier = p.getInt("start", 850),
            step = p.getInt("step", 10),
            intervalSec = p.getInt("interval", 30),
            daySession = p.getBoolean("day", true),
            nightSession = p.getBoolean("night", true),
        )
    }

    /** 保存设置；档位规则变了就把“已提醒档位”重置到初始状态。 */
    fun saveConfig(ctx: Context, c: GoldConfig) {
        val old = config(ctx)
        val ed = prefs(ctx).edit()
            .putBoolean("enabled", c.enabled).putFloat("base", c.basePrice.toFloat())
            .putInt("start", c.startTier).putInt("step", c.step).putInt("interval", c.intervalSec)
            .putBoolean("day", c.daySession).putBoolean("night", c.nightSession)
        if (old.startTier != c.startTier || old.step != c.step) {
            ed.putInt("last", TierLogic.initial(c.startTier, c.step))
        }
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

    private fun inUserSession(c: GoldConfig, nowMs: Long): Boolean {
        if (!Session.isTrading(nowMs)) return false
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")).apply { timeInMillis = nowMs }
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val isDay = h in 9..15
        return if (isDay) c.daySession else c.nightSession
    }

    /** 拉一次行情并判断是否提醒。界面上的“立即刷新”也调用它。 */
    // 后台服务和界面可能同时刷新，用锁保证同一时间只判断一次，避免重复提醒
    private val lock = Mutex()

    suspend fun refresh(ctx: Context) = lock.withLock { refreshLocked(ctx) }

    private suspend fun refreshLocked(ctx: Context) {
        val now = System.currentTimeMillis()
        val snap = GoldSource.fetch(now)
        _snapshot.value = snap
        val p = snap.primary ?: return
        val cur = _points.value
        val sameDay = cur.lastOrNull()?.let { (now - it.first) < 20 * 3600_000L } ?: true
        _points.value = ((if (sameDay) cur else emptyList()) + (now to p.price)).takeLast(1500)

        val c = config(ctx)
        if (!snap.usableForAlert || !inUserSession(c, now)) return
        val last = lastAlerted(ctx)
        val d = TierLogic.evaluate(p.price, last, c.startTier, c.step)
        if (d.newLastAlerted != last) prefs(ctx).edit().putInt("last", d.newLastAlerted).apply()
        val tier = d.alertTier ?: return
        val drop = c.basePrice - p.price
        val title = "金价跌破 $tier 元/克" + if (d.crossed >= 2) "（连跌 ${d.crossed} 档）" else ""
        val body = String.format(
            Locale.CHINA, "当前 %.2f 元/克，较基准 %s 已%s %.2f 元。",
            p.price, fmtBase(c.basePrice), if (drop >= 0) "下跌" else "上涨", kotlin.math.abs(drop),
        )
        Notifier.alert(ctx, id, title, body)
    }

    fun fmtBase(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.CHINA, "%.2f", v)

    override val background: BackgroundTask = object : BackgroundTask {
        override fun isEnabled(ctx: Context) = config(ctx).enabled
        override suspend fun runOnce(ctx: Context) = refresh(ctx)
        override fun nextDelayMs(ctx: Context, nowMs: Long): Long {
            val c = config(ctx)
            return if (inUserSession(c, nowMs)) c.intervalSec * 1000L else 15 * 60_000L
        }
        override fun needsPreciseTiming(ctx: Context, nowMs: Long) = inUserSession(config(ctx), nowMs)
        override fun statusLine(ctx: Context): String? {
            val s = _snapshot.value ?: return "金价 获取中…"
            val p = s.primary ?: return "金价 获取失败"
            val c = config(ctx)
            val next = TierLogic.nextTier(lastAlerted(ctx), c.startTier, c.step)
            val flag = when {
                !s.fresh -> " · 数据延迟"
                !s.verified -> " · 未校验"
                else -> ""
            }
            return String.format(Locale.CHINA, "Au99.99 %.2f · 下一档 %d%s", p.price, next, flag)
        }
    }

    @Composable
    override fun HomeSummary() {
        val snap by snapshot.collectAsState()
        val p = snap?.primary
        Text(p?.let { String.format(Locale.CHINA, "%.2f", it.price) } ?: "--", color = C.Gold, fontSize = 20.sp, fontFamily = Mono)
        Text(
            when {
                snap == null -> "获取中…"
                p == null -> "行情获取失败"
                !snap!!.fresh -> "数据延迟 / 休市"
                else -> "元/克 · 上海金 Au99.99"
            },
            color = C.Muted, fontSize = 11.sp,
        )
    }

    @Composable
    override fun Screen(onBack: () -> Unit) = GoldScreen(onBack)
}

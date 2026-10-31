package com.eric.toolbox.modules.gold

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.floor

val BJ: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

/**
 * 档位规则（纯计算，无任何 Android 依赖，便于单元测试）。
 *
 * 档位 = start - step × n（n 为整数，向下无下限）。
 * lastAlerted（L）= 上次已提醒的档位；初始为 start + step，表示还没提醒过。
 *
 * 1. 价格回升超过 L + step：把 L 重置为“当前价上方最近一档”，不提醒。
 * 2. 否则找 D = 满足 P ≤ 档位 的最小档位；仅当 D < L 时提醒，并把 L 更新为 D。
 */
object TierLogic {
    data class Decision(
        val alertTier: Int?,      // 需要提醒的档位，null 表示不提醒
        val newLastAlerted: Int,  // 新的 L
        val crossed: Int,         // 本次一次性跨过了几档（≥2 表示跳空连跌）
        val reset: Boolean,       // 是否发生了回升重置
    )

    fun initial(start: Int, step: Int): Int = start + step

    /** 满足 t > p 的最小档位。 */
    fun tierAbove(p: Double, start: Int, step: Int): Int {
        val n = ceil((start - p) / step - 1e-9).toInt() - 1
        return start - step * n
    }

    /** 满足 p ≤ t 的最小档位。 */
    fun tierAtOrAbove(p: Double, start: Int, step: Int): Int {
        val n = floor((start - p) / step + 1e-9).toInt()
        return start - step * n
    }

    /**
     * @param p 用于判断的价格（通常是两次查询之间的最低价）
     * @param current 当前价，用于判断是否回升重置；默认与 p 相同
     */
    fun evaluate(p: Double, lastAlerted: Int, start: Int, step: Int, current: Double = p): Decision {
        require(step > 0) { "档位间隔必须大于 0" }
        if (current > lastAlerted + step && p > lastAlerted + step) {
            val newL = tierAbove(current, start, step)
            return Decision(null, newL, 0, reset = newL != lastAlerted)
        }
        if (p > start) return Decision(null, lastAlerted, 0, false)
        val d = tierAtOrAbove(p, start, step)
        if (d < lastAlerted) {
            val crossed = (minOf(lastAlerted, start + step) - d) / step
            return Decision(d, d, crossed.coerceAtLeast(1), false)
        }
        return Decision(null, lastAlerted, 0, false)
    }

    /** 下一个会触发提醒的档位（用于界面显示“下一档”）。 */
    fun nextTier(lastAlerted: Int, start: Int, step: Int): Int = minOf(lastAlerted - step, start)
}

/** 上涨提醒：涨到目标价提醒一次；回落到目标价下方超过 rearm 元后重新生效。 */
object RiseLogic {
    data class Decision(val fire: Boolean, val armed: Boolean)

    fun evaluate(price: Double, target: Double, armed: Boolean, rearm: Double): Decision = when {
        target <= 0 -> Decision(false, armed)
        armed && price >= target -> Decision(true, false)
        !armed && price < target - rearm -> Decision(false, true)
        else -> Decision(false, armed)
    }
}

/** 智能刷新间隔：离档位越近刷新越快。 */
object SmartInterval {
    /** @param distance 离最近一个提醒价位（下一档或上涨目标）的距离（元），可为负 */
    fun seconds(distance: Double, nearSeconds: Int): Int = when {
        distance > 8 -> 180
        distance > 3 -> 60
        else -> nearSeconds
    }
}

/** 交易时段（一律按北京时间计算，不受手机时区影响）。 */
object Session {
    private fun cal(ms: Long) = Calendar.getInstance(BJ).apply { timeInMillis = ms }

    /**
     * 上海金：日盘 周一至周五 9:00–15:30；夜盘 周一至周五 20:00–次日 2:30。
     * 法定节假日休市无法离线判断，靠“报价时间是否过期”兜底。
     */
    fun marketTrading(nowMs: Long): Boolean {
        val c = cal(nowMs)
        val dow = c.get(Calendar.DAY_OF_WEEK)
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val weekday = dow in Calendar.MONDAY..Calendar.FRIDAY
        if (weekday && m in (9 * 60) until (15 * 60 + 30)) return true
        if (weekday && m >= 20 * 60) return true
        if (dow in Calendar.TUESDAY..Calendar.SATURDAY && m < 2 * 60 + 30) return true
        return false
    }

    /** 工行积存金：周一至周五 9:10–22:30。 */
    fun bankTrading(nowMs: Long): Boolean {
        val c = cal(nowMs)
        val dow = c.get(Calendar.DAY_OF_WEEK)
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return dow in Calendar.MONDAY..Calendar.FRIDAY && m in (9 * 60 + 10) until (22 * 60 + 30)
    }

    /** 北京时间的日期 yyyyMMdd，用于判断“新的一天”。 */
    fun dayKey(ms: Long): Int {
        val c = cal(ms)
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }

    /**
     * 上海金一个交易日的起点：20:00 夜盘开始。
     * 给定某一时刻，返回它所属交易日的起点（周一的交易日从上周五 20:00 开始）。
     */
    fun marketDayStart(ms: Long): Long {
        val c = cal(ms)
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        if (m < 20 * 60) {
            c.add(Calendar.DAY_OF_MONTH, -1)
            while (c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                c.add(Calendar.DAY_OF_MONTH, -1)
            }
        }
        c.set(Calendar.HOUR_OF_DAY, 20); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /**
     * 把时刻映射到上海金交易日的时间轴（0..780 分钟）：
     * 20:00–02:30 → 0..390；09:00–15:30 → 390..780。不在时段内返回 null。
     */
    fun marketAxisMinute(ms: Long): Int? {
        val c = cal(ms)
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return when {
            m >= 20 * 60 -> m - 20 * 60
            m <= 2 * 60 + 30 -> m + 4 * 60
            m in (9 * 60)..(15 * 60 + 30) -> 390 + (m - 9 * 60)
            else -> null
        }
    }

    /** 工行时段轴：09:10–22:30 → 0..800。 */
    fun bankAxisMinute(ms: Long): Int? {
        val c = cal(ms)
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return if (m in (9 * 60 + 10)..(22 * 60 + 30)) m - (9 * 60 + 10) else null
    }
}

/** 持仓与交易明细的计算（移动加权平均成本）。 */
data class Trade(
    val id: Long,
    val buy: Boolean,
    val date: String,
    val grams: Double,
    val price: Double,
    val fee: Double,
) {
    val amount: Double get() = grams * price
}

data class Holding(val grams: Double, val cost: Double, val realized: Double) {
    val avgCost: Double get() = if (grams > 1e-9) cost / grams else 0.0
}

object LedgerMath {
    fun summarize(trades: List<Trade>): Holding {
        var grams = 0.0
        var cost = 0.0
        var realized = 0.0
        for (t in trades.sortedWith(compareBy({ it.date }, { it.id }))) {
            if (t.buy) {
                grams += t.grams
                cost += t.grams * t.price + t.fee
            } else {
                val g = minOf(t.grams, grams)
                val avg = if (grams > 1e-9) cost / grams else 0.0
                realized += g * t.price - t.fee - avg * g
                cost -= avg * g
                grams -= g
            }
        }
        if (grams < 1e-9) { grams = 0.0; cost = 0.0 }
        return Holding(grams, cost, realized)
    }
}

/**
 * 提醒时段外（如 23:00 之后的夜盘）的价格记录：只记最低、最高和最后价，不提醒；
 * 回到提醒时段后汇总提醒一次。
 */
object NightLogic {
    data class Night(val low: Double, val lowT: Long, val high: Double, val highT: Long, val last: Double, val lastT: Long, val label: String)

    fun merge(n: Night?, low: Double, high: Double, last: Double, t: Long, label: String): Night = when {
        n == null -> Night(low, t, high, t, last, t, label)
        else -> Night(
            low = minOf(n.low, low), lowT = if (low < n.low) t else n.lowT,
            high = maxOf(n.high, high), highT = if (high > n.high) t else n.highT,
            last = last, lastT = t, label = if (low < n.low) label else n.label,
        )
    }

    /** 一次跨过的各档位，从高到低，如 alertTier=830、crossed=2、step=10 → [840, 830]。 */
    fun tiers(alertTier: Int, crossed: Int, step: Int): List<Int> =
        (crossed - 1 downTo 0).map { alertTier + step * it }
}

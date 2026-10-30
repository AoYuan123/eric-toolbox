package com.eric.toolbox.modules.gold

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.floor

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

    /** 满足 p ≤ t 的最小档位（p 必须 ≤ start 才有意义）。 */
    fun tierAtOrAbove(p: Double, start: Int, step: Int): Int {
        val n = floor((start - p) / step + 1e-9).toInt()
        return start - step * n
    }

    fun evaluate(p: Double, lastAlerted: Int, start: Int, step: Int): Decision {
        require(step > 0) { "档位间隔必须大于 0" }
        if (p > lastAlerted + step) {
            val newL = tierAbove(p, start, step)
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

/** 上海黄金交易所交易时段（一律按北京时间计算，不受手机时区影响）。 */
object Session {
    private val TZ: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    /**
     * 日盘 周一至周五 9:00–15:30；夜盘 周一至周五 20:00–次日 2:30（周五夜盘延续到周六凌晨）。
     * 法定节假日休市无法离线判断，靠“报价时间是否过期”兜底。
     */
    fun isTrading(nowMs: Long): Boolean {
        val c = Calendar.getInstance(TZ).apply { timeInMillis = nowMs }
        val dow = c.get(Calendar.DAY_OF_WEEK) // 周日=1 … 周六=7
        val minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val weekday = dow in Calendar.MONDAY..Calendar.FRIDAY
        if (weekday && minutes in (9 * 60) until (15 * 60 + 30)) return true
        if (weekday && minutes >= 20 * 60) return true
        val dayAfterWeekday = dow in Calendar.TUESDAY..Calendar.SATURDAY
        if (dayAfterWeekday && minutes < 2 * 60 + 30) return true
        return false
    }
}

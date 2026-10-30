package com.eric.toolbox.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import java.util.TimeZone

/**
 * 全局运行时段：默认周一至周五 8:00–23:00。
 * 时段外后台服务完全停止；到点由精确闹钟唤醒。
 * 一律按北京时间计算，不受手机时区影响。
 */
object RunWindow {
    val TZ: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    fun startMin(ctx: Context) = Store.global(ctx).getInt("win_start", 8 * 60)
    fun endMin(ctx: Context) = Store.global(ctx).getInt("win_end", 23 * 60)
    fun weekendOff(ctx: Context) = Store.global(ctx).getBoolean("win_weekend_off", true)

    fun set(ctx: Context, startMin: Int, endMin: Int, weekendOff: Boolean) {
        Store.global(ctx).edit().putInt("win_start", startMin).putInt("win_end", endMin).putBoolean("win_weekend_off", weekendOff).apply()
    }

    fun isOpen(ctx: Context, nowMs: Long = System.currentTimeMillis()): Boolean =
        isOpen(nowMs, startMin(ctx), endMin(ctx), weekendOff(ctx))

    fun isOpen(nowMs: Long, start: Int, end: Int, weekendOff: Boolean): Boolean {
        val c = Calendar.getInstance(TZ).apply { timeInMillis = nowMs }
        val dow = c.get(Calendar.DAY_OF_WEEK)
        if (weekendOff && (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY)) return false
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return m in start until end
    }

    /** 下一次时段开始的时间点。 */
    fun nextOpen(nowMs: Long, start: Int, weekendOff: Boolean): Long {
        val c = Calendar.getInstance(TZ).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, start / 60); set(Calendar.MINUTE, start % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= nowMs) c.add(Calendar.DAY_OF_MONTH, 1)
        while (weekendOff && (c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)) {
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return c.timeInMillis
    }

    fun describe(ctx: Context): String {
        fun hm(m: Int) = String.format("%02d:%02d", m / 60, m % 60)
        return "${if (weekendOff(ctx)) "周一至周五 " else "每天 "}${hm(startMin(ctx))}–${hm(endMin(ctx))}"
    }

    /** 安排在下一个时段开始时唤醒。能用精确闹钟就用精确的，否则退回普通闹钟（可能晚几分钟）。 */
    fun scheduleWake(ctx: Context) {
        val at = nextOpen(System.currentTimeMillis(), startMin(ctx), weekendOff(ctx))
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 7, Intent(ctx, WakeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        Store.global(ctx).edit().putLong("next_wake", at).apply()
    }

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

/** 时段开始时被闹钟唤醒，拉起后台服务。 */
class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MonitorService.sync(context)
    }
}

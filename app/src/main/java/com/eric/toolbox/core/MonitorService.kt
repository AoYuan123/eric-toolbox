package com.eric.toolbox.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 统一的后台服务：一个常驻通知 + 为每个开启了后台任务的模块各跑一个循环。
 * 数据全天更新；提醒只在提醒时段（默认工作日 8:00–23:00）发出，由各模块自己判断。
 * 时段外各模块会放慢刷新；另外在提醒时段开始时用精确闹钟唤醒一次，作为保险。
 * 某个模块出错只会记录日志，不影响其他模块。
 */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = mutableMapOf<String, Job>()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        val n = Notifier.serviceNotification(this, "正在启动…")
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifier.ID_SERVICE, n, type)
        running = true
        Heartbeat.checkGap(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        syncJobs()
        return START_STICKY
    }

    private fun syncJobs() {
        RunWindow.scheduleWake(this)
        val wanted = ModuleRegistry.all.filter { it.background?.isEnabled(this) == true }
        val wantedIds = wanted.map { it.id }.toSet()
        jobs.keys.filter { it !in wantedIds }.forEach { jobs.remove(it)?.cancel() }
        if (wanted.isEmpty()) {
            Heartbeat.clear(this)
            stopSelf()
            return
        }
        for (m in wanted) {
            if (jobs[m.id]?.isActive == true) continue
            val task = m.background!!
            jobs[m.id] = scope.launch {
                while (isActive) {
                    try {
                        task.runOnce(this@MonitorService)
                    } catch (e: Exception) {
                        Log.w("MonitorService", "module ${m.id} failed", e)
                    }
                    refreshNotification()
                    updateWakeLock()
                    val now = System.currentTimeMillis()
                    var wait = task.nextDelayMs(this@MonitorService, now).coerceIn(5_000, 30 * 60_000)
                    // 时段外放慢刷新时，保证提醒时段一开始就醒来（汇总夜间提醒）
                    if (!RunWindow.isOpen(this@MonitorService, now)) {
                        val untilOpen = RunWindow.nextOpen(now, RunWindow.startMin(this@MonitorService), RunWindow.weekendOff(this@MonitorService)) - now + 5_000
                        wait = minOf(wait, untilOpen.coerceAtLeast(5_000))
                    }
                    Heartbeat.beat(this@MonitorService, now + wait)
                    delay(wait)
                }
            }
        }
    }

    private fun refreshNotification() {
        val lines = ModuleRegistry.all.mapNotNull { m ->
            val t = m.background ?: return@mapNotNull null
            if (!t.isEnabled(this)) null else t.statusLine(this)
        }
        val text = lines.joinToString("  ·  ").ifBlank { "运行中" }
        try {
            NotificationManagerCompat.from(this).notify(Notifier.ID_SERVICE, Notifier.serviceNotification(this, text))
        } catch (_: SecurityException) {
        }
    }

    /** 只在有模块需要准点刷新时（如接近档位）持有 CPU 唤醒锁，其余时间放开以省电。 */
    private fun updateWakeLock() {
        val now = System.currentTimeMillis()
        val need = ModuleRegistry.all.any { m -> m.background?.let { it.isEnabled(this) && it.needsPreciseTiming(this, now) } == true }
        if (need) {
            if (wakeLock == null) {
                wakeLock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EricToolbox:monitor").apply { setReferenceCounted(false) }
            }
            wakeLock?.acquire(20 * 60_000L) // 每轮续期，最长 20 分钟
        } else if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var running = false
            private set

        /** 根据模块开关启动或停止服务（全天运行）。可以安全地重复调用。 */
        fun sync(ctx: Context) {
            val any = ModuleRegistry.all.any { it.background?.isEnabled(ctx) == true }
            val i = Intent(ctx, MonitorService::class.java)
            if (!any) {
                Heartbeat.clear(ctx)
                if (running) ctx.stopService(i)
                return
            }
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                // Android 12+ 在后台启动前台服务可能被拒绝；关闭电池优化后可豁免
                Log.w("MonitorService", "start refused", e)
            }
        }
    }
}

/** 心跳：服务每跑一轮记录一次时间和“预计下次醒来时间”，用于判断后台是否还活着、记录“被系统停止”的时长。 */
object Heartbeat {
    fun beat(ctx: Context, expectNextMs: Long) = Store.global(ctx).edit()
        .putLong("heartbeat", System.currentTimeMillis()).putLong("hb_next", expectNextMs).apply()
    fun last(ctx: Context): Long = Store.global(ctx).getLong("heartbeat", 0L)
    fun expectedNext(ctx: Context): Long = Store.global(ctx).getLong("hb_next", 0L)
    fun clear(ctx: Context) = Store.global(ctx).edit().putLong("heartbeat", 0L).putLong("hb_next", 0L).apply()

    /** 服务重新启动时调用：比预计醒来时间晚了 5 分钟以上，就记一条系统记录。 */
    fun checkGap(ctx: Context) {
        val expect = expectedNext(ctx)
        if (expect == 0L) return
        val late = System.currentTimeMillis() - expect
        if (late > 5 * 60_000L) {
            AlertLog.add(
                ctx, AlertEntry(System.currentTimeMillis(), AlertLog.SYSTEM, "后台曾被系统停止约 ${late / 60_000} 分钟", "已自动恢复。频繁出现请检查“我的 › 后台保活设置”。"),
            )
        }
    }
}

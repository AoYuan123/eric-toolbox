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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        syncJobs()
        return START_STICKY
    }

    private fun syncJobs() {
        val wanted = ModuleRegistry.all.filter { it.background?.isEnabled(this) == true }
        val wantedIds = wanted.map { it.id }.toSet()
        jobs.keys.filter { it !in wantedIds }.forEach { jobs.remove(it)?.cancel() }
        if (wanted.isEmpty()) {
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
                    Heartbeat.beat(this@MonitorService)
                    refreshNotification()
                    updateWakeLock()
                    delay(task.nextDelayMs(this@MonitorService, System.currentTimeMillis()).coerceAtLeast(5_000))
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

    /** 只在有模块需要准点刷新时（如交易时段）持有 CPU 唤醒锁，其余时间放开以省电。 */
    private fun updateWakeLock() {
        val now = System.currentTimeMillis()
        val need = Store.global(this).getBoolean("precise_timing", true) &&
            ModuleRegistry.all.any { m -> m.background?.let { it.isEnabled(this) && it.needsPreciseTiming(this, now) } == true }
        if (need && wakeLock?.isHeld != true) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EricToolbox:monitor").apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L) // 上限 6 小时，循环里会持续续期
            }
        } else if (need) {
            wakeLock?.acquire(6 * 60 * 60 * 1000L)
        } else if (!need && wakeLock?.isHeld == true) {
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

        /** 有任何模块开启了后台任务就启动服务，否则停止。可以安全地重复调用。 */
        fun sync(ctx: Context) {
            val any = ModuleRegistry.all.any { it.background?.isEnabled(ctx) == true }
            val i = Intent(ctx, MonitorService::class.java)
            if (any) {
                try {
                    ContextCompat.startForegroundService(ctx, i)
                } catch (e: Exception) {
                    // Android 12+ 在后台启动前台服务可能被拒绝；关闭电池优化后可豁免
                    Log.w("MonitorService", "start refused", e)
                }
            } else if (running) {
                ctx.stopService(i)
            }
        }
    }
}

/** 心跳：服务每跑一轮记录一次时间，主页据此判断后台是否还活着。 */
object Heartbeat {
    fun beat(ctx: Context) = Store.global(ctx).edit().putLong("heartbeat", System.currentTimeMillis()).apply()
    fun last(ctx: Context): Long = Store.global(ctx).getLong("heartbeat", 0L)
}

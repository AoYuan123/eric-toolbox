package com.eric.toolbox.modules.family

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eric.toolbox.MainActivity
import com.eric.toolbox.R
import com.eric.toolbox.core.Notifier
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * 提醒。只做两件事：每天早上一声，以及一条能一键写进系统日历的入口。
 *
 * 为什么用 WorkManager 而不是 AlarmManager 精确闹钟：
 *  - 产检提醒差十几分钟完全没影响，不值得为它再要一个"精确闹钟"权限；
 *  - WorkManager 的每日任务在重启、换机恢复后自己会回来，AlarmManager 得自己写开机重排；
 *  - 代价是 Doze 下可能晚到几十分钟（实测早上 9 点前后半小时内居多）。这个代价认了。
 *
 * 一条通知覆盖"提前三天"和"当天"：每天检查一次，命中哪天就写哪天的话。
 * 通知 id 固定，所以不会一天堆一条，第二天自动顶掉前一天的。
 */
class FamilyReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { FamilyReminder.check(applicationContext) }
        return Result.success()
    }
}

object FamilyReminder {
    private const val CHANNEL = "family_plan"
    private const val NOTIFY_ID = 4242
    private const val WORK = "family_reminder"

    fun enabled(ctx: Context): Boolean = !FamilyStore.flag(ctx, "remind_off")

    fun setEnabled(ctx: Context, on: Boolean) {
        FamilyStore.setFlag(ctx, "remind_off", !on)
        if (on) schedule(ctx)
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "产检与疫苗提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "每天早上检查一次，只在有产检、疫苗或儿保的时候响"
                setShowBadge(true)
            }
        )
    }

    /** 每天 9:00 左右跑一次。重复排不会叠加，已排过就保持原样。 */
    fun schedule(ctx: Context) {
        if (!enabled(ctx)) return
        ensureChannel(ctx)
        val delay = nextMorning() - System.currentTimeMillis()
        val req = PeriodicWorkRequestBuilder<FamilyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()
        runCatching {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }

    private fun nextMorning(): Long {
        val now = ZonedDateTime.now(FamilyDate.ZONE)
        var at = now.withHour(9).withMinute(0).withSecond(0).withNano(0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    /** 跑一次检查，命中就发通知。Worker 和"测试提醒"都走这里。 */
    fun check(ctx: Context) {
        if (!enabled(ctx)) return
        val edd = FamilyStore.edd(ctx)
        val birth = FamilyStore.birth(ctx)
        if (edd == null && birth == null) return

        val today = FamilyDate.today()
        val rows = FamilyPlan.rows(edd, birth, FamilyStore.doneKeys(ctx))
        val hits = FamilyPlan.bumps(rows, today)
        if (hits.isEmpty()) return
        if (!Notifier.canPost(ctx)) return

        val first = hits.first()
        val title = if (first.due == today) "今天：${first.item.title}" else "还有 3 天：${first.item.title}"
        val body = buildString {
            append(first.item.note)
            val more = hits.size - 1
            if (more > 0) append("\n还有 $more 件在这几天，点开看时间表。")
        }
        post(ctx, title, body)
    }

    /** 设置页里点一下就能确认通知通不通，不用等到明天早上。 */
    fun test(ctx: Context) {
        ensureChannel(ctx)
        post(ctx, "提醒是通的", "有产检、疫苗或儿保的那天早上 9 点，我会在这里说一句。")
    }

    private fun post(ctx: Context, title: String, body: String) {
        if (!Notifier.canPost(ctx)) return
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_MODULE, "family")
        }
        val pi = PendingIntent.getActivity(
            ctx, NOTIFY_ID, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFY_ID, n) }
    }
}

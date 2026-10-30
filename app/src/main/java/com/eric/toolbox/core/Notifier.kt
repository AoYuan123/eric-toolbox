package com.eric.toolbox.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.eric.toolbox.MainActivity
import com.eric.toolbox.R

object Notifier {
    const val CH_SERVICE = "service"
    const val CH_ALERT = "alert"
    const val ID_SERVICE = 1

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "后台运行状态", NotificationManager.IMPORTANCE_LOW).apply {
                description = "常驻通知，显示实时价格，不响铃"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "到价提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "跌破档位等重要提醒，带声音和震动"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    private fun openApp(ctx: Context, moduleId: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (moduleId != null) putExtra(MainActivity.EXTRA_MODULE, moduleId)
        }
        return PendingIntent.getActivity(
            ctx, moduleId?.hashCode() ?: 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun serviceNotification(ctx: Context, text: String): android.app.Notification =
        NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Eric的百宝箱 · 后台运行中")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(ctx, null))
            .build()

    /** 发一条到价提醒，并写入提醒记录。 */
    fun alert(ctx: Context, moduleId: String, title: String, body: String) {
        AlertLog.add(ctx, AlertEntry(System.currentTimeMillis(), moduleId, title, body))
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, moduleId))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt() + 100, n)
        } catch (_: SecurityException) {
        }
    }
}

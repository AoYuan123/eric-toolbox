package com.eric.toolbox.core

import android.content.Context
import android.os.Build
import com.eric.toolbox.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 闪退记录：未捕获的异常写进 files/crash/（最多留 5 份），下次打开 App 时记进「提醒记录」（系统），体检页可查看、复制。
 * 只记录异常堆栈和机型/版本，不含任何个人数据或密钥。
 */
object CrashLog {
    private fun dir(ctx: Context) = File(ctx.filesDir, "crash").apply { mkdirs() }

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write(app, t, e) }
            prev?.uncaughtException(t, e)
        }
        runCatching { reportPending(app) }
    }

    private fun write(ctx: Context, t: Thread, e: Throwable) {
        val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
        val now = System.currentTimeMillis()
        val head = "time=${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(now))}\n" +
            "app=v${BuildConfig.VERSION_NAME}\ndevice=${Build.MANUFACTURER} ${Build.MODEL} Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\nthread=${t.name}\n\n"
        File(dir(ctx), "crash-$now.txt").writeText(head + sw.toString().take(20_000))
        File(dir(ctx), "pending").writeText(now.toString())
        dir(ctx).listFiles { f -> f.name.startsWith("crash-") }?.sortedByDescending { it.name }?.drop(5)?.forEach { it.delete() }
    }

    private fun reportPending(ctx: Context) {
        val p = File(dir(ctx), "pending")
        if (!p.isFile) return
        p.delete()
        val f = latest(ctx) ?: return
        val first = f.readLines().drop(5).firstOrNull { it.isNotBlank() }.orEmpty().take(160)
        AlertLog.add(ctx, AlertEntry(System.currentTimeMillis(), AlertLog.SYSTEM, "上次 App 闪退了", "$first（详情：我的 › 体检 › 闪退记录）"))
    }

    fun latest(ctx: Context): File? = dir(ctx).listFiles { f -> f.name.startsWith("crash-") }?.maxByOrNull { it.name }
    fun all(ctx: Context): List<File> = dir(ctx).listFiles { f -> f.name.startsWith("crash-") }?.sortedByDescending { it.name }.orEmpty()
    fun clear(ctx: Context) { dir(ctx).listFiles()?.forEach { it.delete() } }
}

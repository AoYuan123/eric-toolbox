package com.eric.toolbox.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 在 App 内下载新版安装包并调起系统安装界面，和常规软件的“检查更新 → 下载 → 安装”一样。
 * - 下载地址的跳转（如 Gitee → foruda.gitee.com）自己跟随，只允许 https；
 * - 下载完先确认是本 App、版本号对得上，再交给系统安装（系统还会校验签名是否一致）；
 * - Android 8 以上第一次安装需要用户允许“安装未知应用”，没开时直接带到设置页。
 */
object ApkInstaller {
    private const val MAX_BYTES = 200L * 1024 * 1024

    private fun dir(ctx: Context) = File(ctx.cacheDir, "updates").apply { mkdirs() }

    /** 启动时调用：已经装上（版本号不比本机新）的安装包、下到一半的残片都删掉，不留垃圾。返回删掉的字节数。 */
    fun cleanup(ctx: Context): Long {
        var freed = 0L
        File(ctx.cacheDir, "updates").listFiles()?.forEach { f ->
            val code = Regex("update-(\\d+)").find(f.name)?.groupValues?.get(1)?.toIntOrNull()
            if (code == null || code <= com.eric.toolbox.BuildConfig.VERSION_CODE || f.name.contains("-part")) { freed += f.length(); f.delete() }
        }
        return freed
    }

    /** 下载到缓存目录，onProgress(0..100，未知总大小时为 -1)。返回下载好的文件。 */
    suspend fun download(ctx: Context, url: String, versionCode: Int, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        dir(ctx).listFiles()?.forEach { it.delete() } // 清掉以前的安装包
        val out = File(dir(ctx), "update-$versionCode.apk")
        val part = File(dir(ctx), "update-$versionCode-part.apk") // 系统解析安装包要求 .apk 后缀
        var current = url
        var conn: HttpURLConnection? = null
        try {
            for (hop in 0..5) {
                require(current.startsWith("https://")) { "下载地址不安全（不是 https）" }
                val c = URL(current).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) EricToolbox")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw java.io.IOException("下载跳转地址缺失")
                    current = URL(URL(current), loc).toString()
                    c.disconnect()
                    if (hop == 5) throw java.io.IOException("下载跳转次数过多")
                    continue
                }
                if (code !in 200..299) { c.disconnect(); throw java.io.IOException("下载失败：HTTP $code") }
                conn = c
                break
            }
            val c = conn ?: throw java.io.IOException("下载失败")
            val total = c.contentLengthLong
            var done = 0L
            var lastPct = -2
            c.inputStream.use { input ->
                part.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        if (done > MAX_BYTES) throw java.io.IOException("文件过大")
                        val pct = if (total > 0) (done * 100 / total).toInt() else -1
                        if (pct != lastPct) { lastPct = pct; withContext(Dispatchers.Main) { onProgress(pct) } }
                    }
                }
            }
            if (total > 0 && done != total) throw java.io.IOException("下载不完整，请重试")
        } finally {
            conn?.disconnect()
        }
        verify(ctx, part, versionCode)
        if (!part.renameTo(out)) throw java.io.IOException("保存安装包失败")
        out
    }

    /** 确认下载的是本 App 的安装包，并且版本号就是要更新的那个。 */
    private fun verify(ctx: Context, f: File, versionCode: Int) {
        val head = ByteArray(2)
        f.inputStream().use { it.read(head) }
        if (head[0] != 'P'.code.toByte() || head[1] != 'K'.code.toByte()) throw java.io.IOException("下载的不是安装包（可能被网络劫持或需要登录），请重试")
        val info = ctx.packageManager.getPackageArchiveInfo(f.absolutePath, 0) ?: throw java.io.IOException("安装包损坏，请重试")
        if (info.packageName != ctx.packageName) throw java.io.IOException("安装包不是本 App")
        @Suppress("DEPRECATION")
        val vc = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
        if (vc != versionCode) throw java.io.IOException("安装包版本（$vc）与预期（$versionCode）不一致")
    }

    /** 是否已允许本 App 安装应用（Android 8 以上需要用户在设置里打开一次）。 */
    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** 调起系统安装界面。 */
    fun install(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** 已经下载好、可以直接安装的文件（比如去设置里开权限回来后）。 */
    fun downloaded(ctx: Context, versionCode: Int): File? = File(dir(ctx), "update-$versionCode.apk").takeIf { it.isFile }
}

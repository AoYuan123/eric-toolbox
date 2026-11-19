package com.eric.toolbox.modules.family

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/** 文件 → 可交给系统相机/其它 App 的 content:// 地址（FileProvider 已在清单里注册）。 */
object FamilyCapture {
    fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
}

/**
 * 录音。用 MediaRecorder 直接出 m4a（AAC），不引入新库。
 *
 * 来电、其它 App 抢麦克风时 MediaRecorder 会抛异常并停掉，
 * 这时 [stop] 返回已经录到的时长，而不是丢掉整段——在诊室被电话打断是很可能的，
 * 已经录到的医生那半句不能白丢。
 */
class AudioRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var startedAt = 0L
    private var file: File? = null

    val running: Boolean get() = rec != null

    fun start(target: File): Boolean {
        if (rec != null) return false
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(target.absolutePath)
            r.prepare()
            r.start()
            rec = r
            file = target
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            runCatching { target.delete() }
            rec = null
            file = null
            false
        }
    }

    /** 返回录到的毫秒数；0 表示什么都没录上（此时文件已删）。 */
    fun stop(): Long {
        val r = rec ?: return 0L
        rec = null
        val elapsed = System.currentTimeMillis() - startedAt
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        val f = file
        file = null
        if (!ok || f == null || !f.isFile || f.length() < 1024) {
            f?.let { runCatching { it.delete() } }
            return 0L
        }
        return elapsed
    }

    fun cancel() {
        val r = rec ?: return
        rec = null
        runCatching { r.stop() }
        runCatching { r.release() }
        file?.let { runCatching { it.delete() } }
        file = null
    }
}

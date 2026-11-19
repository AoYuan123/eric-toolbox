package com.eric.toolbox.modules.family

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 片子里这一段用图还是用动的那几秒。 */
enum class ClipMode { STILL, MOTION }

/**
 * 成片里的一段。
 *
 * [source] 在编辑台打开时就解析好了：普通照片就是那张 jpg；
 * 实况照片、视频是他们各自的 mp4（实况的那段已经抽到缓存里）。
 */
data class Clip(
    val entry: Entry,
    val source: File,
    val mode: ClipMode,
    /** 照片是停留秒数；视频是裁剪上限。 */
    val seconds: Int,
    /** 源本身多长（照片为 0）。 */
    val durMs: Long,
) {
    /** 这段在片子里实际占多长。 */
    val shownMs: Long
        get() = if (mode == ClipMode.STILL) seconds * 1000L
        else minOf(if (durMs > 0) durMs else seconds * 1000L, seconds * 1000L).coerceAtLeast(800L)
}

/**
 * 把绳子上的素材渲染成一个 mp4。
 *
 * 为什么要渲染两遍：
 *   Media3 的硬性约束是「一个序列里不能同时出现视频和图片」，也不能一条序列只有图片、另一条只有视频再叠在一起
 *   （同一轨道的序列不允许在时间上重叠）。所以只能先把每一段归一化成"同样大小、同样编码、都有音轨"的小片子，
 *   再把这些小片子拼成一条纯视频序列。
 *
 * 归一化的另一个好处是稳：用户的素材有 720p 也有 4K，有 H.264 也有 HEVC，
 * 竖的横的都有。不统一就拼，某些手机上会花屏或者后半段黑掉。
 *
 * 代价是慢：60 秒的片子，两遍下来一般 1–3 分钟，手机还会发热。
 * 所以这里把每一段都缓存成文件，同一段第二次做片子直接复用，不再转。
 */
class MovieExporter(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var running: Transformer? = null

    fun cancel() {
        runCatching { running?.cancel() }
        job?.cancel()
        running = null
    }

    fun start(
        clips: List<Clip>,
        onProgress: (Float, String) -> Unit,
        onDone: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (clips.isEmpty()) {
            onError("还没有可以放进片子的素材")
            return
        }
        job?.cancel()
        job = scope.launch {
            try {
                val out = compose(clips, onProgress)
                onProgress(1f, "好了")
                onDone(out)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                onError(friendly(t))
            }
        }
    }

    private suspend fun compose(clips: List<Clip>, onProgress: (Float, String) -> Unit): File {
        val clipCache = File(ctx.cacheDir, "family_clips").apply { mkdirs() }
        val outDir = File(ctx.filesDir, "family/movies").apply { mkdirs() }

        // 两遍的工作量按"每段归一化 = 1 份，拼接 = 段数 份"估，进度才对得上体感。
        val total = clips.size * 2f
        val normalized = ArrayList<File>(clips.size)

        clips.forEachIndexed { i, clip ->
            val f = normalize(clip, clipCache) { p ->
                onProgress((i + p) / total, "正在处理第 ${i + 1} 段，共 ${clips.size} 段")
            }
            normalized += f
        }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
        val out = File(outDir, "memory_$stamp.mp4")
        if (out.exists()) out.delete()

        val items = normalized.map {
            EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(it))).build()
        }
        val composition = Composition.Builder(listOf(EditedMediaItemSequence(items))).build()
        render(composition, out) { p ->
            onProgress((clips.size + p * clips.size) / total, "正在合成，别退出")
        }
        return out
    }

    /** 把一段变成 1080×1920、30fps、H.264 + AAC 的标准小片子，并缓存。 */
    private suspend fun normalize(clip: Clip, cache: File, onProgress: (Float) -> Unit): File {
        val key = "${clip.entry.id}_${clip.seconds}_${clip.mode.name}"
        val cached = File(cache, "$key.mp4")
        if (cached.isFile && cached.length() > 20_000L) {
            onProgress(1f)
            return cached
        }
        if (!clip.source.isFile) throw IllegalStateException("有一段素材找不到了")

        val item = if (clip.mode == ClipMode.STILL) {
            // 图片必须显式给时长和帧率，否则 Transformer 不知道这一帧该停多久。
            EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(clip.source)))
                .setDurationUs(clip.seconds * 1_000_000L)
                .setFrameRate(30)
                .build()
        } else {
            val mb = MediaItem.Builder().setUri(Uri.fromFile(clip.source))
            // 只在源比上限长时才裁——对短片设一个超过自己长度的终点，某些解码器会直接报错。
            if (clip.durMs > clip.seconds * 1000L) {
                mb.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setEndPositionMs(clip.seconds * 1000L)
                        .build()
                )
            }
            EditedMediaItem.Builder(mb.build()).build()
        }

        val composition = Composition.Builder(listOf(EditedMediaItemSequence(listOf(item))))
            // 图片没有音轨，强制补一条静音轨：后面拼接时所有片段都有音轨，格式才一致。
            .experimentalSetForceAudioTrack(true)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf<Effect>(
                        Presentation.createForWidthAndHeight(
                            1080, 1920, Presentation.LAYOUT_SCALE_TO_FIT,
                        )
                    ),
                )
            )
            .build()

        val tmp = File(cache, "$key.tmp.mp4")
        if (tmp.exists()) tmp.delete()
        render(composition, tmp, onProgress)
        if (cached.exists()) cached.delete()
        tmp.renameTo(cached)
        return cached
    }

    /** 跑一次渲染。Transformer 必须在有 Looper 的线程上创建和使用，所以全程在主线程。 */
    private suspend fun render(composition: Composition, out: File, onProgress: (Float) -> Unit) {
        val done = CompletableDeferred<Unit>()
        val holder = ProgressHolder()

        val transformer = Transformer.Builder(ctx)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    done.complete(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    done.completeExceptionally(exportException)
                }
            })
            .build()
        running = transformer

        val poller = scope.launch {
            while (isActive) {
                val state = transformer.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress / 100f)
                }
                delay(250)
            }
        }

        try {
            transformer.start(composition, out.absolutePath)
            done.await()
        } finally {
            poller.cancel()
            running = null
        }
    }

    private fun friendly(t: Throwable): String = when {
        t is ExportException -> "合成失败了（错误码 ${t.errorCode}）。多半是某个视频的编码这台手机不支持，把那一段去掉再试。"
        t.message?.contains("No space", true) == true -> "空间不够了，先清一点再试。"
        else -> "合成失败了：${t.message ?: t.javaClass.simpleName}"
    }
}

/** 编辑台要用：读一段素材自己的长度。读不到就返回 0，后面按上限处理。 */
object ClipProbe {
    fun durationMs(file: File): Long = runCatching {
        // 不用 use{}：MediaMetadataRetriever 到 API 29 才实现 AutoCloseable，minSdk 26 上 close() 不存在。
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            runCatching { r.release() }
        }
    }.getOrDefault(0L)
}

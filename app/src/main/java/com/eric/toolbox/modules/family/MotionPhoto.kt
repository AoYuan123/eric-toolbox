package com.eric.toolbox.modules.family

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/**
 * 实况照片（动态照片）的解析与提取。
 *
 * 所谓实况照片，就是一张普通 JPEG，后面直接接了一段 1–3 秒的 MP4；
 * JPEG 头里的 XMP 写着"视频有多长"，从文件末尾倒数这么多字节就是视频起点。
 * 另存的时候只要把这段字节抄出来，就是一段能直接播的 mp4——不解码、不转码、无损。
 *
 * 两种标准都要认，因为国内机型一半一半：
 *  1. 旧标准（小米 MIUI、多数国产）：GCamera:MicroVideoOffset = 视频字节数
 *  2. 新标准（Pixel、三星、HyperOS 3、以及 Android 官方规范）：
 *     Container:Directory 里 Semantic=MotionPhoto 那一项带 Item:Length
 * 两套都是"从文件末尾倒数的字节数"，所以处理方式一样：size - length 就是起点。
 *
 * 兜底：XMP 被图床或微信抹掉时（很常见），直接扫尾部找 MP4 的 ftyp 盒子。
 * 这也是 live-photo-conv 那类工具的做法，比只信元数据可靠。
 *
 * 找不到就返回 null，界面按普通照片处理——绝不弹"解析失败"。
 */
object MotionPhoto {
    private const val HEAD_BYTES = 512 * 1024        // XMP 一定在文件前部
    private const val SCAN_BYTES = 8 * 1024 * 1024   // 兜底扫描只看尾部 8MB

    /** 视频在文件里的位置。 */
    data class Span(val start: Long, val length: Long)

    fun probe(file: File): Span? {
        if (!file.isFile) return null
        val size = file.length()
        if (size < 200_000L) return null

        val head = runCatching { readHead(file, HEAD_BYTES) }.getOrNull() ?: return null
        // XMP 是 ASCII/UTF-8 的 XML，用 ISO-8859-1 读不会丢字节，正则照样能匹配。
        val text = String(head, Charsets.ISO_8859_1)

        val lengths = LinkedHashSet<Long>()
        numberAfter(text, "GCamera:MicroVideoOffset")?.let { lengths += it }
        Regex("Item:Length\\s*=\\s*\"(\\d+)\"|Item:Length>\\s*(\\d+)\\s*<").findAll(text).forEach { m ->
            val raw = m.groupValues[1].ifEmpty { m.groupValues[2] }
            raw.toLongOrNull()?.let { lengths += it }
        }

        // 多个候选取第一个"倒推回去真是 MP4 开头"的，这样增益图那种 Length 会被自动排除。
        for (len in lengths) {
            if (len <= 0 || len >= size) continue
            val start = size - len
            if (looksLikeMp4(file, start)) return Span(start, len)
        }

        val scanned = scanTailForVideo(file, size) ?: return null
        return Span(scanned, size - scanned)
    }

    /** 这个文件是不是实况照片。 */
    fun isLive(file: File): Boolean = probe(file) != null

    /**
     * 把实况照片里的那段视频抄到缓存目录，返回可直接播放的文件。
     * 抄一次就缓存，同一条记录重复放映不会重复 IO。
     */
    fun videoOf(ctx: Context, entry: Entry): File? {
        if (!entry.isLive) return null
        val src = FamilyStore.fileOf(ctx, entry.file)
        if (!src.isFile) return null

        val dir = File(ctx.cacheDir, "family_live").apply { mkdirs() }
        val out = File(dir, entry.file.substringBeforeLast('.') + ".mp4")
        if (out.isFile && out.length() > 10_000L) return out

        val span = probe(src) ?: return null
        return runCatching {
            val tmp = File(dir, out.name + ".tmp")
            RandomAccessFile(src, "r").use { raf ->
                raf.seek(span.start)
                tmp.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    var left = span.length
                    while (left > 0) {
                        val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n <= 0) break
                        os.write(buf, 0, n)
                        left -= n
                    }
                }
            }
            if (out.exists()) out.delete()
            tmp.renameTo(out)
            if (out.length() > 10_000L) out else null
        }.getOrNull()
    }

    // ---------------------------------------------------------------

    private fun readHead(file: File, max: Int): ByteArray =
        RandomAccessFile(file, "r").use { raf ->
            val len = minOf(max.toLong(), raf.length()).toInt()
            val buf = ByteArray(len)
            raf.readFully(buf)
            buf
        }

    /** 同时支持属性写法和元素写法：`Key="123"` 与 `<Key>123</Key>`。 */
    private fun numberAfter(text: String, key: String): Long? {
        Regex("$key\\s*=\\s*\"(\\d+)\"").find(text)?.let {
            return it.groupValues[1].toLongOrNull()
        }
        Regex("<$key>\\s*(\\d+)\\s*</$key>").find(text)?.let {
            return it.groupValues[1].toLongOrNull()
        }
        return null
    }

    /** 起点处应该是一个 MP4 盒子：4 字节长度 + "ftyp"。 */
    private fun looksLikeMp4(file: File, start: Long): Boolean {
        if (start <= 0L || start >= file.length() - 16L) return false
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buf = ByteArray(12)
                if (raf.read(buf) < 12) return@use false
                val type = String(buf, 4, 4, Charsets.ISO_8859_1)
                val boxSize = ((buf[0].toLong() and 0xFF) shl 24) or
                    ((buf[1].toLong() and 0xFF) shl 16) or
                    ((buf[2].toLong() and 0xFF) shl 8) or
                    (buf[3].toLong() and 0xFF)
                type == "ftyp" && boxSize in 8L..4096L
            }
        }.getOrDefault(false)
    }

    /** XMP 丢了就在文件尾部找 ftyp；取最后一个合法的（视频总是接在最后）。 */
    private fun scanTailForVideo(file: File, size: Long): Long? = runCatching {
        val from = maxOf(0L, size - SCAN_BYTES)
        val want = (size - from).toInt()
        val buf = ByteArray(want)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(from)
            var read = 0
            while (read < want) {
                val n = raf.read(buf, read, want - read)
                if (n <= 0) break
                read += n
            }
            var last = -1
            var i = 4
            while (i + 4 <= read) {
                if (buf[i] == 0x66.toByte() && buf[i + 1] == 0x74.toByte() &&
                    buf[i + 2] == 0x79.toByte() && buf[i + 3] == 0x70.toByte()
                ) {
                    val boxStart = i - 4
                    val boxSize = ((buf[boxStart].toLong() and 0xFF) shl 24) or
                        ((buf[boxStart + 1].toLong() and 0xFF) shl 16) or
                        ((buf[boxStart + 2].toLong() and 0xFF) shl 8) or
                        (buf[boxStart + 3].toLong() and 0xFF)
                    if (boxSize in 8L..4096L) last = boxStart
                }
                i++
            }
            if (last < 0) null else (from + last).takeIf { looksLikeMp4(file, it) }
        }
    }.getOrNull()
}

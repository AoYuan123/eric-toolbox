package com.eric.toolbox.modules.vod.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 探测一条播放线路的真实情况。
 *
 * 为什么需要它：同一部片在不同站、不同线路上的码率能差一倍以上，而站点给的
 * "清晰度"标签完全不可信——标着 1080P 的实际可能只有 1080×608、800 kbps。
 * 与其让用户挨个点开试，不如播之前先问一句"这条线到底多重"。
 *
 * 代价是每条线路多一次几 KB 的请求，只在详情页做，且并发、失败不影响主流程。
 */
object LineProbe {

    data class Info(
        /** 峰值码率，单位 kbps。0 表示拿不到（多为单码率的子播放列表） */
        val kbps: Int = 0,
        /** 最高分辨率，如 1920x1080。空表示没标 */
        val resolution: String = "",
        /** 是否带独立字幕轨 */
        val hasSubtitles: Boolean = false,
        /** 这个地址能不能用 */
        val reachable: Boolean = false,
        /** 不是 m3u8 的情况：直链 mp4、或需要解析的分享页 */
        val note: String = "",
    ) {
        val hasBandwidth: Boolean get() = kbps > 0

        /** 给 UI 用的一行短描述 */
        fun summary(): String = when {
            !reachable -> note.ifBlank { "连不上" }
            kbps <= 0 -> note.ifBlank { "可播" }
            else -> buildString {
                append(fmtKbps(kbps))
                if (resolution.isNotBlank()) append(" · ").append(resolution)
                if (hasSubtitles) append(" · 带字幕")
            }
        }

        private fun fmtKbps(v: Int): String =
            if (v >= 1000) {
                // 固定用 US 格式化：部分地区小数点是逗号，会显示成「1,5 Mbps」
                String.format(java.util.Locale.US, "%.1f Mbps", v / 1000.0)
            } else {
                "$v kbps"
            }
    }

    /**
     * 探测一集。传空地址、非 m3u8 地址都会立刻返回，不发请求。
     *
     * @param referer 部分 CDN 靠这个头放行，传站点域名即可
     */
    suspend fun probe(url: String, ua: String = "", referer: String = "", timeoutMs: Int = 6000): Info =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext Info(note = "没有地址")
            if (!url.contains(".m3u8", ignoreCase = true)) {
                // mp4 直链不需要解析，能点开就能播，不要为此多说一句废话
                return@withContext Info(reachable = true, note = if (url.contains(".mp4")) "MP4 直链" else "非 m3u8")
            }
            val headers = buildMap {
                if (ua.isNotBlank()) put("User-Agent", ua)
                if (referer.isNotBlank()) put("Referer", referer)
            }
            val text = runCatching { VodHttp.get(url, headers, timeoutMs) }.getOrNull()
                ?: return@withContext Info(note = "连不上")
            if (!text.contains("#EXTM3U")) {
                return@withContext Info(note = "不是播放列表")
            }
            val bws = BANDWIDTH.findAll(text)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .toList()
            val res = RESOLUTION.findAll(text)
                .map { it.groupValues[1] }
                .maxByOrNull { pixelsOf(it) }
                .orEmpty()
            Info(
                // m3u8 里的 BANDWIDTH 单位是 bit/s，换成 kbps 更好读
                kbps = if (bws.isEmpty()) 0 else (bws.max() / 1000),
                resolution = res,
                hasSubtitles = text.contains("TYPE=SUBTITLES", ignoreCase = true),
                reachable = true,
            )
        }

    private val BANDWIDTH = Regex("BANDWIDTH=(\\d+)")
    private val RESOLUTION = Regex("RESOLUTION=(\\d+)x(\\d+)")

    private fun pixelsOf(r: String): Int {
        val m = RESOLUTION.find(r) ?: return 0
        val w = m.groupValues[1].toIntOrNull() ?: return 0
        val h = m.groupValues[2].toIntOrNull() ?: return 0
        return w * h
    }
}

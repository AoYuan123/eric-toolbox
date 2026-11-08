package com.eric.toolbox.modules.vod.core

import com.eric.toolbox.modules.vod.model.Episode
import com.eric.toolbox.modules.vod.model.PlayGroup

/**
 * 解析 CMS 站点的播放地址串。
 *
 * 标准写法是三层分隔：
 *   源1$$$源2$$$源3          （$$$ 分隔线路/播放源，与 vod_play_from 顺序对应）
 *   第1集$url#第2集$url      （# 分隔集，$ 分隔集名与地址）
 *
 * 现实里的脏数据很多：某一段缺失、没有集名、混进换行。
 * 这里的原则是「能救回来就救」，救不回来的整段丢掉，绝不留半截坏地址给播放器。
 */
object EpisodeParser {

    private const val GROUP_SEP = "\$\$\$"
    /** 个别站点写成六位 dollar，等价处理。Kotlin 里 $ 必须转义，别写成裸的这种写法。 */
    private const val GROUP_SEP_ALT = "\$\$\$\$\$\$"
    private const val EP_SEP = "#"

    fun parse(from: String, url: String, allowOpaque: Boolean = false): List<PlayGroup> {
        val urlRaw = url.replace(GROUP_SEP_ALT, GROUP_SEP)
        val fromRaw = from.replace(GROUP_SEP_ALT, GROUP_SEP)

        val names = fromRaw.split(GROUP_SEP)
        val blocks = urlRaw.split(GROUP_SEP)
        val n = maxOf(names.size, blocks.size)

        val out = ArrayList<PlayGroup>()
        for (i in 0 until n) {
            val src = names.getOrElse(i) { "" }.trim().ifBlank { "线路 ${i + 1}" }
            val eps = episodesOf(blocks.getOrElse(i) { "" }, allowOpaque)
            if (eps.isNotEmpty()) out += PlayGroup(src, eps)
        }
        return out
    }

    /**
     * @param allowOpaque 允许「不是网址」的集地址。
     *
     * Spider 站点（尤其云盘类）给的不是媒体地址，而是一串 token / 分享口令，
     * 要在播放前交给插件的 playerContent 才变成真地址。以前一刀切只留 http(s)，
     * 结果这类站点的每一集都被丢掉，详情页直接显示「没有返回播放地址」——
     * 明明站点给了数据。所以按站点类型放开，直连 CMS 站点仍然只认 http(s)。
     */
    private fun episodesOf(block: String, allowOpaque: Boolean): List<Episode> {
        val out = ArrayList<Episode>()
        for (seg in block.split(EP_SEP, "\n", "\r")) {
            val s = seg.trim()
            if (s.isBlank()) continue
            val idx = s.indexOf('$')
            if (idx > 0) {
                val name = s.substring(0, idx).trim().ifBlank { "${out.size + 1}" }
                val u = s.substring(idx + 1).trim()
                if (u.isBlank()) continue
                if (allowOpaque || u.startsWith("http://") || u.startsWith("https://")) {
                    out += Episode(name, u)
                }
            } else if (s.startsWith("http://") || s.startsWith("https://")) {
                out += Episode("${out.size + 1}", s)
            }
        }
        return out
    }
}

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

    fun parse(from: String, url: String): List<PlayGroup> {
        val urlRaw = url.replace(GROUP_SEP_ALT, GROUP_SEP)
        val fromRaw = from.replace(GROUP_SEP_ALT, GROUP_SEP)

        val names = fromRaw.split(GROUP_SEP)
        val blocks = urlRaw.split(GROUP_SEP)
        val n = maxOf(names.size, blocks.size)

        val out = ArrayList<PlayGroup>()
        for (i in 0 until n) {
            val src = names.getOrElse(i) { "" }.trim().ifBlank { "线路 ${i + 1}" }
            val eps = episodesOf(blocks.getOrElse(i) { "" })
            if (eps.isNotEmpty()) out += PlayGroup(src, eps)
        }
        return out
    }

    private fun episodesOf(block: String): List<Episode> {
        val out = ArrayList<Episode>()
        for (seg in block.split(EP_SEP, "\n", "\r")) {
            val s = seg.trim()
            if (s.isBlank()) continue
            val idx = s.indexOf('$')
            if (idx > 0) {
                val name = s.substring(0, idx).trim().ifBlank { "${out.size + 1}" }
                val u = s.substring(idx + 1).trim()
                // 只认 http(s)，脏数据里的相对路径、广告文案一律丢弃
                if (u.startsWith("http://") || u.startsWith("https://")) {
                    out += Episode(name, u)
                }
            } else if (s.startsWith("http://") || s.startsWith("https://")) {
                out += Episode("${out.size + 1}", s)
            }
        }
        return out
    }
}

package com.eric.toolbox.modules.pan.core

/**
 * 标题归一化。
 *
 * 目的：把「【夸克网盘】星际穿越 Interstellar (2014) 4K HDR 中英双字.mp4」
 * 和「星际穿越 Interstellar 2014 2160p 国英双语」认成同一部片子，
 * 才能做同名聚合去重。
 *
 * 规则来自原始 Python 实现，这里逐条对应，别随意删改——
 * 每一条都是踩过的坑（比如 \b 在中英混排里失效，必须用前后瞻）。
 */
object TitleNormalizer {

    /** 文件后缀。注意 $ 必须转义，raw string 里写 $ 会触发模板解析 */
    private val EXT = Regex(
        "\\.(?:txt|epub|mobi|pdf|azw3|mp4|mkv|ts|flv|avi|mov|rmvb|wmv|iso|zip|rar|7z)\$",
        RegexOption.IGNORE_CASE,
    )

    /** 括号内容：《【夸克网盘】》《(4K)》之类。开头的 [ 不能省，少了整条正则就错位 */
    private val BRACKET = Regex("""[\[【（(]([^\[\]()【】（）]+)[\]】）)]""")

    /** 年份，如 (2014) */
    private val YEAR_ONLY = Regex("""(?:19|20)\d{2}""")

    /** 季数，如 S01 / 第一季 */
    private val SEASON = Regex(
        "(?:^|[^\\w])(?:S\\d+|第[一二三四五六七八九十\\d]+季)(?:\\$|[^\\w])",
        RegexOption.IGNORE_CASE,
    )

    /** 判断括号里是不是纯噪词/渠道词（整串匹配，不是包含） */
    private val NOISE_WHOLE = Regex(
        """(?:4k|8k|1080p|720p|2160p|hd|uhd|fhd|hevc|h\.?265|h\.?264|x265|x264|avc|remux|web-?dl|web-?rip|bdrip|bluray|blu-ray|""" +
            """超清|高清|高码率|高码|原盘|自制|修复版|珍藏版|蓝光|国粤双语|双语|国语|粤语|中字|双字|无字|熟肉|生肉|压制|""" +
            """完整版|未删减版|未删减|精校版|校对版|精校全本|全集|合集|全\d+集|全\d+期|更新至\d+集|更新至\d+期|更至\d+集|更至\d+期|更新至\d+|更至\d+|第\d+集|第\d+期|单文件|""" +
            """夸克网盘|夸克|阿里云盘|阿里|百度网盘|百度|迅雷网盘|迅雷|UC网盘|UC|115网盘|115|网盘资源|网盘|资源|分享|链接|""" +
            """美剧|韩剧|日剧|国产剧|国剧|港剧|台剧|泰剧|英剧|电视剧|电影|动漫|纪录片|综艺|""" +
            """内嵌|官译|杜比|视界|HDR|SDR|60帧|120帧|无水印|去水印|纯净版|防失联|扫码|关注)+""",
        RegexOption.IGNORE_CASE,
    )

    /** 英文画质编码噪词：前后必须不是字母数字，避免误伤片名里的 4k / hd 等字串 */
    private val NOISE_EN = Regex(
        """(?<![a-zA-Z0-9])(?:4k|8k|1080p|720p|2160p|hd|uhd|fhd|hevc|h\.?265|h\.?264|x265|x264|avc|remux|web-?dl|web-?rip|bdrip|bluray|blu-ray)(?![a-zA-Z0-9])""",
        RegexOption.IGNORE_CASE,
    )

    /** 中文画质/语言噪词（比原 Python 版多补了几个常见字幕标签，聚合效果更好） */
    private val NOISE_CN = Regex(
        """超清|高清|高码率|高码|原盘|自制|修复版|珍藏版|蓝光|国粤双语|双语|国语|粤语|中英双字|中英字幕|中文字幕|中字|双字|简繁|简中|繁中|无字|熟肉|生肉|压制""",
    )

    /** 状态与版本词 */
    private val STATE_WORDS = Regex(
        """完整版|未删减版|未删减|精校版|校对版|精校全本|全集|合集|全\d+集|全\d+期|更新至\d+集|更新至\d+期|更至\d+集|更至\d+期|更新至\d+|更至\d+|第\d+集|第\d+期|单文件""",
    )

    /** 渠道与网盘词：长的写前面，否则「夸克」会先把「夸克网盘」吃掉一半 */
    private val CHANNEL_WORDS = Regex(
        """夸克网盘|夸克|阿里云盘|阿里|百度网盘|百度|迅雷网盘|迅雷|UC网盘|UC|115网盘|115|网盘资源|网盘|资源|分享|链接""",
    )

    private val EMPTY_BRACKET = Regex("""[\[(（【]\s*[\])）】]""")
    private val BLANKS = Regex("""[\s\-_/\\|,，]+""")

    /**
     * 归一化标题。返回值可能是空串（标题全是噪词时），
     * 调用方遇到空串应退化为用 URL 做分组 key。
     */
    fun normalize(note: String?): String {
        val raw = note ?: return ""
        if (raw.isBlank()) return ""

        var s = unescapeHtml(raw)
        s = toHalfWidth(s)
        s = EXT.replace(s, "")

        // 括号处理：年份/季数保留，纯噪词删除，其它去括号保留文字
        s = BRACKET.replace(s) { m ->
            val content = m.groupValues[1].trim()
            when {
                YEAR_ONLY.matches(content) -> "($content)"
                SEASON.containsMatchIn(content) -> " $content "
                NOISE_WHOLE.matches(content.replace(Regex("""[\s\-_/\\|,，]+"""), "")) -> " "
                else -> " $content "
            }
        }

        s = NOISE_EN.replace(s, " ")
        s = NOISE_CN.replace(s, " ")
        s = STATE_WORDS.replace(s, " ")
        s = CHANNEL_WORDS.replace(s, " ")
        s = EMPTY_BRACKET.replace(s, " ")
        s = BLANKS.replace(s, " ").trim()

        return s.ifBlank { raw.trim() }
    }

    /** 全角转半角，顺带把全角空格换成普通空格 */
    private fun toHalfWidth(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when {
                    ch == '\u3000' -> ' '
                    ch.code in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()
                    else -> ch
                },
            )
        }
        return sb.toString()
    }

    /** 只处理最常见的几种实体，够用了 */
    private fun unescapeHtml(s: String): String {
        if (!s.contains('&')) return s
        var out = s
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
        // 数字实体 &#123; / &#x1F600;
        val numRe = Regex("""&#(x?)([0-9a-fA-F]+);""")
        if (numRe.containsMatchIn(out)) {
            out = numRe.replace(out) { m ->
                val code = runCatching {
                    if (m.groupValues[1].isNotEmpty()) m.groupValues[2].toInt(16)
                    else m.groupValues[2].toInt()
                }.getOrNull()
                if (code != null && code in 0..0x10FFFF) String(Character.toChars(code)) else m.value
            }
        }
        return out
    }
}

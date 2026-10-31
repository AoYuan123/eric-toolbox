package com.eric.toolbox.modules.pan.core

/**
 * 从一段文本（网页正文、帖子、公众号文章）里抓网盘分享链接和提取码。
 *
 * 难点在提取码配对：一条帖子里可能有 5 个链接和 5 个提取码，
 * 必须「就近配对」，不能共用一个码，否则用户拿着 A 链接配 B 的码打不开。
 */
object LinkExtractor {

    /** 带协议或协议相对（//）的 URL */
    private val URL_RE = Regex(
        """(?:https?:)?//[A-Za-z0-9\-._~:/?#\[\]@!&'()*+,;=%]+""",
        RegexOption.IGNORE_CASE,
    )

    /** 无协议头的裸域名，如 pan.quark.cn/s/xxxx */
    private val BARE_DOMAIN = Regex(
        """(?:pan\.quark\.cn|pan\.baidu\.com|(?:www\.)?alipan\.com|(?:www\.)?aliyundrive\.com|pan\.xunlei\.com|drive\.uc\.cn|115\.com|cloud\.189\.cn|yun\.139\.com|123pan\.com|lanzou\w*\.com)/[A-Za-z0-9\-._~:/?#\[\]@!&'()*+,;=%]+""",
        RegexOption.IGNORE_CASE,
    )

    /** 链接后面紧跟的提取码说明 */
    private val PWD_AFTER = Regex(
        """(?:提取码|提取密码|访问码|访问密码|密码|pwd|password|passcode|code)\s*[:：=]?\s*([A-Za-z0-9]{2,16})""",
        RegexOption.IGNORE_CASE,
    )

    /** 链接前面可能有的「(访问码: abcd):」前置写法 */
    private val PWD_BEFORE = Regex(
        "(?:提取码|访问码|密码|pwd|code)\\s*[:：=]?\\s*([A-Za-z0-9]{2,16})\\s*[)）]?\\s*[:：]?\\s*\\$",
        RegexOption.IGNORE_CASE,
    )

    private val URL_PWD_PARAM = Regex("""[?&](?:pwd|password|passcode|code)=([A-Za-z0-9]{2,16})""", RegexOption.IGNORE_CASE)

    /** 需要从链接尾部剥掉的标点/中文 */
    private const val TAIL_TRIM = ".,;:!?'\"“”‘’、。，；：！？）)】」』》>"

    /** 提取码搜索窗口：链接后多少字符内找 */
    private const val PWD_WINDOW = 40

    data class Found(val url: String, val password: String = "")

    /**
     * 抓出所有分享链接。同一 URL 只返回一次（保留首次出现的提取码）。
     * 会顺带做全角转半角、剥尾随标点、补协议头。
     */
    fun extract(text: String?): List<Found> {
        if (text.isNullOrBlank()) return emptyList()
        val src = toHalfWidth(text)

        // 收集所有命中位置，按起点排序，方便做「就近配对」的边界判断
        val hits = mutableListOf<Triple<Int, Int, String>>() // start, end, url
        URL_RE.findAll(src).forEach { m ->
            val raw = trimTail(m.value)
            if (raw.length > 8) hits.add(Triple(m.range.first, m.range.first + raw.length, normalizeScheme(raw)))
        }
        BARE_DOMAIN.findAll(src).forEach { m ->
            // 已经被上面带协议的匹配覆盖了就跳过
            if (hits.any { m.range.first >= it.first && m.range.first < it.second }) return@forEach
            val raw = trimTail(m.value)
            if (raw.length > 8) hits.add(Triple(m.range.first, m.range.first + raw.length, "https://$raw"))
        }
        if (hits.isEmpty()) return emptyList()
        hits.sortBy { it.first }

        val out = mutableListOf<Found>()
        val seen = mutableSetOf<String>()
        for ((idx, hit) in hits.withIndex()) {
            val (_, end, url) = hit
            if (!CloudType.isShareUrl(url)) continue
            if (!seen.add(url)) continue

            val nextStart = hits.getOrNull(idx + 1)?.first ?: Int.MAX_VALUE
            val windowEnd = minOf(end + PWD_WINDOW, nextStart, src.length)
            val after = src.substring(end, windowEnd)

            var pwd = URL_PWD_PARAM.find(url)?.groupValues?.get(1).orEmpty()
            if (pwd.isEmpty()) {
                pwd = PWD_AFTER.find(after)?.groupValues?.get(1).orEmpty()
            }
            if (pwd.isEmpty()) {
                // 前置写法：「(访问码: abcd): https://...」
                val before = src.substring(maxOf(0, hit.first - 30), hit.first)
                pwd = PWD_BEFORE.find(before)?.groupValues?.get(1).orEmpty()
            }
            out.add(Found(url, pwd))
        }
        return out
    }

    /** 只要链接，不管提取码 */
    fun extractUrls(text: String?): List<String> = extract(text).map { it.url }

    /** 从单个 URL 里抽提取码（百度/阿里常见 ?pwd=xxxx） */
    fun passwordFromUrl(url: String?): String =
        URL_PWD_PARAM.find(url ?: "")?.groupValues?.get(1).orEmpty()

    private fun normalizeScheme(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http", ignoreCase = true) -> url
        else -> "https://$url"
    }

    private fun trimTail(url: String): String {
        var s = url
        while (s.isNotEmpty() && TAIL_TRIM.contains(s.last())) {
            s = s.dropLast(1)
        }
        return s
    }

    private fun toHalfWidth(s: String): String {
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
}

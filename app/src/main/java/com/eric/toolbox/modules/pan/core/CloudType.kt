package com.eric.toolbox.modules.pan.core

import com.eric.toolbox.modules.pan.model.CloudOrder

/**
 * 网盘类型识别与归一化。
 * 各数据源的类型写法不一样，统一转成这里的规范标识再往外走。
 */
object CloudType {

    private val ALIAS = mapOf(
        "ali" to "aliyun",
        "alipan" to "aliyun",
        "yidong" to "mobile",
    )

    /** 展示名 */
    private val NAMES = mapOf(
        "quark" to "夸克网盘",
        "aliyun" to "阿里云盘",
        "baidu" to "百度网盘",
        "xunlei" to "迅雷网盘",
        "uc" to "UC网盘",
        "115" to "115网盘",
        "tianyi" to "天翼云盘",
        "mobile" to "移动云盘",
        "123" to "123网盘",
        "lanzou" to "蓝奏云",
        "pikpak" to "PikPak",
        "guangya" to "光鸭云盘",
        "magnet" to "磁力链接",
        "ed2k" to "电驴链接",
        "others" to "其他",
    )

    /** 小云搜索返回的中文网盘名 */
    private val YUNSO_NAMES = mapOf(
        "夸克" to "quark", "百度" to "baidu", "阿里" to "aliyun",
        "迅雷" to "xunlei", "UC" to "uc", "115" to "115",
        "天翼" to "tianyi", "移动" to "mobile", "123" to "123",
        "蓝奏" to "lanzou", "PikPak" to "pikpak",
    )

    /** 海搜内部代码 → 规范标识 */
    private val TO_HAISOU = mapOf("aliyun" to "ali", "mobile" to "yidong")
    private val FROM_HAISOU = TO_HAISOU.entries.associate { (k, v) -> v to k }

    /** URL → 网盘 */
    private val URL_RULES: List<Pair<Regex, String>> = listOf(
        Regex("pan\\.quark\\.cn", RegexOption.IGNORE_CASE) to "quark",
        Regex("pan\\.baidu\\.com", RegexOption.IGNORE_CASE) to "baidu",
        Regex("alipan\\.com|aliyundrive\\.com", RegexOption.IGNORE_CASE) to "aliyun",
        Regex("pan\\.xunlei\\.com", RegexOption.IGNORE_CASE) to "xunlei",
        Regex("drive\\.uc\\.cn|fast\\.uc\\.cn", RegexOption.IGNORE_CASE) to "uc",
        Regex("115\\.com|115cdn\\.com|anxia\\.com", RegexOption.IGNORE_CASE) to "115",
        Regex("cloud\\.189\\.cn", RegexOption.IGNORE_CASE) to "tianyi",
        Regex("yun\\.139\\.com|caiyun\\.139\\.com", RegexOption.IGNORE_CASE) to "mobile",
        Regex("123pan\\.com|123912\\.com|123684\\.com|123865\\.com", RegexOption.IGNORE_CASE) to "123",
        Regex("lanzou", RegexOption.IGNORE_CASE) to "lanzou",
        Regex("mypikpak\\.com", RegexOption.IGNORE_CASE) to "pikpak",
        Regex("guangyapan\\.com", RegexOption.IGNORE_CASE) to "guangya",
        Regex("^magnet:", RegexOption.IGNORE_CASE) to "magnet",
        Regex("^ed2k:", RegexOption.IGNORE_CASE) to "ed2k",
    )

    /** 通用 URL 提取（从 HTML 或正文里抓链接） */
    val URL_REGEX = Regex("""https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""")

    fun canon(code: String?): String {
        val c = (code ?: "").trim().lowercase()
        if (c.isEmpty()) return "others"
        return ALIAS[c] ?: c
    }

    /** 从 URL 判断网盘类型，认不出返回 others */
    fun fromUrl(url: String?): String {
        val u = (url ?: "").trim()
        if (u.isEmpty()) return "others"
        for ((re, type) in URL_RULES) {
            if (re.containsMatchIn(u)) return type
        }
        return "others"
    }

    /** 先看源给的类型，认不出再从 URL 猜 */
    fun resolve(code: String?, url: String?): String {
        val c = canon(code)
        return if (c == "others" || c.isEmpty()) fromUrl(url) else c
    }

    fun displayName(code: String): String = NAMES[code] ?: code

    fun fromYunsoName(name: String?): String? = YUNSO_NAMES[(name ?: "").trim()]
    fun toHaisou(code: String): String = TO_HAISOU[canon(code)] ?: canon(code)
    fun fromHaisou(code: String): String = FROM_HAISOU[(code ?: "").trim().lowercase()] ?: canon(code)

    /**
     * 是否「分享链接形态」。
     * 排除网盘首页入口按钮那种链接，否则会把一堆无效地址当结果。
     */
    fun isShareUrl(url: String?): Boolean {
        val u = (url ?: "").trim()
        if (u.isEmpty()) return false
        val lower = u.lowercase()
        if (lower.startsWith("magnet:") || lower.startsWith("ed2k:")) return true
        return Regex("/s/|/t/|/w/i/|/share", RegexOption.IGNORE_CASE).containsMatchIn(u)
    }

    /** 排序用的来源等级：主源 0，补充源 1，影视库 2 */
    fun engineRank(source: String): Int = when (source.substringBefore(':').lowercase()) {
        "pansou", "haisou", "yunso", "local" -> 0
        "ataw", "panxiaozi" -> 1
        "movie" -> 2
        else -> 5
    }

    /** 该类型是否支持匿名核验 */
    fun supportsVerify(code: String): Boolean = canon(code) in setOf(
        "quark", "aliyun", "baidu", "115", "123", "tianyi", "lanzou", "uc",
    )

    fun orderIndex(code: String): Int = CloudOrder.ALL.indexOf(canon(code)).let { if (it < 0) 999 else it }
}

package com.eric.toolbox.modules.vod.model

/**
 * 影视板块的数据模型。
 *
 * 字段命名刻意贴近各环节的原始键名（CMS 站点返回 vod_xxx、TVBox 配置里的 key/api 等），
 * 排查线上问题时可以直接跟抓到的原始报文对得上，不用再翻译一层。
 */

/** 站点类型。 */
object SiteType {
    /** 苹果 CMS 的 XML 输出 */
    const val XML = 0

    /** 苹果 CMS 的 JSON 输出 */
    const val JSON = 1

    /**
     * Spider：TVBox / 影视仓的插件站点。api 字段写的是插件里的类名
     * （如 csp_DouDouGuard），要靠配置顶层的 spider 字段把插件包下下来、
     * 加载进进程才有数据。走 [com.eric.toolbox.modules.vod.core.SpiderHub]。
     */
    const val SPIDER = 3
}

/**
 * 一个点播站点。对应配置里 sites[] 的一项。
 */
data class VodSite(
    val key: String,
    val name: String,
    val type: Int = SiteType.JSON,
    val api: String = "",
    /** 部分站点在 ext 里塞分类表或额外参数 */
    val ext: String = "",
    val searchable: Boolean = true,
    val filterable: Boolean = false,
    val playable: Boolean = true,
    /** 来自哪个订阅，用于在 UI 上分组展示 */
    val subId: String = "",
) {
    /** type=3：要靠插件包才有数据，不能直接发 HTTP 请求 */
    val isSpider: Boolean get() = type == SiteType.SPIDER

    /** 标准接口站点：SiteClient 能直接处理 */
    val supported: Boolean get() = type != SiteType.SPIDER

    val unsupportedReason: String
        get() = if (isSpider) "Spider 站点依赖配置里的插件包" else ""

    /** 接口地址是否完整可用。Spider 站点的 api 是类名，不算地址 */
    val hasApi: Boolean get() = api.startsWith("http://") || api.startsWith("https://")
}

/** 解析器，对应配置里 parses[] 的一项。 */
data class ParseRule(
    val name: String,
    val url: String,
    /** 0 = 把待解析地址拼在 url 后面；其它类型（JS 嗅探）本 App 不支持 */
    val type: Int = 0,
    val ext: String = "",
) {
    val supported: Boolean get() = type == 0 && url.isNotBlank()
}

/** 直播频道，对应配置里 lives[] 的一项。 */
data class LiveChannel(
    val group: String,
    val name: String,
    val url: String,
)

/** 分类。站点能返回 class[] 时用它，否则用户手工填。 */
data class VodCategory(
    val id: String,
    val name: String,
)

/** 一份完整配置（一个订阅解析后的结果）。 */
/**
 * 一份配置里站点的构成统计。
 *
 * 只报「可用 N 个」不够用：用户看到「导入成功」但搜不出东西，会以为是 bug。
 * 必须能回答「一共几个、为什么别的用不了」，所以把跳过原因分开计数。
 */
data class ConfigStats(
    val total: Int = 0,
    /** type=3 的总数（含因缺插件包、或属于 JS 脚本而被跳过的） */
    val spider: Int = 0,
    /** api 缺失或不是 http(s) 绝对地址（影视仓常见的 ./lib/xxx.js 就算这类） */
    val noApi: Int = 0,
    val usable: Int = 0,
) {
    val skipped: Int get() = total - usable
}

data class VodConfig(
    val subId: String = "",
    val subName: String = "",
    val sites: List<VodSite> = emptyList(),
    val parses: List<ParseRule> = emptyList(),
    val lives: List<LiveChannel> = emptyList(),
    /**
     * 顶层 spider 字段原文：插件包地址 + md5。
     * 只有它是远程 http 地址时，这份配置里的 type=3 站点才有可能跑起来。
     */
    val spiderDecl: String = "",
    /** 站点自带的分类表，取不到就为空，由用户手工配置 */
    val categories: List<VodCategory> = emptyList(),
    val ua: String = "",
    val timeoutMs: Int = 15_000,
    /** 解析过程中遇到的软提示（如跳过了 Spider 站点），要展示给用户 */
    val warnings: List<String> = emptyList(),
    /** 站点构成统计，用于「共 N 个 · 可用 M 个」这类结论 */
    val stats: ConfigStats = ConfigStats(),
)

/**
 * 订阅来源。远程订阅有 url；本地导入的只有 rawJson。
 * 二者互斥，写操作任何时候都要保证 url 与 rawJson 至少有一个非空。
 */
data class Subscription(
    val id: String,
    val name: String,
    val url: String = "",
    val rawJson: String = "",
    val enabled: Boolean = true,
    val updatedAt: Long = 0L,
    /** 上次拉取/导入是否成功 */
    val lastError: String = "",
) {
    val isRemote: Boolean get() = url.isNotBlank()
    fun toJson() = org.json.JSONObject().apply {
        put("id", id); put("name", name); put("url", url); put("rawJson", rawJson)
        put("enabled", enabled); put("updatedAt", updatedAt); put("lastError", lastError)
    }

    companion object {
        fun fromJson(o: org.json.JSONObject) = Subscription(
            id = o.optString("id"),
            name = o.optString("name"),
            url = o.optString("url"),
            rawJson = o.optString("rawJson"),
            enabled = o.optBoolean("enabled", true),
            updatedAt = o.optLong("updatedAt"),
            lastError = o.optString("lastError"),
        )
    }
}

/** 列表 / 搜索里的一条影片。 */
data class VodItem(
    val siteKey: String,
    val siteName: String,
    val id: String,
    val name: String,
    val pic: String = "",
    /** 站点备注，常见是「HD」「更新至12集」 */
    val remarks: String = "",
    val typeName: String = "",
    val year: String = "",
    val area: String = "",
    val actor: String = "",
    val director: String = "",
    val content: String = "",
    /** 播放来源串，如 腾讯$xxx$$$优酷$yyy */
    val playFrom: String = "",
    /** 播放地址串，与 playFrom 顺序对应 */
    val playUrl: String = "",
    /**
     * 站点给的更新时间，转成时间戳。解析不出来就是 0。
     * 首页要把多个站的最新内容混在一起排，没有这个字段排不了序。
     */
    val updatedAt: Long = 0L,
)

/**
 * 站点更新时间的解析。
 *
 * 各家写法五花八门（`2024-01-01 12:00:00`、`2024/01/01`、只有日期没有时间），
 * 解析失败一律返回 0，排序时自然沉底，不影响其它内容展示。
 */
object VodTime {

    private val PATTERNS = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy/MM/dd",
        "yyyy-MM-dd'T'HH:mm:ss",
    )

    fun parse(raw: String): Long {
        val s = raw.trim()
        if (s.length < 8) return 0L
        // 时间戳写法：10 位或 13 位纯数字
        if (s.all { it.isDigit() }) {
            val n = s.toLongOrNull() ?: return 0L
            return when (s.length) {
                10 -> n * 1000
                13 -> n
                else -> 0L
            }
        }
        for (p in PATTERNS) {
            val v = runCatching {
                java.text.SimpleDateFormat(p, java.util.Locale.CHINA).parse(s)?.time ?: 0L
            }.getOrDefault(0L)
            if (v > 0L) return v
        }
        return 0L
    }
}

/** 单集 */
data class Episode(
    val name: String,
    val url: String,
)

/** 一个播放源下的全部剧集 */
data class PlayGroup(
    val source: String,
    val episodes: List<Episode>,
)

/** TMDB 返回的元数据。没有配置 API key 时整块功能不可用。 */
data class TmdbMovie(
    val id: Int,
    val title: String,
    val overview: String = "",
    val poster: String = "",
    val rating: Double = 0.0,
    val date: String = "",
) {
    val hasPoster: Boolean get() = poster.isNotBlank()
}

/** 一条播放记录。用于详情页显示上次看到哪儿、以及主页的「继续观看」。 */
data class PlayHistory(
    val title: String,
    val sourceName: String,
    val url: String,
    val episodeName: String = "",
    val pic: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** 看过多少比例，0~1；拿不到总时长时为 0 */
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    companion object {
        fun fromJson(o: org.json.JSONObject) = PlayHistory(
            title = o.optString("t"),
            sourceName = o.optString("s"),
            url = o.optString("u"),
            episodeName = o.optString("e"),
            pic = o.optString("p"),
            positionMs = o.optLong("pos"),
            durationMs = o.optLong("dur"),
            updatedAt = o.optLong("at"),
        )
    }

    fun toJson() = org.json.JSONObject().apply {
        put("t", title); put("s", sourceName); put("u", url); put("e", episodeName)
        put("p", pic); put("pos", positionMs); put("dur", durationMs); put("at", updatedAt)
    }
}

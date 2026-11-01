package com.eric.toolbox.modules.vod.model

/**
 * 影视板块的数据模型。
 *
 * 字段命名刻意贴近各环节的原始键名（CMS 站点返回 vod_xxx、TVBox 配置里的 key/api 等），
 * 排查线上问题时可以直接跟抓到的原始报文对得上，不用再翻译一层。
 */

/** 站点类型。本 App 只支持走标准 HTTP 接口的站点。 */
object SiteType {
    /** 苹果 CMS 的 XML 输出 */
    const val XML = 0

    /** 苹果 CMS 的 JSON 输出 */
    const val JSON = 1

    /** Spider：需要加载第三方 jar/dex 并执行里面的代码 —— 明确不支持 */
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
    /** Spider 类站点需要动态执行第三方代码，本 App 一律不支持。 */
    val supported: Boolean get() = type != SiteType.SPIDER

    val unsupportedReason: String
        get() = if (type == SiteType.SPIDER) "Spider 站点需要加载并执行第三方代码，本 App 不支持" else ""

    /** 接口地址是否完整可用 */
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
data class VodConfig(
    val subId: String = "",
    val subName: String = "",
    val sites: List<VodSite> = emptyList(),
    val parses: List<ParseRule> = emptyList(),
    val lives: List<LiveChannel> = emptyList(),
    /** 站点自带的分类表，取不到就为空，由用户手工配置 */
    val categories: List<VodCategory> = emptyList(),
    val ua: String = "",
    val timeoutMs: Int = 15_000,
    /** 解析过程中遇到的软提示（如跳过了 Spider 站点），要展示给用户 */
    val warnings: List<String> = emptyList(),
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
)

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

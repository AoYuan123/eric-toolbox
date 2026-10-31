package com.eric.toolbox.modules.pan.model

/**
 * 一条网盘资源结果。
 * 字段命名与原始 Python 实现的 JSON 保持一致，方便互相校验。
 */
data class PanItem(
    /** 网盘标识，见 CloudType */
    val cloud: String,
    /** 分享链接原始地址 */
    val url: String,
    /** 提取码，没有则空串 */
    val password: String = "",
    /** 标题（资源名），可能带噪词 */
    val note: String = "",
    /** 来源标记，如 pansou:plugin:labi / ataw:quark / yunso */
    val source: String = "",
    /** 日期，已格式化为 yyyy-MM-dd；未知则空串 */
    val datetime: String = "",
    /** 体积（字节），0 表示未知 */
    val size: Long = 0L,
    /** 文件数，0 表示未知 */
    val files: Int = 0,
    /** 是否热门 */
    val hot: Boolean = false,
    /** 详情页地址（有则用于二次核对内容） */
    val detail: String = "",
    /** 同资源副本 */
    val dups: MutableList<Dup> = mutableListOf(),
    /** 归一化后的标题，由 Aggregator 填充，仅内部使用 */
    var normTitle: String = "",
    /** 内容标签，如 [内容:小说]，空串表示未知 */
    var contentTag: String = "",
    /** 内容抽查摘要（夸克抽查才有），空串表示未抽查 */
    var contentPeek: String = "",
    /** 核验状态 */
    var verify: VerifyState = VerifyState.UNCHECKED,
    /** 上次核验时间戳（毫秒） */
    var lastCheckedAt: Long = 0L,
) {
    /** 供 UI 展示的体积文本 */
    fun sizeText(): String = when {
        size <= 0L -> ""
        size >= (1L shl 30) -> String.format(java.util.Locale.US, "%.2f GB", size.toDouble() / (1L shl 30))
        else -> String.format(java.util.Locale.US, "%.0f MB", size.toDouble() / (1L shl 20))
    }

    /** 是否可在 UI 上直接点开 */
    fun isOpenable(): Boolean = url.isNotBlank()
}

data class Dup(
    val url: String,
    val note: String = "",
    val source: String = "",
)

/**
 * 核验状态。
 * UNCHECKED 必须如实展示为「未核验」，不能渲染成有效。
 */
enum class VerifyState(val label: String) {
    OK("已验证"),
    /** 需提取码：链接本身是活的，只是没码打不开。别跟「疑似失效」混为一谈 */
    LOCKED("需提取码"),
    SUSPECT("疑似失效"),
    BAD("已失效"),
    UNCHECKED("未核验"),
}

/** 单引擎返回 */
data class EngineResult(
    val engine: String,
    val items: List<PanItem> = emptyList(),
    /** 该源报告的总命中数（可能大于 items.size） */
    val total: Int = 0,
    /** 失败原因，null 表示成功 */
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}

/** 一次搜索的完整结果 */
data class SearchResult(
    val keyword: String,
    val items: List<PanItem>,
    /** 各源命中数 */
    val totals: Map<String, Int> = emptyMap(),
    /** 各源错误信息 */
    val errors: List<String> = emptyList(),
    /** 因熔断被跳过的源 */
    val skippedEngines: List<String> = emptyList(),
    /** 实际使用的关键词变体（0 结果兜底时） */
    val variantUsed: String = "",
    /** 各源耗时（毫秒） */
    val elapsedMs: Map<String, Long> = emptyMap(),
    /** 是否启用了 TA搜 兜底（默认源全空时才会发生） */
    val atawFallback: Boolean = false,
) {
    /** 按网盘分组，顺序固定 */
    fun grouped(): List<Pair<String, List<PanItem>>> =
        items.groupBy { it.cloud }
            .let { map ->
                val ordered = CloudOrder.ALL.filter { map.containsKey(it) } +
                        map.keys.filter { it !in CloudOrder.ALL }
                ordered.map { it to map.getValue(it) }
            }
}

/** 展示顺序：按国内用户实际可用度排，不要按字母排 */
object CloudOrder {
    val ALL = listOf(
        "quark", "aliyun", "baidu", "xunlei", "uc", "115", "tianyi",
        "mobile", "123", "lanzou", "pikpak", "guangya", "magnet", "ed2k", "others",
    )
}

/** 搜索选项 */
data class SearchOptions(
    /** 只保留这些网盘；空集合表示全部 */
    val cloudTypes: Set<String> = emptySet(),
    /** 必须包含的词 */
    val include: List<String> = emptyList(),
    /** 命中即排除的词 */
    val exclude: List<String> = emptyList(),
    /** 每个网盘最多保留条数，0 表示不限 */
    val limitPerCloud: Int = 8,
    /** 启用的引擎 */
    val engines: List<String> = listOf("pansou", "haisou", "yunso", "ataw"),
    /** 单个请求超时（毫秒） */
    val timeoutMs: Int = 30_000,
    /** pansou 专用超时，这个源天生慢 */
    val pansouTimeoutMs: Int = 45_000,
    /** pansou 的 src 参数：plugin（插件源）/ tg（TG 频道）/ all */
    val pansouSrc: String = "all",
    /** 小云搜索 mode：90001 综合，其他值按站点支持 */
    val yunsoMode: String = "90001",
    /** 海搜检索范围：title=仅标题，其余按站点支持的 scope */
    val haisouScope: String = "title",
    /** 海搜体积下限（GB），null 不限 */
    val minSizeGb: Double? = null,
    /** 海搜体积上限（GB），null 不限 */
    val maxSizeGb: Double? = null,
    /** 忽略熔断状态，强制所有引擎都跑一次 */
    val fresh: Boolean = false,
    /** 0 结果时是否用关键词变体重试 */
    val useVariants: Boolean = true,
    /**
     * 是否展开全部同名副本。
     * false（默认）= 折叠成一条主条目 + dups；
     * true = 副本平铺展示，不再带「另有 N 个」提示。
     */
    val allDups: Boolean = false,
    /** 单源内部最多抓多少条详情（盘小子 / ataw 用），影响耗时 */
    val engineItemLimit: Int = 8,
)

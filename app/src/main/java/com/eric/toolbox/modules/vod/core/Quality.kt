package com.eric.toolbox.modules.vod.core

import com.eric.toolbox.modules.vod.model.PlayGroup
import com.eric.toolbox.modules.vod.model.VodItem

/**
 * 画质识别。
 *
 * 为什么要有这一层：**决定画质的是选哪条线路，不是用哪个站点。**
 * 同一个站同一部片，常常同时挂着「腾讯」「优酷」「蓝光4K」「流畅」好几条线路，
 * 清晰度从 360P 到 4K 都有。所以「我要 1080P 以上」这件事无法靠挑源保证，
 * 必须在 App 侧自己识别、过滤、排序。
 *
 * 识别依据是标题 / 备注 / 分类 / 线路名里的关键字。站点没标画质一律判 UNKNOWN——
 * 宁可不认也不乱猜，猜错会把 480P 当成 4K 排到最前面，比不认更糟。
 */
object Quality {

    enum class Level(val rank: Int, val label: String) {
        /** 站点没标。默认不剔除，除非用户勾了「只看明确标注」 */
        UNKNOWN(0, ""),
        SD(1, "480P"),
        HD(2, "720P"),
        FHD(3, "1080P"),
        Q2K(4, "2K"),
        UHD(5, "4K"),
        ;

        val known: Boolean get() = this != UNKNOWN

        companion object {
            fun fromRank(rank: Int): Level = entries.firstOrNull { it.rank == rank } ?: UNKNOWN

            /** 给用户选的门槛档位：不限 / 720P+ / 1080P+ / 4K */
            fun thresholds(): List<Level> = listOf(UNKNOWN, HD, FHD, UHD)
        }
    }

    /*
     * 各档位的匹配规则，**按 rank 降序排列**，命中第一条就是最高画质。
     *
     * 几个刻意的选择：
     * - 数字清晰度（1080p / 2160p）优先于含糊字眼（HD / 蓝光），前者是硬指标；
     * - 「HD」在很多站里只是「高清」统称，实际未必到 720P，保守映射到 720P 档；
     * - 「蓝光 / BluRay」通常落在 1080P，归入 FHD；
     * - **不用 \b**：Java 的 \b 按 ASCII 处理、Python 按 Unicode 处理，
     *   本机没法编译 Kotlin 时根本验不出差异，而「4K修复版」这种中英紧挨的写法
     *   恰恰是最常见的。改用显式 lookaround，语义在两边一致、行为可预测。
     */
    private const val NOT_ALNUM_BEFORE = "(?<![a-zA-Z0-9])"
    private const val NOT_ALNUM_AFTER = "(?![a-zA-Z0-9])"

    private val RULES: List<Pair<Level, Regex>> = listOf(
        Level.UHD to Regex("${NOT_ALNUM_BEFORE}(4k|2160[pi]|uhd)${NOT_ALNUM_AFTER}", RegexOption.IGNORE_CASE),
        Level.Q2K to Regex("${NOT_ALNUM_BEFORE}(2k|1440[pi])${NOT_ALNUM_AFTER}", RegexOption.IGNORE_CASE),
        Level.FHD to Regex(
            "${NOT_ALNUM_BEFORE}(1080[pi]|fhd|full[\\s_.-]*hd|blu[\\s_.-]*ray|bdrip)${NOT_ALNUM_AFTER}" +
                "|蓝光|原盘|原画|高码",
            RegexOption.IGNORE_CASE,
        ),
        Level.HD to Regex(
            "${NOT_ALNUM_BEFORE}(720[pi]|hd|hdtv|hr[\\s_.-]*hdtv)${NOT_ALNUM_AFTER}",
            RegexOption.IGNORE_CASE,
        ),
        Level.SD to Regex(
            "${NOT_ALNUM_BEFORE}(480[pi]|sd|dvd|dvdrip)${NOT_ALNUM_AFTER}|流畅|标清",
            RegexOption.IGNORE_CASE,
        ),
    )

    fun detect(text: String): Level {
        if (text.isBlank()) return Level.UNKNOWN
        for ((level, re) in RULES) {
            if (re.containsMatchIn(text)) return level
        }
        return Level.UNKNOWN
    }

    /** 多段文本里取最高的那一档。 */
    fun detectAll(vararg parts: String): Level {
        var best = Level.UNKNOWN
        for (p in parts) {
            val lv = detect(p)
            if (lv.rank > best.rank) best = lv
        }
        return best
    }

    /**
     * 一部片宣称的最高画质。
     *
     * 只看 name / remarks / typeName / playFrom。刻意**不看 content（简介）**——
     * 简介里常见「高清视效」「IMAX 摄影」这类描述性文字，拿来判画质必误伤。
     */
    fun of(item: VodItem): Level = detectAll(item.name, item.remarks, item.typeName, item.playFrom)

    /** 一条线路的画质。只认线路名，不认播放地址——域名里撞到 hd/4k 的概率太高。 */
    fun of(group: PlayGroup): Level = detect(group.source)

    /**
     * 是否达到门槛。UNKNOWN 的处理是这套逻辑的关键，分三种情况：
     *
     * - min = UNKNOWN（不限）→ 一律通过；
     * - min > UNKNOWN 且 requireKnown = true  → UNKNOWN 不通过（严格模式，宁缺毋滥）；
     * - min > UNKNOWN 且 requireKnown = false → UNKNOWN 通过（默认，避免大量无标注站点搜出空结果）。
     */
    fun pass(level: Level, min: Level, requireKnown: Boolean): Boolean {
        // 「只看明确标注」优先判断：不限画质但要求标注时，UNKNOWN 一样要挡掉
        if (requireKnown && !level.known) return false
        if (min == Level.UNKNOWN) return true
        return level.rank >= min.rank
    }

    /**
     * 过滤列表，返回「留下的」和「被画质刷掉多少条」。
     * 保持原顺序，不做重排——排序交给相关性那一层，两者职责分开。
     */
    fun filter(
        items: List<VodItem>,
        min: Level,
        requireKnown: Boolean,
    ): Pair<List<VodItem>, Int> {
        if (min == Level.UNKNOWN && !requireKnown) return items to 0
        val kept = ArrayList<VodItem>(items.size)
        var dropped = 0
        for (item in items) {
            if (pass(of(item), min, requireKnown)) kept += item else dropped++
        }
        return kept to dropped
    }

    /** 一组线路里画质最高的那条的下标，没有就返回 -1。用于详情页「跳到最高画质」。 */
    fun bestIndex(groups: List<PlayGroup>): Int {
        var best = -1
        var bestRank = 0
        groups.forEachIndexed { i, g ->
            val rank = of(g).rank
            if (rank > bestRank) {
                bestRank = rank
                best = i
            }
        }
        return if (bestRank > 0) best else -1
    }
}

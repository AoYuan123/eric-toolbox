package com.eric.toolbox.modules.vod.core

import com.eric.toolbox.modules.vod.core.Quality.Level
import com.eric.toolbox.modules.vod.model.Episode
import com.eric.toolbox.modules.vod.model.PlayGroup
import com.eric.toolbox.modules.vod.model.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画质识别的回归测试。
 *
 * 这套用例的意义不只是「现在是对的」，而是防止以后有人改正则时悄悄退化：
 * 线上标题是中文 + 英文规格混写（「4K修复版」「1080P国语」），
 * 只靠肉眼读正则看不出问题。网盘模块那条少了开头方括号的正则就是这么溜过去的。
 */
class QualityTest {

    private val UHD = Level.UHD
    private val FHD = Level.FHD
    private val HD = Level.HD
    private val SD = Level.SD
    private val UN = Level.UNKNOWN

    // ------------------------------------------------------------- 识别准确性

    @Test
    fun detectsChineseEnglishMixedTitles() {
        assertEquals(UHD, Quality.detect("沙丘.2021.4K.HDR.中英字幕"))
        assertEquals(UHD, Quality.detect("4K修复版 泰坦尼克号"))
        assertEquals(UHD, Quality.detect("繁城之下 4K超清"))
        assertEquals(HD, Quality.detect("年会不能停 HD国语"))
        assertEquals(SD, Quality.detect("老片 DVD国语"))
        assertEquals(FHD, Quality.detect("某片 1080p WEB-DL"))
    }

    @Test
    fun prefersNumericResolutionOverVagueWords() {
        // 数字是硬指标，含糊字眼只是参考
        assertEquals(UHD, Quality.detect("2160p WEB-DL"))
        assertEquals(FHD, Quality.detect("奥本海默 1080P 蓝光原盘"))
        assertEquals(HD, Quality.detect("720P.HD中字"))
    }

    @Test
    fun doesNotMatchAdjacentAlphanumerics() {
        // 中文紧挨是允许的，但英文字母数字紧挨必须挡掉，
        // 否则 …1440p、x264 这类串会被后面的规则吃掉
        assertEquals(Level.Q2K, Quality.detect("1440p版本"))
        assertEquals(UN, Quality.detect("电影 x264 AAC"))
        assertEquals(UN, Quality.detect("Bodyguard 保镖"))
        assertEquals(UN, Quality.detect("Sand Castle 沙堡"))
        assertEquals(UN, Quality.detect("K2 巅峰时刻"))
    }

    @Test
    fun leavesUnmarkedTitlesUnknown() {
        assertEquals(UN, Quality.detect("一年级毕业季 2016 全集"))
        assertEquals(UN, Quality.detect("布达佩斯大饭店 英语"))
        assertEquals(UN, Quality.detect(""))
    }

    @Test
    fun readsItemsWithoutTouchingDescription() {
        // content 是简介，里面「高清视效」这类描述性文字不能用来判画质
        val item = VodItem(
            siteKey = "s", siteName = "某站", id = "1", name = "某片",
            content = "本片采用 4K 摄影机拍摄，视觉效果震撼",
        )
        assertEquals(UN, Quality.of(item))

        val marked = item.copy(remarks = "更新至全12集", playFrom = "蓝光4K")
        assertEquals(UHD, Quality.of(marked))
    }

    @Test
    fun readsPlayGroupFromSourceNameOnly() {
        val g = PlayGroup("蓝光4K", listOf(Episode("第1集", "https://a.example/x.m3u8")))
        assertEquals(UHD, Quality.of(g))

        // 域名里撞到关键字不能算：只看线路名
        val trap = PlayGroup("线路1", listOf(Episode("第1集", "https://4k.example/x.m3u8")))
        assertEquals(UN, Quality.of(trap))
    }

    // ----------------------------------------------------------------- 门槛

    @Test
    fun unknownIsKeptByDefaultButDroppedWhenRequired() {
        assertTrue(Quality.pass(UN, Level.FHD, requireKnown = false))
        assertFalse(Quality.pass(UN, Level.FHD, requireKnown = true))
    }

    @Test
    fun requireKnownAlsoAppliesWhenNoMinimum() {
        // 不限画质 + 要求标注 → 没标注的照样挡掉
        assertTrue(Quality.pass(FHD, Level.UNKNOWN, requireKnown = true))
        assertFalse(Quality.pass(UN, Level.UNKNOWN, requireKnown = true))
    }

    @Test
    fun minimumBehavesAsAtLeast() {
        assertTrue(Quality.pass(UHD, Level.FHD, false))
        assertTrue(Quality.pass(FHD, Level.FHD, false))
        assertFalse(Quality.pass(HD, Level.FHD, false))
        assertFalse(Quality.pass(SD, Level.FHD, false))
    }

    @Test
    fun filterCountsDroppedItems() {
        val items = listOf(
            VodItem("s", "站", "1", "沙丘 4K"),
            VodItem("s", "站", "2", "沙丘 流畅版"),
            VodItem("s", "站", "3", "沙丘"),
        )
        val (kept, dropped) = Quality.filter(items, Level.FHD, requireKnown = false)
        // 4K 留下，无标注留下，流畅版被挡
        assertEquals(2, kept.size)
        assertEquals(1, dropped)

        val (strict, droppedStrict) = Quality.filter(items, Level.FHD, requireKnown = true)
        // 严格模式下只剩明确标注且达标的 4K 一条
        assertEquals(1, strict.size)
        assertEquals(2, droppedStrict)
    }

    @Test
    fun bestIndexFindsHighestQualityLine() {
        val groups = listOf(
            PlayGroup("腾讯视频", listOf(Episode("1", "https://a/1"))),
            PlayGroup("蓝光4K", listOf(Episode("1", "https://b/1"))),
            PlayGroup("优酷", listOf(Episode("1", "https://c/1"))),
        )
        assertEquals(1, Quality.bestIndex(groups))

        // 全都没标 → 返回 -1，UI 据此不显示「跳到最高画质」
        val plain = listOf(PlayGroup("线路1", listOf(Episode("1", "https://a/1"))))
        assertEquals(-1, Quality.bestIndex(plain))
    }

    @Test
    fun levelsRoundTripThroughRank() {
        for (lvl in Level.entries) {
            assertEquals(lvl, Level.fromRank(lvl.rank))
        }
        // 非法 rank 退回 UNKNOWN，不能让脏数据把 App 搞崩
        assertEquals(UN, Level.fromRank(99))
    }
}

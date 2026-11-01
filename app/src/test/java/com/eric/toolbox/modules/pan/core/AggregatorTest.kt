package com.eric.toolbox.modules.pan.core

import com.eric.toolbox.modules.pan.model.PanItem
import com.eric.toolbox.modules.pan.model.SearchOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 聚合器回归测试：去重、折叠、限条、沉底 */
class AggregatorTest {

    @Test
    fun 同一链接不同写法只留一条() {
        val items = listOf(
            PanItem(cloud = "quark", url = "https://pan.quark.cn/s/abc", note = "星际穿越"),
            PanItem(cloud = "quark", url = "https://pan.quark.cn/s/abc/", note = "星际穿越"),
            PanItem(cloud = "quark", url = "https://PAN.QUARK.CN/s/abc", note = "星际穿越"),
        )
        val out = Aggregator.aggregate(items, "星际穿越", SearchOptions())
        assertEquals(1, out.size)
    }

    @Test
    fun 同名同盘折叠成一条并挂副本() {
        val items = listOf(
            PanItem(cloud = "quark", url = "https://pan.quark.cn/s/a1", note = "星际穿越 4K"),
            PanItem(cloud = "quark", url = "https://pan.quark.cn/s/a2", note = "星际穿越 1080p"),
        )
        val out = Aggregator.aggregate(items, "星际穿越", SearchOptions())
        assertEquals(1, out.size)
        assertEquals(1, out[0].dups.size)
    }

    @Test
    fun 不同网盘不折叠() {
        val items = listOf(
            PanItem(cloud = "quark", url = "https://pan.quark.cn/s/a1", note = "星际穿越"),
            PanItem(cloud = "baidu", url = "https://pan.baidu.com/s/b1", note = "星际穿越"),
        )
        val out = Aggregator.aggregate(items, "星际穿越", SearchOptions())
        assertEquals(2, out.size)
    }

    @Test
    fun 每个网盘限条() {
        val items = (1..15).map {
            PanItem(cloud = "quark", url = "https://pan.quark.cn/s/x$it", note = "资源$it")
        }
        val out = Aggregator.aggregate(items, "资源", SearchOptions(limitPerCloud = 8))
        assertEquals(8, out.size)
    }

    @Test
    fun 确认失效的沉底() {
        val dead = "https://pan.quark.cn/s/dead"
        val items = listOf(
            PanItem(cloud = "quark", url = dead, note = "片A"),
            PanItem(cloud = "quark", url = "https://pan.quark.cn/s/alive", note = "片B"),
        )
        val out = Aggregator.aggregate(
            items, "片", SearchOptions(),
            deadUrls = setOf(Aggregator.normUrl(dead)),
        )
        assertEquals("https://pan.quark.cn/s/alive", out[0].url)
    }

    @Test
    fun 排除词生效() {
        val items = listOf(
            PanItem(cloud = "quark", url = "https://pan.quark.cn/s/a1", note = "星际穿越 预告片"),
            PanItem(cloud = "quark", url = "https://pan.quark.cn/s/a2", note = "星际穿越 正片"),
        )
        val out = Aggregator.aggregate(items, "星际穿越", SearchOptions(exclude = listOf("预告")))
        assertEquals(1, out.size)
        assertTrue(out[0].note.contains("正片"))
    }

    @Test
    fun 展开副本后不再折叠() {
        val items = listOf(
            PanItem(cloud = "quark", url = "https://pan.quark.cn/s/a1", note = "星际穿越 4K"),
            PanItem(cloud = "quark", url = "https://pan.quark.cn/s/a2", note = "星际穿越 1080p"),
        )
        val folded = Aggregator.aggregate(items, "星际穿越", SearchOptions())
        assertEquals(1, folded.size)
        assertEquals(1, folded[0].dups.size)

        val flat = Aggregator.aggregate(items, "星际穿越", SearchOptions(allDups = true))
        assertEquals(2, flat.size)
        assertTrue(flat.all { it.dups.isEmpty() })
    }

    @Test
    fun 关键词命中多的排前面() {
        val items = listOf(
            PanItem(cloud = "quark", url = "https://pan.quark.cn/s/a1", note = "星际穿越"),
            PanItem(cloud = "quark", url = "https://pan.quark.cn/s/a2", note = "星际穿越 诺兰 4K"),
        )
        val out = Aggregator.aggregate(items, "星际穿越 诺兰", SearchOptions())
        assertTrue(out[0].note.contains("诺兰"))
    }

    // ------------------------------------------------ 严格匹配 / 简介去噪

    /** 真实事故：搜「一年级毕业季」搜出了「恶作剧之吻」，因为它的简介里出现了完整关键词 */
    private val noisyItem = PanItem(
        cloud = "quark",
        url = "https://pan.quark.cn/s/noisy",
        note = "恶作剧之吻 全2季描述：故事从一场告白开始。一年级毕业季这天花招百出，" +
            "袁湘琴终于追到了江直树。",
    )

    @Test
    fun 纯标题要把简介切干净() {
        assertEquals("恶作剧之吻 全2季", Aggregator.pureTitle(noisyItem.note))
        assertEquals("某剧 第1集", Aggregator.pureTitle("某剧 第1集简介：balabala"))
        assertEquals("未标注资源", Aggregator.pureTitle("未标注资源"))
        assertEquals("", Aggregator.pureTitle(null))
        assertEquals("", Aggregator.pureTitle(""))
    }

    @Test
    fun 严格匹配剔除只蹭到简介的结果() {
        val out = Aggregator.aggregate(listOf(noisyItem), "一年级毕业季", SearchOptions())
        assertEquals(0, out.size)
    }

    @Test
    fun 关掉严格匹配后能捞回来() {
        val out = Aggregator.aggregate(
            listOf(noisyItem), "一年级毕业季", SearchOptions(strict = false),
        )
        assertEquals(1, out.size)
    }

    @Test
    fun 严格匹配不误伤正常结果() {
        val good = PanItem(
            cloud = "quark",
            url = "https://pan.quark.cn/s/good",
            note = "一年级毕业季 2016 全集 4K",
        )
        val out = Aggregator.aggregate(listOf(good), "一年级毕业季", SearchOptions())
        assertEquals(1, out.size)
    }

    @Test
    fun 剔除数量通过回调传给上层() {
        val items = listOf(
            noisyItem,
            PanItem(
                cloud = "quark",
                url = "https://pan.quark.cn/s/ok",
                note = "一年级毕业季 2016",
            ),
        )
        var dropped = -1
        val out = Aggregator.aggregate(items, "一年级毕业季", SearchOptions()) { dropped = it }
        assertEquals(1, out.size)
        assertEquals(1, dropped)
    }

    @Test
    fun 多个关键词只要命中一个就保留() {
        // 分词后有两个 token，只命中一半也要留着，否则容易矫枉过正
        val item = PanItem(
            cloud = "quark",
            url = "https://pan.quark.cn/s/a1",
            note = "一年级 综艺合集",
        )
        val out = Aggregator.aggregate(listOf(item), "一年级 毕业季", SearchOptions())
        assertEquals(1, out.size)
    }
}

package com.eric.toolbox.modules.vod.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 播放串解析。
 *
 * 重点是 allowOpaque：云盘类插件给的集地址是一串 token，不是网址。
 * 以前一刀切只认 http(s)，导致这类站点每一集都被丢掉，
 * 详情页显示「没有返回播放地址」——真机上就是这么翻车的。
 */
class EpisodeParserTest {

    @Test
    fun spiderTokenKeptWhenAllowed() {
        val groups = EpisodeParser.parse(
            "玩偶",
            "第1集\$abc123#第2集\$def456",
            allowOpaque = true,
        )
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].episodes.size)
        assertEquals("abc123", groups[0].episodes[0].url)
        assertEquals("第2集", groups[0].episodes[1].name)
    }

    @Test
    fun directSiteStillRequiresHttp() {
        val groups = EpisodeParser.parse(
            "线路1",
            "第1集\$abc123#第2集\$https://a.com/1.m3u8",
        )
        assertEquals(1, groups.size)
        // token 被丢掉，只留真正能播的那一集
        assertEquals(1, groups[0].episodes.size)
        assertEquals("https://a.com/1.m3u8", groups[0].episodes[0].url)
    }

    @Test
    fun multipleLinesSplitByTripleDollar() {
        val groups = EpisodeParser.parse(
            "A\$\$\$B",
            "第1集\$https://a.com/1.m3u8\$\$\$第1集\$https://b.com/1.m3u8",
        )
        assertEquals(2, groups.size)
        assertEquals("A", groups[0].source)
        assertEquals("B", groups[1].source)
    }

    @Test
    fun blankAddressDropped() {
        val groups = EpisodeParser.parse("L", "第1集\$#第2集\$https://a.com/2.m3u8", allowOpaque = true)
        assertEquals(1, groups.size)
        assertEquals(1, groups[0].episodes.size)
    }
}

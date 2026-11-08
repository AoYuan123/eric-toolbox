package com.eric.toolbox.modules.vod.core

import com.eric.toolbox.modules.vod.model.VodTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 推荐源与线路探测的逻辑测试。
 *
 * 这两块最容易出「看起来对、实际不工作」的问题：
 *  - toTvBoxJson 生成的格式如果解析器不认，用户装完还是空白页；
 *  - 时间解析如果错了，聚合首页的排序就是乱的。
 * 所以不测网络，只把纯逻辑钉住。
 */
class VodSourceTest {

    // ------------------------------------------------------------ 推荐源往返

    @Test
    fun generatedConfigIsParsedBackToTheSameSites() {
        val json = BuiltinSources.toTvBoxJson(BuiltinSources.fallback)
        val cfg = ConfigParser.parse(json, "builtin", "推荐源")

        assertEquals(BuiltinSources.fallback.size, cfg.sites.size)
        assertEquals("量子资源", cfg.sites.first().name)
        assertEquals(BuiltinSources.fallback.first().api, cfg.sites.first().api)
    }

    @Test
    fun allBuiltinSitesArePlayableWithoutSpider() {
        // 这是硬要求：一条 Spider 都不能混进来，否则等于让 App 执行远程代码
        val json = BuiltinSources.toTvBoxJson(BuiltinSources.fallback)
        val cfg = ConfigParser.parse(json, "builtin", "推荐源")
        assertTrue(cfg.sites.all { it.supported })
        assertTrue(cfg.sites.all { it.hasApi })
        assertTrue(cfg.sites.all { it.searchable })
    }

    @Test
    fun everyBuiltinApiIsHttps() {
        // 明文 http 在部分机型上会被系统拦，推荐源必须全是 https
        assertTrue(BuiltinSources.fallback.all { it.api.startsWith("https://") })
    }

    // ---------------------------------------------------------------- 时间

    @Test
    fun parsesCommonTimeFormats() {
        val dash = VodTime.parse("2026-11-08 12:00:00")
        val slash = VodTime.parse("2026/11/08 12:00:00")
        assertTrue(dash > 0L)
        assertEquals(dash, slash)
    }

    @Test
    fun parsesUnixTimestampsInBothLengths() {
        val sec = VodTime.parse("1760000000")
        val ms = VodTime.parse("1760000000000")
        assertEquals(ms, sec)
    }

    @Test
    fun unknownTimeFallsBackToZero() {
        assertEquals(0L, VodTime.parse(""))
        assertEquals(0L, VodTime.parse("刚刚更新"))
        assertEquals(0L, VodTime.parse("--"))
    }

    // ------------------------------------------------------------ 线路描述

    @Test
    fun summaryShowsBandwidthAndResolution() {
        val info = LineProbe.Info(
            kbps = 1500, resolution = "1920x1080", hasSubtitles = true, reachable = true,
        )
        assertEquals("1.5 Mbps · 1920x1080 · 带字幕", info.summary())
    }

    @Test
    fun summaryKeepsLowBitrateReadableInKbps() {
        // 低于 1 Mbps 用 kbps 显示，不然会变成「0.8 Mbps」看不出差距
        val info = LineProbe.Info(kbps = 800, resolution = "1080x608", reachable = true)
        assertEquals("800 kbps · 1080x608", info.summary())
    }

    @Test
    fun unreachableAndUnknownCasesAreDistinct() {
        assertEquals("连不上", LineProbe.Info().summary())
        assertEquals("可播", LineProbe.Info(reachable = true).summary())
        assertEquals("MP4 直链", LineProbe.Info(reachable = true, note = "MP4 直链").summary())
    }
}

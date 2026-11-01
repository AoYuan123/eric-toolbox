package com.eric.toolbox.modules.vod.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

/**
 * 配置还原层的回归测试（裸 JSON / Base64 / 图种 / 注释）。
 *
 * 这层的难点在于「输入完全不可控」：配置是用户自己贴的第三方内容，
 * 线上见过四种壳——裸 JSON、整份 Base64、藏在图片后面的 Base64（图种）、以及各种注释。
 * 每加一种兼容就多一种退化可能，所以每种都要钉死一条用例。
 *
 * 尤其 [keepsDoubleSlashInsideStrings]：注释剥离一旦被改成「全局把 // 删掉」，
 * 站点地址里的 http:// 会被拦腰截断且看起来毫无异常，只能靠这条断言拦住。
 */
class ConfigParserTest {

    private val HTTP_API = "http://cms.example.com/api.php/provide/vod/"
    private val HTTPS_API = "https://cms.example.com/api.php/provide/vod/"

    /**
     * 一段模拟的图片二进制：JPEG 头尾 + 夹着的非 Base64 字符。
     * 真实图种（饭太硬那种）就是 JPEG 数据后面直接拼 Base64，中间没有任何分隔符。
     */
    private val IMAGE_JUNK = buildString {
        append('\u00FF').append('\u00D8') // SOI
        append('\u00FF').append('\u00E0') // APP0
        append("\u0000\u0010JFIF\u0000\u0001\u0001\u0000\u0000\u0001\u0000\u0001\u0000\u0000")
        repeat(240) { append('\u00A3') } // 图像数据，刻意用 Base64 字符集之外的字符
        append('\u00FF').append('\u00D9') // EOI
    }

    private fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray())

    // ------------------------------------------------------------------ 裸 JSON

    @Test
    fun parsesPlainJson() {
        val json = """{"sites":[{"key":"a","name":"A站","type":1,"api":"$HTTPS_API"}]}"""
        val cfg = ConfigParser.parse(json, "s1", "饭太硬")
        assertEquals(1, cfg.sites.size)
        assertEquals("A站", cfg.sites.first().name)
        assertEquals(HTTPS_API, cfg.sites.first().api)
    }

    // ------------------------------------------------------------------ 注释

    @Test
    fun stripsLineComments() {
        val json = """
        {
          // 这一行是被注释掉的坏站点，各家配置很喜欢留着不删
          "sites":[{"key":"a","name":"A站","type":1,"api":"$HTTPS_API"}]
        }
        """.trimIndent()
        val cfg = ConfigParser.parse(json, "s1", "n")
        assertEquals(1, cfg.sites.size)
        assertEquals("A站", cfg.sites.first().name)
    }

    /**
     * 命门用例：注释剥离必须区分「字符串里」和「结构里」。
     * 站点地址十个有八个是 http://，这里的双斜杠绝不能被当成注释起点。
     */
    @Test
    fun keepsDoubleSlashInsideStrings() {
        val json = """{"sites":[{"key":"a","name":"A站","type":1,"api":"$HTTP_API"}]}"""
        val cfg = ConfigParser.parse(json, "s1", "n")
        assertEquals(
            "api 里的 http:// 必须原样保留，被当成注释截断就说明有人在全局替换 //",
            HTTP_API,
            cfg.sites.first().api,
        )
    }

    @Test
    fun stripsBlockComments() {
        val json = """
        { /* 这段来源不明，先留着说明 */ "sites":[
          {"key":"a","name":"A站","type":1,"api":"$HTTPS_API"}
        ] }
        """.trimIndent()
        val cfg = ConfigParser.parse(json, "s1", "n")
        assertEquals(1, cfg.sites.size)
    }

    /** 注释被抹掉后经常留下半拉悬空逗号：{"a":1, // 注释 \n } */
    @Test
    fun toleratesTrailingCommaAfterComment() {
        val json = """
        {"lives":[{"name":"直播","url":"http://a.example/tv.m3u8"}, // 备用源先注释掉
        ]}
        """.trimIndent()
        val cfg = ConfigParser.parse(json, "s1", "n")
        assertEquals(1, cfg.lives.size)
        assertEquals("直播", cfg.lives.first().name)
    }

    // ------------------------------------------------------------------ 配对

    /** 站点名里出现花括号时，抠 JSON 的括号配对必须跳过字符串内部 */
    @Test
    fun keepsBracesInsideStrings() {
        val json = """{"sites":[{"key":"a","name":"测试}站点{名","type":1,"api":"$HTTPS_API"}]}"""
        val cfg = ConfigParser.parse(json, "s1", "n")
        assertEquals(1, cfg.sites.size)
        assertEquals("测试}站点{名", cfg.sites.first().name)
    }

    // ------------------------------------------------------------------ Base64

    @Test
    fun decodesWholeBase64() {
        val inner = """{"sites":[{"key":"a","name":"A站","type":1,"api":"$HTTPS_API"}]}"""
        val cfg = ConfigParser.parse(b64(inner), "s1", "n")
        assertEquals(1, cfg.sites.size)
        assertEquals("A站", cfg.sites.first().name)
    }

    // ------------------------------------------------------------------ 图种

    /**
     * 图种：一张图片后面直接拼 Base64，线上最常见也最容易整个模块「识别不了」。
     * 断言里连 Base64 段前的 U+FFFD 错位字节也一并验证——
     * 图片区被 UTF-8 解码后会产生替换字符，但 Base64 段本身是 ASCII，不受影响。
     */
    @Test
    fun extractsStegoBehindImage() {
        val inner = """{"sites":[
            {"key":"a","name":"一号站","type":1,"api":"$HTTPS_API"},
            {"key":"b","name":"二号站","type":1,"api":"$HTTPS_API"}
        ],"lives":[{"name":"直播","url":"http://a.example/tv.m3u8"}]}"""
        val stego = IMAGE_JUNK + b64(inner)

        val cfg = ConfigParser.parse(stego, "s1", "饭太硬")
        assertEquals(2, cfg.sites.size)
        assertEquals("一号站", cfg.sites.first().name)
        assertEquals(1, cfg.lives.size)
        assertTrue("应当告知用户这份配置藏在图片里", cfg.warnings.any { it.contains("图种") })
    }

    // ------------------------------------------------------------------ Spider

    /** 大配置里 Spider 常占几十个，必须汇总成一句，不能逐条把屏幕刷爆 */
    @Test
    fun aggregatesSpiderWarnings() {
        val json = """{"sites":[
            {"key":"a","name":"豆豆","type":3,"api":"csp_DouDou"},
            {"key":"b","name":"厂长","type":3,"api":"csp_CC"},
            {"key":"c","name":"原创","type":3,"api":"csp_YC"},
            {"key":"d","name":"可用站","type":1,"api":"$HTTPS_API"}
        ]}"""
        val cfg = ConfigParser.parse(json, "s1", "n")
        assertEquals(1, cfg.sites.size)
        assertEquals("可用站", cfg.sites.first().name)

        // 注意：这里不能写成 `?: fail(...)`。JUnit 的 fail() 返回 void，
        // Kotlin 眼里是 Unit，Elvis 两侧的共同类型会被推成 Any，
        // 后面调 .contains() 就编译不过了（本次 CI 第二轮就栽在这）。
        val spider = cfg.warnings.firstOrNull { it.contains("Spider") }
        if (spider == null) {
            fail("应该有 Spider 汇总提示")
            return
        }
        assertTrue("汇总里要报数量：$spider", spider.contains("3 个"))
        assertTrue("汇总里要举例子：$spider", spider.contains("豆豆"))
        assertTrue("警告不该刷屏：${cfg.warnings}", cfg.warnings.size <= 2)
    }

    // ------------------------------------------------------------------ 兜底

    @Test
    fun throwsMeaningfulErrorOnGarbage() {
        try {
            ConfigParser.parse("hello world this is nothing useful", "s1", "n")
            fail("应当报告无法识别")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("看不懂"))
        }
    }
}

package com.eric.toolbox.modules.vod.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「粘一条链接」这条路的回归测试。
 *
 * 这里不联网：只测纯判定的部分（URL 拼接、响应体识别、配置统计）。
 * 真实源站的可用性会变，不能写进单测，否则包一换就红。
 */
class QuickAddTest {

    // ---------------------------------------------------------- URL 拼接

    @Test
    fun `没有 query 的接口补问号`() {
        assertEquals(
            "https://x.com/api.php/provide/vod?ac=list",
            QuickAdd.withAcList("https://x.com/api.php/provide/vod"),
        )
    }

    @Test
    fun `已有 query 的接口用 and 连接`() {
        assertEquals(
            "https://x.com/api.php/provide/vod?from=1&ac=list",
            QuickAdd.withAcList("https://x.com/api.php/provide/vod?from=1"),
        )
    }

    @Test
    fun `结尾就是问号的不再加分隔符`() {
        assertEquals(
            "https://x.com/api.php/provide/vod?ac=list",
            QuickAdd.withAcList("https://x.com/api.php/provide/vod?"),
        )
    }

    @Test
    fun `已经带了 ac 参数的原样返回`() {
        val u = "https://x.com/api.php/provide/vod?ac=list&wd=abc"
        assertEquals(u, QuickAdd.withAcList(u))
    }

    // ---------------------------------------------------------- 响应体识别

    @Test
    fun `标准 JSON 接口能读出影片数与分类数`() {
        val body = """{"code":1,"list":[{"vod_id":1},{"vod_id":2}],"class":[{"type_id":1}]}"""
        val r = QuickAdd.readCms(body)
        assertNotNull(r)
        assertEquals(2, r!!.hits)
        assertEquals(1, r.cats)
        assertFalse(r.xml)
    }

    @Test
    fun `只有分类没有影片也算接口通`() {
        // 空结果接口不能判成不可用：站点通、只是当前没数据
        val r = QuickAdd.readCms("""{"list":[],"class":[{"type_id":1},{"type_id":2}]}""")
        assertNotNull(r)
        assertEquals(0, r!!.hits)
        assertEquals(2, r.cats)
    }

    @Test
    fun `普通 JSON 不当成接口`() {
        assertNull(QuickAdd.readCms("""{"code":0,"msg":"参数错误"}"""))
    }

    @Test
    fun `XML 接口能数出 video 节点`() {
        val body = "<?xml version=\"1.0\"?><rss><list><video><name>a</name></video><video><name>b</name></video></list></rss>"
        val r = QuickAdd.readCms(body)
        assertNotNull(r)
        assertEquals(2, r!!.hits)
        assertTrue(r.xml)
    }

    @Test
    fun `HTML 网页不会被当成 XML 接口`() {
        val body = "<!DOCTYPE html><html><head><title>导航</title></head><body>hi</body></html>"
        assertNull(QuickAdd.readCms(body))
    }

    @Test
    fun `空响应不崩`() {
        assertNull(QuickAdd.readCms(""))
        assertNull(QuickAdd.readCms("   "))
    }

    // ------------------------------------------------------------ 配置统计

    @Test
    fun `配置统计把 Spider 和无效地址分开计`() {
        val cfg = """
            {"sites":[
              {"key":"a","name":"正常站","type":1,"api":"https://a.com/api.php/provide/vod"},
              {"key":"b","name":"脚本站","type":3,"api":"./lib/drpy2.min.js"},
              {"key":"c","name":"相对路径","type":1,"api":"./api.php"}
            ]}
        """.trimIndent()
        val v = ConfigParser.parse(cfg, "t", "t")
        assertEquals(3, v.stats.total)
        assertEquals(1, v.stats.spider)
        assertEquals(1, v.stats.noApi)
        assertEquals(1, v.stats.usable)
        assertEquals(2, v.stats.skipped)
        assertEquals(listOf("正常站"), v.sites.map { it.name })
    }

    @Test
    fun `整份全是 Spider 时可用数为零`() {
        val cfg = """
            {"sites":[
              {"key":"a","name":"x","type":3,"api":"./lib/drpy2.min.js"},
              {"key":"b","name":"y","type":3,"api":"./lib/lf_search3_min.js"}
            ]}
        """.trimIndent()
        val v = ConfigParser.parse(cfg, "t", "t")
        assertEquals(2, v.stats.total)
        assertEquals(2, v.stats.spider)
        assertEquals(0, v.stats.usable)
        assertTrue(v.sites.isEmpty())
    }

    @Test
    fun `type 写成字符串也能认`() {
        // 实际配置里出现过 "type":"3"，optInt 要能吃下
        val cfg = """{"sites":[{"key":"a","name":"x","type":"3","api":"./lib/drpy2.min.js"}]}"""
        val v = ConfigParser.parse(cfg, "t", "t")
        assertEquals(1, v.stats.spider)
        assertEquals(0, v.stats.usable)
    }

    @Test
    fun `手动添加的站点能写回再读出来`() {
        // persist 需要 Context，这里只验证生成的 JSON 能被解析回同样的站点
        val json = """
            {"name":"手动添加","sites":[
              {"key":"quick_1","name":"bfzyapi.com","type":1,"api":"https://bfzyapi.com/api.php/provide/vod","searchable":true}
            ]}
        """.trimIndent()
        val v = ConfigParser.parse(json, QuickAdd.MANUAL_SUB_ID, "手动添加")
        assertEquals(1, v.sites.size)
        assertEquals("bfzyapi.com", v.sites[0].name)
        assertEquals(QuickAdd.MANUAL_SUB_ID, v.sites[0].subId)
        assertTrue(v.sites[0].hasApi)
    }
}

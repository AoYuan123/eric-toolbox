package com.eric.toolbox.modules.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 书内资源取不到是"图片不显示"的头号原因：HTML 里的路径跟 zip 里的条目名经常差一点
 * （大小写、目录、多一层 ../）。这里造一本假的 EPUB，把几种对不上的写法都过一遍。
 * 测试走 BookContent（阅读器真正的入口），不直接碰 Epub 内部。
 */
class EpubAssetTest {

    private val jpeg = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x02, 0x03,
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): File {
        val f = File.createTempFile("book", ".epub")
        f.deleteOnExit()
        ZipOutputStream(f.outputStream()).use { z ->
            for ((name, bytes) in entries) {
                z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry()
            }
        }
        return f
    }

    /** 造一本书：图片在 zip 里的真实名字由 imageEntry 决定，OPF 里声明的 media-type 可空。 */
    private fun book(imageEntry: String, mediaType: String): BookContent {
        val container = """<?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>""".trimIndent()
        // 三引号里没法以引号结尾，属性引号单独拼
        val q = "\""
        val mt = if (mediaType.isEmpty()) "" else " media-type=$q$mediaType$q"
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>测试书</dc:title></metadata>
              <manifest>
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="i1" href="$imageEntry"$mt/>
              </manifest>
              <spine><itemref idref="c1"/></spine>
            </package>""".trimIndent()
        val ch = """<?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>一</title></head>
            <body><p>正文</p><img src="../Images/a.jpg" alt=""/></body></html>""".trimIndent()
        val f = zipOf(
            "META-INF/container.xml" to container.toByteArray(),
            "OEBPS/content.opf" to opf.toByteArray(),
            "OEBPS/text/ch1.xhtml" to ch.toByteArray(),
            imageEntry to jpeg,
        )
        return BookContent.open(f, "epub")
    }

    @Test
    fun `路径完全一致时照常取到`() {
        book("images/a.jpg", "image/jpeg").use { c ->
            assertEquals(1, c.chapterCount)
            assertEquals("OEBPS/text/ch1.xhtml", c.chapterPath(0))
            assertNotNull(c.resource("OEBPS/images/a.jpg"))
        }
    }

    @Test
    fun `大小写不一致也能取到`() {
        // zip 里是 images/A.JPG，网页请求的是 OEBPS/Images/a.jpg
        book("images/A.JPG", "image/jpeg").use { c ->
            assertNotNull(c.resource("OEBPS/Images/a.jpg"))
            assertNotNull(c.resource("oebps/IMAGES/A.JPG"))
        }
    }

    @Test
    fun `目录名写错时按文件名兜底`() {
        // 网页把 ../Images 写成了同目录下的 Images（路径多一层），按文件名还能找回来
        book("images/a.jpg", "image/jpeg").use { c ->
            assertNotNull(c.resource("OEBPS/text/Images/a.jpg"))
        }
    }

    @Test
    fun `同名文件有多个时不乱认`() {
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>t</dc:title></metadata>
              <manifest>
                <item id="c" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="p" href="a/cover.jpg" media-type="image/jpeg"/>
                <item id="q" href="b/cover.jpg" media-type="image/jpeg"/>
              </manifest>
              <spine><itemref idref="c"/></spine>
            </package>""".trimIndent()
        val f = zipOf(
            "META-INF/container.xml" to """<?xml version="1.0" encoding="UTF-8"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray(),
            "content.opf" to opf.toByteArray(),
            "ch1.xhtml" to "<html><body><p>x</p></body></html>".toByteArray(),
            "a/cover.jpg" to jpeg,
            "b/cover.jpg" to jpeg,
        )
        BookContent.open(f, "epub").use { c ->
            // 路径对得上就用对得上的那个（不能因为重名就随便挑）
            assertNotNull(c.resource("a/cover.jpg"))
            assertNotNull(c.resource("b/cover.jpg"))
            // 完全对不上的名字要老实返回空，不能拿别的图顶上
            assertNull(c.resource("c/zzz.jpg"))
            assertNull(c.resource("c/other.jpg"))
        }
    }

    @Test
    fun `OPF 没写 media-type 时按没声明处理`() {
        book("images/a.jpg", "").use { c ->
            // 不是把空串丢给 WebView（空类型图就不画了），而是走扩展名
            assertEquals("image/jpeg", c.mime("OEBPS/images/a.jpg"))
            assertEquals("image/jpeg", c.asset("OEBPS/images/a.jpg")!!.mime)
        }
    }

    @Test
    fun `声明的类型对不上文件头时以文件头为准`() {
        book("images/a.jpg", "application/octet-stream").use { c ->
            assertEquals("image/jpeg", c.asset("OEBPS/images/a.jpg")!!.mime)
        }
    }

    @Test
    fun `取不到的资源返回空而不是抛异常`() {
        book("images/a.jpg", "image/jpeg").use { c ->
            assertNull(c.resource("OEBPS/Images/不存在.png"))
            assertNull(c.resource(""))
        }
    }

    @Test
    fun `按文件头认 png gif webp svg`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        val gif = "GIF89a....".toByteArray()
        val webp = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBPVP8 ".toByteArray()
        val svg = "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".toByteArray()
        assertEquals("image/png", BookContent.sniff(png, "application/octet-stream"))
        assertEquals("image/gif", BookContent.sniff(gif, ""))
        assertEquals("image/webp", BookContent.sniff(webp, "application/octet-stream"))
        assertEquals("image/svg+xml", BookContent.sniff(svg, "application/octet-stream"))
    }

    @Test
    fun `章节正文里内嵌 svg 不能被当成图片`() {
        val xhtml = "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>字</p><svg viewBox=\"0 0 1 1\"></svg></body></html>".toByteArray()
        assertEquals("text/html", BookContent.sniff(xhtml, "text/html"))
        assertEquals("application/octet-stream", BookContent.sniff(ByteArray(0), ""))
    }

    @Test
    fun `jfif 也算 jpeg`() {
        assertEquals("image/jpeg", BookContent.mimeOf("Images/图1.jfif"))
        assertEquals("image/jpeg", BookContent.mimeOf("Images/图1.JPG"))
        assertEquals("image/svg+xml", BookContent.mimeOf("Images/图1.svg"))
    }
}

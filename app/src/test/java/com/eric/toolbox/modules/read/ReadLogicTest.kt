package com.eric.toolbox.modules.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReadLogicTest {

    // 11-07 实测 https://gutendex.com/books/?search=pride%20prejudice 的第一条（字段原样，删去其余条目）
    private val gutendex = """
        {"count":6,"next":null,"previous":null,"results":[{"id":1342,"title":"Pride and Prejudice",
        "authors":[{"name":"Austen, Jane","birth_year":1775,"death_year":1817}],"languages":["en"],"download_count":208430,
        "formats":{"application/epub+zip":"https://www.gutenberg.org/ebooks/1342.epub3.images",
        "image/jpeg":"https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg",
        "text/plain; charset=utf-8":"https://www.gutenberg.org/ebooks/1342.txt.utf-8"}},
        {"id":0,"title":"bad"},{"id":5,"title":"","authors":[]}]}
    """.trimIndent()

    // 11-07 实测 https://standardebooks.org/ebooks?query=austen 的列表结构（截取一条）
    private val seHtml = """
        <ol class="ebooks-list grid">
        <li typeof="schema:Book" about="/ebooks/jane-austen/northanger-abbey">
          <div class="thumbnail-container" aria-hidden="true"><a href="/ebooks/jane-austen/northanger-abbey" tabindex="-1" property="schema:url">
          <picture><img src="/images/covers/jane-austen_northanger-abbey/425395aabd215dd5589126afb1b0650788f2f1eb/cover@2x.jpg" alt="" property="schema:image" height="335" width="224" loading="lazy"/></picture></a></div>
          <p><a href="/ebooks/jane-austen/northanger-abbey" property="schema:url"><span property="schema:name">Northanger Abbey</span></a></p>
          <p class="author" typeof="schema:Person" property="schema:author" resource="/ebooks/jane-austen"><a href="https://standardebooks.org/ebooks/jane-austen" property="schema:url"><span property="schema:name">Jane Austen</span></a></p>
        </li></ol>
    """.trimIndent()

    @Test fun gutendexParse() {
        val hits = ReadLogic.parseGutendex(gutendex)
        assertEquals(1, hits.size)
        val h = hits[0]
        assertEquals("Pride and Prejudice", h.title)
        assertEquals(listOf("Jane Austen"), h.authors)
        assertEquals(1342, h.gutenbergId)
        assertEquals(208430, h.downloads)
        assertEquals("https://www.gutenberg.org/ebooks/1342.epub.noimages", h.editions[0].url) // 11-07 实测 0.6 MB
        assertEquals("https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg", h.coverUrl)
        assertTrue(h.downloadable)
    }

    @Test fun standardParse() {
        val hits = ReadLogic.parseStandard(seHtml)
        assertEquals(1, hits.size)
        assertEquals("Northanger Abbey", hits[0].title)
        assertEquals(listOf("Jane Austen"), hits[0].authors)
        assertEquals("/ebooks/jane-austen/northanger-abbey", hits[0].seBookPath)
        assertTrue(hits[0].coverUrl!!.startsWith("https://standardebooks.org/images/covers/"))
        assertTrue(hits[0].downloadable)
    }

    @Test fun standardEpubLink() {
        // 11-07 实测 /ebooks/jane-austen/emma 书页里的下载链接顺序
        val page = """
            <a href="/ebooks/jane-austen/emma/downloads/jane-austen_emma.epub">x</a>
            <a href="/ebooks/jane-austen/emma/downloads/jane-austen_emma.azw3">x</a>
            <a href="/ebooks/jane-austen/emma/downloads/jane-austen_emma.kepub.epub">x</a>
            <a href="/ebooks/jane-austen/emma/downloads/jane-austen_emma_advanced.epub">x</a>
        """
        assertEquals("https://standardebooks.org/ebooks/jane-austen/emma/downloads/jane-austen_emma.epub?source=download", ReadLogic.standardEpubFromBookPage(page))
        assertNull(ReadLogic.standardEpubFromBookPage("<a href=\"/ebooks/a/b/downloads/a_b_advanced.epub\">"))
    }

    @Test fun openLibParse() {
        val json = """{"numFound":2,"docs":[{"key":"/works/OL1W","title":"Dream of the Red Chamber","author_name":["Cao Xueqin"],"cover_i":6655966,"ebook_access":"borrowable","language":["eng","chi"]},{"key":"/works/OL2W"}]}"""
        val hits = ReadLogic.parseOpenLib(json)
        assertEquals(1, hits.size)
        assertEquals("https://covers.openlibrary.org/b/id/6655966-M.jpg", hits[0].coverUrl)
        assertEquals("https://openlibrary.org/works/OL1W", hits[0].webUrl)
        assertFalse(hits[0].downloadable)
    }

    @Test fun mergeSameBook() {
        val g = ReadLogic.parseGutendex(gutendex)
        val s = listOf(ReadLogic.parseStandard(seHtml)[0].copy(title = "Pride and Prejudice", authors = listOf("Jane Austen"), key = ReadLogic.mergeKey("Pride and Prejudice", listOf("Jane Austen"))))
        val ol = listOf(ReadLogic.Hit(ReadLogic.mergeKey("The Pride and Prejudice", listOf("Austen")), "The Pride and Prejudice", listOf("Austen"), "", null, listOf(ReadLogic.Source.OPENLIB), emptyList(), "https://x"),
            ReadLogic.Hit(ReadLogic.mergeKey("Other", listOf("X")), "Other", listOf("X"), "", null, listOf(ReadLogic.Source.OPENLIB), emptyList(), "https://y"))
        val m = ReadLogic.merge(g, s, ol)
        assertEquals(2, m.size)
        assertEquals(listOf(ReadLogic.Source.GUTENBERG, ReadLogic.Source.STANDARD, ReadLogic.Source.OPENLIB), m[0].sources)
        assertEquals(1342, m[0].gutenbergId)
        assertNotNull(m[0].seBookPath)
        assertEquals("Other", m[1].title) // 不能下载的排后面
    }

    @Test fun flipNames() {
        assertEquals("Jane Austen", ReadLogic.flipName("Austen, Jane"))
        assertEquals("Homer", ReadLogic.flipName("Homer"))
        assertEquals("Cao, Xueqin, 1715?-1763", ReadLogic.flipName("Cao, Xueqin, 1715?-1763"))
    }

    @Test fun txtChinese() {
        val raw = "书名\r\n作者：某某\r\n\r\n第一章 开始\r\n这是第一章的内容。\r\n\r\n第二章 继续\r\n第二章内容。\r\n第十二回 再续\r\n内容三"
        val ch = ReadLogic.splitTxt(raw)
        assertEquals(listOf("第一章 开始", "第二章 继续", "第十二回 再续"), ch.map { it.title })
        assertEquals("这是第一章的内容。", ch[0].text)
    }

    @Test fun txtEnglishAndFallback() {
        val en = ReadLogic.splitTxt("Preface text that is long enough to keep as an opening section for sure here.\nChapter 1\nIt is a truth.\nCHAPTER II\nMore.")
        assertEquals(listOf("开篇", "Chapter 1", "CHAPTER II"), en.map { it.title })
        val plain = ReadLogic.splitTxt("a".repeat(13000))
        assertEquals(3, plain.size)
    }

    @Test fun txtGbk() {
        val s = "第一章 天下大势，分久必合"
        assertEquals(s, ReadLogic.decodeTxt(s.toByteArray(charset("GBK"))))
        assertEquals(s, ReadLogic.decodeTxt(s.toByteArray(Charsets.UTF_8)))
    }

    @Test fun txtHtmlEscapes() {
        val h = ReadLogic.txtChapterHtml(ReadLogic.TxtChapter("第一章 <x>", "a<b\n\nc&d"))
        assertEquals("<h2>第一章 &lt;x&gt;</h2><p>a&lt;b</p><p>c&amp;d</p>", h)
    }

    @Test fun timeEstimates() {
        assertEquals(4, ReadLogic.minutesLeft(5600, 0.0, 1400.0))
        assertEquals(1, ReadLogic.minutesLeft(5600, 1.0, 1400.0))
        assertEquals(0.5, ReadLogic.bookFraction(listOf(100, 100), 1, 0.0), 1e-9)
        assertEquals(0.75, ReadLogic.bookFraction(listOf(100, 100), 1, 0.5), 1e-9)
        // 一页 1400 字读了 60 秒 → 样本 1400，旧值 1000 → 0.8*1000+0.2*1400
        assertEquals(1080.0, ReadLogic.updateCpm(1000.0, 1400, 60.0), 1e-9)
        assertEquals(1000.0, ReadLogic.updateCpm(1000.0, 1400, 2.0), 1e-9) // 翻太快不算
        assertTrue(ReadLogic.isCjk("天下大势，分久必合，合久必分"))
        assertFalse(ReadLogic.isCjk("It is a truth universally acknowledged"))
    }

    @Test fun stripTagsWorks() {
        assertEquals("Hi & bye ’ A", ReadLogic.stripTags("<html><head><title>x</title></head><body><p>Hi &amp; <b>bye</b> &#8217; &#65;</p><script>evil()</script></body></html>"))
    }

    // ---------- EPUB ----------
    private fun makeEpub(withNav: Boolean): File {
        val f = File.createTempFile("book", ".epub")
        ZipOutputStream(f.outputStream()).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            put("OEBPS/content.opf", """<?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Pride and Prejudice</dc:title><dc:creator>Jane Austen</dc:creator><dc:language>en</dc:language><meta name="cover" content="cov"/></metadata>
                <manifest>
                  <item id="c1" href="text/ch%201.xhtml" media-type="application/xhtml+xml"/>
                  <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                  <item id="bad" href="../../etc/passwd" media-type="application/xhtml+xml"/>
                  <item id="cov" href="img/cover.jpg" media-type="image/jpeg"/>
                  ${if (withNav) """<item id="nav" href="nav.xhtml" properties="nav" media-type="application/xhtml+xml"/>""" else """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>"""}
                </manifest>
                <spine${if (withNav) "" else " toc=\"ncx\""}><itemref idref="c1"/><itemref idref="bad"/><itemref idref="c2"/></spine></package>""")
            put("OEBPS/text/ch 1.xhtml", "<html><body><h1>Chapter 1</h1><p>It is a truth universally acknowledged.</p></body></html>")
            put("OEBPS/text/ch2.xhtml", "<html><body><h1>Chapter 2</h1><p>Mr. Bennet was among the earliest.</p></body></html>")
            put("OEBPS/img/cover.jpg", "JPEG")
            if (withNav) put("OEBPS/nav.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="text/ch%201.xhtml">Chapter 1</a></li><li><a href="text/ch2.xhtml#s">Chapter 2</a><ol><li><a href="text/ch2.xhtml#t">Part</a></li></ol></li></ol></nav></body></html>""")
            else put("OEBPS/toc.ncx", """<?xml version="1.0"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap><navPoint id="a"><navLabel><text>Chapter 1</text></navLabel><content src="text/ch%201.xhtml"/></navPoint><navPoint id="b"><navLabel><text>Chapter 2</text></navLabel><content src="text/ch2.xhtml"/></navPoint></navMap></ncx>""")
        }
        return f
    }

    @Test fun epubNav() {
        val f = makeEpub(true)
        Epub.open(f).use { e ->
            assertEquals("Pride and Prejudice", e.title)
            assertEquals(listOf("Jane Austen"), e.authors)
            assertEquals(listOf("OEBPS/text/ch 1.xhtml", "OEBPS/text/ch2.xhtml"), e.spine) // 越界的条目被丢弃
            assertEquals("OEBPS/img/cover.jpg", e.coverPath)
            assertEquals(listOf("Chapter 1", "Chapter 2", "Part"), e.toc.map { it.title })
            assertEquals(listOf(0, 0, 1), e.toc.map { it.level })
            assertEquals(1, e.spineIndexOf(e.toc[2].href))
        }
        BookContent.open(f, "epub").use { c ->
            assertEquals(2, c.chapterCount)
            assertEquals("Chapter 2 Mr. Bennet was among the earliest.", c.chapterText(1))
            assertEquals("Chapter 1", c.chapterTitle(0))
            assertEquals(1, c.search("bennet").size)
            assertFalse(c.cjk)
        }
        f.delete()
    }

    @Test fun epubNcx() {
        val f = makeEpub(false)
        Epub.open(f).use { e -> assertEquals(listOf("Chapter 1", "Chapter 2"), e.toc.map { it.title }) }
        f.delete()
    }

    @Test fun resolvePaths() {
        assertEquals("OEBPS/text/a.xhtml", Epub.resolve("OEBPS", "text/a.xhtml#x"))
        assertEquals("OEBPS/img/a.png", Epub.resolve("OEBPS/text", "../img/a.png"))
        assertEquals("a b.xhtml", Epub.resolve("", "a%20b.xhtml"))
        assertEquals("c++.xhtml", Epub.resolve("", "c++.xhtml"))
        assertNull(Epub.resolve("OEBPS", "../../x"))
        assertNull(Epub.resolve("OEBPS", "https://evil/x"))
    }

    @Test fun readerHtmlWrap() {
        val s = ReadSettings(fontSize = 20, paged = true)
        val out = ReaderHtml.wrap("<html><head><title>t</title><script>alert(1)</script></head><body onload=\"x()\"><p>Hi</p></body></html>", s, "paper")
        assertFalse(out.contains("alert(1)"))
        assertFalse(out.contains("onload"))
        assertTrue(out.contains("font-size:20px"))
        assertTrue(out.contains("column-width"))
        assertTrue(out.indexOf("window.__next") > out.indexOf("<p>Hi</p>"))
        val bare = ReaderHtml.wrap("<p>x</p>", s.copy(paged = false), "night")
        assertTrue(bare.contains("<body>") && bare.contains("#111214"))
        assertFalse(bare.contains("column-width"))
        assertEquals("night", ReadSettings(autoNight = true).effectiveTheme(23))
        assertEquals("paper", ReadSettings(autoNight = true).effectiveTheme(12))
    }
}

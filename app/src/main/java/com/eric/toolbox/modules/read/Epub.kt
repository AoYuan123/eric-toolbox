package com.eric.toolbox.modules.read

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 极简 EPUB 解析（EPUB 2/3）：container.xml → OPF（书名、作者、manifest、spine）→ 目录（EPUB3 nav 或 EPUB2 NCX）。
 * 只读 zip 里的条目，不解压到磁盘；路径一律在书内规范化，越界（../ 跑出书外）的直接忽略。
 */
class Epub private constructor(private val zip: ZipFile) : AutoCloseable {

    data class Item(val id: String, val href: String, val mediaType: String)
    data class TocEntry(val title: String, val href: String, val level: Int)

    var title: String = ""; private set
    var authors: List<String> = emptyList(); private set
    var language: String = ""; private set
    /** 阅读顺序里的章节文件（书内完整路径）。 */
    var spine: List<String> = emptyList(); private set
    var toc: List<TocEntry> = emptyList(); private set
    var coverPath: String? = null; private set
    private val mediaTypes = HashMap<String, String>()

    fun has(path: String) = zip.getEntry(path) != null
    fun read(path: String): ByteArray? = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
    fun mediaType(path: String): String? = mediaTypes[path]

    /** 目录项对应 spine 的第几章（找不到返回 -1）。 */
    fun spineIndexOf(href: String): Int = spine.indexOf(href.substringBefore('#'))

    override fun close() = zip.close()

    companion object {
        fun open(file: File): Epub {
            val z = ZipFile(file)
            try {
                return Epub(z).also { it.parse() }
            } catch (e: Exception) {
                z.close(); throw e
            }
        }

        private fun dbf() = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // 防 XXE：能设就设（Android 自带解析器对部分特性会抛异常，忽略即可）
            listOf(
                "http://apache.org/xml/features/disallow-doctype-decl" to false,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
            ).forEach { (f, v) -> try { setFeature(f, v) } catch (_: Exception) {} }
        }

        fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
            val b = dbf().newDocumentBuilder()
            b.setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
            return b.parse(ByteArrayInputStream(bytes))
        }

        /** 把 base 目录下的相对 href 规范成书内完整路径；跑出书外返回 null。 */
        fun resolve(baseDir: String, href: String): String? {
            val clean = try { URLDecoder.decode(href.substringBefore('#').replace("+", "%2B"), "UTF-8") } catch (_: Exception) { href.substringBefore('#') }
            if (clean.isEmpty() || clean.contains("://") || clean.startsWith("data:")) return null
            val parts = ArrayList<String>()
            val full = if (clean.startsWith("/")) clean.trimStart('/') else (if (baseDir.isEmpty()) clean else "$baseDir/$clean")
            for (seg in full.split('/')) {
                when (seg) {
                    "", "." -> {}
                    ".." -> { if (parts.isEmpty()) return null; parts.removeAt(parts.size - 1) }
                    else -> parts += seg
                }
            }
            return parts.joinToString("/")
        }

        fun dirOf(path: String) = path.substringBeforeLast('/', "")

        private fun Element.children(local: String): List<Element> {
            val out = ArrayList<Element>()
            val nl = childNodes
            for (i in 0 until nl.length) { val n = nl.item(i); if (n is Element && (n.localName ?: n.nodeName.substringAfter(':')) == local) out += n }
            return out
        }

        private fun org.w3c.dom.Node.all(local: String): List<Element> {
            val nl = (this as? org.w3c.dom.Document)?.getElementsByTagNameNS("*", local) ?: (this as Element).getElementsByTagNameNS("*", local)
            return (0 until nl.length).map { nl.item(it) as Element }
        }

        private fun Element.text() = textContent?.replace(Regex("""\s+"""), " ")?.trim() ?: ""
    }

    private fun parse() {
        val container = read("META-INF/container.xml") ?: throw java.io.IOException("不是 EPUB（缺少 container.xml）")
        val opfPath = parseXml(container).all("rootfile").firstOrNull()?.getAttribute("full-path")?.takeIf { it.isNotBlank() }
            ?: throw java.io.IOException("EPUB 结构不完整（没有 OPF）")
        val opf = parseXml(read(opfPath) ?: throw java.io.IOException("EPUB 缺少 $opfPath"))
        val base = dirOf(opfPath)

        title = opf.all("title").firstOrNull()?.text().orEmpty()
        authors = opf.all("creator").map { it.text() }.filter { it.isNotEmpty() }.distinct()
        language = opf.all("language").firstOrNull()?.text().orEmpty()

        val items = HashMap<String, Item>()
        var navHref: String? = null
        var coverHref: String? = null
        opf.all("item").forEach { e ->
            val path = resolve(base, e.getAttribute("href")) ?: return@forEach
            val it = Item(e.getAttribute("id"), path, e.getAttribute("media-type"))
            items[it.id] = it
            mediaTypes[path] = it.mediaType
            val props = e.getAttribute("properties")
            if (props.split(' ').contains("nav")) navHref = path
            if (props.split(' ').contains("cover-image")) coverHref = path
        }
        if (coverHref == null) {
            val coverId = opf.all("meta").firstOrNull { it.getAttribute("name") == "cover" }?.getAttribute("content")
            coverHref = coverId?.let { items[it]?.href }
        }
        coverPath = coverHref?.takeIf { has(it) }

        val spineEl = opf.all("spine").firstOrNull()
        spine = spineEl?.children("itemref")
            ?.filter { it.getAttribute("linear") != "no" }
            ?.mapNotNull { items[it.getAttribute("idref")]?.href }
            ?.filter { has(it) }
            ?.distinct()
            .orEmpty()
        if (spine.isEmpty()) throw java.io.IOException("EPUB 没有可读的章节")

        toc = navHref?.let { runCatching { parseNav(it) }.getOrNull() }?.takeIf { it.isNotEmpty() }
            ?: spineEl?.getAttribute("toc")?.let { items[it]?.href }?.let { runCatching { parseNcx(it) }.getOrNull() }
            ?: emptyList()
        if (title.isEmpty()) title = "未命名"
    }

    private fun parseNav(path: String): List<TocEntry> {
        val doc = parseXml(read(path) ?: return emptyList())
        val nav = doc.all("nav").firstOrNull { el ->
            (el.getAttributeNS("http://www.idpf.org/2007/ops", "type").ifEmpty { el.getAttribute("epub:type") }).contains("toc")
        } ?: doc.all("nav").firstOrNull() ?: return emptyList()
        val out = ArrayList<TocEntry>()
        fun walk(ol: Element, level: Int) {
            ol.children("li").forEach { li ->
                val a = li.all("a").firstOrNull()
                if (a != null) {
                    val href = resolve(dirOf(path), a.getAttribute("href"))
                    if (href != null) out += TocEntry(a.text(), href + a.getAttribute("href").substringAfter('#', "").let { if (it.isEmpty()) "" else "#$it" }, level)
                }
                li.children("ol").forEach { walk(it, level + 1) }
            }
        }
        nav.children("ol").forEach { walk(it, 0) }
        return out
    }

    private fun parseNcx(path: String): List<TocEntry> {
        val doc = parseXml(read(path) ?: return emptyList())
        val out = ArrayList<TocEntry>()
        fun walk(parent: Element, level: Int) {
            parent.children("navPoint").forEach { np ->
                val label = np.all("text").firstOrNull()?.text().orEmpty()
                val src = np.children("content").firstOrNull()?.getAttribute("src").orEmpty()
                val href = resolve(dirOf(path), src)
                if (href != null && label.isNotEmpty()) out += TocEntry(label, href + src.substringAfter('#', "").let { if (it.isEmpty()) "" else "#$it" }, level)
                walk(np, level + 1)
            }
        }
        doc.all("navMap").firstOrNull()?.let { walk(it, 0) }
        return out
    }
}

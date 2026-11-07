package com.eric.toolbox.modules.read

import java.io.File

/**
 * 阅读器看到的一本书：章节列表、每章 HTML、书内资源（图片、样式）。EPUB 和 TXT 统一成这个样子。
 * 书内资源走虚拟地址 https://book.local/<书内路径>，由 WebView 拦截后从 zip 里读，不联网。
 */
abstract class BookContent : AutoCloseable {
    abstract val title: String
    abstract val language: String
    abstract val chapterCount: Int
    /** 每章的书内路径（用作 WebView 的 base url）。 */
    abstract fun chapterPath(i: Int): String
    /** 章节原始 HTML（未加阅读器样式）。 */
    abstract fun chapterHtml(i: Int): String
    abstract fun resource(path: String): ByteArray?
    abstract fun mime(path: String): String
    abstract val toc: List<Pair<String, Int>>

    private val textCache = HashMap<Int, String>()
    fun chapterText(i: Int): String = synchronized(textCache) { textCache.getOrPut(i) { ReadLogic.stripTags(chapterHtml(i)) } }
    val chapterChars: List<Int> by lazy { (0 until chapterCount).map { chapterText(it).length.coerceAtLeast(1) } }
    val cjk: Boolean by lazy { ReadLogic.isCjk((0 until minOf(chapterCount, 3)).joinToString(" ") { chapterText(it).take(2000) }) }

    /** 章节标题：优先用目录里的名字，没有就用“第 N 章”。 */
    fun chapterTitle(i: Int): String = toc.lastOrNull { it.second == i }?.first ?: toc.lastOrNull { it.second in 0..i }?.first ?: "第 ${i + 1} 部分"

    data class SearchHit(val chapter: Int, val snippet: String, val offset: Int)

    /** 全文搜索（不区分大小写），最多返回 200 条。 */
    fun search(q: String, limit: Int = 200): List<SearchHit> {
        val needle = q.trim()
        if (needle.length < 2 && !cjk) return emptyList()
        if (needle.isEmpty()) return emptyList()
        val out = ArrayList<SearchHit>()
        for (i in 0 until chapterCount) {
            val t = chapterText(i)
            var from = 0
            while (out.size < limit) {
                val k = t.indexOf(needle, from, ignoreCase = true)
                if (k < 0) break
                val s = maxOf(0, k - 30); val e = minOf(t.length, k + needle.length + 40)
                out += SearchHit(i, (if (s > 0) "…" else "") + t.substring(s, e) + (if (e < t.length) "…" else ""), k)
                from = k + needle.length
            }
            if (out.size >= limit) break
        }
        return out
    }

    companion object {
        fun open(file: File, format: String): BookContent = if (format == "txt") TxtContent(file) else EpubContent(Epub.open(file))

        fun mimeOf(path: String): String = when (path.substringAfterLast('.').lowercase()) {
            "xhtml", "xht" -> "application/xhtml+xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "otf" -> "font/otf"
            "ttf" -> "font/ttf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            else -> "application/octet-stream"
        }
    }
}

private class EpubContent(private val epub: Epub) : BookContent() {
    override val title get() = epub.title
    override val language get() = epub.language
    override val chapterCount get() = epub.spine.size
    override fun chapterPath(i: Int) = epub.spine[i]
    override fun chapterHtml(i: Int): String = epub.read(epub.spine[i])?.let { decode(it) } ?: ""
    override fun resource(path: String) = epub.read(path)
    override fun mime(path: String) = epub.mediaType(path) ?: mimeOf(path)
    override val toc: List<Pair<String, Int>> by lazy {
        epub.toc.mapNotNull { t -> epub.spineIndexOf(t.href).takeIf { it >= 0 }?.let { (("　".repeat(t.level.coerceAtMost(3))) + t.title) to it } }
    }
    override fun close() = epub.close()

    private fun decode(b: ByteArray): String {
        val head = String(b, 0, minOf(b.size, 300), Charsets.ISO_8859_1)
        val cs = Regex("""encoding=["']([A-Za-z0-9_-]+)["']""").find(head)?.groupValues?.get(1)
        return runCatching { String(b, charset(cs ?: "UTF-8")) }.getOrElse { String(b, Charsets.UTF_8) }
    }
}

private class TxtContent(file: File) : BookContent() {
    private val chapters = ReadLogic.splitTxt(file.readText())
    override val title = file.nameWithoutExtension
    override val language = ""
    override val chapterCount get() = chapters.size
    override fun chapterPath(i: Int) = "txt/$i.html"
    override fun chapterHtml(i: Int) = "<html><body>" + ReadLogic.txtChapterHtml(chapters[i]) + "</body></html>"
    override fun resource(path: String): ByteArray? = null
    override fun mime(path: String) = "text/html"
    override val toc = chapters.mapIndexed { i, c -> c.title to i }
    override fun close() {}
}

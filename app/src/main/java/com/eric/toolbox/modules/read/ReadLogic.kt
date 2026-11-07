package com.eric.toolbox.modules.read

import org.json.JSONObject
import java.net.URLEncoder

/**
 * 读书模块的纯逻辑（不依赖 Android，可单测）：三个公版书库的检索地址与解析、合并去重、TXT 分章、阅读时间估算。
 *
 * 书库（11-07 用户电脑国内网络实测）：
 * - Gutendex（Project Gutenberg 的第三方 JSON 接口）：https://gutendex.com/books/?search=…，偶尔 45 秒以上不返回，所以单独给长超时。
 * - Standard Ebooks：完整 OPDS 目录需要捐款会员，网页搜索 /ebooks?query=… 公开，这里解析网页；
 *   下载链接 …/downloads/xxx.epub 直接访问返回“感谢下载”页面，加 ?source=download 才是 EPUB 文件。
 * - Open Library：search.json 公开，能查书目和封面；借阅要登录 Internet Archive，App 里不做，只跳网页。
 */
object ReadLogic {

    enum class Source(val label: String) { GUTENBERG("Gutenberg"), STANDARD("Standard Ebooks"), OPENLIB("Open Library"), LOCAL("本机") }

    /** 一个可下载的版本。 */
    data class Edition(val source: Source, val label: String, val url: String, val note: String = "")

    /** 搜索结果里的一本书（可能合并了多个书库）。 */
    data class Hit(
        val key: String,
        val title: String,
        val authors: List<String>,
        val language: String,
        val coverUrl: String?,
        val sources: List<Source>,
        val editions: List<Edition>,
        /** 只能去网页看（Open Library 借阅等） */
        val webUrl: String?,
        val gutenbergId: Int? = null,
        val downloads: Int? = null,
        val seBookPath: String? = null,
    ) {
        val downloadable get() = editions.isNotEmpty() || seBookPath != null
    }

    private fun enc(q: String) = URLEncoder.encode(q.trim(), "UTF-8")

    fun gutendexUrl(q: String) = "https://gutendex.com/books/?search=${enc(q)}"
    fun standardUrl(q: String) = "https://standardebooks.org/ebooks?query=${enc(q)}"
    fun openLibUrl(q: String) =
        "https://openlibrary.org/search.json?q=${enc(q)}&limit=20&fields=key,title,author_name,first_publish_year,cover_i,ebook_access,language"

    // ---------- Gutendex ----------
    fun parseGutendex(json: String): List<Hit> {
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Hit>()
        for (i in 0 until arr.length()) {
            val b = arr.getJSONObject(i)
            val id = b.optInt("id", -1)
            val title = b.optString("title").trim()
            if (id <= 0 || title.isEmpty()) continue
            val authors = b.optJSONArray("authors")?.let { a -> (0 until a.length()).map { flipName(a.getJSONObject(it).optString("name")) } } ?: emptyList()
            val langs = b.optJSONArray("languages")?.let { l -> (0 until l.length()).map { l.getString(it) } } ?: emptyList()
            val formats = b.optJSONObject("formats")
            val hasEpub = formats?.has("application/epub+zip") == true
            val cover = formats?.optString("image/jpeg")?.takeIf { it.startsWith("https://") }
            val eds = if (hasEpub) listOf(
                Edition(Source.GUTENBERG, "EPUB · 无插图", "https://www.gutenberg.org/ebooks/$id.epub.noimages", "体积小，推荐"),
                Edition(Source.GUTENBERG, "EPUB · 带插图", "https://www.gutenberg.org/ebooks/$id.epub3.images", "体积较大"),
            ) else emptyList()
            out += Hit(
                key = mergeKey(title, authors), title = title, authors = authors, language = langs.joinToString(","),
                coverUrl = cover, sources = listOf(Source.GUTENBERG), editions = eds,
                webUrl = "https://www.gutenberg.org/ebooks/$id", gutenbergId = id, downloads = b.optInt("download_count", 0),
            )
        }
        return out
    }

    /** Gutenberg 作者名是“Austen, Jane”，转成“Jane Austen”。 */
    fun flipName(n: String): String {
        val s = n.trim()
        val i = s.indexOf(", ")
        if (i <= 0) return s
        val rest = s.substring(i + 2)
        if (rest.contains(", ")) return s
        return "$rest ${s.substring(0, i)}".trim()
    }

    // ---------- Standard Ebooks（解析网页） ----------
    private val seItem = Regex("""<li[^>]*typeof="schema:Book"[^>]*about="(/ebooks/[^"]+)"[\s\S]*?</li>""")
    private val seImg = Regex("""<img[^>]*src="(/images/covers/[^"]+)"""")
    private val seName = Regex("""<span property="schema:name">([^<]*)</span>""")

    fun parseStandard(html: String): List<Hit> = seItem.findAll(html).mapNotNull { m ->
        val block = m.value
        val path = m.groupValues[1]
        val names = seName.findAll(block).map { htmlUnescape(it.groupValues[1]).trim() }.toList()
        val title = names.firstOrNull()?.ifEmpty { null } ?: return@mapNotNull null
        val authors = names.drop(1)
        val img = seImg.find(block)?.groupValues?.get(1)
        Hit(
            key = mergeKey(title, authors), title = title, authors = authors, language = "en",
            coverUrl = img?.let { "https://standardebooks.org$it" }, sources = listOf(Source.STANDARD), editions = emptyList(),
            webUrl = "https://standardebooks.org$path", seBookPath = path,
        )
    }.toList()

    private val seEpubLink = Regex("""href="(/ebooks/[^"]+/downloads/[^"]+\.epub)"""")

    /** 从 Standard Ebooks 书页里找“兼容版 EPUB”（排除 advanced / kepub），返回带 ?source=download 的直链。 */
    fun standardEpubFromBookPage(html: String): String? {
        val links = seEpubLink.findAll(html).map { it.groupValues[1] }.distinct().toList()
        val pick = links.firstOrNull { !it.endsWith("_advanced.epub") && !it.endsWith(".kepub.epub") } ?: return null
        return "https://standardebooks.org$pick?source=download"
    }

    // ---------- Open Library ----------
    fun parseOpenLib(json: String): List<Hit> {
        val docs = JSONObject(json).optJSONArray("docs") ?: return emptyList()
        val out = ArrayList<Hit>()
        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            val title = d.optString("title").trim()
            if (title.isEmpty()) continue
            val authors = d.optJSONArray("author_name")?.let { a -> (0 until minOf(a.length(), 3)).map { a.getString(it) } } ?: emptyList()
            val key = d.optString("key")
            val cover = d.optInt("cover_i", 0).takeIf { it > 0 }?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" }
            val langs = d.optJSONArray("language")?.let { l -> (0 until minOf(l.length(), 3)).map { l.getString(it) } } ?: emptyList()
            out += Hit(
                key = mergeKey(title, authors), title = title, authors = authors, language = langs.joinToString(","),
                coverUrl = cover, sources = listOf(Source.OPENLIB), editions = emptyList(),
                webUrl = if (key.startsWith("/")) "https://openlibrary.org$key" else null,
            )
        }
        return out
    }

    fun openLibAccess(json: String): Map<String, String> {
        val docs = JSONObject(json).optJSONArray("docs") ?: return emptyMap()
        val m = HashMap<String, String>()
        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            m[d.optString("key")] = d.optString("ebook_access", "no_ebook")
        }
        return m
    }

    // ---------- 合并 ----------
    fun normTitle(t: String): String =
        t.lowercase().substringBefore(';').substringBefore(':').replace(Regex("""^(the|a|an)\s+"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), "")

    fun mergeKey(title: String, authors: List<String>): String {
        val sur = authors.firstOrNull()?.trim()?.split(Regex("""\s+"""))?.lastOrNull()?.lowercase()?.replace(Regex("""[^\p{L}]"""), "") ?: ""
        return normTitle(title) + "|" + sur
    }

    /** 合并多个书库的结果：同名同作者合成一张卡片；能下载的排前面，其余保持各书库自己的相关度顺序。 */
    fun merge(vararg lists: List<Hit>): List<Hit> {
        val order = LinkedHashMap<String, Hit>()
        for (list in lists) for (h in list) {
            val old = order[h.key]
            order[h.key] = if (old == null) h else old.copy(
                sources = (old.sources + h.sources).distinct(),
                editions = old.editions + h.editions,
                coverUrl = old.coverUrl ?: h.coverUrl,
                gutenbergId = old.gutenbergId ?: h.gutenbergId,
                downloads = old.downloads ?: h.downloads,
                seBookPath = old.seBookPath ?: h.seBookPath,
                webUrl = old.webUrl ?: h.webUrl,
                authors = old.authors.ifEmpty { h.authors },
            )
        }
        return order.values.sortedBy { if (it.downloadable) 0 else 1 }
    }

    // ---------- TXT ----------
    private val chapterLine = Regex(
        """^\s*(第[零〇一二两三四五六七八九十百千万0-9]{1,8}[章回节卷集部篇](?:[ \t\u3000:：、.．\-—]+[^\n]{0,30})?|(?i:chapter)\s+[0-9IVXLCivxlc]+[^\n]{0,40}|序章|楔子|尾声|后记)\s*$""",
        RegexOption.MULTILINE,
    )

    data class TxtChapter(val title: String, val text: String)

    /** 按“第X章 / Chapter X”等标题分章；找不到标题就按约 6000 字切段。 */
    fun splitTxt(raw: String, fallbackTitle: String = "第"): List<TxtChapter> {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').trimStart('﻿')
        val heads = chapterLine.findAll(text).toList()
        if (heads.size >= 2) {
            val out = ArrayList<TxtChapter>()
            val pre = text.substring(0, heads.first().range.first).trim()
            if (pre.length > 50) out += TxtChapter("开篇", pre)
            heads.forEachIndexed { i, m ->
                val end = if (i + 1 < heads.size) heads[i + 1].range.first else text.length
                out += TxtChapter(m.value.trim(), text.substring(m.range.last + 1, end).trim())
            }
            return out
        }
        val out = ArrayList<TxtChapter>()
        var pos = 0
        var n = 1
        while (pos < text.length) {
            var end = minOf(text.length, pos + 6000)
            if (end < text.length) { val nl = text.indexOf('\n', end); if (nl in end..(end + 2000)) end = nl }
            out += TxtChapter("$fallbackTitle $n 段", text.substring(pos, end).trim())
            pos = end; n++
        }
        return out.ifEmpty { listOf(TxtChapter("全文", text)) }
    }

    /** TXT 编码：严格按 UTF-8 解码失败就当 GBK（国内常见 TXT 多为 GBK）。 */
    fun decodeTxt(bytes: ByteArray): String {
        val dec = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return try { dec.decode(java.nio.ByteBuffer.wrap(bytes)).toString() } catch (_: Exception) { String(bytes, charset("GBK")) }
    }

    fun txtChapterHtml(c: TxtChapter): String {
        val paras = c.text.split(Regex("""\n\s*\n|\n""")).map { it.trim() }.filter { it.isNotEmpty() }
        return buildString {
            append("<h2>").append(htmlEscape(c.title)).append("</h2>")
            paras.forEach { append("<p>").append(htmlEscape(it)).append("</p>") }
        }
    }

    // ---------- 阅读时间 ----------
    /** 默认阅读速度（字符/分钟）：英文约 250 词/分 ≈ 1400 字符；中文约 400 字/分。 */
    fun defaultCpm(isCjk: Boolean) = if (isCjk) 400.0 else 1400.0

    fun isCjk(sample: String): Boolean {
        val letters = sample.count { it.isLetter() }
        if (letters == 0) return false
        val cjk = sample.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        return cjk * 3 > letters
    }

    /** 本章剩余分钟数（向上取整，至少 1）。 */
    fun minutesLeft(chapterChars: Int, fraction: Double, cpm: Double): Int {
        val left = chapterChars * (1.0 - fraction.coerceIn(0.0, 1.0))
        return maxOf(1, kotlin.math.ceil(left / cpm.coerceAtLeast(50.0)).toInt())
    }

    /** 读速学习：只采信 5 秒到 5 分钟之间的一页（太快是快速翻页，太慢是走开了），按 0.2 权重滑动平均。 */
    fun updateCpm(old: Double, charsOnPage: Int, seconds: Double): Double {
        if (seconds < 5 || seconds > 300 || charsOnPage < 100) return old
        val sample = charsOnPage / (seconds / 60.0)
        if (sample < 50 || sample > 5000) return old
        return old * 0.8 + sample * 0.2
    }

    /** 全书进度：按各章字符数加权。 */
    fun bookFraction(chapterChars: List<Int>, index: Int, fraction: Double): Double {
        val total = chapterChars.sum().toDouble()
        if (total <= 0 || chapterChars.isEmpty()) return 0.0
        val i = index.coerceIn(0, chapterChars.size - 1)
        val before = chapterChars.take(i).sum()
        return ((before + chapterChars[i] * fraction.coerceIn(0.0, 1.0)) / total).coerceIn(0.0, 1.0)
    }

    // ---------- 杂项 ----------
    fun htmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun htmlUnescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'").replace("&rsquo;", "’").replace("&nbsp;", " ")

    /** 去掉标签得到纯文本（用于字数统计、全文搜索）。 */
    fun stripTags(html: String): String =
        htmlUnescape(
            html.replace(Regex("""<(script|style|head)[\s\S]*?</\1>""", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("""<[^>]+>"""), " ")
        ).replace(Regex("""&#(\d+);""")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
            .replace(Regex("""\s+"""), " ").trim()
}

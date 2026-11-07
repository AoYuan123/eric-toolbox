package com.eric.toolbox.modules.read

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 书架：书存在 App 私有目录 files/read/books/<id>/，书架信息与阅读进度存在 files/read/shelf.json。
 * （书文件较大，不放进 SharedPreferences，所以不进「备份导出」；排版设置在模块设置里，会进备份。）
 */
object Library {
    data class Bookmark(val chapter: Int, val fraction: Double, val snippet: String, val at: Long)

    data class Book(
        val id: String,
        val title: String,
        val authors: List<String>,
        val format: String, // epub / txt
        val source: String,
        val coverUrl: String?,
        val addedAt: Long,
        val lastReadAt: Long = 0,
        val chapter: Int = 0,
        val fraction: Double = 0.0,
        val progress: Double = 0.0,
        val bookmarks: List<Bookmark> = emptyList(),
        val gutenbergId: Int? = null,
        val finished: Boolean = false,
    ) {
        fun toJson() = JSONObject().apply {
            put("id", id); put("title", title); put("authors", JSONArray(authors)); put("format", format); put("source", source)
            put("cover", coverUrl ?: ""); put("added", addedAt); put("last", lastReadAt); put("ch", chapter); put("fr", fraction)
            put("pg", progress); put("gid", gutenbergId ?: 0); put("fin", finished)
            put("bm", JSONArray().apply { bookmarks.forEach { b -> put(JSONObject().put("c", b.chapter).put("f", b.fraction).put("s", b.snippet).put("t", b.at)) } })
        }

        companion object {
            fun fromJson(o: JSONObject) = Book(
                id = o.getString("id"), title = o.optString("title"),
                authors = o.optJSONArray("authors")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                format = o.optString("format", "epub"), source = o.optString("source"), coverUrl = o.optString("cover").ifEmpty { null },
                addedAt = o.optLong("added"), lastReadAt = o.optLong("last"), chapter = o.optInt("ch"), fraction = o.optDouble("fr", 0.0),
                progress = o.optDouble("pg", 0.0), gutenbergId = o.optInt("gid", 0).takeIf { it > 0 }, finished = o.optBoolean("fin"),
                bookmarks = o.optJSONArray("bm")?.let { a ->
                    (0 until a.length()).map { val b = a.getJSONObject(it); Bookmark(b.optInt("c"), b.optDouble("f", 0.0), b.optString("s"), b.optLong("t")) }
                } ?: emptyList(),
            )
        }
    }

    private fun root(ctx: Context) = File(ctx.filesDir, "read").apply { mkdirs() }
    private fun shelfFile(ctx: Context) = File(root(ctx), "shelf.json")
    fun bookDir(ctx: Context, id: String) = File(File(root(ctx), "books"), id).apply { mkdirs() }
    fun bookFile(ctx: Context, b: Book) = File(bookDir(ctx, b.id), "book.${b.format}")

    @Synchronized
    fun all(ctx: Context): List<Book> {
        val f = shelfFile(ctx)
        if (!f.isFile) return emptyList()
        return runCatching {
            val a = JSONArray(f.readText())
            (0 until a.length()).map { Book.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun save(ctx: Context, list: List<Book>) {
        val tmp = File(root(ctx), "shelf.json.tmp")
        tmp.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
        tmp.renameTo(shelfFile(ctx))
    }

    fun get(ctx: Context, id: String) = all(ctx).firstOrNull { it.id == id }

    @Synchronized
    fun upsert(ctx: Context, b: Book) {
        val list = all(ctx).filter { it.id != b.id } + b
        save(ctx, list)
    }

    @Synchronized
    fun update(ctx: Context, id: String, f: (Book) -> Book) {
        val list = all(ctx)
        save(ctx, list.map { if (it.id == id) f(it) else it })
    }

    @Synchronized
    fun remove(ctx: Context, id: String) {
        save(ctx, all(ctx).filter { it.id != id })
        File(File(root(ctx), "books"), id).deleteRecursively()
        Notes.removeBook(ctx, id)
    }

    /** 最近读的排前面，没读过的按加入时间。 */
    fun recent(ctx: Context) = all(ctx).sortedByDescending { maxOf(it.lastReadAt, it.addedAt) }

    private fun newId() = java.lang.Long.toString(System.currentTimeMillis(), 36) + (100..999).random()

    // ---------- 下载 ----------
    private const val MAX_BYTES = 150L * 1024 * 1024

    /** 下载 EPUB（自己跟随跳转，只允许 https），校验是 zip 且能解析成 EPUB 后加入书架。 */
    suspend fun download(
        ctx: Context, url: String, title: String, authors: List<String>, source: String, coverUrl: String?, gutenbergId: Int?,
        onProgress: (done: Long, total: Long) -> Unit,
    ): Book = withContext(Dispatchers.IO) {
        val b = Book(newId(), title, authors, "epub", source, coverUrl, System.currentTimeMillis(), gutenbergId = gutenbergId)
        val dir = bookDir(ctx, b.id)
        val part = File(dir, "book.part")
        try {
            var current = url
            var conn: HttpURLConnection? = null
            for (hop in 0..6) {
                require(current.startsWith("https://")) { "下载地址不是 https" }
                val c = URL(current).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 20_000
                c.readTimeout = 45_000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) EricToolbox")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw java.io.IOException("跳转地址缺失")
                    current = URL(URL(current), loc).toString(); c.disconnect()
                    if (hop == 6) throw java.io.IOException("跳转次数过多")
                    continue
                }
                if (code !in 200..299) { c.disconnect(); throw java.io.IOException("下载失败：HTTP $code") }
                conn = c; break
            }
            val c = conn ?: throw java.io.IOException("下载失败")
            try {
                val total = c.contentLengthLong
                var done = 0L
                var last = 0L
                c.inputStream.use { input ->
                    part.outputStream().use { os ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n); done += n
                            if (done > MAX_BYTES) throw java.io.IOException("文件过大")
                            val now = System.currentTimeMillis()
                            if (now - last > 200) { last = now; withContext(Dispatchers.Main) { onProgress(done, total) } }
                        }
                    }
                }
                if (total > 0 && done != total) throw java.io.IOException("下载不完整，请重试")
            } finally { c.disconnect() }
            val head = ByteArray(2).also { h -> part.inputStream().use { it.read(h) } }
            if (head[0] != 'P'.code.toByte() || head[1] != 'K'.code.toByte()) throw java.io.IOException("下载到的不是电子书文件（可能是网页），请换一个版本试试")
            val target = File(dir, "book.epub")
            if (!part.renameTo(target)) throw java.io.IOException("保存失败")
            Epub.open(target).use { e -> if (e.spine.isEmpty()) throw java.io.IOException("电子书没有章节") }
            upsert(ctx, b)
            b
        } catch (e: Exception) {
            dir.deleteRecursively(); throw e
        }
    }

    /** 导入手机里的 EPUB / TXT（系统文件选择器给的 Uri），复制进 App 私有目录。 */
    suspend fun import(ctx: Context, uri: Uri): Book = withContext(Dispatchers.IO) {
        var name = "导入的书"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val lower = name.lowercase()
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > MAX_BYTES) throw java.io.IOException("文件过大") }
            out.toByteArray()
        } ?: throw java.io.IOException("读取文件失败")
        val isZip = bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val format = when {
            isZip -> "epub"
            lower.endsWith(".txt") || !lower.contains('.') -> "txt"
            else -> throw java.io.IOException("只支持 EPUB 和 TXT 文件")
        }
        val id = newId()
        val dir = bookDir(ctx, id)
        try {
            val f = File(dir, "book.$format")
            if (format == "txt") f.writeText(ReadLogic.decodeTxt(bytes)) else f.writeBytes(bytes)
            var title = name.substringBeforeLast('.')
            var authors = emptyList<String>()
            if (format == "epub") Epub.open(f).use { e -> if (e.title.isNotBlank() && e.title != "未命名") title = e.title; authors = e.authors }
            val b = Book(id, title, authors, format, "本机导入", null, System.currentTimeMillis())
            upsert(ctx, b)
            b
        } catch (e: Exception) {
            dir.deleteRecursively(); throw e
        }
    }
}

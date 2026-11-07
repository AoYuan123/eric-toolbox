package com.eric.toolbox.modules.read

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 生词本与划线笔记：存在 files/read/vocab.json、files/read/notes.json（和书一样不进备份）。 */
object Notes {
    data class Word(
        val word: String, val phonetic: String, val meaning: String, val sentence: String,
        val bookId: String, val bookTitle: String, val chapter: Int, val fraction: Double,
        val addedAt: Long, val box: Int = 0, val due: Long = 0,
    )

    data class Highlight(
        val id: String, val bookId: String, val bookTitle: String, val chapter: Int, val chapterTitle: String,
        /** 划线的原文，以及它是本章第几次出现（同一句话可能出现多次） */
        val text: String, val occurrence: Int, val fraction: Double,
        val note: String = "", val translation: String? = null, val at: Long,
    )

    private fun dir(ctx: Context) = File(ctx.filesDir, "read").apply { mkdirs() }

    private fun readArr(f: File): JSONArray = if (f.isFile) runCatching { JSONArray(f.readText()) }.getOrDefault(JSONArray()) else JSONArray()
    private fun writeArr(f: File, a: JSONArray) { val t = File(f.parentFile, f.name + ".tmp"); t.writeText(a.toString()); t.renameTo(f) }

    // ---------- 生词 ----------
    private fun vf(ctx: Context) = File(dir(ctx), "vocab.json")

    @Synchronized
    fun words(ctx: Context): List<Word> {
        val a = readArr(vf(ctx))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Word(o.optString("w"), o.optString("p"), o.optString("m"), o.optString("s"), o.optString("b"), o.optString("bt"),
                o.optInt("c"), o.optDouble("f", 0.0), o.optLong("t"), o.optInt("box"), o.optLong("due"))
        }
    }

    @Synchronized
    private fun saveWords(ctx: Context, list: List<Word>) {
        writeArr(vf(ctx), JSONArray().apply {
            list.forEach { w ->
                put(JSONObject().put("w", w.word).put("p", w.phonetic).put("m", w.meaning).put("s", w.sentence).put("b", w.bookId)
                    .put("bt", w.bookTitle).put("c", w.chapter).put("f", w.fraction).put("t", w.addedAt).put("box", w.box).put("due", w.due))
            }
        })
    }

    fun hasWord(ctx: Context, w: String) = words(ctx).any { it.word.equals(w, true) }

    @Synchronized
    fun addWord(ctx: Context, w: Word) { saveWords(ctx, words(ctx).filter { !it.word.equals(w.word, true) } + w) }

    @Synchronized
    fun removeWord(ctx: Context, w: String) { saveWords(ctx, words(ctx).filter { !it.word.equals(w, true) }) }

    @Synchronized
    fun reviewWord(ctx: Context, w: String, result: Int) {
        val now = System.currentTimeMillis()
        saveWords(ctx, words(ctx).map { if (it.word.equals(w, true)) ReadLogic.review(it.box, result, now).let { (b, d) -> it.copy(box = b, due = d) } else it })
    }

    fun dueWords(ctx: Context, now: Long = System.currentTimeMillis()) = words(ctx).filter { it.due <= now }.sortedBy { it.due }

    // ---------- 划线笔记 ----------
    private fun nf(ctx: Context) = File(dir(ctx), "notes.json")

    @Synchronized
    fun highlights(ctx: Context): List<Highlight> {
        val a = readArr(nf(ctx))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Highlight(o.optString("id"), o.optString("b"), o.optString("bt"), o.optInt("c"), o.optString("ct"), o.optString("x"),
                o.optInt("k"), o.optDouble("f", 0.0), o.optString("n"), o.optString("tr").ifEmpty { null }, o.optLong("t"))
        }
    }

    @Synchronized
    private fun saveHl(ctx: Context, list: List<Highlight>) {
        writeArr(nf(ctx), JSONArray().apply {
            list.forEach { h ->
                put(JSONObject().put("id", h.id).put("b", h.bookId).put("bt", h.bookTitle).put("c", h.chapter).put("ct", h.chapterTitle)
                    .put("x", h.text).put("k", h.occurrence).put("f", h.fraction).put("n", h.note).put("tr", h.translation ?: "").put("t", h.at))
            }
        })
    }

    fun bookHighlights(ctx: Context, bookId: String) = highlights(ctx).filter { it.bookId == bookId }.sortedWith(compareBy({ it.chapter }, { it.fraction }))

    @Synchronized
    fun addHighlight(ctx: Context, h: Highlight) {
        val list = highlights(ctx)
        if (list.any { it.bookId == h.bookId && it.chapter == h.chapter && it.text == h.text && it.occurrence == h.occurrence }) return
        saveHl(ctx, list + h)
    }

    @Synchronized
    fun updateHighlight(ctx: Context, id: String, f: (Highlight) -> Highlight) = saveHl(ctx, highlights(ctx).map { if (it.id == id) f(it) else it })

    @Synchronized
    fun removeHighlight(ctx: Context, id: String) = saveHl(ctx, highlights(ctx).filter { it.id != id })

    /** 删除书时一并删除它的划线（生词保留：词还有用，只是回不到原文）。 */
    @Synchronized
    fun removeBook(ctx: Context, bookId: String) = saveHl(ctx, highlights(ctx).filter { it.bookId != bookId })

    fun exportBook(ctx: Context, bookId: String, title: String): String =
        ReadLogic.exportNotes(title, bookHighlights(ctx, bookId).map { ReadLogic.NoteLine(it.chapterTitle, it.text, it.note, it.translation) })
}

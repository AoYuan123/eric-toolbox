package com.eric.toolbox.modules.english

import android.content.Context
import com.eric.toolbox.modules.english.law.LawData
import com.eric.toolbox.core.Store
import com.eric.toolbox.modules.read.Dict
import com.eric.toolbox.modules.read.Notes
import com.eric.toolbox.modules.read.ReadLogic
import java.io.File

/** 学英语的存储：卡片进度 files/english/cards.json（进度随备份走 prefs 里的计数；卡片文件不大）。 */
object EnglishStore {
    private const val LAW = com.eric.toolbox.modules.english.law.LAW_BOOK
    fun prefs(ctx: Context) = Store.module(ctx, "english")
    private fun file(ctx: Context) = File(File(ctx.filesDir, "english").apply { mkdirs() }, "cards.json")

    /** 考试词书（来自离线词典标签）。 */
    val EXAMS = linkedMapOf("cet4" to ("四级" to "CET-4"), "cet6" to ("六级" to "CET-6"), "ky" to ("考研" to "Postgrad"),
        "ielts" to ("雅思" to "IELTS"), "toefl" to ("托福" to "TOEFL"), "gre" to ("GRE" to "GRE"))

    fun books(ctx: Context): List<String> = prefs(ctx).getString("books", "ip,daily,mine")!!.split(',').filter { it.isNotBlank() }
    fun setBooks(ctx: Context, list: List<String>) { prefs(ctx).edit().putString("books", list.joinToString(",")).apply() }
    fun newLimit(ctx: Context) = prefs(ctx).getInt("new_limit", 10)
    fun setNewLimit(ctx: Context, n: Int) { prefs(ctx).edit().putInt("new_limit", n).apply() }

    @Volatile private var cache: MutableMap<String, EnglishLogic.Card>? = null

    @Synchronized
    fun cards(ctx: Context): MutableMap<String, EnglishLogic.Card> {
        cache?.let { return it }
        val f = file(ctx)
        val m = if (f.isFile) EnglishLogic.listFromJson(f.readText()).associateBy { it.id }.toMutableMap() else mutableMapOf()
        cache = m
        return m
    }

    @Synchronized
    fun put(ctx: Context, c: EnglishLogic.Card) {
        val m = cards(ctx); val wasNew = m[c.id]?.isNew != false
        m[c.id] = c
        file(ctx).writeText(EnglishLogic.listToJson(m.values))
        val today = EnglishLogic.dayNo(System.currentTimeMillis())
        val p = prefs(ctx)
        val ed = p.edit()
        if (p.getInt("day", 0) != today) ed.putInt("day", today).putInt("new_today", 0).putInt("done_today", 0)
        val base = if (p.getInt("day", 0) != today) 0 else null
        ed.putInt("done_today", (base ?: p.getInt("done_today", 0)) + 1)
        if (wasNew && c.reps == 1) ed.putInt("new_today", (base ?: p.getInt("new_today", 0)) + 1)
        val days = p.getStringSet("days", emptySet())!!.toMutableSet().apply { add(today.toString()) }
        ed.putStringSet("days", days.toList().sortedDescending().take(400).toSet())
        ed.apply()
    }

    fun newToday(ctx: Context): Int = prefs(ctx).let { if (it.getInt("day", 0) == EnglishLogic.dayNo(System.currentTimeMillis())) it.getInt("new_today", 0) else 0 }
    fun doneToday(ctx: Context): Int = prefs(ctx).let { if (it.getInt("day", 0) == EnglishLogic.dayNo(System.currentTimeMillis())) it.getInt("done_today", 0) else 0 }
    fun streak(ctx: Context): Int = EnglishLogic.streak(prefs(ctx).getStringSet("days", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet(), System.currentTimeMillis())
    fun weekDays(ctx: Context): List<Boolean> {
        val set = prefs(ctx).getStringSet("days", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()
        val now = System.currentTimeMillis()
        return (6 downTo 0).map { EnglishLogic.dayNo(now - it * 86_400_000L) in set }
    }

    // ---------------- 词条 ----------------
    data class Item(val book: String, val key: String, val en: String, val zh: String, val ex: String, val exZh: String, val note: String, val phonetic: String, val phrase: Boolean)

    fun item(ctx: Context, book: String, key: String): Item? = when (book) {
        EnglishBooks.IP, EnglishBooks.DAILY -> EnglishBooks.book(book).firstOrNull { it.key == key }?.let { t ->
            val ph = if (!t.en.contains(' ')) runCatching { Dict.lookup(ctx, t.en)?.phonetic }.getOrNull().orEmpty() else ""
            Item(book, key, t.en, t.zh, t.ex, t.exZh, t.note, ph, book == EnglishBooks.DAILY)
        }
        LAW -> LawData.get(ctx).terms.firstOrNull { it.key == key }?.let { t ->
            Item(book, key, t.en, t.zh, t.exEn, t.exZh, t.noteZh + "（出处：" + t.refLabel + "）", "", t.en.contains(' '))
        }
        EnglishBooks.MINE -> Notes.words(ctx).firstOrNull { it.word.equals(key, true) }?.let { w ->
            Item(book, key, w.word, ReadLogic.shortMeaning(w.meaning, 3), w.sentence, "", if (w.bookTitle.isNotBlank()) "来自《${w.bookTitle}》" else "", w.phonetic, false)
        }
        else -> runCatching { Dict.lookup(ctx, key) }.getOrNull()?.let { e -> Item(book, key, e.word, ReadLogic.shortMeaning(e.meaning, 3), "", "", Dict.tagText(e.tags), e.phonetic, false) }
    }

    /** 新词池：按选中的词书轮流取（工作词、口语、生词交替出现）。 */
    fun newPool(ctx: Context, limit: Int = 60): List<EnglishLogic.Card> {
        val cs = cards(ctx)
        val lists = books(ctx).map { b ->
            val keys: List<String> = when (b) {
                EnglishBooks.IP, EnglishBooks.DAILY -> EnglishBooks.book(b).map { it.key }
                EnglishBooks.MINE -> Notes.words(ctx).sortedBy { it.addedAt }.map { it.word.lowercase() }
                LAW -> LawData.get(ctx).terms.map { it.key }
                else -> runCatching { Dict.wordsByTag(ctx, b).map { it.word.lowercase() } }.getOrDefault(emptyList())
            }
            keys.asSequence().map { k -> "$b:$k" }.filter { cs[it]?.isNew != false }.take(limit).map { id -> EnglishLogic.Card(id, b, id.substringAfter(':')) }.toList()
        }
        val out = ArrayList<EnglishLogic.Card>()
        var i = 0
        while (out.size < limit && lists.any { i < it.size }) { lists.forEach { l -> if (i < l.size) out += l[i] }; i++ }
        return out.take(limit)
    }

    fun queue(ctx: Context): List<EnglishLogic.Card> =
        EnglishLogic.queue(cards(ctx), newPool(ctx, newLimit(ctx) + 5), newToday(ctx), newLimit(ctx), System.currentTimeMillis())

    fun dueCount(ctx: Context): Int { val now = System.currentTimeMillis(); return cards(ctx).values.count { !it.isNew && it.due <= now } }

    fun learnedCount(ctx: Context, book: String) = cards(ctx).values.count { it.book == book && !it.isNew }
    fun bookSize(ctx: Context, book: String): Int = when (book) {
        EnglishBooks.IP, EnglishBooks.DAILY -> EnglishBooks.book(book).size
        EnglishBooks.MINE -> Notes.words(ctx).size
        LAW -> LawData.get(ctx).terms.size
        else -> runCatching { Dict.wordsByTag(ctx, book).size }.getOrDefault(0)
    }

    /** 查词结果加入背词：放进「我的生词本」（与读书模块共用）。 */
    fun addToMine(ctx: Context, word: String, phonetic: String, meaning: String, sentence: String = "") {
        if (Notes.hasWord(ctx, word)) return
        Notes.addWord(ctx, Notes.Word(word, phonetic, meaning, sentence, "", "查词", 0, 0.0, System.currentTimeMillis()))
    }
}

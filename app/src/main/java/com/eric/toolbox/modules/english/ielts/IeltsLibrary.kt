package com.eric.toolbox.modules.english.ielts

import android.content.Context
import com.eric.toolbox.modules.english.EnglishStore
import com.eric.toolbox.modules.read.Dict
import java.util.Locale

/** 一个可背的词条。zh 是展示用释义（换行已压平），phonetic 可能为空。 */
data class Voc(val key: String, val en: String, val zh: String, val phonetic: String)

/**
 * 雅思词库：不另存词表，直接吃离线词典 ECDICT 的考试标签。
 *
 * 为什么这么做：
 * - 词典里 ielts 标签就有 5038 词、toefl 6970 词，全是真词条（释义 + 音标），
 *   比自己编一份"大全"可靠得多，也不会让 APK 再胖几 MB。
 * - 按字母序每 50 词切一个单元（101 个单元），这是墨墨/扇贝那套"按单元推进"的做法：
 *   有终点、有进度、可以一次只背一个单元，不会面对 5000 词无从下手。
 * - 卡片的 id 仍然是 `ielts:abandon` 这种形式，和英语模块里原有的考试词书共用进度，
 *   在词书页选中的「雅思」和在这里背的是同一批卡。
 */
object IeltsLibrary {
    data class Lib(val id: String, val zh: String, val en: String, val tag: String, val hint: String)

    /** 每个单元多少个词。50 是常见背词 App 的单元大小，一个单元约 10~15 分钟。 */
    const val UNIT = 50

    val LIBS: List<Lib> = listOf(
        Lib("ielts", "雅思全词库", "IELTS", "ielts", "阅读、听力、写作的底子都在这 5038 词里"),
        Lib("toefl", "托福全词库", "TOEFL", "toefl", "6970 词，学术词更多，考雅思也用得上"),
        Lib("cet6", "六级", "CET-6", "cet6", "5406 词，先补基础再上雅思更稳"),
        Lib("ky", "考研", "Postgrad", "ky", "4801 词"),
        Lib("cet4", "四级", "CET-4", "cet4", "3846 词，最基础的一层"),
        Lib("gre", "GRE", "GRE", "gre", "7504 词，冲 7 分以上再看"),
    )

    /** 场景词单独一本：词来自手写场景内容，不是词典，所以没有 tag。 */
    val SCENE_LIB = Lib(IeltsData.BOOK, "场景词", "Scenarios", "", "按 30 个场景整理，带搭配和例句")

    fun libs(): List<Lib> = LIBS + SCENE_LIB

    fun lib(id: String): Lib? = libs().firstOrNull { it.id == id }

    /** 词库里有多少词。场景词按「全部手写场景词」算，不受场景页勾选影响。 */
    fun count(ctx: Context, lib: Lib): Int =
        if (lib.id == IeltsData.BOOK) IeltsData.words.size
        else runCatching { Dict.countByTag(ctx, lib.tag) }.getOrDefault(0)

    fun units(total: Int): Int = if (total <= 0) 0 else (total + UNIT - 1) / UNIT

    /** 第 unit 个单元（0 开始）的词。 */
    fun words(ctx: Context, lib: Lib, unit: Int): List<Voc> {
        if (unit < 0) return emptyList()
        if (lib.id == IeltsData.BOOK) {
            return IeltsData.words.drop(unit * UNIT).take(UNIT).map { w ->
                Voc(w.en.lowercase(Locale.ROOT), w.en, w.zh, "")
            }
        }
        return runCatching {
            Dict.sliceByTag(ctx, lib.tag, unit * UNIT, UNIT).map { e ->
                Voc(e.word.lowercase(Locale.ROOT), e.word, clean(e.meaning), e.phonetic)
            }
        }.getOrDefault(emptyList())
    }

    /** 整本词库一次读完（单元网格要用；逐单元查会触发上百次全表扫描，进页面会卡）。 */
    fun allWords(ctx: Context, lib: Lib): List<Voc> {
        if (lib.id == IeltsData.BOOK) {
            return IeltsData.words.map { w -> Voc(w.en.lowercase(Locale.ROOT), w.en, w.zh, "") }
        }
        return runCatching {
            Dict.wordsByTag(ctx, lib.tag).map { e ->
                Voc(e.word.lowercase(Locale.ROOT), e.word, clean(e.meaning), e.phonetic)
            }
        }.getOrDefault(emptyList())
    }

    /** 每个单元已学多少个词（卡片有复习记录才算），下标即单元序号。 */
    fun learnedByUnit(ctx: Context, lib: Lib): List<Int> {
        val cards = EnglishStore.cards(ctx)
        return allWords(ctx, lib).chunked(UNIT).map { chunk ->
            chunk.count { v -> (cards["${lib.id}:${v.key}"]?.reps ?: 0) > 0 }
        }
    }

    /** 单元里已学多少个（卡片有复习记录才算）。 */
    fun learnedIn(ctx: Context, lib: Lib, words: List<Voc>): Int {
        val cards = EnglishStore.cards(ctx)
        return words.count { v -> (cards["${lib.id}:${v.key}"]?.reps ?: 0) > 0 }
    }

    /** 词典释义里带 \r\n 和换行，一张卡片放不下，压成一行。 */
    fun clean(meaning: String): String = meaning
        .replace("\r\n", "；").replace("\n", "；").replace("\r", "；")
        .replace(Regex("；{2,}"), "；").trim().trimEnd('；')

    /** 选择题选项用的短释义：截到第一个分句，避免一屏只放得下两个选项。 */
    fun brief(meaning: String, max: Int = 26): String {
        val a = clean(meaning).substringBefore('；').substringBefore('，').trim()
        val s = if (a.isBlank()) clean(meaning) else a
        return if (s.length > max) s.take(max) + "…" else s
    }

    /** 记忆持久度：还没学 / 学习中 / 已牢固（间隔拉到 21 天以上算牢固，和墨墨的"熟知"一个意思）。 */
    data class Progress(val total: Int, val learning: Int, val solid: Int) {
        val done get() = learning + solid
        val fresh get() = (total - done).coerceAtLeast(0)
    }

    fun progress(ctx: Context, lib: Lib, total: Int): Progress {
        val cs = EnglishStore.cards(ctx).values.filter { it.book == lib.id }
        val solid = cs.count { it.ivlDays >= 21.0 }
        val learning = cs.count { it.reps > 0 && it.ivlDays < 21.0 }
        return Progress(total, learning, solid)
    }

    /** 错词本：忘过的词（lapses > 0），忘得多的排前面。不需要另建表，直接从卡片推。 */
    fun wrongKeys(ctx: Context, lib: Lib, max: Int = 120): List<String> =
        EnglishStore.cards(ctx).values
            .filter { it.book == lib.id && it.lapses > 0 }
            .sortedWith(compareByDescending<com.eric.toolbox.modules.english.EnglishLogic.Card> { it.lapses }.thenByDescending { it.last })
            .take(max)
            .map { it.key }

    /** 把错词还原成可背的词条（单词和专有词用词典查，场景词用手写内容）。 */
    fun look(ctx: Context, lib: Lib, key: String): Voc? {
        if (lib.id == IeltsData.BOOK) return IeltsData.word(key)?.let { Voc(it.en.lowercase(Locale.ROOT), it.en, it.zh, "") }
        val e = runCatching { Dict.lookup(ctx, key) }.getOrNull() ?: return null
        return Voc(key, e.word, clean(e.meaning), e.phonetic)
    }
}

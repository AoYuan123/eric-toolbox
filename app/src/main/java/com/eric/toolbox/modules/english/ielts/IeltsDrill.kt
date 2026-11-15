package com.eric.toolbox.modules.english.ielts

import com.eric.toolbox.modules.english.EnglishLogic
import java.util.Collections
import java.util.Random

/**
 * 单元的学后测验（纯逻辑，可单测）。
 *
 * 为什么要有这一步：只「看一眼 + 点认识」的词，第二天大概率忘。成熟背词 App
 * （墨墨/不背单词/百词斩）都是「学一批 → 立刻用不同题型考一遍 → 错的回炉」，
 * 三种题型分别对应三种真实能力：
 * - 看词选义：阅读里认得（认知）
 * - 听音选义：听力里听得出（雅思听力最吃这个）
 * - 听写拼写：写作里写得对（雅思写作拼错直接扣分）
 */
object IeltsDrill {
    enum class Kind(val zh: String, val en: String, val prompt: String) {
        MEANING("看词选义", "Pick the meaning", "这个单词是什么意思？"),
        LISTEN("听音选义", "Listen and pick", "听发音，选出对应的意思"),
        SPELL("听写拼写", "Spell it", "根据释义拼出这个单词"),
    }

    data class Q(
        val kind: Kind,
        val v: Voc,
        val options: List<String>,
        val answer: Int,
    ) {
        val isChoice: Boolean get() = options.isNotEmpty()
    }

    const val SIZE = 8

    /** 从一批词里生成测验。选项干扰项取自同一批词的释义，所以难度和词库一致。 */
    fun quiz(words: List<Voc>, size: Int = SIZE, seed: Int = 0): List<Q> {
        val pool = words.map { IeltsLibrary.brief(it.zh) }.filter { it.isNotBlank() }.distinct()
        if (pool.size < 4) return emptyList()

        val pick = if (words.size <= size) words else {
            val copy = words.toMutableList()
            Collections.shuffle(copy, Random(seed.toLong() * 131 + 17))
            copy.take(size)
        }

        val out = ArrayList<Q>(pick.size)
        pick.forEachIndexed { i, v ->
            val ans = IeltsLibrary.brief(v.zh)
            if (ans.isBlank()) return@forEachIndexed
            // 词组（have a word with 这类）不考拼写：拼写题只对单词有意义
            val kind = when {
                v.en.contains(' ') -> if (i % 2 == 0) Kind.MEANING else Kind.LISTEN
                else -> Kind.values()[i % Kind.values().size]
            }
            if (kind == Kind.SPELL) {
                out += Q(kind, v, emptyList(), -1)
                return@forEachIndexed
            }
            val d = EnglishLogic.distractors(ans, pool, seed + i * 31)
            if (d.size < 3) return@forEachIndexed
            val opts = (d + ans).toMutableList()
            Collections.shuffle(opts, Random(seed.toLong() * 7 + i))
            out += Q(kind, v, opts, opts.indexOf(ans))
        }
        return out
    }

    fun ok(q: Q, pick: Int): Boolean = pick == q.answer

    fun spellOk(q: Q, input: String): Boolean = EnglishLogic.spellOk(input, q.v.en)

    /** 得分。0~100。 */
    fun score(right: Int, total: Int): Int = if (total <= 0) 0 else right * 100 / total
}

package com.eric.toolbox.modules.english.ielts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学后测验的纯逻辑体检。
 *
 * 这里守的是「正确答案的下标」：正确答案下标算错，界面上一切都是正常的——
 * 选中也会变色、也会判对错，只是判反了，肉眼完全看不出来，只能靠测试。
 */
class IeltsDrillTest {

    private fun words(n: Int): List<Voc> = (1..n).map { i ->
        Voc("w$i", "word$i", "n. 释义$i, 另一层意思$i；补充$i", "wɜːd$i")
    }

    @Test
    fun everyChoiceQuestionHasFourDistinctOptionsAndCorrectIndex() {
        val qs = IeltsDrill.quiz(words(50), size = 8, seed = 3)
        assertTrue("词够多的时候应该有题", qs.isNotEmpty())
        qs.forEach { q ->
            if (q.isChoice) {
                assertEquals("选项必须四个", 4, q.options.size)
                assertEquals("选项不能重复", 4, q.options.distinct().size)
                assertTrue("正确答案下标越界：${q.answer}", q.answer in q.options.indices)
                // 正确答案必须真的是这个词的释义
                assertEquals(IeltsLibrary.brief(q.v.zh), q.options[q.answer])
            }
        }
    }

    @Test
    fun spellingQuestionsHaveNoOptions() {
        val qs = IeltsDrill.quiz(words(50), size = 12, seed = 5)
        val spell = qs.filter { it.kind == IeltsDrill.Kind.SPELL }
        assertTrue("至少出一道拼写题", spell.isNotEmpty())
        spell.forEach { q ->
            assertEquals("拼写题不该有选项", 0, q.options.size)
            assertEquals(-1, q.answer)
            assertTrue(IeltsDrill.spellOk(q, q.v.en.uppercase()))
            assertTrue(!IeltsDrill.spellOk(q, "definitely-not-it"))
        }
    }

    @Test
    fun multiWordEntriesNeverBecomeSpellingQuestions() {
        val mixed = listOf(
            Voc("have a word with", "have a word with", "和……谈谈", ""),
            Voc("on the side", "on the side", "另放", ""),
        ) + words(10)
        IeltsDrill.quiz(mixed, size = 12, seed = 1).forEach { q ->
            if (q.kind == IeltsDrill.Kind.SPELL) {
                assertTrue("词组不该考拼写：${q.v.en}", !q.v.en.contains(' '))
            }
        }
    }

    @Test
    fun tooFewWordsGivesEmptyQuizInsteadOfCrashing() {
        assertTrue(IeltsDrill.quiz(words(2)).isEmpty())
        assertTrue(IeltsDrill.quiz(emptyList()).isEmpty())
    }

    @Test
    fun quizIsDeterministicForSameSeed() {
        val a = IeltsDrill.quiz(words(30), size = 8, seed = 9).map { it.v.key to it.answer }
        val b = IeltsDrill.quiz(words(30), size = 8, seed = 9).map { it.v.key to it.answer }
        assertEquals(a, b)
    }

    @Test
    fun scoreRoundsDownAndHandlesZero() {
        assertEquals(0, IeltsDrill.score(0, 0))
        assertEquals(75, IeltsDrill.score(6, 8))
        assertEquals(100, IeltsDrill.score(8, 8))
    }

    @Test
    fun briefStripsExtraClausesSoOptionsStayShort() {
        val long = IeltsLibrary.brief("n. 电池, 殴打；[化] 蓄电池；补充说明这里还有很长很长的内容")
        assertTrue("brief 应该截断：$long", long.length <= 27)
        assertTrue("brief 不该切断出空串", long.isNotBlank())
    }

    @Test
    fun cleanFlattensNewlines() {
        assertEquals("n. 一；n. 二；n. 三", IeltsLibrary.clean("n. 一\r\nn. 二\nn. 三"))
        assertEquals("", IeltsLibrary.clean(""))
    }
}

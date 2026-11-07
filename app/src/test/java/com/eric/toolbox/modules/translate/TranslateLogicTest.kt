package com.eric.toolbox.modules.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslateLogicTest {
    @Test fun direction() {
        assertEquals("zh" to "en", TranslateLogic.resolve(TranslateLogic.Dir.AUTO, "请在本周五前把专利检索报告发给客户。"))
        assertEquals("en" to "zh", TranslateLogic.resolve(TranslateLogic.Dir.AUTO, "Please send the report by Friday."))
        assertEquals("zh" to "en", TranslateLogic.resolve(TranslateLogic.Dir.AUTO, "PD-1 抑制剂"))
        assertEquals("en" to "zh", TranslateLogic.resolve(TranslateLogic.Dir.EN_ZH, "专利"))
    }

    @Test fun singleWord() {
        assertEquals("patent", TranslateLogic.singleEnglishWord(" Patent. "))
        assertEquals("don't", TranslateLogic.singleEnglishWord("don’t"))
        assertEquals("well-known", TranslateLogic.singleEnglishWord("well-known"))
        assertNull(TranslateLogic.singleEnglishWord("patent law"))
        assertNull(TranslateLogic.singleEnglishWord("专利"))
        assertNull(TranslateLogic.singleEnglishWord("177Lu"))
    }

    @Test fun history() {
        val a = TranslateLogic.Item("专利", "patent", "zh", "en", 1)
        val b = TranslateLogic.Item("权利要求", "claims", "zh", "en", 2)
        var h = TranslateLogic.addHistory(emptyList(), a)
        h = TranslateLogic.addHistory(h, b)
        h = TranslateLogic.addHistory(h, a.copy(at = 3))
        assertEquals(listOf("专利", "权利要求"), h.map { it.src })
        assertEquals(h, TranslateLogic.historyFromJson(TranslateLogic.historyToJson(h)))
        assertEquals(30, (1..40).fold(emptyList<TranslateLogic.Item>()) { l, i -> TranslateLogic.addHistory(l, a.copy(src = "s$i")) }.size)
        assertEquals(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f), TranslateLogic.speeds)
    }
}

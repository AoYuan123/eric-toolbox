package com.eric.toolbox.modules.trials

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatorTest {
    @Test fun signMatchesBaiduDocExample() {
        // 百度翻译开放平台接入文档的签名示例：appid=2015063000000001, q=apple, salt=1435660288, 密钥=12345678
        assertEquals("f89f9594663708c1605f3d736d01d2d4", Translator.sign("2015063000000001", "apple", "1435660288", "12345678"))
    }

    @Test fun parseResult() {
        val r = Translator.parse("""{"from":"en","to":"zh","trans_result":[{"src":"apple","dst":"苹果"},{"src":"Progression-free survival","dst":"无进展生存期"}]}""")
        assertEquals(listOf("apple" to "苹果", "Progression-free survival" to "无进展生存期"), r)
    }

    @Test fun parseError() {
        val e = runCatching { Translator.parse("""{"error_code":"54001","error_msg":"Invalid Sign"}""") }.exceptionOrNull()
        assertTrue(e!!.message!!.contains("签名错误"))
        assertTrue(e.message!!.contains("54001"))
    }

    @Test fun batching() {
        val lines = List(100) { "x".repeat(100) }
        val b = Translator.batches(lines)
        assertTrue(b.all { it.size <= 40 && it.sumOf { s -> s.length + 1 } <= 4500 })
        assertEquals(100, b.sumOf { it.size })
    }
}

package com.eric.toolbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class MailerTest {
    @Test fun headerEncoding() {
        assertEquals("Hello", Mailer.encodeHeader("Hello"))
        val h = Mailer.encodeHeader("【百宝箱】金价跌破 840 元/克")
        assertTrue(h.startsWith("=?UTF-8?B?") && h.endsWith("?="))
        val inner = h.removePrefix("=?UTF-8?B?").removeSuffix("?=")
        assertEquals("【百宝箱】金价跌破 840 元/克", String(Base64.getDecoder().decode(inner), Charsets.UTF_8))
    }

    @Test fun messageFormat() {
        val m = Mailer.buildMessage("a@qq.com", "b@163.com", "测试", "第一行\n第二行", 0L)
        val (head, body) = m.split("\r\n\r\n", limit = 2)
        assertTrue(head.contains("From: =?UTF-8?B?"))
        assertTrue(head.contains("<a@qq.com>"))
        assertTrue(head.contains("To: <b@163.com>"))
        assertTrue(head.contains("Content-Transfer-Encoding: base64"))
        assertFalse(m.contains("\n.\r\n")) // 正文 base64 不会出现单独一个点的行（SMTP 结束符）
        assertEquals("第一行\r\n第二行", String(Base64.getDecoder().decode(body.replace("\r\n", "")), Charsets.UTF_8))
        assertTrue(body.split("\r\n").all { it.length <= 76 })
    }

    @Test fun recipientsRoundTrip() {
        val list = listOf(MailRecipient("a@qq.com", "我自己", setOf("gold", "trials", "system")), MailRecipient("w@corp.com", "工作", setOf("trials")))
        assertEquals(list, Mailer.parseRecipients(Mailer.recipientsJson(list)))
        assertTrue(Mailer.parseRecipients("not json").isEmpty())
    }

    @Test fun emailCheck() {
        assertTrue(Mailer.isEmail("123456@qq.com"))
        assertFalse(Mailer.isEmail("123456"))
        assertFalse(Mailer.isEmail("a b@qq.com"))
    }
}

package com.eric.toolbox.modules.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiLogicTest {
    @Test
    fun parsesToolCallReply() {
        // OpenAI 兼容格式的工具调用返回（字段按 DeepSeek 文档）
        val body = """{"choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant","content":"",
            "tool_calls":[{"id":"call_1","type":"function","function":{"name":"get_gold_price","arguments":"{}"}}]}}],
            "usage":{"prompt_tokens":1200,"completion_tokens":30,"prompt_cache_hit_tokens":1000,"prompt_cache_miss_tokens":200}}"""
        val r = AiLogic.parseReply(body)
        assertEquals("tool_calls", r.finish)
        assertEquals(1, r.msg.toolCalls.size)
        assertEquals("get_gold_price", r.msg.toolCalls[0].name)
        assertEquals(1000, r.usage.hit); assertEquals(200, r.usage.miss)
        // flash：1000×0.02 + 200×1 + 30×2 = 280 / 1e6 元
        assertEquals(0.00028, AiLogic.cost(r.usage, AiLogic.deepseekPrice("deepseek-v4-flash")), 1e-9)
    }

    @Test
    fun nullContentAndNoUsage() {
        val r = AiLogic.parseReply("""{"choices":[{"message":{"role":"assistant","content":null}}]}""")
        assertEquals(null, r.msg.content)
        assertEquals(0, r.usage.prompt)
    }

    @Test
    fun requestHasThinkingOffForDeepseekOnly() {
        val msgs = listOf(AiLogic.Msg("user", "你好"))
        val d = JSONObject(AiLogic.buildRequest("deepseek", "deepseek-v4-flash", msgs, JSONArray()))
        assertEquals("disabled", d.getJSONObject("thinking").getString("type"))
        assertFalse(d.has("tools"))
        val q = JSONObject(AiLogic.buildRequest("qwen", "qwen-plus", msgs, JSONArray().put(JSONObject().put("type", "function"))))
        assertFalse(q.has("thinking")); assertTrue(q.has("tools"))
    }

    @Test
    fun toolMessagesRoundTrip() {
        val m = AiLogic.Msg("assistant", null, listOf(AiLogic.ToolCall("c1", "set_theme", "{\"theme\":\"ivory\"}")), at = 5)
        val back = AiLogic.msgFromJson(AiLogic.msgToJson(m))
        assertEquals(m.toolCalls, back.toolCalls); assertEquals(null, back.content)
        val t = AiLogic.msgFromJson(AiLogic.msgToJson(AiLogic.Msg("tool", "ok", toolCallId = "c1")))
        assertEquals("c1", t.toolCallId)
    }

    @Test
    fun windowStartsAtUser() {
        val h = listOf(
            AiLogic.Msg("user", "1"), AiLogic.Msg("assistant", null, listOf(AiLogic.ToolCall("a", "x", "{}"))), AiLogic.Msg("tool", "r", toolCallId = "a"),
            AiLogic.Msg("assistant", "ok"), AiLogic.Msg("user", "2"), AiLogic.Msg("assistant", "ok2"),
        )
        val w = AiLogic.window(h, 4)
        assertEquals("user", w.first().role); assertEquals("2", w.first().content)
        assertEquals(h, AiLogic.window(h, 10))
    }

    @Test
    fun errorsExplained() {
        assertTrue(AiLogic.explainError(402, """{"error":{"message":"Insufficient Balance"}}""").contains("余额不足"))
        assertTrue(AiLogic.explainError(401, "").contains("密钥"))
    }

    @Test
    fun balanceAndSearch() {
        // 官方文档示例（/user/balance）
        val b = AiLogic.parseBalance("""{"is_available":true,"balance_infos":[{"currency":"CNY","total_balance":"110.00","granted_balance":"10.00","topped_up_balance":"100.00"}]}""")!!
        assertEquals("110.00", b.total); assertEquals("10.00", b.granted)
        val h = AiLogic.parseSearch("""{"results":[{"title":"A","url":"https://a.com","snippet":"s","summary":""},{"title":"no url"}]}""")
        assertEquals(1, h.size); assertEquals("s", h[0].snippet)
        assertTrue(AiLogic.parseSearch("""{"results":[]}""").isEmpty())
    }
}

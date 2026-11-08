package com.eric.toolbox.modules.ai

import org.json.JSONArray
import org.json.JSONObject

/** AI 管家的纯逻辑（可单测）：服务商、请求拼装、返回解析、费用估算、对话存储格式。 */
object AiLogic {

    /** 模型服务。价格单位：元 / 百万 tokens（DeepSeek 为 11-08 官网价，其余由用户填）。 */
    data class Provider(val key: String, val name: String, val url: String, val models: List<String>, val note: String)

    val PROVIDERS = listOf(
        Provider("deepseek", "DeepSeek", "https://api.deepseek.com/chat/completions", listOf("deepseek-v4-flash", "deepseek-v4-pro"), "推荐：便宜，支持调用百宝箱功能"),
        Provider("qwen", "通义千问（阿里云百炼）", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", listOf("qwen-plus", "qwen-max"), "新开通有免费额度"),
        Provider("custom", "其他（兼容 OpenAI 格式）", "", emptyList(), "自己填地址和模型名"),
    )

    fun provider(key: String) = PROVIDERS.firstOrNull { it.key == key } ?: PROVIDERS[0]

    /** DeepSeek 官网价（元/百万 tokens）：缓存命中输入、未命中输入、输出。 */
    data class Price(val hit: Double, val miss: Double, val out: Double)

    fun deepseekPrice(model: String): Price? = when (model) {
        "deepseek-v4-flash" -> Price(0.02, 1.0, 2.0)
        "deepseek-v4-pro" -> Price(0.025, 3.0, 6.0)
        else -> null
    }

    data class Usage(val prompt: Int, val hit: Int, val miss: Int, val completion: Int)

    fun parseUsage(o: JSONObject?): Usage {
        if (o == null) return Usage(0, 0, 0, 0)
        val prompt = o.optInt("prompt_tokens")
        val hit = o.optInt("prompt_cache_hit_tokens", 0)
        val miss = if (o.has("prompt_cache_miss_tokens")) o.optInt("prompt_cache_miss_tokens") else prompt - hit
        return Usage(prompt, hit, miss, o.optInt("completion_tokens"))
    }

    /** 估算费用（元）。price 为 null 时用用户填的输入/输出单价。 */
    fun cost(u: Usage, price: Price?, userIn: Double = 0.0, userOut: Double = 0.0): Double =
        if (price != null) (u.hit * price.hit + u.miss * price.miss + u.completion * price.out) / 1_000_000.0
        else (u.prompt * userIn + u.completion * userOut) / 1_000_000.0

    // ---------------- 消息 ----------------

    data class ToolCall(val id: String, val name: String, val args: String)

    /** 一条对话消息（OpenAI 格式）。role: system / user / assistant / tool。 */
    data class Msg(
        val role: String,
        val content: String?,
        val toolCalls: List<ToolCall> = emptyList(),
        val toolCallId: String? = null,
        val reasoning: String? = null,
        val at: Long = 0,
    )

    fun msgToApi(m: Msg): JSONObject {
        val o = JSONObject().put("role", m.role)
        if (m.content != null) o.put("content", m.content) else o.put("content", JSONObject.NULL)
        if (m.toolCalls.isNotEmpty()) o.put("tool_calls", JSONArray().apply {
            m.toolCalls.forEach { put(JSONObject().put("id", it.id).put("type", "function").put("function", JSONObject().put("name", it.name).put("arguments", it.args))) }
        })
        if (m.toolCallId != null) o.put("tool_call_id", m.toolCallId)
        if (!m.reasoning.isNullOrEmpty()) o.put("reasoning_content", m.reasoning)
        return o
    }

    fun buildRequest(provider: String, model: String, messages: List<Msg>, tools: JSONArray): String {
        val o = JSONObject().put("model", model).put("messages", JSONArray().apply { messages.forEach { put(msgToApi(it)) } })
        if (tools.length() > 0) o.put("tools", tools)
        // DeepSeek V4 默认开思考模式；管家对话关掉，更快更省（官方参数 thinking.type=disabled）
        if (provider == "deepseek") o.put("thinking", JSONObject().put("type", "disabled"))
        return o.toString()
    }

    data class Reply(val msg: Msg, val usage: Usage, val finish: String)

    fun parseReply(body: String): Reply {
        val root = JSONObject(body)
        val ch = root.optJSONArray("choices")?.optJSONObject(0) ?: error("返回里没有 choices：${body.take(200)}")
        val m = ch.optJSONObject("message") ?: JSONObject()
        val calls = mutableListOf<ToolCall>()
        m.optJSONArray("tool_calls")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val f = c.optJSONObject("function") ?: continue
                calls += ToolCall(c.optString("id"), f.optString("name"), f.optString("arguments", "{}"))
            }
        }
        val content = if (m.isNull("content")) null else m.optString("content")
        val reasoning = if (m.has("reasoning_content") && !m.isNull("reasoning_content")) m.optString("reasoning_content") else null
        return Reply(Msg("assistant", content, calls, reasoning = reasoning, at = System.currentTimeMillis()), parseUsage(root.optJSONObject("usage")), ch.optString("finish_reason"))
    }

    /** 把服务器的错误返回翻成人话。 */
    fun explainError(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        val hint = when (code) {
            401 -> "密钥不对或已失效，请到设置里重新粘贴"
            402 -> "账户余额不足，请到服务商后台充值"
            429 -> "请求太频繁或额度用完，稍后再试"
            in 500..599 -> "服务商那边暂时出错，稍后再试"
            else -> "请求失败"
        }
        return "$hint（HTTP $code${if (msg.isNotBlank()) "：${msg.take(120)}" else ""}）"
    }

    // ---------------- 存储 ----------------

    fun msgToJson(m: Msg): JSONObject = msgToApi(m).put("at", m.at)

    fun msgFromJson(o: JSONObject): Msg {
        val calls = mutableListOf<ToolCall>()
        o.optJSONArray("tool_calls")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i); val f = c.getJSONObject("function")
                calls += ToolCall(c.optString("id"), f.optString("name"), f.optString("arguments"))
            }
        }
        return Msg(
            o.optString("role"), if (o.isNull("content")) null else o.optString("content"), calls,
            if (o.has("tool_call_id")) o.optString("tool_call_id") else null,
            if (o.has("reasoning_content")) o.optString("reasoning_content") else null, o.optLong("at"),
        )
    }

    /**
     * 发给模型的上下文：最近 maxMsgs 条，但不能从 tool 消息或带 tool_calls 的 assistant 中间截断
     * （OpenAI 格式要求每个 tool_call 后面都跟着它的 tool 结果）。
     */
    fun window(history: List<Msg>, maxMsgs: Int): List<Msg> {
        if (history.size <= maxMsgs) return history
        var start = history.size - maxMsgs
        while (start < history.size && history[start].role != "user") start++
        return history.subList(start, history.size)
    }

    fun parseArgs(s: String): JSONObject = runCatching { JSONObject(s.ifBlank { "{}" }) }.getOrElse { JSONObject() }

    fun monthKey(ms: Long): String {
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")); c.timeInMillis = ms
        return "%04d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1)
    }

    // ---------------- DeepSeek 余额 / 联网搜索（官方 /user/balance、/v1/tools/search、/v1/tools/fetch） ----------------

    data class Balance(val currency: String, val total: String, val granted: String, val toppedUp: String, val available: Boolean)

    fun parseBalance(body: String): Balance? {
        val o = JSONObject(body)
        val b = o.optJSONArray("balance_infos")?.optJSONObject(0) ?: return null
        return Balance(b.optString("currency"), b.optString("total_balance"), b.optString("granted_balance"), b.optString("topped_up_balance"), o.optBoolean("is_available", true))
    }

    data class Hit(val title: String, val url: String, val snippet: String)

    /** 搜索结果：字段名按官方文档（results[].title/url/snippet，summary 给模型用），兼容 data 包一层的写法。 */
    fun parseSearch(body: String): List<Hit> {
        val o = JSONObject(body)
        val arr = o.optJSONArray("results") ?: o.optJSONObject("data")?.optJSONArray("results") ?: o.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = r.optString("url").ifBlank { r.optString("link") }
            if (url.isBlank()) return@mapNotNull null
            Hit(r.optString("title"), url, r.optString("summary").ifBlank { r.optString("snippet") }.ifBlank { r.optString("content") }.take(600))
        }
    }

    const val SEARCH_COST_YUAN = 0.005 // 中文价格页：5 元 / 1000 次成功请求
}

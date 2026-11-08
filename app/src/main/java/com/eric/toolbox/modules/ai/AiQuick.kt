package com.eric.toolbox.modules.ai

import android.content.Context
import com.eric.toolbox.core.Http
import org.json.JSONArray

/** 一次性提问（不进对话记录、不调用工具），给学英语「AI 讲讲」等用。花费计入 AI 管家的月花费和上限。 */
object AiQuick {
    suspend fun ask(ctx: Context, system: String, user: String): Result<String> =
        chat(ctx, listOf(AiLogic.Msg("system", system), AiLogic.Msg("user", user)))

    /** 多轮（口语对练用）：messages 第一条是 system。 */
    suspend fun chat(ctx: Context, messages: List<AiLogic.Msg>): Result<String> = runCatching {
        val s = AiStore.settings(ctx)
        val key = AiStore.key(ctx, s.provider)
        require(key.isNotBlank()) { "还没填 AI 密钥（AI 管家 › 设置）" }
        require(AiStore.monthCost(ctx) < s.cap) { "本月 AI 花费已到上限" }
        val body = AiLogic.buildRequest(s.provider, s.model, messages, JSONArray())
        val resp = try { Http.postJson(AiStore.url(s), body, mapOf("Authorization" to "Bearer $key")) }
        catch (e: Http.HttpError) { throw IllegalStateException(AiLogic.explainError(e.code, e.body)) }
        val r = AiLogic.parseReply(resp)
        AiStore.addCost(ctx, AiLogic.cost(r.usage, if (s.provider == "deepseek") AiLogic.deepseekPrice(s.model) else null, s.priceIn, s.priceOut))
        r.msg.content?.trim().orEmpty().ifEmpty { error("没有返回内容") }
    }
}

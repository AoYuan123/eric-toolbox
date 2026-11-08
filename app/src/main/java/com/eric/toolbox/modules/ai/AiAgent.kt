package com.eric.toolbox.modules.ai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eric.toolbox.core.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 对话引擎：发消息 → 模型要调用功能 → 只读的直接执行、要改东西的等用户确认 → 把结果交回模型 → 得到回答。 */
object AiAgent {
    val history = mutableStateListOf<AiLogic.Msg>()
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var pending by mutableStateOf<Pending?>(null); private set
    /** 每次得到最终回答时 +1（界面据此决定要不要朗读）。 */
    var replied by mutableStateOf(0); private set
    private var loaded = false
    /** 底部 AI 键长按进来时为 true：对话页一打开就开始听。 */
    var voiceOnOpen = false

    class Pending(val calls: List<Pair<AiLogic.ToolCall, AiTools.Plan>>)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        history.clear(); history.addAll(AiStore.loadChat(ctx))
        // 上次退出时有没处理完的确认：当作取消，保证每个 tool_call 都有结果
        val ids = history.filter { it.role == "tool" }.mapNotNull { it.toolCallId }.toSet()
        val dangling = history.lastOrNull { it.role == "assistant" && it.toolCalls.isNotEmpty() }?.toolCalls?.filter { it.id !in ids }.orEmpty()
        if (dangling.isNotEmpty()) {
            dangling.forEach { history += AiLogic.Msg("tool", "用户没有确认，这个改动没有执行", toolCallId = it.id, at = System.currentTimeMillis()) }
            history += AiLogic.Msg("assistant", "（上次的改动没确认，已取消。）", at = System.currentTimeMillis())
        }
    }

    fun clear(ctx: Context) { history.clear(); pending = null; error = null; AiStore.saveChat(ctx, history) }

    private fun systemPrompt(ctx: Context): String {
        val now = SimpleDateFormat("yyyy-MM-dd EEEE HH:mm", Locale.CHINA).apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }.format(Date())
        return """
你是「Eric的百宝箱」安卓 App 里的 AI 管家，帮用户 Eric 查询和管理百宝箱。现在是北京时间 $now。
规则：
1. 涉及百宝箱里的数据（金价、试验、书架、记录、日期等），一定先调用对应功能拿到真实结果再回答，绝不编造数字。功能返回出错就如实说。
2. 要改设置、加关注时，直接调用对应的功能；App 会弹确认卡给用户，用户确认前不要说“已改好”。
3. 用户没开放或没有的功能，直接说做不到，并告诉他可以去哪个页面手动操作。
4. 回答用简体中文，简短直接；价格写单位“元/克”。
""".trimIndent()
    }

    /** 不跟页面走：离开对话页请求也会继续完成。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun send(ctx: Context, text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy || pending != null) return
        error = null
        history += AiLogic.Msg("user", t, at = System.currentTimeMillis())
        AiStore.saveChat(ctx, history)
        launchRun(ctx)
    }

    private fun launchRun(ctx: Context) {
        busy = true
        scope.launch {
            try { run(ctx) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                error = when (e) {
                    is Http.HttpError -> AiLogic.explainError(e.code, e.body)
                    is java.net.SocketTimeoutException -> "等了 60 秒没回应，网络可能不太好，再试一次"
                    is java.net.UnknownHostException -> "连不上服务器，检查一下网络"
                    else -> e.message ?: "出错了"
                }
            } finally { busy = false; AiStore.saveChat(ctx, history) }
        }
    }

    private suspend fun run(ctx: Context) {
        val s = AiStore.settings(ctx)
        val key = AiStore.key(ctx, s.provider)
        val url = AiStore.url(s)
        if (key.isBlank()) throw IllegalStateException("还没填 API 密钥：点右上角「设置」粘贴")
        if (!url.startsWith("https://")) throw IllegalStateException("接口地址要以 https:// 开头（在设置里填）")
        if (s.model.isBlank()) throw IllegalStateException("还没选模型（在设置里填）")
        repeat(6) {
            if (AiStore.monthCost(ctx) >= s.cap) throw IllegalStateException("本月花费已到上限 ¥${"%.2f".format(s.cap)}，可以在设置里调高；下月自动恢复")
            val msgs = listOf(AiLogic.Msg("system", systemPrompt(ctx))) + AiLogic.window(history.toList(), 30)
            val body = AiLogic.buildRequest(s.provider, s.model, msgs, AiTools.specs(s.groups))
            val resp = Http.postJson(url, body, mapOf("Authorization" to "Bearer $key"))
            val r = AiLogic.parseReply(resp)
            val price = if (s.provider == "deepseek") AiLogic.deepseekPrice(s.model) else null
            AiStore.addCost(ctx, AiLogic.cost(r.usage, price, s.priceIn, s.priceOut))
            history += r.msg
            if (r.msg.toolCalls.isEmpty()) { replied++; return }
            val plans = mutableListOf<Pair<AiLogic.ToolCall, AiTools.Plan>>()
            for (c in r.msg.toolCalls) {
                val tool = AiTools.byName(c.name)
                val result: String? = when {
                    tool == null -> "没有这个功能：${c.name}"
                    tool.group !in s.groups -> "用户没有开放「${AiStore.GROUPS[tool.group]?.first}」给 AI 操作"
                    tool.write -> try { plans += c to tool.plan!!(ctx, AiLogic.parseArgs(c.args)); null } catch (e: Exception) { "不能执行：${e.message}" }
                    else -> try { tool.exec!!(ctx, AiLogic.parseArgs(c.args)) } catch (e: Exception) { "执行出错：${e.message}" }
                }
                if (result != null) history += AiLogic.Msg("tool", result.take(6000), toolCallId = c.id, at = System.currentTimeMillis())
            }
            if (plans.isNotEmpty()) { pending = Pending(plans); return }
        }
        throw IllegalStateException("来回调用次数太多，已停下")
    }

    fun confirm(ctx: Context) {
        val p = pending ?: return
        busy = true
        scope.launch {
            for ((call, plan) in p.calls) {
                val text = try {
                    val snap = AiStore.snapshot(ctx, plan.parts)
                    val r = plan.apply()
                    val id = AiStore.addLog(ctx, plan.group, plan.summary, snap)
                    "已执行：${plan.summary}。$r（记录#$id）"
                } catch (e: Exception) { "执行失败：${e.message}" }
                history += AiLogic.Msg("tool", text, toolCallId = call.id, at = System.currentTimeMillis())
            }
            pending = null
            AiStore.saveChat(ctx, history)
            busy = false
            launchRun(ctx)
        }
    }

    fun cancel(ctx: Context) {
        val p = pending ?: return
        p.calls.forEach { (c, _) -> history += AiLogic.Msg("tool", "用户取消了这个改动，没有执行", toolCallId = c.id, at = System.currentTimeMillis()) }
        history += AiLogic.Msg("assistant", "好的，已取消，没有做任何改动。", at = System.currentTimeMillis())
        pending = null
        AiStore.saveChat(ctx, history)
    }

    /** 从工具结果里取出操作记录编号，用来在对话里显示「撤销」。 */
    fun logIdOf(toolText: String?): Long? = toolText?.let { Regex("记录#(\\d+)").find(it)?.groupValues?.get(1)?.toLongOrNull() }
}

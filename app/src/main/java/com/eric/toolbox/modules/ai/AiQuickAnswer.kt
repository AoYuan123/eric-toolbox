package com.eric.toolbox.modules.ai

import android.content.Context
import com.eric.toolbox.core.AlertLog
import com.eric.toolbox.modules.english.EnglishStore
import com.eric.toolbox.modules.gold.GoldModule
import com.eric.toolbox.modules.trials.TrialModule
import com.eric.toolbox.ui.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地快答：短而明确的问题（问金价、看提醒、背词进度、试验变化、DeepSeek 余额）直接用百宝箱自己的数据回答，
 * 不调用大模型、不要密钥、不花钱。只有带“改/设/提醒我”等动作词或较长的句子才交给 AI。
 */
object AiQuickAnswer {
    enum class Kind { GOLD, RECORDS, ENGLISH, TRIALS, BALANCE }

    private val ACTION = Regex("改|设|设置|帮我|提醒我|加|删|换|切|为什么|怎么|分析|预测|新闻|原因|set|change|why|add")

    fun match(text: String): Kind? {
        val t = text.trim().lowercase()
        if (t.length > 24 || ACTION.containsMatchIn(t)) return null
        return when {
            Regex("金价|黄金|积存金|gold").containsMatchIn(t) -> Kind.GOLD
            Regex("余额|balance").containsMatchIn(t) -> Kind.BALANCE
            Regex("提醒|记录|alert").containsMatchIn(t) -> Kind.RECORDS
            Regex("生词|单词|背词|复习|words|review").containsMatchIn(t) -> Kind.ENGLISH
            Regex("试验|临床|trial").containsMatchIn(t) -> Kind.TRIALS
            else -> null
        }
    }

    private fun hm(ms: Long?) = if (ms == null || ms <= 0) "--" else SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(ms))

    suspend fun answer(ctx: Context, k: Kind): String = when (k) {
        Kind.GOLD -> {
            val cur = GoldModule.snapshot.value
            if (cur == null || System.currentTimeMillis() - cur.fetchedAtMs > 60_000) GoldModule.refresh(ctx)
            val s = GoldModule.snapshot.value ?: error(GoldModule.status.value.error ?: "取价失败")
            val c = GoldModule.config(ctx)
            val next = GoldModule.lastAlerted(ctx) - c.step
            buildString {
                s.market?.let { m ->
                    append(tr("上海金 Au99.99：%.2f 元/克（%s）", "Au99.99: %.2f CNY/g (%s)").format(m.price, hm(m.timeMs)))
                    m.prevClose?.let { p -> append(tr("，较昨收 %+.2f", ", %+.2f vs prev close").format(m.price - p)) }
                    append(if (m.price > next) tr("，距下一档 $next 还差 %.2f", "; %.2f above next tier $next").format(m.price - next) else tr("，已低于 $next", "; below $next"))
                    append("\n")
                }
                s.bank?.jcj?.let { append(tr("工行积存金：%.2f", "ICBC gold savings: %.2f").format(it)).append("\n") }
                s.bank?.ryBuy?.let { append(tr("如意金积存价：%.2f", "ICBC Ruyi: %.2f").format(it)).append("\n") }
                if (!s.marketVerified()) append(tr("（多源比对未通过，数字仅供参考）", "(sources disagree; treat with care)"))
            }.trim()
        }
        Kind.RECORDS -> AlertLog.load(ctx).take(5).joinToString("\n") { "${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(it.timeMs))} ${it.title}：${it.body}" }
            .ifEmpty { tr("还没有提醒记录", "No alerts yet") }
        Kind.ENGLISH -> tr("今天已练 %d 个；到期复习 %d 个，今天还能学新词 %d 个；连续 %d 天。", "Done %d today; %d due; %d new left; %d-day streak.").format(
            EnglishStore.doneToday(ctx), EnglishStore.dueCount(ctx), (EnglishStore.newLimit(ctx) - EnglishStore.newToday(ctx)).coerceAtLeast(0), EnglishStore.streak(ctx))
        Kind.TRIALS -> {
            val ch = TrialModule.changes(ctx).sortedByDescending { it.atMs }.take(5)
            if (ch.isEmpty()) tr("关注的临床试验暂无变化记录", "No trial updates") else ch.joinToString("\n") { "${it.nct} ${it.detail}" }
        }
        Kind.BALANCE -> AiTools.balance(ctx)?.let { b -> tr("DeepSeek 余额：%s %s（赠送 %s，充值 %s）", "DeepSeek balance: %s %s (granted %s, topped up %s)").format(b.total, b.currency, b.granted, b.toppedUp) }
            ?: tr("还没有保存 DeepSeek 密钥", "No DeepSeek key saved")
    }
}

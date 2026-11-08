package com.eric.toolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.BuildConfig
import com.eric.toolbox.core.CrashLog
import com.eric.toolbox.core.Heartbeat
import com.eric.toolbox.core.Http
import com.eric.toolbox.core.ModuleRegistry
import com.eric.toolbox.core.MonitorService
import com.eric.toolbox.core.Notifier
import com.eric.toolbox.modules.ai.AiStore
import com.eric.toolbox.modules.ai.AiTools
import com.eric.toolbox.modules.gold.GoldModule
import com.eric.toolbox.modules.trials.TrialModule
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 体检：一页看清各项运行状态；「复制全部」把结果变成一段文字，方便发给 Claude 排查。 */
private enum class Lv { OK, WARN, BAD, INFO }
private data class Check(val title: String, val value: String, val lv: Lv)

private fun t(ms: Long) = if (ms <= 0) "--" else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ms))

private fun localChecks(ctx: Context): List<Check> {
    val now = System.currentTimeMillis()
    val out = mutableListOf<Check>()
    val running = ModuleRegistry.all.count { it.background?.isEnabled(ctx) == true }
    val beat = Heartbeat.last(ctx); val next = Heartbeat.expectedNext(ctx)
    out += when {
        running == 0 -> Check(tr("后台服务", "Background"), tr("没有开启的监控", "No monitors on"), Lv.INFO)
        !MonitorService.running || beat == 0L || now - next > 3 * 60_000 -> Check(tr("后台服务", "Background"), tr("可能被系统停止（上次心跳 ${t(beat)}）", "May be stopped (last beat ${t(beat)})"), Lv.BAD)
        else -> Check(tr("后台服务", "Background"), tr("运行中 · $running 项 · 心跳 ${t(beat)}", "Running · $running · beat ${t(beat)}"), Lv.OK)
    }
    val ka = keepAliveStatus(ctx).count { it }
    out += Check(tr("后台保活设置", "Keep-alive setup"), "$ka / 5", if (ka >= 4) Lv.OK else Lv.WARN)
    out += Check(tr("通知权限", "Notifications"), if (Notifier.canPost(ctx)) tr("已开启", "On") else tr("未开启", "Off"), if (Notifier.canPost(ctx)) Lv.OK else Lv.BAD)
    val gs = GoldModule.status.value
    out += Check(tr("金价数据", "Gold data"),
        if (gs.lastOkMs > 0) tr("上次成功 ${t(gs.lastOkMs)}", "Last OK ${t(gs.lastOkMs)}") + (gs.error?.let { tr("；部分出错：", "; errors: ") + it.take(80) } ?: "") else tr("本次打开还没取到", "Not fetched yet"),
        when { gs.lastOkMs > 0 && gs.error == null -> Lv.OK; gs.lastOkMs > 0 -> Lv.WARN; else -> Lv.INFO })
    val subs = TrialModule.subs(ctx); val errs = subs.count { it.lastError != null }
    out += Check(tr("临床试验", "Trials"), tr("${subs.size} 组关注 · 上次检查 ${t(TrialModule.lastRun(ctx))}", "${subs.size} watches · last ${t(TrialModule.lastRun(ctx))}") + if (errs > 0) tr(" · $errs 组出错", " · $errs errors") else "",
        if (errs > 0) Lv.WARN else Lv.OK)
    val engine = Settings.Secure.getString(ctx.contentResolver, "tts_default_synth").orEmpty()
    out += Check(tr("语音引擎（朗读）", "TTS engine"), engine.ifBlank { tr("未知", "Unknown") }, if (engine.isBlank()) Lv.WARN else Lv.OK)
    val asr = SpeechRecognizer.isRecognitionAvailable(ctx)
    out += Check(tr("系统语音识别", "Speech recognition"), if (asr) tr("可用", "Available") else tr("这台手机没有（可用输入法语音）", "Not available"), if (asr) Lv.OK else Lv.WARN)
    val ai = AiStore.settings(ctx)
    out += Check(tr("AI 密钥", "AI key"), if (AiStore.key(ctx, ai.provider).isNotBlank()) tr("已保存（${ai.provider}）", "Saved (${ai.provider})") else tr("未填", "Not set"),
        if (AiStore.key(ctx, ai.provider).isNotBlank()) Lv.OK else Lv.INFO)
    out += Check(tr("本月 AI 花费", "AI spend this month"), "¥%.3f / ¥%.0f".format(AiStore.monthCost(ctx), ai.cap), if (AiStore.monthCost(ctx) < ai.cap) Lv.OK else Lv.WARN)
    out += Check(tr("存储占用", "Storage"), formatBytes(appDataBytes(ctx)), Lv.INFO)
    val crashes = CrashLog.all(ctx)
    out += Check(tr("闪退记录", "Crashes"), if (crashes.isEmpty()) tr("没有", "None") else tr("${crashes.size} 次，最近 ${t(crashes.first().lastModified())}", "${crashes.size}, latest ${t(crashes.first().lastModified())}"),
        if (crashes.isEmpty()) Lv.OK else Lv.WARN)
    return out
}

@Composable
fun HealthScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val checks = remember(tick) { localChecks(ctx) }
    val net = remember { mutableStateMapOf<String, Check>() }
    var showCrash by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    fun runNet() {
        testing = true
        scope.launch {
            suspend fun probe(name: String, block: suspend () -> String) {
                val t0 = System.currentTimeMillis()
                net[name] = try { val v = block(); Check(name, "$v · ${System.currentTimeMillis() - t0} ms", Lv.OK) } catch (e: Exception) { Check(name, tr("不通：", "Failed: ") + (e.message ?: "?").take(60), Lv.BAD) }
            }
            probe(tr("更新通道 Gitee", "Updates · Gitee")) { Http.get("https://gitee.com/api/v5/repos/${BuildConfig.GITEE_REPO}/releases/latest", timeoutMs = 10_000).let { tr("通", "OK") } }
            probe(tr("更新通道 GitHub", "Updates · GitHub")) { Http.get("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest", mapOf("Accept" to "application/vnd.github+json"), timeoutMs = 10_000).let { tr("通", "OK") } }
            if (AiStore.key(ctx, "deepseek").isNotBlank()) probe(tr("DeepSeek 余额", "DeepSeek balance")) { AiTools.balance(ctx)?.let { "${it.total} ${it.currency}" } ?: "?" }
            testing = false
        }
    }

    fun report(): String = buildString {
        append("Eric的百宝箱 v${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n")
        append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())).append("\n\n")
        (checks + net.values).forEach { append(if (it.lv == Lv.BAD) "✕ " else if (it.lv == Lv.WARN) "! " else "· ").append(it.title).append("：").append(it.value).append("\n") }
        CrashLog.latest(ctx)?.let { append("\n最近一次闪退：\n").append(it.readText().take(3000)) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(tr("体检", "Health check"), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val bad = (checks + net.values).count { it.lv == Lv.BAD }; val warn = (checks + net.values).count { it.lv == Lv.WARN }
            Tile(strong = true) {
                Caption(tr("总体", "Overall"))
                Text(when { bad > 0 -> tr("有 $bad 项异常", "$bad problem(s)"); warn > 0 -> tr("基本正常，$warn 项注意", "Mostly fine, $warn to check"); else -> tr("全部正常", "All good") },
                    color = when { bad > 0 -> C.Warn; warn > 0 -> C.Gold; else -> C.Text }, fontSize = 22.sp)
                Caption(tr("有问题时点「复制全部」发给我就行", "Tap \"Copy all\" and send it to me if something's off"))
            }
            ListCard {
                (checks + net.values).forEach { c ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(when (c.lv) { Lv.OK -> C.Ok; Lv.WARN -> C.Gold; Lv.BAD -> C.Warn; Lv.INFO -> C.Line }, CircleShape))
                        Spacer(Modifier.size(10.dp))
                        Text(c.title, color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(0.42f))
                        Text(c.value, color = C.Muted, fontSize = 12.sp, modifier = Modifier.weight(0.58f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(Modifier.weight(1f), onClick = { if (!testing) runNet() }) { Text(if (testing) tr("检测中…", "Testing…") else tr("检测网络通道", "Test connections"), color = C.Text, fontSize = 14.sp) }
                Tile(Modifier.weight(1f), onClick = {
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("health", report()))
                    Toast.makeText(ctx, tr("已复制，可以粘贴给我", "Copied"), Toast.LENGTH_SHORT).show()
                }) { Text(tr("复制全部", "Copy all"), color = C.Text, fontSize = 14.sp) }
            }
            if (CrashLog.all(ctx).isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(Modifier.weight(1f), onClick = { showCrash = true }) { Text(tr("查看闪退详情", "View crash"), color = C.Text, fontSize = 14.sp) }
                Tile(Modifier.weight(1f), onClick = { CrashLog.clear(ctx); tick++ }) { Text(tr("清除闪退记录", "Clear crashes"), color = C.Muted, fontSize = 14.sp) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showCrash) AlertDialog(
        onDismissRequest = { showCrash = false },
        confirmButton = { TextButton(onClick = { showCrash = false }) { Text(tr("关闭", "Close")) } },
        title = { Text(tr("最近一次闪退", "Latest crash")) },
        text = { SelectionContainer { Text(CrashLog.latest(ctx)?.readText()?.take(6000).orEmpty(), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.verticalScroll(rememberScrollState())) } },
    )
}


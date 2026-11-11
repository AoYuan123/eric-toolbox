package com.eric.toolbox.modules.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.eric.toolbox.core.VoiceInput
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.eric.toolbox.ui.press
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.eric.toolbox.core.Category
import com.eric.toolbox.core.ToolModule
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.ListCard
import com.eric.toolbox.ui.RowItem
import com.eric.toolbox.ui.SectionLabel
import com.eric.toolbox.ui.ToggleRow
import com.eric.toolbox.ui.TopBar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AiModule : ToolModule {
    override val id = "ai"
    override val name = "AI 管家"
    override val category = Category.TOOL
    override val icon: ImageVector = Icons.Filled.AutoAwesome

    @Composable
    override fun HomeSummary() { Text("问我，或让我帮你改设置", color = C.Muted, fontSize = 13.sp) }

    @Composable
    override fun Screen(onBack: () -> Unit) = AiRoot(onBack)
}

private val Red = Color(0xFFE5484D)
private val Green = Color(0xFF6FCF97)
private fun onGold() = if (C.palette.isLight) Color.White else C.Bg

@Composable
private fun AiRoot(onBack: () -> Unit) {
    var page by remember { mutableStateOf("chat") }
    BackHandler(enabled = page != "chat") { page = if (page == "log") "settings" else "chat" }
    when (page) {
        "settings" -> AiSettings(onBack = { page = "chat" }, openLog = { page = "log" })
        "log" -> AiLogScreen(onBack = { page = "settings" })
        "history" -> AiHistory(onBack = { page = "chat" })
        else -> AiChat(onBack, openSettings = { page = "settings" }, openHistory = { page = "history" })
    }
}

@Composable
private fun Chip(text: String, color: Color = C.Muted) {
    Text(text, color = color, fontSize = 12.sp, modifier = Modifier.background(C.Surface, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
private fun SmallBtn(text: String, gold: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 40.dp).background(if (gold) C.Gold else Color.Transparent, RoundedCornerShape(10.dp))
            .border(1.dp, if (gold) C.Gold else C.Line, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (gold) onGold() else C.Text, fontSize = 14.sp, fontWeight = if (gold) FontWeight.Medium else FontWeight.Normal) }
}

// ======================= 对话页 =======================

private sealed interface Row0 {
    data class User(val text: String) : Row0
    data class Bot(val text: String) : Row0
    data class Tools(val titles: List<String>) : Row0
    data class Done(val summary: String, val logId: Long) : Row0
    data class Fail(val text: String) : Row0
}

private fun rows(history: List<AiLogic.Msg>): List<Row0> {
    val out = mutableListOf<Row0>()
    for (m in history) when (m.role) {
        "user" -> out += Row0.User(m.content.orEmpty())
        "assistant" -> {
            val reads = m.toolCalls.mapNotNull { AiTools.byName(it.name) }.filter { !it.write }.map { it.title }
            if (reads.isNotEmpty()) out += Row0.Tools(reads)
            if (!m.content.isNullOrBlank()) out += Row0.Bot(m.content.trim())
        }
        "tool" -> {
            val t = m.content.orEmpty()
            val id = AiAgent.logIdOf(t)
            when {
                id != null -> out += Row0.Done(t.removePrefix("已执行：").substringBefore("。").substringBefore("（记录#"), id)
                t.startsWith("执行失败") || t.startsWith("不能执行") || t.startsWith("用户没有开放") -> out += Row0.Fail(t)
            }
        }
    }
    return out
}

@Composable
private fun AiChat(onBack: () -> Unit, openSettings: () -> Unit, openHistory: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    LaunchedEffect(Unit) { AiAgent.load(app) }
    val s = remember(AiAgent.busy, AiAgent.replied) { AiStore.settings(app) }
    val cost = remember(AiAgent.busy, AiAgent.replied) { AiStore.monthCost(app) }
    var input by remember { mutableStateOf("") }
    var undoTick by remember { mutableIntStateOf(0) }
    val logs = remember(undoTick, AiAgent.history.size) { AiStore.logs(app).associateBy { it.id } }
    val list = rows(AiAgent.history)
    val listState = rememberLazyListState()
    LaunchedEffect(list.size, AiAgent.pending, AiAgent.busy, AiAgent.error) { val n = listState.layoutInfo.totalItemsCount; if (n > 0) listState.animateScrollToItem(n - 1) }

    // ---- 朗读回答 ----
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) { onDispose { tts.value?.shutdown() } }
    LaunchedEffect(AiAgent.replied) {
        if (AiAgent.replied == 0 || !s.speakReply) return@LaunchedEffect
        val text = AiAgent.history.lastOrNull { it.role == "assistant" && !it.content.isNullOrBlank() }?.content ?: return@LaunchedEffect
        val t = tts.value
        if (t == null) tts.value = TextToSpeech(app) { st -> if (st == TextToSpeech.SUCCESS) { tts.value?.language = Locale.SIMPLIFIED_CHINESE; tts.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ai") } }
        else t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ai")
    }

    // ---- 按住说话 ----
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    val voice = remember {
        VoiceInput(app, "zh-CN",
            onPartial = { partial = it },
            onFinal = { text ->
                listening = false; partial = ""
                if (AiStore.settings(app).voiceAutoSend) AiAgent.send(app, text) else input = (input + text)
            },
            onError = { e ->
                listening = false; partial = ""
                val msg = when (e) {
                    VoiceInput.NOTHING, SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听清，再按住说一次"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别要联网，检查一下网络"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
                    SpeechRecognizer.ERROR_CLIENT -> null
                    else -> "语音识别出错（代码 $e）"
                }
                msg?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
            })
    }
    DisposableEffect(Unit) { onDispose { voice.destroy() } }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(ctx, "没有麦克风权限，没法语音输入", Toast.LENGTH_LONG).show()
        else Toast.makeText(ctx, "已允许麦克风，再按住话筒说话", Toast.LENGTH_SHORT).show()
    }
    /** hold=true：按住说话，松手才发（停顿不会提前结束）。 */
    fun startListen(hold: Boolean = false): Boolean {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(Manifest.permission.RECORD_AUDIO); return false
        }
        val available = VoiceInput.available(ctx)
        AiStore.prefs(app).edit().putBoolean("asr_available", available).putLong("asr_checked", System.currentTimeMillis()).apply()
        if (!available) {
            Toast.makeText(ctx, "这台手机没有系统语音识别服务。可以点输入框，用输入法键盘上的语音输入", Toast.LENGTH_LONG).show()
            return false
        }
        tts.value?.stop()
        partial = ""; listening = true
        return voice.start(hold)
    }

    LaunchedEffect(Unit) { if (AiAgent.voiceOnOpen) { AiAgent.voiceOnOpen = false; startListen() } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TopBarBack(onBack)
            Column(Modifier.weight(1f)) {
                Text("AI 管家", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Text)
                Text("${AiLogic.provider(s.provider).name.substringBefore("（")} · ${s.model.removePrefix("deepseek-")} · 本月 ¥${"%.2f".format(cost)} / 上限 ¥${"%.0f".format(s.cap)}",
                    color = C.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
            HeadIcon(Icons.Outlined.Add, "新对话") { if (AiAgent.busy) { Toast.makeText(ctx, "AI 还在回答，等这条说完再切换", Toast.LENGTH_SHORT).show() } else { AiAgent.newChat(app); input = "" } }
            HeadIcon(Icons.Outlined.History, "历史对话") { if (AiAgent.busy) { Toast.makeText(ctx, "AI 还在回答，等这条说完再切换", Toast.LENGTH_SHORT).show() } else openHistory() }
            HeadIcon(Icons.Outlined.Settings, "设置", onClick = openSettings)
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("可以这样跟我说：", color = C.Muted, fontSize = 13.sp)
                    listOf("今天金价多少？离 840 还差几块", "跌破 820 提醒我，档位改成每 5 元一档", "帮我加个关注：Lutetium Lu 177，中国，二三期",
                        "接着读《傲慢与偏见》给我听", "明年 3 月 15 日往后 30 个工作日是哪天", "切到象牙白主题").forEach {
                        Text("· $it", color = C.Text, fontSize = 14.sp, modifier = Modifier.clickable { input = it })
                    }
                    if (AiStore.key(app, s.provider).isBlank()) Text("还没填 API 密钥：点右上角「设置」粘贴后才能用。", color = C.Gold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(list) { r ->
                when (r) {
                    is Row0.User -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(r.text, color = C.Gold, fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.widthIn(max = 300.dp)
                            .background(C.GoldBg, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)).padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                    is Row0.Bot -> Text(r.text, color = C.Text, fontSize = 15.sp, lineHeight = 24.sp, modifier = Modifier.widthIn(max = 330.dp)
                        .background(C.Surface, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)).padding(horizontal = 14.dp, vertical = 10.dp))
                    is Row0.Tools -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { r.titles.distinct().forEach { Chip("✓ $it") } }
                    is Row0.Fail -> Chip("✕ ${r.text.take(80)}", Red)
                    is Row0.Done -> {
                        val undone = logs[r.logId]?.undone == true
                        Column(Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("已执行的改动", color = C.Muted, fontSize = 12.sp)
                            Text(r.summary, color = C.Text, fontSize = 14.sp, lineHeight = 21.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (undone) "已撤销" else "✓ 已改好，记进「操作记录」", color = if (undone) C.Muted else Green, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (!undone) SmallBtn("撤销") {
                                    val e = AiStore.undo(app, r.logId) { AiTools.afterUndo(app) }
                                    Toast.makeText(ctx, e ?: "已撤销，恢复到改动前", Toast.LENGTH_SHORT).show(); undoTick++
                                }
                            }
                        }
                    }
                }
            }
            AiAgent.pending?.let { p ->
                item {
                    Column(Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(16.dp)).border(1.dp, C.Gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("我要做这些改动，确认吗？", color = C.Gold, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        p.calls.forEach { (_, plan) ->
                            plan.changes.forEach { (k, v) ->
                                Column(Modifier.border(width = 0.dp, color = Color.Transparent).padding(start = 10.dp)) {
                                    Text(k, color = C.Muted, fontSize = 12.sp)
                                    Text(v, color = C.Text, fontSize = 15.sp, lineHeight = 22.sp)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallBtn("取消", modifier = Modifier.weight(1f)) { AiAgent.cancel(app) }
                            SmallBtn("确认执行", gold = true, modifier = Modifier.weight(1f)) { AiAgent.confirm(app) }
                        }
                    }
                }
            }
            if (AiAgent.busy) item { Chip("思考中…") }
            AiAgent.error?.let { item { Text(it, color = Red, fontSize = 13.sp, lineHeight = 20.sp) } }
            item { Box(Modifier.size(4.dp)) }
        }

        Column(Modifier.fillMaxWidth().background(C.Surface).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (listening) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(Red, CircleShape))
                Text(if (partial.isBlank()) "正在听…  松开发送" else partial, color = C.Text, fontSize = 14.sp, maxLines = 2)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("今天金价", "有新试验吗", "继续听书", "生词要复习吗", "最近的提醒").forEach { q ->
                    Text(q, color = C.Muted, fontSize = 12.sp, modifier = Modifier.border(1.dp, C.Line, RoundedCornerShape(16.dp))
                        .clickable(enabled = !AiAgent.busy && AiAgent.pending == null) { AiAgent.send(app, q) }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    input, { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("打字，或按住下面的话筒说话", fontSize = 14.sp) }, maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = C.Bg, unfocusedContainerColor = C.Bg, focusedTextColor = C.Text, unfocusedTextColor = C.Text),
                )
                val canSend = input.isNotBlank() && !AiAgent.busy && AiAgent.pending == null
                Box(Modifier.size(48.dp).background(if (canSend) C.Gold else C.Line, CircleShape).clickable(enabled = canSend) { AiAgent.send(app, input); input = "" },
                    contentAlignment = Alignment.Center) { Text("↑", color = onGold(), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }
            // 话筒放在正中间，拇指最好按
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier.size(68.dp).background(if (listening) Red else C.Gold, CircleShape).pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            if (startListen(hold = true)) { tryAwaitRelease(); voice.stop() }
                        })
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Mic, contentDescription = "按住说话", tint = onGold(), modifier = Modifier.size(30.dp)) }
                Text(if (listening) "松开发送" else "按住说话", color = C.Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun HeadIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = desc, tint = C.Text, modifier = Modifier.size(22.dp))
    }
}

// ======================= 历史对话 =======================

@Composable
private fun AiHistory(onBack: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    var tick by remember { mutableIntStateOf(0) }
    val list = remember(tick) { AiStore.chats(ctx) }
    var del by remember { mutableStateOf<AiStore.ChatInfo?>(null) }
    val fmt = remember { SimpleDateFormat("M月d日 HH:mm", Locale.CHINA) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TopBarBack(onBack)
            Text("历史对话", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.weight(1f))
            SmallBtn("新对话", gold = true) { if (AiAgent.busy) { Toast.makeText(ctx, "AI 还在回答，等这条说完再切换", Toast.LENGTH_SHORT).show() } else { AiAgent.newChat(ctx); onBack() } }
        }
        Text("点一下继续聊，长按删除。最多保留 100 个。", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (list.isEmpty()) item { Text("还没有历史对话", color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp)) }
            items(list, key = { it.id }) { c ->
                val cur = c.id == AiAgent.chatId
                Column(Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(14.dp))
                    .border(1.dp, if (cur) C.Gold.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(14.dp))
                    .press(onLongPress = { del = c }) { if (AiAgent.busy) { Toast.makeText(ctx, "AI 还在回答，等这条说完再切换", Toast.LENGTH_SHORT).show() } else { AiAgent.openChat(ctx, c.id); onBack() } }.padding(14.dp)) {
                    Text(c.title, color = C.Text, fontSize = 15.sp, maxLines = 1)
                    Text((if (cur) "当前 · " else "") + fmt.format(Date(c.updated)), color = C.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
    del?.let { c ->
        AlertDialog(onDismissRequest = { del = null }, containerColor = C.Surface,
            title = { Text("删除这个对话？", color = C.Text) }, text = { Text(c.title, color = C.Muted) },
            confirmButton = { TextButton({ AiAgent.deleteChat(ctx, c.id); del = null; tick++ }) { Text("删除", color = Red) } },
            dismissButton = { TextButton({ del = null }) { Text("取消", color = C.Muted) } })
    }
}

@Composable
private fun TopBarBack(onBack: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Text("‹", color = C.Muted, fontSize = 28.sp) }
}

// ======================= 设置页 =======================

@Composable
private fun AiSettings(onBack: () -> Unit, openLog: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    var s by remember { mutableStateOf(AiStore.settings(ctx)) }
    fun upd(n: AiStore.Settings) { s = n; AiStore.save(ctx, n) }
    var keyInput by remember(s.provider) { mutableStateOf("") }
    var hasKey by remember(s.provider) { mutableStateOf(AiStore.key(ctx, s.provider).isNotBlank()) }
    val p = AiStore.prefs(ctx)

    Column(Modifier.fillMaxSize()) {
        TopBar("AI 管家设置", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("模型服务")
            ListCard {
                AiLogic.PROVIDERS.forEach { pv ->
                    val on = pv.key == s.provider
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                        upd(s.copy(provider = pv.key, model = AiStore.prefs(ctx).getString("model_${pv.key}", null) ?: pv.models.firstOrNull() ?: ""))
                    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(18.dp).border(2.dp, if (on) C.Gold else C.Line, CircleShape).padding(4.dp)) { if (on) Box(Modifier.fillMaxSize().background(C.Gold, CircleShape)) }
                        Column(Modifier.weight(1f)) { Text(pv.name, color = C.Text, fontSize = 15.sp); Text(pv.note, color = C.Muted, fontSize = 11.sp) }
                    }
                }
            }
            ListCard {
                Text("API 密钥" + if (hasKey) "（已保存 · 不进备份）" else "（未填）", color = if (hasKey) Green else C.Gold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                OutlinedTextField(keyInput, { keyInput = it }, label = { Text(if (hasKey) "要换密钥就粘贴新的" else "粘贴你在服务商后台生成的密钥") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallBtn("保存密钥", gold = true) { if (keyInput.isNotBlank()) { AiStore.setKey(ctx, s.provider, keyInput); keyInput = ""; hasKey = true; Toast.makeText(ctx, "已保存", Toast.LENGTH_SHORT).show() } }
                    if (hasKey) SmallBtn("删除密钥") { AiStore.setKey(ctx, s.provider, ""); hasKey = false }
                }
                if (s.provider == "custom") {
                    OutlinedTextField(s.customUrl, { upd(s.copy(customUrl = it)) }, label = { Text("接口地址（…/chat/completions）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Text("模型", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiLogic.provider(s.provider).models.forEach { m ->
                        val on = m == s.model
                        Text(m.removePrefix("deepseek-") + when (m) { "deepseek-v4-flash" -> " 省钱"; "deepseek-v4-pro" -> " 更聪明"; else -> "" },
                            color = if (on) onGold() else C.Muted, fontSize = 13.sp, modifier = Modifier.background(if (on) C.Gold else C.Bg, RoundedCornerShape(8.dp))
                                .clickable { upd(s.copy(model = m)); p.edit().putString("model_${s.provider}", m).apply() }.padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                }
                if (s.provider != "deepseek") OutlinedTextField(s.model, { upd(s.copy(model = it.trim())); p.edit().putString("model_${s.provider}", it.trim()).apply() },
                    label = { Text("模型名（可直接改）") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
            }

            SectionLabel("花费")
            ListCard {
                RowItem("本月已用（估算）", "¥" + "%.3f".format(AiStore.monthCost(ctx)))
                if (s.provider == "deepseek" && hasKey) {
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    var bal by remember { mutableStateOf("点击查询") }
                    RowItem("DeepSeek 余额", bal, sub = "官方余额接口，含赠送和充值", onClick = {
                        bal = "查询中…"
                        scope.launch { bal = runCatching { AiTools.balance(ctx)?.let { "${it.total} ${it.currency}（赠 ${it.granted}）" } ?: "没有返回" }.getOrElse { "查询失败：${it.message}" } }
                    })
                }
                var capText by remember { mutableStateOf("%.0f".format(s.cap)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("每月上限（元）", color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    OutlinedTextField(capText, { capText = it; it.toDoubleOrNull()?.takeIf { v -> v > 0 }?.let { v -> upd(s.copy(cap = v)) } }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.widthIn(max = 110.dp).padding(vertical = 6.dp))
                }
                if (s.provider != "deepseek") {
                    var pin by remember { mutableStateOf(if (s.priceIn > 0) s.priceIn.toString() else "") }
                    var pout by remember { mutableStateOf(if (s.priceOut > 0) s.priceOut.toString() else "") }
                    Text("这家的单价我没内置，按你在官网看到的填（元/百万 tokens），不填就记为 0：", color = C.Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        OutlinedTextField(pin, { pin = it; upd(s.copy(priceIn = it.toDoubleOrNull() ?: 0.0)) }, label = { Text("输入单价") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(pout, { pout = it; upd(s.copy(priceOut = it.toDoubleOrNull() ?: 0.0)) }, label = { Text("输出单价") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                }
            }
            Text("DeepSeek 按官网 11-08 单价估算（flash 输入 1 元、输出 2 元 / 百万 tokens）；到上限后暂停，下月自动恢复。实际扣费以服务商后台为准。",
                color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))

            SectionLabel("允许 AI 操作的模块")
            ListCard {
                AiStore.GROUPS.forEach { (g, v) ->
                    ToggleRow(v.first, v.second, g in s.groups) { on -> upd(s.copy(groups = if (on) s.groups + g else s.groups - g)) }
                }
                RowItem("改动前必须确认", "🔒 开", sub = "改设置、加关注都会先弹确认卡（固定开启）")
            }

            SectionLabel("语音")
            ListCard {
                ToggleRow("按住说话，松开就发送", "关掉则只把识别的文字填进输入框", s.voiceAutoSend) { upd(s.copy(voiceAutoSend = it)) }
                ToggleRow("把回答读出来", "用手机的系统语音引擎（你装的 Kokoro）", s.speakReply) { upd(s.copy(speakReply = it)) }
                val checked = p.getLong("asr_checked", 0L)
                RowItem("系统语音识别", if (checked == 0L) "按一次🎙后检测" else if (p.getBoolean("asr_available", false)) "可用" else "这台手机没有")
            }

            ListCard {
                RowItem("操作记录", "", onClick = openLog)
                RowItem("新开对话", "", sub = "当前对话保存在「历史对话」里", onClick = { if (AiAgent.busy) Toast.makeText(ctx, "AI 还在回答，等这条说完再切换", Toast.LENGTH_SHORT).show() else { AiAgent.newChat(ctx); Toast.makeText(ctx, "已新开对话", Toast.LENGTH_SHORT).show() } })
            }
            Box(Modifier.size(20.dp))
        }
    }
}

// ======================= 操作记录 =======================

@Composable
private fun AiLogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    var tick by remember { mutableIntStateOf(0) }
    val logs = remember(tick) { AiStore.logs(ctx) }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }
    Column(Modifier.fillMaxSize()) {
        TopBar("AI 操作记录", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("每次改动前自动留一份改动前的设置，点「撤销」恢复。只读的操作（查价格、查记录）不记在这里。", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
            if (logs.isEmpty()) Text("还没有记录", color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            else ListCard {
                logs.forEach { l ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("${fmt.format(Date(l.at))} · ${AiStore.GROUPS[l.group]?.first ?: l.group}", color = C.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text(l.summary, color = C.Text, fontSize = 14.sp, lineHeight = 21.sp)
                        }
                        if (l.undone) Text("已撤销", color = C.Muted, fontSize = 13.sp)
                        else SmallBtn("撤销") {
                            val e = AiStore.undo(ctx, l.id) { AiTools.afterUndo(ctx) }
                            Toast.makeText(ctx, e ?: "已撤销", Toast.LENGTH_SHORT).show(); tick++
                        }
                    }
                }
            }
        }
    }
}

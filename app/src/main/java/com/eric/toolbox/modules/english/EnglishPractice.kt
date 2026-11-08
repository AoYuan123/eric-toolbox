package com.eric.toolbox.modules.english

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.eric.toolbox.modules.ai.AiLogic
import com.eric.toolbox.modules.ai.AiQuick
import com.eric.toolbox.modules.read.Notes
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.Caption
import com.eric.toolbox.ui.Tile
import com.eric.toolbox.ui.press
import com.eric.toolbox.ui.tr
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

// ======================= 场景 =======================

internal data class Scene(val id: String, val zh: String, val en: String, val work: Boolean, val role: String, val goalZh: String)

internal val SCENES = listOf(
    Scene("intro", "自我介绍", "Introducing yourself", true, "a friendly patent attorney from a US firm meeting Eric for the first time at a conference", "介绍自己的工作和专业方向（生物医药知识产权），问对方的工作"),
    Scene("client", "客户电话会", "Client call", true, "the head of IP at a biotech company (Eric's client) on a video call about their antibody program", "确认客户需求、约定交付时间和下一步"),
    Scene("oa", "审查意见讨论", "Office action discussion", true, "a US patent attorney at the foreign associate firm, discussing how to respond to an office action with an obviousness rejection", "说明审查员的意见，提出修改权利要求或争辩的方案"),
    Scene("fto", "FTO 结论沟通", "FTO findings", true, "a business development director at a pharma client who wants to know whether their product has freedom to operate", "用简单的话讲清 FTO 结论、风险专利和建议"),
    Scene("smalltalk", "会议寒暄", "Small talk at a meeting", true, "a European colleague chatting during a coffee break at an industry conference", "自然寒暄：天气、行程、对会议的印象"),
    Scene("hotel", "酒店入住", "Hotel check-in", false, "a hotel front desk clerk", "报预订信息，问早餐和退房时间"),
    Scene("food", "餐厅点餐", "Ordering food", false, "a waiter at a restaurant", "问推荐、点菜、结账"),
    Scene("ask", "问路出行", "Asking directions", false, "a local person on the street", "问怎么去会展中心、坐什么车"),
)

private fun hasMic(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** 英文语音识别（系统识别服务）。没有服务时返回 false。 */
private class EnListener(private val ctx: Context, private val onPartial: (String) -> Unit, private val onFinal: (String) -> Unit, private val onError: (String?) -> Unit) {
    private var rec: SpeechRecognizer? = null
    fun start(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return false
        val r = rec ?: SpeechRecognizer.createSpeechRecognizer(ctx).also { rec = it }
        var last = ""
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onPartialResults(b: Bundle?) { b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { last = it; onPartial(it) } }
            override fun onResults(b: Bundle?) { onFinal(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().ifBlank { last }) }
            override fun onError(e: Int) {
                if (last.isNotBlank()) onFinal(last)
                else onError(when (e) { SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> tr("没听清，再说一次", "Didn't catch that"); SpeechRecognizer.ERROR_CLIENT -> null; else -> tr("识别出错（$e）", "Recognition error ($e)") })
            }
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true))
        return true
    }
    fun stop() { rec?.stopListening() }
    fun destroy() { rec?.destroy(); rec = null }
}

// ======================= 练口语 =======================

private data class Line(val ai: Boolean, val text: String, val better: String = "", val why: String = "")

@Composable
internal fun SpeakScreen(voice: EnVoice, onBack: () -> Unit) {
    var scene by remember { mutableStateOf<Scene?>(null) }
    val s = scene
    if (s == null) ScenePicker(onBack) { scene = it } else Conversation(s, voice) { scene = null }
}

@Composable
private fun ScenePicker(onBack: () -> Unit, pick: (Scene) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header(tr("练口语", "Speaking"), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Caption(tr("AI 扮演对方，你用英文说；每句下面会给更地道的说法。说不出来可以先说中文。用 AI 管家的 DeepSeek 密钥，一场约几分钱。", "AI plays the other side; you speak English and get a better phrasing under each line."), Modifier.padding(horizontal = 4.dp))
            listOf(true to tr("工作", "Work"), false to tr("日常", "Daily life")).forEach { (work, label) ->
                Caption(label, Modifier.padding(start = 4.dp, top = 6.dp))
                SCENES.filter { it.work == work }.forEach { sc ->
                    Tile(onClick = { pick(sc) }) {
                        Text(tr(sc.zh, sc.en), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Caption(tr("目标：", "Goal: ") + sc.goalZh)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun systemPrompt(s: Scene) = """
You are role-playing as ${s.role}. The user, Eric, is a Chinese IP consultant in biopharma practising spoken English.
Scenario goal for Eric (in Chinese): ${s.goalZh}.
Rules:
- Stay in character. Reply in natural spoken English, 1-3 short sentences, then usually ask one question to keep the conversation going.
- If Eric writes Chinese, treat it as what he wants to say: show how to say it in "better", and reply to it in English.
- Judge only Eric's LAST message. "better": a more natural/correct English version of it ("" if already natural). "why": brief reason in Chinese (<= 30 characters, "" if none).
- Output ONLY JSON: {"reply": "...", "better": "...", "why": "..."}
""".trimIndent()

@Composable
private fun Conversation(s: Scene, voice: EnVoice, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<Line>() }
    var busy by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    var phrases by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val list = rememberLazyListState()
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun history(): List<AiLogic.Msg> = listOf(AiLogic.Msg("system", systemPrompt(s))) +
        lines.map { if (it.ai) AiLogic.Msg("assistant", JSONObject().put("reply", it.text).put("better", "").put("why", "").toString()) else AiLogic.Msg("user", it.text) }

    fun turn(userText: String?) {
        if (busy) return
        if (userText != null) lines += Line(false, userText)
        busy = true
        scope.launch {
            val msgs = if (lines.isEmpty()) history() + AiLogic.Msg("user", "(Start the conversation with a short opening line. better and why must be empty.)") else history()
            val r = AiQuick.chat(ctx.applicationContext, msgs)
            busy = false
            r.onSuccess { raw ->
                val t = EnglishLogic.parseTurn(raw)
                if (userText != null && lines.isNotEmpty() && !lines.last().ai) lines[lines.lastIndex] = lines.last().copy(better = t.better, why = t.why)
                lines += Line(true, t.reply)
                voice.speak(t.reply)
            }.onFailure { Toast.makeText(ctx, it.message ?: "AI 出错", Toast.LENGTH_LONG).show() }
        }
    }
    LaunchedEffect(Unit) { turn(null) }
    LaunchedEffect(lines.size, busy) { if (lines.isNotEmpty()) list.animateScrollToItem(lines.size - 1 + if (busy) 1 else 0) }
    val listener = remember { EnListener(ctx, onPartial = { partial = it }, onFinal = { listening = false; partial = ""; if (it.isNotBlank()) turn(it) }, onError = { listening = false; partial = ""; it?.let { m -> Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show() } }) }
    DisposableEffect(Unit) { onDispose { listener.destroy(); voice.stop() } }

    fun finish() {
        if (lines.count { !it.ai } == 0) { onBack(); return }
        busy = true
        scope.launch {
            val transcript = lines.joinToString("\n") { (if (it.ai) "Partner: " else "Eric: ") + it.text }
            val r = AiQuick.ask(ctx.applicationContext,
                "你是英语口语老师。用中文给出简短反馈。只输出 JSON：{\"summary\":\"3-5 行中文点评：做得好的、主要问题、下次注意\",\"phrases\":[{\"en\":\"值得记的英文表达\",\"zh\":\"中文\"}]}，phrases 3-5 条，优先这次对话里 Eric 用错或没用上的地道说法。",
                transcript)
            busy = false
            r.onSuccess { raw ->
                val t = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                runCatching {
                    val o = JSONObject(t.substring(t.indexOf('{'), t.lastIndexOf('}') + 1))
                    report = o.optString("summary")
                    val a = o.optJSONArray("phrases")
                    phrases = if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it).let { p -> p.optString("en") to p.optString("zh") } }.filter { it.first.isNotBlank() }
                }.onFailure { report = t }
                EnglishStore.prefs(ctx).edit().putInt("speak_day", EnglishLogic.dayNo(System.currentTimeMillis())).putString("speak_last", s.id).apply()
            }.onFailure { Toast.makeText(ctx, it.message ?: "AI 出错", Toast.LENGTH_LONG).show() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header(tr(s.zh, s.en), onBack) { Text(tr("结束 · 报告", "Finish"), color = C.Gold, fontSize = 14.sp, modifier = Modifier.press { finish() }.padding(8.dp)) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Caption(tr("目标：", "Goal: ") + s.goalZh) }
            items(lines.toList()) { l ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (l.ai) Alignment.Start else Alignment.End) {
                    Text(l.text, color = if (l.ai) C.Text else C.Gold, fontSize = 15.sp, lineHeight = 23.sp, fontFamily = FontFamily.Serif,
                        modifier = Modifier.widthIn(max = 320.dp).background(if (l.ai) C.Surface else C.GoldBg, RoundedCornerShape(16.dp)).press { if (l.ai) voice.speak(l.text) }.padding(horizontal = 14.dp, vertical = 10.dp))
                    if (l.better.isNotBlank()) Column(Modifier.widthIn(max = 320.dp).padding(top = 4.dp).border(1.dp, C.Line, RoundedCornerShape(12.dp)).press { voice.speak(l.better) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(tr("更地道：", "Better: ") + l.better, color = C.Text, fontSize = 13.sp, fontFamily = FontFamily.Serif)
                        if (l.why.isNotBlank()) Caption(l.why)
                    }
                }
            }
            if (busy) item { Caption(tr("对方在想…", "Thinking…")) }
            report?.let { r ->
                item {
                    Tile(strong = true) {
                        Text(tr("这场的反馈", "Feedback"), color = C.Gold, fontSize = 13.sp)
                        Text(r, color = C.Text, fontSize = 14.sp, lineHeight = 22.sp)
                        phrases.forEach { (en, zh) ->
                            Spacer(Modifier.height(6.dp))
                            Text(en, color = C.Text, fontSize = 14.sp, fontFamily = FontFamily.Serif, modifier = Modifier.press { voice.speak(en) })
                            Caption(zh)
                        }
                        if (phrases.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Btn(tr("把这些表达加入生词本", "Add phrases to My words"), primary = true, modifier = Modifier.fillMaxWidth()) {
                                phrases.forEach { (en, zh) -> if (!Notes.hasWord(ctx, en)) Notes.addWord(ctx, Notes.Word(en, "", zh, "", "", tr("口语", "Speaking"), 0, 0.0, System.currentTimeMillis())) }
                                Toast.makeText(ctx, tr("已加入，会安排背诵", "Added"), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(C.Surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (listening) Text(partial.ifBlank { tr("正在听…松开发送", "Listening… release to send") }, color = C.Text, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), placeholder = { Text(tr("打字，或按住下面的话筒说英文", "Type, or hold the mic below")) }, maxLines = 3)
                Btn(tr("发送", "Send"), primary = input.isNotBlank()) { if (input.isNotBlank()) { turn(input.trim()); input = "" } }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(68.dp).background(if (listening) C.Warn else C.Gold, CircleShape).pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        if (!hasMic(ctx)) { perm.launch(Manifest.permission.RECORD_AUDIO); return@detectTapGestures }
                        voice.stop()
                        if (listener.start()) { listening = true; tryAwaitRelease(); listener.stop() }
                        else Toast.makeText(ctx, tr("这台手机没有系统语音识别，可以用输入法的语音输入打字", "No speech recognition on this phone; use keyboard voice input"), Toast.LENGTH_LONG).show()
                    })
                }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Mic, contentDescription = tr("按住说话", "Hold to speak"), tint = onAcc(), modifier = Modifier.size(30.dp)) }
                Text(if (listening) tr("松开发送", "Release to send") else tr("按住说英文", "Hold to speak"), color = C.Muted, fontSize = 11.sp)
            }
        }
    }
}

// ======================= 学发音 =======================

/** 易错音（中国人常混的几组），每组两个最小对立词。 */
private val PAIRS = listOf(
    "/θ/ – /s/" to listOf("think" to "sink", "three" to "free", "thank" to "sank"),
    "/v/ – /w/" to listOf("vest" to "west", "very" to "wary", "vine" to "wine"),
    "/æ/ – /e/" to listOf("bad" to "bed", "man" to "men", "sad" to "said"),
    "/iː/ – /ɪ/" to listOf("sheep" to "ship", "leave" to "live", "feel" to "fill"),
    "/l/ – /n/" to listOf("light" to "night", "low" to "no", "lice" to "nice"),
    "/r/ – /l/" to listOf("right" to "light", "read" to "lead", "pray" to "play"),
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PronScreen(voice: EnVoice, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sentences = remember { (EnglishBooks.ip.map { it.ex to it.exZh } + EnglishBooks.daily.map { it.ex to it.exZh }).shuffled(java.util.Random(EnglishLogic.dayNo(System.currentTimeMillis()).toLong())) }
    var i by remember { mutableIntStateOf(0) }
    val (target, targetZh) = sentences[i % sentences.size]
    val file = remember { File(ctx.cacheDir, "pron.m4a") }
    var recording by remember { mutableStateOf(false) }
    var hasRec by remember(i) { mutableStateOf(false) }
    var check by remember(i) { mutableStateOf<List<Pair<String, Boolean>>?>(null) }
    var checking by remember { mutableStateOf(false) }
    val recorder = remember { mutableStateOf<MediaRecorder?>(null) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    DisposableEffect(Unit) { onDispose { runCatching { recorder.value?.release() }; player.value?.release(); file.delete(); voice.stop() } }
    val listener = remember { EnListener(ctx, onPartial = {}, onFinal = { checking = false; check = EnglishLogic.wordCheck(target, it) }, onError = { checking = false; it?.let { m -> Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show() } }) }
    DisposableEffect(Unit) { onDispose { listener.destroy() } }
    LaunchedEffect(i) { file.delete() }

    fun startRec() {
        voice.stop(); player.value?.release(); player.value = null
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(44100); r.setAudioEncodingBitRate(96000); r.setOutputFile(file.path)
        r.prepare(); r.start(); recorder.value = r; recording = true
    }
    fun stopRec() {
        runCatching { recorder.value?.stop() }.onSuccess { hasRec = file.length() > 0 }.onFailure { file.delete(); hasRec = false }
        recorder.value?.release(); recorder.value = null; recording = false
    }
    fun playMine(done: (() -> Unit)? = null) {
        if (!file.isFile) return
        player.value?.release()
        player.value = MediaPlayer().apply { setDataSource(file.path); setOnCompletionListener { done?.invoke() }; prepare(); start() }
    }

    Column(Modifier.fillMaxSize()) {
        Header(tr("学发音", "Pronunciation"), onBack) { Caption(tr("免费 · 录音不上传", "Free · stays on phone")) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(strong = true) {
                Caption(tr("跟读这句", "Shadow this sentence"))
                val c = check
                if (c == null) Text(target, color = C.Text, fontSize = 19.sp, lineHeight = 28.sp, fontFamily = FontFamily.Serif)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    c.forEach { (w, ok) -> Text(w, color = if (ok) C.Text else C.Warn, fontSize = 19.sp, fontFamily = FontFamily.Serif, textDecoration = if (ok) null else TextDecoration.Underline) }
                }
                Caption(targetZh)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(tr("▶ 标准音", "▶ Model"), modifier = Modifier.weight(1f)) { voice.speak(target) }
                    Btn(tr("▶ 慢速", "▶ Slow"), modifier = Modifier.weight(1f)) { voice.speak(target, rate = 0.75f) }
                }
            }
            Tile {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(72.dp).background(if (recording) C.Warn else C.Gold, CircleShape).pointerInput(i) {
                        detectTapGestures(onPress = {
                            if (!hasMic(ctx)) { perm.launch(Manifest.permission.RECORD_AUDIO); return@detectTapGestures }
                            val ok = runCatching { startRec() }.isSuccess
                            if (ok) { tryAwaitRelease(); stopRec() } else Toast.makeText(ctx, tr("录音启动失败", "Couldn't start recording"), Toast.LENGTH_SHORT).show()
                        })
                    }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Mic, contentDescription = tr("按住录音", "Hold to record"), tint = onAcc(), modifier = Modifier.size(32.dp)) }
                    Text(if (recording) tr("正在录…松开结束", "Recording… release to stop") else tr("按住录音，松开结束", "Hold to record"), color = C.Text, fontSize = 14.sp)
                    Caption(tr("录音只存在手机缓存，离开本页就删除", "Recording is deleted when you leave"))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(tr("▶ 我的录音", "▶ Mine"), modifier = Modifier.weight(1f)) { if (hasRec) playMine() }
                    Btn(tr("A/B 对比", "A/B compare"), primary = hasRec, modifier = Modifier.weight(1f)) {
                        if (hasRec) voice.speak(target, rate = 0.9f) { playMine() }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Caption(tr("A/B 对比：先放标准音，紧接着放你的录音，最容易听出差别。", "A/B: model first, then you."))
            }
            Tile(onClick = {
                if (!hasMic(ctx)) { perm.launch(Manifest.permission.RECORD_AUDIO); return@Tile }
                voice.stop()
                if (listener.start()) { checking = true; check = null } else Toast.makeText(ctx, tr("这台手机没有系统语音识别，这项检查用不了", "No speech recognition on this phone"), Toast.LENGTH_LONG).show()
            }) {
                Text(if (checking) tr("请读一遍这句…（说完会自动结束）", "Read it now…") else tr("读一遍，看机器听不听得懂", "Read it — can the phone understand you?"), color = C.Text, fontSize = 14.sp)
                Caption(tr("用手机语音识别听你读，没识别出来的词会标红。只是参考，不是专业评分。", "Words the recognizer missed turn red. A rough check, not a score."))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(tr("上一句", "Previous"), modifier = Modifier.weight(1f)) { if (i > 0) i-- }
                Btn(tr("下一句", "Next"), primary = true, modifier = Modifier.weight(1f)) { i++ }
            }
            Caption(tr("中国人最常读错的几组音（点单词听）", "Commonly confused sounds (tap to hear)"), Modifier.padding(start = 4.dp, top = 6.dp))
            PAIRS.forEach { (ipa, words) ->
                Tile {
                    Text(ipa, color = C.Gold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        words.forEach { (a, b) ->
                            Pill(a) { voice.speak(a, rate = 0.8f) }
                            Text("·", color = C.Muted)
                            Pill(b) { voice.speak(b, rate = 0.8f) }
                            Spacer(Modifier.size(6.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}


package com.eric.toolbox.modules.translate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.Category
import com.eric.toolbox.core.Store
import com.eric.toolbox.core.ToolModule
import com.eric.toolbox.modules.read.Dict
import com.eric.toolbox.modules.read.Listen
import com.eric.toolbox.modules.read.Notes
import com.eric.toolbox.modules.read.ReadLogic
import com.eric.toolbox.modules.trials.Translator
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.TopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** 翻译：中英互译（百度翻译，与临床试验、读书共用设置和额度）+ 用系统语音引擎朗读（逐句高亮）。 */
object TranslateModule : ToolModule {
    override val id = "translate"
    override val name = "翻译"
    override val category = Category.TOOL
    override val icon: ImageVector = Icons.Filled.Translate

    @Composable
    override fun HomeSummary() {
        Text("中英互译 · 朗读", color = C.Text, fontSize = 13.sp)
    }

    @Composable
    override fun Screen(onBack: () -> Unit) = TranslateScreen(onBack)

    fun prefs(ctx: Context) = Store.module(ctx, id)
}

/** 朗读器：一段文字切句后排队交给系统语音引擎（提前排队，AI 引擎句间不停顿），onStart 回调告诉界面正在读第几句。 */
private class Reader(ctx: Context) {
    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var gen = 0
    var onIndex: (Int) -> Unit = {}
    var onEnd: () -> Unit = {}

    fun speak(sentences: List<String>, zh: Boolean, rate: Float) {
        val t = tts
        if (t == null) {
            tts = TextToSpeech(app) { st ->
                main.post {
                    ready = st == TextToSpeech.SUCCESS
                    if (!ready) { Toast.makeText(app, "手机没有可用的语音引擎（系统设置 › 文字转语音）", Toast.LENGTH_LONG).show(); onEnd(); return@post }
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String) { main.post { idx(id)?.let(onIndex) } }
                        override fun onDone(id: String) { main.post { if (idx(id) == lastIndex) onEnd() } }
                        @Deprecated("Deprecated in Java") override fun onError(id: String) { main.post { if (idx(id) == lastIndex) onEnd() } }
                    })
                    speak(sentences, zh, rate)
                }
            }
            return
        }
        if (!ready) return
        if (Listen.playing) Listen.pause() // 别和听书同时出声
        val r = t.setLanguage(if (zh) Locale.SIMPLIFIED_CHINESE else Locale.US)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
            Toast.makeText(app, "语音引擎不支持${if (zh) "中文" else "英语"}，可在系统设置 › 文字转语音里换引擎", Toast.LENGTH_LONG).show()
        t.setSpeechRate(rate)
        gen++
        lastIndex = sentences.size - 1
        sentences.forEachIndexed { i, s -> t.speak(s, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "t${gen}_$i") }
    }

    private var lastIndex = -1
    private fun idx(id: String): Int? { val p = id.removePrefix("t").split('_'); return if (p.size == 2 && p[0].toIntOrNull() == gen) p[1].toIntOrNull() else null }

    fun stop() { gen++; tts?.stop() }
    fun shutdown() { tts?.shutdown(); tts = null }
}

@Composable
private fun Btn(text: String, gold: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 42.dp).background(if (gold) C.GoldBg else Color.Transparent, RoundedCornerShape(10.dp))
            .border(1.dp, if (gold) C.GoldBg else C.Line, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (gold) C.Gold else C.Text, fontSize = 14.sp) }
}

@Composable
private fun TranslateScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = remember { TranslateModule.prefs(ctx) }
    var dir by remember { mutableStateOf(runCatching { TranslateLogic.Dir.valueOf(p.getString("dir", "AUTO")!!) }.getOrDefault(TranslateLogic.Dir.AUTO)) }
    var input by remember { mutableStateOf(p.getString("last_in", "") ?: "") }
    var output by remember { mutableStateOf(p.getString("last_out", "") ?: "") }
    var outLang by remember { mutableStateOf(p.getString("last_to", "en") ?: "en") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var rate by remember { mutableFloatStateOf(p.getFloat("rate", 1.0f)) }
    var history by remember { mutableStateOf(TranslateLogic.historyFromJson(p.getString("history", "[]"))) }
    var dictEntry by remember { mutableStateOf<Dict.Entry?>(null) }
    var wordAdded by remember { mutableStateOf(false) }
    // 朗读状态：读的是哪段（out/in）、第几句
    var speaking by remember { mutableStateOf<String?>(null) }
    var spIdx by remember { mutableIntStateOf(-1) }
    var spSentences by remember { mutableStateOf<List<String>>(emptyList()) }
    val reader = remember { Reader(ctx) }
    DisposableEffect(Unit) { onDispose { reader.shutdown() } }
    reader.onIndex = { spIdx = it }
    reader.onEnd = { speaking = null; spIdx = -1 }

    fun lookupWord(src: String, dst: String) {
        val w = TranslateLogic.singleEnglishWord(src) ?: TranslateLogic.singleEnglishWord(dst)
        dictEntry = null; wordAdded = false
        if (w != null) scope.launch {
            dictEntry = withContext(Dispatchers.IO) { runCatching { Dict.lookup(ctx, w) }.getOrNull() }
            wordAdded = dictEntry?.let { Notes.hasWord(ctx, it.word) } ?: false
        }
    }

    fun run() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        reader.stop(); speaking = null
        val (from, to) = TranslateLogic.resolve(dir, text)
        busy = true; err = null
        scope.launch {
            val (res, e) = Translator.translateText(ctx, text, from, to)
            busy = false
            if (res == null) { err = e; return@launch }
            output = res; outLang = to
            history = TranslateLogic.addHistory(history, TranslateLogic.Item(text, res, from, to, System.currentTimeMillis()))
            p.edit().putString("history", TranslateLogic.historyToJson(history)).putString("last_in", text).putString("last_out", res).putString("last_to", to).apply()
            lookupWord(text, res)
        }
    }

    fun speak(which: String) {
        if (speaking == which) { reader.stop(); speaking = null; spIdx = -1; return }
        val text = if (which == "out") output else input
        if (text.isBlank()) return
        val zh = if (which == "out") outLang == "zh" else outLang != "zh"
        val ss = ReadLogic.splitSentences(text.replace('\n', ' ')).map { it.text }.ifEmpty { listOf(text) }
        spSentences = ss; spIdx = -1; speaking = which
        reader.speak(ss, zh, rate)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("翻译", onBack) { Text("本月已用 ${"%.1f".format(Translator.monthUsed(ctx) / 10000.0)} 万字", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp)) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 方向
            Row(Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(14.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TranslateLogic.Dir.entries.forEach { d ->
                    val on = d == dir
                    Box(Modifier.weight(1f).heightIn(min = 40.dp).background(if (on) C.Gold else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { dir = d; p.edit().putString("dir", d.name).apply() }, contentAlignment = Alignment.Center) {
                        Text(d.label, color = if (on) (if (C.palette.isLight) Color.White else C.Bg) else C.Muted, fontSize = 13.sp)
                    }
                }
            }
            if (dir == TranslateLogic.Dir.AUTO) Text("自动：输入里有汉字就译成英文，否则译成中文", color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))

            OutlinedTextField(input, { input = it }, label = { Text("输入原文（一个词或一大段话都可以）") }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp), maxLines = 12)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${input.length} 字", color = C.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Btn("粘贴") {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let { input = it }
                }
                Btn("清空") { input = ""; output = ""; dictEntry = null; err = null; reader.stop(); speaking = null }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 50.dp).background(if (busy) C.Surface else C.Gold, RoundedCornerShape(12.dp)).clickable(enabled = !busy) { run() }, contentAlignment = Alignment.Center) {
                Text(if (busy) "翻译中…" else "翻译", color = if (busy) C.Muted else (if (C.palette.isLight) Color.White else C.Bg), fontSize = 16.sp)
            }
            err?.let { Text(it, color = C.Warn, fontSize = 13.sp) }

            if (output.isNotBlank()) {
                Column(Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("译文", color = C.Muted, fontSize = 12.sp)
                    if (speaking == "out" && spSentences.isNotEmpty()) {
                        Text(buildAnnotatedString {
                            spSentences.forEachIndexed { i, s ->
                                if (i == spIdx) withStyle(SpanStyle(background = C.GoldBg, color = C.Gold)) { append(s) } else append(s)
                                if (i < spSentences.size - 1) append(if (outLang == "zh") "" else " ")
                            }
                        }, color = C.Text, fontSize = 17.sp, lineHeight = 27.sp, fontFamily = if (outLang == "en") FontFamily.Serif else FontFamily.Default)
                    } else Text(output, color = C.Text, fontSize = 17.sp, lineHeight = 27.sp, fontFamily = if (outLang == "en") FontFamily.Serif else FontFamily.Default)

                    dictEntry?.let { e ->
                        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
                            Text(e.word + if (e.phonetic.isNotBlank()) "  /${e.phonetic}/ · 离线词典" else " · 离线词典", color = C.Muted, fontSize = 13.sp)
                            Text(ReadLogic.shortMeaning(e.meaning), color = C.Text, fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn(if (speaking == "out") "■ 停止" else "▶ 朗读译文", gold = true, modifier = Modifier.weight(1f)) { speak("out") }
                        Btn("复制", modifier = Modifier.weight(1f)) {
                            ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("译文", output))
                            Toast.makeText(ctx, "已复制译文", Toast.LENGTH_SHORT).show()
                        }
                        val e = dictEntry
                        if (e != null) Btn(if (wordAdded) "✓ 已在生词本" else "加生词本", gold = wordAdded, modifier = Modifier.weight(1f)) {
                            if (wordAdded) { Notes.removeWord(ctx, e.word); wordAdded = false }
                            else { Notes.addWord(ctx, Notes.Word(e.word, e.phonetic, ReadLogic.shortMeaning(e.meaning), "", "", "翻译", 0, 0.0, System.currentTimeMillis())); wordAdded = true }
                        } else Btn(if (speaking == "in") "■ 停止" else "朗读原文", modifier = Modifier.weight(1f)) { speak("in") }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("语速", color = C.Muted, fontSize = 12.sp)
                        TranslateLogic.speeds.forEach { v ->
                            val on = v == rate
                            Box(Modifier.heightIn(min = 32.dp).background(if (on) C.Gold else C.Bg, RoundedCornerShape(8.dp)).clickable {
                                rate = v; p.edit().putFloat("rate", v).apply()
                                speaking?.let { w -> speaking = null; speak(w) } // 正在读就按新语速重读
                            }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                Text("${v}×", color = if (on) (if (C.palette.isLight) Color.White else C.Bg) else C.Muted, fontSize = 12.sp)
                            }
                        }
                    }
                    Text("朗读用手机的系统语音引擎（你装的 Kokoro）。读的时候正在读的句子会高亮。", color = C.Muted, fontSize = 11.sp)
                }
            }

            if (history.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("最近翻译", color = C.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    Text("清除", color = C.Muted, fontSize = 12.sp, modifier = Modifier.clickable { history = emptyList(); p.edit().putString("history", "[]").apply() }.padding(8.dp))
                }
                Column(Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                    history.forEach { h ->
                        Column(Modifier.fillMaxWidth().clickable {
                            reader.stop(); speaking = null
                            input = h.src; output = h.dst; outLang = h.to; err = null; lookupWord(h.src, h.dst)
                        }.padding(vertical = 10.dp)) {
                            Text(h.src, color = C.Text, fontSize = 14.sp, maxLines = 2)
                            Text(h.dst, color = C.Muted, fontSize = 12.sp, maxLines = 2)
                        }
                    }
                }
            }
            Text("翻译：百度翻译（与临床试验、读书共用设置和每月上限）。同一句话翻过一次就存在手机上，再翻不扣额度。", color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
            Box(Modifier.height(16.dp))
        }
    }
}

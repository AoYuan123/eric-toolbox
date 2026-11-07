package com.eric.toolbox.modules.read

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.modules.trials.Translator
import com.eric.toolbox.ui.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** 单词发音：系统文字转语音，第一次用时才初始化。手机没有英文语音时提示一次。 */
class Speaker(private val ctx: Context) {
    private var tts: TextToSpeech? = null
    private var ok = false
    private var pending: String? = null

    fun speak(text: String) {
        val t = tts
        if (t == null) {
            pending = text
            tts = TextToSpeech(ctx.applicationContext) { status ->
                ok = status == TextToSpeech.SUCCESS
                val engine = tts
                if (ok && engine != null) {
                    val r = engine.setLanguage(Locale.US)
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        ok = false
                        Toast.makeText(ctx, "手机的语音引擎不支持英语发音，可在系统设置 › 文字转语音里换一个引擎", Toast.LENGTH_LONG).show()
                    }
                }
                if (!ok && status != TextToSpeech.SUCCESS) Toast.makeText(ctx, "手机没有可用的语音引擎", Toast.LENGTH_LONG).show()
                pending?.let { p -> if (ok) engine?.speak(p, TextToSpeech.QUEUE_FLUSH, null, "w") }
                pending = null
            }
            return
        }
        if (ok) t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "w")
    }

    fun shutdown() { tts?.shutdown(); tts = null }
}

fun shareText(ctx: Context, subject: String, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(i, "导出笔记（可选微信、邮件、备忘录等）").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** 把一句英文翻成中文：复用临床试验模块的百度翻译设置和每月额度。返回 (译文, 错误提示)。 */
suspend fun translateSentence(ctx: Context, text: String): Pair<String?, String?> {
    if (!Translator.ready(ctx)) return null to "翻译还没开通：到「临床试验 › 翻译设置」填写百度翻译的 APP ID 和密钥（和临床试验共用每月额度）"
    Translator.cached(ctx, text)?.let { return it to null }
    withContext(Dispatchers.IO) { Translator.translateMissing(ctx, listOf(text)) }
    return Translator.cached(ctx, text)?.let { it to null } ?: (null to (Translator.lastError(ctx) ?: "翻译失败，请稍后再试"))
}

@Composable
private fun Btn(text: String, on: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 42.dp).background(if (on) C.GoldBg else Color.Transparent, RoundedCornerShape(10.dp))
            .border(1.dp, if (on) C.GoldBg else C.Line, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) C.Gold else C.Text, fontSize = 13.sp) }
}

/** 长按单词/句子弹出的卡片：释义、发音、加生词本、翻译整句、划线。 */
@Composable
fun WordCard(
    pick: Pick, bookTitle: String, tts: Speaker,
    onClose: () -> Unit, onAddWord: (Dict.Entry) -> Unit, onHighlight: (translation: String?) -> Unit, onListenHere: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var entry by remember(pick) { mutableStateOf<Dict.Entry?>(null) }
    var looked by remember(pick) { mutableStateOf(false) }
    var added by remember(pick) { mutableStateOf(false) }
    var tr by remember(pick) { mutableStateOf<String?>(null) }
    var trErr by remember(pick) { mutableStateOf<String?>(null) }
    var trBusy by remember(pick) { mutableStateOf(false) }
    LaunchedEffect(pick) {
        if (pick.word.isNotBlank()) {
            entry = withContext(Dispatchers.IO) { runCatching { Dict.lookup(ctx, pick.word) }.getOrNull() }
            added = entry?.let { Notes.hasWord(ctx, it.word) } ?: false
        }
        looked = true
    }
    val atTop = pick.y > 0.55
    Column(Modifier.fillMaxSize().clickable(onClick = onClose), verticalArrangement = if (atTop) Arrangement.Top else Arrangement.Bottom) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).background(C.Surface, RoundedCornerShape(18.dp)).clickable(enabled = true, onClick = {})
                .then(if (atTop) Modifier else Modifier.navigationBarsPadding()).padding(16.dp).heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val e = entry
            if (pick.word.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e?.word ?: pick.word, color = C.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    if (!e?.phonetic.isNullOrBlank()) Text("  /${e!!.phonetic}/", color = C.Muted, fontSize = 13.sp)
                    Box(Modifier.weight(1f))
                    Btn("发音") { tts.speak(e?.word ?: pick.word) }
                }
                when {
                    !looked -> Text("查询中…", color = C.Muted, fontSize = 13.sp)
                    e == null -> Text("离线词典里没有「${pick.word}」（人名、地名或少见词常查不到）", color = C.Muted, fontSize = 13.sp)
                    else -> {
                        if (e.lemmaOf != null && !e.lemmaOf.equals(pick.word, true) && e.word.equals(e.lemmaOf, true)) Text("原形 ${e.word} · 文中为 ${pick.word}", color = C.Muted, fontSize = 12.sp)
                        else if (e.lemmaOf != null && !e.word.equals(e.lemmaOf, true)) Text("也可查原形 ${e.lemmaOf}", color = C.Muted, fontSize = 12.sp)
                        Text(ReadLogic.shortMeaning(e.meaning), color = C.Text, fontSize = 15.sp, lineHeight = 22.sp)
                        Dict.tagText(e.tags).takeIf { it.isNotEmpty() }?.let { Text(it, color = C.Muted, fontSize = 11.sp) }
                    }
                }
            }
            if (pick.sentence.isNotBlank()) {
                Text(pick.sentence, color = C.Muted, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = FontFamily.Serif, maxLines = 6)
                tr?.let { Text(it, color = C.Text, fontSize = 14.sp, lineHeight = 20.sp) }
                trErr?.let { Text(it, color = C.Warn, fontSize = 12.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (e != null) Btn(if (added) "✓ 已在生词本" else "加生词本", on = added, modifier = Modifier.weight(1f)) {
                    if (added) { Notes.removeWord(ctx, e.word); added = false } else { onAddWord(e); added = true }
                }
                val cjk = ReadLogic.isCjk(pick.sentence)
                if (!cjk && pick.sentence.isNotBlank()) Btn(if (trBusy) "翻译中…" else "翻译整句", modifier = Modifier.weight(1f)) {
                    if (trBusy || tr != null) return@Btn
                    trBusy = true; trErr = null
                    scope.launch { val (t, err) = translateSentence(ctx, pick.sentence); tr = t; trErr = err; trBusy = false }
                }
                if (pick.sentence.isNotBlank()) Btn("划线", modifier = Modifier.weight(1f)) { onHighlight(tr) }
            }
            if (pick.sentence.isNotBlank()) Btn("从这句开始听", modifier = Modifier.fillMaxWidth()) { onListenHere() }
            Text("词典：ECDICT（离线）" + if (pick.sentence.isNotBlank()) " · 翻译：百度翻译（与临床试验共用额度）" else "", color = C.Muted, fontSize = 10.sp)
        }
    }
}

/** 点到已划线的句子：看译文、写笔记、删除划线。 */
@Composable
fun HighlightCard(h: Notes.Highlight, onClose: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember(h.id) { mutableStateOf(h.note) }
    var tr by remember(h.id) { mutableStateOf(h.translation) }
    var err by remember(h.id) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().clickable(onClick = onClose), verticalArrangement = Arrangement.Bottom) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).background(C.Surface, RoundedCornerShape(18.dp)).clickable(onClick = {}).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("划线", color = C.Muted, fontSize = 12.sp)
            Text(h.text, color = C.Text, fontSize = 14.sp, fontFamily = FontFamily.Serif, lineHeight = 20.sp, maxLines = 8)
            tr?.let { Text(it, color = C.Text, fontSize = 14.sp) }
            err?.let { Text(it, color = C.Warn, fontSize = 12.sp) }
            OutlinedTextField(note, { note = it }, label = { Text("笔记（可不填）") }, modifier = Modifier.fillMaxWidth(), maxLines = 5)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("保存", on = true, modifier = Modifier.weight(1f)) { Notes.updateHighlight(ctx, h.id) { it.copy(note = note.trim(), translation = tr) }; onClose() }
                if (tr == null && !ReadLogic.isCjk(h.text)) Btn("翻译", modifier = Modifier.weight(1f)) {
                    scope.launch { val (t, e) = translateSentence(ctx, h.text); tr = t; err = e; if (t != null) Notes.updateHighlight(ctx, h.id) { it.copy(translation = t) } }
                }
                Btn("删除划线", modifier = Modifier.weight(1f)) { onDelete() }
            }
        }
    }
}

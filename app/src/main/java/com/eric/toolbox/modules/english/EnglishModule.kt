package com.eric.toolbox.modules.english

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.Category
import com.eric.toolbox.core.ToolModule
import com.eric.toolbox.modules.ai.AiQuick
import com.eric.toolbox.modules.read.Dict
import com.eric.toolbox.modules.read.Listen
import com.eric.toolbox.modules.read.ReadLogic
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.Caption
import com.eric.toolbox.ui.DotMatrix
import com.eric.toolbox.ui.I18n
import com.eric.toolbox.ui.Meter
import com.eric.toolbox.ui.NumStyle
import com.eric.toolbox.ui.Tile
import com.eric.toolbox.ui.press
import com.eric.toolbox.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** 学英语（V2.0 第一批：查单词、背单词、词书）。底部导航第二格。 */
object EnglishModule : ToolModule {
    override val id = "english"
    override val name = "学英语"
    override val category = Category.TOOL
    override val icon: ImageVector = Icons.Outlined.Translate

    @Composable
    override fun HomeSummary() { Text(tr("背单词 · 查词", "Words · Lookup"), color = C.Muted, fontSize = 13.sp) }

    @Composable
    override fun Screen(onBack: () -> Unit) = EnglishRoot(onBack = onBack, isTab = false)
}

private fun onAcc() = if (C.palette.isLight) Color.White else Color(0xFF121110)

/** 英语朗读（美音 / 英音），用手机系统语音引擎。 */
private class EnVoice(ctx: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val app = ctx.applicationContext
    private var pending: Pair<String, Locale>? = null
    init {
        tts = TextToSpeech(app) { st -> ready = st == TextToSpeech.SUCCESS; pending?.let { (t, l) -> speak(t, l) }; pending = null }
    }
    fun speak(text: String, loc: Locale = Locale.US, rate: Float = 1f) {
        if (!ready) { pending = text to loc; return }
        if (Listen.playing) Listen.pause()
        val t = tts ?: return
        if (t.setLanguage(loc) < 0) t.setLanguage(Locale.US)
        t.setSpeechRate(rate)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "en")
    }
    fun shutdown() { tts?.shutdown(); tts = null }
}

@Composable
fun EnglishRoot(onBack: () -> Unit, isTab: Boolean) {
    var page by remember { mutableStateOf("home") }
    val voice = run { val ctx = LocalContext.current; remember { EnVoice(ctx) } }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }
    BackHandler(enabled = page != "home" || !isTab) { if (page != "home") page = "home" else onBack() }
    AnimatedContent(page, label = "en") { p ->
        when (p) {
            "study" -> StudyScreen(voice) { page = "home" }
            "lookup" -> LookupScreen(voice) { page = "home" }
            "books" -> BooksScreen { page = "home" }
            else -> EnHome(isTab, onBack, go = { page = it })
        }
    }
}

@Composable
private fun Header(title: String, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) Box(Modifier.size(44.dp).press(onClick = onBack), contentAlignment = Alignment.Center) { Text("‹", color = C.Muted, fontSize = 28.sp) }
        else Spacer(Modifier.width(12.dp))
        Text(title, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Btn(text: String, primary: Boolean = false, modifier: Modifier = Modifier, sub: String? = null, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 48.dp).press(onClick = onClick).background(if (primary) C.Gold else Color.Transparent, RoundedCornerShape(14.dp))
            .border(1.dp, if (primary) C.Gold else C.Line, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(text, color = if (primary) onAcc() else C.Text, fontSize = 15.sp, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal)
        if (sub != null) Text(sub, color = if (primary) onAcc().copy(alpha = 0.7f) else C.Muted, fontSize = 11.sp)
    }
}

// ======================= 首页 =======================

@Composable
private fun EnHome(isTab: Boolean, onBack: () -> Unit, go: (String) -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { tick++ }
    val queue = remember(tick) { EnglishStore.queue(ctx) }
    val done = remember(tick) { EnglishStore.doneToday(ctx) }
    val due = remember(tick) { EnglishStore.dueCount(ctx) }
    val newLeft = (EnglishStore.newLimit(ctx) - EnglishStore.newToday(ctx)).coerceAtLeast(0)
    val streak = remember(tick) { EnglishStore.streak(ctx) }
    val week = remember(tick) { EnglishStore.weekDays(ctx) }

    Column(Modifier.fillMaxSize()) {
        Header(tr("学英语", "English"), if (isTab) null else onBack) {
            Text(tr("连续 $streak 天", "$streak-day streak"), color = C.Muted, fontSize = 12.sp, style = NumStyle)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(strong = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Caption(tr("今天", "Today"), Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { on -> Box(Modifier.size(8.dp, 20.dp).background(if (on) C.Text else C.Line, RoundedCornerShape(4.dp))) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DotMatrix(done.toString(), dot = 7.dp)
                    Text(tr("个已练", "done"), color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat(tr("到期复习", "Due"), due.toString())
                    Stat(tr("今天新词", "New"), newLeft.toString())
                    Stat(tr("待练", "Queue"), queue.size.toString())
                }
                Spacer(Modifier.height(12.dp))
                Btn(if (queue.isEmpty()) tr("今天练完了 ✓", "All done for today ✓") else tr("开始练习", "Start"), primary = queue.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    if (queue.isNotEmpty()) go("study")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Entry(Icons.Outlined.Search, tr("查单词", "Lookup"), tr("离线秒查 · 专业释义", "Offline · domain sense"), Modifier.weight(1f)) { go("lookup") }
                Entry(Icons.Outlined.Style, tr("词书", "Word books"), tr("工作 · 口语 · 生词", "Work · Speaking · Mine"), Modifier.weight(1f)) { go("books") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Entry(Icons.Outlined.Mic, tr("练口语", "Speaking"), tr("下一版：AI 情景对练", "Next: AI role-play"), Modifier.weight(1f), dim = true) {
                    Toast.makeText(ctx, tr("口语对练在下一版加入", "Coming in the next version"), Toast.LENGTH_SHORT).show()
                }
                Entry(Icons.AutoMirrored.Outlined.MenuBook, tr("学发音", "Pronunciation"), tr("下一版：跟读对比", "Next: shadowing"), Modifier.weight(1f), dim = true) {
                    Toast.makeText(ctx, tr("发音练习在下一版加入", "Coming in the next version"), Toast.LENGTH_SHORT).show()
                }
            }
            Caption(tr("正在学的词书", "Active books"), Modifier.padding(start = 4.dp, top = 6.dp))
            EnglishStore.books(ctx).forEach { b ->
                val size = remember(tick, b) { EnglishStore.bookSize(ctx, b) }
                val learned = remember(tick, b) { EnglishStore.learnedCount(ctx, b) }
                Tile(onClick = { go("books") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bookName(b), color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("$learned / $size", color = C.Muted, fontSize = 12.sp, style = NumStyle)
                    }
                    Spacer(Modifier.height(8.dp))
                    Meter(if (size == 0) 0f else learned.toFloat() / size)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Stat(k: String, v: String) {
    Column { Text(v, color = C.Text, fontSize = 18.sp, style = NumStyle); Caption(k) }
}

@Composable
private fun Entry(icon: ImageVector, title: String, sub: String, modifier: Modifier, dim: Boolean = false, onClick: () -> Unit) {
    Tile(modifier.heightIn(min = 104.dp), onClick = onClick) {
        Icon(icon, null, tint = if (dim) C.Muted else C.Text, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = if (dim) C.Muted else C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Caption(sub)
    }
}

fun bookName(b: String): String = when (b) {
    EnglishBooks.IP -> tr("生物医药知识产权", "Biopharma IP")
    EnglishBooks.DAILY -> tr("日常口语高频表达", "Everyday phrases")
    EnglishBooks.MINE -> tr("我的生词本", "My words")
    else -> EnglishStore.EXAMS[b]?.let { tr(it.first, it.second) } ?: b
}

// ======================= 背单词 =======================

@Composable
private fun StudyScreen(voice: EnVoice, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val queue = remember { EnglishStore.queue(ctx).toMutableList() }
    var idx by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }
    val total = queue.size

    if (idx >= queue.size) {
        Column(Modifier.fillMaxSize()) {
            Header(tr("背单词", "Review"), onDone)
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                DotMatrix(EnglishStore.doneToday(ctx).toString(), dot = 9.dp, color = C.Gold)
                Spacer(Modifier.height(12.dp))
                Text(tr("今天练完了", "Done for today"), color = C.Text, fontSize = 18.sp)
                Caption(tr("明天会按记忆曲线安排复习", "Reviews are scheduled by your memory curve"))
                Spacer(Modifier.height(20.dp))
                Btn(tr("返回", "Back"), primary = true, modifier = Modifier.width(160.dp), onClick = onDone)
            }
        }
        return
    }
    val card = queue[idx]
    val item = remember(card.id) { EnglishStore.item(ctx, card.book, card.key) }
    if (item == null) { LaunchedEffect(card.id) { idx++ }; return }
    // 练法：口语表达 → 看中文想英文；单词 → 新词看词想义，复习轮换 听音选义 / 例句填空 / 拼写
    val mode = remember(card.id) {
        when {
            item.phrase -> "phrase"
            card.isNew -> "recall"
            else -> listOf("recall", "choice", "cloze", "spell").let { l ->
                val m = l[(card.reps + card.key.length) % l.size]
                if (m == "cloze" && EnglishLogic.cloze(item.ex, item.en) == null) "recall" else if (m == "spell" && item.en.contains(' ')) "choice" else m
            }
        }
    }
    val choices = remember(card.id) {
        if (mode != "choice") emptyList() else {
            val pool = (EnglishBooks.ip + EnglishBooks.daily).map { EnglishLogic.shortZh(it.zh) }
            (EnglishLogic.distractors(EnglishLogic.shortZh(item.zh), pool, card.id.hashCode()) + EnglishLogic.shortZh(item.zh)).shuffled(java.util.Random(card.id.hashCode().toLong()))
        }
    }
    LaunchedEffect(card.id) { revealed = false; input = ""; picked = null; if (mode == "choice") voice.speak(item.en) }
    fun rate(r: Int) {
        val now = System.currentTimeMillis()
        val next = EnglishLogic.schedule(card, r, now)
        EnglishStore.put(ctx, next)
        if (r == EnglishLogic.AGAIN) queue.add(next) // 忘了：本轮末尾再来一次
        idx++
    }

    Column(Modifier.fillMaxSize()) {
        Header(tr("背单词", "Review"), onDone) { Text("${idx + 1} / ${queue.size}", color = C.Muted, fontSize = 12.sp, style = NumStyle) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val segs = total.coerceAtMost(20)
            repeat(segs) { i -> Box(Modifier.weight(1f).height(3.dp).background(if (i < idx * segs / total.coerceAtLeast(1)) C.Text else C.Line, RoundedCornerShape(2.dp))) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Caption(bookName(card.book) + if (card.isNew) tr(" · 新词", " · new") else "")
            Tile(strong = true) {
                when (mode) {
                    "phrase" -> {
                        Caption(tr("这句英文怎么说？", "How do you say it in English?"))
                        Text(item.zh, color = C.Text, fontSize = 20.sp, lineHeight = 28.sp)
                        if (revealed) { Spacer(Modifier.height(12.dp)); EnText(item.en, 22, voice) }
                    }
                    "choice" -> {
                        Caption(tr("听发音，选意思", "Listen and pick the meaning"))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(56.dp).background(C.GoldBg, CircleShape).press { voice.speak(item.en) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.VolumeUp, null, tint = C.Gold)
                            }
                            if (revealed) Text(item.en, color = C.Text, fontSize = 24.sp, fontFamily = FontFamily.Serif)
                        }
                    }
                    "cloze" -> {
                        Caption(tr("补全例句", "Fill in the blank"))
                        Text(EnglishLogic.cloze(item.ex, item.en) ?: item.ex, color = C.Text, fontSize = 17.sp, lineHeight = 26.sp, fontFamily = FontFamily.Serif)
                        if (item.exZh.isNotBlank()) Caption(item.exZh)
                    }
                    "spell" -> {
                        Caption(tr("看中文，拼出单词", "Spell the word"))
                        Text(item.zh, color = C.Text, fontSize = 18.sp, lineHeight = 26.sp)
                    }
                    else -> EnText(item.en, 32, voice, item.phonetic)
                }
                if (mode == "cloze" || mode == "spell") {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(input, { input = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(tr("输入英文", "Type here")) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { revealed = true }))
                }
                if (mode == "choice") {
                    Spacer(Modifier.height(10.dp))
                    choices.forEach { c ->
                        val right = c == EnglishLogic.shortZh(item.zh)
                        val border = when { picked == null -> C.Line; right -> C.Ok; c == picked -> C.Warn; else -> C.Line }
                        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 46.dp).border(1.dp, border, RoundedCornerShape(12.dp))
                            .press(enabled = picked == null) { picked = c; revealed = true }.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                            Text(c, color = C.Text, fontSize = 15.sp)
                        }
                    }
                }
                if (revealed && mode != "phrase") {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
                    Spacer(Modifier.height(12.dp))
                    if (mode != "recall") EnText(item.en, 22, voice, item.phonetic)
                    if (mode == "spell" || mode == "cloze") {
                        val ok = EnglishLogic.spellOk(input, item.en)
                        Text(if (ok) tr("✓ 拼对了", "✓ Correct") else tr("✕ 你写的是：$input", "✕ You typed: $input"), color = if (ok) C.Ok else C.Warn, fontSize = 13.sp)
                    }
                    Text(item.zh, color = C.Text, fontSize = 16.sp, lineHeight = 24.sp)
                }
                if (revealed) {
                    if (item.ex.isNotBlank() && mode != "cloze") {
                        Spacer(Modifier.height(10.dp))
                        Text(item.ex, color = C.Text, fontSize = 15.sp, lineHeight = 23.sp, fontFamily = FontFamily.Serif, modifier = Modifier.press { voice.speak(item.ex, rate = 0.9f) })
                        if (item.exZh.isNotBlank()) Caption(item.exZh)
                    }
                    if (item.note.isNotBlank()) { Spacer(Modifier.height(6.dp)); Caption(item.note, color = C.Gold) }
                }
            }
            if (!revealed) Btn(tr("看答案", "Show answer"), primary = true, modifier = Modifier.fillMaxWidth()) { revealed = true }
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val now = System.currentTimeMillis()
                Btn(tr("忘了", "Again"), sub = EnglishLogic.preview(card, EnglishLogic.AGAIN, now, I18n.en), modifier = Modifier.weight(1f)) { rate(EnglishLogic.AGAIN) }
                Btn(tr("模糊", "Hard"), sub = EnglishLogic.preview(card, EnglishLogic.HARD, now, I18n.en), modifier = Modifier.weight(1f)) { rate(EnglishLogic.HARD) }
                Btn(tr("记得", "Good"), primary = true, sub = EnglishLogic.preview(card, EnglishLogic.GOOD, now, I18n.en), modifier = Modifier.weight(1f)) { rate(EnglishLogic.GOOD) }
            }
            AiExplain(item.en, item.zh)
        }
    }
}

@Composable
private fun EnText(text: String, size: Int, voice: EnVoice, phonetic: String = "") {
    Text(text, color = C.Text, fontSize = size.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, lineHeight = (size * 1.3).sp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        if (phonetic.isNotBlank()) Text("/$phonetic/", color = C.Muted, fontSize = 13.sp)
        Pill(tr("▶ 美", "▶ US")) { voice.speak(text, Locale.US) }
        Pill(tr("▶ 英", "▶ UK")) { voice.speak(text, Locale.UK) }
        Pill(tr("慢速", "Slow")) { voice.speak(text, Locale.US, 0.7f) }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Text(text, color = C.Text, fontSize = 12.sp, modifier = Modifier.border(1.dp, C.Line, RoundedCornerShape(50)).press(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp))
}

/** 「AI 讲讲」：点了才调用 DeepSeek（用 AI 管家里的密钥，计入同一个月花费上限）。 */
@Composable
private fun AiExplain(word: String, zh: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember(word) { mutableStateOf<String?>(null) }
    var busy by remember(word) { mutableStateOf(false) }
    Tile {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("AI 讲讲", "Ask AI"), color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (text == null) Pill(if (busy) tr("请稍等…", "Thinking…") else tr("3 个例句 + 易混词", "Examples + look-alikes")) {
                if (busy) return@Pill
                busy = true
                scope.launch {
                    val r = AiQuick.ask(ctx.applicationContext,
                        "你是英语老师，学生是生物医药领域的知识产权顾问。回答用中文讲解，例句用英文并附中文。简洁，不超过 180 字。",
                        "讲讲「$word」（$zh）：给 3 个例句（优先专利、临床或商务场景），列出 1-2 个易混词并说明区别。")
                    busy = false
                    text = r.getOrElse { tr("出错了：", "Error: ") + it.message }
                }
            }
        }
        text?.let { Spacer(Modifier.height(8.dp)); Text(it, color = C.Text, fontSize = 14.sp, lineHeight = 22.sp) }
    }
}

// ======================= 查单词 =======================

@Composable
private fun LookupScreen(voice: EnVoice, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var entry by remember { mutableStateOf<Dict.Entry?>(null) }
    var term by remember { mutableStateOf<Term?>(null) }
    var searched by remember { mutableStateOf("") }
    var added by remember { mutableStateOf(false) }
    val recent = remember { mutableStateOf(EnglishStore.prefs(ctx).getString("recent_lookup", "")!!.split('|').filter { it.isNotBlank() }) }
    fun search(w: String) {
        val word = w.trim(); if (word.isEmpty()) return
        searched = word
        scope.launch {
            entry = withContext(Dispatchers.IO) { runCatching { Dict.lookup(ctx, word) }.getOrNull() }
            term = EnglishBooks.findIp(word) ?: entry?.let { EnglishBooks.findIp(it.word) }
            added = entry?.let { com.eric.toolbox.modules.read.Notes.hasWord(ctx, it.word) } ?: false
            val r = (listOf(word.lowercase()) + recent.value.filter { it != word.lowercase() }).take(12)
            recent.value = r; EnglishStore.prefs(ctx).edit().putString("recent_lookup", r.joinToString("|")).apply()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Header(tr("查单词", "Lookup"), onBack) { Caption(tr("离线 58,226 词", "58,226 words offline")) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text(tr("输入英文单词", "Type an English word")) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = C.Muted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search(q) }),
                shape = RoundedCornerShape(16.dp))
            val e = entry
            if (searched.isNotEmpty() && e == null && term == null) Caption(tr("离线词典里没有「$searched」，可以试试翻译模块。", "\"$searched\" isn't in the offline dictionary."))
            if (e != null || term != null) Tile(strong = true) {
                EnText(e?.word ?: term!!.en, 30, voice, e?.phonetic.orEmpty())
                if (e != null) {
                    if (e.lemmaOf != null && !e.lemmaOf.equals(searched, true)) Caption(tr("「$searched」是 ${e.lemmaOf} 的变形", "\"$searched\" is a form of ${e.lemmaOf}"))
                    Spacer(Modifier.height(10.dp))
                    Text(ReadLogic.shortMeaning(e.meaning, 6), color = C.Text, fontSize = 15.sp, lineHeight = 23.sp)
                    val tags = Dict.tagText(e.tags)
                    if (tags.isNotBlank()) Caption(tags)
                    Caption(tr("以上来自离线词典 ECDICT", "From the offline ECDICT dictionary"))
                }
                term?.let { t ->
                    Spacer(Modifier.height(10.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line)); Spacer(Modifier.height(10.dp))
                    Text(tr("专业语境", "In IP / biopharma"), color = C.Gold, fontSize = 12.sp)
                    Text(t.zh, color = C.Text, fontSize = 15.sp, lineHeight = 23.sp)
                    Text(t.ex, color = C.Text, fontSize = 14.sp, lineHeight = 22.sp, fontFamily = FontFamily.Serif, modifier = Modifier.padding(top = 6.dp).press { voice.speak(t.ex, rate = 0.9f) })
                    Caption(t.exZh)
                    if (t.note.isNotBlank()) Caption(t.note, color = C.Gold)
                }
                Spacer(Modifier.height(12.dp))
                Btn(if (added) tr("✓ 已在生词本，会安排背诵", "✓ In My words") else tr("＋ 加入背词", "＋ Add to review"), primary = !added, modifier = Modifier.fillMaxWidth()) {
                    if (!added && e != null) { EnglishStore.addToMine(ctx, e.word, e.phonetic, ReadLogic.shortMeaning(term?.zh ?: e.meaning, 3), term?.ex.orEmpty()); added = true }
                }
            }
            if (e != null || term != null) AiExplain(e?.word ?: term!!.en, term?.zh ?: ReadLogic.shortMeaning(e!!.meaning, 1))
            if (recent.value.isNotEmpty()) {
                Caption(tr("最近查过", "Recent"), Modifier.padding(start = 4.dp, top = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    recent.value.forEach { w -> Pill(w) { q = w; search(w) } }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ======================= 词书 =======================

@Composable
private fun BooksScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var books by remember { mutableStateOf(EnglishStore.books(ctx)) }
    var limit by remember { mutableIntStateOf(EnglishStore.newLimit(ctx)) }
    fun toggle(b: String) { books = if (b in books) books - b else books + b; EnglishStore.setBooks(ctx, books) }
    Column(Modifier.fillMaxSize()) {
        Header(tr("词书", "Word books"), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Caption(tr("选中的词书会交替出新词。专业词书给的是专利 / 生物医药语境里的意思。", "New words rotate across the selected books."), Modifier.padding(horizontal = 4.dp))
            listOf(EnglishBooks.IP, EnglishBooks.DAILY, EnglishBooks.MINE).forEach { b -> BookRow(b, b in books, ctx) { toggle(b) } }
            Caption(tr("考试词书（来自离线词典标签）", "Exam lists (from the offline dictionary)"), Modifier.padding(start = 4.dp, top = 8.dp))
            EnglishStore.EXAMS.keys.forEach { b -> BookRow(b, b in books, ctx) { toggle(b) } }
            Caption(tr("每天新词", "New words per day"), Modifier.padding(start = 4.dp, top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 20, 30).forEach { n ->
                    val on = n == limit
                    Text("$n", color = if (on) onAcc() else C.Text, fontSize = 14.sp, style = NumStyle,
                        modifier = Modifier.background(if (on) C.Gold else C.Surface, RoundedCornerShape(10.dp)).press { limit = n; EnglishStore.setNewLimit(ctx, n) }.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BookRow(b: String, on: Boolean, ctx: Context, onClick: () -> Unit) {
    val size = remember(b) { EnglishStore.bookSize(ctx, b) }
    val learned = remember(b, on) { EnglishStore.learnedCount(ctx, b) }
    Tile(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(bookName(b), color = C.Text, fontSize = 15.sp)
                Caption(tr("已学 $learned / 共 $size", "$learned / $size learned"))
            }
            Box(Modifier.size(22.dp).border(1.5.dp, if (on) C.Gold else C.Line, CircleShape).padding(5.dp)) { if (on) Box(Modifier.fillMaxSize().background(C.Gold, CircleShape)) }
        }
    }
}

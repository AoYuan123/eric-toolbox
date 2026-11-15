package com.eric.toolbox.modules.english.ielts

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.eric.toolbox.core.VoiceInput
import com.eric.toolbox.modules.english.Btn
import com.eric.toolbox.modules.english.EnVoice
import com.eric.toolbox.modules.english.EnglishLogic
import com.eric.toolbox.modules.english.EnglishStore
import com.eric.toolbox.modules.english.Header
import com.eric.toolbox.modules.english.Pill
import com.eric.toolbox.modules.english.onAcc
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.Caption
import com.eric.toolbox.ui.Meter
import com.eric.toolbox.ui.Tile
import com.eric.toolbox.ui.press
import com.eric.toolbox.ui.tr
import java.util.Locale

/**
 * 雅思场景学习：场景库 + 场景详情（对话 / 词汇 / 语块 / 练习）。
 *
 * 设计取舍：
 * - 内容默认英文在前、中文在后，可以在顶栏一键关掉中文。学口语要的是「先看到英文」，
 *   但初学一遍全英文会看不懂，所以默认都露出来，熟了再关。
 * - 场景词不是另建一套背词系统，直接进 EnglishStore 的 SM-2 队列（词书 id = ielts_scene），
 *   第二天背单词就考这个场景的词，闭环。
 * - 跟读只做识别比对 + 命中率，不上传录音、不调 AI，免费且离线。
 */
@Composable
internal fun IeltsRoot(voice: EnVoice, onBack: () -> Unit) {
    val stack = remember { mutableStateListOf("home") }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() }
    BackHandler { back() }
    val r = stack.last()
    if (r.startsWith("s:")) IeltsSceneScreen(r.removePrefix("s:"), voice) { back() }
    else IeltsHome { stack.add(it) }
}

// ======================= 场景库 =======================

@Composable
private fun IeltsHome(go: (String) -> Unit) {
    val ctx = LocalContext.current
    var cat by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    val cats = remember { listOf(Cat.LIFE, Cat.WORK, Cat.ACADEMIC) }
    val onIds = remember(tick) { IeltsStore.on(ctx) }
    val readIds = remember(tick) { IeltsStore.read(ctx) }
    val inBook = remember(tick) { IeltsData.BOOK in EnglishStore.books(ctx) }
    val learned = remember(tick) { EnglishStore.learnedCount(ctx, IeltsData.BOOK) }
    val list = remember(cat) { IeltsData.byCat(cats[cat]) }

    Column(Modifier.fillMaxSize()) {
        Header(tr("雅思场景", "IELTS by scenario"), null) { Caption("${IeltsData.scenes.size} " + tr("个场景", "scenarios")) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 总览：一句话说清这套东西是什么、进度在哪
            Tile(strong = true) {
                Text(tr("按场景学，不按词表背", "Learn by situation, not by word list"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Caption(tr("${IeltsData.scenes.size} 个场景 · ${IeltsData.words.size} 个场景词 · ${IeltsData.scenes.sumOf { it.chunks.size }} 条功能语块",
                    "${IeltsData.scenes.size} scenarios · ${IeltsData.words.size} words · ${IeltsData.scenes.sumOf { it.chunks.size }} chunks"))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Stat(tr("已读场景", "Read"), "${readIds.size}/${IeltsData.scenes.size}")
                    Stat(tr("场景词入队", "In review"), "${if (inBook) onIds.size else 0}/${IeltsData.scenes.size}")
                    Stat(tr("已掌握", "Learned"), "$learned/${IeltsData.words.size}")
                }
                Spacer(Modifier.height(10.dp))
                Meter(if (IeltsData.words.isEmpty()) 0f else learned.toFloat() / IeltsData.words.size)
                Spacer(Modifier.height(6.dp))
                Caption(tr("场景词走的是同一套记忆曲线：进了队列，第二天背单词就会考。",
                    "Scenario words join the same spaced-repetition queue."))
            }
            if (IeltsData.BOOK !in EnglishStore.books(ctx) || onIds.isEmpty()) {
                Btn(tr("把全部场景词加入每日背词", "Add all scenario words to daily review"), primary = true, modifier = Modifier.fillMaxWidth()) {
                    EnglishStore.setBooks(ctx, (EnglishStore.books(ctx) + IeltsData.BOOK).distinct())
                    IeltsStore.setOn(ctx, IeltsData.scenes.map { it.id }.toSet())
                    tick++
                }
            }
            // 分类切换
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEachIndexed { k, c ->
                    val on = k == cat
                    Text(tr(c.zh, c.en), color = if (on) onAcc() else C.Text, fontSize = 14.sp,
                        modifier = Modifier.background(if (on) C.Gold else C.Surface, RoundedCornerShape(12.dp)).press { cat = k }.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            list.forEach { s ->
                SceneTile(s, readIds = readIds, on = s.id in onIds, inBook = inBook) { go("s:${s.id}") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Stat(k: String, v: String) {
    Column { Text(v, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium); Caption(k) }
}

@Composable
private fun SceneTile(s: Scene, readIds: Set<String>, on: Boolean, inBook: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val said = remember(s.id) { IeltsStore.shadow(ctx, s.id) }
    Tile(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(s.zh, s.en), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Caption(s.en)
            }
            Text("›", color = C.Muted, fontSize = 20.sp)
        }
        Spacer(Modifier.height(4.dp))
        Caption(tr("目标：", "Goal: ") + s.goal)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Mark(tr("读", "Read"), s.id in readIds, "")
            Mark(tr("词", "Words"), inBook && on, if (inBook && on) "✓" else "")
            Mark(tr("说", "Speak"), said > 0, if (said > 0) "$said%" else "")
        }
    }
}

/** 进度小标：左边一个小圆点，右边文字（可选后缀，比如命中率）。 */
@Composable
private fun Mark(label: String, ok: Boolean, suffix: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(if (ok) C.Gold else C.Line, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(5.dp))
        Text(label + if (suffix.isBlank()) "" else " $suffix", color = if (ok) C.Text else C.Muted, fontSize = 11.sp)
    }
}

// ======================= 场景详情 =======================

private val TABS_ZH = listOf("对话", "词汇", "语块", "练习")
private val TABS_EN = listOf("Dialogue", "Words", "Chunks", "Practice")

@Composable
private fun IeltsSceneScreen(id: String, voice: EnVoice, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = IeltsData.scene(id)
    // 场景 id 传错（比如改名后旧的返回栈）：直接退回去，不要在 composition 里改状态
    if (s == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }; return }
    var tab by remember { mutableIntStateOf(0) }
    var zhOn by remember { mutableStateOf(true) }
    var inReview by remember(s.id) { mutableStateOf(IeltsStore.inReview(ctx, s.id)) }
    androidx.compose.runtime.LaunchedEffect(s.id) { IeltsStore.markRead(ctx, s.id) }

    Column(Modifier.fillMaxSize()) {
        Header(tr(s.zh, s.en), onBack) {
            Pill(if (zhOn) "中 + EN" else "EN only") { zhOn = !zhOn }
        }
        Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TABS_ZH.forEachIndexed { k, zh ->
                val on = k == tab
                Text(tr(zh, TABS_EN[k]), color = if (on) onAcc() else C.Text, fontSize = 14.sp,
                    modifier = Modifier.background(if (on) C.Gold else C.Surface, RoundedCornerShape(12.dp)).press { tab = k }.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Caption(tr("目标：", "Goal: ") + s.goal, Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            if (tab == 1) ReviewButton(s, inReview) { inReview = it }
            when (tab) {
                0 -> LinesTab(s, voice, zhOn)
                1 -> WordsTab(s, voice, zhOn)
                2 -> ChunksTab(s, voice, zhOn)
                else -> PracticeTab(s, voice, zhOn)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 场景词进/出背词队列。词书没选的时候顺手把词书选上，否则按钮点了没反应。 */
@Composable
private fun ReviewButton(s: Scene, on: Boolean, changed: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    Btn(
        if (on) tr("这个场景的 ${s.words.size} 个词已进背词 ✓（点一下移出）", "${s.words.size} words in review ✓ (tap to remove)")
        else tr("＋ 这个场景的 ${s.words.size} 个词加入背词", "＋ Add these ${s.words.size} words to review"),
        primary = !on, modifier = Modifier.fillMaxWidth(),
    ) {
        changed(if (on) IeltsStore.removeFromReview(ctx, s.id) else IeltsStore.addToReview(ctx, s.id))
    }
}

// ---------------- 对话 ----------------

@Composable
private fun LinesTab(s: Scene, voice: EnVoice, zhOn: Boolean) {
    Caption(tr("A 是对方，B 是你。点整句朗读；有「地道」提示的句子是中式说法最容易出问题的地方。",
        "A is them, B is you. Tap a line to hear it."), Modifier.padding(horizontal = 4.dp))
    s.lines.forEachIndexed { i, l ->
        Tile {
            LineRow(tr("A", "A"), l.a, l.aZh, zhOn, voice = voice)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
            Spacer(Modifier.height(8.dp))
            LineRow(tr("B", "B"), l.b, l.bZh, zhOn, voice = voice, mine = true)
            if (zhOn && l.tip.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("▸ " + l.tip, color = C.Gold, fontSize = 12.sp, lineHeight = 18.sp)
            }
            if (i == s.lines.lastIndex && zhOn) {
                Spacer(Modifier.height(6.dp))
                Caption(tr("把 B 的句子连起来说一遍，就是这个场景的完整对话。", "Say all B lines in a row — that's the whole scene."))
            }
        }
    }
}

@Composable
private fun LineRow(tag: String, en: String, zh: String, zhOn: Boolean, voice: EnVoice, mine: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Text(tag, color = if (mine) C.Gold else C.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp).width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(en, color = C.Text, fontSize = 15.sp, lineHeight = 23.sp, fontFamily = FontFamily.Serif)
            if (zhOn && zh.isNotBlank()) Text(zh, color = C.Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
        Icon(Icons.Outlined.VolumeUp, tr("朗读", "Read aloud"), tint = C.Muted,
            modifier = Modifier.size(28.dp).press { voice.speak(en, rate = if (mine) 0.95f else 1f) }.padding(4.dp))
    }
}

// ---------------- 词汇 ----------------

@Composable
private fun WordsTab(s: Scene, voice: EnVoice, zhOn: Boolean) {
    Caption(tr("释义给的是这个场景里的意思和固定搭配，不是词典第一条。点卡片看例句。",
        "Senses and collocations as used in this scene. Tap a card for examples."), Modifier.padding(horizontal = 4.dp))
    s.words.forEach { w ->
        var open by remember(w.en) { mutableStateOf(false) }
        Tile(onClick = { open = !open }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.en, color = C.Text, fontSize = 17.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.VolumeUp, tr("朗读", "Read aloud"), tint = C.Muted,
                    modifier = Modifier.size(28.dp).press { voice.speak(w.en) }.padding(4.dp))
            }
            if (zhOn) { Text(w.zh, color = C.Text, fontSize = 13.sp, lineHeight = 19.sp); Spacer(Modifier.height(2.dp)) }
            Text(w.coll, color = C.Gold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            AnimatedVisibility(open) {
                Column {
                    Spacer(Modifier.height(6.dp))
                    Text(w.ex, color = C.Text, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = FontFamily.Serif,
                        modifier = Modifier.press { voice.speak(w.ex, rate = 0.9f) })
                    if (zhOn) Caption(w.exZh)
                    if (zhOn && w.tip.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("▸ " + w.tip, color = C.Warn, fontSize = 12.sp, lineHeight = 18.sp) }
                }
            }
            if (!open) { Spacer(Modifier.height(2.dp)); Caption(tr("点开看例句", "Tap for an example")) }
        }
    }
}

// ---------------- 语块 ----------------

@Composable
private fun ChunksTab(s: Scene, voice: EnVoice, zhOn: Boolean) {
    Caption(tr("语块是「一个意思一句话」，背下来能直接套。雅思连贯性分看的就是这些。",
        "Chunks are ready-made sentence frames — what coherence scores reward."), Modifier.padding(horizontal = 4.dp))
    s.chunks.forEach { c ->
        Tile(onClick = { voice.speak(c.en, rate = 0.9f) }) {
            if (zhOn) { Text(c.zh, color = C.Muted, fontSize = 12.sp); Spacer(Modifier.height(3.dp)) }
            Text(c.en, color = C.Text, fontSize = 16.sp, lineHeight = 23.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
            if (zhOn && c.use.isNotBlank()) { Spacer(Modifier.height(4.dp)); Caption(c.use) }
        }
    }
}

// ---------------- 练习 ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeTab(s: Scene, voice: EnVoice, zhOn: Boolean) {
    Caption(tr("先看题自己说一遍，再点开骨架对一下要点——不是背范文，是记住该说哪几块。",
        "Answer out loud first, then reveal the skeleton."), Modifier.padding(horizontal = 4.dp))
    s.asks.forEach { q ->
        var open by remember(q.q) { mutableStateOf(false) }
        Tile(strong = open, onClick = { open = !open }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("P${q.part}", color = C.Gold, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.border(1.dp, C.Gold.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(q.q, color = C.Text, fontSize = 15.sp, lineHeight = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
                    if (zhOn) Caption(q.qZh)
                }
                Icon(Icons.Outlined.VolumeUp, tr("朗读", "Read aloud"), tint = C.Muted,
                    modifier = Modifier.size(28.dp).press { voice.speak(q.q, rate = 0.95f) }.padding(4.dp))
            }
            AnimatedVisibility(open) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
                    Spacer(Modifier.height(8.dp))
                    Caption(tr("回答骨架", "Answer skeleton"), color = C.Gold)
                    WrapText(q.skeleton)
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    ShadowBox(s, voice)
}

/** 骨架用关键词 + 加号写，按空格切开展示太碎，这里整体当一个段落。 */
@Composable
private fun WrapText(t: String) {
    Text(t, color = C.Text, fontSize = 14.sp, lineHeight = 22.sp)
}

/**
 * 跟读：先把你的那句（B）读标准音，按一下开说，说完自动比对每个词有没有被识别到，
 * 给出命中率。免费、离线、录音不上传——只调系统语音识别，不调 AI。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShadowBox(s: Scene, voice: EnVoice) {
    val ctx = LocalContext.current
    var i by remember(s.id) { mutableIntStateOf(0) }
    var listening by remember { mutableStateOf(false) }
    var check by remember(s.id) { mutableStateOf<List<Pair<String, Boolean>>?>(null) }
    var heard by remember { mutableStateOf("") }
    var best by remember(s.id) { mutableIntStateOf(IeltsStore.shadow(ctx, s.id)) }
    val target = s.lines[i % s.lines.size].b
    val curTarget by rememberUpdatedState(target)
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val listener = remember {
        VoiceInput(ctx, "en-US", onPartial = { heard = it }, onFinal = { text ->
            listening = false
            heard = ""
            val c = EnglishLogic.wordCheck(curTarget, text)
            check = c
            val pct = if (c.isEmpty()) 0 else c.count { it.second } * 100 / c.size
            if (pct > best) best = pct
            IeltsStore.setShadow(ctx, s.id, pct)
        }, onError = { e ->
            listening = false
            val msg = when (e) {
                VoiceInput.NOTHING, android.speech.SpeechRecognizer.ERROR_NO_MATCH, android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> tr("没听清，再说一次", "Didn't catch that")
                android.speech.SpeechRecognizer.ERROR_CLIENT -> null
                else -> tr("识别出错（$e）", "Recognition error ($e)")
            }
            msg?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
        })
    }
    DisposableEffect(Unit) { onDispose { listener.destroy(); voice.stop() } }

    fun start() {
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!ok) { perm.launch(Manifest.permission.RECORD_AUDIO); return }
        voice.stop()
        check = null
        heard = ""
        if (listener.start(hold = false)) listening = true
        else Toast.makeText(ctx, tr("这台手机没有系统语音识别，可以先用输入法的语音输入", "No speech recognition on this phone"), Toast.LENGTH_LONG).show()
    }

    Tile(strong = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Caption(tr("跟读你这一句", "Shadow your line"), Modifier.weight(1f), color = C.Gold)
            if (best > 0) Caption(tr("最好 $best%", "Best $best%"))
        }
        Spacer(Modifier.height(6.dp))
        val c = check
        if (c == null) {
            Text(target, color = C.Text, fontSize = 17.sp, lineHeight = 25.sp, fontFamily = FontFamily.Serif)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                c.forEach { (w, ok) ->
                    Text(w, color = if (ok) C.Text else C.Warn, fontSize = 17.sp, fontFamily = FontFamily.Serif,
                        textDecoration = if (ok) null else TextDecoration.Underline)
                }
            }
            Spacer(Modifier.height(6.dp))
            val hit = c.count { it.second }
            Text(tr("命中 $hit / ${c.size} · ${hit * 100 / c.size}%", "$hit / ${c.size} words · ${hit * 100 / c.size}%"),
                color = if (hit * 100 / c.size >= 80) C.Ok else C.Warn, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (hit < c.size) Caption(tr("红色划线的词没被识别到，放慢一点重说这几个词。", "Red words weren't recognised — slow down and retry those."))
        }
        if (listening && heard.isNotBlank()) { Spacer(Modifier.height(6.dp)); Caption(tr("听到：", "Heard: ") + heard) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(tr("▶ 标准音", "▶ Model"), modifier = Modifier.weight(1f)) { voice.speak(target) }
            Btn(tr("▶ 慢速", "▶ Slow"), modifier = Modifier.weight(1f)) { voice.speak(target, Locale.US, 0.7f) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(if (listening) C.Warn else C.Gold, RoundedCornerShape(26.dp)).press { if (!listening) start() },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Mic, tr("跟读", "Speak"), tint = onAcc(), modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(if (listening) tr("正在听…说完自动结束", "Listening… speak now") else tr("点麦克风，把你的句子说一遍", "Tap the mic and say your line"),
                    color = if (listening) C.Warn else C.Text, fontSize = 13.sp)
                Caption(tr("只比对识别结果，不录音、不上传", "Recognition only — nothing recorded"))
            }
        }
        Spacer(Modifier.height(8.dp))
        Btn(tr("换一句", "Next line"), modifier = Modifier.fillMaxWidth()) {
            i++
            check = null
        }
    }
}

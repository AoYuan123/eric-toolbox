package com.eric.toolbox.modules.english.ielts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.modules.english.Btn
import com.eric.toolbox.modules.english.EnVoice
import com.eric.toolbox.modules.english.EnglishLogic
import com.eric.toolbox.modules.english.EnglishStore
import com.eric.toolbox.modules.english.Header
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.Caption
import com.eric.toolbox.ui.Meter
import com.eric.toolbox.ui.Tile
import com.eric.toolbox.ui.press
import com.eric.toolbox.ui.tr

/**
 * 词库 + 单元学习 + 学后测验 + 我的（错词本 / 持久度）。
 *
 * 参照成熟背词 App 的做法（墨墨的单元化与记忆持久度、不背单词的拼写测试、百词斩的听音选义）：
 * - 5000 词不一次摊开，切成 50 词一个单元，有终点、有进度；
 * - 学完一个单元立刻测，三种题型轮着来，答错的自动记一次「忘记」并进错词本；
 * - 卡片仍然走 EnglishStore 的 SM-2，和主页背单词共用进度，不是另建一套系统。
 */

// ======================= 词库列表 =======================

@Composable
internal fun IeltsVault(onOpenLib: (String) -> Unit) {
    val ctx = LocalContext.current
    val libs = remember { IeltsLibrary.libs() }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(strong = true) {
                Text(tr("按单元背，一次 50 个", "50 words per unit"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Caption(tr("词条全部来自离线词典，雅思 5038 词都在里面。学完一个单元立刻测，错的自动进错词本。",
                    "All words come from the offline dictionary — 5038 for IELTS alone."))
            }
            libs.forEach { lib ->
                val total = remember(lib.id) { IeltsLibrary.count(ctx, lib) }
                val prog = remember(lib.id) { IeltsLibrary.progress(ctx, lib, total) }
                Tile(onClick = { onOpenLib(lib.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr(lib.zh, lib.en), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(8.dp))
                                Text("$total", color = C.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            Caption(lib.hint)
                        }
                        Text("›", color = C.Muted, fontSize = 20.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Meter(if (total == 0) 0f else prog.done.toFloat() / total)
                    Spacer(Modifier.height(5.dp))
                    Caption(tr("已学 ${prog.done} · 牢固 ${prog.solid} · 还没学 ${prog.fresh}", "${prog.done} learned · ${prog.solid} solid · ${prog.fresh} left"))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ======================= 单元列表 =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IeltsUnits(libId: String, onBack: () -> Unit, onOpenUnit: (Int) -> Unit) {
    val ctx = LocalContext.current
    val lib = remember(libId) { IeltsLibrary.lib(libId) }
    if (lib == null) { LaunchedEffect(Unit) { onBack() }; return }
    val total = remember(libId) { IeltsLibrary.count(ctx, lib) }
    val n = IeltsLibrary.units(total)
    // 一次扫完整个词库再分块；逐单元查库会触发上百次全表扫描，进页面会卡
    val learned = remember(libId, total) { IeltsLibrary.learnedByUnit(ctx, lib) }

    Column(Modifier.fillMaxSize()) {
        Header(tr(lib.zh, lib.en), onBack) { Caption("$n " + tr("单元", "units")) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Caption(tr("$n 个单元 · 每个 ${IeltsLibrary.UNIT} 词 · 按字母序。点一格开始学。",
                "$n units · ${IeltsLibrary.UNIT} words each · alphabetical"), Modifier.padding(horizontal = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until n).forEach { u ->
                    val cnt = learned.getOrElse(u) { 0 }
                    val full = cnt >= IeltsLibrary.UNIT
                    Box(
                        Modifier.size(54.dp)
                            .background(if (cnt > 0) C.GoldBg else C.Surface, RoundedCornerShape(12.dp))
                            .border(1.dp, if (full) C.Gold else C.Line, RoundedCornerShape(12.dp))
                            .press { onOpenUnit(u) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${u + 1}", color = if (cnt > 0) C.Gold else C.Text, fontSize = 15.sp,
                                fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                            Text(if (cnt == 0) "—" else "$cnt", color = C.Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ======================= 单元：学 + 测 =======================

private enum class Phase { LEARN, QUIZ, DONE }

@Composable
internal fun IeltsUnitDrill(libId: String, unit: Int, voice: EnVoice, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val lib = remember(libId) { IeltsLibrary.lib(libId) }
    if (lib == null) { LaunchedEffect(Unit) { onBack() }; return }
    val words = remember(libId, unit) { IeltsLibrary.words(ctx, lib, unit) }

    var phase by remember { mutableStateOf(Phase.LEARN) }
    var i by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    val ratings = remember { mutableStateListOf<Int>() }
    var shift by remember { mutableIntStateOf(0) }
    var resRight by remember { mutableIntStateOf(0) }
    var resTotal by remember { mutableIntStateOf(0) }

    /** 把已经打分的卡落盘（一次写，不逐张写 50 次文件）。 */
    fun flush() {
        if (ratings.isEmpty()) return
        val now = System.currentTimeMillis()
        val list = words.mapIndexedNotNull { k, v ->
            val r = ratings.getOrNull(k) ?: return@mapIndexedNotNull null
            val id = "${lib.id}:${v.key}"
            val base = EnglishStore.cards(ctx)[id] ?: EnglishLogic.Card(id, lib.id, v.key)
            EnglishLogic.schedule(base, r, now)
        }
        EnglishStore.putMany(ctx, list)
    }
    // 学到一半按返回也要存，否则这几十个词白学
    DisposableEffect(libId, unit) { onDispose { runCatching { flush() } } }

    val title = tr("${lib.zh} · 单元 ${unit + 1}", "${lib.en} · Unit ${unit + 1}")
    Column(Modifier.fillMaxSize()) {
        Header(title, onBack) {
            Text(
                when (phase) {
                    Phase.LEARN -> "${i + 1}/${words.size}"
                    Phase.QUIZ -> tr("测验", "Quiz")
                    Phase.DONE -> tr("完成", "Done")
                },
                color = C.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            )
        }
        when {
            words.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Caption(tr("这个单元没有词。", "This unit is empty."))
            }

            phase == Phase.LEARN -> LearnBody(
                words = words, i = i, revealed = revealed, voice = voice,
                onReveal = { revealed = true },
                onRate = { rating ->
                    ratings.add(rating)
                    if (i + 1 >= words.size) {
                        runCatching { flush() }
                        phase = Phase.QUIZ
                    } else {
                        i++
                        revealed = false
                    }
                },
            )

            phase == Phase.QUIZ -> {
                val quiz = remember(libId, unit, shift) { IeltsDrill.quiz(words, IeltsDrill.SIZE, unit * 10 + shift) }
                if (quiz.isEmpty()) {
                    LaunchedEffect(Unit) { phase = Phase.DONE }
                } else {
                    QuizBody(
                        quiz = quiz, voice = voice,
                        onDone = { right, wrong ->
                            // 答错的词按「忘记」记一次：间隔归零、lapses++，于是自动进错词本
                            val now = System.currentTimeMillis()
                            val list = wrong.mapNotNull { v ->
                                val id = "${lib.id}:${v.key}"
                                val base = EnglishStore.cards(ctx)[id] ?: EnglishLogic.Card(id, lib.id, v.key)
                                EnglishLogic.schedule(base, EnglishLogic.AGAIN, now)
                            }
                            runCatching { EnglishStore.putMany(ctx, list) }
                            resRight = right
                            resTotal = quiz.size
                            phase = Phase.DONE
                        },
                    )
                }
            }

            else -> DoneBody(
                words = words, lib = lib, right = resRight, total = resTotal,
                onRedo = {
                    ratings.clear(); i = 0; revealed = false; shift++
                    resRight = 0; resTotal = 0; phase = Phase.LEARN
                },
                onQuizAgain = { shift++; resRight = 0; resTotal = 0; phase = Phase.QUIZ },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun LearnBody(
    words: List<Voc>,
    i: Int,
    revealed: Boolean,
    voice: EnVoice,
    onReveal: () -> Unit,
    onRate: (Int) -> Unit,
) {
    val v = words[i.coerceIn(0, words.size - 1)]
    // 只为算「下次什么时候」的预览，用一张新卡的参数
    val fresh = EnglishLogic.Card("preview", "", v.key)
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Meter((i.toFloat() / words.size).coerceIn(0f, 1f))
        Spacer(Modifier.height(6.dp))
        Tile(strong = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.en, color = C.Text, fontSize = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.VolumeUp, tr("朗读", "Read aloud"), tint = C.Muted,
                    modifier = Modifier.size(30.dp).press { voice.speak(v.en) }.padding(4.dp))
            }
            if (v.phonetic.isNotBlank()) Text("/${v.phonetic}/", color = C.Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            if (revealed) {
                Text(v.zh, color = C.Text, fontSize = 16.sp, lineHeight = 25.sp)
            } else {
                Btn(tr("显示释义", "Show meaning"), primary = true, modifier = Modifier.fillMaxWidth()) { onReveal() }
                Spacer(Modifier.height(4.dp))
                Caption(tr("先自己回想一遍再点，比直接看记得牢", "Recall first, then reveal"))
            }
        }
        if (revealed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RateBtn(tr("忘记", "Again"), EnglishLogic.preview(fresh, EnglishLogic.AGAIN, now, false), C.Warn, Modifier.weight(1f)) { onRate(EnglishLogic.AGAIN) }
                RateBtn(tr("模糊", "Hard"), EnglishLogic.preview(fresh, EnglishLogic.HARD, now, false), C.Gold, Modifier.weight(1f)) { onRate(EnglishLogic.HARD) }
                RateBtn(tr("认识", "Good"), EnglishLogic.preview(fresh, EnglishLogic.GOOD, now, false), C.Ok, Modifier.weight(1f)) { onRate(EnglishLogic.GOOD) }
            }
            Spacer(Modifier.height(4.dp))
            Caption(tr("忘记 → 10 分钟后再来；模糊 → 明天；认识 → 间隔越拉越长", "Again: 10 min · Hard: tomorrow · Good: longer each time"))
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun RateBtn(text: String, sub: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .press { onClick() }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(sub, color = C.Muted, fontSize = 10.sp)
    }
}

@Composable
private fun QuizBody(quiz: List<IeltsDrill.Q>, voice: EnVoice, onDone: (Int, List<Voc>) -> Unit) {
    var qi by remember { mutableIntStateOf(0) }
    var picked by remember { mutableIntStateOf(-2) }   // -2 未答；选择题是选项下标；拼写题 0=对 -1=错
    var typed by remember { mutableStateOf("") }
    var right by remember { mutableIntStateOf(0) }
    val wrong = remember { mutableStateListOf<Voc>() }
    val q = quiz[qi]
    val answered = picked != -2
    val yes = if (q.isChoice) q.answer >= 0 && picked == q.answer else picked == 0 && !q.isChoice

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Meter((qi.toFloat() / quiz.size).coerceIn(0f, 1f))
        Caption(tr("第 ${qi + 1} / ${quiz.size} 题", "Question ${qi + 1} / ${quiz.size}"), Modifier.padding(horizontal = 4.dp))
        Tile(strong = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr(q.kind.zh, q.kind.en), color = C.Gold, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.border(1.dp, C.Gold.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
                Spacer(Modifier.width(8.dp))
                Caption(q.kind.prompt)
            }
            Spacer(Modifier.height(12.dp))
            when (q.kind) {
                IeltsDrill.Kind.MEANING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(q.v.en, color = C.Text, fontSize = 28.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.VolumeUp, tr("朗读", "Read aloud"), tint = C.Muted,
                            modifier = Modifier.size(28.dp).press { voice.speak(q.v.en) }.padding(4.dp))
                    }
                    if (q.v.phonetic.isNotBlank()) Text("/${q.v.phonetic}/", color = C.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }

                IeltsDrill.Kind.LISTEN -> {
                    Box(Modifier.size(64.dp).background(C.GoldBg, RoundedCornerShape(32.dp)).press { voice.speak(q.v.en) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.VolumeUp, tr("朗读", "Read aloud"), tint = C.Gold, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Caption(tr("点喇叭听发音，可以重复听", "Tap to listen — repeat as often as you like"))
                }

                IeltsDrill.Kind.SPELL -> {
                    Text(IeltsLibrary.clean(q.v.zh), color = C.Text, fontSize = 16.sp, lineHeight = 25.sp)
                    Spacer(Modifier.height(8.dp))
                    Caption(tr("${q.v.en.length} 个字母，首字母 ${q.v.en.first().uppercase()}", "${q.v.en.length} letters, starts with ${q.v.en.first().uppercase()}"))
                }
            }
            Spacer(Modifier.height(12.dp))
            if (q.isChoice) {
                q.options.forEachIndexed { k, opt ->
                    val bg = when {
                        !answered -> C.Bg
                        k == q.answer -> C.OkBg
                        k == picked -> C.WarnBg
                        else -> C.Bg
                    }
                    val fg = when {
                        !answered -> C.Text
                        k == q.answer -> C.Ok
                        k == picked -> C.Warn
                        else -> C.Muted
                    }
                    Text(opt, color = fg, fontSize = 15.sp, lineHeight = 21.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .background(bg, RoundedCornerShape(10.dp))
                            .press(enabled = !answered) { picked = k }
                            .padding(horizontal = 12.dp, vertical = 11.dp))
                }
            } else {
                OutlinedTextField(
                    typed, { typed = it }, singleLine = true, enabled = !answered,
                    label = { Text(tr("拼出这个单词", "Type the word")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!answered) {
                    Spacer(Modifier.height(8.dp))
                    Btn(tr("提交", "Submit"), primary = true, modifier = Modifier.fillMaxWidth()) {
                        picked = if (IeltsDrill.spellOk(q, typed)) 0 else -1
                    }
                }
            }
        }

        if (answered) {
            Text(
                if (yes) tr("对了 ✓", "Correct ✓")
                else if (q.isChoice) tr("错了 · 正确答案：${q.options.getOrNull(q.answer).orEmpty()}", "Wrong · answer: ${q.options.getOrNull(q.answer).orEmpty()}")
                else tr("拼错了 · 正确拼写：${q.v.en}", "Wrong · correct spelling: ${q.v.en}"),
                color = if (yes) C.Ok else C.Warn, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 4.dp),
            )
            Btn(tr("下一题", "Next"), primary = true, modifier = Modifier.fillMaxWidth()) {
                if (yes) right++ else wrong.add(q.v)
                if (qi + 1 >= quiz.size) onDone(right, wrong.toList()) else { qi++; picked = -2; typed = "" }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DoneBody(
    words: List<Voc>,
    lib: IeltsLibrary.Lib,
    right: Int,
    total: Int,
    onRedo: () -> Unit,
    onQuizAgain: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val prog = remember(tick) { IeltsLibrary.progress(ctx, lib, IeltsLibrary.count(ctx, lib)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile(strong = true) {
            Text(tr("这个单元过完了", "Unit finished"), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Num(words.size.toString(), tr("学了", "studied"))
                Num(if (total == 0) "—" else "$right/$total", tr("答对", "correct"))
                Num(prog.solid.toString(), tr("牢固", "solid"))
            }
            Spacer(Modifier.height(10.dp))
            Caption(tr("答错的词已按「忘记」记了一次，10 分钟后会再来，同时进了错词本。",
                "Missed words were marked Again and added to your mistake list."))
        }
        Btn(tr("再测一遍这个单元", "Quiz again"), modifier = Modifier.fillMaxWidth()) { onQuizAgain() }
        Btn(tr("重学一遍", "Study again"), modifier = Modifier.fillMaxWidth()) { onRedo() }
        Btn(tr("回到单元列表", "Back to units"), primary = true, modifier = Modifier.fillMaxWidth()) {
            tick++
            onBack()
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Num(v: String, k: String) {
    Column {
        Text(v, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
        Caption(k)
    }
}

// ======================= 我的：今日 / 持久度 / 错词本 =======================

@Composable
internal fun IeltsMine(voice: EnVoice, onOpenLib: (String) -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val ielts = remember { IeltsLibrary.lib("ielts") }
    val total = remember(tick) { ielts?.let { IeltsLibrary.count(ctx, it) } ?: 0 }
    val prog = remember(tick) { ielts?.let { IeltsLibrary.progress(ctx, it, total) } ?: IeltsLibrary.Progress(0, 0, 0) }
    val wrongItems = remember(tick) {
        ielts?.let { l -> IeltsLibrary.wrongKeys(ctx, l, 40).mapNotNull { k -> IeltsLibrary.look(ctx, l, k) } } ?: emptyList()
    }
    val doneToday = remember(tick) { EnglishStore.doneToday(ctx) }
    val newToday = remember(tick) { EnglishStore.newToday(ctx) }
    val due = remember(tick) { EnglishStore.dueCount(ctx) }
    val streak = remember(tick) { EnglishStore.streak(ctx) }
    val week = remember(tick) { EnglishStore.weekDays(ctx) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(strong = true) {
                Text(tr("今天", "Today"), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Num(newToday.toString(), tr("新词", "new"))
                    Num(doneToday.toString(), tr("动了多少卡", "cards done"))
                    Num(due.toString(), tr("待复习", "due"))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    week.forEach { on -> Box(Modifier.size(14.dp).background(if (on) C.Gold else C.Line, RoundedCornerShape(4.dp))) }
                    Spacer(Modifier.width(6.dp))
                    Caption(tr("最近 7 天", "last 7 days"))
                }
            }

            Tile {
                Text(tr("雅思词库记忆持久度", "IELTS retention"), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Meter(if (total == 0) 0f else prog.done.toFloat() / total)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Column { Text("${prog.solid}", color = C.Ok, fontSize = 17.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace); Caption(tr("牢固", "solid")) }
                    Column { Text("${prog.learning}", color = C.Gold, fontSize = 17.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace); Caption(tr("学习中", "learning")) }
                    Column { Text("${prog.fresh}", color = C.Muted, fontSize = 17.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace); Caption(tr("未学", "untouched")) }
                }
                Spacer(Modifier.height(8.dp))
                Caption(tr("复习间隔拉到 21 天以上才算牢固——和墨墨的「熟知」同一个标准。", "Interval ≥ 21 days counts as solid."))
            }

            Tile(onClick = { onOpenLib("ielts") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("去背雅思全词库", "Open the IELTS list"), color = C.Text, fontSize = 15.sp)
                        Caption(tr("$total 词 · 分 ${IeltsLibrary.units(total)} 个单元", "$total words · ${IeltsLibrary.units(total)} units"))
                    }
                    Text("›", color = C.Muted, fontSize = 20.sp)
                }
            }

            Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("错词本", "Mistakes"), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (wrongItems.isNotEmpty()) Text(tr("刷新", "Refresh"), color = C.Gold, fontSize = 12.sp, modifier = Modifier.press { tick++ }.padding(4.dp))
            }
            if (wrongItems.isEmpty()) {
                Caption(tr("还没有错词。测验里答错的词会自动进来。", "Empty — missed words land here automatically."), Modifier.padding(horizontal = 4.dp))
            } else {
                Caption(tr("忘过的词按遗忘次数排序，共 ${wrongItems.size} 个", "${wrongItems.size} words you have forgotten"), Modifier.padding(horizontal = 4.dp))
                wrongItems.forEach { v ->
                    Tile {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(v.en, color = C.Text, fontSize = 17.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)
                                Caption(IeltsLibrary.brief(v.zh, 40))
                            }
                            Icon(Icons.Outlined.VolumeUp, tr("朗读", "Read aloud"), tint = C.Muted,
                                modifier = Modifier.size(28.dp).press { voice.speak(v.en) }.padding(4.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

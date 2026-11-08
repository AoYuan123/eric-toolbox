package com.eric.toolbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.Route
import com.eric.toolbox.core.Heartbeat
import com.eric.toolbox.core.ModuleRegistry
import com.eric.toolbox.core.MonitorService
import com.eric.toolbox.core.Store
import com.eric.toolbox.core.ToolModule
import com.eric.toolbox.modules.english.EnglishStore
import com.eric.toolbox.modules.gold.GoldModule
import com.eric.toolbox.modules.read.Library
import com.eric.toolbox.modules.read.Listen
import com.eric.toolbox.modules.trials.TrialModule
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 模块显示名（中英）。 */
fun moduleName(m: ToolModule): String = when (m.id) {
    "gold" -> tr("实时金价", "Gold")
    "trials" -> tr("临床试验", "Trials")
    "read" -> tr("读书", "Reading")
    "translate" -> tr("翻译", "Translate")
    "datecalc" -> tr("日期计算", "Dates")
    "english" -> tr("学英语", "English")
    "ai" -> tr("AI 管家", "AI")
    else -> m.name
}

/** V2 首页：便当盒布局，主角是金价（点阵数字），其余按“今天要用到的”排。 */
@Composable
fun HomeScreen(go: (Route) -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); tick++ } }
    val visible = remember(tick) { ModuleRegistry.visibleOrdered(ctx).map { it.id }.toSet() }
    val running = remember(tick) { ModuleRegistry.all.count { it.background?.isEnabled(ctx) == true } }
    val now = System.currentTimeMillis()
    val clock = SimpleDateFormat(if (I18n.en) "MMM d EEE HH:mm" else "M月d日 EEE HH:mm", if (I18n.en) Locale.US else Locale.CHINA).format(Date(now))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(tr("百宝箱", "Toolbox"), color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(clock, color = C.Muted, fontSize = 12.sp, style = NumStyle)
            }
            Health(running, tick) { MonitorService.sync(ctx); go(Route.KeepAlive) }
        }
        if ("gold" in visible) GoldTile { go(Route.Module("gold")) }
        if ("translate" in visible) TranslateTile { go(Route.Module("translate")) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EnglishTile(Modifier.weight(1f), tick) { go(Route.English) }
            ListenTile(Modifier.weight(1f)) { go(Route.Module("read")) }
        }
        val tools = ModuleRegistry.visibleOrdered(ctx).filter { it.id !in setOf("gold", "trials", "english", "ai", "translate") }
        if (tools.isNotEmpty()) {
            Caption(tr("工具", "Tools"), Modifier.padding(start = 4.dp, top = 4.dp))
            tools.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { m ->
                        Tile(Modifier.weight(1f).heightIn(min = 86.dp), onClick = { go(Route.Module(m.id)) }) {
                            Icon(m.icon, null, tint = C.Text, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.weight(1f).heightIn(min = 14.dp))
                            Text(moduleName(m), color = C.Text, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        if ("trials" in visible) TrialsTile(tick) { go(Route.Module("trials")) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TranslateTile(onClick: () -> Unit) {
    Tile(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Translate, null, tint = C.Text, modifier = Modifier.size(20.dp))
            Text(tr("翻译", "Translate"), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Caption(tr("中 ⇄ 英", "ZH ⇄ EN"))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().background(C.Bg, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(tr("输入或粘贴要翻译的文字…", "Type or paste text to translate…"), color = C.Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Health(running: Int, tick: Long, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val beat = remember(tick) { Heartbeat.last(ctx) }
    val expect = remember(tick) { Heartbeat.expectedNext(ctx) }
    val now = System.currentTimeMillis()
    val bad = running > 0 && (!MonitorService.running || beat == 0L || now - expect > 3 * 60_000)
    val (text, color) = when {
        running == 0 -> tr("后台未开启", "Background off") to C.Muted
        bad -> tr("后台可能被停止 · 修复", "Background stopped · fix") to C.Warn
        else -> tr("后台 $running 项运行中", "$running running") to C.Muted
    }
    Row(Modifier.press(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(if (bad) C.Warn else if (running == 0) C.Line else C.Ok, CircleShape))
        Text(text, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun GoldTile(onClick: () -> Unit) {
    val ctx = LocalContext.current
    val snap by GoldModule.snapshot.collectAsState()
    LaunchedEffect(Unit) { val s = GoldModule.snapshot.value; if (s == null || System.currentTimeMillis() - s.fetchedAtMs > 60_000) runCatching { GoldModule.refresh(ctx) } }
    val q = snap?.market
    val price = q?.price ?: snap?.td?.price
    val prev = q?.prevClose
    val cfg = remember { GoldModule.config(ctx) }
    val nextTier = remember(snap) { GoldModule.lastAlerted(ctx) - cfg.step }
    val series = remember(snap) { GoldModule.loadBankSeries(ctx).mapNotNull { it.jcj }.takeLast(40) }
    Tile(strong = true, onClick = onClick) {
        Row {
            Caption(tr("上海金 Au99.99 · 元/克", "Au99.99 · CNY/g"), Modifier.weight(1f))
            Caption(q?.timeMs?.let { SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(it)) } ?: "--:--")
        }
        Spacer(Modifier.height(6.dp))
        val d = if (price != null && prev != null) price - prev else null
        val tone = when { d == null -> C.Muted; d >= 0 -> C.Up; else -> C.Down }
        Row(verticalAlignment = Alignment.Bottom) {
            val txt = price?.let { "%.2f".format(it) } ?: "---.--"
            Text(txt.substringBefore('.'), color = if (price == null) C.Muted else C.Text, fontSize = 46.sp, fontWeight = FontWeight.SemiBold, style = NumStyle,
                modifier = Modifier.alignByBaseline())
            Text("." + txt.substringAfter('.'), color = C.Muted, fontSize = 24.sp, fontWeight = FontWeight.Medium, style = NumStyle, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            if (d != null && prev != null) Text("%+.2f  %+.2f%%".format(d, d / prev * 100), color = tone, fontSize = 13.sp, fontWeight = FontWeight.Medium, style = NumStyle,
                modifier = Modifier.alignByBaseline().background(tone.copy(alpha = 0.14f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        if (series.size >= 3) { Spacer(Modifier.height(8.dp)); Spark(series, tone) }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (price != null) Text(
                if (price > nextTier) tr("距 $nextTier 还差 %.2f".format(price - nextTier), "%.2f above $nextTier".format(price - nextTier))
                else tr("已低于 $nextTier", "below $nextTier"),
                color = C.Muted, fontSize = 12.sp, style = NumStyle, modifier = Modifier.weight(1f),
            ) else Spacer(Modifier.weight(1f))
            snap?.bank?.jcj?.let { j -> Text(tr("工行积存金 %.2f", "ICBC savings %.2f").format(j), color = C.Muted, fontSize = 12.sp, style = NumStyle) }
        }
    }
}

@Composable
private fun Spark(values: List<Double>, tone: Color) {
    val lo = values.min(); val hi = values.max(); val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(40.dp)) {
        val stepX = size.width / (values.size - 1)
        fun y(v: Double) = (4f + (size.height - 8f) * (1 - ((v - lo) / span))).toFloat()
        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * stepX, y(v)) }
        val fill = androidx.compose.ui.graphics.Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, tone.copy(alpha = 0.10f))
        drawPath(path, tone, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(tone, 5f, Offset(size.width, y(values.last())))
    }
}

@Composable
private fun EnglishTile(modifier: Modifier, tick: Long, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val done = remember(tick) { EnglishStore.doneToday(ctx) }
    val due = remember(tick) { EnglishStore.dueCount(ctx) }
    val newLeft = remember(tick) { (EnglishStore.newLimit(ctx) - EnglishStore.newToday(ctx)).coerceAtLeast(0) }
    val week = remember(tick) { EnglishStore.weekDays(ctx) }
    Tile(modifier.heightIn(min = 156.dp), onClick = onClick) {
        Caption(tr("学英语 · 今天", "English · today"))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DotMatrix(done.toString(), dot = 6.dp)
            Text(tr("已练", "done"), color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { week.forEach { on -> Box(Modifier.weight(1f).height(5.dp).background(if (on) C.Text else C.Line, RoundedCornerShape(3.dp))) } }
        Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
        Caption(tr("复习 $due · 新词 $newLeft", "$due due · $newLeft new"))
    }
}

@Composable
private fun ListenTile(modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val book = remember(Listen.bookId) { Library.recent(ctx).firstOrNull() }
    Tile(modifier.heightIn(min = 156.dp), onClick = onClick) {
        Caption(if (Listen.active) tr("正在听", "Listening") else tr("最近在读", "Reading"))
        Spacer(Modifier.height(8.dp))
        val title = if (Listen.active) Listen.bookTitle else book?.title
        Text(title ?: tr("书架还是空的", "No books yet"), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, lineHeight = 20.sp)
        if (book != null && !Listen.active) Caption(tr("读到 %.0f%%", "%.0f%% read").format(book.progress * 100))
        Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
        if (book != null) Meter(book.progress.toFloat())
        Spacer(Modifier.height(10.dp))
        if (book != null || Listen.active) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(C.Text, CircleShape).press {
                if (Listen.active) Listen.toggle()
                else book?.let { b -> runCatching { Listen.startTts(ctx, b, if (b.listenChapter >= 0) b.listenChapter else b.chapter, if (b.listenChapter >= 0) b.listenSentence else 0) } }
            }, contentAlignment = Alignment.Center) {
                Icon(if (Listen.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, tint = C.Bg, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Caption(if (Listen.playing) tr("朗读中", "Playing") else tr("接着听", "Resume"))
        }
    }
}

@Composable
private fun TrialsTile(tick: Long, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val seen = remember(tick) { Store.global(ctx).getLong("trials_seen", 0L) }
    val fresh = remember(tick) { TrialModule.changes(ctx).count { it.atMs > seen } }
    val subs = remember(tick) { TrialModule.subs(ctx).size }
    Tile(onClick = { Store.global(ctx).edit().putLong("trials_seen", System.currentTimeMillis()).apply(); onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(9.dp).background(if (fresh > 0) C.Gold else C.Line, CircleShape))
            Column(Modifier.weight(1f)) {
                Text(if (fresh > 0) tr("临床试验有 $fresh 项新变化", "$fresh new trial updates") else tr("临床试验暂无新变化", "No new trial updates"), color = C.Text, fontSize = 14.sp)
                Caption(tr("关注 $subs 组 · 每天 9:00 / 18:00 检查", "$subs watches · checked 9:00 / 18:00"))
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = C.Muted)
        }
    }
}


package com.eric.toolbox.modules.read

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.CardBox
import com.eric.toolbox.ui.Mono
import com.eric.toolbox.ui.Segmented
import com.eric.toolbox.ui.TopBar

/** 生词本 / 划线笔记（所有书）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VocabScreen(onBack: () -> Unit, go: (RPage) -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf("vocab") }
    var tick by remember { mutableIntStateOf(0) }
    val words = remember(tick) { Notes.words(ctx).sortedByDescending { it.addedAt } }
    val due = remember(tick) { Notes.dueWords(ctx).size }
    val hls = remember(tick) { Notes.highlights(ctx).sortedWith(compareBy({ it.bookTitle }, { it.chapter }, { it.fraction })) }
    var delWord by remember { mutableStateOf<Notes.Word?>(null) }
    var delHl by remember { mutableStateOf<Notes.Highlight?>(null) }
    val tts = remember { Speaker(ctx) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    Column(Modifier.fillMaxSize()) {
        TopBar("生词本 · 笔记", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Segmented(listOf("vocab" to "生词本 ${words.size}", "notes" to "划线笔记 ${hls.size}"), tab) { tab = it }
            if (tab == "vocab") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("${words.size}", "个生词", Modifier.weight(1f))
                    Stat("$due", "现在该复习", Modifier.weight(1f))
                }
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).background(if (due > 0) C.Gold else C.Surface, RoundedCornerShape(12.dp))
                        .clickable(enabled = due > 0) { go(RPage.Review) },
                    contentAlignment = Alignment.Center,
                ) { Text(if (due > 0) "开始复习（$due 个）" else "暂时没有要复习的词", color = if (due > 0) (if (C.palette.isLight) Color.White else C.Bg) else C.Muted, fontSize = 15.sp) }
                if (words.isEmpty()) Text("阅读时长按一个单词，在弹出的卡片里点「加生词本」。", color = C.Muted, fontSize = 13.sp)
                Text("复习间隔按记忆曲线：认识一次，下次隔 1、2、4、7、15、30、60 天；不认识就从头来。长按一个词可删除。", color = C.Muted, fontSize = 11.sp)
                words.forEach { w ->
                    Column(
                        Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(16.dp)).combinedClickable(onClick = { tts.speak(w.word) }, onLongClick = { delWord = w }).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(w.word, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif)
                            if (w.phonetic.isNotBlank()) Text("  /${w.phonetic}/", color = C.Muted, fontSize = 12.sp)
                        }
                        Text(w.meaning.lines().take(2).joinToString("；"), color = C.Muted, fontSize = 13.sp)
                        if (w.sentence.isNotBlank()) Text(w.sentence, color = C.Text, fontSize = 13.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif, maxLines = 3)
                        Text("《${w.bookTitle}》 · 第 ${w.chapter + 1} 章", color = C.Muted, fontSize = 11.sp)
                    }
                }
            } else {
                if (hls.isEmpty()) Text("阅读时长按一句话，在弹出的卡片里点「划线」。点书里已划线的句子可以写笔记。", color = C.Muted, fontSize = 13.sp)
                hls.groupBy { it.bookId }.forEach { (bid, list) ->
                    val title = list.first().bookTitle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("《$title》 ${list.size} 条", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("导出", color = C.Gold, fontSize = 13.sp, modifier = Modifier.clickable { shareText(ctx, "《$title》笔记", Notes.exportBook(ctx, bid, title)) }.padding(10.dp))
                    }
                    list.forEach { h ->
                        Column(
                            Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(16.dp)).combinedClickable(onClick = {}, onLongClick = { delHl = h }).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(h.chapterTitle.trim(), color = C.Muted, fontSize = 11.sp)
                            Text(h.text, color = C.Text, fontSize = 14.sp, fontFamily = FontFamily.Serif)
                            h.translation?.let { Text("译：$it", color = C.Muted, fontSize = 13.sp) }
                            if (h.note.isNotBlank()) Text("笔记：${h.note}", color = C.Gold, fontSize = 13.sp)
                        }
                    }
                }
            }
            Box(Modifier.height(16.dp))
        }
    }
    delWord?.let { w ->
        AlertDialog(onDismissRequest = { delWord = null }, title = { Text("删除生词「${w.word}」？") },
            confirmButton = { TextButton(onClick = { Notes.removeWord(ctx, w.word); delWord = null; tick++ }) { Text("删除", color = C.Warn) } },
            dismissButton = { TextButton(onClick = { delWord = null }) { Text("取消") } })
    }
    delHl?.let { h ->
        AlertDialog(onDismissRequest = { delHl = null }, title = { Text("删除这条划线？") },
            confirmButton = { TextButton(onClick = { Notes.removeHighlight(ctx, h.id); delHl = null; tick++ }) { Text("删除", color = C.Warn) } },
            dismissButton = { TextButton(onClick = { delHl = null }) { Text("取消") } })
    }
}

@Composable
private fun Stat(n: String, label: String, modifier: Modifier) {
    Column(modifier.background(C.Surface, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(n, color = C.Gold, fontSize = 22.sp, fontFamily = Mono)
        Text(label, color = C.Muted, fontSize = 12.sp)
    }
}

/** 卡片复习：先看词和例句，点一下看释义，再选 认识 / 模糊 / 不认识。 */
@Composable
internal fun ReviewScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val queue = remember { Notes.dueWords(ctx).toMutableList() }
    var idx by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    val tts = remember { Speaker(ctx) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    Column(Modifier.fillMaxSize()) {
        TopBar("复习生词", onBack) { Text("${minOf(idx + 1, queue.size)}/${queue.size}", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(10.dp)) }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (idx >= queue.size) {
                CardBox {
                    Text("这一轮复习完了，共 $done 个。", color = C.Text, fontSize = 16.sp)
                    Text("选「不认识」的词 10 分钟后会再出现。", color = C.Muted, fontSize = 13.sp)
                }
                return@Column
            }
            val w = queue[idx]
            Column(
                Modifier.fillMaxWidth().weight(1f).background(C.Surface, RoundedCornerShape(20.dp)).clickable { shown = true }.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(w.word, color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, modifier = Modifier.clickable { tts.speak(w.word) })
                if (w.phonetic.isNotBlank()) Text("/${w.phonetic}/  点单词发音", color = C.Muted, fontSize = 13.sp)
                if (w.sentence.isNotBlank()) Text(w.sentence, color = C.Text, fontSize = 15.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif, lineHeight = 22.sp)
                Box(Modifier.weight(1f))
                if (shown) Text(w.meaning, color = C.Text, fontSize = 16.sp, lineHeight = 23.sp)
                else Text("想一想它的意思，点卡片看释义", color = C.Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("《${w.bookTitle}》", color = C.Muted, fontSize = 11.sp)
            }
            if (shown) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0 to "不认识", 1 to "模糊", 2 to "认识").forEach { (r, t) ->
                    Box(
                        Modifier.weight(1f).heightIn(min = 52.dp).background(if (r == 2) C.Gold else C.Surface, RoundedCornerShape(12.dp))
                            .clickable { Notes.reviewWord(ctx, w.word, r); done++; idx++; shown = false },
                        contentAlignment = Alignment.Center,
                    ) { Text(t, color = if (r == 2) (if (C.palette.isLight) Color.White else C.Bg) else C.Text, fontSize = 15.sp) }
                }
            }
        }
    }
}

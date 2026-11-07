package com.eric.toolbox.modules.read

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.Http
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.Segmented
import com.eric.toolbox.ui.ToggleRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** LibriVox 章节下载（离开页面也继续下；同一时间只下一个，国内连 archive.org 慢，排队更稳）。 */
object AudioDownloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lock = Mutex()
    /** key = 录音id_段号 → 0..1 进度；-1 排队中；-2 失败 */
    val progress = mutableStateMapOf<String, Float>()
    val errors = mutableStateMapOf<String, String>()

    fun key(ab: ReadLogic.AudioBook, i: Int) = "${ab.id}_$i"

    fun enqueue(ctx: Context, bookId: String, ab: ReadLogic.AudioBook, i: Int) {
        val k = key(ab, i)
        if (progress[k]?.let { it >= -1f } == true) return
        val f = Listen.audioFile(ctx, bookId, ab, i)
        if (f.isFile) return
        progress[k] = -1f; errors.remove(k)
        scope.launch {
            lock.withLock {
                var lastErr: Exception? = null
                for (attempt in 1..3) {
                    try {
                        Library.downloadFile(ab.sections[i].url, f) { done, total -> progress[k] = if (total > 0) done.toFloat() / total else 0.01f }
                        progress.remove(k); return@withLock
                    } catch (e: Exception) { lastErr = e; kotlinx.coroutines.delay(3000L * attempt) }
                }
                progress[k] = -2f
                errors[k] = when (lastErr) { is java.net.SocketTimeoutException -> "超时"; is java.net.UnknownHostException -> "网络不通"; else -> lastErr?.message ?: "失败" }
            }
        }
    }
}

@Composable
private fun Chips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (k, t) ->
            val on = k == selected
            Box(
                Modifier.heightIn(min = 36.dp).background(if (on) C.Gold else Color.Transparent, RoundedCornerShape(10.dp))
                    .border(1.dp, if (on) C.Gold else C.Line, RoundedCornerShape(10.dp)).clickable { onSelect(k) }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(t, color = if (on) (if (C.palette.isLight) Color.White else C.Bg) else C.Muted, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun RoundBtn(text: String, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(if (big) 64.dp else 52.dp).background(if (big) C.Gold else C.Surface, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (big) (if (C.palette.isLight) Color.White else C.Bg) else C.Text, fontSize = if (big) 15.sp else 12.sp) }
}

private fun sleepKey(): String = when {
    Listen.sleepAt == 0L -> "0"
    Listen.sleepAt == -1L -> "-1"
    else -> "on"
}

/**
 * 听书面板（阅读器菜单「听书」）。onStartHere：从阅读页当前页开始读；chapter：阅读页当前章。
 */
@Composable
fun ListenPanel(book: Library.Book, readerChapter: Int, onStartHere: () -> Unit) {
    val ctx = LocalContext.current
    val sameBook = Listen.bookId == book.id
    var tab by remember { mutableStateOf(if (sameBook && Listen.mode == Listen.Mode.AUDIO) "audio" else "tts") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Segmented(listOf("tts" to "机器朗读", "audio" to "真人朗读"), tab) { tab = it }
        Listen.message?.takeIf { sameBook }?.let { Text(it, color = C.Warn, fontSize = 12.sp) }
        if (tab == "tts") TtsPart(book, readerChapter, onStartHere) else AudioPart(book)
        Text("锁屏、切到别的 App 也会继续播放；通知栏、锁屏、耳机按键都能暂停和切换；拔下耳机自动暂停。", color = C.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun TtsPart(book: Library.Book, readerChapter: Int, onStartHere: () -> Unit) {
    val ctx = LocalContext.current
    val active = Listen.bookId == book.id && Listen.mode == Listen.Mode.TTS
    if (active) {
        Text("${Listen.chapterTitle} · 第 ${Listen.index + 1}/${Listen.sentences.size} 句", color = C.Text, fontSize = 14.sp)
        Text("读到的句子在书里高亮，读完一页自动翻页；长按任意一句，点「从这里听」可跳过去。", color = C.Muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundBtn("上一句") { Listen.prev() }
            RoundBtn(if (Listen.playing) "暂停" else "继续", big = true) { Listen.toggle() }
            RoundBtn("下一句") { Listen.next() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("从当前页开始读", color = C.Gold, fontSize = 13.sp, modifier = Modifier.clickable { onStartHere() }.padding(vertical = 6.dp))
            Text("停止听书", color = C.Muted, fontSize = 13.sp, modifier = Modifier.clickable { Listen.stop() }.padding(vertical = 6.dp))
        }
    } else {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).background(C.Gold, RoundedCornerShape(12.dp)).clickable { onStartHere() },
            contentAlignment = Alignment.Center,
        ) { Text("从当前页开始朗读", color = if (C.palette.isLight) Color.White else C.Bg, fontSize = 15.sp) }
        if (book.listenChapter >= 0) Text("上次听到第 ${book.listenChapter + 1} 章第 ${book.listenSentence + 1} 句 · 点这里接着听", color = C.Gold, fontSize = 13.sp,
            modifier = Modifier.clickable { runCatching { Listen.startTts(ctx, book, book.listenChapter, book.listenSentence) }.onFailure { Listen.message = "打不开：${it.message}" } }.padding(vertical = 6.dp))
    }
    Text("语速", color = C.Muted, fontSize = 13.sp)
    Chips(listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f).map { it.toString() to "${it}×" }, Listen.speed.toString()) { Listen.changeSpeed(it.toFloat()) }
    Text("定时关闭", color = C.Muted, fontSize = 13.sp)
    Chips(listOf("0" to "关", "15" to "15 分钟", "30" to "30 分钟", "60" to "60 分钟", "-1" to "读完本章"), sleepKey().let { if (it == "on") "" else it }) { Listen.setSleep(it.toInt()) }
    if (Listen.sleepAt > 0) Text("将在 ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(Listen.sleepAt))} 停止", color = C.Gold, fontSize = 12.sp)
    if (active) {
        val voices = remember(Listen.tick / 50) { Listen.voices() }
        if (voices.isNotEmpty()) {
            Text("声音（手机语音引擎提供，${voices.size} 个）", color = C.Muted, fontSize = 13.sp)
            val cur = Listen.currentVoice()
            Chips(voices.take(12).mapIndexed { i, v -> v.name to ("声音 ${i + 1}" + if (v.isNetworkConnectionRequired) "（联网）" else "") }, cur ?: "") { Listen.setVoice(it) }
        }
        ToggleRow("读完本章自动读下一章", null, Listen.autoNext) { Listen.setAutoNextChapter(it) }
    }
}

@Composable
private fun AudioPart(book: Library.Book) {
    val ctx = LocalContext.current
    var list by remember(book.id) { mutableStateOf<List<ReadLogic.AudioBook>?>(null) }
    var err by remember(book.id) { mutableStateOf<String?>(null) }
    var chosen by remember(book.id) { mutableStateOf<ReadLogic.AudioBook?>(if (Listen.bookId == book.id) Listen.audio else null) }
    LaunchedEffect(book.id) {
        if (list != null) return@LaunchedEffect
        try {
            val all = ReadLogic.parseLibriVox(Http.get(ReadLogic.librivoxUrl(book.title), timeoutMs = 30_000), book.gutenbergId)
            list = all
            if (chosen == null) chosen = all.firstOrNull { it.id == book.audioId }
        } catch (e: Exception) { err = "没连上 LibriVox：${e.message ?: e.javaClass.simpleName}（境外网站，国内有时连不上）" }
    }
    Text("LibriVox 是志愿者录制的公版书有声书（英文为主）。音频在境外的 archive.org，国内很慢，所以先按段下载到手机再听。", color = C.Muted, fontSize = 12.sp)
    when {
        err != null -> Text(err!!, color = C.Warn, fontSize = 13.sp)
        list == null -> { Text("正在查这本书有没有真人录音…", color = C.Muted, fontSize = 13.sp); LinearProgressIndicator(color = C.Gold, trackColor = C.Line, modifier = Modifier.fillMaxWidth()) }
        list!!.isEmpty() -> Text(if (book.gutenbergId != null) "LibriVox 上没有这本书（Gutenberg #${book.gutenbergId}）的录音。" else "没找到同名录音（这本书不是从 Gutenberg 下载的，只能按书名找）。", color = C.Muted, fontSize = 13.sp)
        chosen == null -> {
            Text("找到 ${list!!.size} 个录音版本，选一个：", color = C.Text, fontSize = 14.sp)
            list!!.forEach { ab ->
                Column(Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(14.dp)).clickable { chosen = ab }.padding(12.dp)) {
                    Text(ab.title, color = C.Text, fontSize = 14.sp)
                    Text("${ab.readers.ifEmpty { "朗读者未注明" }} · ${ab.sections.size} 段 · 共 ${ReadLogic.duration(ab.totalSeconds)}", color = C.Muted, fontSize = 12.sp)
                }
            }
        }
        else -> AudioSections(book, chosen!!) { chosen = null }
    }
}

@Composable
private fun AudioSections(book: Library.Book, ab: ReadLogic.AudioBook, onChange: () -> Unit) {
    val ctx = LocalContext.current
    val playingThis = Listen.bookId == book.id && Listen.mode == Listen.Mode.AUDIO && Listen.audio?.id == ab.id
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(ab.readers.ifEmpty { ab.title }, color = C.Text, fontSize = 14.sp)
            Text("${ab.sections.size} 段 · ${ReadLogic.duration(ab.totalSeconds)}", color = C.Muted, fontSize = 12.sp)
        }
        Text("换版本", color = C.Muted, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onChange).padding(8.dp))
    }
    if (playingThis) {
        Text(Listen.chapterTitle, color = C.Text, fontSize = 14.sp)
        val dur = Listen.durationMs.coerceAtLeast(1)
        var drag by remember { mutableStateOf<Float?>(null) }
        Slider(drag ?: (Listen.positionMs.toFloat() / dur), { drag = it }, onValueChangeFinished = { drag?.let { Listen.seekAudio((it * dur).toLong()) }; drag = null },
            colors = SliderDefaults.colors(thumbColor = C.Gold, activeTrackColor = C.Gold))
        Text("${mmss(Listen.positionMs)} / ${mmss(Listen.durationMs)}", color = C.Muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundBtn("−15 秒") { Listen.prev() }
            RoundBtn(if (Listen.playing) "暂停" else "继续", big = true) { Listen.toggle() }
            RoundBtn("+15 秒") { Listen.next() }
        }
        Text("语速", color = C.Muted, fontSize = 13.sp)
        Chips(listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f).map { it.toString() to "${it}×" }, Listen.speed.toString()) { Listen.changeSpeed(it.toFloat()) }
        Text("定时关闭", color = C.Muted, fontSize = 13.sp)
        Chips(listOf("0" to "关", "15" to "15 分钟", "30" to "30 分钟", "60" to "60 分钟", "-1" to "听完本段"), sleepKey().let { if (it == "on") "" else it }) { Listen.setSleep(it.toInt()) }
        Text("停止听书", color = C.Muted, fontSize = 13.sp, modifier = Modifier.clickable { Listen.stop() }.padding(vertical = 6.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("全部下载（排队，一段一段下）", color = C.Gold, fontSize = 13.sp, modifier = Modifier.clickable { ab.sections.indices.forEach { AudioDownloads.enqueue(ctx, book.id, ab, it) } }.padding(vertical = 6.dp))
    }
    ab.sections.forEachIndexed { i, sct ->
        val f = Listen.audioFile(ctx, book.id, ab, i)
        val k = AudioDownloads.key(ab, i)
        val p = AudioDownloads.progress[k]
        val has = f.isFile && p == null
        val isCur = playingThis && Listen.section == i
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sct.title, color = if (isCur) C.Gold else C.Text, fontSize = 14.sp)
                Text(listOfNotNull(sct.seconds.takeIf { it > 0 }?.let { ReadLogic.duration(it) },
                    when { has -> "已下载 %.1f MB".format(f.length() / 1048576.0); p == -1f -> "排队中"; p == -2f -> "下载失败：${AudioDownloads.errors[k]}"; p != null -> "下载中 ${(p * 100).toInt()}%"; else -> null })
                    .joinToString(" · "), color = if (p == -2f) C.Warn else C.Muted, fontSize = 12.sp)
                if (p != null && p >= 0) LinearProgressIndicator(progress = { p }, color = C.Gold, trackColor = C.Line, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            when {
                has -> Text(if (isCur) "播放中" else "播放", color = C.Gold, fontSize = 14.sp, modifier = Modifier.clickable {
                    if (!isCur) Listen.startAudio(ctx, book, ab, i, if (book.audioId == ab.id && book.audioSection == i) book.audioMs else 0)
                }.padding(10.dp))
                p == null || p == -2f -> Text(if (p == -2f) "重试" else "下载", color = C.Text, fontSize = 14.sp, modifier = Modifier.clickable { AudioDownloads.enqueue(ctx, book.id, ab, i) }.padding(10.dp))
                else -> {}
            }
        }
    }
}

private fun mmss(ms: Long): String { val s = (ms / 1000).toInt(); return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

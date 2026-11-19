package com.eric.toolbox.modules.family

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.TopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 做片子：顺绳挑片段、排顺序、定照片停多久，然后渲染成一个 mp4。
 *
 * 刻意不做"拖拽排序"——手指按着一串缩略图拖，在横向列表里很容易误触滚动，
 * 而且没法在 CI 里验证手感。改成选中 + 左移右移两个按钮，慢半秒，但一次就对。
 *
 * 也不做"每段单独加字幕"：字幕要烧进画面，得逐帧渲染，代价远大于收益。
 * 时间、孕周这些信息，等文本轨道那版再说。
 */
@Composable
fun MovieScreen(entries: List<Entry>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var clips by remember { mutableStateOf<List<Clip>?>(null) }
    var selected by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<ExportState>(ExportState.Idle) }
    val exporter = remember { MovieExporter(ctx) }

    DisposableEffect(Unit) { onDispose { exporter.cancel() } }

    LaunchedEffect(entries.size) {
        clips = withContext(Dispatchers.IO) { buildClips(ctx, entries) }
        selected = 0
    }

    Dialog(onDismissRequest = { if (state !is ExportState.Running) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(C.Bg)) {
            Column(Modifier.fillMaxSize()) {
                TopBar("做片子", onClose)

                val list = clips
                when {
                    list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = C.Gold, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }

                    list.isEmpty() -> Column(
                        Modifier.fillMaxSize().padding(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("绳子上还没有能用的画面", color = C.Text, fontSize = 15.sp)
                        Text(
                            "照片、视频、实况照片都能进片子；录音不行，它没有画面。",
                            color = C.Muted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    else -> {
                        EditorBody(
                            clips = list,
                            selected = selected.coerceIn(0, list.size - 1),
                            onSelect = { selected = it },
                            onMove = { from, to ->
                                if (to in list.indices) {
                                    clips = list.toMutableList().apply { add(to, removeAt(from)) }
                                    selected = to
                                }
                            },
                            onRemove = { idx ->
                                clips = list.toMutableList().apply { removeAt(idx) }
                                selected = (idx - 1).coerceAtLeast(0)
                            },
                            onSeconds = { idx, delta ->
                                clips = list.toMutableList().apply {
                                    val c = get(idx)
                                    set(idx, c.copy(seconds = (c.seconds + delta).coerceIn(1, 10)))
                                }
                            },
                            onMake = {
                                state = ExportState.Running(0f, "准备中")
                                exporter.start(
                                    clips = list,
                                    onProgress = { p, s -> state = ExportState.Running(p, s) },
                                    onDone = { f -> state = ExportState.Done(f) },
                                    onError = { m -> state = ExportState.Failed(m) },
                                )
                            },
                        )
                    }
                }
            }

            when (val s = state) {
                is ExportState.Running -> RunningPane(
                    progress = s.progress,
                    label = s.label,
                    onCancel = {
                        exporter.cancel()
                        state = ExportState.Idle
                    },
                )

                is ExportState.Done -> ResultPane(
                    file = s.file,
                    onShare = { share(ctx, s.file) },
                    onAgain = { state = ExportState.Idle },
                    onClose = onClose,
                )

                is ExportState.Failed -> AlertPane(
                    message = s.message,
                    onDismiss = { state = ExportState.Idle },
                )

                else -> Unit
            }
        }
    }
}

@Composable
private fun EditorBody(
    clips: List<Clip>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onSeconds: (Int, Int) -> Unit,
    onMake: () -> Unit,
) {
    val totalMs = clips.sumOf { it.shownMs }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text(
                "按绳子的顺序排好了。想调就点某一段，再左移右移。",
                color = C.Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Text(
                "${clips.size} 段 · 大约 ${secondsLabel(totalMs)}",
                color = C.Gold,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(clips, key = { _, c -> c.entry.id + c.seconds }) { i, clip ->
                ClipCard(clip, i, i == selected) { onSelect(i) }
            }
        }

        val cur = clips.getOrNull(selected)
        if (cur != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "第 ${selected + 1} 段",
                        color = C.Text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (cur.mode == ClipMode.STILL) "照片 · 停 ${cur.seconds} 秒" else "视频 · 取前 ${cur.seconds} 秒",
                        color = C.Muted,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionChip("← 前移", selected > 0) { onMove(selected, selected - 1) }
                    ActionChip("后移 →", selected < clips.size - 1) { onMove(selected, selected + 1) }
                    if (cur.mode == ClipMode.STILL) {
                        ActionChip("－1 秒", cur.seconds > 1) { onSeconds(selected, -1) }
                        ActionChip("＋1 秒", cur.seconds < 10) { onSeconds(selected, 1) }
                    }
                    ActionChip("删掉", true, warn = true) { onRemove(selected) }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "生成要在这台手机上跑，60 秒的片子大概 1–3 分钟，期间别退出。",
                color = C.Muted.copy(alpha = 0.65f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(C.Gold).clickable(onClick = onMake),
                contentAlignment = Alignment.Center,
            ) {
                Text("生成片子", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ClipCard(clip: Clip, index: Int, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    val base = Modifier.size(width = 76.dp, height = 100.dp)
        .clip(shape)
        .background(C.Surface)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            (if (active) base.border(1.6.dp, C.Gold, shape) else base).clickable(onClick = onClick),
        ) {
            if (clip.mode == ClipMode.STILL) {
                AsyncImage(
                    model = clip.source,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        null,
                        tint = C.Muted.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Box(
                Modifier.align(Alignment.TopStart).padding(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text("${index + 1}", color = Color.White, fontSize = 9.sp)
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    "${(clip.shownMs / 1000f).let { if (it < 10) String.format("%.1f", it) else it.toInt().toString() }}s",
                    color = Color.White,
                    fontSize = 9.sp,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(FamilyDate.short(clip.entry.atMs), color = C.Muted.copy(alpha = 0.7f), fontSize = 10.sp)
    }
}

@Composable
private fun ActionChip(label: String, enabled: Boolean, warn: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (enabled) C.Surface else C.Surface.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = when {
                !enabled -> C.Muted.copy(alpha = 0.35f)
                warn -> C.Warn.copy(alpha = 0.85f)
                else -> C.Text
            },
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun RunningPane(progress: Float, label: String, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${(progress * 100).toInt()}%",
                color = C.Gold,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(C.Gold))
            }
            Spacer(Modifier.height(14.dp))
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            Text(
                "停下",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onCancel).padding(10.dp),
            )
        }
    }
}

@Composable
private fun ResultPane(file: File, onShare: () -> Unit, onAgain: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember(file.path) { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(file.path) {
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
        player.prepare()
        onDispose { runCatching { player.release() } }
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopBar("片子做好了", onClose)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { c -> PlayerView(c).apply { this.player = player; useController = true } },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(13.dp))
                    .background(C.Surface).clickable(onClick = onAgain),
                contentAlignment = Alignment.Center,
            ) { Text("改一改再做", color = C.Text, fontSize = 14.sp) }
            Box(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(13.dp))
                    .background(C.Gold).clickable(onClick = onShare),
                contentAlignment = Alignment.Center,
            ) { Text("发出去", color = Color.White, fontSize = 14.sp) }
        }
        Text(
            "存在手机里：${file.name}",
            color = C.Muted.copy(alpha = 0.6f),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 20.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun AlertPane(message: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(16.dp))
                .background(C.Surface).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("没做成", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                message,
                color = C.Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(C.Bg)
                    .clickable(onClick = onDismiss).padding(horizontal = 22.dp, vertical = 9.dp),
            ) { Text("知道了", color = C.Gold, fontSize = 13.sp) }
        }
    }
}

private sealed class ExportState {
    data object Idle : ExportState()
    data class Running(val progress: Float, val label: String) : ExportState()
    data class Done(val file: File) : ExportState()
    data class Failed(val message: String) : ExportState()
}

/** 把绳子上的素材转成可编辑的片段；录音没有画面，直接跳过。 */
private fun buildClips(ctx: Context, entries: List<Entry>): List<Clip> = entries.mapNotNull { e ->
    when {
        e.kind == Kind.AUDIO -> null

        e.kind == Kind.PHOTO && e.isLive -> {
            val motion = runCatching { MotionPhoto.videoOf(ctx, e) }.getOrNull()
            if (motion != null) {
                Clip(e, motion, ClipMode.MOTION, 8, ClipProbe.durationMs(motion))
            } else {
                val still = FamilyStore.fileOf(ctx, e.file)
                if (still.isFile) Clip(e, still, ClipMode.STILL, 3, 0L) else null
            }
        }

        e.kind == Kind.PHOTO -> {
            val still = FamilyStore.fileOf(ctx, e.file)
            if (still.isFile) Clip(e, still, ClipMode.STILL, 3, 0L) else null
        }

        else -> {
            val v = FamilyStore.fileOf(ctx, e.file)
            if (v.isFile) Clip(e, v, ClipMode.MOTION, 8, ClipProbe.durationMs(v)) else null
        }
    }
}

private fun secondsLabel(ms: Long): String {
    val s = (ms / 1000).toInt()
    return if (s < 60) "$s 秒" else "${s / 60} 分 ${s % 60} 秒"
}

private fun share(ctx: Context, file: File) {
    val uri = runCatching {
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    }.getOrNull()
    if (uri == null) {
        Toast.makeText(ctx, "分享失败，文件还没准备好", Toast.LENGTH_SHORT).show()
        return
    }
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        ctx.startActivity(Intent.createChooser(i, "把片子发出去").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(ctx, "没有能接收视频的 App", Toast.LENGTH_SHORT).show() }
}

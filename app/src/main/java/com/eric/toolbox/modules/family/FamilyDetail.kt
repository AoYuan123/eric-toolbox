package com.eric.toolbox.modules.family

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.Meter
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate

/**
 * 点开一个挂点：看大图/播片/听声音，改那句当时写的话，改挂在哪天。
 *
 * 关闭即保存——不给"保存/取消"两个按钮，因为这里没有"错误输入"这回事，
 * 写一半退出去也不该丢。
 */
@Composable
fun FamilyDetail(
    entry: Entry,
    mediaDir: File,
    edd: LocalDate?,
    birth: LocalDate?,
    onDismiss: () -> Unit,
    onSaved: (Entry) -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val file = File(mediaDir, entry.file)

    var text by remember(entry.id) { mutableStateOf(entry.text) }
    var atDate by remember(entry.id) { mutableStateOf(FamilyDate.dayOf(entry.atMs)) }
    var askDelete by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { onSaved(entry.copy(text = text.trim(), atMs = FamilyDate.atDay(atDate))) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(C.Bg)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onSaved(entry.copy(text = text.trim(), atMs = FamilyDate.atDay(atDate))) }) {
                    Icon(Icons.Filled.Close, "关闭", tint = C.Muted)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    FamilyDate.long(atDate),
                    color = C.Gold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { pickDate = true }.padding(8.dp),
                )
            }

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                when (entry.kind) {
                    Kind.PHOTO -> AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    )
                    Kind.VIDEO -> VideoPane(file)
                    Kind.AUDIO -> AudioPane(file, entry.durationMs)
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val stage = FamilyDate.stageLabel(edd, birth, atDate)
                if (stage.isNotEmpty()) {
                    Text(stage, color = C.Muted, fontSize = 12.sp)
                }

                // 引文用衬线体，跟界面其他字区分开——这是"当时的话"，不是现在的备注。
                val quoteStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = C.Text,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(200) },
                    placeholder = { Text("那天想说的话", color = C.Muted.copy(alpha = 0.6f), fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                    textStyle = quoteStyle,
                )

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "删掉这条",
                        color = C.Warn.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { askDelete = true }.padding(vertical = 10.dp, horizontal = 2.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "挂在 ${FamilyDate.short(atDate)}",
                        color = C.Muted.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { pickDate = true }.padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }

    if (pickDate) {
        PickDateDialog(initial = atDate, onDismiss = { pickDate = false }) {
            atDate = it
            pickDate = false
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("删掉这条？", fontSize = 16.sp) },
            text = { Text("照片文件也会一起删掉，找不回来。", color = C.Muted, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { askDelete = false; onDelete() }) { Text("删掉", color = C.Warn) } },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("算了") } },
        )
    }
}

@Composable
private fun VideoPane(file: File) {
    val ctx = LocalContext.current
    val player = remember(file.path) { ExoPlayer.Builder(ctx).build() }

    DisposableEffect(file.path) {
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
        player.prepare()
        onDispose { player.release() }
    }

    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
    )
}

@Composable
private fun AudioPane(file: File, durationMs: Long) {
    val ctx = LocalContext.current
    val player = remember(file.path) { ExoPlayer.Builder(ctx).build() }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(0L) }

    DisposableEffect(file.path) {
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
        player.prepare()
        onDispose { player.release() }
    }

    LaunchedEffect(file.path) {
        while (true) {
            pos = player.currentPosition
            playing = player.isPlaying
            delay(200)
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(VoiceColor)
                .clickable {
                    if (player.isPlaying) player.pause() else player.play()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (playing) "暂停" else "播放",
                tint = Color(0xFFC9A55C),
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        val total = if (durationMs > 0) durationMs else 1L
        Meter(
            fraction = (pos.toFloat() / total.toFloat()).coerceIn(0f, 1f),
            color = Color(0xFFC9A55C),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(FamilyDate.duration(pos), color = C.Muted, fontSize = 12.sp)
            Text(FamilyDate.duration(durationMs), color = C.Muted, fontSize = 12.sp)
        }
    }
}

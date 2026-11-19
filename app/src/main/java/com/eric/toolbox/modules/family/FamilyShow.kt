package com.eric.toolbox.modules.family

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * 放映厅：顺着绳子一件事一件事放过去。
 *
 * 三种素材用三种放法，但节奏是同一条：
 *  - 照片：3.6 秒，同时极缓地推近 6%。幅度小到你以为它没动，不做的话整段就成了幻灯片。
 *  - 视频：最多放 8 秒（原片更长的部分点一下暂停自己拖），不然一段旅行片能放十分钟。
 *  - 录音：按它自己的长度放完。
 *
 * 底部那根拉直的白线就是绳子本身：一个个点是挂点，走到哪就亮到哪。
 * 全屏沉浸，除了关闭没有任何控件——放映的时候不该看见按钮。
 */
private const val PHOTO_MS = 3600L
private const val LIVE_MS = 3200L
private const val VIDEO_MS = 8000L
private const val AUDIO_CAP_MS = 60_000L

@Composable
fun FamilyShow(
    entries: List<Entry>,
    mediaDir: File,
    edd: LocalDate?,
    birth: LocalDate?,
    onClose: () -> Unit,
) {
    if (entries.isEmpty()) return

    val ctx = LocalContext.current
    var index by remember { mutableStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { runCatching { player.release() } } }

    val total = entries.size
    val current = entries.getOrNull(index)

    // 实况照片抽出来的那段 mp4；普通照片永远是 null。
    var motion by remember { mutableStateOf<File?>(null) }

    // 换到音视频就换源；照片必须停下来，否则上一段的声音会盖着这张照片继续响。
    LaunchedEffect(index) {
        val e = entries.getOrNull(index) ?: return@LaunchedEffect
        motion = null
        // 抄一次缓存住，再放就不用碰原图了。抄不出来（厂商私有格式）就当普通照片。
        val live = if (e.isLive) {
            withContext(Dispatchers.IO) { runCatching { MotionPhoto.videoOf(ctx, e) }.getOrNull() }
        } else null
        motion = live

        val uri = when {
            live != null -> Uri.fromFile(live)
            e.kind == Kind.VIDEO -> Uri.fromFile(File(mediaDir, e.file))
            else -> null
        }
        if (uri == null) {
            player.pause()
            player.clearMediaItems()
        } else {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = !paused
        }
    }

    // 自动往前走。暂停时这条协程被取消，恢复时重新计时（照片会重新数 3.6 秒，可以接受）。
    LaunchedEffect(index, paused, finished, motion) {
        if (paused || finished) return@LaunchedEffect
        val e = entries.getOrNull(index) ?: return@LaunchedEffect
        when {
            motion != null -> delay(LIVE_MS)
            e.kind == Kind.VIDEO -> delay(VIDEO_MS)
            e.kind == Kind.AUDIO -> delay(e.durationMs.coerceIn(1000L, AUDIO_CAP_MS))
            else -> delay(PHOTO_MS)
        }
        if (index + 1 < total) index += 1 else { finished = true; player.pause() }
    }

    LaunchedEffect(paused) {
        if (player.mediaItemCount > 0) {
            if (paused) player.pause() else player.play()
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { if (!finished) paused = !paused } },
        ) {
            when {
                current == null -> Unit
                motion != null -> PlayerPane(player)
                current.kind == Kind.PHOTO -> PhotoPane(File(mediaDir, current.file), index)
                current.kind == Kind.VIDEO -> PlayerPane(player)
                else -> AudioPane(player)
            }

            // 底部一层从透明到黑的渐变：字压在照片最亮的地方也能看清。
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)))),
            )

            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
            ) {
                current?.let { e ->
                    val stage = FamilyDate.stageLabel(edd, birth, FamilyDate.dayOf(e.atMs))
                    Text(
                        listOf(FamilyDate.short(e.atMs), stage).filter { it.isNotEmpty() }.joinToString("  ·  "),
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 12.sp,
                    )
                    if (e.text.isNotBlank()) {
                        Text(
                            e.text,
                            color = Color.White,
                            fontFamily = FontFamily.Serif,
                            fontSize = 16.sp,
                            lineHeight = 25.sp,
                            maxLines = 4,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                RopeProgress(index = index, total = total)
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        finished -> "看完了 · 点右上角关上"
                        paused -> "点到的地方停住了"
                        else -> "${index + 1} / $total"
                    },
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) { Icon(Icons.Filled.Close, "关闭", tint = Color.White.copy(alpha = 0.75f)) }
        }
    }
}

/** 照片：三秒半里极缓地推近，边推边等下一张。 */
@Composable
private fun PhotoPane(file: File, index: Int) {
    val zoom = remember(index) { Animatable(1f) }
    LaunchedEffect(index) {
        zoom.snapTo(1f)
        zoom.animateTo(1.06f, tween(PHOTO_MS.toInt(), easing = LinearEasing))
    }
    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .graphicsLayer { scaleX = zoom.value; scaleY = zoom.value },
    )
}

/** 视频：没有控制条，只放画面。 */
@Composable
private fun PlayerPane(player: ExoPlayer) {
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** 录音：没有画面，就给一段跟着声音走的波纹，比黑屏一行字好。 */
@Composable
private fun AudioPane(player: ExoPlayer) {
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(0L) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    LaunchedEffect(player) {
        while (true) {
            runCatching {
                pos = player.currentPosition
                total = player.duration.coerceAtLeast(0L)
            }
            delay(120)
        }
    }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(44.dp)) {
                val bars = 5
                val gap = size.width / (bars + 1)
                val hs = floatArrayOf(0.45f, 0.85f, 0.3f, 1f, 0.55f)
                for (b in 0 until bars) {
                    val h = size.height * hs[b] * (if (playing) 1f else 0.55f)
                    val x = gap * (b + 1)
                    drawLine(
                        Color(0xFFC9A55C),
                        Offset(x, (size.height - h) / 2f),
                        Offset(x, (size.height + h) / 2f),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (total > 0) "${FamilyDate.duration(pos)} / ${FamilyDate.duration(total)}" else "录音",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
        )
    }
}

/** 底部那根拉直的绳子。走过的点亮成金色，当前那颗大一点。 */
@Composable
private fun RopeProgress(index: Int, total: Int) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val y = size.height / 2f
        val left = 6f
        val right = size.width - 6f
        drawLine(
            Color.White.copy(alpha = 0.18f),
            Offset(left, y), Offset(right, y),
            strokeWidth = 1.dp.toPx(),
        )
        if (total <= 0) return@Canvas
        // 点多的时候间距会很密，但那是好事——一眼能看出这段日子有多长。
        val step = if (total > 1) (right - left) / (total - 1) else 0f

        if (index > 0 && total > 1) {
            drawLine(
                Color(0xFFC9A55C),
                Offset(left, y), Offset(left + step * index, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        for (i in 0 until total) {
            val x = left + step * i
            val isNow = i == index
            drawCircle(
                color = when {
                    isNow -> Color(0xFFE0BC72)
                    i < index -> Color(0xFFC9A55C).copy(alpha = 0.85f)
                    else -> Color.White.copy(alpha = 0.25f)
                },
                radius = if (isNow) 3.4.dp.toPx() else 1.6.dp.toPx(),
                center = Offset(x, y),
            )
        }
    }
}

package com.eric.toolbox.modules.family

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.TopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlin.math.sin

/** 麻绳的颜色：刻意不用强调色（信号橙），绳子是"背景物件"，抢眼就俗了。 */
internal val RopeColor = Color(0xFF8A7A5C)

/** 纸/声音片的底色。跟主题无关——它们模拟的是实物，白天夜里都是一张纸。 */
internal val PaperColor = Color(0xFFE6DAC0)
internal val PaperInk = Color(0xFF4A4238)
internal val VoiceColor = Color(0xFF242B27)

internal val ITEM_W = 88.dp
internal val ITEM_H = 206.dp

/** 每个挂点的绳子高度（正弦起伏，避免一条死直线）。 */
private fun nodeY(i: Int): Float = 26f + 7f * sin(i * 0.85f)

/** 每个挂点的倾斜角，按序号固定——同一天再看还是歪的那几度，不会自己变。 */
private fun tiltOf(i: Int): Float = ((i * 37) % 9 - 4) * 0.62f

private data class PendingShot(val file: File, val atMs: Long, val kind: Kind)

@Composable
fun FamilyScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var edd by remember { mutableStateOf(FamilyStore.edd(ctx)) }
    var birth by remember { mutableStateOf(FamilyStore.birth(ctx)) }
    val entries = remember { mutableStateListOf<Entry>() }
    // 必须先于 reload() 声明：Kotlin 的局部函数不能向前引用局部变量。
    var doneSet by remember { mutableStateOf(FamilyStore.doneKeys(ctx)) }

    fun reload() {
        val fresh = FamilyStore.load(ctx)
        entries.clear()
        entries.addAll(fresh)
        doneSet = FamilyStore.doneKeys(ctx)
    }

    LaunchedEffect(Unit) {
        // 老照片补判"是不是实况"，一次就好；放 IO 上，不卡住第一帧。
        withContext(Dispatchers.IO) { runCatching { FamilyStore.migrateLive(ctx) } }
        reload()
        // 每天早上的检查靠 WorkManager，重复排不会叠加。放在这里而不是 App.onCreate：
        // 没用过这个模块的人不该被排一个每天醒来的任务。
        FamilyReminder.schedule(ctx)
    }

    var shelfAt by remember { mutableStateOf<Int?>(null) }
    var shelfDate by remember { mutableStateOf(FamilyDate.today()) }
    var detail by remember { mutableStateOf<Entry?>(null) }
    var pickFor by remember { mutableStateOf<String?>(null) }
    var planOpen by remember { mutableStateOf(false) }
    var showOpen by remember { mutableStateOf(false) }
    var movieOpen by remember { mutableStateOf(false) }

    // 第一次进来、还没定起点：自动把设置弹出来一次。之后不再打扰。
    var setupOpen by remember {
        mutableStateOf(FamilyStore.edd(ctx) == null && !FamilyStore.flag(ctx, "setup_seen"))
    }

    fun closeSetup() {
        setupOpen = false
        FamilyStore.setFlag(ctx, "setup_seen", true)
    }

    var pending by remember { mutableStateOf<PendingShot?>(null) }
    var importAt by remember { mutableStateOf(0L) }

    val recorder = remember { AudioRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var recStart by remember { mutableStateOf(0L) }
    var recTick by remember { mutableStateOf(0L) }
    var recTarget by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(recording) {
        while (recording) {
            recTick = System.currentTimeMillis() - recStart
            delay(200)
        }
    }

    fun put(file: File, kind: Kind, atMs: Long, durationMs: Long) {
        // 相机偶尔会返回 OK 但一个字节都没写（存储满、权限被收回），
        // 这种必须当场删掉，否则绳子上会多一个永远空白的挂点。
        if (!file.isFile || file.length() < 1024L) {
            runCatching { file.delete() }
            return
        }
        FamilyStore.add(
            ctx,
            Entry(
                id = "e" + System.currentTimeMillis(),
                kind = kind,
                file = file.name,
                atMs = atMs,
                madeAtMs = System.currentTimeMillis(),
                durationMs = durationMs,
                // 拍照/相册进来的可能是实况照片：当场判一次，之后不用再判。
                live = if (kind == Kind.PHOTO) {
                    if (runCatching { MotionPhoto.isLive(file) }.getOrDefault(false)) 1 else 0
                } else 0,
            ),
        )
        reload()
    }

    // ---------- 相册 ----------
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = ctx.contentResolver.getType(uri)
        val isVideo = mime?.startsWith("video") == true
        val kind = if (isVideo) Kind.VIDEO else Kind.PHOTO
        val name = FamilyStore.newName(ctx, kind, if (isVideo) "mp4" else "jpg")
        val target = FamilyStore.fileOf(ctx, name)
        val ok = runCatching {
            ctx.contentResolver.openInputStream(uri).use { input ->
                target.outputStream().use { out -> input?.copyTo(out) }
            }
        }.isSuccess
        if (ok) {
            put(target, kind, importAt, if (isVideo) 0L else 0L)
        } else {
            runCatching { target.delete() }
        }
    }

    // ---------- 拍照 / 录像 ----------
    // 用系统相机而不是自己写 CameraX 取景：
    //   1) 厂商相机的计算摄影（小米/华为的夜景、人像）第三方拿不到，自己拍的画质一定更差；
    //   2) 相机兼容性没法在 CI 里验证，一旦写错就是"点了没反应"，这个模块不能有这种风险。
    // 代价是跳出去拍：所以必须显式给相机 App 授写权限，否则它会写不进我们的 Uri。
    val shootLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val p = pending
        pending = null
        if (res.resultCode == Activity.RESULT_OK && p != null) put(p.file, p.kind, p.atMs, 0L)
        else p?.file?.let { runCatching { it.delete() } }
    }

    fun shoot(action: String, file: File, kind: Kind) {
        pending = PendingShot(file, shelfDateMs(shelfDate), kind)
        shelfAt = null
        val uri = FamilyCapture.uriFor(ctx, file)
        val intent = Intent(action).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { shootLauncher.launch(intent) }.onFailure {
            pending = null
            runCatching { file.delete() }
            Toast.makeText(ctx, "没找到能用的相机", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- 录音权限 ----------
    fun startRecording(file: File) {
        if (recorder.start(file)) {
            recTarget = file
            recStart = System.currentTimeMillis()
            recTick = 0L
            recording = true
            shelfAt = null
        }
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val f = recTarget
        if (granted && f != null) startRecording(f)
    }

    fun onRecordClick() {
        val name = FamilyStore.newName(ctx, Kind.AUDIO, "m4a")
        val f = FamilyStore.fileOf(ctx, name)
        recTarget = f
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) startRecording(f) else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun stopRecording() {
        val ms = recorder.stop()
        recording = false
        val f = recTarget
        recTarget = null
        if (ms > 0 && f != null) put(f, Kind.AUDIO, shelfDateMs(shelfDate), ms) else f?.let { runCatching { it.delete() } }
    }

    // ---------- 布局 ----------
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("记忆绳", onBack) {
                if (entries.isNotEmpty()) {
                    IconButton(onClick = { showOpen = true }) {
                        Icon(
                            Icons.Filled.PlayCircle,
                            "放映",
                            tint = C.Muted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                TextButton(onClick = { planOpen = true }) { Text("时间表", color = C.Muted, fontSize = 13.sp) }
            }

            val stage = FamilyDate.stageLabel(edd, birth, FamilyDate.today())
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stage.ifEmpty { "还没定起点" },
                    color = if (stage.isEmpty()) C.Warn else C.Gold,
                    fontSize = 13.sp,
                )
                Text("  ·  已挂 ${entries.size} 件", color = C.Muted, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                if (entries.size >= 2) {
                    Text(
                        "做片子 ›",
                        color = C.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { movieOpen = true }.padding(vertical = 4.dp, horizontal = 2.dp),
                    )
                }
            }

            // 绳尾那张纸片：只写"下一件"。没有就什么都不挂，绳子干净到头。
            val nextRow = FamilyPlan.next(FamilyPlan.rows(edd, birth, doneSet), FamilyDate.today())

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (entries.isEmpty()) {
                    EmptyRope(onLong = { shelfAt = 0; shelfDate = FamilyDate.today() })
                } else {
                    RopeStrip(
                        entries = entries,
                        edd = edd,
                        mediaDir = FamilyStore.mediaDir(ctx),
                        next = nextRow,
                        onOpen = { detail = it },
                        onPlan = { planOpen = true },
                        onLong = { idx ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            shelfAt = idx
                            shelfDate = suggestDate(entries, idx)
                        },
                    )
                }
            }

            Text(
                if (entries.isEmpty()) "长按上面那片空白" else "长按绳上任意位置，补一天",
                color = C.Muted.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            )
        }

        if (shelfAt != null && !recording) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable { shelfAt = null })
            ToolShelf(
                modifier = Modifier.align(Alignment.BottomCenter),
                date = shelfDate,
                onPickDate = { pickFor = "shelf" },
                onAlbum = {
                    importAt = shelfDateMs(shelfDate)
                    shelfAt = null
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                onPhoto = {
                    val f = FamilyStore.fileOf(ctx, FamilyStore.newName(ctx, Kind.PHOTO, "jpg"))
                    shoot(MediaStore.ACTION_IMAGE_CAPTURE, f, Kind.PHOTO)
                },
                onVideo = {
                    val f = FamilyStore.fileOf(ctx, FamilyStore.newName(ctx, Kind.VIDEO, "mp4"))
                    shoot(MediaStore.ACTION_VIDEO_CAPTURE, f, Kind.VIDEO)
                },
                onRecord = { onRecordClick() },
            )
        }

        if (recording) {
            RecordingBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                elapsed = recTick,
                onStop = { stopRecording() },
                onCancel = {
                    recorder.cancel()
                    recording = false
                    recTarget?.let { runCatching { it.delete() } }
                    recTarget = null
                },
            )
        }
    }

    detail?.let { e ->
        FamilyDetail(
            entry = e,
            mediaDir = FamilyStore.mediaDir(ctx),
            edd = edd,
            birth = birth,
            onDismiss = { detail = null },
            onSaved = { updated ->
                if (updated != e) FamilyStore.update(ctx, updated)
                reload()
                detail = null
            },
            onDelete = {
                FamilyStore.remove(ctx, e.id)
                reload()
                detail = null
            },
        )
    }

    if (movieOpen) {
        MovieScreen(entries = entries, onClose = { movieOpen = false })
    }

    if (showOpen) {
        FamilyShow(
            entries = entries,
            mediaDir = FamilyStore.mediaDir(ctx),
            edd = edd,
            birth = birth,
            onClose = { showOpen = false },
        )
    }

    if (planOpen) {
        FamilyPlanScreen(
            edd = edd,
            birth = birth,
            done = doneSet,
            onToggleDone = { key, v ->
                FamilyStore.setDone(ctx, key, v)
                doneSet = FamilyStore.doneKeys(ctx)
            },
            onSetup = { planOpen = false; setupOpen = true },
            onClose = { planOpen = false },
        )
    }

    if (setupOpen) {
        SetupDialog(
            edd = edd,
            birth = birth,
            onPickEdd = { closeSetup(); pickFor = "edd" },
            onPickBirth = { closeSetup(); pickFor = "birth" },
            onDismiss = { closeSetup() },
        )
    }

    pickFor?.let { which ->
        PickDateDialog(
            initial = when (which) {
                "edd" -> edd ?: FamilyDate.today().plusDays(140)
                "birth" -> birth ?: FamilyDate.today()
                else -> shelfDate
            },
            onDismiss = { pickFor = null },
        ) { d ->
            when (which) {
                "edd" -> { edd = d; FamilyStore.setEdd(ctx, d) }
                "birth" -> { birth = d; FamilyStore.setBirth(ctx, d) }
                else -> shelfDate = d
            }
            pickFor = null
        }
    }
}

private fun shelfDateMs(d: LocalDate): Long = FamilyDate.atDay(d)

/** 长按落在两条之间时，取中间那一天；落在两头就贴着最近那天往外挪一天。 */
private fun suggestDate(list: List<Entry>, idx: Int): LocalDate {
    if (list.isEmpty()) return FamilyDate.today()
    val left = list.getOrNull(idx - 1)
    val right = list.getOrNull(idx)
    return when {
        left == null && right == null -> FamilyDate.today()
        left == null -> FamilyDate.dayOf(right!!.atMs).minusDays(1)
        right == null -> FamilyDate.dayOf(left.atMs).plusDays(1)
        else -> {
            val a = FamilyDate.dayOf(left.atMs)
            val b = FamilyDate.dayOf(right.atMs)
            val mid = (FamilyDate.daysBetween(a, b) / 2).toLong()
            if (mid <= 0) a else a.plusDays(mid)
        }
    }
}

@Composable
private fun EmptyRope(onLong: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().height(60.dp)) {
            val p = Path().apply {
                moveTo(0f, 30f)
                cubicTo(size.width * 0.3f, 40f, size.width * 0.7f, 20f, size.width, 32f)
            }
            drawPath(p, RopeColor.copy(alpha = 0.45f), style = Stroke(width = 1.2.dp.toPx()))
        }
        Spacer(Modifier.height(20.dp))
        Text("绳子还是空的", color = C.Text, fontSize = 15.sp)
        Text("长按这片空白，挂上第一张", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.clip(RoundedCornerShape(30.dp)).background(C.Surface).clickable(onClick = onLong)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) { Text("挂第一件", color = C.Gold, fontSize = 13.sp) }
    }
}

@Composable
private fun RopeStrip(
    entries: List<Entry>,
    edd: LocalDate?,
    mediaDir: File,
    next: PlanRow?,
    onOpen: (Entry) -> Unit,
    onPlan: () -> Unit,
    onLong: (Int) -> Unit,
) {
    val state = rememberLazyListState()
    val density = LocalDensity.current

    Box(
        Modifier.fillMaxWidth().height(ITEM_H).pointerInput(entries.size) {
            detectTapGestures(
                onLongPress = { off ->
                    val pad = with(density) { 20.dp.toPx() }
                    val unit = with(density) { ITEM_W.toPx() }
                    val x = state.firstVisibleItemScrollOffset + off.x - pad
                    val idx = (state.firstVisibleItemIndex + (x / unit).toInt()).coerceIn(0, entries.size)
                    onLong(idx)
                },
            )
        },
    ) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(entries, key = { _, e -> e.id }) { i, e ->
                RopeItem(e, i, mediaDir, onOpen)
            }
            // 纸片挂在最后，长按落在这上面时索引正好等于 entries.size，也就是"挂到最末尾"。
            if (next != null) {
                item(key = "paper") { RopePaper(next, entries.size, onPlan) }
            }
        }
    }
}

/**
 * 绳尾那张纸。刻意比照片小、比照片薄、还偏一点角度——
 * 一眼就能分出"这是还没发生的一件待办"，不是一张已经拍好的照片。
 */
@Composable
private fun RopePaper(row: PlanRow, index: Int, onClick: () -> Unit) {
    val today = FamilyDate.today()
    Column(
        Modifier.width(96.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val y0 = nodeY(index).dp.toPx()
            val y1 = nodeY(index + 1).dp.toPx()
            val dip = 4.dp.toPx()
            val path = Path().apply {
                moveTo(0f, y0)
                quadraticBezierTo(size.width / 2f, (y0 + y1) / 2f + dip, size.width, y1)
            }
            drawPath(path, RopeColor, style = Stroke(width = 1.2.dp.toPx()))
            drawLine(
                Color.White.copy(alpha = 0.18f),
                Offset(size.width / 2f, (y0 + y1) / 2f + dip * 0.6f),
                Offset(size.width / 2f, size.height),
                strokeWidth = 0.8.dp.toPx(),
            )
        }

        Box(
            Modifier
                .width(58.dp)
                .height(70.dp)
                .rotate(-2.6f)
                .clip(RoundedCornerShape(1.dp))
                .background(PaperColor)
                .clickable(onClick = onClick),
        ) {
            Column(Modifier.fillMaxSize().padding(6.dp)) {
                Text(FamilyDate.short(row.due), color = PaperInk.copy(alpha = 0.5f), fontSize = 8.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    row.item.title,
                    color = PaperInk,
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(9.dp))
        Text(
            if (row.due == today) "今天" else FamilyPlan.daysLabel(row.due, today),
            color = C.Gold.copy(alpha = 0.9f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun RopeItem(e: Entry, i: Int, mediaDir: File, onOpen: (Entry) -> Unit) {
    val angle = tiltOf(i)
    Column(
        Modifier.width(ITEM_W).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val y0 = nodeY(i).dp.toPx()
            val y1 = nodeY(i + 1).dp.toPx()
            val dip = 4.dp.toPx()
            val path = Path().apply {
                moveTo(0f, y0)
                quadraticBezierTo(size.width / 2f, (y0 + y1) / 2f + dip, size.width, y1)
            }
            drawPath(path, RopeColor, style = Stroke(width = 1.2.dp.toPx()))
            val hangFrom = (y0 + y1) / 2f + dip * 0.6f
            drawLine(
                Color.White.copy(alpha = 0.18f),
                Offset(size.width / 2f, hangFrom),
                Offset(size.width / 2f, size.height),
                strokeWidth = 0.8.dp.toPx(),
            )
        }

        Box(
            Modifier
                .width(if (e.kind == Kind.AUDIO) 34.dp else 62.dp)
                .height(78.dp)
                .rotate(angle)
                .clip(RoundedCornerShape(1.5.dp))
                .background(if (e.kind == Kind.AUDIO) VoiceColor else C.Surface)
                .clickable { onOpen(e) },
            contentAlignment = Alignment.Center,
        ) {
            when (e.kind) {
                Kind.AUDIO -> VoiceFace(e.durationMs)
                else -> {
                    AsyncImage(
                        model = File(mediaDir, e.file),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (e.kind == Kind.VIDEO) VideoBadge(Modifier.align(Alignment.BottomEnd))
                }
            }
        }

        Spacer(Modifier.height(9.dp))
        Text(FamilyDate.short(e.atMs), color = C.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun VoiceFace(durationMs: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val bars = 5
            val gapX = size.width / (bars + 1)
            val hs = floatArrayOf(0.5f, 0.85f, 0.35f, 0.9f, 0.55f)
            for (b in 0 until bars) {
                val h = size.height * hs[b]
                val x = gapX * (b + 1)
                drawLine(
                    Color(0xFFC9A55C),
                    Offset(x, (size.height - h) / 2f),
                    Offset(x, (size.height + h) / 2f),
                    strokeWidth = 1.4.dp.toPx(),
                )
            }
        }
        Text(
            FamilyDate.duration(durationMs),
            color = Color(0xFF8A7A5C),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun VideoBadge(modifier: Modifier = Modifier) {
    Canvas(modifier.padding(4.dp).size(11.dp)) {
        val p = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.18f)
            lineTo(size.width * 0.82f, size.height * 0.5f)
            lineTo(size.width * 0.28f, size.height * 0.82f)
            close()
        }
        drawPath(p, Color.White.copy(alpha = 0.9f))
    }
}

@Composable
private fun ToolShelf(
    modifier: Modifier = Modifier,
    date: LocalDate,
    onPickDate: () -> Unit,
    onAlbum: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onRecord: () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(C.Surface)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 26.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("挂在这天", color = C.Muted, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                FamilyDate.long(date),
                color = C.Gold,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onPickDate),
            )
            Spacer(Modifier.weight(1f))
            Text("改", color = C.Muted.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.clickable(onClick = onPickDate))
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ShelfTool("相册", Icons.Filled.PhotoLibrary, onAlbum)
            ShelfTool("拍照", Icons.Filled.PhotoCamera, onPhoto)
            ShelfTool("录像", Icons.Filled.Videocam, onVideo)
            ShelfTool("录音", Icons.Filled.Mic, onRecord)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ShelfTool(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(C.Bg).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = C.Text, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(7.dp))
        Text(label, color = C.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun RecordingBar(modifier: Modifier = Modifier, elapsed: Long, onStop: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(C.Surface)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFE24B4A)))
            Spacer(Modifier.width(10.dp))
            Text("正在录", color = C.Text, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(FamilyDate.duration(elapsed), color = C.Gold, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).background(C.Bg).clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) { Text("不要了", color = C.Muted, fontSize = 14.sp) }
            Box(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).background(C.Gold).clickable(onClick = onStop),
                contentAlignment = Alignment.Center,
            ) { Text("挂上去", color = Color.White, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun SetupDialog(
    edd: LocalDate?,
    birth: LocalDate?,
    onPickEdd: () -> Unit,
    onPickBirth: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("绳子的起点", fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    "有预产期，绳子才知道每一张是孕几周。等 TA 出生，再补上生日，后面就自动改算月龄。",
                    color = C.Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(14.dp))
                SetupRow("预产期", edd?.let { FamilyDate.long(it) } ?: "未设置", onPickEdd)
                SetupRow("出生日期", birth?.let { FamilyDate.long(it) } ?: "还没出生", onPickBirth)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("好") } },
    )
}

@Composable
private fun SetupRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = C.Muted, fontSize = 12.sp)
        Text("  ›", color = C.Muted, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickDateDialog(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L,
        yearRange = 1990..2100,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))) } ?: onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) { DatePicker(state = state, showModeToggle = false) }
}

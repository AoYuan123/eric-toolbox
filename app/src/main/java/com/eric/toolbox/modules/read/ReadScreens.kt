package com.eric.toolbox.modules.read

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.eric.toolbox.core.Http
import com.eric.toolbox.modules.read.ReadLogic.Source
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.CardBox
import com.eric.toolbox.ui.ListCard
import com.eric.toolbox.ui.Pill
import com.eric.toolbox.ui.RowItem
import com.eric.toolbox.ui.SectionLabel
import com.eric.toolbox.ui.TopBar
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private val coverColors = listOf(0xFF6E2B2B, 0xFF2B4A6E, 0xFF3E5A3A, 0xFF5A4A2B, 0xFF4A3E5A, 0xFF2B5A5A).map { Color(it) }

@Composable
private fun Cover(title: String, url: String?, modifier: Modifier) {
    val bg = coverColors[(title.hashCode() and 0x7fffffff) % coverColors.size]
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(bg)) {
        Text(title, color = Color(0xFFF4ECD8), fontSize = 11.sp, fontFamily = FontFamily.Serif, maxLines = 4, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content(); Box(Modifier.height(16.dp))
    }
}

// ================= 首页（书架） =================
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReadHome(onBack: () -> Unit, go: (RPage) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val books = remember(tick) { Library.recent(ctx) }
    var toDelete by remember { mutableStateOf<Library.Book?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "正在导入…"
        scope.launch {
            try { val b = Library.import(ctx, uri); tick++; Toast.makeText(ctx, "已导入《${b.title}》", Toast.LENGTH_SHORT).show() }
            catch (e: Exception) { Toast.makeText(ctx, "导入失败：${e.message}", Toast.LENGTH_LONG).show() }
            busy = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("读书", onBack)
        Page {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).background(C.Surface, RoundedCornerShape(14.dp)).clickable { go(RPage.Search()) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text("搜书名、作者（同时查 3 个公版书库）", color = C.Muted, fontSize = 14.sp) }

            val cur = books.firstOrNull { it.lastReadAt > 0 && !it.finished }
            if (cur != null) {
                SectionLabel("继续")
                Row(
                    Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(20.dp)).clickable { go(RPage.Reader(cur.id)) }.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Cover(cur.title, cur.coverUrl, Modifier.width(72.dp).height(104.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(cur.title, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(cur.authors.joinToString(" · ").ifEmpty { cur.source }, color = C.Muted, fontSize = 12.sp, maxLines = 1)
                        LinearProgressIndicator(progress = { cur.progress.toFloat() }, color = C.Gold, trackColor = C.Line, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        Text("全书 ${(cur.progress * 100).toInt()}%", color = C.Muted, fontSize = 12.sp)
                        Box(Modifier.background(C.Gold, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text("继续读", color = if (C.palette.isLight) Color.White else C.Bg, fontSize = 13.sp)
                        }
                    }
                }
            }

            SectionLabel("我的书架 · ${books.size} 本" + if (books.isNotEmpty()) "（长按删除）" else "")
            if (books.isEmpty()) Text("书架还是空的。搜一本书下载，或者导入手机里的 EPUB / TXT。", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(4.dp))
            books.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { b ->
                        Column(
                            Modifier.weight(1f).combinedClickable(onClick = { go(RPage.Reader(b.id)) }, onLongClick = { toDelete = b }),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Cover(b.title, b.coverUrl, Modifier.fillMaxWidth().aspectRatio(3f / 4.3f))
                            Text(b.title, color = C.Text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                when { b.finished -> "已读完"; b.lastReadAt == 0L -> "未开始 · ${b.source}"; else -> "${(b.progress * 100).toInt()}%" },
                                color = C.Muted, fontSize = 11.sp,
                            )
                        }
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 46.dp).border(1.dp, C.Line, RoundedCornerShape(12.dp))
                    .clickable(enabled = busy == null) { picker.launch(arrayOf("application/epub+zip", "text/plain", "application/octet-stream")) },
                contentAlignment = Alignment.Center,
            ) { Text(busy ?: "导入手机里的 EPUB / TXT", color = C.Muted, fontSize = 14.sp) }

            SectionLabel("书库")
            ListCard {
                RowItem("Project Gutenberg", "可下载", "七万多本公版书，EPUB 直接下载")
                RowItem("Standard Ebooks", "可下载", "公版书精排版，EPUB 直接下载")
                RowItem("Open Library", "查书目", "书目、封面；借阅需在网页登录")
                RowItem("中华古籍资源库（国家图书馆）", "", "古籍扫描影像，免登录，在 App 内打开国图网页") { go(RPage.Guji) }
            }
        }
    }

    toDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("从书架删除？") },
            text = { Text("《${b.title}》的文件、进度和书签都会删除。") },
            confirmButton = { TextButton(onClick = { Library.remove(ctx, b.id); toDelete = null; tick++ }) { Text("删除", color = C.Warn) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("取消") } },
        )
    }
}

// ================= 搜索 =================
private data class SrcState(val loading: Boolean = false, val count: Int = 0, val error: String? = null)

@Composable
internal fun SearchScreen(initial: String, onBack: () -> Unit, go: (RPage) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf(initial) }
    var gut by remember { mutableStateOf<List<ReadLogic.Hit>>(emptyList()) }
    var se by remember { mutableStateOf<List<ReadLogic.Hit>>(emptyList()) }
    var ol by remember { mutableStateOf<List<ReadLogic.Hit>>(emptyList()) }
    val states = remember { mutableStateOf(mapOf<Source, SrcState>()) }
    var filter by remember { mutableStateOf<Source?>(null) }
    var onlyDl by remember { mutableStateOf(false) }
    var searchedFor by remember { mutableStateOf("") }

    fun run() {
        val query = q.trim()
        if (query.isEmpty()) return
        ReadPrefs.addQuery(ctx, query)
        searchedFor = query
        gut = emptyList(); se = emptyList(); ol = emptyList()
        states.value = Source.entries.filter { it != Source.LOCAL }.associateWith { SrcState(loading = true) }
        fun set(s: Source, st: SrcState) { states.value = states.value + (s to st) }
        scope.launch {
            val a = async { runCatching { ReadLogic.parseGutendex(Http.get(ReadLogic.gutendexUrl(query), timeoutMs = 40_000)) } }
            val b = async { runCatching { ReadLogic.parseStandard(Http.get(ReadLogic.standardUrl(query), timeoutMs = 20_000)) } }
            val c = async { runCatching { ReadLogic.parseOpenLib(Http.get(ReadLogic.openLibUrl(query), timeoutMs = 20_000)) } }
            launch { b.await().onSuccess { se = it; set(Source.STANDARD, SrcState(count = it.size)) }.onFailure { set(Source.STANDARD, SrcState(error = msg(it))) } }
            launch { c.await().onSuccess { ol = it; set(Source.OPENLIB, SrcState(count = it.size)) }.onFailure { set(Source.OPENLIB, SrcState(error = msg(it))) } }
            launch { a.await().onSuccess { gut = it; set(Source.GUTENBERG, SrcState(count = it.size)) }.onFailure { set(Source.GUTENBERG, SrcState(error = msg(it))) } }
        }
    }
    LaunchedEffect(Unit) { if (initial.isNotBlank()) run() }

    val merged = ReadLogic.merge(gut, se, ol)
        .filter { filter == null || filter in it.sources }
        .filter { !onlyDl || it.downloadable }

    Column(Modifier.fillMaxSize()) {
        TopBar("搜书", onBack)
        Page {
            OutlinedTextField(
                q, { q = it }, singleLine = true, label = { Text("书名或作者（英文书用英文搜）") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { run() }),
            )
            if (searchedFor.isEmpty()) {
                val recent = ReadPrefs.recentQueries(ctx)
                if (recent.isNotEmpty()) {
                    SectionLabel("最近搜过")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollRow()) {
                        recent.forEach { r -> Box(Modifier.clickable { q = r; run() }) { Pill(r, C.Text, C.Surface) } }
                    }
                }
                Text("提示：这三个书库基本都是英文公版书（作者去世较久、版权已过期的作品），现代中文书很少。", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(4.dp))
                return@Page
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollRow()) {
                Chip("全部", filter == null) { filter = null }
                listOf(Source.GUTENBERG, Source.STANDARD, Source.OPENLIB).forEach { s ->
                    val st = states.value[s]
                    val label = s.label + when { st == null -> ""; st.loading -> " …"; st.error != null -> " ✕"; else -> " ${st.count}" }
                    Chip(label, filter == s) { filter = if (filter == s) null else s }
                }
                Chip("只看可下载", onlyDl) { onlyDl = !onlyDl }
            }
            states.value.filter { it.value.error != null }.forEach { (s, st) ->
                Text("${s.label} 没连上：${st.error}（可以稍后再搜一次）", color = C.Warn, fontSize = 12.sp)
            }
            if (states.value[Source.GUTENBERG]?.loading == true) Text("Gutenberg 有时很慢（实测偶尔超过 45 秒），结果会陆续出来。", color = C.Muted, fontSize = 12.sp)
            if (merged.isEmpty() && states.value.values.none { it.loading }) Text("没有找到。试试换个拼写，或者只搜作者姓氏。", color = C.Muted, fontSize = 13.sp)
            merged.forEach { h -> HitCard(h) { go(RPage.Detail(h)) } }
        }
    }
}

private fun msg(e: Throwable) = when (e) {
    is java.net.SocketTimeoutException -> "超时"
    is java.net.UnknownHostException -> "网络不通"
    else -> e.message ?: e.javaClass.simpleName
}

@Composable
private fun Modifier.horizontalScrollRow() = this.then(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()))

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 36.dp).border(1.dp, if (on) C.Gold else C.Line, RoundedCornerShape(18.dp))
            .background(if (on) C.Gold else Color.Transparent, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) (if (C.palette.isLight) Color.White else C.Bg) else C.Muted, fontSize = 13.sp) }
}

@Composable
private fun HitCard(h: ReadLogic.Hit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(h.title, h.coverUrl, Modifier.width(56.dp).height(80.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(h.title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(h.authors.take(2).joinToString(", ").ifEmpty { null }, h.language.ifEmpty { null }, h.downloads?.takeIf { it > 0 }?.let { "下载 %,d 次".format(it) }).joinToString(" · "),
                color = C.Muted, fontSize = 12.sp, maxLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(h.sources.joinToString(" · ") { it.label }, C.Muted, C.Bg)
                if (h.downloadable) Pill("可下载 EPUB", C.Gold, C.GoldBg) else Pill("仅网页查看", C.Muted, C.Bg)
            }
        }
    }
}

// ================= 详情 / 下载 =================
@Composable
internal fun DetailScreen(h: ReadLogic.Hit, onBack: () -> Unit, openBook: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(-1f) }
    var busy by remember { mutableStateOf(false) }

    fun start(url: String?, sourceLabel: String, resolveSe: Boolean) {
        busy = true; status = "准备下载…"; progress = -1f
        scope.launch {
            try {
                val real = if (resolveSe) {
                    status = "正在找 Standard Ebooks 的下载地址…"
                    val page = Http.get("https://standardebooks.org" + h.seBookPath, timeoutMs = 20_000)
                    ReadLogic.standardEpubFromBookPage(page) ?: throw java.io.IOException("书页上没找到 EPUB 下载链接")
                } else url!!
                val b = Library.download(ctx, real, h.title, h.authors, sourceLabel, h.coverUrl, h.gutenbergId) { done, total ->
                    progress = if (total > 0) done.toFloat() / total else -1f
                    status = if (total > 0) "下载中 %.1f / %.1f MB".format(done / 1048576.0, total / 1048576.0) else "下载中 %.1f MB".format(done / 1048576.0)
                }
                Toast.makeText(ctx, "已加入书架", Toast.LENGTH_SHORT).show()
                openBook(b.id)
            } catch (e: Exception) {
                status = "下载失败：${msg(e)}。境外书库在国内偶尔很慢，可以再试一次。"
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("书籍详情", onBack)
        Page {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Cover(h.title, h.coverUrl, Modifier.width(110.dp).height(158.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(h.title, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    if (h.authors.isNotEmpty()) Text(h.authors.joinToString("、"), color = C.Text, fontSize = 13.sp)
                    Text(listOfNotNull(h.language.ifEmpty { null }, h.gutenbergId?.let { "Gutenberg #$it" }).joinToString(" · "), color = C.Muted, fontSize = 12.sp)
                    h.downloads?.takeIf { it > 0 }?.let { Text("下载 %,d 次".format(it), color = C.Muted, fontSize = 12.sp) }
                    Text("来源：" + h.sources.joinToString("、") { it.label }, color = C.Muted, fontSize = 12.sp)
                }
            }
            if (h.editions.isNotEmpty() || h.seBookPath != null) {
                SectionLabel("电子书版本（点一个下载）")
                ListCard {
                    h.editions.forEach { e -> RowItem(e.label, if (busy) "" else "下载", e.source.label + if (e.note.isNotEmpty()) " · ${e.note}" else "") { if (!busy) start(e.url, e.source.label, false) } }
                    if (h.seBookPath != null) RowItem("EPUB · 精排版", if (busy) "" else "下载", "Standard Ebooks · 排版和校对质量高") { if (!busy) start(null, Source.STANDARD.label, true) }
                }
            } else {
                CardBox { Text("这本书在这些书库里没有可直接下载的版本（Open Library 上多为在版书，借阅需要在网页登录 Internet Archive 账号）。", color = C.Muted, fontSize = 13.sp) }
            }
            status?.let {
                CardBox {
                    Text(it, color = C.Text, fontSize = 13.sp)
                    if (busy) { if (progress >= 0) LinearProgressIndicator(progress = { progress }, color = C.Gold, trackColor = C.Line, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(color = C.Gold, trackColor = C.Line, modifier = Modifier.fillMaxWidth()) }
                }
            }
            h.webUrl?.let { url ->
                ListCard { RowItem("在网页上打开", "", url.removePrefix("https://").substringBefore('/')) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
            }
            Text("听书（机器朗读 / 真人有声书）在下一个版本加上。", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(4.dp))
        }
    }
}

// ================= 古籍（内嵌国图网页） =================
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun GujiScreen(onBack: () -> Unit) {
    val webRef = remember { arrayOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    BackHandler { val w = webRef[0]; if (w != null && w.canGoBack()) w.goBack() else onBack() }
    Column(Modifier.fillMaxSize()) {
        TopBar("中华古籍资源库", onBack) {
            Text("刷新", color = C.Muted, fontSize = 14.sp, modifier = Modifier.clickable { webRef[0]?.reload() }.padding(10.dp))
        }
        if (loading) LinearProgressIndicator(color = C.Gold, trackColor = C.Line, modifier = Modifier.fillMaxWidth())
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    webRef[0] = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                            val s = req.url.scheme
                            return s != "http" && s != "https" // 只在网页里打开 http/https，别的协议（拨号、唤起 App 等）不处理
                        }
                        override fun onPageFinished(view: WebView, url: String) { loading = false }
                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { loading = true }
                    }
                    loadUrl("http://read.nlc.cn/thematDataSearch/toGujiIndex")
                }
            },
        )
        Text("国家图书馆网页，App 只是在里面打开它；古籍是扫描图片，不能查词或朗读。", color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { webRef[0]?.destroy() } }
}

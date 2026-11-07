package com.eric.toolbox.modules.read

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.eric.toolbox.core.KeyHooks
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.Segmented
import com.eric.toolbox.ui.ToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.Calendar

private fun hex(s: String) = Color(android.graphics.Color.parseColor(s))

private enum class Panel { NONE, TOC, MARKS, STYLE, SEARCH, NOTES, LISTEN }

/** 长按取到的词和整句（来自阅读页脚本）。 */
data class Pick(val word: String, val sentence: String, val occurrence: Int, val y: Double)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(bookId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var book by remember { mutableStateOf(Library.get(ctx, bookId)) }
    var content by remember { mutableStateOf<BookContent?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var rs by remember { mutableStateOf(ReadPrefs.settings(ctx)) }
    fun curTheme() = rs.effectiveTheme(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    val theme = curTheme()
    val col = ReadSettings.colors(theme)
    val bg = hex(col.bg); val fg = hex(col.fg); val muted = hex(col.muted)

    var chapter by remember { mutableIntStateOf(book?.chapter ?: 0) }
    var fraction by remember { mutableStateOf(book?.fraction ?: 0.0) }
    var pages by remember { mutableIntStateOf(1) }
    var pageNo by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var cpm by remember { mutableStateOf(ReadPrefs.cpm(ctx)) }
    val pageShownAt = remember { longArrayOf(System.currentTimeMillis()) }
    // 章节加载完成后要跳到的位置：>=0 比例；-1 末页；-2 查找第 n 个匹配
    val pending = remember { doubleArrayOf(0.0) }
    val pendingFind = remember { arrayOf<Pair<String, Int>?>(null) }
    val webRef = remember { arrayOf<WebView?>(null) }
    // 当前章节包装好的阅读页：WebView 请求 https://book.local/<章节路径>?__r=<序号> 时由拦截器直接返回
    val pageHtml = remember { arrayOf<String?>(null) }
    val pageSeq = remember { intArrayOf(0) }
    var pick by remember { mutableStateOf<Pick?>(null) }
    var openHl by remember { mutableStateOf<String?>(null) }
    val pendingHl = remember { arrayOf<String?>(null) }
    val tts = remember { Speaker(ctx) }
    // 听书同步：follow=朗读时自动翻到正在读的句子；用户手动翻页后暂停跟随，显示「回到朗读位置」
    var follow by remember { mutableStateOf(true) }
    val lastSpeakAt = remember { longArrayOf(0) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    LaunchedEffect(bookId) {
        val b = book ?: run { err = "书不在书架上了"; return@LaunchedEffect }
        try {
            content = withContext(Dispatchers.IO) { BookContent.open(Library.bookFile(ctx, b), b.format) }
            Library.update(ctx, b.id) { it.copy(lastReadAt = System.currentTimeMillis()) }
            if (cpm <= 0) cpm = ReadLogic.defaultCpm(content!!.cjk)
        } catch (e: Exception) { err = "打不开这本书：${e.message}" }
    }
    DisposableEffect(Unit) { onDispose { content?.close(); webRef[0]?.destroy() } }

    val c = content
    fun load(i: Int, at: Double) {
        val cc = content ?: return
        val idx = i.coerceIn(0, cc.chapterCount - 1)
        chapter = idx; pending[0] = at
        pageHtml[0] = ReaderHtml.wrap(cc.chapterHtml(idx), rs, curTheme())
        pageSeq[0]++
        // 11-07 真机：loadDataWithBaseURL 在部分 WebView 上会先走 data: 地址，被下面的外链拦截挡住显示“网页无法打开”。
        // 改为直接加载书内虚拟地址，由拦截器返回页面内容。
        val path = cc.chapterPath(idx).split('/').joinToString("/") { android.net.Uri.encode(it) }
        webRef[0]?.loadUrl("https://book.local/$path?__r=${pageSeq[0]}")
    }
    fun js(code: String) = webRef[0]?.evaluateJavascript(code, null)
    fun saveProgress() {
        val cc = content ?: return
        val pg = ReadLogic.bookFraction(cc.chapterChars, chapter, fraction)
        Library.update(ctx, bookId) { it.copy(chapter = chapter, fraction = fraction, progress = pg, lastReadAt = System.currentTimeMillis()) }
        book = Library.get(ctx, bookId)
    }
    fun listeningHere() = Listen.bookId == bookId && Listen.mode == Listen.Mode.TTS
    fun speakJs() {
        val s = Listen.current ?: return
        if (!listeningHere() || Listen.chapter != chapter) return
        lastSpeakAt[0] = System.currentTimeMillis()
        js("__speak(" + org.json.JSONObject.quote(s.text) + "," + s.occurrence + "," + follow + ")")
    }
    fun startListen(sentence: Int) {
        val b = Library.get(ctx, bookId) ?: return
        follow = true
        runCatching { Listen.startTts(ctx, b, chapter, sentence) }.onFailure { Toast.makeText(ctx, "朗读启动失败：${it.message}", Toast.LENGTH_LONG).show() }
    }
    fun startHere() {
        val cc = content ?: return
        webRef[0]?.evaluateJavascript("__firstVisible()") { v ->
            val off = v?.toIntOrNull() ?: 0
            val t = cc.chapterText(chapter)
            val ss = ReadLogic.splitSentences(t)
            startListen(ReadLogic.sentenceAt(ReadLogic.sentenceOffsets(t, ss), off))
        }
    }
    fun applySettings(s: ReadSettings) { rs = s; ReadPrefs.save(ctx, s); load(chapter, fraction) }

    DisposableEffect(rs.volumeKeys, c) {
        KeyHooks.handler = if (rs.volumeKeys && c != null) { e: KeyEvent ->
            if (menu || panel != Panel.NONE || pick != null || openHl != null) false
            else when (e.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> { if (e.action == KeyEvent.ACTION_DOWN) js("__next()"); true }
                KeyEvent.KEYCODE_VOLUME_UP -> { if (e.action == KeyEvent.ACTION_DOWN) js("__prev()"); true }
                else -> false
            }
        } else null
        onDispose { KeyHooks.handler = null }
    }

    BackHandler {
        when {
            pick != null -> pick = null
            openHl != null -> openHl = null
            panel != Panel.NONE -> panel = Panel.NONE
            menu -> menu = false
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        if (err != null || c == null) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = muted) }
                Text(err ?: "正在打开…", color = fg, fontSize = 15.sp)
            }
            return@Box
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp)) {
                Text(book?.title ?: "", color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(c.chapterTitle(chapter).trim(), color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { context ->
                    WebView(context).apply {
                        webRef[0] = this
                        setBackgroundColor(android.graphics.Color.parseColor(col.bg))
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_NEVER
                        keepScreenOn = rs.keepScreenOn
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportZoom(false)
                        settings.builtInZoomControls = false
                        settings.textZoom = 100
                        val main = Handler(Looper.getMainLooper())
                        addJavascriptInterface(object {
                            @JavascriptInterface fun ready(total: Int) = main.post {
                                pages = total
                                val hls = Notes.bookHighlights(ctx, bookId).filter { it.chapter == chapter }
                                if (hls.isNotEmpty()) js("__hl(" + org.json.JSONArray(hls.map { org.json.JSONObject().put("id", it.id).put("t", it.text).put("k", it.occurrence) }) + ")")
                                if (listeningHere() && Listen.chapter == chapter) main.postDelayed({ speakJs() }, 60)
                                val ph = pendingHl[0]
                                if (ph != null) { pendingHl[0] = null; js("__goHl(" + org.json.JSONObject.quote(ph) + ")"); return@post }
                                val pf = pendingFind[0]
                                when {
                                    pf != null -> { pendingFind[0] = null; js("__find(${org.json.JSONObject.quote(pf.first)}, ${pf.second})") }
                                    pending[0] < 0 -> js("__go(${total - 1}, false)")
                                    else -> js("__goFrac(${pending[0]})")
                                }
                            }.let {}
                            @JavascriptInterface fun page(p: Int, total: Int, f: Double) = main.post {
                                val now = System.currentTimeMillis()
                                if (p != pageNo) {
                                    val perPage = (content?.chapterChars?.getOrNull(chapter) ?: 0) / maxOf(1, total)
                                    val ncpm = ReadLogic.updateCpm(cpm, perPage, (now - pageShownAt[0]) / 1000.0)
                                    if (ncpm != cpm) { cpm = ncpm; ReadPrefs.saveCpm(ctx, ncpm) }
                                }
                                pageShownAt[0] = now
                                if (listeningHere() && Listen.playing && now - lastSpeakAt[0] > 1500 && p != pageNo) follow = false
                                pageNo = p; pages = total; fraction = f
                                saveProgress()
                            }.let {}
                            @JavascriptInterface fun tap(zone: Int) = main.post { if (pick != null || openHl != null) { pick = null; openHl = null } else { menu = !menu; panel = Panel.NONE } }.let {}
                            @JavascriptInterface fun word(json: String) = main.post {
                                runCatching { val o = org.json.JSONObject(json); pick = Pick(o.optString("word"), o.optString("sentence"), o.optInt("k"), o.optDouble("y", 0.5)) }
                                menu = false; openHl = null
                            }.let {}
                            @JavascriptInterface fun hl(id: String) = main.post { openHl = id; pick = null; menu = false }.let {}
                            @JavascriptInterface fun edge(dir: Int) = main.post {
                                val cc = content ?: return@post
                                if (dir > 0) {
                                    if (chapter + 1 < cc.chapterCount) load(chapter + 1, 0.0)
                                    else { Library.update(ctx, bookId) { it.copy(finished = true, progress = 1.0) }; Toast.makeText(ctx, "已经读到最后了", Toast.LENGTH_SHORT).show() }
                                } else {
                                    if (chapter > 0) load(chapter - 1, -1.0) else Toast.makeText(ctx, "已经是开头", Toast.LENGTH_SHORT).show()
                                }
                            }.let {}
                        }, "R")
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                                val u = req.url
                                val scheme = u.scheme ?: ""
                                // data:/about:/blob: 等是 WebView 自己的地址，交给它处理；只拦 http(s) 外链
                                if (scheme != "http" && scheme != "https") return null
                                if (u.host == "book.local") {
                                    val path = (u.path ?: "").trimStart('/')
                                    if (u.getQueryParameter("__r") != null) {
                                        val h = pageHtml[0] ?: ""
                                        return WebResourceResponse("text/html", "utf-8", 200, "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(h.toByteArray(Charsets.UTF_8)))
                                    }
                                    val data = content?.resource(path)
                                    if (data != null) return WebResourceResponse(content!!.mime(path), null, ByteArrayInputStream(data))
                                }
                                // 书里引用的网络资源一律不加载（不联网、不被追踪）
                                return WebResourceResponse("text/plain", "utf-8", 404, "blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                            }
                            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                                val u = req.url
                                val cc = content ?: return true
                                if (u.host == "book.local") {
                                    val path = (u.path ?: "").trimStart('/')
                                    val idx = (0 until cc.chapterCount).firstOrNull { cc.chapterPath(it) == path }
                                    if (idx != null) load(idx, 0.0)
                                } else {
                                    Toast.makeText(ctx, "书里的外部链接不打开", Toast.LENGTH_SHORT).show()
                                }
                                return true
                            }
                        }
                        post { load(chapter, fraction) }
                    }
                },
                update = { it.keepScreenOn = rs.keepScreenOn; it.setBackgroundColor(android.graphics.Color.parseColor(col.bg)) },
            )
            val left = ReadLogic.minutesLeft(c.chapterChars.getOrElse(chapter) { 0 }, fraction, if (cpm > 0) cpm else ReadLogic.defaultCpm(c.cjk))
            val bookPct = (ReadLogic.bookFraction(c.chapterChars, chapter, fraction) * 100).toInt()
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp)) {
                Text("本章还剩约 $left 分钟", color = muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("第 ${chapter + 1}/${c.chapterCount} 章 ${(fraction * 100).toInt()}% · 全书 $bookPct%", color = muted, fontSize = 11.sp)
            }
        }

        if (menu && panel == Panel.NONE) ReaderMenu(
            title = book?.title ?: "", bookPct = ReadLogic.bookFraction(c.chapterChars, chapter, fraction),
            marked = book?.bookmarks?.any { it.chapter == chapter && kotlin.math.abs(it.fraction - fraction) < 0.02 } == true,
            onBack = onBack,
            onSearch = { panel = Panel.SEARCH },
            onMark = {
                val b = book ?: return@ReaderMenu
                val existing = b.bookmarks.firstOrNull { it.chapter == chapter && kotlin.math.abs(it.fraction - fraction) < 0.02 }
                val t = c.chapterText(chapter)
                val pos = (t.length * fraction).toInt().coerceIn(0, maxOf(0, t.length - 1))
                val snip = t.substring(pos, minOf(t.length, pos + 50)).trim()
                Library.update(ctx, bookId) {
                    it.copy(bookmarks = if (existing != null) it.bookmarks - existing else it.bookmarks + Library.Bookmark(chapter, fraction, snip, System.currentTimeMillis()))
                }
                book = Library.get(ctx, bookId)
                Toast.makeText(ctx, if (existing != null) "已删除书签" else "已加书签", Toast.LENGTH_SHORT).show()
            },
            onSeek = { f ->
                val total = c.chapterChars.sum().toDouble()
                var acc = 0.0; var idx = 0
                while (idx < c.chapterCount - 1 && acc + c.chapterChars[idx] < f * total) { acc += c.chapterChars[idx]; idx++ }
                val within = ((f * total - acc) / c.chapterChars[idx]).coerceIn(0.0, 1.0)
                load(idx, within)
            },
            onPrevCh = { if (chapter > 0) load(chapter - 1, 0.0) },
            onNextCh = { if (chapter + 1 < c.chapterCount) load(chapter + 1, 0.0) },
            onPanel = { panel = it },
        )

        when (panel) {
            Panel.TOC -> Sheet("目录", { panel = Panel.NONE }) {
                LazyColumn(Modifier.fillMaxWidth()) {
                    val items = c.toc.ifEmpty { (0 until c.chapterCount).map { "第 ${it + 1} 部分" to it } }
                    itemsIndexed(items) { _, (t, i) ->
                        Text(t.ifBlank { "第 ${i + 1} 部分" }, color = if (i == chapter) C.Gold else C.Text, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { panel = Panel.NONE; menu = false; load(i, 0.0) }.padding(vertical = 12.dp))
                    }
                }
            }
            Panel.MARKS -> Sheet("书签", { panel = Panel.NONE }) {
                val marks = book?.bookmarks.orEmpty().sortedWith(compareBy({ it.chapter }, { it.fraction }))
                if (marks.isEmpty()) Text("还没有书签。阅读时点屏幕中间，再点右上角的书签图标。", color = C.Muted, fontSize = 14.sp)
                LazyColumn(Modifier.fillMaxWidth()) {
                    itemsIndexed(marks) { _, m ->
                        Column(Modifier.fillMaxWidth().clickable { panel = Panel.NONE; menu = false; load(m.chapter, m.fraction) }.padding(vertical = 10.dp)) {
                            Text(c.chapterTitle(m.chapter).trim(), color = C.Muted, fontSize = 12.sp)
                            Text(m.snippet.ifBlank { "（第 ${m.chapter + 1} 章 ${(m.fraction * 100).toInt()}%）" }, color = C.Text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Panel.STYLE -> Sheet("排版", { panel = Panel.NONE }) { StylePanel(rs) { applySettings(it) } }
            Panel.SEARCH -> Sheet("全文搜索", { panel = Panel.NONE }) {
                var q by remember { mutableStateOf("") }
                var hits by remember { mutableStateOf<List<BookContent.SearchHit>>(emptyList()) }
                var searched by remember { mutableStateOf(false) }
                OutlinedTextField(q, { q = it }, singleLine = true, label = { Text("输入要找的词") }, modifier = Modifier.fillMaxWidth(),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                        scope.launch { hits = withContext(Dispatchers.Default) { c.search(q) }; searched = true }
                    }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search))
                Text(if (!searched) "回车开始搜索" else "找到 ${hits.size} 处" + if (hits.size >= 200) "（只显示前 200 处）" else "", color = C.Muted, fontSize = 12.sp)
                LazyColumn(Modifier.fillMaxWidth()) {
                    itemsIndexed(hits) { _, h ->
                        Column(Modifier.fillMaxWidth().clickable {
                            val nth = hits.filter { it.chapter == h.chapter }.indexOf(h)
                            panel = Panel.NONE; menu = false
                            pendingFind[0] = q to nth
                            load(h.chapter, 0.0)
                        }.padding(vertical = 10.dp)) {
                            Text(c.chapterTitle(h.chapter).trim(), color = C.Muted, fontSize = 12.sp)
                            Text(h.snippet, color = C.Text, fontSize = 14.sp)
                        }
                    }
                }
            }
            Panel.NOTES -> Sheet("本书笔记", { panel = Panel.NONE }) {
                val list = remember { Notes.bookHighlights(ctx, bookId) }
                if (list.isEmpty()) Text("还没有划线。长按书里的一句话，在弹出的卡片里点「划线」。", color = C.Muted, fontSize = 14.sp)
                else Text("共 ${list.size} 条 · 导出", color = C.Gold, fontSize = 13.sp, modifier = Modifier.clickable {
                    shareText(ctx, "《${book?.title}》笔记", Notes.exportBook(ctx, bookId, book?.title ?: ""))
                }.padding(vertical = 6.dp))
                LazyColumn(Modifier.fillMaxWidth()) {
                    itemsIndexed(list) { _, h ->
                        Column(Modifier.fillMaxWidth().clickable { panel = Panel.NONE; menu = false; pendingHl[0] = h.id; load(h.chapter, h.fraction) }.padding(vertical = 10.dp)) {
                            Text(h.chapterTitle.trim(), color = C.Muted, fontSize = 12.sp)
                            Text(h.text, color = C.Text, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (h.note.isNotBlank()) Text("笔记：" + h.note, color = C.Gold, fontSize = 13.sp)
                        }
                    }
                }
            }
            Panel.LISTEN -> Sheet("听书", { panel = Panel.NONE }) {
                val b = book
                if (b != null) ListenPanel(b, chapter) { panel = Panel.NONE; menu = false; startHere() }
            }
            Panel.NONE -> {}
        }

        // 朗读到新句子：同章就高亮，跨章就先翻到那一章
        LaunchedEffect(Listen.tick, Listen.chapter, Listen.bookId, Listen.mode) {
            if (!listeningHere()) return@LaunchedEffect
            if (Listen.chapter != chapter) { if (follow) load(Listen.chapter, 0.0) } else speakJs()
        }
        if (listeningHere() && panel == Panel.NONE && !menu) {
            Row(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 14.dp, bottom = 30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!follow) Text("回到朗读位置", color = C.Bg, fontSize = 13.sp, modifier = Modifier.background(C.Gold, RoundedCornerShape(16.dp)).clickable {
                    follow = true; if (Listen.chapter != chapter) load(Listen.chapter, 0.0) else speakJs()
                }.padding(horizontal = 12.dp, vertical = 8.dp))
                Text(if (Listen.playing) "暂停朗读" else "继续朗读", color = C.Text, fontSize = 13.sp, modifier = Modifier.background(C.Surface, RoundedCornerShape(16.dp)).clickable { Listen.toggle() }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }

        pick?.let { pk ->
            WordCard(pk, bookTitle = book?.title ?: "", tts = tts,
                onClose = { pick = null },
                onListenHere = {
                    val t = c.chapterText(chapter); val ss = ReadLogic.splitSentences(t)
                    val head = pk.sentence.take(40)
                    val i = ss.indexOfFirst { it.text.contains(head) || head.contains(it.text.take(40)) }.coerceAtLeast(0)
                    pick = null; startListen(i)
                },
                onAddWord = { e ->
                    Notes.addWord(ctx, Notes.Word(e.word, e.phonetic, ReadLogic.shortMeaning(e.meaning), pk.sentence, bookId, book?.title ?: "", chapter, fraction, System.currentTimeMillis()))
                },
                onHighlight = { tr ->
                    val id = java.lang.Long.toString(System.currentTimeMillis(), 36)
                    Notes.addHighlight(ctx, Notes.Highlight(id, bookId, book?.title ?: "", chapter, c.chapterTitle(chapter), pk.sentence, pk.occurrence, fraction, translation = tr, at = System.currentTimeMillis()))
                    js("__hl([" + org.json.JSONObject().put("id", id).put("t", pk.sentence).put("k", pk.occurrence) + "])")
                    pick = null
                    Toast.makeText(ctx, "已划线，可在菜单「笔记」里查看", Toast.LENGTH_SHORT).show()
                })
        }
        openHl?.let { id ->
            val h = Notes.highlights(ctx).firstOrNull { it.id == id }
            if (h == null) {
                LaunchedEffect(id) { openHl = null }
            } else {
                HighlightCard(h, onClose = { openHl = null }, onDelete = {
                    Notes.removeHighlight(ctx, id); js("__unhl(" + org.json.JSONObject.quote(id) + ")"); openHl = null
                })
            }
        }
    }
}

@Composable
private fun ReaderMenu(
    title: String, bookPct: Double, marked: Boolean,
    onBack: () -> Unit, onSearch: () -> Unit, onMark: () -> Unit, onSeek: (Double) -> Unit,
    onPrevCh: () -> Unit, onNextCh: () -> Unit, onPanel: (Panel) -> Unit,
) {
    var seek by remember(bookPct) { mutableStateOf(bookPct.toFloat()) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(C.Surface).statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回书架", tint = C.Muted) }
            Text(title, color = C.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "全文搜索", tint = C.Text) }
            IconButton(onClick = onMark) { Icon(if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (marked) "删除书签" else "加书签", tint = C.Gold) }
        }
        Box(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("上一章", color = C.Text, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onPrevCh).padding(10.dp))
                Slider(seek, { seek = it }, onValueChangeFinished = { onSeek(seek.toDouble()) }, modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = C.Gold, activeTrackColor = C.Gold))
                Text("下一章", color = C.Text, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onNextCh).padding(10.dp))
            }
            Text("全书 ${(seek * 100).toInt()}%", color = C.Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(Modifier.fillMaxWidth()) {
                listOf("目录" to Panel.TOC, "书签" to Panel.MARKS, "笔记" to Panel.NOTES, "听书" to Panel.LISTEN, "排版" to Panel.STYLE).forEach { (t, p) ->
                    Box(Modifier.weight(1f).heightIn(min = 52.dp).clickable { onPanel(p) }, contentAlignment = Alignment.Center) {
                        Text(t, color = C.Text, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Sheet(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0x99000000))) {
        Box(Modifier.fillMaxWidth().weight(0.15f).clickable(onClick = onClose))
        Column(
            Modifier.fillMaxWidth().weight(0.85f).background(C.Bg, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = C.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text("关闭", color = C.Muted, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onClose).padding(10.dp))
            }
            content()
        }
    }
}

@Composable
private fun StylePanel(s: ReadSettings, onChange: (ReadSettings) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("字号", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
            Text("A−", color = C.Text, fontSize = 15.sp, modifier = Modifier.border(1.dp, C.Line, RoundedCornerShape(10.dp)).clickable { onChange(s.copy(fontSize = (s.fontSize - 1).coerceAtLeast(12))) }.padding(horizontal = 16.dp, vertical = 8.dp))
            Text("${s.fontSize}", color = C.Text, fontSize = 16.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("A+", color = C.Text, fontSize = 18.sp, modifier = Modifier.border(1.dp, C.Line, RoundedCornerShape(10.dp)).clickable { onChange(s.copy(fontSize = (s.fontSize + 1).coerceAtMost(34))) }.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        Label("行距")
        Segmented(listOf("1.4" to "紧凑", "1.7" to "标准", "2.0" to "宽松", "2.3" to "很宽"), s.lineHeight.toString()) { onChange(s.copy(lineHeight = it.toDouble())) }
        Label("边距")
        Segmented(listOf("12" to "窄", "22" to "标准", "34" to "宽"), s.margin.toString()) { onChange(s.copy(margin = it.toInt())) }
        Label("字体")
        Segmented(listOf("serif" to "衬线", "sans" to "无衬线", "system" to "书自带"), s.font) { onChange(s.copy(font = it)) }
        Label("翻页")
        Segmented(listOf("paged" to "左右翻页", "scroll" to "上下滚动"), if (s.paged) "paged" else "scroll") { onChange(s.copy(paged = it == "paged")) }
        Label("主题")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ReadSettings.themes.forEach { (k, v) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onChange(s.copy(theme = k)) }) {
                    Box(
                        Modifier.size(48.dp).background(hex(v.second.bg), CircleShape)
                            .border(2.dp, if (s.theme == k) C.Gold else C.Line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("Aa", color = hex(v.second.fg), fontSize = 15.sp) }
                    Text(v.first, color = C.Muted, fontSize = 12.sp)
                }
            }
        }
        ToggleRow("音量键翻页", null, s.volumeKeys) { onChange(s.copy(volumeKeys = it)) }
        ToggleRow("点屏幕左右两侧翻页", "中间点一下呼出菜单", s.tapSides) { onChange(s.copy(tapSides = it)) }
        ToggleRow("点一下单词就查词", "默认长按查词；打开后点单词直接查，点空白处才翻页", s.tapLookup) { onChange(s.copy(tapLookup = it)) }
        ToggleRow("阅读时屏幕常亮", null, s.keepScreenOn) { onChange(s.copy(keepScreenOn = it)) }
        ToggleRow("晚上 22 点到早上 6 点自动夜间", null, s.autoNight) { onChange(s.copy(autoNight = it)) }
    }
}

@Composable
private fun Label(t: String) = Text(t, color = C.Muted, fontSize = 13.sp)

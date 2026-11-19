package com.eric.toolbox.modules.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.eric.toolbox.modules.zlib.ZlibModule
import com.eric.toolbox.ui.C

/**
 * 内置浏览器界面。
 *
 * 结构就三行：地址栏、网页、底下一排键。不搞多标签、不搞书签栏 ——
 * 那两样一上，代码量和出问题的面就翻倍，而这里要解决的只是「别跳出去」。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun BrowserScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val focusMgr = LocalFocusManager.current

    var target by remember { mutableStateOf(BrowserModule.HOME) }
    // 序号：搜同一个词两次也要真的重新加载，光比地址会以为是没响应
    var seq by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(BrowserModule.HOME) }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var canFwd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    val web = remember { arrayOfNulls<WebView>(1) }
    // 已经加载过的「序号|地址」。用数组而不是 state：只做标记，不该触发重组。
    val loaded = remember { arrayOfNulls<String>(1) }
    val focus = remember { FocusRequester() }
    val bgArgb = remember { C.Bg.toArgb() }

    fun navigate(raw: String) {
        target = BrowserModule.toTarget(raw)
        seq += 1
        editing = false
        focusMgr.clearFocus()
    }

    BackHandler {
        when {
            editing -> {
                editing = false
                focusMgr.clearFocus()
            }
            web[0]?.canGoBack() == true -> web[0]?.goBack()
            else -> onBack()
        }
    }

    LaunchedEffect(editing) {
        if (editing) runCatching { focus.requestFocus() }
    }

    // 离开就把 WebView 收掉。不 destroy 会漏内存，WebView 是漏得最凶的控件之一。
    DisposableEffect(Unit) {
        onDispose {
            web[0]?.let { w ->
                runCatching {
                    w.stopLoading()
                    w.webChromeClient = null
                    (w.parent as? ViewGroup)?.removeView(w)
                    w.destroy()
                }
            }
            web[0] = null
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 2.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    when {
                        editing -> {
                            editing = false
                            focusMgr.clearFocus()
                        }
                        web[0]?.canGoBack() == true -> web[0]?.goBack()
                        else -> onBack()
                    }
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = C.Muted, modifier = Modifier.size(20.dp))
            }

            Box(
                Modifier.weight(1f).height(38.dp)
                    .background(C.Surface, RoundedCornerShape(19.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (editing) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = TextStyle(color = C.Text, fontSize = 14.sp),
                        cursorBrush = SolidColor(C.Text),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigate(input) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                } else {
                    Text(
                        BrowserModule.pretty(shown),
                        color = C.Muted,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable {
                            input = shown
                            editing = true
                        },
                    )
                }
            }

            // 直接跳到 zlib 模块里配的那个站点，不用再记域名
            TextButton(
                onClick = { navigate(ZlibModule.current(ctx) + "/") },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text("书库", color = C.Muted, fontSize = 13.sp)
            }
        }

        // 进度条占位始终存在，加载完成时不会让网页往上跳 2dp
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (progress in 1..99) {
                Box(Modifier.fillMaxWidth(progress / 100f).height(2.dp).background(C.Gold))
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { c ->
                newWebView(
                    ctx = c,
                    bg = bgArgb,
                    onUrl = { shown = it },
                    onProgress = { progress = it },
                    onNav = { b, f ->
                        canBack = b
                        canFwd = f
                    },
                ).also { web[0] = it }
            },
            update = { w ->
                web[0] = w
                // 只有目标真的变了才加载。update 每次重组都会跑，
                // 这里不设闸的话会无限刷新，页面永远停在开头。
                val key = "$seq|$target"
                if (loaded[0] != key) {
                    loaded[0] = key
                    w.loadUrl(target)
                }
            },
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavKey(Icons.AutoMirrored.Filled.ArrowBack, canBack) { web[0]?.goBack() }
            NavKey(Icons.AutoMirrored.Filled.ArrowForward, canFwd) { web[0]?.goForward() }
            NavKey(Icons.Filled.Refresh, true) { web[0]?.reload() }
            NavKey(Icons.Filled.Home, true) { navigate(BrowserModule.HOME) }
            NavKey(Icons.Filled.OpenInNew, true) {
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(shown))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavKey(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        Icon(
            icon,
            null,
            tint = if (enabled) C.Muted else C.Line,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * WebView 的各项设置。单独拎出来是因为这里每一条都有原因，
 * 删掉任意一条都会以「某个站用不了」的形式表现出来，很难反查。
 */
@SuppressLint("SetJavaScriptEnabled")
private fun newWebView(
    ctx: Context,
    bg: Int,
    onUrl: (String) -> Unit,
    onProgress: (Int) -> Unit,
    onNav: (Boolean, Boolean) -> Unit,
): WebView {
    val wv = WebView(ctx)
    wv.setBackgroundColor(bg)
    wv.isVerticalScrollBarEnabled = false

    wv.settings.apply {
        javaScriptEnabled = true
        // Cloudflare 的校验页和多数现代站点都要 localStorage
        domStorageEnabled = true
        databaseEnabled = true
        // target=_blank 的链接改在当前窗口打开。
        // 开成 true 又没实现 onCreateWindow，Z-Library 的下载按钮会「点了没反应」，
        // 这是内置浏览器最容易被判定成「坏了」的原因。
        setSupportMultipleWindows(false)
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        loadWithOverviewMode = true
        useWideViewPort = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        // 有些站的视频要用户点一下才播，这里放开，减少「点了也不动」
        mediaPlaybackRequiresUserGesture = false
        cacheMode = WebSettings.LOAD_DEFAULT
        userAgentString = BrowserModule.userAgent(ctx)
    }

    CookieManager.getInstance().setAcceptCookie(true)
    // cf_clearance 是第三方 Cookie；关着它，Cloudflare 的验证永远转圈
    CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

    wv.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
            val scheme = (req.url.scheme ?: "").lowercase()
            if (scheme != "http" && scheme != "https") {
                // intent:// market:// tel: mailto: 这些 WebView 处理不了，交给系统
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, req.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            onUrl(url ?: "")
            onProgress(3)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            onUrl(url ?: "")
            onProgress(100)
            onNav(view.canGoBack(), view.canGoForward())
            // Cookie 是异步落盘的。过了验证不立刻刷一次，进程一被杀就白过，
            // 下次进 Z-Library 还得重新点一遍「我不是机器人」。
            runCatching { CookieManager.getInstance().flush() }
        }
    }

    wv.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            onProgress(newProgress)
            // 有些站不触发 onPageFinished（长轮询、SPA 路由），这里补一次
            if (newProgress >= 100) onNav(view.canGoBack(), view.canGoForward())
        }
    }

    wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        if (url.isNullOrBlank()) return@setDownloadListener
        val name = BrowserDownload.enqueue(ctx, url, userAgent, contentDisposition, mimeType)
        Toast.makeText(
            ctx,
            if (name != null) "开始下载：$name" else "这个下载没能开始，可以点右下角换系统浏览器试",
            Toast.LENGTH_SHORT,
        ).show()
    }

    return wv
}

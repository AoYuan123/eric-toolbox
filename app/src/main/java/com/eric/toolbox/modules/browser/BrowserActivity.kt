package com.eric.toolbox.modules.browser

import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.ThemeManager
import com.eric.toolbox.ui.ToolboxTheme

/**
 * 内置浏览器的宿主 Activity。
 *
 * 为什么单独做一个 Activity，而不是在主界面里加一条路由：
 * 全工程有七八处会「点开一个网页」——新闻原文、法律条文、临床试验、网盘链接、
 * 读书外链、Z-Library 搜索结果、检查更新页——它们分散在各个模块里，
 * 拿不到主界面的导航栈。做成 Activity 之后，任何地方一行
 * `BrowserModule.open(ctx, url)` 就能在 App 里打开，**永远不跳外部浏览器**。
 *
 * 影视播放页 PlayerActivity 也是这个路子，架构上不突兀。
 */
class BrowserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 独立 Activity 必须自己应用主题：App.onCreate 里不做这件事，
        // 不调的话 C.palette 还是上一次的值，深色/浅色会错。
        ThemeManager.apply(this)
        applySystemBars()

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        setContent {
            ToolboxTheme {
                BrowserScreen(onBack = { finish() }, startUrl = url)
            }
        }
    }

    private fun applySystemBars() {
        val light = C.palette.isLight
        val style = if (light) SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
        else SystemBarStyle.dark(AColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    companion object {
        private const val EXTRA_URL = "url"

        /** 打开；调用方拿不到 Activity 时返回 false。 */
        fun open(ctx: Context, url: String): Boolean = runCatching {
            ctx.startActivity(
                Intent(ctx, BrowserActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }
}

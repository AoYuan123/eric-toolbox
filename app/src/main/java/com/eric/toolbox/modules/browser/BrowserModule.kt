package com.eric.toolbox.modules.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebSettings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.Category
import com.eric.toolbox.core.ToolModule
import com.eric.toolbox.ui.C

/**
 * 内置浏览器。
 *
 * 为什么要有它：搜书、查资料时不该每次都跳出 App 再跳回来，系统浏览器里
 * 一堆标签页，回来还得重新找。这里就是一个够用的壳子。
 *
 * 关于 Cloudflare（重要，别改回去）：
 * Z-Library 这类站前置了 Cloudflare 的浏览器校验，纯 HTTP 请求过不去，WebView 能过。
 * 但 WebView 默认的 UA 里带 `; wv` 和 `Version/4.0` 两个标记，等于自报「我是内嵌控件」，
 * 很容易被判成机器人，卡在「请稍候」无限循环。
 *
 * 所以 [userAgent] 的做法不是「伪装成别的浏览器」，而是**如实**：
 * 拿系统真实的 WebView UA，只剥掉那两个标记，Chrome 版本号原样保留。
 * 这样声称的版本和实际内核永远一致 —— 版本对不上才是最强的机器人信号
 * （见过一个案例：App 里写死 Chrome 91，实际内核是 131，登录永远过不去）。
 */
object BrowserModule : ToolModule {
    override val id = "browser"
    override val name = "浏览器"
    override val category = Category.TOOL
    override val icon: ImageVector = Icons.Outlined.Public

    /** 首页。cn.bing.com 国内直连，搜索这一段不需要代理。 */
    const val HOME = "https://cn.bing.com"

    fun searchUrl(q: String): String = "https://cn.bing.com/search?q=" + Uri.encode(q.trim())

    /**
     * 判断用户敲的是网址还是搜索词。
     * 只认「没有空格 + 中间带点」，所以「三体 刘慈欣」会去搜索，
     * 「z-library.sk」直接当网址打开。
     */
    fun isUrlLike(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty() || t.any { it == ' ' || it == '\n' }) return false
        if (t.startsWith("http://") || t.startsWith("https://")) return true
        val host = t.substringBefore('/').substringBefore('?')
        if (host.isEmpty()) return false
        return host.contains('.') && !host.startsWith('.') && !host.endsWith('.')
    }

    fun toTarget(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return HOME
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        return if (isUrlLike(t)) "https://$t" else searchUrl(t)
    }

    /**
     * 把系统 WebView 的 UA 收拾成一个「正常的 Chrome」。见文件头的说明。
     *
     * 剥掉的三样：
     * - `; wv`      —— WebView 标记，最直接的暴露
     * - ` Version/4.0` —— 老式内嵌浏览器的写法
     * - ` Build/xxx` —— WebView 特有，真 Chrome 的 UA 里没有这段
     */
    fun userAgent(ctx: Context): String {
        val raw = runCatching { WebSettings.getDefaultUserAgent(ctx) }.getOrNull()
        if (raw.isNullOrBlank()) {
            // 兜底：拿不到系统 UA 时给一个自洽的，不要去抄某个具体机型
            return "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        }
        return raw
            .replace("; wv)", ")")
            .replace(Regex(" Build/[^;)]+"), "")
            .replace(" Version/4.0", "")
            .trim()
    }

    /** 地址栏显示用：去掉协议头；太长时掐中间，别把域名尾巴盖住。 */
    fun pretty(url: String): String {
        val s = url.removePrefix("https://").removePrefix("http://").removeSuffix("/")
        if (s.isEmpty()) return HOME.removePrefix("https://")
        return if (s.length <= 56) s else s.take(30) + "…" + s.takeLast(22)
    }

    @Composable
    override fun HomeSummary() {
        Text("必应搜索 · 站内打开", color = C.Muted, fontSize = 12.sp)
    }

    @Composable
    override fun Screen(onBack: () -> Unit) = BrowserScreen(onBack)
}

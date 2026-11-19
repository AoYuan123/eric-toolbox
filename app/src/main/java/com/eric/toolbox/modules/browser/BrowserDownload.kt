package com.eric.toolbox.modules.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import java.net.URLDecoder

/**
 * 把网页上的下载交给系统下载器。
 *
 * 为什么不自己写下载线程：系统下载器有断点续传、通知栏进度、失败重试，
 * 而且不用申请存储权限。自己写一遍只会更差。
 *
 * 但有两样必须手动带过去，否则 Z-Library 这类站会直接 403：
 * - **Cookie**：WebView 里过了 Cloudflare、登了账号，那些状态全在 Cookie 里，
 *   下载请求是另一个客户端发出的，不带就等于换了个人。
 * - **UA**：有些站会把 UA 和 Cookie 绑在一起校验（cf_clearance 就是绑 UA 的）。
 */
object BrowserDownload {

    /** 文件名里不能出现的字符。Windows 那套限制，DownloadManager 在部分机型上也认。 */
    private val ILLEGAL = Regex("""[\\/:*?"<>|\x00-\x1F]""")

    /**
     * 返回最终用的文件名（给界面提示用），失败返回 null。
     * 失败不抛异常：下载起不来是常事（链接过期、需要登录），不该让浏览器崩。
     */
    fun enqueue(
        ctx: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mime: String?,
    ): String? = runCatching {
        val name = fileName(url, contentDisposition, mime)
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)
                ?.takeIf { it.isNotBlank() }
                ?.let { addRequestHeader("Cookie", it) }
            if (!mime.isNullOrBlank()) setMimeType(mime)
            setTitle(name)
            setDescription("来自百宝箱浏览器")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 29 起公共下载目录不需要任何权限
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            } else {
                // 26~28 没权限写公共目录，退到 App 自己的外部目录，至少能存下来
                setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, name)
            }
        }
        ctx.getSystemService(DownloadManager::class.java).enqueue(req)
        name
    }.getOrNull()

    /**
     * 文件名优先级：Content-Disposition > URL 最后一段 > 兜底名。
     * Z-Library 的真实书名只在 Content-Disposition 里，URL 那边是一串 id，
     * 所以不解析它的话，下载下来就是「1234567.epub」，过两天全忘了是什么书。
     */
    private fun fileName(url: String, cd: String?, mime: String?): String {
        parseDisposition(cd)?.takeIf { it.isNotBlank() }?.let { return safe(it, mime) }

        val last = Uri.parse(url).lastPathSegment?.let { seg ->
            runCatching { URLDecoder.decode(seg, "UTF-8") }.getOrDefault(seg)
        }
        if (!last.isNullOrBlank() && last.contains('.')) return safe(last, mime)
        return safe(last?.takeIf { it.isNotBlank() } ?: "download", mime)
    }

    /**
     * `filename*=UTF-8''%E4%B8%89%E4%BD%93.epub` 是编码版，优先用它（中文书名都走这个）；
     * 老服务器只给 `filename="book.epub"`，那就退回去。
     */
    private fun parseDisposition(cd: String?): String? {
        if (cd.isNullOrBlank()) return null

        val starred = Regex("""filename\*\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
            .find(cd)?.groupValues?.get(1)?.trim()?.trim('"')
        if (!starred.isNullOrBlank()) {
            // 形如 UTF-8''xxx 或 UTF-8'zh'xxx，引号里是字符集，取最后一个 ' 之后
            val raw = if (starred.contains("''")) starred.substringAfter("''") else starred.substringAfterLast('\'')
            val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()
            if (!decoded.isNullOrBlank()) return decoded
        }

        return Regex("""filename\s*=\s*([^";]+)""", RegexOption.IGNORE_CASE)
            .find(cd)?.groupValues?.get(1)?.trim()?.trim('"')
    }

    /**
     * 清掉非法字符，并在没扩展名时按 mime 补一个。
     * 不补的话，DownloadManager 会存成一个没有后缀的文件，点开哪个 App 都不认。
     */
    private fun safe(name: String, mime: String?): String {
        val cleaned = name.replace(ILLEGAL, "_").trim().take(120)
        val base = cleaned.ifBlank { "download" }
        if (base.contains('.')) return base
        val ext = when {
            mime == null -> ""
            mime.contains("epub", true) -> ".epub"
            mime.contains("pdf", true) -> ".pdf"
            mime.contains("zip", true) -> ".zip"
            mime.contains("mobi", true) -> ".mobi"
            mime.contains("text", true) -> ".txt"
            else -> ""
        }
        return base + ext
    }
}

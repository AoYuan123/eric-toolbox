package com.eric.toolbox.modules.vod.core

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

/**
 * 影视板块的 HTTP 客户端。
 *
 * 两个地方跟普通请求不一样：
 *  1. 站点常常靠 UA / Referer 放行，所以默认伪装成浏览器，并允许每个请求覆盖请求头。
 *  2. 报错信息要能直接给用户看，所以统一把异常翻成中文。
 *
 * 注意：明文 http 自 v1.0.22 起已放开（影视源大多是 http，用户拍板），
 * 理由写在 AndroidManifest 的 usesCleartextTraffic 注释里，别改回 false。
 */
object VodHttp {

    /** 默认伪装成桌面浏览器，很多源站只认这个 */
    const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    class HttpError(val code: Int, message: String) : Exception(message)

    /**
     * GET 一个地址，返回响应正文字符串。
     *
     * @param headers 额外请求头，会覆盖默认值（用来传 Referer、自定义 UA）
     */
    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 15_000,
    ): String {
        require(url.startsWith("http://") || url.startsWith("https://")) { "地址必须以 http 开头" }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", headers["User-Agent"]?.ifBlank { null } ?: DEFAULT_UA)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            headers.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank() && !k.equals("Accept-Encoding", true)) setRequestProperty(k, v)
            }
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw HttpError(code, "源站返回 HTTP $code")
            readBody(conn)
        } finally {
            conn.disconnect()
        }
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream: InputStream = conn.inputStream
        val enc = conn.contentEncoding.orEmpty()
        val body = if (enc.equals("gzip", ignoreCase = true)) GZIPInputStream(stream) else stream
        return String(body.readBytes(), charsetOf(conn.contentType))
    }

    /**
     * GET 二进制。Spider 插件是 zip/dex，按字符串解码会直接毁掉内容，
     * 所以单独开一条字节通道，不做任何字符集处理。
     */
    fun getBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 60_000,
    ): ByteArray {
        require(url.startsWith("http://") || url.startsWith("https://")) { "地址必须以 http 开头" }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", headers["User-Agent"]?.ifBlank { null } ?: DEFAULT_UA)
            setRequestProperty("Accept", "*/*")
            headers.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank() && !k.equals("Accept-Encoding", true)) setRequestProperty(k, v)
            }
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw HttpError(code, "源站返回 HTTP $code")
            val stream: InputStream = conn.inputStream
            val enc = conn.contentEncoding.orEmpty()
            val body = if (enc.equals("gzip", ignoreCase = true)) GZIPInputStream(stream) else stream
            body.readBytes()
        } finally {
            conn.disconnect()
        }
    }

    /** 从 Content-Type 里取 charset，取不到就按 UTF-8。老 CMS 常见 GBK。 */
    private fun charsetOf(contentType: String?): Charset {
        if (contentType.isNullOrBlank()) return Charsets.UTF_8
        val m = Regex("charset\\s*=\\s*([^;\\s]+)", RegexOption.IGNORE_CASE).find(contentType)
            ?: return Charsets.UTF_8
        val name = m.groupValues[1].trim().trim('"')
        return try {
            Charset.forName(name)
        } catch (e: Exception) {
            Charsets.UTF_8
        }
    }

    /** 把底层异常翻成人看得懂的中文说明。 */
    fun messageOf(e: Throwable): String = when (e) {
        is HttpError -> e.message ?: "请求失败"
        is SocketTimeoutException -> "连接超时"
        is UnknownHostException -> "域名解析失败，检查网络或换一个源"
        is java.net.ConnectException -> "连不上，源站可能已关闭"
        is javax.net.ssl.SSLHandshakeException -> "SSL 握手失败，源站证书有问题"
        is IllegalArgumentException -> e.message ?: "参数有误"
        else -> {
            val msg = e.message.orEmpty()
            when {
                msg.contains("Cleartext", ignoreCase = true) ->
                    "系统拦截了明文 http（Manifest 的 usesCleartextTraffic 被改过？）"
                msg.isBlank() -> e.javaClass.simpleName
                else -> msg
            }
        }
    }
}

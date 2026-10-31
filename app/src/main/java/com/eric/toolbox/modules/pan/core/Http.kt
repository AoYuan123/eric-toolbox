package com.eric.toolbox.modules.pan.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class HttpException(val status: Int, message: String) : Exception(message)

/**
 * 极简 HTTP 封装，只依赖 JDK，不需要 OkHttp。
 * 如果你项目里已有 OkHttp，可以整体替换实现，接口保持不变即可。
 *
 * 全部是 suspend 方法，内部切到 IO 线程，不要在主线程直接调用阻塞版本。
 */
object Http {

    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

    /** 错误响应体最多读这么多，防止日志炸掉 */
    private const val MAX_ERR_BODY = 8 * 1024

    suspend fun getText(
        url: String,
        timeoutMs: Int = 30_000,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        request(url, "GET", null, timeoutMs, headers)
    }

    suspend fun getJson(
        url: String,
        timeoutMs: Int = 30_000,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject = JSONObject(getText(url, timeoutMs, headers))

    suspend fun postJson(
        url: String,
        body: JSONObject,
        timeoutMs: Int = 30_000,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject = JSONObject(postText(url, body.toString(), timeoutMs, headers))

    suspend fun postText(
        url: String,
        body: String,
        timeoutMs: Int = 30_000,
        headers: Map<String, String> = emptyMap(),
        contentType: String = "application/json",
    ): String = withContext(Dispatchers.IO) {
        request(url, "POST", body, timeoutMs, headers, contentType)
    }

    private fun request(
        url: String,
        method: String,
        body: String?,
        timeoutMs: Int,
        headers: Map<String, String>,
        contentType: String = "application/json",
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json, text/html, */*")
            setRequestProperty("Accept-Encoding", "gzip")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            if (method == "POST" && body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                // 429/412/503 这些是风控信号，调用方要按「强风控」处理
                val errText = runCatching {
                    conn.errorStream?.let { readAll(it, conn.contentEncoding, MAX_ERR_BODY) }
                }.getOrNull() ?: ""
                throw HttpException(code, "HTTP $code ${errText.take(180)}")
            }
            val stream = conn.inputStream ?: throw HttpException(code, "响应体为空")
            return readAll(stream, conn.contentEncoding, Int.MAX_VALUE)
        } finally {
            conn.disconnect()
        }
    }

    private fun readAll(
        raw: java.io.InputStream,
        encoding: String?,
        limit: Int,
    ): String {
        val stream = if (encoding?.contains("gzip", ignoreCase = true) == true) {
            GZIPInputStream(raw)
        } else {
            raw
        }
        stream.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                val writable = minOf(n, limit - total)
                if (writable > 0) {
                    out.write(buf, 0, writable)
                    total += writable
                }
                if (total >= limit) break
            }
            return out.toString("UTF-8")
        }
    }
}

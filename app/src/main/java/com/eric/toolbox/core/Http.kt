package com.eric.toolbox.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/** 极简 HTTP 工具：只允许 https，带超时和大小上限。 */
object Http {
    private const val MAX_BYTES = 2 * 1024 * 1024

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        charset: Charset = Charsets.UTF_8,
        timeoutMs: Int = 8000,
    ): String = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "只允许 https 请求" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) EricToolbox")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            val bytes = conn.inputStream.use { input ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                    if (buf.size() > MAX_BYTES) throw java.io.IOException("响应过大")
                }
                buf.toByteArray()
            }
            String(bytes, charset)
        } finally {
            conn.disconnect()
        }
    }
}

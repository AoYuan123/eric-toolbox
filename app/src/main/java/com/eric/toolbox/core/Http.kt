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
    ): String = request(url, headers, charset, timeoutMs, null)

    /** 表单 POST（application/x-www-form-urlencoded）。 */
    suspend fun postForm(
        url: String,
        form: String,
        headers: Map<String, String> = emptyMap(),
        charset: Charset = Charsets.UTF_8,
        timeoutMs: Int = 8000,
    ): String = request(url, headers, charset, timeoutMs, form)

    /** JSON POST（AI 接口用）。非 2xx 时把服务器返回的错误信息带进异常，方便提示“余额不足”“密钥错误”等。 */
    suspend fun postJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 60_000,
    ): String = request(url, headers, Charsets.UTF_8, timeoutMs, json, "application/json")

    class HttpError(val code: Int, val body: String) : java.io.IOException("HTTP $code")

    private suspend fun request(
        url: String, headers: Map<String, String>, charset: Charset, timeoutMs: Int, form: String?,
        contentType: String = "application/x-www-form-urlencoded; charset=UTF-8",
    ): String = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "只允许 https 请求" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) EricToolbox")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (form != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = runCatching { conn.errorStream?.use { String(it.readBytes().take(4000).toByteArray(), Charsets.UTF_8) } }.getOrNull() ?: ""
                throw HttpError(code, err)
            }
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

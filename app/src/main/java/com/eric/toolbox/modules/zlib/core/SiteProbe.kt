package com.eric.toolbox.modules.zlib.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 站点连通性探测。
 *
 * 这个类只回答一个问题：「这台设备此刻能不能连上」。
 * 它回答不了「这个域名还存不存在」——墙内直连境外站点失败是常态（DNS 污染、SNI 阻断），
 * 同一个域名换个网络环境结论会完全反过来。
 *
 * 所以调用方只允许把失败呈现成「可能需代理」，任何地方都不能写成「域名已失效」。
 * 之前就栽在拿本机 curl 超时当域名已死的证据上，别在 App 里重犯一次。
 */
object SiteProbe {
    enum class State { UNKNOWN, OK, UNREACHABLE }

    /**
     * Cloudflare 的人机校验会返回 403 / 429 / 503。
     * 能拿到这些响应代码，说明链路是通的，只有内容被拦，所以照样算 OK。
     */
    suspend fun probe(base: String, timeoutMs: Int = 6000): State = withContext(Dispatchers.IO) {
        val url = base.trimEnd('/') + "/"
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) EricToolbox")
            }
            try {
                val code = conn.responseCode
                if (code in 200..399 || code == 403 || code == 429 || code == 503) State.OK
                else State.UNREACHABLE
            } finally {
                runCatching { conn.disconnect() }
            }
        } catch (e: Exception) {
            State.UNREACHABLE
        }
    }

    /**
     * 把用户随手粘的东西收拾成裸域名。
     *
     * 没写协议默认补 https；用户明确写了 http 就保留——有些镜像确实只有 http，
     * Manifest 里已经放开明文（v1.0.22 定的），这里再强行升级成 https 会把人挡在门外。
     * 认不出的返回 null，由界面提示格式不对，不要悄悄存进去。
     */
    fun normalize(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        s = s.substringBefore('?').substringBefore('#').trimEnd('/').lowercase()
        // 逐段校验：每个标签必须以字母数字开头结尾，所以 a..b、-a.com 这类都会被挡掉。
        val ok = Regex("^https?://([A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?\\.)+[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(:\\d+)?$")
        return if (ok.matches(s)) s else null
    }

    /** 展示用：去掉协议头。 */
    fun host(url: String): String = url.removePrefix("https://").removePrefix("http://")
}

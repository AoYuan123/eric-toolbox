package com.eric.toolbox.modules.vod.core

import com.eric.toolbox.modules.vod.model.ParseRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 解析器调用（配置里的 parses[]）。
 *
 * 只支持 type=0：把待播放地址拼在解析接口后面，接口返回真实地址。
 * type=1 那种 JavaScript 嗅探脚本需要在 App 里跑远程代码，不做。
 *
 * 用法上要注意：绝大多数线路本来就能直接播，解析器是「播不了时的兜底」，
 * 不要无条件先过一层解析，那样既慢又容易被封。
 */
object ParseResolver {

    suspend fun resolve(
        rule: ParseRule,
        playUrl: String,
        timeoutMs: Int = 10_000,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!rule.supported) throw IllegalArgumentException("「${rule.name}」不是支持的类型")
            val target = rule.url + URLEncoder.encode(playUrl, "UTF-8")
            extractUrl(VodHttp.get(target, timeoutMs = timeoutMs))
        }
    }

    private fun extractUrl(body: String): String {
        val t = body.trim()
        // 有的直接吐一行地址
        if (t.startsWith("http://") || t.startsWith("https://")) {
            return t.substringBefore('\n').trim()
        }
        val o = runCatching { JSONObject(t) }.getOrNull()
        if (o != null) {
            val u = o.optString("url").trim().ifBlank { o.optString("data").trim() }
            if (u.startsWith("http://") || u.startsWith("https://")) return u
        }
        throw IllegalArgumentException("解析器没返回可用的播放地址")
    }
}

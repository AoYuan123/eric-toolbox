package com.eric.toolbox.core

import com.eric.toolbox.BuildConfig
import org.json.JSONObject

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String?, val pageUrl: String, val notes: String)

/** 从 GitHub Releases 检查新版本。只读取公开信息，不需要任何账号或密钥。 */
object Updater {
    suspend fun check(): UpdateInfo? {
        val body = Http.get(
            "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest",
            headers = mapOf("Accept" to "application/vnd.github+json"),
        )
        val o = JSONObject(body)
        val tag = o.optString("tag_name") // 形如 v1.0.12
        val code = tag.substringAfterLast('.').toIntOrNull() ?: return null
        if (code <= BuildConfig.VERSION_CODE) return null
        val assets = o.optJSONArray("assets")
        var apk: String? = null
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url")
        }
        return UpdateInfo(code, tag.removePrefix("v"), apk, o.optString("html_url"), o.optString("body"))
    }
}

package com.eric.toolbox.core

import com.eric.toolbox.BuildConfig
import org.json.JSONObject

data class UpdateInfo(
    val versionCode: Int, val versionName: String, val apkUrl: String?, val pageUrl: String, val notes: String,
    val source: String,
)

/**
 * 检查新版本：先查 Gitee 发行版（国内不翻墙可下载），连不上或没有再查 GitHub Releases。
 * 只读取公开信息，不需要任何账号或密钥。版本号取标签 v1.0.N 的 N，与本机 versionCode 比较。
 */
object Updater {
    suspend fun check(): UpdateInfo? {
        try {
            gitee()?.let { return it }
        } catch (e: Exception) {
            // Gitee 不通：只能靠 GitHub
            return github()
        }
        // Gitee 显示已是最新：再看一眼 GitHub（防止 Gitee 同步失败漏掉新版），GitHub 连不上就按“已是最新”处理
        return try { github() } catch (_: Exception) { null }
    }

    private suspend fun gitee(): UpdateInfo? {
        val repo = BuildConfig.GITEE_REPO
        val o = JSONObject(Http.get("https://gitee.com/api/v5/repos/$repo/releases/latest"))
        return parse(o, "https://gitee.com/$repo/releases", "Gitee（国内）")
    }

    private suspend fun github(): UpdateInfo? {
        val o = JSONObject(
            Http.get(
                "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest",
                headers = mapOf("Accept" to "application/vnd.github+json"),
            ),
        )
        return parse(o, o.optString("html_url"), "GitHub（国内可能需要翻墙）")
    }

    private fun parse(o: JSONObject, page: String, source: String): UpdateInfo? {
        val tag = o.optString("tag_name") // 形如 v1.0.12
        val code = tag.substringAfterLast('.').toIntOrNull() ?: return null
        if (code <= BuildConfig.VERSION_CODE) return null
        val assets = o.optJSONArray("assets")
        var apk: String? = null
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url")
        }
        return UpdateInfo(code, tag.removePrefix("v"), apk, page, o.optString("body"), source)
    }
}

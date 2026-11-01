package com.eric.toolbox.modules.vod.core

import com.eric.toolbox.modules.vod.model.TmdbMovie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * TMDB 元数据。纯展示用途（海报、评分、简介），不提供任何播放地址。
 *
 * API key 由用户自己申请自己填，App 不代管、不内置。没填的话整块功能跳过，
 * 站点自带的海报依然能用。
 */
object TmdbClient {

    private const val BASE = "https://api.themoviedb.org/3"
    /** 列表用的小图足够了，省流量 */
    private const val IMG = "https://image.tmdb.org/t/p/w342"
    private const val LANG = "zh-CN"

    suspend fun search(apiKey: String, kw: String): Result<List<TmdbMovie>> =
        request(apiKey, "/search/multi?query=${enc(kw)}")

    suspend fun trending(apiKey: String): Result<List<TmdbMovie>> =
        request(apiKey, "/trending/all/week")

    fun posterUrl(path: String): String =
        if (path.isBlank()) "" else if (path.startsWith("http")) path else IMG + path.trimStart('/')

    private suspend fun request(apiKey: String, path: String): Result<List<TmdbMovie>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sep = if (path.contains("?")) "&" else "?"
                val url = "$BASE$path${sep}api_key=${apiKey.trim()}&language=$LANG"
                parse(VodHttp.get(url, timeoutMs = 15_000))
            }
        }

    private fun parse(body: String): List<TmdbMovie> {
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<TmdbMovie>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            // 电影用 title，剧集和综艺用 name
            val name = o.optString("title").trim().ifBlank { o.optString("name").trim() }
            if (name.isBlank()) continue
            val poster = o.optString("poster_path").trim()
            out += TmdbMovie(
                id = o.optInt("id"),
                title = name,
                overview = o.optString("overview").trim(),
                poster = if (poster.isBlank()) "" else posterUrl(poster),
                rating = o.optDouble("vote_average", 0.0),
                date = o.optString("release_date").trim()
                    .ifBlank { o.optString("first_air_date").trim() },
            )
        }
        return out
    }

    private fun enc(s: String): String = URLEncoder.encode(s.trim(), "UTF-8")
}

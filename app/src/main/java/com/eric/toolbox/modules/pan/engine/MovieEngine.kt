package com.eric.toolbox.modules.pan.engine

import com.eric.toolbox.modules.pan.core.Dates
import com.eric.toolbox.modules.pan.core.Http
import com.eric.toolbox.modules.pan.model.EngineResult
import com.eric.toolbox.modules.pan.model.PanItem
import com.eric.toolbox.modules.pan.model.SearchOptions
import java.net.URLEncoder

/**
 * 影视库（meng-ge.top）：按片名查条目，返回百度/夸克两个网盘链接。
 * 对应 Python `search_movie`。
 *
 * 特点：它不是「找某个剧集资源」的搜索，而是「这部片有没有人分享」的索引，
 * 所以标题会带类型后缀（如「流浪地球2（电影）」），排序里来源等级最低。
 */
class MovieEngine(
    private val base: String = DEFAULT_BASE,
) : PanEngine {

    override val name: String = "movie"

    override suspend fun search(keyword: String, opts: SearchOptions): EngineResult {
        val qs = "page=1&size=${opts.limitPerCloud.coerceAtLeast(1)}&keyword=" +
            URLEncoder.encode(keyword, "UTF-8")
        val payload = Http.getJson("$base?$qs", opts.timeoutMs)

        val code = payload.optInt("code", 0)
        if (code != 0) {
            return EngineResult(name, emptyList(), 0, error = payload.optString("message").ifBlank { "影视库失败" })
        }

        val data = payload.optJSONArray("data") ?: return EngineResult(name, emptyList(), 0)
        val out = ArrayList<PanItem>()
        for (i in 0 until data.length()) {
            val row = data.optJSONObject(i) ?: continue
            val title = row.optString("movieName")
            val typ = TYPE_NAMES[row.optString("type")] ?: row.optString("type")
            val note = if (typ.isEmpty()) title else "$title（$typ）"
            val dt = Dates.normalize(row.optString("updateTime"))
            val hot = row.optBoolean("hot", false)

            for ((cloud, key) in LINK_KEYS) {
                val link = row.optString(key).trim()
                if (link.isEmpty()) continue
                out.add(
                    PanItem(
                        cloud = cloud,
                        url = link,
                        note = note,
                        source = "movie-api",
                        datetime = dt,
                        hot = hot,
                    ),
                )
            }
        }
        return EngineResult(name, out, out.size)
    }

    companion object {
        const val DEFAULT_BASE = "https://meng-ge.top/api/movieData/getMoviesByType"

        /** 影视库只给这两个网盘的直链 */
        private val LINK_KEYS = listOf("baidu" to "baiduLink", "quark" to "quarkLink")

        private val TYPE_NAMES = mapOf(
            "TV" to "电视剧",
            "TV_4K" to "电视剧（4K）",
            "MOVIE" to "电影",
            "MOVIE_4K" to "电影（4K）",
            "ANIME" to "动漫",
            "ANIME_4K" to "动漫（4K）",
        )
    }
}

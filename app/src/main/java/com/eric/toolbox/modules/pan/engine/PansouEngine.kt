package com.eric.toolbox.modules.pan.engine

import com.eric.toolbox.modules.pan.core.CloudType
import com.eric.toolbox.modules.pan.core.Dates
import com.eric.toolbox.modules.pan.core.Http
import com.eric.toolbox.modules.pan.model.EngineResult
import com.eric.toolbox.modules.pan.model.PanItem
import com.eric.toolbox.modules.pan.model.SearchOptions
import java.net.URLEncoder

/**
 * 公开盘搜（pansou）。聚合了 20+ 个插件源，覆盖面最广，但天生慢（实测 40s+）。
 *
 * 关键坑：
 *  - 公开实例的 POST 会被中间层改坏或超时，必须 GET 优先
 *  - 参数 res 必须是 merge，用 all 却按 merged_by_type 解析会「有结果却显示无结果」
 *  - 响应可能被包一层 data，要兼容
 */
class PansouEngine(
    private val base: String = DEFAULT_BASE,
) : PanEngine {

    override val name: String = "pansou"

    override suspend fun search(keyword: String, opts: SearchOptions): EngineResult {
        return try {
            val qs = buildString {
                append("kw=").append(enc(keyword))
                append("&res=merge")
                append("&src=").append(opts.pansouSrc)
                if (opts.cloudTypes.isNotEmpty()) {
                    append("&cloud_types=").append(opts.cloudTypes.joinToString(",") { CloudType.canon(it) })
                }
            }
            val json = Http.getJson("$base/api/search?$qs", opts.pansouTimeoutMs)
            val data = json.optJSONObject("data") ?: json
            val merged = data.optJSONObject("merged_by_type")
                ?: return EngineResult(name, emptyList(), 0, error = "响应里没有 merged_by_type")

            val items = ArrayList<PanItem>()
            val keys = merged.keys()
            while (keys.hasNext()) {
                val ctype = keys.next()
                val arr = merged.optJSONArray(ctype) ?: continue
                for (i in 0 until arr.length()) {
                    val row = arr.optJSONObject(i) ?: continue
                    val url = row.optString("url").trim()
                    if (url.isEmpty()) continue
                    val src = row.optString("source").ifBlank { "pansou" }
                    items.add(
                        PanItem(
                            cloud = CloudType.resolve(ctype, url),
                            url = url,
                            password = row.optString("password").trim(),
                            note = stripTags(row.optString("note")),
                            source = "pansou:$src",
                            datetime = Dates.normalize(row.optString("datetime")),
                        ),
                    )
                }
            }
            val total = data.optInt("total", items.size)
            EngineResult(name, items, if (total > 0) total else items.size)
        } catch (e: Exception) {
            EngineResult(name, emptyList(), 0, error = e.message ?: e.javaClass.simpleName)
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun stripTags(s: String?): String =
        (s ?: "").replace(Regex("<[^>]+>"), "").trim()

    companion object {
        /** 域名随时可能变，建议从远端配置下发，别硬编码 */
        const val DEFAULT_BASE = "https://so.252035.xyz"
    }
}

package com.eric.toolbox.modules.pan.engine

import com.eric.toolbox.modules.pan.core.CloudType
import com.eric.toolbox.modules.pan.core.Dates
import com.eric.toolbox.modules.pan.core.Http
import com.eric.toolbox.modules.pan.model.EngineResult
import com.eric.toolbox.modules.pan.model.PanItem
import com.eric.toolbox.modules.pan.model.SearchOptions
import java.net.URLEncoder

/**
 * 小云搜索（yunso）。JSON 接口，最稳的一个源，建议作为主力。
 *
 * 关键坑：
 *  - 参数名必须是 wd。用 keyword 会触发站点人机验证，返回 code < 0
 *  - Scrurlname 是中文网盘名，要按映射表转；缺失时用 URL 兜底识别
 *  - addtime 是毫秒时间戳
 */
class YunsoEngine(
    private val api: String = DEFAULT_API,
) : PanEngine {

    override val name: String = "yunso"

    override suspend fun search(keyword: String, opts: SearchOptions): EngineResult {
        return try {
            val qs = "wd=${URLEncoder.encode(keyword, "UTF-8")}&mode=${opts.yunsoMode}&page=1"
            val json = Http.getJson("$api?$qs", opts.timeoutMs)

            val code = json.optInt("code", 0)
            if (code != 0) {
                return EngineResult(name, emptyList(), 0, error = json.optString("msg").ifBlank { "返回 code=$code" })
            }
            val arr = json.optJSONArray("Data") ?: return EngineResult(name, emptyList(), 0)

            val items = ArrayList<PanItem>(arr.length())
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val url = row.optString("Scrurl").trim()
                if (url.isEmpty()) continue
                val nameHint = row.optString("Scrurlname").trim()
                val cloud = CloudType.fromYunsoName(nameHint) ?: CloudType.fromUrl(url)
                items.add(
                    PanItem(
                        cloud = CloudType.resolve(cloud, url),
                        url = url,
                        password = row.optString("Scrpass").trim(),
                        note = row.optString("ScrName").trim(),
                        source = "yunso",
                        datetime = Dates.normalize(row.optString("addtime")),
                    ),
                )
            }
            val total = json.optInt("Query_result_Total", items.size)
            EngineResult(name, items, if (total > 0) total else items.size)
        } catch (e: Exception) {
            EngineResult(name, emptyList(), 0, error = e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        /** 域名可能变，建议远端配置 */
        const val DEFAULT_API = "https://www.yunso.net/api/opensearch.php"

        const val MODE_SMART = "90001"
        const val MODE_EXACT = "90002"
    }
}

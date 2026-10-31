package com.eric.toolbox.modules.pan.engine

import com.eric.toolbox.modules.pan.core.CloudType
import com.eric.toolbox.modules.pan.core.Http
import com.eric.toolbox.modules.pan.model.EngineResult
import com.eric.toolbox.modules.pan.model.PanItem
import com.eric.toolbox.modules.pan.model.SearchOptions
import org.json.JSONArray
import org.json.JSONObject

/**
 * 海搜（haisou）。
 *
 * 关键坑：
 *  - platforms 绝对不能传 "all"，会 422；不筛选时整个字段要省略
 *  - 返回的是 share_code 碎片，得自己拼成完整链接
 *  - 平台代码和通用标识不同：aliyun→ali、mobile→yidong
 *  - 这个源经常 429，失败就当它不可用，别重试
 */
class HaisouEngine(
    private val apiBase: String = DEFAULT_API,
) : PanEngine {

    override val name: String = "haisou"

    override suspend fun search(keyword: String, opts: SearchOptions): EngineResult {
        return try {
            val body = JSONObject().apply {
                put("query", keyword)
                put(
                    "pagination",
                    JSONObject().apply {
                        put("page", 1)
                        put("page_size", maxOf(10, opts.limitPerCloud * 2))
                    },
                )
                val filters = JSONObject()
                if (opts.cloudTypes.isNotEmpty()) {
                    val platforms = opts.cloudTypes
                        .map { CloudType.toHaisou(it) }
                        .filter { it.isNotBlank() && it != "all" }
                    if (platforms.isNotEmpty()) filters.put("platforms", JSONArray(platforms))
                }
                // scope=title 是站点默认值，不传；传了反而可能被当成非法枚举
                if (opts.haisouScope.isNotBlank() && opts.haisouScope != "title") {
                    filters.put("scope", opts.haisouScope)
                }
                opts.minSizeGb?.let { filters.put("min_size", (it * 1024 * 1024 * 1024).toLong()) }
                opts.maxSizeGb?.let { filters.put("max_size", (it * 1024 * 1024 * 1024).toLong()) }
                if (filters.length() > 0) put("filters", filters)
            }

            val json = Http.postJson("$apiBase/shares/search", body, opts.timeoutMs)
            if (!json.optBoolean("success", false)) {
                return EngineResult(name, emptyList(), 0, error = json.optString("message").ifBlank { "接口返回失败" })
            }
            val data = json.optJSONObject("data") ?: return EngineResult(name, emptyList(), 0)
            val arr = data.optJSONArray("items") ?: return EngineResult(name, emptyList(), 0)

            val items = ArrayList<PanItem>(arr.length())
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val code = row.optString("share_code").trim()
                if (code.isEmpty()) continue
                val platform = row.optString("platform").trim().ifBlank { "others" }
                val pwd = row.optString("share_pwd").trim()
                val url = buildUrl(platform, code, pwd)
                if (url.isEmpty()) continue

                val hsid = row.optString("hsid").trim()
                items.add(
                    PanItem(
                        cloud = CloudType.resolve(CloudType.fromHaisou(platform), url),
                        url = url,
                        password = pwd,
                        note = row.optString("share_name").trim(),
                        source = "haisou",
                        size = row.optLong("stat_size", 0L),
                        files = row.optInt("stat_file", 0),
                        detail = if (hsid.isNotEmpty()) "https://haisou.cc/j/$hsid" else "",
                    ),
                )
            }
            val total = data.optJSONObject("pagination")?.optInt("total", items.size) ?: items.size
            EngineResult(name, items, if (total > 0) total else items.size)
        } catch (e: Exception) {
            EngineResult(name, emptyList(), 0, error = e.message ?: e.javaClass.simpleName)
        }
    }

    /** share_code → 完整链接。表里没有的返回空，宁可不展示也不要拼错地址。 */
    private fun buildUrl(platform: String, code: String, pwd: String): String {
        val spec = PREFIX[platform] ?: return ""
        val (prefix, pwdKey) = spec
        val base = prefix + code
        return if (pwd.isNotEmpty() && pwdKey.isNotEmpty()) {
            "$base?$pwdKey=${java.net.URLEncoder.encode(pwd, "UTF-8")}"
        } else {
            base
        }
    }

    companion object {
        const val DEFAULT_API = "https://haisou.cc/api/v2"

        /** 平台 → (链接前缀, 提取码参数名) */
        private val PREFIX: Map<String, Pair<String, String>> = mapOf(
            "quark" to ("https://pan.quark.cn/s/" to ""),
            "baidu" to ("https://pan.baidu.com/s/" to "pwd"),
            "ali" to ("https://www.alipan.com/s/" to "pwd"),
            "xunlei" to ("https://pan.xunlei.com/s/" to "pwd"),
            "tianyi" to ("https://cloud.189.cn/t/" to ""),
            "yidong" to ("https://yun.139.com/shareweb/#/w/i/" to ""),
            "115" to ("https://115.com/s/" to "password"),
            "123" to ("https://www.123pan.com/s/" to "pwd"),
            "uc" to ("https://drive.uc.cn/s/" to ""),
        )
    }
}

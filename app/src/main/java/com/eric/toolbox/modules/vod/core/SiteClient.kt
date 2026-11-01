package com.eric.toolbox.modules.vod.core

import com.eric.toolbox.modules.vod.model.VodCategory
import com.eric.toolbox.modules.vod.model.VodItem
import com.eric.toolbox.modules.vod.model.VodSite
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 单个点播站点的协议客户端。针对的是苹果 CMS 那一类标准 HTTP 接口（type 0/1）：
 *
 *   首页/分类   ?ac=list&t=<分类id>&pg=<页>
 *   搜索       ?ac=list&wd=<关键词>&pg=<页>
 *   详情       ?ac=videolist&ids=<影片id>
 *
 * 返回体各家不太一样，所以 JSON 优先、XML 兜底，两边都做成「缺字段不报错、整体可用即可」。
 * 详情页才能拿到完整的播放串，列表页通常是空的——这是标准行为，不是 bug。
 */
class SiteClient(
    private val site: VodSite,
    private val ua: String = "",
    private val timeoutMs: Int = 15_000,
) {

    data class PageResult(
        val items: List<VodItem> = emptyList(),
        val categories: List<VodCategory> = emptyList(),
        val total: Int = 0,
        val error: String = "",
    ) {
        val ok: Boolean get() = error.isBlank()
    }

    fun home(page: Int = 1): PageResult = list(emptyMap(), page)

    fun category(tid: String, page: Int = 1): PageResult = list(mapOf("t" to tid), page)

    fun search(kw: String, page: Int = 1): PageResult = list(mapOf("wd" to kw), page)

    /** 详情。列表页拿不到播放串，要点进去才是全的。 */
    fun detail(id: String): PageResult {
        val params = LinkedHashMap<String, String>()
        params["ac"] = "videolist"
        params["ids"] = id
        return fetch(apiUrl(params))
    }

    // -------------------------------------------------------------- 请求层

    private fun list(extra: Map<String, String>, page: Int): PageResult {
        val params = LinkedHashMap<String, String>()
        params["ac"] = "list"
        if (page > 1) params["pg"] = page.toString()
        params.putAll(extra)
        return fetch(apiUrl(params))
    }

    private fun fetch(u: String): PageResult = try {
        val headers = if (ua.isBlank()) emptyMap() else mapOf("User-Agent" to ua)
        parseBody(VodHttp.get(u, headers, timeoutMs))
    } catch (e: Exception) {
        PageResult(error = VodHttp.messageOf(e))
    }

    /**
     * 拼 URL。站点给的 api 可能自带 query（如 api.php/provide/vod?from=x），
     * 也可能没有，两种情况都要拼对。
     */
    private fun apiUrl(params: LinkedHashMap<String, String>): String {
        val base = site.api.trim()
        val q = params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        return when {
            base.contains("?") -> {
                val sep = if (base.endsWith("?") || base.endsWith("&")) "" else "&"
                base + sep + q
            }
            else -> "$base?$q"
        }
    }

    private fun parseBody(body: String): PageResult =
        if (body.trimStart().startsWith("{")) parseJson(body) else parseXml(body)

    // ------------------------------------------------------------- JSON 分支

    private fun parseJson(body: String): PageResult {
        val root = JSONObject(body)
        val items = ArrayList<VodItem>()
        val arr = root.optJSONArray("list") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val it = fromJson(o)
            if (it.id.isNotBlank() && it.name.isNotBlank()) items += it
        }
        return PageResult(
            items = items,
            categories = jsonCategories(root),
            total = intOf(root, "total", "totalcount", "recordcount"),
        )
    }

    private fun jsonCategories(root: JSONObject): List<VodCategory> {
        val out = ArrayList<VodCategory>()
        val cls = root.optJSONArray("class") ?: return out
        for (i in 0 until cls.length()) {
            val o = cls.optJSONObject(i) ?: continue
            val id = firstStr(o, "type_id", "id", "tid")
            val nm = firstStr(o, "type_name", "name")
            if (id.isNotBlank() && nm.isNotBlank()) out += VodCategory(id, nm)
        }
        return out
    }

    private fun fromJson(o: JSONObject): VodItem = VodItem(
        siteKey = site.key,
        siteName = site.name,
        id = firstStr(o, "vod_id", "id"),
        name = firstStr(o, "vod_name", "name"),
        pic = absolutePic(firstStr(o, "vod_pic", "pic")),
        remarks = firstStr(o, "vod_remarks", "remarks", "note"),
        typeName = firstStr(o, "type_name", "typename", "type"),
        year = firstStr(o, "vod_year", "year"),
        area = firstStr(o, "vod_area", "area"),
        actor = firstStr(o, "vod_actor", "actor"),
        director = firstStr(o, "vod_director", "director"),
        content = stripHtml(firstStr(o, "vod_content", "content", "des")),
        playFrom = firstStr(o, "vod_play_from", "play_from"),
        playUrl = firstStr(o, "vod_play_url", "play_url"),
    )

    // -------------------------------------------------------------- XML 分支

    private fun parseXml(body: String): PageResult {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isCoalescing = true
        val doc = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)))

        val items = ArrayList<VodItem>()
        val videos = doc.getElementsByTagName("video")
        for (i in 0 until videos.length) {
            val e = videos.item(i) as? Element ?: continue
            val it = fromXml(e)
            if (it.id.isNotBlank() && it.name.isNotBlank()) items += it
        }

        // 分类节点写作 <ty id="1">电影</ty>
        val cats = ArrayList<VodCategory>()
        val tys = doc.getElementsByTagName("ty")
        for (i in 0 until tys.length) {
            val e = tys.item(i) as? Element ?: continue
            val id = e.getAttribute("id").trim()
            val nm = e.textContent?.trim().orEmpty()
            if (id.isNotBlank() && nm.isNotBlank()) cats += VodCategory(id, nm)
        }
        return PageResult(items = items, categories = cats, total = items.size)
    }

    private fun fromXml(e: Element): VodItem {
        val (from, playUrl) = xmlPlay(e)
        return VodItem(
            siteKey = site.key,
            siteName = site.name,
            id = tag(e, "id"),
            name = tag(e, "name"),
            pic = absolutePic(tag(e, "pic")),
            remarks = tag(e, "note"),
            typeName = tag(e, "type"),
            year = tag(e, "year"),
            area = tag(e, "area"),
            actor = tag(e, "actor"),
            director = tag(e, "director"),
            content = stripHtml(tag(e, "des")),
            playFrom = from,
            playUrl = playUrl,
        )
    }

    /** XML 里播放源写在 <dl><dd flag="源名">第1集$url#第2集$url</dd></dl> */
    private fun xmlPlay(e: Element): Pair<String, String> {
        val dl = e.getElementsByTagName("dl")
        if (dl.length == 0) return "" to ""
        val root = dl.item(0) as? Element ?: return "" to ""
        val dds = root.getElementsByTagName("dd")
        val froms = ArrayList<String>()
        val urls = ArrayList<String>()
        for (i in 0 until dds.length) {
            val dd = dds.item(i) as? Element ?: continue
            val txt = dd.textContent?.trim().orEmpty()
            if (txt.isBlank()) continue
            froms += dd.getAttribute("flag").trim().ifBlank { "线路 ${i + 1}" }
            urls += txt
        }
        return froms.joinToString("\$\$\$") to urls.joinToString("\$\$\$")
    }

    private fun tag(e: Element, name: String): String {
        val n = e.getElementsByTagName(name)
        return if (n.length > 0) n.item(0).textContent?.trim().orEmpty() else ""
    }

    // ------------------------------------------------------------------ 工具

    /** 海报可能是相对路径，补成绝对地址，否则加载不出来 */
    private fun absolutePic(pic: String): String {
        if (pic.isBlank()) return ""
        return when {
            pic.startsWith("http://") || pic.startsWith("https://") -> pic
            pic.startsWith("//") -> "https:$pic"
            pic.startsWith("/") -> originOf(site.api) + pic
            else -> pic
        }
    }

    private fun originOf(url: String): String {
        val i = url.indexOf("://")
        if (i < 0) return url
        val rest = url.substring(i + 3)
        val slash = rest.indexOf('/')
        val host = if (slash < 0) rest else rest.substring(0, slash)
        return url.substring(0, i) + "://" + host
    }

    private fun firstStr(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k, "").trim()
            if (v.isNotBlank() && v != "null") return v
        }
        return ""
    }

    private fun intOf(o: JSONObject, vararg keys: String): Int {
        for (k in keys) {
            val v = o.opt(k) ?: continue
            val n = when (v) {
                is Int -> v
                is String -> v.trim().toIntOrNull()
                else -> null
            }
            if (n != null) return n
        }
        return 0
    }

    /** 简介里塞了一堆 HTML 标签和实体，展示前清干净 */
    fun stripHtml(s: String): String = s
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .trim()
}

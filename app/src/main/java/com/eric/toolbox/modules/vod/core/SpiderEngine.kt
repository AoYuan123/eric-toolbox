package com.eric.toolbox.modules.vod.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.eric.toolbox.modules.vod.model.VodItem
import com.eric.toolbox.modules.vod.model.VodSite
import com.eric.toolbox.modules.vod.model.VodTime
import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderDebug
import dalvik.system.DexClassLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Modifier

/**
 * 一个插件包（配置里的 spider）对应一个引擎。同一个包里通常挂着几十个站点类，
 * 所以引擎按 jar 复用，蜘蛛实例按类名缓存——重复 new 会把站点自己的状态搞丢。
 *
 * 加载链路（这是 TVBox 那套约定，顺序不能换）：
 *
 *   1. DexClassLoader 加载插件包
 *   2. 找到 com.github.catvod.spider.Init，调 init(Context)
 *      —— 加固过的包在这一步解密自己的载荷、加载 native 库
 *   3. Init.getSpider(类名) 拿到具体站点；加固包只有这一条路走得通
 *      （业务类在加密载荷里，Class.forName 看不到）
 *   4. 退路：未加固的包直接 Class.forName("com.github.catvod.spider." + 类名)
 *
 * 第 3 步失败时会自动落到第 4 步，所以普通包和加固包用同一套代码。
 */
class SpiderEngine(
    private val ctx: Context,
    val jar: File,
) {

    private val loader: DexClassLoader
    private val initClass: Class<*>
    private var initInstance: Any? = null

    /** 类名 -> 实例。插件站点内部常持有 cookie/session，必须复用 */
    private val spiders = HashMap<String, Spider>()

    /** 初始化失败的原因，展示给用户时不用再猜 */
    @Volatile
    var bootError: String = ""
        private set

    init {
        // 必须在 Class.forName 之前：壳的静态初始化也可能去开那个库
        prepareHostEnv()
        val odex = File(ctx.codeCacheDir, "spider_odex").apply { mkdirs() }
        loader = DexClassLoader(jar.absolutePath, odex.absolutePath, null, ctx.classLoader)
        initClass = Class.forName(INIT_CLASS, true, loader)
        bootstrap()
    }

    // ------------------------------------------------------------- 初始化

    private fun bootstrap() {
        SpiderDebug.log("插件加载: ${jar.name} (${jar.length()} 字节)")
        // init 可能是静态方法，也可能是单例上的实例方法，两种都试。
        val ok = tryStaticInit() || tryInstanceInit()
        if (!ok) {
            bootError = "插件初始化方法没有调用成功（Init.init 既不是静态也无法实例化）"
            SpiderDebug.log(bootError)
        }
    }

    /**
     * TVBox 那套插件假定宿主 App 有 `databases/tv` 这个库，init 时会以只读方式打开它
     * （可能是读自己的缓存/配置）。本 App 没有这个文件，壳会抛
     * SQLiteCantOpenDatabaseException 并把 init 的后续步骤跳过——真机上表现为
     * 「插件引擎就绪但搜索全空」。
     *
     * 所以加载前先建一个合法的空库：必须以可写方式建一次并写进表结构，
     * 否则 0 字节文件在只读打开时仍然不是合法数据库。
     */
    private fun prepareHostEnv() {
        runCatching {
            val dbFile = ctx.getDatabasePath(HOST_DB_NAME)
            dbFile.parentFile?.mkdirs()
            if (!dbFile.isFile || dbFile.length() == 0L) {
                SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
                    db.execSQL("CREATE TABLE IF NOT EXISTS eric_meta (k TEXT PRIMARY KEY, v TEXT)")
                }
                SpiderDebug.log("宿主数据库占位已建: ${dbFile.absolutePath}")
            }
        }.onFailure {
            SpiderDebug.log("宿主数据库占位失败: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun tryStaticInit(): Boolean = runCatching {
        val m = initClass.getMethod("init", Context::class.java)
        if (!Modifier.isStatic(m.modifiers)) return false
        m.invoke(null, ctx)
        SpiderDebug.log("Init.init(Context) 静态调用完成")
        true
    }.getOrElse {
        SpiderDebug.log("静态 init 失败: ${it.javaClass.simpleName}: ${it.message}")
        false
    }

    private fun tryInstanceInit(): Boolean {
        initInstance = runCatching { initClass.getMethod("get").invoke(null) }
            .getOrElse {
                SpiderDebug.log("Init.get() 失败: ${it.message}")
                null
            }
            ?: runCatching { initClass.getConstructor().newInstance() }
                .getOrElse {
                    SpiderDebug.log("Init 无法实例化: ${it.message}")
                    null
                }

        val inst = initInstance ?: return false
        return runCatching {
            val m = initClass.getMethod("init", Context::class.java)
            if (Modifier.isStatic(m.modifiers)) return false
            m.invoke(inst, ctx)
            SpiderDebug.log("Init.init(Context) 实例调用完成")
            true
        }.getOrElse {
            SpiderDebug.log("实例 init 失败: ${it.javaClass.simpleName}: ${it.message}")
            false
        }
    }

    // --------------------------------------------------------------- 取实例

    /**
     * 按站点类名取 Spider 实例。
     * 先走加固包的 getSpider（业务类在加密载荷里，只能由壳自己创建），
     * 再走普通反射。
     */
    fun spider(site: VodSite): Spider {
        val api = site.api.trim()
        val key = classNameOf(site.api)
        synchronized(spiders) {
            spiders[key]?.let { return it }
            val s = create(site, api, key)
            spiders[key] = s
            return s
        }
    }

    private fun create(site: VodSite, api: String, key: String): Spider {
        // TVBox 约定：api 写 csp_Xxx，真正的类名是 Xxx（csp = catvod spider）。
        // 不剥前缀就会拿 csp_DouDouGuard 去拼全名，直接 ClassNotFoundException。
        // 壳的 getSpider 两种口径都存在，所以剥过的先试，原名兜底。
        val s = viaGuard(key) ?: viaGuard(api) ?: viaReflect(site, key, api)
        // 插件站点几乎都在 init 里读 ext（接口地址、cookie、token 之类）。
        // 这条路以前只给反射路径调了，加固壳拿到的实例是「没初始化」的，
        // 搜索会直接返回空——真机上表现为「引擎就绪但一条都搜不到」。
        runCatching { s.init(ctx, site.ext) }
            .onFailure { SpiderDebug.log("init($key) 抛异常: ${rootMessage(it)}") }
        // 云盘站的播放要走 127.0.0.1:9978 代理，三种写法都可能出现在 URL 的 do 参数里
        SpiderProxy.register(site.key, s)
        SpiderProxy.register(api, s)
        SpiderProxy.register(key, s)
        return s
    }

    private fun viaGuard(name: String): Spider? = runCatching {
        val inst = initInstance
            ?: runCatching { initClass.getMethod("get").invoke(null) }.getOrNull()
            ?: return null
        val m = initClass.getMethod("getSpider", String::class.java)
        val s = m.invoke(inst, name) as? Spider ?: return null
        runCatching { s.siteKey = name }
        SpiderDebug.log("getSpider($name) 成功")
        s
    }.getOrElse { e ->
        SpiderDebug.log("getSpider($name) 失败: ${rootMessage(e)}")
        null
    }

    private fun viaReflect(site: VodSite, key: String, api: String): Spider {
        // 极少数包真的用 csp_ 当类名的一部分，剥过的找不到就再试原名
        val cls = runCatching { Class.forName("$SPIDER_PKG$key", true, loader) }
            .getOrElse { Class.forName("$SPIDER_PKG$api", true, loader) }
        val s = cls.getDeclaredConstructor().newInstance() as Spider
        runCatching { s.siteKey = api }
        SpiderDebug.log("反射创建 ${cls.simpleName} 成功")
        return s
    }

    // ------------------------------------------------------------- 数据接口

    /** 搜索。插件返回 {"list":[...]} */
    fun search(site: VodSite, keyword: String): SiteClient.PageResult = call(site) { s ->
        s.searchContent(keyword, false)
    }

    fun home(site: VodSite, page: Int): SiteClient.PageResult = call(site) { s ->
        if (page <= 1) s.homeContent(false) else ""
    }

    fun category(site: VodSite, tid: String, page: Int): SiteClient.PageResult = call(site) { s ->
        @Suppress("UNCHECKED_CAST")
        s.categoryContent(tid, page.toString(), false, HashMap<String, String>())
    }

    fun detail(site: VodSite, id: String): SiteClient.PageResult = call(site) { s ->
        s.detailContent(listOf(id))
    }

    /** 播放。返回 (直连地址, 请求头, 是否还需要解析) */
    data class Player(val url: String = "", val headers: Map<String, String> = emptyMap(),
                      val error: String = "")

    fun player(site: VodSite, flag: String, id: String): Player = try {
        val s = spider(site)
        SpiderProxy.ensureStarted()
        SpiderProxy.bindLast(s)
        val raw = s.playerContent(flag, id, emptyList())
        if (raw.isBlank()) {
            Player(error = "插件没有返回播放地址")
        } else {
            val o = JSONObject(raw)
            val url = rewriteLocal(o.optString("url").trim())
            val headers = headersOf(o)
            if (url.isBlank()) {
                // parse=1 表示要经过解析器，那种地址本身不是媒体流。
                // 这里如实说明，不拿解析器页面去喂播放器（会一直转圈）。
                Player(error = o.optString("msg").ifBlank {
                    if (o.optInt("parse", 0) == 1) "这条线路需要额外解析，暂不支持" else "插件没有给出播放地址"
                })
            } else {
                if (url.contains("127.0.0.1") || url.contains("localhost")) {
                    SpiderDebug.log("播放走本地代理: $url")
                }
                Player(url, headers)
            }
        }
    } catch (e: Throwable) {
        SpiderDebug.log("播放失败: ${rootMessage(e)}")
        Player(error = rootMessage(e))
    }

    /**
     * 插件回的头信息。写法有三种：JSONObject、JSON 字符串、字段名叫 headers。
     * 这些头不是装饰——很多源站校验 Referer/UA，少了直接 403。
     */
    private fun headersOf(o: JSONObject): Map<String, String> {
        val raw: Any = o.opt("header") ?: o.opt("headers") ?: return emptyMap()
        val obj = if (raw is JSONObject) {
            raw
        } else {
            runCatching { JSONObject(raw.toString()) }.getOrNull() ?: return emptyMap()
        }
        val out = HashMap<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = obj.optString(k)
        }
        return out
    }

    /**
     * 插件的代理地址端口是写死的（多见 9978）。万一我们监听不到那个端口、
     * 或插件用的是别的端口，统一改写到本机实际监听的端口上，避免「地址对但连不上」。
     */
    private fun rewriteLocal(url: String): String {
        if (!LOCAL_HOST.containsMatchIn(url)) return url
        return LOCAL_HOST.replace(url, "://127.0.0.1:${SpiderProxy.PORT}")
    }

    private fun call(site: VodSite, block: (Spider) -> String): SiteClient.PageResult = try {
        val raw = block(spider(site))
        if (raw.isBlank()) {
            SpiderDebug.log("${site.name}: 插件返回空串")
            SiteClient.PageResult(error = "插件返回空数据")
        } else {
            val page = parse(raw, site)
            // 空结果是最难查的一种：可能是没搜到、可能是接口被墙回了错误 JSON。
            // 把原文头部留下来，看日志就能分辨。
            if (page.items.isEmpty()) {
                SpiderDebug.log("${site.name}: 0 条，原文前 160 字: ${raw.take(160)}")
            }
            page
        }
    } catch (e: Throwable) {
        SpiderDebug.log("${site.name}: 调用失败 ${rootMessage(e)}")
        SiteClient.PageResult(error = rootMessage(e))
    }

    // ------------------------------------------------------------ JSON 适配

    /**
     * 插件的返回格式跟苹果 CMS 基本一致，但字段名有简写版本（id/name/pic），
     * 所以两种都认。解析失败不抛异常，交给上层显示「无结果」。
     */
    private fun parse(raw: String, site: VodSite): SiteClient.PageResult {
        val text = raw.trim()
        val root = when {
            text.startsWith("{") -> JSONObject(text)
            text.startsWith("[") -> JSONObject().put("list", JSONArray(text))
            else -> return SiteClient.PageResult(error = "插件返回的不是 JSON", raw = raw.take(400))
        }
        val items = ArrayList<VodItem>()
        // 详情接口常把结构放在 list 里，个别插件用 data / vod 包一层
        val arr = root.optJSONArray("list")
            ?: root.optJSONObject("data")?.optJSONArray("list")
            ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val item = itemOf(o, site)
            if (item.id.isNotBlank() && item.name.isNotBlank()) items += item
        }
        return SiteClient.PageResult(
            items = items,
            categories = categoriesOf(root),
            raw = raw.take(400),
        )
    }

    private fun itemOf(o: JSONObject, site: VodSite): VodItem = VodItem(
        siteKey = site.key,
        siteName = site.name,
        id = str(o, "vod_id", "id"),
        name = str(o, "vod_name", "name"),
        pic = str(o, "vod_pic", "pic"),
        remarks = str(o, "vod_remarks", "remarks", "note"),
        typeName = str(o, "type_name", "type", "typename"),
        year = str(o, "vod_year", "year"),
        area = str(o, "vod_area", "area"),
        actor = str(o, "vod_actor", "actor"),
        director = str(o, "vod_director", "director"),
        content = stripHtml(str(o, "vod_content", "content", "des")),
        playFrom = str(o, "vod_play_from", "play_from"),
        playUrl = str(o, "vod_play_url", "play_url"),
        updatedAt = VodTime.parse(str(o, "vod_time", "time", "last")),
    )

    private fun categoriesOf(root: JSONObject): List<com.eric.toolbox.modules.vod.model.VodCategory> {
        val out = ArrayList<com.eric.toolbox.modules.vod.model.VodCategory>()
        val arr = root.optJSONArray("class") ?: return out
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = str(o, "type_id", "id")
            val name = str(o, "type_name", "name")
            if (id.isNotBlank() && name.isNotBlank()) {
                out += com.eric.toolbox.modules.vod.model.VodCategory(id, name)
            }
        }
        return out
    }

    private fun str(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k, "").trim()
            if (v.isNotBlank() && v != "null") return v
        }
        return ""
    }

    private fun stripHtml(s: String): String = s
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .trim()

    companion object {
        private const val INIT_CLASS = "com.github.catvod.spider.Init"
        private const val SPIDER_PKG = "com.github.catvod.spider."

        /** 插件壳假定宿主有这个库，见 prepareHostEnv 的注释 */
        private const val HOST_DB_NAME = "tv"

        /** 匹配 http://127.0.0.1:9978 这类本机地址，用于改写端口 */
        private val LOCAL_HOST = Regex("://(?:127\\.0\\.0\\.1|localhost)(?::\\d+)?")

        /**
         * 配置里的 api 是「类名指示符」，不是类名本身：
         *   csp_DouDouGuard → 类是 DouDouGuard（csp_ 前缀是 TVBox 的约定，表示走插件）
         *   DouDouGuard     → 原样
         * 少数写成 csp_Xxx.csp_Yyy 想指定内部类，只取第一段。
         */
        fun classNameOf(api: String): String {
            val head = api.trim().substringBefore('.')
            return if (head.startsWith("csp_")) head.removePrefix("csp_") else head
        }

        /** 反射的 InvocationTargetException 把真正的原因包了一层，挖出来 */
        fun rootMessage(e: Throwable): String {
            var t: Throwable = e
            while (t.cause != null && t.cause !== t) t = t.cause!!
            val name = t.javaClass.simpleName
            val msg = t.message.orEmpty().take(200)
            return if (msg.isBlank()) name else "$name: $msg"
        }
    }
}

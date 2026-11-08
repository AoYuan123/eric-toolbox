package com.eric.toolbox.modules.vod.core

import android.content.Context
import com.eric.toolbox.modules.vod.model.VodConfig
import com.eric.toolbox.modules.vod.model.VodSite
import com.github.catvod.crawler.SpiderDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Spider 站点的统一入口。
 *
 * SearchHub / VodDetail 这些调用点没有 Context，也不该为这件事改造它们的签名，
 * 所以这里用 Application Context 做一次 attach，配置里的插件声明也集中登记在这里
 * （由 VodModule 每次重载订阅时调用 [register]）。
 *
 * 一个插件包被加载进进程后是全局共享的，同一份配置的几十个站点共用一个引擎，
 * 不会重复解 dex——那一步很贵，而且重复加载容易让加固壳的状态错乱。
 */
object SpiderHub {

    /** 插件调用的硬超时。里面是别人的代码，卡住不能连累整次搜索 */
    private const val CALL_TIMEOUT_MS = 12_000L

    @Volatile private var appCtx: Context? = null

    /** subId -> 插件声明 */
    private val decls = HashMap<String, SpiderJar.Decl>()

    /** 带插件的订阅里的 Spider 站点，供「自检」用 */
    private val spiderSites = ArrayList<VodSite>()

    /** url -> 引擎 */
    private val engines = HashMap<String, SpiderEngine>()

    /** 加载失败过的 url，别每分钟重试一次、每次都卡半分钟 */
    private val broken = HashMap<String, String>()

    private val mutex = Mutex()

    @Volatile var lastError: String = ""
        private set

    fun attach(ctx: Context) {
        appCtx = ctx.applicationContext
    }

    /** 每次重载订阅后调用，刷新「哪个订阅带插件」 */
    fun register(configs: List<VodConfig>) {
        synchronized(decls) {
            decls.clear()
            spiderSites.clear()
            configs.forEach { c ->
                val d = SpiderJar.parse(c.spiderDecl) ?: return@forEach
                if (!d.isRemote) return@forEach
                decls[c.subId] = d
                spiderSites += c.sites.filter { it.isSpider }
            }
        }
    }

    /** 这个站点所在订阅有没有可用的插件声明 */
    fun hasPlugin(site: VodSite): Boolean = synchronized(decls) { decls.containsKey(site.subId) }

    /** 已加载的引擎数量，UI 上用来判断"插件是否就绪" */
    fun loadedCount(): Int = synchronized(engines) { engines.size }

    private suspend fun engineFor(site: VodSite): SpiderEngine? {
        val ctx = appCtx ?: run {
            lastError = "应用上下文未初始化"
            return null
        }
        val decl = synchronized(decls) { decls[site.subId] } ?: run {
            lastError = "这份配置没有声明插件包（spider 字段为空或不是 http 地址）"
            return null
        }
        mutex.withLock {
            synchronized(engines) { engines[decl.url] }?.let { return it }
            synchronized(broken) { broken[decl.url] }?.let {
                lastError = it
                return null
            }
            return try {
                val jar = SpiderJar.ensure(ctx, decl)
                val e = SpiderEngine(ctx, jar)
                synchronized(engines) { engines[decl.url] = e }
                // 云盘类站点播的是 127.0.0.1:9978 的代理地址，服务要提前就位
                SpiderProxy.ensureStarted()
                SpiderDebug.log("插件引擎就绪: ${jar.name}")
                e
            } catch (t: Throwable) {
                val msg = SpiderEngine.rootMessage(t)
                lastError = msg
                synchronized(broken) { broken[decl.url] = msg }
                SpiderDebug.log("插件引擎创建失败: $msg")
                null
            }
        }
    }

    /** 站点是否真的能跑（插件已就绪）——UI 用它区分「可用」和「需要插件」 */
    suspend fun ready(site: VodSite): Boolean = engineFor(site) != null

    /**
     * 插件里是别人的代码，可能死循环、可能等一个永远不返回的连接。
     * 不加硬超时的话，一个卡住的站点会把整次搜索拖死。
     */
    private suspend fun guarded(
        what: String,
        block: () -> SiteClient.PageResult,
    ): SiteClient.PageResult = withTimeoutOrNull(CALL_TIMEOUT_MS) {
        withContext(Dispatchers.IO) { runInterruptible { block() } }
    } ?: SiteClient.PageResult(error = "${CALL_TIMEOUT_MS / 1000} 秒无响应，已跳过").also {
        SpiderDebug.log("$what 超时")
    }

    suspend fun search(site: VodSite, keyword: String): SiteClient.PageResult? {
        val engine = engineFor(site) ?: return null
        return guarded("${site.name} 搜索") { engine.search(site, keyword) }
    }

    suspend fun home(site: VodSite, page: Int): SiteClient.PageResult? {
        val engine = engineFor(site) ?: return null
        return guarded("${site.name} 首页") { engine.home(site, page) }
    }

    suspend fun category(site: VodSite, tid: String, page: Int): SiteClient.PageResult? {
        val engine = engineFor(site) ?: return null
        return guarded("${site.name} 分类") { engine.category(site, tid, page) }
    }

    suspend fun detail(site: VodSite, id: String): SiteClient.PageResult? {
        val engine = engineFor(site) ?: return null
        return guarded("${site.name} 详情") { engine.detail(site, id) }
    }

    suspend fun player(site: VodSite, flag: String, id: String): SpiderEngine.Player? =
        engineFor(site)?.player(site, flag, id)

    /**
     * 一键自检：拿前几个 Spider 站点真的搜一次，逐个报条数/原因。
     *
     * 存在的理由——插件是别人的代码，日志只有它自己知道；用户能给的现场越多，
     * 定位越快。这里顺手清掉失败记忆，用户点一下等于「重试 + 取证」。
     */
    suspend fun selfTest(keyword: String = "三体", limitSites: Int = 5): String =
        withContext(Dispatchers.IO) {
            val pool = synchronized(decls) { spiderSites.toList() }
            if (pool.isEmpty()) {
                return@withContext "没有可跑的 Spider 站点。先去「添加接口」导入带 spider 字段的配置。"
            }
            synchronized(broken) { broken.clear() }
            val n = minOf(limitSites, pool.size)
            val sb = StringBuilder()
            sb.append("自检「").append(keyword).append("」，试其中 ").append(n).append(" 个站点：\n")
            var okCount = 0
            for (site in pool.take(n)) {
                val t0 = System.currentTimeMillis()
                val r = runCatching { search(site, keyword) }.getOrNull()
                val ms = System.currentTimeMillis() - t0
                when {
                    r == null -> sb.append("✗ ").append(site.name).append("：插件没起来（")
                        .append(lastError.ifBlank { "原因未知" }).append("）\n")
                    r.error.isNotBlank() -> sb.append("✗ ").append(site.name).append("：")
                        .append(r.error).append(" (").append(ms).append("ms)\n")
                    else -> {
                        okCount++
                        sb.append("✓ ").append(site.name).append("：")
                            .append(r.items.size).append(" 条")
                        r.items.firstOrNull()?.let { sb.append(" 例《").append(it.name).append("》") }
                        sb.append(" (").append(ms).append("ms)\n")
                    }
                }
            }
            sb.append(if (okCount > 0) "\n有站点能出数据，直接去首页搜。" else "\n这批全没出数据，具体原因见下面日志。")
            val text = sb.toString()
            SpiderDebug.log("—— 自检结果 ——\n$text")
            text
        }

    /** 清掉失败记忆，用户点「重试」时调用 */
    fun reset() {
        synchronized(broken) { broken.clear() }
        synchronized(engines) { engines.clear() }
        lastError = ""
    }

    fun logs(): String = SpiderDebug.dump()

    fun clearLogs() = SpiderDebug.clear()
}

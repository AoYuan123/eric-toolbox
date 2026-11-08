package com.eric.toolbox.modules.vod.core

import android.content.Context
import com.eric.toolbox.modules.vod.model.VodConfig
import com.eric.toolbox.modules.vod.model.VodSite
import com.github.catvod.crawler.SpiderDebug
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    @Volatile private var appCtx: Context? = null

    /** subId -> 插件声明 */
    private val decls = HashMap<String, SpiderJar.Decl>()

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
            configs.forEach { c ->
                val d = SpiderJar.parse(c.spiderDecl) ?: return@forEach
                if (d.isRemote) decls[c.subId] = d
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

    suspend fun search(site: VodSite, keyword: String): SiteClient.PageResult? =
        engineFor(site)?.search(site, keyword)

    suspend fun home(site: VodSite, page: Int): SiteClient.PageResult? =
        engineFor(site)?.home(site, page)

    suspend fun category(site: VodSite, tid: String, page: Int): SiteClient.PageResult? =
        engineFor(site)?.category(site, tid, page)

    suspend fun detail(site: VodSite, id: String): SiteClient.PageResult? =
        engineFor(site)?.detail(site, id)

    suspend fun player(site: VodSite, flag: String, id: String): SpiderEngine.Player? =
        engineFor(site)?.player(site, flag, id)

    /** 清掉失败记忆，用户点「重试」时调用 */
    fun reset() {
        synchronized(broken) { broken.clear() }
        synchronized(engines) { engines.clear() }
        lastError = ""
    }

    fun logs(): String = SpiderDebug.dump()

    fun clearLogs() = SpiderDebug.clear()
}

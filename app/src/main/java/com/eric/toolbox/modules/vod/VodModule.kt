package com.eric.toolbox.modules.vod

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.Category
import com.eric.toolbox.core.ToolModule
import com.eric.toolbox.ui.C
import com.eric.toolbox.modules.vod.core.BuiltinSources
import com.eric.toolbox.modules.vod.core.ConfigRepo
import com.eric.toolbox.modules.vod.core.EpisodeParser
import com.eric.toolbox.modules.vod.core.SearchHub
import com.eric.toolbox.modules.vod.core.SiteClient
import com.eric.toolbox.modules.vod.core.SpiderHub
import com.eric.toolbox.modules.vod.core.TmdbClient
import com.eric.toolbox.modules.vod.core.VodPrefs
import com.eric.toolbox.modules.vod.model.ParseRule
import com.eric.toolbox.modules.vod.model.PlayGroup
import com.eric.toolbox.modules.vod.model.TmdbMovie
import com.eric.toolbox.modules.vod.model.VodCategory
import com.eric.toolbox.modules.vod.model.VodItem
import com.eric.toolbox.modules.vod.model.VodSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * 影视模块：在线搜索并播放影视资源。
 *
 * 这个模块本身不含任何内容也不指向任何具体站点——它是一个空壳，
 * 影视数据来自用户自己导入的订阅配置，责任边界和网盘搜索模块一致。
 */
object VodModule : ToolModule {

    override val id: String = "vod"
    override val name: String = "影视"
    override val category: Category = Category.TOOL
    override val icon: ImageVector = Icons.Filled.Movie

    data class VodUiState(
        val sites: List<VodSite> = emptyList(),
        val currentSiteKey: String = "",
        val currentTid: String = "",
        val categories: List<VodCategory> = emptyList(),
        val items: List<VodItem> = emptyList(),
        val loading: Boolean = false,
        val error: String = "",
        val warnings: List<String> = emptyList(),
        /** 有正文没拉到的订阅，UI 要提示去刷新 */
        val staleSubIds: List<String> = emptyList(),
        val searchedCount: Int = 0,
        /** 被相关性门槛挡掉的条数 */
        val droppedCount: Int = 0,
        /** 被画质门槛挡掉的条数 */
        val droppedByQuality: Int = 0,
        val ua: String = "",
        val timeoutMs: Int = 15_000,
        val parses: List<ParseRule> = emptyList(),
        val trending: List<TmdbMovie> = emptyList(),
        val version: Int = 0,
    )

    private val _state = MutableStateFlow(VodUiState())
    val state: StateFlow<VodUiState> = _state

    private fun bump(block: (VodUiState) -> VodUiState) {
        val cur = _state.value
        _state.value = block(cur).copy(version = cur.version + 1)
    }

    // ------------------------------------------------------------ 配置装载

    /** 重新读取所有启用的订阅，合并出站点列表。不联网。 */
    suspend fun reload(ctx: Context) = withContext(Dispatchers.IO) {
        // 第一次进来且一个订阅都没有，就把推荐源写进去——否则用户看到的是空白页，
        // 还得自己去找一份 TVBox 配置，而市面上那些配置的点播部分基本都是 Spider。
        BuiltinSources.ensureSeeded(ctx)
        val m = ConfigRepo.merged(ctx)
        // Spider 站点的插件声明跟着订阅走，重载时同步过去；
        // 没有这一步，配置里的 spider 字段就只是段字符串，没人用得上。
        SpiderHub.attach(ctx)
        SpiderHub.register(m.configs)
        val remembered = VodPrefs.lastSiteKey(ctx)
        // 空串代表「全部站点」聚合。默认走聚合，站少的时候没必要先选站
        val siteKey = if (remembered.isNotBlank() && m.sites.any { it.key == remembered }) {
            remembered
        } else {
            ""
        }
        val ownerCfg = m.configs.firstOrNull { c ->
            siteKey.isBlank() || c.sites.any { it.key == siteKey }
        }
        bump {
            it.copy(
                sites = m.sites,
                parses = m.parses,
                warnings = m.warnings,
                staleSubIds = m.staleSubIds,
                currentSiteKey = siteKey,
                ua = ownerCfg?.ua.orEmpty(),
                timeoutMs = ownerCfg?.timeoutMs ?: 15_000,
                error = if (m.isEmpty) "还没有任何站点，去「我的」页恢复推荐源" else "",
            )
        }
        if (siteKey.isNotBlank()) VodPrefs.setLastSiteKey(ctx, siteKey)
    }

    fun selectSite(ctx: Context, key: String) {
        if (key.isBlank()) {
            VodPrefs.setLastSiteKey(ctx, "")
            bump {
                it.copy(
                    currentSiteKey = "",
                    currentTid = "",
                    categories = emptyList(),
                    items = emptyList(),
                    error = "",
                )
            }
            return
        }
        val m = ConfigRepo.merged(ctx)
        val cfg = m.configs.firstOrNull { c -> c.sites.any { it.key == key } }
        VodPrefs.setLastSiteKey(ctx, key)
        bump {
            it.copy(
                currentSiteKey = key,
                currentTid = "",
                categories = VodPrefs.customCategories(ctx, key),
                items = emptyList(),
                ua = cfg?.ua.orEmpty(),
                timeoutMs = cfg?.timeoutMs ?: 15_000,
                error = "",
            )
        }
    }

    private fun findSite(ctx: Context, key: String): VodSite? {
        val k = key.ifBlank { VodPrefs.lastSiteKey(ctx) }
        return _state.value.sites.firstOrNull { it.key == k }
            ?: ConfigRepo.merged(ctx).sites.firstOrNull { it.key == k }
    }

    // ------------------------------------------------------------ 浏览 / 搜索

    /**
     * 首页默认视图：把所有站点的最近更新混在一起。
     * 打开就有内容可点，不用先选站。
     */
    suspend fun aggregate(ctx: Context) {
        val sites = _state.value.sites.ifEmpty { ConfigRepo.merged(ctx).sites }
        if (sites.isEmpty()) {
            bump { it.copy(error = "还没有可用站点，去「我的」页恢复推荐源") }
            return
        }
        bump { it.copy(loading = true, error = "", currentTid = "") }
        val list = SearchHub.latest(sites, _state.value.ua, _state.value.timeoutMs)
        bump {
            it.copy(
                loading = false,
                items = list,
                categories = emptyList(),
                currentSiteKey = "",
                error = if (list.isEmpty()) {
                    "所有站点都没返回内容。可能当前网络受限，或站点已更换地址。"
                } else "",
            )
        }
    }

    suspend fun browse(ctx: Context, tid: String = "", page: Int = 1) {
        val site = findSite(ctx, _state.value.currentSiteKey)
        if (site == null) {
            bump { it.copy(error = "没有可用站点，请先在「我的 › 订阅」里添加配置") }
            return
        }
        bump { it.copy(loading = true, error = "", currentTid = tid) }
        val result = SearchHub.browse(site, tid, page, _state.value.ua, _state.value.timeoutMs)
        if (!result.ok) {
            bump { it.copy(loading = false, error = result.error.ifBlank { "「${site.name}」没有返回数据" }) }
            return
        }
        val cats = result.categories.ifEmpty { VodPrefs.customCategories(ctx, site.key) }
        bump {
            it.copy(
                loading = false,
                items = result.items,
                categories = cats,
                error = if (result.items.isEmpty()) "「${site.name}」这一页没有内容" else "",
            )
        }
    }

    suspend fun search(ctx: Context, keyword: String, strict: Boolean = true) {
        val kw = keyword.trim()
        if (kw.isBlank()) return
        val sites = _state.value.sites.ifEmpty { ConfigRepo.merged(ctx).sites }
        bump { it.copy(loading = true, error = "", items = emptyList(), searchedCount = 0) }
        val minRank = VodPrefs.minQuality(ctx)
        val out = SearchHub.search(
            sites = sites,
            keyword = kw,
            ua = _state.value.ua,
            timeoutMs = _state.value.timeoutMs,
            strict = strict,
            minQualityRank = minRank,
            // 「只看标了清晰度的」只在设了画质门槛时才生效。
            // 画质选「不限」还开这个开关，等于把所有不标画质的站点全清空——
            // 界面上看着像"没搜到"，其实是被自己筛掉了。
            requireKnownQuality = minRank > 0 && VodPrefs.requireKnownQuality(ctx),
            // 边搜边出：几十个插件站点排队，等全部回来才上屏太像卡死
            onPartial = { items, done ->
                bump { it.copy(items = items, searchedCount = done) }
            },
        )
        bump {
            it.copy(
                loading = false,
                items = out.hits,
                searchedCount = out.searchedCount,
                droppedCount = out.droppedCount,
                droppedByQuality = out.droppedByQuality,
                error = if (out.hits.isEmpty()) {
                    out.errors.firstOrNull()
                        ?: if (out.droppedByQuality > 0) {
                            "有 ${out.droppedByQuality} 条被画质筛选挡掉了，放宽要求再试试"
                        } else {
                            "没有找到相关结果，换个名字试试"
                        }
                } else "",
            )
        }
    }

    /** 详情结果：除了条目本身，把失败原因和插件原文一起带出来，界面上能说人话 */
    data class DetailResult(
        val item: VodItem,
        val error: String = "",
        val rawHead: String = "",
    )

    /** 详情页要拿完整播放串，列表页通常是空的。 */
    suspend fun detail(ctx: Context, item: VodItem): DetailResult = withContext(Dispatchers.IO) {
        val site = findSite(ctx, item.siteKey)
            ?: return@withContext DetailResult(item, "找不到这个站点，可能订阅被删了")
        val result = if (site.isSpider) {
            SpiderHub.detail(site, item.id)
                ?: return@withContext DetailResult(
                    item,
                    SpiderHub.lastError.ifBlank { "插件没有加载起来" },
                )
        } else {
            SiteClient(site, _state.value.ua, _state.value.timeoutMs).detail(item.id)
        }
        if (!result.ok) {
            return@withContext DetailResult(item, result.error.ifBlank { "站点没有返回数据" }, result.raw)
        }
        val full = result.items.firstOrNull()
        if (full == null) {
            return@withContext DetailResult(item, "站点返回的数据里没有这条片子", result.raw)
        }
        DetailResult(
            item = if (full.playUrl.isNotBlank()) full else item.copy(
                // 详情里没有播放串就把详情里的其它字段补上，至少信息是全的
                pic = item.pic.ifBlank { full.pic },
                content = item.content.ifBlank { full.content },
                actor = item.actor.ifBlank { full.actor },
                year = item.year.ifBlank { full.year },
                area = item.area.ifBlank { full.area },
            ),
            error = if (full.playUrl.isBlank()) "站点给了详情，但里面没有播放地址" else "",
            rawHead = if (full.playUrl.isBlank()) result.raw else "",
        )
    }

    /** 换地址的结果。失败也要说清原因，别让播放器去啃一个 token */
    data class Resolved(
        val url: String = "",
        val error: String = "",
        /** 插件要求的请求头（Referer / UA 等）。不少源站少这个头就 403 */
        val headers: Map<String, String> = emptyMap(),
    ) {
        val ok: Boolean get() = url.isNotBlank() && error.isBlank()
    }

    /**
     * 播放前把某一集换成真正能播的地址。
     *
     * 直连站点给的就是 m3u8，原样返回；Spider 站点（尤其云盘类）给的是站内标识，
     * 必须经过 playerContent 才变成媒体地址或本地代理地址。
     */
    suspend fun resolvePlayUrl(
        ctx: Context,
        siteKey: String,
        flag: String,
        url: String,
    ): Resolved = withContext(Dispatchers.IO) {
        val site = findSite(ctx, siteKey) ?: return@withContext Resolved(url)
        if (!site.isSpider) return@withContext Resolved(url)
        // 已经是媒体直链就别打扰插件：一集一问，40 集的剧集列表会慢得离谱
        if (looksDirect(url)) return@withContext Resolved(url)
        val p = SpiderHub.player(site, flag, url)
            ?: return@withContext Resolved(
                "",
                SpiderHub.lastError.ifBlank { "插件没有加载起来" },
            )
        if (p.url.isBlank()) {
            return@withContext Resolved("", p.error.ifBlank { "插件没有给出播放地址" })
        }
        Resolved(p.url, "", p.headers)
    }

    /**
     * 从一份剧集列表里把「不是网址」的那些换成真地址。
     * 列表页一次问十几集太慢，所以只换当前这一集，其余等播到再换。
     */
    suspend fun resolveIfNeeded(ctx: Context, siteKey: String, flag: String, url: String): Resolved =
        resolvePlayUrl(ctx, siteKey, flag, url)

    private fun looksDirect(u: String): Boolean {
        if (!u.startsWith("http")) return false
        val s = u.lowercase()
        return s.contains(".m3u8") || s.contains(".mp4") || s.contains(".flv") ||
            s.contains(".mkv") || s.contains(".ts")
    }

    /**
     * 把播放串拆成线路。Spider 站点允许「不是网址」的集地址——
     * 云盘类给的就是一串 token，播放前才换成真地址。直连站点仍然只认 http(s)。
     */
    fun playGroups(ctx: Context, item: VodItem): List<PlayGroup> {
        val site = findSite(ctx, item.siteKey)
        return EpisodeParser.parse(item.playFrom, item.playUrl, allowOpaque = site?.isSpider == true)
    }

    /** 站点域名，作为 Referer 传给播放器。部分源站靠这个头放行。 */
    fun originFor(ctx: Context, siteKey: String): String {
        val site = findSite(ctx, siteKey) ?: return ""
        val url = site.api
        val i = url.indexOf("://")
        if (i < 0) return ""
        val rest = url.substring(i + 3)
        val slash = rest.indexOf('/')
        val host = if (slash < 0) rest else rest.substring(0, slash)
        return url.substring(0, i) + "://" + host + "/"
    }

    // ---------------------------------------------------------- TMDB 元数据

    suspend fun trending(ctx: Context) {
        val key = VodPrefs.tmdbKey(ctx)
        if (key.isBlank()) {
            bump { it.copy(trending = emptyList()) }
            return
        }
        val list = TmdbClient.trending(key).getOrDefault(emptyList())
        bump { it.copy(trending = list) }
    }

    suspend fun searchTmdb(ctx: Context, kw: String) {
        val key = VodPrefs.tmdbKey(ctx)
        if (key.isBlank()) return
        val list = TmdbClient.search(key, kw).getOrDefault(emptyList())
        bump { it.copy(trending = list) }
    }

    val hasTmdb: (Context) -> Boolean = { VodPrefs.tmdbKey(it).isNotBlank() }

    // ------------------------------------------------------------- ToolModule

    @Composable
    override fun HomeSummary() {
        val v by state.collectAsState()
        Column {
            Text(
                if (v.sites.isEmpty()) "还没有添加订阅" else "${v.sites.size} 个站点就绪",
                color = C.Muted, fontSize = 12.sp,
            )
            Text(
                if (v.trending.isEmpty()) "配置驱动的在线影视检索" else "TMDB 已接入，可直接看榜单",
                color = C.Muted.copy(alpha = 0.7f), fontSize = 12.sp,
            )
        }
    }

    @Composable
    override fun Screen(onBack: () -> Unit) = VodRoot(onBack)
}

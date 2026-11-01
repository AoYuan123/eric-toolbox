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
import com.eric.toolbox.modules.vod.core.ConfigRepo
import com.eric.toolbox.modules.vod.core.EpisodeParser
import com.eric.toolbox.modules.vod.core.SearchHub
import com.eric.toolbox.modules.vod.core.SiteClient
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
        val m = ConfigRepo.merged(ctx)
        val remembered = VodPrefs.lastSiteKey(ctx)
        val siteKey = when {
            remembered.isNotBlank() && m.sites.any { it.key == remembered } -> remembered
            m.sites.isNotEmpty() -> m.sites.first().key
            else -> ""
        }
        bump {
            it.copy(
                sites = m.sites,
                parses = m.parses,
                warnings = m.warnings,
                staleSubIds = m.staleSubIds,
                currentSiteKey = siteKey,
                ua = m.configs.firstOrNull { c -> c.sites.any { s -> s.key == siteKey } }?.ua.orEmpty(),
                timeoutMs = m.configs.firstOrNull { c -> c.sites.any { s -> s.key == siteKey } }?.timeoutMs
                    ?: 15_000,
                error = if (m.isEmpty) "还没有任何站点，去「我的 › 订阅」添加一份配置" else "",
            )
        }
        if (siteKey.isNotBlank()) VodPrefs.setLastSiteKey(ctx, siteKey)
    }

    fun selectSite(ctx: Context, key: String) {
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
        bump { it.copy(loading = true, error = "", items = emptyList()) }
        val out = SearchHub.search(sites, kw, _state.value.ua, _state.value.timeoutMs, strict)
        bump {
            it.copy(
                loading = false,
                items = out.hits,
                searchedCount = out.searchedCount,
                droppedCount = out.droppedCount,
                error = if (out.hits.isEmpty()) {
                    out.errors.firstOrNull() ?: "没有找到相关结果，换个名字试试"
                } else "",
            )
        }
    }

    /** 详情页要拿完整播放串，列表页通常是空的。 */
    suspend fun detail(ctx: Context, item: VodItem): VodItem = withContext(Dispatchers.IO) {
        val site = findSite(ctx, item.siteKey) ?: return@withContext item
        val result = SiteClient(site, _state.value.ua, _state.value.timeoutMs).detail(item.id)
        val full = result.items.firstOrNull()
        if (full != null && full.playUrl.isNotBlank()) full else item
    }

    fun playGroups(item: VodItem): List<PlayGroup> = EpisodeParser.parse(item.playFrom, item.playUrl)

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

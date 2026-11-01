package com.eric.toolbox.modules.pan

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.Category
import com.eric.toolbox.core.Store
import com.eric.toolbox.core.ToolModule
import com.eric.toolbox.modules.pan.core.EngineCircuit
import com.eric.toolbox.modules.pan.engine.PanSearch
import com.eric.toolbox.modules.pan.model.PanItem
import com.eric.toolbox.modules.pan.model.SearchOptions
import com.eric.toolbox.ui.C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * juicePans。
 *
 * 纯客户端聚合：并行问几个公开的网盘资源检索站，把「同一个资源在不同站点的多条结果」
 * 折叠成一条，再按网盘分组展示。不爬网盘、不登录、不绕任何限制，
 * 干的事跟你在浏览器里一个个打开这些站点搜同一句话是一样的，只是帮你合并去重。
 */
object PanModule : ToolModule {
    override val id = "pan"
    override val name = "juicePans"
    override val category = Category.TOOL
    override val icon: ImageVector = Icons.Filled.TravelExplore

    /** 默认开的源：前面四个覆盖最广，盘小子/影视库按需在界面里勾。 */
    val DEFAULT_ENGINES = "pansou,haisou,yunso,ataw"

    /** 界面上可选的源：[key, 展示名, 说明] */
    val ENGINE_CHOICES: List<Triple<String, String, String>> = listOf(
        Triple("pansou", "盘搜", "收录最广，也最慢"),
        Triple("haisou", "海搜", "命中多，容易限流"),
        Triple("yunso", "小云", "最稳，速度快"),
        Triple("ataw", "TA搜", "补充源，详情全"),
        Triple("panxiaozi", "盘小子", "补充源"),
        Triple("movie", "影视库", "只收影视资源"),
    )

    private val _state = MutableStateFlow(PanUiState())
    val state: StateFlow<PanUiState> = _state

    fun prefs(ctx: Context) = Store.module(ctx, id)

    fun engines(ctx: Context): List<String> =
        prefs(ctx).getString("engines", DEFAULT_ENGINES)!!.split(',').filter { it.isNotBlank() }

    fun setEngines(ctx: Context, list: List<String>) {
        prefs(ctx).edit().putString("engines", list.joinToString(",")).apply()
        bump()
    }

    fun limitPerCloud(ctx: Context): Int = prefs(ctx).getInt("limit", 8)
    fun setLimit(ctx: Context, n: Int) {
        prefs(ctx).edit().putInt("limit", n.coerceIn(3, 30)).apply()
        bump()
    }

    fun allDups(ctx: Context): Boolean = prefs(ctx).getBoolean("all_dups", false)
    fun setAllDups(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("all_dups", on).apply()
        bump()
    }

    /**
     * 严格匹配：标题一个分词都没命中的直接剔除。默认开。
     *
     * 之前这个开关不存在，kwHits 只参与排序，结果「一年级毕业季」能搜出「恶作剧之吻」——
     * 后者的简介蹭到了关键词。搜资源这件事宁可少给几条，也不要让用户自己在噪音里翻。
     */
    fun strict(ctx: Context): Boolean = prefs(ctx).getBoolean("strict", true)
    fun setStrict(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("strict", on).apply()
        bump()
    }

    /** 网盘筛选，空集合表示全部 */
    fun cloudFilter(ctx: Context): Set<String> = prefs(ctx).getStringSet("clouds", emptySet())!!.toSet()
    fun setCloudFilter(ctx: Context, set: Set<String>) {
        prefs(ctx).edit().putStringSet("clouds", set).apply()
        bump()
    }

    fun history(ctx: Context): List<String> =
        prefs(ctx).getString("history", "")!!.split('\n').filter { it.isNotBlank() }

    private fun pushHistory(ctx: Context, kw: String) {
        val list = (listOf(kw) + history(ctx)).distinct().take(12)
        prefs(ctx).edit().putString("history", list.joinToString("\n")).apply()
    }

    fun lastKeyword(ctx: Context) = prefs(ctx).getString("last_kw", "")!!
    fun lastCount(ctx: Context) = prefs(ctx).getInt("last_count", 0)
    fun lastAt(ctx: Context) = prefs(ctx).getLong("last_at", 0L)

    private fun bump() {
        _state.value = _state.value.copy(version = _state.value.version + 1)
    }

    private fun circuitOf(ctx: Context) =
        EngineCircuit.fromJson(prefs(ctx).getString("circuit", "{}"))

    private fun saveCircuit(ctx: Context, c: Map<String, com.eric.toolbox.modules.pan.core.EngineState>) {
        prefs(ctx).edit().putString("circuit", EngineCircuit.toJson(c)).apply()
    }

    /** 熔断状态里还有哪些源在冷却，UI 上给个提示，避免用户以为是搜不到 */
    fun coolingEngines(ctx: Context): List<String> {
        val c = circuitOf(ctx)
        val now = System.currentTimeMillis()
        return c.keys.filter { EngineCircuit.isBlocked(it, c.toMutableMap(), now) }
    }

    /**
     * 执行一次搜索。force = 忽略熔断（界面上「强制再搜一次」）。
     */
    suspend fun search(ctx: Context, keyword: String, force: Boolean = false) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        _state.value = _state.value.copy(
            keyword = kw, loading = true, error = "", items = emptyList(),
            errors = emptyList(), skipped = emptyList(), variant = "", done = false,
        )
        val circuit = circuitOf(ctx)
        val opts = SearchOptions(
            engines = engines(ctx).ifEmpty { DEFAULT_ENGINES.split(',') },
            cloudTypes = cloudFilter(ctx),
            limitPerCloud = limitPerCloud(ctx),
            allDups = allDups(ctx),
            strict = strict(ctx),
            fresh = force,
        )
        try {
            val r = PanSearch(
                circuit = circuit,
                onCircuitChanged = { saveCircuit(ctx, it) },
            ).search(kw, opts)
            _state.value = _state.value.copy(
                loading = false, items = r.items, errors = r.errors, skipped = r.skippedEngines,
                variant = r.variantUsed, elapsed = r.elapsedMs, done = true,
                droppedRelevance = r.droppedRelevance,
                version = _state.value.version + 1,
            )
            prefs(ctx).edit()
                .putString("last_kw", kw)
                .putInt("last_count", r.items.size)
                .putLong("last_at", System.currentTimeMillis())
                .apply()
            pushHistory(ctx, kw)
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, error = e.message ?: "搜索失败")
        }
    }

    /** 核验单条链接是否还活着，结果直接写回 item */
    suspend fun verify(item: PanItem) {
        val v = com.eric.toolbox.modules.pan.verify.LinkVerifier
            .verify(item.url, item.cloud, item.password)
        item.verify = v.state
        item.lastCheckedAt = System.currentTimeMillis()
        bump()
    }

    @Composable
    override fun HomeSummary() {
        val ctx = LocalContext.current
        val v by state.collectAsState()
        val kw = remember(v.version) { lastKeyword(ctx) }
        val n = remember(v.version) { lastCount(ctx) }
        val at = remember(v.version) { lastAt(ctx) }
        Text(
            if (kw.isBlank()) "还没搜过" else "上次：$kw · $n 条",
            color = C.Muted, fontSize = 12.sp,
        )
        Text(
            if (at <= 0L) "6 个源聚合去重" else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(at)),
            color = C.Muted, fontSize = 12.sp,
        )
    }

    @Composable
    override fun Screen(onBack: () -> Unit) = PanRoot(onBack)
}

/** 界面状态。items 里的 PanItem 自带可变字段（核验结果），改完靠 version 触发重绘。 */
data class PanUiState(
    val keyword: String = "",
    val loading: Boolean = false,
    val error: String = "",
    val items: List<PanItem> = emptyList(),
    val errors: List<String> = emptyList(),
    val skipped: List<String> = emptyList(),
    val variant: String = "",
    val elapsed: Map<String, Long> = emptyMap(),
    val done: Boolean = false,
    /** 被相关性硬门槛剔除的条数，展示给用户，让他知道不是没搜到而是被筛掉了 */
    val droppedRelevance: Int = 0,
    val version: Int = 0,
)

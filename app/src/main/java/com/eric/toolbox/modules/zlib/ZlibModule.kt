package com.eric.toolbox.modules.zlib

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
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
import com.eric.toolbox.modules.zlib.core.SiteProbe
import com.eric.toolbox.ui.C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Z-Library 入口。
 *
 * App 这边只是一个书签加搜索框：拼好 /s/书名 交给系统浏览器打开，走系统代理。
 * 不做内置浏览器——站点前置 Cloudflare 的人机校验，WebView 里基本过不去；
 * 不做账号登录，密码不落在 App 里；不内置「官方域名清单」，因为那东西不存在，
 * 域名是一组轮换镜像，内置了反而误导人。
 */
object ZlibModule : ToolModule {
    override val id = "zlib"
    override val name = "Z-Library"
    override val category = Category.TOOL
    override val icon: ImageVector = Icons.Filled.MenuBook

    /** 只是起点，不是「官方清单」。域名会被封、会轮换，用户自己加。 */
    val DEFAULT_SITES = listOf("https://z-library.biz", "https://z-library.sk")
    const val DEFAULT_SEARCH_TPL = "/s/{q}"
    private const val MAX_RECENT = 8

    private val _state = MutableStateFlow(ZlibUiState())
    val state: StateFlow<ZlibUiState> = _state

    fun prefs(ctx: Context) = Store.module(ctx, id)

    // ---------------- 站点 ----------------

    fun sites(ctx: Context): List<String> {
        val raw = prefs(ctx).getString("sites", null) ?: return DEFAULT_SITES
        val list = raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        return list.ifEmpty { DEFAULT_SITES }
    }

    private fun setSites(ctx: Context, list: List<String>) {
        prefs(ctx).edit().putString("sites", list.joinToString("\n")).apply()
        bump()
    }

    /** 添加成功返回 null，失败返回给用户看的原因。 */
    fun addSite(ctx: Context, raw: String): String? {
        val n = SiteProbe.normalize(raw) ?: return "域名格式不对，填 z-library.xxx 这种就行"
        val list = sites(ctx)
        if (n in list) return "这个站点已经在列表里了"
        setSites(ctx, list + n)
        return null
    }

    /** 至少留一个。删到空会让 sites() 回退默认值，看起来像「删了又自己长回来」。 */
    fun removeSite(ctx: Context, url: String): Boolean {
        val list = sites(ctx)
        if (list.size <= 1) return false
        val next = list.filter { it != url }
        setSites(ctx, next)
        if (current(ctx) !in next) setCurrent(ctx, next.first())
        return true
    }

    fun current(ctx: Context): String {
        val cur = prefs(ctx).getString("current", "")!!
        val all = sites(ctx)
        return if (cur in all) cur else all.first()
    }

    fun setCurrent(ctx: Context, url: String) {
        prefs(ctx).edit().putString("current", url).apply()
        bump()
    }

    // ---------------- 搜索 ----------------

    /** 搜索路径做成模板，站点改版导致 404 时改这里，不用等发版。 */
    fun searchTpl(ctx: Context): String = prefs(ctx).getString("search_tpl", DEFAULT_SEARCH_TPL)!!

    fun setSearchTpl(ctx: Context, tpl: String) {
        prefs(ctx).edit().putString("search_tpl", tpl.trim().ifBlank { DEFAULT_SEARCH_TPL }).apply()
        bump()
    }

    fun searchUrl(ctx: Context, keyword: String): String {
        val q = Uri.encode(keyword.trim())
        return current(ctx) + searchTpl(ctx).replace("{q}", q)
    }

    fun homeUrl(ctx: Context): String = current(ctx) + "/"

    fun recent(ctx: Context): List<String> =
        prefs(ctx).getString("recent", "")!!.split('\n').filter { it.isNotBlank() }

    fun clearRecent(ctx: Context) {
        prefs(ctx).edit().remove("recent").apply()
        bump()
    }

    /** 搜完记一笔，供主页卡片和「最近搜过」用。只存关键词，不存任何网页内容。 */
    fun recordSearch(ctx: Context, keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        val list = (listOf(kw) + recent(ctx)).distinct().take(MAX_RECENT)
        prefs(ctx).edit()
            .putString("recent", list.joinToString("\n"))
            .putString("last_kw", kw)
            .putLong("last_at", System.currentTimeMillis())
            .apply()
        bump()
    }

    fun lastKw(ctx: Context): String = prefs(ctx).getString("last_kw", "")!!
    fun lastAt(ctx: Context): Long = prefs(ctx).getLong("last_at", 0L)

    // ---------------- 探测记录 ----------------

    fun probes(ctx: Context): Map<String, SiteProbe.State> {
        val raw = prefs(ctx).getString("probes", "")!!
        if (raw.isBlank()) return emptyMap()
        return raw.split('\n').mapNotNull { line ->
            val p = line.split('|')
            if (p.size != 2) null
            else runCatching { p[0] to SiteProbe.State.valueOf(p[1]) }.getOrNull()
        }.toMap()
    }

    fun saveProbe(ctx: Context, url: String, st: SiteProbe.State) {
        val m = probes(ctx).toMutableMap()
        m[url] = st
        prefs(ctx).edit()
            .putString("probes", m.entries.joinToString("\n") { "${it.key}|${it.value.name}" })
            .putLong("probed_at", System.currentTimeMillis())
            .apply()
        bump()
    }

    fun probedAt(ctx: Context): Long = prefs(ctx).getLong("probed_at", 0L)

    // ---------------- 打开 ----------------

    fun open(ctx: Context, url: String): Boolean = try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun bump() {
        _state.value = _state.value.copy(version = _state.value.version + 1)
    }

    @Composable
    override fun HomeSummary() {
        val ctx = LocalContext.current
        val v by state.collectAsState()
        val kw = remember(v.version) { lastKw(ctx) }
        val at = remember(v.version) { lastAt(ctx) }
        val n = remember(v.version) { sites(ctx).size }
        Text(
            if (kw.isBlank()) "还没搜过" else "上次：$kw",
            color = C.Muted, fontSize = 12.sp,
        )
        Text(
            if (at <= 0L) "$n 个站点 · 需代理" else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(at)),
            color = C.Muted, fontSize = 12.sp,
        )
    }

    @Composable
    override fun Screen(onBack: () -> Unit) = ZlibRoot(onBack)
}

data class ZlibUiState(val version: Int = 0)

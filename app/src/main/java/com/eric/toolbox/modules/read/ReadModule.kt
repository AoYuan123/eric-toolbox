package com.eric.toolbox.modules.read

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.Category
import com.eric.toolbox.core.Store
import com.eric.toolbox.core.ToolModule
import com.eric.toolbox.ui.C

/** 读书：搜三个公版书库、下载 EPUB、导入本机 EPUB/TXT、内置阅读器；古籍内嵌国图网页。 */
object ReadModule : ToolModule {
    override val id = "read"
    override val name = "读书"
    override val category = Category.TOOL
    override val icon: ImageVector = Icons.AutoMirrored.Filled.MenuBook

    @Composable
    override fun HomeSummary() {
        val ctx = LocalContext.current
        val b = Library.recent(ctx).firstOrNull { it.lastReadAt > 0 }
        Text(if (b != null) "在读 ${(b.progress * 100).toInt()}%" else "书架 ${Library.all(ctx).size} 本", color = C.Text, fontSize = 13.sp)
    }

    @Composable
    override fun Screen(onBack: () -> Unit) = ReadRoot(onBack)
}

internal sealed interface RPage {
    data object Home : RPage
    data class Search(val q: String = "") : RPage
    data class Detail(val hit: ReadLogic.Hit) : RPage
    data class Reader(val bookId: String) : RPage
    data object Guji : RPage
}

@Composable
private fun ReadRoot(onBack: () -> Unit) {
    var stack by remember { mutableStateOf(listOf<RPage>(RPage.Home)) }
    val go: (RPage) -> Unit = { stack = stack + it }
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) else onBack() }
    val p = stack.last()
    // 阅读器和古籍页自己处理返回键（先关菜单 / 网页后退）
    BackHandler(enabled = stack.size > 1 && p !is RPage.Reader && p !is RPage.Guji) { back() }
    when (p) {
        RPage.Home -> ReadHome(onBack, go)
        is RPage.Search -> SearchScreen(p.q, back, go)
        is RPage.Detail -> DetailScreen(p.hit, back) { id -> stack = stack.dropLast(1) + RPage.Reader(id) }
        is RPage.Reader -> ReaderScreen(p.bookId, back)
        RPage.Guji -> GujiScreen(back)
    }
}

/** 排版设置和读速存在模块设置里（会随「备份导出」一起备份）。 */
object ReadPrefs {
    private fun p(ctx: Context) = Store.module(ctx, ReadModule.id)

    fun settings(ctx: Context): ReadSettings {
        val p = p(ctx)
        val d = ReadSettings()
        return ReadSettings(
            fontSize = p.getInt("font_size", d.fontSize),
            lineHeight = p.getFloat("line_height", d.lineHeight.toFloat()).toDouble().let { Math.round(it * 10) / 10.0 },
            margin = p.getInt("margin", d.margin),
            font = p.getString("font", d.font)!!,
            theme = p.getString("theme", d.theme)!!,
            paged = p.getBoolean("paged", d.paged),
            volumeKeys = p.getBoolean("volume_keys", d.volumeKeys),
            keepScreenOn = p.getBoolean("keep_on", d.keepScreenOn),
            autoNight = p.getBoolean("auto_night", d.autoNight),
            tapSides = p.getBoolean("tap_sides", d.tapSides),
        )
    }

    fun save(ctx: Context, s: ReadSettings) {
        p(ctx).edit()
            .putInt("font_size", s.fontSize).putFloat("line_height", s.lineHeight.toFloat()).putInt("margin", s.margin)
            .putString("font", s.font).putString("theme", s.theme).putBoolean("paged", s.paged)
            .putBoolean("volume_keys", s.volumeKeys).putBoolean("keep_on", s.keepScreenOn)
            .putBoolean("auto_night", s.autoNight).putBoolean("tap_sides", s.tapSides).apply()
    }

    /** 学到的阅读速度（字符/分钟），0 表示还没学到，用默认值。 */
    fun cpm(ctx: Context): Double = p(ctx).getFloat("cpm", 0f).toDouble()
    fun saveCpm(ctx: Context, v: Double) = p(ctx).edit().putFloat("cpm", v.toFloat()).apply()

    fun recentQueries(ctx: Context): List<String> = p(ctx).getString("recent_q", "")!!.split('\n').filter { it.isNotBlank() }
    fun addQuery(ctx: Context, q: String) {
        val list = (listOf(q.trim()) + recentQueries(ctx).filter { it != q.trim() }).take(8)
        p(ctx).edit().putString("recent_q", list.joinToString("\n")).apply()
    }
}

package com.eric.toolbox.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.Store

data class Palette(
    val key: String,
    val name: String,
    val bg: Color,
    val surface: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val gold: Color,
    val goldBg: Color,
    val ok: Color,
    val okBg: Color,
    val warn: Color,
    val warnBg: Color,
    val isLight: Boolean,
)

object Palettes {
    val Dark = Palette(
        "dark", "曜石黑", Color(0xFF14161A), Color(0xFF1E2127), Color(0xFF2C3038), Color(0xFFF2F3F5), Color(0xFFA9AFBA),
        Color(0xFFE8BE5A), Color(0xFF2A2418), Color(0xFF7FD9A4), Color(0xFF1F2A22), Color(0xFFFF8A80), Color(0xFF3A1E1E), false,
    )
    val Ivory = Palette(
        "ivory", "象牙白", Color(0xFFF6F3EC), Color(0xFFFFFFFF), Color(0xFFE6E0D4), Color(0xFF1E1D1A), Color(0xFF6B675E),
        Color(0xFF9A6B12), Color(0xFFF3E7C9), Color(0xFF1E7A48), Color(0xFFE1F2E7), Color(0xFFC0392B), Color(0xFFFBE3E0), true,
    )
    val Green = Palette(
        "green", "墨绿", Color(0xFF101C18), Color(0xFF182A24), Color(0xFF26403A), Color(0xFFEFF3F1), Color(0xFFA3B5AE),
        Color(0xFFD9B45A), Color(0xFF2B2A1A), Color(0xFF86DDB0), Color(0xFF1C3328), Color(0xFFFF8A80), Color(0xFF3A1E1E), false,
    )
    val all = listOf(Dark, Ivory, Green)
}

/**
 * 全局配色。界面里统一用 C.xxx 取色；切换主题时只改这里，整个 App 自动重绘。
 */
object C {
    var palette by mutableStateOf(Palettes.Dark)
    /** true = 红涨绿跌（中国习惯）；false = 绿涨红跌。 */
    var redUp by mutableStateOf(true)

    val Bg get() = palette.bg
    val Surface get() = palette.surface
    val Line get() = palette.line
    val Text get() = palette.text
    val Muted get() = palette.muted
    val Gold get() = palette.gold
    val GoldBg get() = palette.goldBg
    val Ok get() = palette.ok
    val OkBg get() = palette.okBg
    val Warn get() = palette.warn
    val WarnBg get() = palette.warnBg

    private val red = Color(0xFFFF6B6B)
    private val green = Color(0xFF3CC08A)
    val Up get() = if (redUp) red else green
    val Down get() = if (redUp) green else red
}

/** 主题设置：dark / ivory / green / system。 */
object ThemeManager {
    fun key(ctx: Context): String = Store.global(ctx).getString("theme", "dark")!!

    fun apply(ctx: Context) {
        val p = Store.global(ctx)
        val k = key(ctx)
        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        C.palette = when (k) {
            "ivory" -> Palettes.Ivory
            "green" -> Palettes.Green
            "system" -> if (night) Palettes.Dark else Palettes.Ivory
            else -> Palettes.Dark
        }
        C.redUp = p.getBoolean("red_up", true)
    }

    fun set(ctx: Context, key: String) {
        Store.global(ctx).edit().putString("theme", key).apply()
        apply(ctx)
    }

    fun setRedUp(ctx: Context, v: Boolean) {
        Store.global(ctx).edit().putBoolean("red_up", v).apply()
        apply(ctx)
    }
}

val Mono = FontFamily.Monospace

@Composable
fun ToolboxTheme(content: @Composable () -> Unit) {
    val p = C.palette
    val scheme = if (p.isLight) {
        lightColorScheme(
            primary = p.gold, onPrimary = Color.White, background = p.bg, onBackground = p.text,
            surface = p.surface, onSurface = p.text, surfaceVariant = p.surface, onSurfaceVariant = p.muted, outline = p.line,
        )
    } else {
        darkColorScheme(
            primary = p.gold, onPrimary = p.bg, background = p.bg, onBackground = p.text,
            surface = p.surface, onSurface = p.text, surfaceVariant = p.surface, onSurfaceVariant = p.muted, outline = p.line,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** 页面顶部：返回箭头 + 标题 + 右侧操作。 */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = C.Muted) }
        } else {
            Box(Modifier.size(16.dp))
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** 设置类列表卡片：内边距更小，行与行之间由 RowItem 自己控制高度。 */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
fun Pill(text: String, fg: Color, bg: Color) {
    Box(Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = fg, fontSize = 12.sp)
    }
}

@Composable
fun RowItem(label: String, value: String = "", sub: String? = null, onClick: (() -> Unit)? = null) {
    val m = Modifier.fillMaxWidth().heightIn(min = 50.dp)
    Row(
        if (onClick != null) m.clickable(onClick = onClick) else m,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = C.Text, fontSize = 15.sp)
            if (sub != null) Text(sub, color = C.Muted, fontSize = 12.sp)
        }
        Text(value, color = C.Muted, fontSize = 14.sp)
        if (onClick != null) Text("  ›", color = C.Muted, fontSize = 16.sp)
    }
}

@Composable
fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = C.Text, fontSize = 15.sp)
            if (sub != null) Text(sub, color = C.Muted, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = C.Gold, checkedThumbColor = Color.White),
        )
    }
}

/** 分段选择器（如 大盘 / 积存金 / 如意金）。 */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, modifier: Modifier = Modifier, compact: Boolean = false, onSelect: (String) -> Unit) {
    Row(modifier.background(C.Surface, RoundedCornerShape(10.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (k, label) ->
            val on = k == selected
            Box(
                (if (compact) Modifier else Modifier.weight(1f)).heightIn(min = if (compact) 32.dp else 40.dp)
                    .background(if (on) C.Gold else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(k) }
                    .padding(horizontal = if (compact) 10.dp else 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) (if (C.palette.isLight) Color.White else C.Bg) else C.Muted, fontSize = if (compact) 13.sp else 14.sp)
            }
        }
    }
}

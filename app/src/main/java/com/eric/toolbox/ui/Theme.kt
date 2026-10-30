package com.eric.toolbox.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 统一配色：所有模块共用，保证风格一致。 */
object C {
    val Bg = Color(0xFF14161A)
    val Surface = Color(0xFF1E2127)
    val Line = Color(0xFF2C3038)
    val Text = Color(0xFFF2F3F5)
    val Muted = Color(0xFFA9AFBA)
    val Gold = Color(0xFFE8BE5A)
    val GoldBg = Color(0xFF2A2418)
    val Ok = Color(0xFF7FD9A4)
    val OkBg = Color(0xFF1F2A22)
    val Up = Color(0xFFFF7A6E)   // 中国习惯：红涨
    val Down = Color(0xFF5BD18B) // 绿跌
    val Warn = Color(0xFFFF8A65)
    val WarnBg = Color(0xFF33201A)
}

val Mono = FontFamily.Monospace

@Composable
fun ToolboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Gold, onPrimary = C.Bg, background = C.Bg, onBackground = C.Text,
            surface = C.Surface, onSurface = C.Text, surfaceVariant = C.Surface, onSurfaceVariant = C.Muted,
            outline = C.Line,
        ),
        content = content,
    )
}

/** 页面顶部：返回箭头 + 标题。 */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
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
        modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun Pill(text: String, fg: Color, bg: Color) {
    Box(Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = fg, fontSize = 12.sp)
    }
}

@Composable
fun RowItem(label: String, value: String = "", onClick: (() -> Unit)? = null) {
    val m = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    Row(
        if (onClick != null) m.then(Modifier.clickableNoRipple(onClick)) else m,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = C.Muted, fontSize = 14.sp)
        if (onClick != null) Text("  ›", color = C.Muted, fontSize = 16.sp)
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

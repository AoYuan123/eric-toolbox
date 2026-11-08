package com.eric.toolbox.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * V2「精密工具」设计语言的通用部件：点阵数字、模块卡片、按压手感、小标签、等宽数字。
 * 原则：只有一个强调色（C.Gold，深色下是信号橙）；不用渐变和发光；数字等宽；按钮按下有轻微缩放和震动。
 */

/** 等宽数字（价格、时间、计数），跳动时不左右抖。 */
val NumStyle = TextStyle(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")

/** 模块卡片：石墨面板 + 20dp 圆角。 */
@Composable
fun Tile(modifier: Modifier = Modifier, strong: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.then(if (onClick != null) Modifier.press(onClick = onClick) else Modifier)
        .background(if (strong) C.Surface else C.Surface.copy(alpha = if (C.palette.isLight) 0.85f else 0.82f), RoundedCornerShape(20.dp))
        .padding(horizontal = 16.dp, vertical = 14.dp)
    Column(m, content = content)
}

/** 小标签（模块名、读数名），中文/英文都用同一种低调灰。 */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = C.Muted) {
    Text(text, color = color, fontSize = 12.sp, modifier = modifier)
}

/** 按压手感：按下缩到 96%、松开回弹，点击时轻震一下。 */
fun Modifier.press(enabled: Boolean = true, onLongPress: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    var down by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (down) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = 900f), label = "press")
    this.graphicsLayer { scaleX = scale; scaleY = scale }.pointerInput(enabled, onClick, onLongPress) {
        if (!enabled) return@pointerInput
        detectTapGestures(
            onPress = { down = true; tryAwaitRelease(); down = false },
            onTap = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
            onLongPress = onLongPress?.let { lp -> { _: Offset -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); lp() } },
        )
    }
}

// ======================= 点阵数字 =======================

/** 5×7 点阵字形（每行 5 位，1=亮点）。支持 0-9 . : - + % / 空格 和 x（占位）。 */
private val GLYPHS: Map<Char, IntArray> = mapOf(
    '0' to intArrayOf(0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110),
    '1' to intArrayOf(0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110),
    '2' to intArrayOf(0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111),
    '3' to intArrayOf(0b11110, 0b00001, 0b00001, 0b01110, 0b00001, 0b00001, 0b11110),
    '4' to intArrayOf(0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010),
    '5' to intArrayOf(0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110),
    '6' to intArrayOf(0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110),
    '7' to intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000),
    '8' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110),
    '9' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100),
    '-' to intArrayOf(0, 0, 0, 0b11111, 0, 0, 0),
    '+' to intArrayOf(0, 0b00100, 0b00100, 0b11111, 0b00100, 0b00100, 0),
    'x' to intArrayOf(0, 0, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001),
    '/' to intArrayOf(0b00001, 0b00010, 0b00010, 0b00100, 0b01000, 0b01000, 0b10000),
    '%' to intArrayOf(0b11001, 0b11010, 0b00010, 0b00100, 0b01000, 0b01011, 0b10011),
)
private val NARROW: Map<Char, IntArray> = mapOf( // 1 列宽的标点
    '.' to intArrayOf(0, 0, 0, 0, 0, 0, 1),
    ':' to intArrayOf(0, 0, 1, 0, 1, 0, 0),
    ' ' to intArrayOf(0, 0, 0, 0, 0, 0, 0),
)

/**
 * 点阵数字（像电子屏）。dot = 每个点的格距；亮点用 color，灭点用很淡的同色做底纹，看起来像真的点阵屏。
 */
@Composable
fun DotMatrix(text: String, dot: Dp = 6.dp, color: Color = C.Text, dim: Boolean = true, modifier: Modifier = Modifier) {
    val chars = text.toCharArray().filter { GLYPHS.containsKey(it) || NARROW.containsKey(it) }
    val cols: Int = chars.sumOf { c: Char -> if (NARROW.containsKey(c)) 2 else 6 } - 1
    val off = color.copy(alpha = if (C.palette.isLight) 0.07f else 0.06f)
    Canvas(modifier.size(dot * cols.coerceAtLeast(1), dot * 7)) {
        val step = dot.toPx(); val r = step * 0.38f
        var x0 = 0
        for (ch in chars) {
            val narrow = NARROW[ch]
            val rows = narrow ?: GLYPHS[ch]!!
            val w = if (narrow != null) 1 else 5
            for (row in 0 until 7) for (c in 0 until w) {
                val on = if (narrow != null) rows[row] == 1 else (rows[row] shr (4 - c)) and 1 == 1
                if (on || dim) drawCircle(if (on) color else off, r, Offset((x0 + c) * step + step / 2, row * step + step / 2))
            }
            x0 += w + 1
        }
    }
}

/** 小进度条（细、圆头）。 */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, color: Color = C.Text) {
    Box(modifier.fillMaxWidth().height(3.dp).background(C.Line, RoundedCornerShape(2.dp))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).background(color, RoundedCornerShape(2.dp)))
    }
}

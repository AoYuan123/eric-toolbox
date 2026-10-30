package com.eric.toolbox.modules.gold

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.ui.C
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** 走势图数据：分时用折线（x 为 0..1 的时间轴位置），K 线用蜡烛。 */
sealed interface ChartData {
    val xLabels: Pair<String, String>

    data class Line(
        val points: List<Pair<Float, Double>>,
        val tips: List<String>,
        val ref: Double?,
        override val xLabels: Pair<String, String>,
    ) : ChartData

    data class Candles(val bars: List<Bar>, override val xLabels: Pair<String, String>) : ChartData
}

object Axis {
    /** 取整的价格刻度（约 4–6 个）。 */
    fun nice(lo: Double, hi: Double): Triple<Double, Double, List<Double>> {
        val span = if (hi - lo < 1e-6) 1.0 else hi - lo
        val raw = span / 4
        val p = 10.0.pow(floor(log10(raw)))
        val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * p }.first { it >= raw }
        val a = floor(lo / step) * step
        val b = ceil(hi / step) * step
        val ticks = generateSequence(a) { it + step }.takeWhile { it <= b + step / 1000 }.toList()
        return Triple(a, b, ticks)
    }

    /** 以昨收为中轴的对称刻度（分时图常用）。 */
    fun symmetric(ref: Double, values: List<Double>): Triple<Double, Double, List<Double>> {
        val dev = (values.maxOfOrNull { abs(it - ref) } ?: 1.0).coerceAtLeast(ref * 0.002) * 1.15
        return Triple(ref - dev, ref + dev, listOf(ref + dev, ref + dev / 2, ref, ref - dev / 2, ref - dev))
    }
}

private const val AXIS_W = 54f

/**
 * 带纵坐标的走势图：双指缩放、拖动平移；长按显示该时刻价格，单击取消。
 */
@Composable
fun PriceChart(data: ChartData, modifier: Modifier = Modifier, heightDp: Int = 190) {
    val measurer = rememberTextMeasurer()
    var scale by remember(data) { mutableFloatStateOf(1f) }
    var offset by remember(data) { mutableFloatStateOf(0f) } // 可见窗口起点（0..1-1/scale）
    var cross by remember(data) { mutableStateOf<Float?>(null) } // 长按位置（像素 x）
    val up = C.Up; val down = C.Down; val muted = C.Muted; val gold = C.Gold; val line = C.Line; val text = C.Text

    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(heightDp.dp)
                .pointerInput(data) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val plotW = size.width - AXIS_W.dp.toPx()
                        val newScale = (scale * zoom).coerceIn(1f, 12f)
                        val focus = ((centroid.x - AXIS_W.dp.toPx()) / plotW).coerceIn(0f, 1f)
                        val focusData = offset + focus / scale
                        var newOffset = focusData - focus / newScale
                        newOffset -= pan.x / plotW / newScale
                        scale = newScale
                        offset = newOffset.coerceIn(0f, 1f - 1f / newScale)
                    }
                }
                .pointerInput(data) {
                    detectTapGestures(onLongPress = { cross = it.x }, onTap = { cross = null }, onDoubleTap = { scale = 1f; offset = 0f })
                },
        ) {
            val axisW = AXIS_W.dp.toPx()
            val top = 6.dp.toPx(); val bottom = size.height - 4.dp.toPx()
            val plotW = size.width - axisW; val plotH = bottom - top
            val x0 = offset; val x1 = offset + 1f / scale
            fun px(xf: Float) = axisW + (xf - x0) / (x1 - x0) * plotW
            val labelStyle = TextStyle(fontSize = 10.sp, color = muted)

            when (data) {
                is ChartData.Line -> {
                    val vis = data.points.filter { it.first in x0..x1 }
                    if (vis.isEmpty()) {
                        drawText(measurer, "暂无数据", Offset(axisW + plotW / 2 - 24.dp.toPx(), top + plotH / 2), labelStyle)
                        return@Canvas
                    }
                    val (lo, hi, ticks) = if (data.ref != null) Axis.symmetric(data.ref, vis.map { it.second })
                    else Axis.nice(vis.minOf { it.second }, vis.maxOf { it.second })
                    fun py(v: Double) = (top + (hi - v) / (hi - lo) * plotH).toFloat()
                    drawGrid(ticks, ::py, axisW, measurer, labelStyle) { v ->
                        when {
                            data.ref == null -> muted
                            v > data.ref + 1e-6 -> up
                            v < data.ref - 1e-6 -> down
                            else -> muted
                        }
                    }
                    data.ref?.let { r ->
                        drawLine(muted, Offset(axisW, py(r)), Offset(size.width, py(r)), 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                    }
                    val path = Path(); val area = Path()
                    vis.forEachIndexed { i, (xf, v) ->
                        val x = px(xf); val y = py(v)
                        if (i == 0) { path.moveTo(x, y); area.moveTo(x, bottom); area.lineTo(x, y) } else { path.lineTo(x, y); area.lineTo(x, y) }
                    }
                    area.lineTo(px(vis.last().first), bottom); area.close()
                    drawPath(area, Brush.verticalGradient(listOf(gold.copy(alpha = 0.22f), gold.copy(alpha = 0.02f)), top, bottom))
                    drawPath(path, gold, style = Stroke(width = 1.6.dp.toPx()))
                    cross?.let { cx ->
                        val target = x0 + ((cx - axisW) / plotW) * (x1 - x0)
                        val idx = data.points.indices.minByOrNull { abs(data.points[it].first - target) } ?: return@let
                        val (xf, v) = data.points[idx]
                        drawCross(px(xf), py(v), top, bottom, axisW, text, line)
                        drawText(measurer, "${data.tips.getOrElse(idx) { "" }}  ${String.format(Locale.CHINA, "%.2f", v)}",
                            Offset(axisW + 4.dp.toPx(), top), TextStyle(fontSize = 11.sp, color = text))
                    }
                }
                is ChartData.Candles -> {
                    val n = data.bars.size
                    if (n == 0) return@Canvas
                    val i0 = floor(x0 * n).toInt().coerceIn(0, n - 1)
                    val i1 = ceil(x1 * n).toInt().coerceIn(i0 + 1, n)
                    val vis = data.bars.subList(i0, i1)
                    val (lo, hi, ticks) = Axis.nice(vis.minOf { it.low }, vis.maxOf { it.high })
                    fun py(v: Double) = (top + (hi - v) / (hi - lo) * plotH).toFloat()
                    drawGrid(ticks, ::py, axisW, measurer, labelStyle) { muted }
                    val cw = plotW / (n * (x1 - x0))
                    vis.forEachIndexed { k, b ->
                        val cx = px((i0 + k + 0.5f) / n)
                        val color = if (b.close >= b.open) up else down
                        drawLine(color, Offset(cx, py(b.high)), Offset(cx, py(b.low)), 1.dp.toPx())
                        val yTop = py(maxOf(b.open, b.close)); val yBot = py(minOf(b.open, b.close))
                        drawRect(color, Offset(cx - cw * 0.35f, yTop), Size(cw * 0.7f, maxOf(1f, yBot - yTop)))
                    }
                    cross?.let { cx ->
                        val k = floor((x0 + (cx - axisW) / plotW * (x1 - x0)) * n).toInt().coerceIn(i0, i1 - 1)
                        val b = data.bars[k]
                        drawCross(px((k + 0.5f) / n), py(b.close), top, bottom, axisW, text, line)
                        drawText(measurer,
                            String.format(Locale.CHINA, "%s 开%.2f 收%.2f 高%.2f 低%.2f", b.label, b.open, b.close, b.high, b.low),
                            Offset(axisW + 4.dp.toPx(), top), TextStyle(fontSize = 10.sp, color = text))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = AXIS_W.dp, top = 2.dp)) {
            Text(data.xLabels.first, color = C.Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(data.xLabels.second, color = C.Muted, fontSize = 11.sp)
        }
    }
}

private fun DrawScope.drawGrid(
    ticks: List<Double>, py: (Double) -> Float, axisW: Float,
    measurer: androidx.compose.ui.text.TextMeasurer, style: TextStyle, colorOf: (Double) -> Color,
) {
    ticks.forEach { v ->
        val y = py(v)
        if (y < 0 || y > size.height) return@forEach
        drawLine(Color(0x22888888), Offset(axisW, y), Offset(size.width, y), 1f)
        val label = if (v >= 100) String.format(Locale.CHINA, "%.2f", v).trimEnd('0').trimEnd('.') else String.format(Locale.CHINA, "%.2f", v)
        val layout = measurer.measure(label, style.copy(color = colorOf(v)))
        drawText(layout, topLeft = Offset(axisW - layout.size.width - 4.dp.toPx(), (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)))
    }
    drawLine(Color(0x44888888), Offset(axisW, 0f), Offset(axisW, size.height), 1f)
}

private fun DrawScope.drawCross(x: Float, y: Float, top: Float, bottom: Float, axisW: Float, color: Color, lineColor: Color) {
    drawLine(color.copy(alpha = 0.5f), Offset(x, top), Offset(x, bottom), 1f)
    drawLine(color.copy(alpha = 0.5f), Offset(axisW, y), Offset(size.width, y), 1f)
    drawCircle(lineColor, 4.dp.toPx(), Offset(x, y))
    drawCircle(color, 2.5.dp.toPx(), Offset(x, y))
}

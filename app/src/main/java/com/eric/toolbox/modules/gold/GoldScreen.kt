package com.eric.toolbox.modules.gold

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.AlertLog
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.CardBox
import com.eric.toolbox.ui.Mono
import com.eric.toolbox.ui.Pill
import com.eric.toolbox.ui.RowItem
import com.eric.toolbox.ui.SectionLabel
import com.eric.toolbox.ui.TopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GoldScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    // 界面打开时自己也刷新（即使后台监控关闭也能看价格）
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { GoldModule.refresh(ctx) }
            delay(GoldModule.config(ctx).intervalSec.coerceAtLeast(15) * 1000L)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(listOf("实时金价", "实时金价 · 档位", "实时金价 · 设置")[tab], onBack)
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> QuoteTab()
                1 -> TiersTab()
                else -> SettingsTab()
            }
        }
        Row(Modifier.fillMaxWidth().background(C.Surface).padding(vertical = 6.dp)) {
            NavItem("行情", Icons.AutoMirrored.Filled.TrendingUp, tab == 0, Modifier.weight(1f)) { tab = 0 }
            NavItem("档位", Icons.AutoMirrored.Filled.List, tab == 1, Modifier.weight(1f)) { tab = 1 }
            NavItem("设置", Icons.Filled.Settings, tab == 2, Modifier.weight(1f)) { tab = 2 }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (active) C.Gold else C.Muted
    Column(modifier.heightIn(min = 52.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = color)
        Text(label, color = color, fontSize = 12.sp)
    }
}

private fun f2(v: Double?) = v?.let { String.format(Locale.CHINA, "%.2f", it) } ?: "--"

@Composable
private fun QuoteTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snap by GoldModule.snapshot.collectAsState()
    val points by GoldModule.points.collectAsState()
    val cfg = GoldModule.config(ctx)
    val last = GoldModule.lastAlerted(ctx)
    val next = TierLogic.nextTier(last, cfg.startTier, cfg.step)
    val p = snap?.primary
    val fmtTime = remember { SimpleDateFormat("HH:mm:ss", Locale.CHINA) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("上海金 Au99.99 · 元/克", color = C.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                when {
                    snap == null -> Pill("获取中", C.Muted, C.Line)
                    p == null -> Pill("获取失败", C.Warn, C.WarnBg)
                    !snap!!.fresh -> Pill("数据延迟/休市", C.Warn, C.WarnBg)
                    Session.isTrading(System.currentTimeMillis()) -> Pill("交易中", C.Ok, C.OkBg)
                    else -> Pill("非交易时段", C.Muted, C.Line)
                }
            }
            Text(f2(p?.price), color = C.Gold, fontSize = 52.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
            if (p != null) {
                val diff = p.price - cfg.basePrice
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "较基准 ${GoldModule.fmtBase(cfg.basePrice)}：" + (if (diff >= 0) "▲ +" else "▼ ") + f2(diff),
                        color = if (diff >= 0) C.Up else C.Down, fontSize = 13.sp,
                    )
                    Text("高 ${f2(p.high)}  低 ${f2(p.low)}", color = C.Muted, fontSize = 13.sp)
                }
                p.prevClose?.let {
                    val chg = p.price - it
                    Text("较昨收 ${f2(it)}：" + (if (chg >= 0) "+" else "") + f2(chg), color = C.Muted, fontSize = 12.sp)
                }
            }
            Sparkline(points.map { it.second }, next.toDouble())
            Row {
                Text(
                    snap?.checks?.joinToString("  ") { "${it.source} ${if (it.code == "AU0") "沪金" else "T+D"} ${f2(it.price)}" } ?: "",
                    color = C.Muted, fontSize = 11.sp, modifier = Modifier.weight(1f),
                )
                Text(
                    if (snap?.verified == true) "多源一致 ✓" else "未通过校验",
                    color = if (snap?.verified == true) C.Ok else C.Warn, fontSize = 11.sp,
                )
            }
        }

        CardBox {
            Row {
                Text("下一档提醒", color = C.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (p != null) Text("还差 ${f2((p.price - next).coerceAtLeast(0.0))} 元", color = C.Muted, fontSize = 13.sp)
            }
            Text("$next", color = C.Text, fontSize = 30.sp, fontFamily = Mono)
            if (p != null) {
                val progress = (1 - ((p.price - next) / cfg.step)).coerceIn(0.0, 1.0).toFloat()
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = C.Gold, trackColor = C.Line)
            }
            val lastText = if (last > cfg.startTier) "尚无" else "$last"
            Text("已提醒到：$lastText · 档位 ${cfg.startTier} 起每 ${cfg.step} 元一档", color = C.Muted, fontSize = 12.sp)
        }

        Row {
            Text(
                "更新 " + (p?.timeMs?.let { fmtTime.format(Date(it)) } ?: "--") + "（行情时间）",
                color = C.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            Text("立即刷新", color = C.Gold, fontSize = 12.sp, modifier = Modifier.clickable { scope.launch { runCatching { GoldModule.refresh(ctx) } } })
        }
        if (!cfg.enabled) Text("后台监控已关闭，只有打开本页时才刷新。可在“设置”里开启。", color = C.Warn, fontSize = 12.sp)
        Box(Modifier.height(12.dp))
    }
}

@Composable
private fun Sparkline(values: List<Double>, tierLine: Double) {
    if (values.size < 2) {
        Text("走势图会在采到更多数据后显示", color = C.Muted, fontSize = 11.sp)
        return
    }
    val min = minOf(values.min(), tierLine) - 0.5
    val max = maxOf(values.max(), tierLine) + 0.5
    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        fun y(v: Double) = (size.height * (1 - (v - min) / (max - min))).toFloat()
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        drawPath(path, C.Gold, style = Stroke(width = 4f))
        val ty = y(tierLine)
        drawLine(C.Muted, Offset(0f, ty), Offset(size.width, ty), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
    }
}

@Composable
private fun TiersTab() {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val cfg = GoldModule.config(ctx)
    val last = remember(version) { GoldModule.lastAlerted(ctx) }
    val snap by GoldModule.snapshot.collectAsState()
    val price = snap?.primary?.price
    val next = TierLogic.nextTier(last, cfg.startTier, cfg.step)
    val logs = remember(version) { AlertLog.forModule(ctx, GoldModule.id) }
    val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.CHINA) }
    // 显示从起始档位往下、至少到“下一档往下 3 档”
    val bottom = minOf(next, cfg.startTier) - cfg.step * 3
    val tiers = generateSequence(cfg.startTier) { it - cfg.step }.takeWhile { it >= bottom }.toList()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            tiers.forEach { t ->
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$t", color = C.Text, fontSize = 20.sp, fontFamily = Mono, modifier = Modifier.weight(0.3f))
                    val note = if (t == next && price != null) "还差 ${f2((price - t).coerceAtLeast(0.0))} 元" else ""
                    Text(note, color = C.Muted, fontSize = 13.sp, modifier = Modifier.weight(0.4f))
                    when {
                        t >= last -> Pill("已提醒", C.Muted, C.Line)
                        t == next -> Pill("下一档", C.Bg, C.Gold)
                        else -> Pill("待触发", C.Muted, C.Bg)
                    }
                }
            }
            Text("无下限，按 ${cfg.step} 元一档继续向下", color = C.Muted, fontSize = 12.sp)
        }
        Text("· 每个档位只提醒一次\n· 价格回升超过上次提醒档位 ${cfg.step} 元以上，自动重置到当前价上方最近一档\n· 数据过期（如节假日休市）或多源校验不通过时不提醒", color = C.Muted, fontSize = 13.sp)

        SectionLabel("提醒记录")
        CardBox {
            if (logs.isEmpty()) Text("还没有提醒记录", color = C.Muted, fontSize = 13.sp)
            logs.take(50).forEach { e ->
                Row {
                    Text(e.title, color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(fmt.format(Date(e.timeMs)), color = C.Muted, fontSize = 12.sp)
                }
            }
        }
        OutlinedButton(onClick = { GoldModule.resetTiers(ctx); version++ }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("手动重置档位", color = C.Text)
        }
        Box(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsTab() {
    val ctx = LocalContext.current
    val cfg0 = remember { GoldModule.config(ctx) }
    var enabled by remember { mutableStateOf(cfg0.enabled) }
    var base by remember { mutableStateOf(GoldModule.fmtBase(cfg0.basePrice)) }
    var start by remember { mutableStateOf(cfg0.startTier.toString()) }
    var step by remember { mutableStateOf(cfg0.step.toString()) }
    var interval by remember { mutableIntStateOf(cfg0.intervalSec) }
    var day by remember { mutableStateOf(cfg0.daySession) }
    var night by remember { mutableStateOf(cfg0.nightSession) }
    var msg by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            ToggleRow("后台监控", "关闭后不再提醒", enabled) { enabled = it }
        }
        SectionLabel("档位规则")
        CardBox {
            NumberField("基准价（元/克）", base) { base = it }
            NumberField("首个提醒档位", start) { start = it }
            NumberField("每档间隔（元）", step) { step = it }
            Text("修改首个档位或间隔后，已提醒记录会重置，从首个档位重新开始。", color = C.Muted, fontSize = 12.sp)
        }
        SectionLabel("刷新与时段")
        CardBox {
            Text("交易时段刷新间隔", color = C.Text, fontSize = 15.sp)
            Row(Modifier.fillMaxWidth().background(C.Bg, RoundedCornerShape(12.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60).forEach { v ->
                    val on = interval == v
                    Box(
                        Modifier.weight(1f).heightIn(min = 40.dp).background(if (on) C.Gold else C.Bg, RoundedCornerShape(9.dp)).clickable { interval = v },
                        contentAlignment = Alignment.Center,
                    ) { Text("$v 秒", color = if (on) C.Bg else C.Muted, fontSize = 14.sp) }
                }
            }
            Text("非交易时段每 15 分钟查一次，省电省流量。", color = C.Muted, fontSize = 12.sp)
            ToggleRow("日盘", "9:00–15:30", day) { day = it }
            ToggleRow("夜盘", "20:00–次日 2:30", night) { night = it }
        }
        SectionLabel("数据源")
        CardBox {
            RowItem("主源", "东方财富 Au99.99")
            RowItem("校验源", "东方财富 T+D · 新浪 沪金")
            Text("主源与至少一个校验源偏差在容差内（T+D 1.5%、沪金 3%），且行情时间在 10 分钟内，才会提醒。", color = C.Muted, fontSize = 12.sp)
        }
        Button(
            onClick = {
                val b = base.toDoubleOrNull()
                val s = start.toIntOrNull()
                val st = step.toIntOrNull()
                msg = when {
                    b == null || b <= 0 -> "基准价需要是大于 0 的数字"
                    s == null || s <= 0 -> "首个档位需要是大于 0 的整数"
                    st == null || st <= 0 || st > s -> "每档间隔需要是大于 0、且不超过首个档位的整数"
                    else -> {
                        GoldModule.saveConfig(ctx, GoldConfig(enabled, b, s, st, interval, day, night))
                        "已保存"
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Bg),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("保存设置") }
        if (msg.isNotBlank()) Text(msg, color = if (msg == "已保存") C.Ok else C.Warn, fontSize = 13.sp)
        Box(Modifier.height(12.dp))
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 15.sp)
            Text(sub, color = C.Muted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.Gold))
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

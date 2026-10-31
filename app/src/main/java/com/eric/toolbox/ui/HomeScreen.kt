package com.eric.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.Route
import com.eric.toolbox.core.Category
import com.eric.toolbox.core.Heartbeat
import com.eric.toolbox.core.ModuleRegistry
import com.eric.toolbox.core.MonitorService
import com.eric.toolbox.core.RunWindow
import com.eric.toolbox.core.ToolModule
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(go: (Route) -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); tick++ } }

    val modules = remember(tick) { ModuleRegistry.visibleOrdered(ctx) }
    val runningCount = remember(tick) { ModuleRegistry.all.count { it.background?.isEnabled(ctx) == true } }
    val beat = remember(tick) { Heartbeat.last(ctx) }
    val expectNext = remember(tick) { Heartbeat.expectedNext(ctx) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Eric的百宝箱", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.Text)

        HealthBar(runningCount, beat, expectNext, onFix = { MonitorService.sync(ctx); go(Route.KeepAlive) })

        for (cat in Category.entries) {
            val list = modules.filter { it.category == cat }
            SectionLabel(cat.title)
            if (list.isEmpty()) {
                EmptySlot(if (cat == Category.MONITOR) "暂无监控模块" else "以后添加的工具会出现在这里")
            } else {
                list.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { m -> ModuleTile(m, Modifier.weight(1f)) { go(Route.Module(m.id)) } }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }

    }
}

@Composable
private fun HealthBar(runningCount: Int, beatMs: Long, expectNextMs: Long, onFix: () -> Unit) {
    val now = System.currentTimeMillis()
    val fmt = SimpleDateFormat("HH:mm", Locale.CHINA)
    val (text, fg, bg) = when {
        runningCount == 0 -> Triple("没有开启后台监控", C.Muted, C.Surface)
        !MonitorService.running || beatMs == 0L || now - expectNextMs > 3 * 60_000 ->
            Triple("后台可能已被系统停止 · 点此修复", C.Warn, C.WarnBg)
        !RunWindow.isOpen(LocalContext.current) ->
            Triple("后台运行中 · 提醒时段外只更新数据（${RunWindow.describe(LocalContext.current)} 提醒）", C.Ok, C.OkBg)
        else -> Triple("后台运行正常 · $runningCount 个监控在跑", C.Ok, C.OkBg)
    }
    Row(
        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(14.dp)).clickable(onClick = onFix)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(fg, CircleShape))
        Text(text, color = fg, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (beatMs > 0) Text("心跳 ${fmt.format(Date(beatMs))}", color = C.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ModuleTile(m: ToolModule, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        modifier.heightIn(min = 132.dp).background(C.Surface, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(C.GoldBg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(m.icon, null, tint = C.Gold, modifier = Modifier.size(20.dp))
            }
            Box(Modifier.weight(1f))
            if (m.background?.isEnabled(ctx) == true) Text("运行中", color = C.Ok, fontSize = 11.sp)
        }
        Text(m.name, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        m.HomeSummary()
    }
}

@Composable
private fun EmptySlot(text: String) {
    Box(
        Modifier.fillMaxWidth().border(1.dp, C.Line, RoundedCornerShape(20.dp)).padding(16.dp),
    ) { Text(text, color = C.Muted, fontSize = 13.sp) }
}

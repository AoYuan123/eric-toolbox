package com.eric.toolbox.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eric.toolbox.BuildConfig
import com.eric.toolbox.Route
import com.eric.toolbox.core.AlertLog
import com.eric.toolbox.core.ModuleRegistry
import com.eric.toolbox.core.MonitorService
import com.eric.toolbox.core.Notifier
import com.eric.toolbox.core.RunWindow
import com.eric.toolbox.core.Store
import com.eric.toolbox.core.UpdateInfo
import com.eric.toolbox.core.Updater
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 页面回到前台时刷新一次（例如从系统设置返回后重新检查状态）。 */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

// ======================= 我的 =======================

@Composable
fun MineScreen(go: (Route) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = rememberResumeTick()
    var themeKey by remember { mutableStateOf(ThemeManager.key(ctx)) }
    var redUp by remember { mutableStateOf(C.redUp) }
    var showWindow by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf("") }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var testMsg by remember { mutableStateOf("") }
    val keepAliveDone = remember(tick) { keepAliveStatus(ctx).count { it } }
    val windowText = remember(tick, showWindow) { RunWindow.describe(ctx) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("我的", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Text)
        Row(
            Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(52.dp).background(C.GoldBg, CircleShape), contentAlignment = Alignment.Center) {
                Text("E", color = C.Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("Eric", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text("v${BuildConfig.VERSION_NAME} · ${ModuleRegistry.all.size} 个模块", color = C.Muted, fontSize = 12.sp)
            }
        }

        SectionLabel("主题")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val options = Palettes.all.map { it.key to it.name } + ("system" to "跟随系统")
            options.forEach { (k, name) ->
                val p = Palettes.all.firstOrNull { it.key == k }
                val sel = themeKey == k
                Column(
                    Modifier.weight(1f).background(C.Surface, RoundedCornerShape(16.dp))
                        .border(2.dp, if (sel) C.Gold else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { themeKey = k; ThemeManager.set(ctx, k) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.size(44.dp, 30.dp).background(p?.bg ?: Color(0xFF5A606B), RoundedCornerShape(8.dp)).padding(4.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) { Box(Modifier.size(18.dp, 6.dp).background(p?.gold ?: Color(0xFFE8BE5A), RoundedCornerShape(3.dp))) }
                    Text(name, color = C.Text, fontSize = 12.sp)
                }
            }
        }

        SectionLabel("提醒与后台")
        ListCard {
            RowItem("通知与提醒", "系统通知设置", onClick = { openAppNotificationSettings(ctx) })
            RowItem("发送一条测试提醒", testMsg, onClick = {
                Notifier.test(ctx); testMsg = if (Notifier.canPost(ctx)) "已发送" else "通知未开启"
            })
            RowItem("后台保活设置", "$keepAliveDone / 5 项已完成", onClick = { go(Route.KeepAlive) })
            RowItem("提醒时段", windowText, sub = "数据全天更新；时段外的跌档在时段开始时汇总提醒一次", onClick = { showWindow = true })
        }

        SectionLabel("通用")
        ListCard {
            RowItem("语言", "简体中文", onClick = { showLang = true })
            RowItem("整理首页模块", "排序 / 隐藏", onClick = { go(Route.Arrange) })
            RowItem("涨跌颜色", if (redUp) "红涨绿跌" else "绿涨红跌", onClick = {
                redUp = !redUp; ThemeManager.setRedUp(ctx, redUp)
            })
            RowItem("数据备份与恢复", "", onClick = { go(Route.Backup) })
        }

        SectionLabel("关于")
        ListCard {
            RowItem("检查更新", updateMsg.ifBlank { "当前 v${BuildConfig.VERSION_NAME}" }, onClick = {
                updateMsg = "正在检查…"
                scope.launch {
                    updateMsg = try {
                        update = Updater.check()
                        if (update == null) "已是最新版本" else "发现新版本 v${update!!.versionName}"
                    } catch (e: Exception) {
                        "检查失败：${e.message ?: "网络错误"}"
                    }
                }
            })
            update?.let { u ->
                if (u.notes.isNotBlank()) Text(u.notes, color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                Button(
                    onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.apkUrl ?: u.pageUrl))) },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Bg),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) { Text("下载并安装 v${u.versionName}") }
            }
            RowItem("数据来源", "东方财富 · 新浪 · 工商银行")
        }
        Text("行情数据来自各网站公开页面，仅供个人参考。", color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
    }

    if (showWindow) WindowDialog(ctx) { showWindow = false; MonitorService.sync(ctx) }
    if (showLang) {
        AlertDialog(
            onDismissRequest = { showLang = false },
            confirmButton = { TextButton(onClick = { showLang = false }) { Text("知道了") } },
            title = { Text("语言") },
            text = { Text("目前只提供简体中文。需要其他语言时告诉我，再加。") },
        )
    }
}

@Composable
private fun WindowDialog(ctx: Context, onDone: () -> Unit) {
    var start by remember { mutableStateOf((RunWindow.startMin(ctx) / 60).toString()) }
    var end by remember { mutableStateOf((RunWindow.endMin(ctx) / 60).toString()) }
    var weekendOff by remember { mutableStateOf(RunWindow.weekendOff(ctx)) }
    var err by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("提醒时段") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(start, { start = it.filter(Char::isDigit) }, label = { Text("开始（时）") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(end, { end = it.filter(Char::isDigit) }, label = { Text("结束（时）") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                ToggleRow("周末不提醒", null, weekendOff) { weekendOff = it }
                if (err.isNotBlank()) Text(err, color = C.Warn, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = start.toIntOrNull(); val e = end.toIntOrNull()
                if (s == null || e == null || s !in 0..23 || e !in 1..24 || s >= e) {
                    err = "开始需在 0–23，结束需在 1–24，且开始早于结束"
                } else {
                    RunWindow.set(ctx, s * 60, e * 60, weekendOff); onDone()
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("取消") } },
    )
}

// ======================= 记录 =======================

@Composable
fun RecordsScreen() {
    val ctx = LocalContext.current
    remember { AlertLog.load(ctx) }
    val all by AlertLog.flow.collectAsState()
    var filter by remember { mutableStateOf("all") }
    val fmtDay = remember { SimpleDateFormat("M月d日", Locale.CHINA) }
    val fmtTime = remember { SimpleDateFormat("HH:mm", Locale.CHINA) }
    val list = all.filter { filter == "all" || it.moduleId == filter }
    val chips = listOf("all" to "全部") + ModuleRegistry.all.filter { it.background != null }.map { it.id to it.name } + (AlertLog.SYSTEM to "系统")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("记录", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { (k, name) ->
                val on = filter == k
                Box(
                    Modifier.background(if (on) C.Gold else C.Surface, RoundedCornerShape(50)).clickable { filter = k }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) { Text(name, color = if (on) (if (C.palette.isLight) Color.White else C.Bg) else C.Muted, fontSize = 13.sp) }
            }
        }
        if (list.isEmpty()) Text("还没有记录", color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp))
        var lastDay = ""
        list.forEach { e ->
            val day = dayLabel(e.timeMs, fmtDay)
            if (day != lastDay) {
                SectionLabel(day); lastDay = day
            }
            CardBox {
                Row {
                    Text(e.title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(fmtTime.format(Date(e.timeMs)), color = C.Muted, fontSize = 12.sp)
                }
                Text(e.body, color = C.Text.copy(alpha = 0.85f), fontSize = 13.sp)
                Text(if (e.moduleId == AlertLog.SYSTEM) "系统" else ModuleRegistry.byId(e.moduleId)?.name ?: e.moduleId, color = C.Muted, fontSize = 11.sp)
            }
        }
        Box(Modifier.height(8.dp))
    }
}

private fun dayLabel(ms: Long, fmt: SimpleDateFormat): String {
    val c = Calendar.getInstance(RunWindow.TZ)
    val today = c.get(Calendar.DAY_OF_YEAR) to c.get(Calendar.YEAR)
    c.timeInMillis = ms
    val d = c.get(Calendar.DAY_OF_YEAR) to c.get(Calendar.YEAR)
    return when {
        d == today -> "今天"
        d.second == today.second && d.first == today.first - 1 -> "昨天"
        else -> fmt.format(Date(ms))
    }
}

// ======================= 后台保活引导 =======================

@SuppressLint("BatteryLife")
private fun keepAliveStatus(ctx: Context): List<Boolean> {
    val prefs = Store.global(ctx)
    return listOf(
        Notifier.canPost(ctx),
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName),
        RunWindow.canExact(ctx),
        prefs.getBoolean("ka_autostart", false),
        prefs.getBoolean("ka_lock", false),
    )
}

@SuppressLint("BatteryLife")
@Composable
fun KeepAliveScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    var version by remember { mutableIntStateOf(0) }
    val st = remember(tick, version) { keepAliveStatus(ctx) }
    val prefs = Store.global(ctx)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("后台保活设置", onBack)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("国产手机会自动清理后台应用。完成下面几步，锁屏后也能按时提醒。", color = C.Muted, fontSize = 14.sp)
            Text("检测到机型：${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", color = C.Muted, fontSize = 13.sp)

            Step(1, "允许通知", "否则收不到提醒", st[0]) { openAppNotificationSettings(ctx) }
            Step(2, "关闭电池优化", "设为“无限制”，后台才能被自动拉起", st[1]) {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                } catch (_: Exception) {
                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            Step(3, "允许闹钟和提醒", "每天到点准时唤醒监控", st[2]) {
                if (Build.VERSION.SDK_INT >= 31) {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                    } catch (_: Exception) { openAppDetails(ctx) }
                }
            }
            Step(4, "允许自启动", "开机和被清理后自动拉起（各品牌入口不同）", st[3]) {
                openAutoStart(ctx)
                prefs.edit().putBoolean("ka_autostart", true).apply(); version++
            }
            Step(5, "在最近任务里锁定 App", "打开多任务界面，下拉或长按本 App 卡片，选“锁定”", st[4], buttonText = "我已锁定") {
                prefs.edit().putBoolean("ka_lock", true).apply(); version++
            }
            Text("第 4、5 步系统无法自动检测，按下按钮后会记为已完成。", color = C.Muted, fontSize = 12.sp)
            OutlinedButton(onClick = { MonitorService.sync(ctx) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("重新启动后台服务", color = C.Text)
            }
            Box(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Step(n: Int, title: String, sub: String, done: Boolean, buttonText: String = "去设置", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).background(if (done) C.OkBg else C.Line, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (done) "✓" else "$n", color = if (done) C.Ok else C.Text, fontSize = 14.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 15.sp)
            Text(sub, color = C.Muted, fontSize = 12.sp)
        }
        if (done) Text("已完成", color = C.Ok, fontSize = 14.sp)
        else Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Bg)) { Text(buttonText) }
    }
}

fun openAppNotificationSettings(ctx: Context) {
    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    try { ctx.startActivity(i) } catch (_: Exception) { openAppDetails(ctx) }
}

private fun openAppDetails(ctx: Context) {
    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
}

/** 尝试跳到各品牌的自启动管理页，全部失败则打开应用详情页。 */
private fun openAutoStart(ctx: Context) {
    val candidates = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
    )
    for (c in candidates) {
        try {
            ctx.startActivity(Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
    openAppDetails(ctx)
}

// ======================= 整理首页 =======================

@Composable
fun ArrangeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var order by remember { mutableStateOf(ModuleRegistry.allOrdered(ctx).map { it.id }) }
    var version by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("整理首页", onBack)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("调整模块在首页上的顺序，或把暂时不用的隐藏起来。隐藏不会停止它的后台监控。", color = C.Muted, fontSize = 13.sp)
            order.forEachIndexed { idx, id ->
                val m = ModuleRegistry.byId(id) ?: return@forEachIndexed
                val hidden = remember(version) { ModuleRegistry.isHidden(ctx, id) }
                Row(
                    Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(m.name, color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    TextButton(enabled = idx > 0, onClick = {
                        order = order.toMutableList().also { val t = it[idx - 1]; it[idx - 1] = it[idx]; it[idx] = t }
                        ModuleRegistry.saveOrder(ctx, order)
                    }) { Text("上移") }
                    TextButton(enabled = idx < order.lastIndex, onClick = {
                        order = order.toMutableList().also { val t = it[idx + 1]; it[idx + 1] = it[idx]; it[idx] = t }
                        ModuleRegistry.saveOrder(ctx, order)
                    }) { Text("下移") }
                    TextButton(onClick = { ModuleRegistry.setHidden(ctx, id, !hidden); version++ }) {
                        Text(if (hidden) "显示" else "隐藏", color = if (hidden) C.Gold else C.Muted)
                    }
                }
            }
        }
    }
}

// ======================= 备份与恢复 =======================

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var importText by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("数据备份与恢复", onBack)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("把所有设置、持仓交易明细和提醒记录复制成一段文本，换手机或重装前保存好；恢复时粘贴回来。", color = C.Muted, fontSize = 13.sp)
            Button(
                onClick = {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("Eric百宝箱备份", Store.exportJson(ctx)))
                    msg = "已复制到剪贴板，请粘贴保存到备忘录或发给自己"
                },
                colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Bg),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("导出备份到剪贴板") }
            OutlinedTextField(
                value = importText, onValueChange = { importText = it },
                label = { Text("粘贴备份文本") }, modifier = Modifier.fillMaxWidth(), maxLines = 6,
            )
            OutlinedButton(enabled = importText.isNotBlank(), onClick = {
                msg = try {
                    Store.importJson(ctx, importText); importText = ""
                    ThemeManager.apply(ctx)
                    MonitorService.sync(ctx)
                    "恢复成功"
                } catch (e: Exception) {
                    "恢复失败：${e.message}"
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("从文本恢复（会覆盖现有数据）", color = C.Text) }
            if (msg.isNotBlank()) Text(msg, color = C.Muted, fontSize = 13.sp)
        }
    }
}

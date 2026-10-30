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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.eric.toolbox.core.Store
import com.eric.toolbox.core.UpdateInfo
import com.eric.toolbox.core.Updater
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
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

// ---------------- 后台保活引导 ----------------

@SuppressLint("BatteryLife")
@Composable
fun KeepAliveScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    val notifOk = remember(tick) { Notifier.canPost(ctx) }
    val batteryOk = remember(tick) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    val prefs = Store.global(ctx)
    var autoStartDone by remember { mutableStateOf(prefs.getBoolean("ka_autostart", false)) }
    var lockDone by remember { mutableStateOf(prefs.getBoolean("ka_lock", false)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("后台保活设置", onBack)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("国产手机会自动清理后台应用。完成下面几步，锁屏后也能按时提醒。", color = C.Muted, fontSize = 14.sp)
            Text("检测到机型：${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", color = C.Muted, fontSize = 13.sp)

            Step(1, "允许通知", "否则收不到提醒", notifOk) { openAppNotificationSettings(ctx) }
            Step(2, "关闭电池优化", "设为“无限制”，也让后台能被自动拉起", batteryOk) {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                } catch (_: Exception) {
                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            Step(3, "允许自启动", "开机和被清理后自动拉起（各品牌入口不同）", autoStartDone) {
                openAutoStart(ctx)
                autoStartDone = true; prefs.edit().putBoolean("ka_autostart", true).apply()
            }
            Step(4, "在最近任务里锁定 App", "打开多任务界面，下拉或长按本 App 卡片，选“锁定”", lockDone, buttonText = "我已锁定") {
                lockDone = true; prefs.edit().putBoolean("ka_lock", true).apply()
            }
            Text("第 3、4 步系统无法自动检测，按下按钮后会记为已完成。", color = C.Muted, fontSize = 12.sp)
            OutlinedButton(onClick = { MonitorService.sync(ctx) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("重新启动后台服务", color = C.Text)
            }
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

private fun openAppNotificationSettings(ctx: Context) {
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

// ---------------- 全部提醒记录 ----------------

@Composable
fun AlertsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val list = remember { AlertLog.load(ctx) }
    val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.CHINA) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("全部提醒记录", onBack)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list.isEmpty()) Text("还没有提醒记录", color = C.Muted)
            list.forEach { e ->
                CardBox {
                    Row {
                        Text(e.title, color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(fmt.format(Date(e.timeMs)), color = C.Muted, fontSize = 12.sp)
                    }
                    Text(e.body, color = C.Muted, fontSize = 13.sp)
                    Text(ModuleRegistry.byId(e.moduleId)?.name ?: e.moduleId, color = C.Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------------- 整理主页 ----------------

@Composable
fun ArrangeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var order by remember { mutableStateOf(ModuleRegistry.allOrdered(ctx).map { it.id }) }
    var version by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("整理主页", onBack)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("调整模块在主页上的顺序，或把暂时不用的隐藏起来。隐藏不会停止它的后台监控。", color = C.Muted, fontSize = 13.sp)
            order.forEachIndexed { idx, id ->
                val m = ModuleRegistry.byId(id) ?: return@forEachIndexed
                val hidden = remember(version) { ModuleRegistry.isHidden(ctx, id) }
                Row(
                    Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
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
                    Switch(
                        checked = !hidden,
                        onCheckedChange = { ModuleRegistry.setHidden(ctx, id, !it); version++ },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.Gold),
                    )
                }
            }
        }
    }
}

// ---------------- 百宝箱设置：更新、备份、省电 ----------------

@Composable
fun GlobalSettingsScreen(onBack: () -> Unit, go: (Route) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = Store.global(ctx)
    var precise by remember { mutableStateOf(prefs.getBoolean("precise_timing", true)) }
    var updateMsg by remember { mutableStateOf("") }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var importText by remember { mutableStateOf("") }
    var backupMsg by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("百宝箱设置", onBack)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("版本更新")
            CardBox {
                RowItem("当前版本", "v${BuildConfig.VERSION_NAME}")
                Button(
                    onClick = {
                        updateMsg = "正在检查…"
                        scope.launch {
                            updateMsg = try {
                                update = Updater.check()
                                if (update == null) "已经是最新版本" else "发现新版本 v${update!!.versionName}"
                            } catch (e: Exception) {
                                "检查失败：${e.message ?: "网络错误"}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Bg),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("检查更新") }
                if (updateMsg.isNotBlank()) Text(updateMsg, color = C.Muted, fontSize = 13.sp)
                update?.let { u ->
                    if (u.notes.isNotBlank()) Text(u.notes, color = C.Muted, fontSize = 12.sp)
                    OutlinedButton(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.apkUrl ?: u.pageUrl)))
                    }, modifier = Modifier.fillMaxWidth()) { Text("下载并安装 v${u.versionName}", color = C.Text) }
                    Text("下载完成后点开安装包即可覆盖安装，设置和记录都会保留。", color = C.Muted, fontSize = 12.sp)
                }
            }

            SectionLabel("耗电")
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("交易时段锁屏也准点刷新", color = C.Text, fontSize = 15.sp)
                        Text("开启后交易时段会保持 CPU 唤醒，提醒更及时，但更耗电", color = C.Muted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = precise,
                        onCheckedChange = { precise = it; prefs.edit().putBoolean("precise_timing", it).apply() },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.Gold),
                    )
                }
            }

            SectionLabel("备份与恢复")
            CardBox {
                Text("把所有设置和提醒记录复制成一段文本，换手机或重装前保存好；恢复时粘贴回来。", color = C.Muted, fontSize = 13.sp)
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("Eric百宝箱备份", Store.exportJson(ctx)))
                    backupMsg = "已复制到剪贴板，请粘贴保存到备忘录或发给自己"
                }, modifier = Modifier.fillMaxWidth()) { Text("导出备份到剪贴板", color = C.Text) }
                OutlinedTextField(
                    value = importText, onValueChange = { importText = it },
                    label = { Text("粘贴备份文本") }, modifier = Modifier.fillMaxWidth(), maxLines = 4,
                )
                OutlinedButton(enabled = importText.isNotBlank(), onClick = {
                    backupMsg = try {
                        Store.importJson(ctx, importText); importText = ""
                        MonitorService.sync(ctx)
                        "恢复成功"
                    } catch (e: Exception) {
                        "恢复失败：${e.message}"
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("从文本恢复", color = C.Text) }
                if (backupMsg.isNotBlank()) Text(backupMsg, color = C.Muted, fontSize = 13.sp)
            }

            SectionLabel("其他")
            CardBox {
                RowItem("后台保活设置", onClick = { go(Route.KeepAlive) })
                RowItem("整理主页", onClick = { go(Route.Arrange) })
            }
            Box(Modifier.heightIn(min = 24.dp))
        }
    }
}

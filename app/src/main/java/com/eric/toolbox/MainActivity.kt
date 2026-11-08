package com.eric.toolbox

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import com.eric.toolbox.ui.tr
import com.eric.toolbox.ui.press
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.ModuleRegistry
import com.eric.toolbox.core.MonitorService
import com.eric.toolbox.core.UpdateBadge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.eric.toolbox.ui.ArrangeScreen
import com.eric.toolbox.ui.BackupScreen
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.HomeScreen
import com.eric.toolbox.ui.MailScreen
import com.eric.toolbox.ui.KeepAliveScreen
import com.eric.toolbox.ui.MineScreen
import com.eric.toolbox.ui.RecordsScreen
import com.eric.toolbox.ui.ThemeManager
import com.eric.toolbox.ui.ToolboxTheme

sealed interface Route {
    data object Home : Route
    data object Records : Route
    data object Mine : Route
    data class Module(val id: String) : Route
    data object KeepAlive : Route
    data object Arrange : Route
    data object Backup : Route
    data object Mail : Route
    data object English : Route
    data object Storage : Route
    data object Health : Route
}

private val TABS = setOf<Route>(Route.Home, Route.English, Route.Mine)

class MainActivity : ComponentActivity() {
    private val stack = mutableStateListOf<Route>(Route.Home)

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        MonitorService.sync(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply(this)
        applySystemBars()
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        handleIntent(intent)

        setContent {
            // 主题切换时同步更新状态栏图标颜色
            LaunchedEffect(Unit) { snapshotFlow { C.palette.isLight }.collect { applySystemBars() } }
            ToolboxTheme {
                val go: (Route) -> Unit = { r ->
                    if (r in TABS) { stack.clear(); stack.add(r) } else stack.add(r)
                }
                val back: () -> Unit = {
                    when {
                        stack.size > 1 -> stack.removeAt(stack.lastIndex)
                        stack.last() != Route.Home -> { stack.clear(); stack.add(Route.Home) }
                        else -> finish()
                    }
                }
                BackHandler(enabled = stack.size > 1 || stack.last() != Route.Home) { back() }
                val current = stack.last()
                Column(Modifier.fillMaxSize().background(C.Bg).safeDrawingPadding()) {
                    Box(Modifier.weight(1f)) {
                        when (current) {
                            Route.Home -> HomeScreen(go)
                            Route.Records -> RecordsScreen(back)
                            Route.Mine -> MineScreen(go)
                            is Route.Module -> {
                                val m = ModuleRegistry.byId(current.id)
                                if (m != null) m.Screen(onBack = back) else HomeScreen(go)
                            }
                            Route.KeepAlive -> KeepAliveScreen(back)
                            Route.Arrange -> ArrangeScreen(back)
                            Route.Backup -> BackupScreen(back)
                            Route.Mail -> MailScreen(back)
                            Route.English -> com.eric.toolbox.modules.english.EnglishRoot(onBack = back, isTab = true)
                            Route.Storage -> com.eric.toolbox.ui.StorageScreen(back)
                            Route.Health -> com.eric.toolbox.ui.HealthScreen(back)
                        }
                    }
                    if (current in TABS) BottomBar(current, go)
                }
            }
        }
    }

    private fun applySystemBars() {
        val light = C.palette.isLight
        val style = if (light) SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
        else SystemBarStyle.dark(AColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (com.eric.toolbox.core.KeyHooks.handler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeManager.apply(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.apply(this)
        MonitorService.sync(this)
        lifecycleScope.launch { UpdateBadge.refresh(applicationContext) }
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_MODULE) ?: return
        if (ModuleRegistry.byId(id) != null) {
            stack.clear()
            stack.add(Route.Home)
            stack.add(Route.Module(id))
        }
    }

    companion object {
        const val EXTRA_MODULE = "module"
    }
}

@Composable
private fun BottomBar(current: Route, go: (Route) -> Unit) {
    val ctx = LocalContext.current
    UpdateBadge.tick
    val alertsNew = com.eric.toolbox.ui.newRecordCount(ctx) > 0
    // 五格：首页 · 学英语 · [AI 管家，居中凸起] · 翻译 · 我的
    Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp), contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.fillMaxWidth().height(60.dp).background(C.Surface, RoundedCornerShape(30.dp)), verticalAlignment = Alignment.CenterVertically) {
            TabItem(tr("首页", "Home"), Icons.Outlined.Home, current == Route.Home, Modifier.weight(1f)) { go(Route.Home) }
            TabItem(tr("学英语", "English"), Icons.Outlined.School, current == Route.English, Modifier.weight(1f)) { go(Route.English) }
            Box(Modifier.weight(1.1f))
            TabItem(tr("翻译", "Translate"), Icons.Outlined.Translate, false, Modifier.weight(1f)) { go(Route.Module("translate")) }
            TabItem(tr("我的", "Me"), Icons.Outlined.Person, current == Route.Mine, Modifier.weight(1f), dot = UpdateBadge.has(ctx) || alertsNew) { go(Route.Mine) }
        }
        // AI 键：点一下进 AI 管家，按住直接开始说话
        Column(Modifier.padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(62.dp).background(C.Bg, CircleShape).padding(4.dp).background(C.Gold, CircleShape).press(
                    onLongPress = { com.eric.toolbox.modules.ai.AiAgent.voiceOnOpen = true; go(Route.Module("ai")) },
                ) { go(Route.Module("ai")) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.GraphicEq, contentDescription = tr("AI 管家（按住说话）", "AI (hold to talk)"),
                    tint = if (C.palette.isLight) Color.White else Color(0xFF121110), modifier = Modifier.size(28.dp))
            }
            Text("AI", color = C.Muted, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TabItem(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, dot: Boolean = false, onClick: () -> Unit) {
    val color = if (active) C.Text else C.Muted
    Column(
        modifier.heightIn(min = 52.dp).press(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(icon, null, tint = color)
            if (dot) Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(9.dp).background(Color(0xFFE5484D), CircleShape))
        }
        Text(label, color = color, fontSize = 11.sp, fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
    }
}

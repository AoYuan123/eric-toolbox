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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
import com.eric.toolbox.ui.ArrangeScreen
import com.eric.toolbox.ui.BackupScreen
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.HomeScreen
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
}

private val TABS = setOf<Route>(Route.Home, Route.Records, Route.Mine)

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
                            Route.Records -> RecordsScreen()
                            Route.Mine -> MineScreen(go)
                            is Route.Module -> {
                                val m = ModuleRegistry.byId(current.id)
                                if (m != null) m.Screen(onBack = back) else HomeScreen(go)
                            }
                            Route.KeepAlive -> KeepAliveScreen(back)
                            Route.Arrange -> ArrangeScreen(back)
                            Route.Backup -> BackupScreen(back)
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
    Column(Modifier.fillMaxWidth().background(C.Surface)) {
        HorizontalDivider(color = C.Line)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TabItem("首页", Icons.Filled.Home, current == Route.Home, Modifier.weight(1f)) { go(Route.Home) }
            TabItem("记录", Icons.AutoMirrored.Filled.ReceiptLong, current == Route.Records, Modifier.weight(1f)) { go(Route.Records) }
            TabItem("我的", Icons.Filled.Person, current == Route.Mine, Modifier.weight(1f)) { go(Route.Mine) }
        }
    }
}

@Composable
private fun TabItem(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (active) C.Gold else C.Muted
    Column(
        modifier.heightIn(min = 52.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = color)
        Text(label, color = color, fontSize = 12.sp)
    }
}

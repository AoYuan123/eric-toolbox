package com.eric.toolbox

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import com.eric.toolbox.core.ModuleRegistry
import com.eric.toolbox.core.MonitorService
import com.eric.toolbox.ui.AlertsScreen
import com.eric.toolbox.ui.ArrangeScreen
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.GlobalSettingsScreen
import com.eric.toolbox.ui.HomeScreen
import com.eric.toolbox.ui.KeepAliveScreen
import com.eric.toolbox.ui.ToolboxTheme

sealed interface Route {
    data object Home : Route
    data class Module(val id: String) : Route
    data object KeepAlive : Route
    data object Alerts : Route
    data object Arrange : Route
    data object Settings : Route
}

class MainActivity : ComponentActivity() {
    private val stack = mutableStateListOf<Route>(Route.Home)

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        MonitorService.sync(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        handleIntent(intent)

        setContent {
            ToolboxTheme {
                val go: (Route) -> Unit = { stack.add(it) }
                val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish() }
                BackHandler(enabled = stack.size > 1) { back() }
                Box(Modifier.fillMaxSize().background(C.Bg).safeDrawingPadding()) {
                    when (val r = stack.last()) {
                        Route.Home -> HomeScreen(go)
                        is Route.Module -> {
                            val m = ModuleRegistry.byId(r.id)
                            if (m != null) m.Screen(onBack = back) else HomeScreen(go)
                        }
                        Route.KeepAlive -> KeepAliveScreen(back)
                        Route.Alerts -> AlertsScreen(back)
                        Route.Arrange -> ArrangeScreen(back)
                        Route.Settings -> GlobalSettingsScreen(back, go)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
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

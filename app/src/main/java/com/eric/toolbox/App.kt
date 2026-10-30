package com.eric.toolbox

import android.app.Application
import com.eric.toolbox.core.AlertLog
import com.eric.toolbox.core.MonitorService
import com.eric.toolbox.core.Notifier
import com.eric.toolbox.core.WatchdogWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        AlertLog.load(this)
        WatchdogWorker.schedule(this)
        MonitorService.sync(this)
    }
}

package com.eric.toolbox.core

import android.view.KeyEvent

/** 让页面临时接管实体按键（例如阅读器的音量键翻页）。返回 true 表示已处理，系统不再调音量。 */
object KeyHooks {
    @Volatile var handler: ((KeyEvent) -> Boolean)? = null
}

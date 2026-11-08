package com.eric.toolbox.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eric.toolbox.core.Store
import java.util.Locale

/**
 * 界面语言：跟随系统 / 简体中文 / English。界面文字用 tr("中文", "English")，切换后立即重绘。
 * V2.0 起新页面全部双语；老页面逐步补英文（没补的仍显示中文）。
 */
object I18n {
    var setting by mutableStateOf("system"); private set
    var en by mutableStateOf(false); private set

    fun init(ctx: Context) {
        setting = Store.global(ctx).getString("lang", "system")!!
        en = resolve(setting)
    }

    fun set(ctx: Context, key: String) {
        Store.global(ctx).edit().putString("lang", key).apply()
        setting = key; en = resolve(key)
    }

    private fun resolve(k: String) = when (k) {
        "en" -> true
        "zh" -> false
        else -> !Locale.getDefault().language.equals("zh", true)
    }

    fun label(k: String) = when (k) { "zh" -> "简体中文"; "en" -> "English"; else -> tr("跟随系统", "System") }
}

/** 取当前语言的文字。 */
fun tr(zh: String, en: String): String = if (I18n.en) en else zh

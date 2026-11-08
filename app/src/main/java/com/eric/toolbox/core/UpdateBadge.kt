package com.eric.toolbox.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eric.toolbox.BuildConfig

/**
 * 新版本红点：打开 App / 回到前台时、以及看门狗（15 分钟）里顺带检查，最多每 30 分钟查一次网络。
 * 查到的最新版本号存在 global（update_seen_code），装上新版后自动消失。
 */
object UpdateBadge {
    var latest by mutableStateOf<UpdateInfo?>(null); private set
    private const val MIN_GAP = 30 * 60_000L

    /** 是否显示红点（版本号比本机新）。 */
    fun has(ctx: Context): Boolean {
        val l = latest
        if (l != null) return l.versionCode > BuildConfig.VERSION_CODE
        return Store.global(ctx).getInt("update_seen_code", 0) > BuildConfig.VERSION_CODE
    }

    fun set(ctx: Context, info: UpdateInfo?) {
        latest = info
        Store.global(ctx).edit().putInt("update_seen_code", info?.versionCode ?: 0)
            .putString("update_seen_name", info?.versionName ?: "").apply()
    }

    fun seenName(ctx: Context): String = latest?.versionName ?: Store.global(ctx).getString("update_seen_name", "") ?: ""

    suspend fun refresh(ctx: Context, force: Boolean = false) {
        val p = Store.global(ctx)
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong("update_checked_at", 0) < MIN_GAP) {
            if (latest == null && has(ctx)) tick++ // 用存下来的结果
            return
        }
        p.edit().putLong("update_checked_at", now).apply()
        try { set(ctx, Updater.check()) } catch (_: Exception) { /* 网络不通：保持原状 */ }
    }

    /** 让 Compose 在只读了存储结果时也能刷新。 */
    var tick by mutableStateOf(0); private set
}

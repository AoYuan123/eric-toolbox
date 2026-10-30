package com.eric.toolbox.core

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 百宝箱里的每一个功能都是一个 ToolModule。
 *
 * 新增一个功能 = 新建一个包（如 modules/xxx），实现这个接口，
 * 然后在 [ModuleRegistry.all] 里加一行。底座（主页、后台服务、通知、
 * 存储、更新、备份）全部自动复用，不需要改动其他代码。
 */
interface ToolModule {
    /** 唯一 ID，只用小写字母和下划线，一旦发布不要再改（存储按它分区）。 */
    val id: String
    val name: String
    val category: Category
    val icon: ImageVector

    /** 主页上的卡片内容（标题和图标由底座画，这里只画摘要，如当前价格）。 */
    @Composable
    fun HomeSummary() {}

    /** 点进模块后的完整界面。onBack 返回百宝箱主页。 */
    @Composable
    fun Screen(onBack: () -> Unit)

    /** 需要后台持续运行的模块返回一个任务；纯工具类模块保持 null。 */
    val background: BackgroundTask? get() = null
}

enum class Category(val title: String) {
    MONITOR("监控类"),
    TOOL("工具类"),
}

/** 后台任务：由 MonitorService 统一调度。 */
interface BackgroundTask {
    /** 用户是否开启了这个模块的后台监控。 */
    fun isEnabled(ctx: Context): Boolean

    /** 距离下一次运行的间隔（毫秒），可以根据是否在交易时段动态调整。 */
    fun nextDelayMs(ctx: Context, nowMs: Long): Long

    /** 执行一次。抛出的异常会被底座捕获并记录，不会拖垮其他模块。 */
    suspend fun runOnce(ctx: Context)

    /** 常驻通知里显示的一行状态（可选）。 */
    fun statusLine(ctx: Context): String? = null

    /** 当前是否需要保持 CPU 唤醒以保证准点刷新（如交易时段）。 */
    fun needsPreciseTiming(ctx: Context, nowMs: Long): Boolean = false
}

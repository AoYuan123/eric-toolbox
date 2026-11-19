package com.eric.toolbox.modules.family

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.Category
import com.eric.toolbox.core.ToolModule
import com.eric.toolbox.ui.C

/**
 * 记忆绳：从第一次看到两条杠，到产检、出生、以后每年。
 *
 * 一条绳子，上面挂着照片、视频、录音；长按任意位置补一天。
 * 不做"空夹子"、不做"待办清单"——这两样东西会立刻把回忆变成任务。
 */
object FamilyModule : ToolModule {
    override val id = "family"
    override val name = "记忆绳"
    override val category = Category.TOOL
    override val icon: ImageVector = Icons.Filled.ChildCare

    @Composable
    override fun HomeSummary() {
        val ctx = LocalContext.current
        val info = remember {
            val edd = FamilyStore.edd(ctx)
            val birth = FamilyStore.birth(ctx)
            val count = FamilyStore.load(ctx).size
            FamilyDate.stageLabel(edd, birth, FamilyDate.today()) to count
        }
        val (stage, count) = info
        if (stage.isEmpty()) {
            Text("还没定起点", color = C.Warn, fontSize = 13.sp)
            Text("点进去设一个预产期", color = C.Muted, fontSize = 12.sp)
        } else {
            Text(stage, color = C.Text, fontSize = 13.sp)
            Text(if (count == 0) "绳子还是空的" else "已挂 $count 件", color = C.Muted, fontSize = 12.sp)
        }
    }

    @Composable
    override fun Screen(onBack: () -> Unit) = FamilyScreen(onBack)
}

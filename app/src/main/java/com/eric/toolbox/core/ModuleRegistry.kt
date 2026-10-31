package com.eric.toolbox.core

import android.content.Context
import com.eric.toolbox.modules.datecalc.DateCalcModule
import com.eric.toolbox.modules.gold.GoldModule
import com.eric.toolbox.modules.trials.TrialModule

/**
 * 模块登记表。新增模块只需要在 all 里加一行。
 */
object ModuleRegistry {
    val all: List<ToolModule> = listOf(
        GoldModule,
        TrialModule,
        DateCalcModule,
        // 以后的新模块加在这里
    )

    fun byId(id: String): ToolModule? = all.firstOrNull { it.id == id }

    /** 按用户在“整理主页”里设定的顺序返回，隐藏的模块排除在外。 */
    fun visibleOrdered(ctx: Context): List<ToolModule> {
        val prefs = Store.global(ctx)
        val order = prefs.getString("home_order", "")!!.split(',').filter { it.isNotBlank() }
        val hidden = prefs.getStringSet("home_hidden", emptySet())!!
        val ranked = all.sortedBy { m -> order.indexOf(m.id).let { if (it < 0) Int.MAX_VALUE else it } }
        return ranked.filter { it.id !in hidden }
    }

    fun allOrdered(ctx: Context): List<ToolModule> {
        val order = Store.global(ctx).getString("home_order", "")!!.split(',').filter { it.isNotBlank() }
        return all.sortedBy { m -> order.indexOf(m.id).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    fun saveOrder(ctx: Context, ids: List<String>) {
        Store.global(ctx).edit().putString("home_order", ids.joinToString(",")).apply()
    }

    fun setHidden(ctx: Context, id: String, hidden: Boolean) {
        val p = Store.global(ctx)
        val set = p.getStringSet("home_hidden", emptySet())!!.toMutableSet()
        if (hidden) set.add(id) else set.remove(id)
        p.edit().putStringSet("home_hidden", set).apply()
    }

    fun isHidden(ctx: Context, id: String): Boolean =
        id in Store.global(ctx).getStringSet("home_hidden", emptySet())!!
}

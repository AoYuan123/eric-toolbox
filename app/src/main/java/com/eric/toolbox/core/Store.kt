package com.eric.toolbox.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地存储：每个模块一个独立分区（"mod_<id>"），全局设置在 "global"。
 * 备份 / 恢复会导出所有分区为一段 JSON 文本。
 */
object Store {
    private const val GLOBAL = "global"

    fun global(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(GLOBAL, Context.MODE_PRIVATE)

    fun module(ctx: Context, moduleId: String): SharedPreferences =
        ctx.getSharedPreferences("mod_$moduleId", Context.MODE_PRIVATE)

    private fun allNames(): List<String> = listOf(GLOBAL, AlertLog.PREFS) + ModuleRegistry.all.map { "mod_${it.id}" }

    /** 导出全部设置和记录（不含任何密钥，本 App 也不存密钥）。 */
    fun exportJson(ctx: Context): String {
        val root = JSONObject()
        root.put("format", 1)
        for (name in allNames()) {
            val p = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            val obj = JSONObject()
            for ((k, v) in p.all) {
                val entry = JSONObject()
                when (v) {
                    is String -> { entry.put("t", "s"); entry.put("v", v) }
                    is Int -> { entry.put("t", "i"); entry.put("v", v) }
                    is Long -> { entry.put("t", "l"); entry.put("v", v) }
                    is Float -> { entry.put("t", "f"); entry.put("v", v.toDouble()) }
                    is Boolean -> { entry.put("t", "b"); entry.put("v", v) }
                    is Set<*> -> { entry.put("t", "set"); entry.put("v", JSONArray(v.map { it.toString() })) }
                    else -> continue
                }
                obj.put(k, entry)
            }
            root.put(name, obj)
        }
        return root.toString()
    }

    /** 从备份文本恢复。只接受本 App 认识的分区名，格式不对直接报错、不改动现有数据。 */
    fun importJson(ctx: Context, text: String) {
        val root = JSONObject(text.trim())
        require(root.optInt("format") == 1) { "备份格式不正确" }
        val names = allNames().toSet()
        // 先完整校验一遍，再写入
        val plan = mutableListOf<Pair<String, JSONObject>>()
        for (name in root.keys()) {
            if (name == "format") continue
            require(name in names) { "未知的数据分区：$name" }
            plan += name to root.getJSONObject(name)
        }
        for ((name, obj) in plan) {
            val ed = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for (k in obj.keys()) {
                val e = obj.getJSONObject(k)
                when (e.getString("t")) {
                    "s" -> ed.putString(k, e.getString("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "set" -> {
                        val arr = e.getJSONArray("v")
                        ed.putStringSet(k, (0 until arr.length()).map { arr.getString(it) }.toSet())
                    }
                }
            }
            ed.commit()
        }
    }
}

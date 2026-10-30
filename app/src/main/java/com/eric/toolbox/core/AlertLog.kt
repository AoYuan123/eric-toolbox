package com.eric.toolbox.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AlertEntry(val timeMs: Long, val moduleId: String, val title: String, val body: String)

/** 所有模块共用的提醒记录，最多保留 500 条。 */
object AlertLog {
    const val PREFS = "alert_log"
    private const val KEY = "entries"
    private const val MAX = 500

    private val _flow = MutableStateFlow<List<AlertEntry>>(emptyList())
    val flow: StateFlow<List<AlertEntry>> = _flow

    @Synchronized
    fun load(ctx: Context): List<AlertEntry> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")!!
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            AlertEntry(o.optLong("t"), o.optString("m"), o.optString("title"), o.optString("body"))
        }
        _flow.value = list
        return list
    }

    @Synchronized
    fun add(ctx: Context, entry: AlertEntry) {
        val list = (listOf(entry) + load(ctx)).take(MAX)
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("t", it.timeMs).put("m", it.moduleId).put("title", it.title).put("body", it.body))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
        _flow.value = list
    }

    fun forModule(ctx: Context, moduleId: String): List<AlertEntry> = load(ctx).filter { it.moduleId == moduleId }
}

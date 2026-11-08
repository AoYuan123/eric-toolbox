package com.eric.toolbox.modules.ai

import android.content.Context
import android.content.SharedPreferences
import com.eric.toolbox.core.AlertEntry
import com.eric.toolbox.core.AlertLog
import com.eric.toolbox.core.Store
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** AI 管家的设置、密钥、花费、对话和操作记录。 */
object AiStore {
    fun prefs(ctx: Context): SharedPreferences = Store.module(ctx, "ai")
    /** 密钥单独存，不进备份（Store 只备份 global / mod_* / alert_log）。 */
    private fun secret(ctx: Context) = ctx.getSharedPreferences("ai_secret", Context.MODE_PRIVATE)

    val GROUPS = linkedMapOf(
        "gold" to ("实时金价" to "查价格；改档位、上涨提醒、刷新设置"),
        "trials" to ("临床试验" to "查变化；加关注（先预览）、开关监控"),
        "read" to ("读书" to "看书架、接着听、暂停、语速、生词情况"),
        "translate" to ("翻译" to "翻译文字（用百度翻译额度）"),
        "datecalc" to ("日期计算" to "算间隔、工作日、农历"),
        "global" to ("全局" to "主题、首页排序、检查更新、看记录"),
        "web" to ("联网搜索" to "搜网页、打开网页并带来源（DeepSeek，每次约 5 厘）"),
    )

    data class Settings(
        val provider: String, val model: String, val customUrl: String, val priceIn: Double, val priceOut: Double,
        val cap: Double, val groups: Set<String>, val voiceAutoSend: Boolean, val speakReply: Boolean,
    )

    fun settings(ctx: Context): Settings {
        val p = prefs(ctx)
        if (!p.getBoolean("web_added", false)) { // v2.1 新增「联网搜索」组：老用户已存的开关集合里补上
            val g = p.getStringSet("groups", null)
            p.edit().putBoolean("web_added", true).apply { if (g != null) putStringSet("groups", g + "web") }.apply()
        }
        val prov = p.getString("provider", "deepseek")!!
        return Settings(
            prov, p.getString("model", AiLogic.provider(prov).models.firstOrNull() ?: "")!!, p.getString("custom_url", "")!!,
            p.getFloat("price_in", 0f).toDouble(), p.getFloat("price_out", 0f).toDouble(), p.getFloat("cap", 20f).toDouble(),
            p.getStringSet("groups", GROUPS.keys)!!.toSet(), p.getBoolean("voice_auto", true), p.getBoolean("speak_reply", false),
        )
    }

    fun save(ctx: Context, s: Settings) {
        prefs(ctx).edit().putString("provider", s.provider).putString("model", s.model).putString("custom_url", s.customUrl)
            .putFloat("price_in", s.priceIn.toFloat()).putFloat("price_out", s.priceOut.toFloat()).putFloat("cap", s.cap.toFloat())
            .putStringSet("groups", s.groups).putBoolean("voice_auto", s.voiceAutoSend).putBoolean("speak_reply", s.speakReply).apply()
    }

    fun key(ctx: Context, provider: String): String = secret(ctx).getString("key_$provider", "") ?: ""
    fun setKey(ctx: Context, provider: String, k: String) { secret(ctx).edit().putString("key_$provider", k.trim()).apply() }

    fun url(s: Settings): String = if (s.provider == "custom") s.customUrl.trim() else AiLogic.provider(s.provider).url

    // ---------------- 花费 ----------------
    fun monthCost(ctx: Context): Double {
        val p = prefs(ctx)
        return if (p.getString("cost_month", "") == AiLogic.monthKey(System.currentTimeMillis())) p.getFloat("cost", 0f).toDouble() else 0.0
    }

    fun addCost(ctx: Context, yuan: Double) {
        val m = AiLogic.monthKey(System.currentTimeMillis())
        val p = prefs(ctx)
        val cur = if (p.getString("cost_month", "") == m) p.getFloat("cost", 0f) else 0f
        p.edit().putString("cost_month", m).putFloat("cost", (cur + yuan).toFloat()).apply()
    }

    // ---------------- 对话 ----------------
    private fun dir(ctx: Context) = File(ctx.filesDir, "ai").apply { mkdirs() }

    fun loadChat(ctx: Context): List<AiLogic.Msg> = runCatching {
        val a = JSONArray(File(dir(ctx), "chat.json").readText())
        (0 until a.length()).map { AiLogic.msgFromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun saveChat(ctx: Context, list: List<AiLogic.Msg>) {
        val keep = AiLogic.window(list, 80)
        File(dir(ctx), "chat.json").writeText(JSONArray().apply { keep.forEach { put(AiLogic.msgToJson(it)) } }.toString())
    }

    // ---------------- 操作记录与撤销 ----------------
    data class LogItem(val id: Long, val at: Long, val group: String, val summary: String, val snapshot: JSONObject, val undone: Boolean)

    private fun logFile(ctx: Context) = File(dir(ctx), "log.json")

    @Synchronized
    fun logs(ctx: Context): List<LogItem> = runCatching {
        val a = JSONArray(logFile(ctx).readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            LogItem(o.getLong("id"), o.getLong("at"), o.optString("g"), o.optString("s"), o.optJSONObject("snap") ?: JSONObject(), o.optBoolean("undone"))
        }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun saveLogs(ctx: Context, list: List<LogItem>) {
        logFile(ctx).writeText(JSONArray().apply {
            list.take(100).forEach { put(JSONObject().put("id", it.id).put("at", it.at).put("g", it.group).put("s", it.summary).put("snap", it.snapshot).put("undone", it.undone)) }
        }.toString())
    }

    /** 给分区里指定的键拍快照（撤销用）；keys 为 null 表示整个分区。 */
    fun snapshot(ctx: Context, parts: Map<String, Collection<String>?>): JSONObject {
        val root = JSONObject()
        for ((n, keys) in parts) {
            val o = JSONObject()
            if (keys != null) o.put("__keys", JSONArray(keys.toList()))
            for ((k, v) in ctx.getSharedPreferences(n, Context.MODE_PRIVATE).all) {
                if (keys != null && k !in keys) continue
                when (v) {
                    is String -> o.put(k, JSONObject().put("t", "s").put("v", v))
                    is Int -> o.put(k, JSONObject().put("t", "i").put("v", v))
                    is Long -> o.put(k, JSONObject().put("t", "l").put("v", v))
                    is Float -> o.put(k, JSONObject().put("t", "f").put("v", v.toDouble()))
                    is Boolean -> o.put(k, JSONObject().put("t", "b").put("v", v))
                    is Set<*> -> o.put(k, JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() })))
                }
            }
            root.put(n, o)
        }
        return root
    }

    private fun restore(ctx: Context, snap: JSONObject) {
        for (n in snap.keys()) {
            val o = snap.getJSONObject(n)
            val ed = ctx.getSharedPreferences(n, Context.MODE_PRIVATE).edit()
            val only = o.optJSONArray("__keys")
            if (only == null) ed.clear() else for (i in 0 until only.length()) ed.remove(only.getString(i))
            for (k in o.keys()) {
                if (k == "__keys") continue
                val e = o.getJSONObject(k)
                when (e.getString("t")) {
                    "s" -> ed.putString(k, e.getString("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "set" -> { val a = e.getJSONArray("v"); ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
                }
            }
            ed.commit()
        }
    }

    fun addLog(ctx: Context, group: String, summary: String, snap: JSONObject): Long {
        val id = System.currentTimeMillis()
        saveLogs(ctx, listOf(LogItem(id, id, group, summary, snap, false)) + logs(ctx))
        AlertLog.add(ctx, AlertEntry(id, "ai", "AI 管家 · ${GROUPS[group]?.first ?: group}", summary))
        return id
    }

    /** 撤销一条操作：恢复改动前的快照。返回 null 表示成功，否则是原因。 */
    fun undo(ctx: Context, id: Long, after: () -> Unit): String? {
        val list = logs(ctx)
        val it = list.firstOrNull { l -> l.id == id } ?: return "找不到这条记录"
        if (it.undone) return "已经撤销过了"
        restore(ctx, it.snapshot)
        saveLogs(ctx, list.map { l -> if (l.id == id) l.copy(undone = true) else l })
        AlertLog.add(ctx, AlertEntry(System.currentTimeMillis(), "ai", "AI 管家 · 撤销", it.summary))
        after()
        return null
    }
}

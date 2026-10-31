package com.eric.toolbox.modules.trials

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.BackgroundTask
import com.eric.toolbox.core.Category
import com.eric.toolbox.core.Http
import com.eric.toolbox.core.MonitorService
import com.eric.toolbox.core.Notifier
import com.eric.toolbox.core.RunWindow
import com.eric.toolbox.core.Store
import com.eric.toolbox.core.ToolModule
import com.eric.toolbox.ui.C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 临床试验监控（ClinicalTrials.gov 官方 API v2，公开、免登录）。
 * 每天 9:00、18:00（北京时间）自动检查；与上次记录对比找出变化；
 * 提醒只在提醒时段内推送，时段外发现的变化留到时段开始时再推送。网络失败 15 分钟后自动重试。
 */
object TrialModule : ToolModule {
    override val id = "trials"
    override val name = "临床试验"
    override val category = Category.MONITOR
    override val icon: ImageVector = Icons.Filled.Science

    private val BJ: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")
    private val CHECK_HOURS = listOf(9, 18)
    private const val RETRY_MS = 15 * 60_000L

    private val _version = MutableStateFlow(0)
    /** 数据变化时递增，界面据此刷新。 */
    val version: StateFlow<Int> = _version
    private fun bump() { _version.value++ }

    fun prefs(ctx: Context) = Store.module(ctx, id)

    // ---------------- 关注条件 ----------------

    fun subs(ctx: Context): List<Sub> {
        val arr = runCatching { JSONArray(prefs(ctx).getString("subs", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { runCatching { TrialLogic.subFromJson(arr.getJSONObject(it)) }.getOrNull() }
    }

    private fun saveSubs(ctx: Context, list: List<Sub>) {
        val arr = JSONArray(); list.forEach { arr.put(TrialLogic.subToJson(it)) }
        prefs(ctx).edit().putString("subs", arr.toString()).apply()
        bump()
    }

    fun sub(ctx: Context, id: Long) = subs(ctx).firstOrNull { it.id == id }

    fun upsertSub(ctx: Context, s: Sub) {
        val list = subs(ctx).toMutableList()
        val i = list.indexOfFirst { it.id == s.id }
        if (i >= 0) list[i] = s else list += s
        saveSubs(ctx, list)
        MonitorService.sync(ctx)
    }

    fun deleteSub(ctx: Context, id: Long) {
        saveSubs(ctx, subs(ctx).filter { it.id != id })
        snapFile(ctx, id).delete()
        saveChanges(ctx, changes(ctx).filter { it.subId != id })
        MonitorService.sync(ctx)
    }

    fun exclude(ctx: Context, id: Long, nct: String) {
        sub(ctx, id)?.let { upsertSub(ctx, it.copy(excluded = it.excluded + nct)) }
    }

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("enabled", true)
    fun setEnabled(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("enabled", on).apply(); bump(); MonitorService.sync(ctx) }

    // ---------------- 快照（每组关注一份） ----------------

    private fun snapFile(ctx: Context, id: Long) = File(ctx.filesDir, "trials_$id.json")

    fun snapshot(ctx: Context, id: Long): Map<String, Trial>? = runCatching {
        val f = snapFile(ctx, id)
        if (!f.isFile) return null
        val arr = JSONArray(f.readText())
        (0 until arr.length()).map { TrialLogic.trialFromJson(arr.getJSONObject(it)) }.associateBy { it.nct }
    }.getOrNull()

    private fun saveSnapshot(ctx: Context, id: Long, list: List<Trial>) {
        val arr = JSONArray(); list.forEach { arr.put(TrialLogic.trialToJson(it)) }
        runCatching { snapFile(ctx, id).writeText(arr.toString()) }
    }

    // ---------------- 变化记录 ----------------

    fun changes(ctx: Context): List<Change> {
        val arr = runCatching { JSONArray(prefs(ctx).getString("changes", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map { TrialLogic.changeFromJson(arr.getJSONObject(it)) }
    }

    private fun saveChanges(ctx: Context, list: List<Change>) {
        val arr = JSONArray(); list.sortedByDescending { it.atMs }.take(300).forEach { arr.put(TrialLogic.changeToJson(it)) }
        prefs(ctx).edit().putString("changes", arr.toString()).apply()
        bump()
    }

    // ---------------- 网络 ----------------

    suspend fun search(s: Sub): SearchResult = TrialLogic.parseSearch(Http.get(TrialLogic.searchUrl(s), timeoutMs = 15_000))

    data class Detail(
        val trial: Trial, val officialTitle: String, val studyType: String, val start: String, val conditions: List<String>,
        val interventions: List<String>, val primaryOutcomes: List<String>, val countries: Map<String, Int>,
    )

    suspend fun detail(nct: String): Detail {
        val fields = "NCTId,BriefTitle,OfficialTitle,OverallStatus,Phase,StudyType,LeadSponsorName,EnrollmentCount,StartDate," +
            "PrimaryCompletionDate,PrimaryOutcomeMeasure,LocationCountry,HasResults,LastUpdatePostDate,StudyFirstPostDate,Condition,InterventionName"
        val body = Http.get("${TrialLogic.BASE}/$nct?fields=$fields", timeoutMs = 15_000)
        val o = JSONObject(body)
        val t = TrialLogic.parseStudy(o) ?: throw java.io.IOException("没有找到 $nct")
        val ps = o.getJSONObject("protocolSection")
        fun arr(mod: String, key: String, field: String?): List<String> {
            val a = ps.optJSONObject(mod)?.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { if (field == null) a.optString(it) else a.optJSONObject(it)?.optString(field).orEmpty() }.filter { it.isNotBlank() }
        }
        val countries = arr("contactsLocationsModule", "locations", "country").groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.associate { it.key to it.value }
        return Detail(
            t, ps.optJSONObject("identificationModule")?.optString("officialTitle").orEmpty(),
            ps.optJSONObject("designModule")?.optString("studyType").orEmpty(),
            ps.optJSONObject("statusModule")?.optJSONObject("startDateStruct")?.optString("date").orEmpty(),
            arr("conditionsModule", "conditions", null), arr("armsInterventionsModule", "interventions", "name"),
            arr("outcomesModule", "primaryOutcomes", "measure"), countries,
        )
    }

    // ---------------- 检查与提醒 ----------------

    private val lock = Mutex()

    private fun dayStr(ms: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = BJ }.format(Date(ms))

    /** 添加关注时：保存首次检索结果作为基准，之后只提醒与它相比的变化。 */
    fun saveBaseline(ctx: Context, s: Sub, r: SearchResult) {
        saveSnapshot(ctx, s.id, r.trials)
        upsertSub(ctx, s.copy(total = r.total, lastCheck = System.currentTimeMillis(), lastError = null))
    }

    /** 检查所有关注；返回失败的组数。force=手动刷新。 */
    suspend fun checkAll(ctx: Context, force: Boolean = false): Int = lock.withLock {
        val now = System.currentTimeMillis()
        var failed = 0
        val found = mutableListOf<Pair<Sub, List<Change>>>()
        for (s in subs(ctx)) {
            try {
                val r = search(s)
                val old = snapshot(ctx, s.id)
                val ch = TrialLogic.diff(s.id, old, r.trials, s.excluded, dayStr(if (s.lastCheck > 0) s.lastCheck else now), now)
                saveSnapshot(ctx, s.id, r.trials)
                // 重新读一次，避免覆盖检查期间用户的修改
                sub(ctx, s.id)?.let { cur -> upsertSubQuiet(ctx, cur.copy(total = r.total, lastCheck = now, lastError = null)) }
                if (ch.isNotEmpty()) {
                    saveChanges(ctx, ch + changes(ctx))
                    found += s to TrialLogic.notifiable(ch, s.events)
                }
            } catch (e: Exception) {
                failed++
                sub(ctx, s.id)?.let { cur -> upsertSubQuiet(ctx, cur.copy(lastError = e.message ?: "网络错误")) }
            }
        }
        val p = prefs(ctx).edit().putLong("last_run", now)
        p.putLong("next_check", if (failed > 0) now + RETRY_MS else nextSlot(now))
        p.apply()
        for ((s, ch) in found) if (ch.isNotEmpty()) queueOrNotify(ctx, s, ch)
        bump()
        failed
    }

    private fun upsertSubQuiet(ctx: Context, s: Sub) {
        val list = subs(ctx).toMutableList()
        val i = list.indexOfFirst { it.id == s.id }
        if (i >= 0) { list[i] = s; saveSubs(ctx, list) }
    }

    private fun summary(s: Sub, ch: List<Change>): Pair<String, String> {
        val title = "临床试验：${TrialLogic.title(s)} 有 ${ch.size} 项变化"
        val body = ch.take(4).joinToString("\n") { "${it.nct} ${it.detail}" } + if (ch.size > 4) "\n…共 ${ch.size} 项，打开 App 查看" else ""
        return title to body
    }

    /** 在提醒时段内直接推送，否则先存起来，等时段开始再推送。 */
    private fun queueOrNotify(ctx: Context, s: Sub, ch: List<Change>) {
        val (title, body) = summary(s, ch)
        if (RunWindow.isOpen(ctx)) {
            Notifier.alert(ctx, id, title, body)
        } else {
            val arr = runCatching { JSONArray(prefs(ctx).getString("pending", "[]")) }.getOrElse { JSONArray() }
            arr.put(JSONObject().put("t", title).put("b", body))
            prefs(ctx).edit().putString("pending", arr.toString()).apply()
        }
    }

    private fun flushPending(ctx: Context) {
        if (!RunWindow.isOpen(ctx)) return
        val arr = runCatching { JSONArray(prefs(ctx).getString("pending", "[]")) }.getOrElse { JSONArray() }
        if (arr.length() == 0) return
        prefs(ctx).edit().putString("pending", "[]").apply()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            Notifier.alert(ctx, id, o.optString("t"), o.optString("b") + "\n（提醒时段外发现，现在补发）")
        }
    }

    /** 下一个检查时刻（每天 9:00、18:00 北京时间）。 */
    fun nextSlot(nowMs: Long): Long {
        val c = Calendar.getInstance(BJ)
        val candidates = (0..1).flatMap { addDay ->
            CHECK_HOURS.map { h ->
                c.timeInMillis = nowMs
                c.add(Calendar.DAY_OF_MONTH, addDay)
                c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
                c.timeInMillis
            }
        }
        return candidates.filter { it > nowMs }.min()
    }

    fun lastRun(ctx: Context) = prefs(ctx).getLong("last_run", 0L)
    fun nextCheck(ctx: Context) = prefs(ctx).getLong("next_check", 0L)

    override val background: BackgroundTask = object : BackgroundTask {
        override fun isEnabled(ctx: Context) = enabled(ctx) && subs(ctx).isNotEmpty()

        override suspend fun runOnce(ctx: Context) {
            flushPending(ctx)
            val now = System.currentTimeMillis()
            var next = nextCheck(ctx)
            if (next == 0L) {
                next = nextSlot(now)
                prefs(ctx).edit().putLong("next_check", next).apply()
            }
            // 错过了检查时刻（如手机关机）也会在恢复后立即补查
            if (now >= next) checkAll(ctx)
        }

        override fun nextDelayMs(ctx: Context, nowMs: Long): Long {
            val next = nextCheck(ctx).takeIf { it > 0 } ?: nextSlot(nowMs)
            return (next - nowMs + 2_000).coerceIn(60_000L, 30 * 60_000L)
        }

        override fun statusLine(ctx: Context): String = "临床试验 关注 ${subs(ctx).size} 组"
    }

    @Composable
    override fun HomeSummary() {
        val v by version.collectAsState()
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val list = androidx.compose.runtime.remember(v) { subs(ctx) }
        val latest = androidx.compose.runtime.remember(v) { changes(ctx).firstOrNull() }
        Text(if (list.isEmpty()) "还没有关注" else "关注 ${list.size} 组", color = C.Muted, fontSize = 12.sp)
        Text(
            latest?.let { "最近变化 " + SimpleDateFormat("MM-dd", Locale.CHINA).apply { timeZone = BJ }.format(Date(it.atMs)) } ?: "暂无变化记录",
            color = if (latest != null) C.Gold else C.Muted, fontSize = 12.sp,
        )
    }

    @Composable
    override fun Screen(onBack: () -> Unit) = TrialRoot(onBack)
}

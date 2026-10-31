package com.eric.toolbox.modules.trials

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** 一项试验的关键字段（来自 ClinicalTrials.gov API v2）。 */
data class Trial(
    val nct: String,
    val title: String,
    val status: String,
    val phases: List<String>,
    val sponsor: String,
    val lastUpdate: String,       // yyyy-MM-dd
    val firstPost: String,        // yyyy-MM-dd
    val hasResults: Boolean,
    val primaryCompletion: String,
    val enrollment: Int?,
)

/** 一组关注条件。 */
data class Sub(
    val id: Long,
    val type: String,             // intr 药物/干预、cond 疾病、spons 申办方、nct 编号
    val keyword: String,
    val cond: String = "",
    val country: String = "",
    val exact: Boolean = true,    // 关键词按完整短语匹配（加引号），减少不相关结果
    val phases: Set<String> = emptySet(),    // "1".."4"
    val statuses: Set<String> = emptySet(),  // RECRUITING 等
    val events: Set<String> = TrialLogic.DEFAULT_EVENTS,
    val excluded: Set<String> = emptySet(),
    val total: Int = 0,
    val lastCheck: Long = 0,
    val lastError: String? = null,
)

/** 一条变化记录。 */
data class Change(val subId: Long, val nct: String, val title: String, val kind: String, val detail: String, val atMs: Long)

data class SearchResult(val total: Int, val trials: List<Trial>)

object TrialLogic {
    const val BASE = "https://clinicaltrials.gov/api/v2/studies"
    const val PAGE = 200
    private const val FIELDS =
        "NCTId,BriefTitle,OverallStatus,Phase,LeadSponsorName,LastUpdatePostDate,StudyFirstPostDate,HasResults,PrimaryCompletionDate,EnrollmentCount"

    val EVENT_NAMES = linkedMapOf(
        "new" to "新登记",
        "status" to "状态变化（如开始招募、终止）",
        "results" to "结果发布",
        "phase" to "阶段变化",
        "dates" to "关键日期调整",
    )
    val DEFAULT_EVENTS = setOf("new", "status", "results", "phase")
    val TYPE_NAMES = linkedMapOf("intr" to "药物 / 干预", "cond" to "疾病 / 适应症", "spons" to "申办方", "nct" to "NCT 编号")
    val STATUS_FILTERS = linkedMapOf(
        "RECRUITING" to "招募中", "NOT_YET_RECRUITING" to "尚未招募", "ACTIVE_NOT_RECRUITING" to "进行中", "COMPLETED" to "已完成",
    )

    private val STATUS_CN = mapOf(
        "RECRUITING" to "招募中", "NOT_YET_RECRUITING" to "尚未招募", "ACTIVE_NOT_RECRUITING" to "进行中·不招募",
        "ENROLLING_BY_INVITATION" to "邀请入组", "COMPLETED" to "已完成", "SUSPENDED" to "暂停",
        "TERMINATED" to "已终止", "WITHDRAWN" to "已撤回", "UNKNOWN" to "状态未知",
        "AVAILABLE" to "可申请", "NO_LONGER_AVAILABLE" to "不再提供", "TEMPORARILY_NOT_AVAILABLE" to "暂不提供",
        "APPROVED_FOR_MARKETING" to "已批准上市", "WITHHELD" to "未公开",
    )

    fun statusCn(s: String) = STATUS_CN[s] ?: s.ifBlank { "--" }

    fun phaseCn(p: List<String>): String {
        if (p.isEmpty() || p == listOf("NA")) return "阶段不适用"
        val roman = p.map {
            when (it) {
                "EARLY_PHASE1" -> "早期 I"; "PHASE1" -> "I"; "PHASE2" -> "II"; "PHASE3" -> "III"; "PHASE4" -> "IV"; else -> it
            }
        }
        return roman.joinToString("/") + " 期"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** NCT 编号输入：支持空格、逗号、换行分隔，自动补 NCT 前缀并去重。 */
    fun parseNctList(text: String): List<String> =
        Regex("""(?i)(?:NCT)?(\d{8})""").findAll(text).map { "NCT" + it.groupValues[1] }.distinct().toList()

    fun searchUrl(s: Sub): String {
        val q = mutableListOf<String>()
        fun kw(v: String) = if (s.exact && v.isNotBlank() && !v.contains('"')) "\"${v.trim()}\"" else v.trim()
        when (s.type) {
            "intr" -> q += "query.intr=" + enc(kw(s.keyword))
            "cond" -> q += "query.cond=" + enc(kw(s.keyword))
            "spons" -> q += "query.spons=" + enc(kw(s.keyword))
            "nct" -> q += "filter.ids=" + enc(parseNctList(s.keyword).joinToString(","))
        }
        if (s.type != "cond" && s.type != "nct" && s.cond.isNotBlank()) q += "query.cond=" + enc(s.cond.trim())
        if (s.type != "nct" && s.country.isNotBlank()) q += "query.locn=" + enc(s.country.trim())
        if (s.statuses.isNotEmpty()) q += "filter.overallStatus=" + enc(s.statuses.sorted().joinToString(","))
        if (s.phases.isNotEmpty()) q += "aggFilters=" + enc("phase:" + s.phases.sorted().joinToString(" "))
        q += "fields=$FIELDS"
        q += "sort=" + enc("LastUpdatePostDate:desc")
        q += "countTotal=true"
        q += "pageSize=$PAGE"
        return BASE + "?" + q.joinToString("&")
    }

    fun parseSearch(body: String): SearchResult {
        val o = JSONObject(body)
        val arr = o.optJSONArray("studies") ?: JSONArray()
        val list = (0 until arr.length()).mapNotNull { parseStudy(arr.optJSONObject(it)) }
        return SearchResult(o.optInt("totalCount", list.size), list)
    }

    fun parseStudy(st: JSONObject?): Trial? {
        val ps = st?.optJSONObject("protocolSection") ?: return null
        val id = ps.optJSONObject("identificationModule") ?: return null
        val nct = id.optString("nctId").ifBlank { return null }
        val sm = ps.optJSONObject("statusModule")
        val dm = ps.optJSONObject("designModule")
        fun date(k: String) = sm?.optJSONObject(k)?.optString("date").orEmpty()
        val phArr = dm?.optJSONArray("phases")
        return Trial(
            nct = nct,
            title = id.optString("briefTitle"),
            status = sm?.optString("overallStatus").orEmpty(),
            phases = (0 until (phArr?.length() ?: 0)).map { phArr!!.optString(it) },
            sponsor = ps.optJSONObject("sponsorCollaboratorsModule")?.optJSONObject("leadSponsor")?.optString("name").orEmpty(),
            lastUpdate = date("lastUpdatePostDateStruct"),
            firstPost = date("studyFirstPostDateStruct"),
            hasResults = st.optBoolean("hasResults", false),
            primaryCompletion = date("primaryCompletionDateStruct"),
            enrollment = dm?.optJSONObject("enrollmentInfo")?.let { if (it.has("count")) it.optInt("count") else null },
        )
    }

    /**
     * 对比上次与本次的检索结果，找出变化。
     * - 上次没有快照（刚添加关注）：只记录现状，不产生任何变化。
     * - 新出现的试验：首次公布日期在上次检查前 7 天内算“新登记”，否则算“新纳入”（例如记录更新后才匹配上条件）。
     * - 只对比两次都出现的试验的状态、阶段、结果、主要完成日期；其余更新记为“其他更新”。
     * @param lastCheckDate 上次检查日期 yyyy-MM-dd
     */
    fun diff(subId: Long, old: Map<String, Trial>?, now: List<Trial>, excluded: Set<String>, lastCheckDate: String, nowMs: Long): List<Change> {
        if (old == null) return emptyList()
        val out = mutableListOf<Change>()
        val cutoff = minusDays(lastCheckDate, 7)
        for (t in now) {
            if (t.nct in excluded) continue
            val o = old[t.nct]
            if (o == null) {
                val isNew = t.firstPost.isNotBlank() && t.firstPost >= cutoff
                out += Change(subId, t.nct, t.title, "new", if (isNew) "新登记（首次公布 ${t.firstPost}）" else "新纳入关注范围（首次公布 ${t.firstPost.ifBlank { "--" }}）", nowMs)
                continue
            }
            var specific = false
            if (o.status != t.status) {
                out += Change(subId, t.nct, t.title, "status", "状态：${statusCn(o.status)} → ${statusCn(t.status)}", nowMs); specific = true
            }
            if (!o.hasResults && t.hasResults) {
                out += Change(subId, t.nct, t.title, "results", "已发布结果", nowMs); specific = true
            }
            if (o.phases != t.phases) {
                out += Change(subId, t.nct, t.title, "phase", "阶段：${phaseCn(o.phases)} → ${phaseCn(t.phases)}", nowMs); specific = true
            }
            if (o.primaryCompletion != t.primaryCompletion && o.primaryCompletion.isNotBlank()) {
                out += Change(subId, t.nct, t.title, "dates", "主要完成日期：${o.primaryCompletion} → ${t.primaryCompletion.ifBlank { "--" }}", nowMs); specific = true
            }
            if (!specific && o.lastUpdate != t.lastUpdate) {
                out += Change(subId, t.nct, t.title, "other", "登记记录有其他更新（官方更新日期 ${t.lastUpdate}）", nowMs)
            }
        }
        return out
    }

    /** yyyy-MM-dd 减若干天（不依赖时区）。 */
    fun minusDays(date: String, days: Long): String =
        runCatching { java.time.LocalDate.parse(date).minusDays(days).toString() }.getOrDefault("0000-00-00")

    /** 需要推送提醒的变化：用户勾选的类型（“其他更新”只记录不推送）。 */
    fun notifiable(changes: List<Change>, events: Set<String>) = changes.filter { it.kind in events }

    // ---------- 存储 ----------

    fun trialToJson(t: Trial): JSONObject = JSONObject()
        .put("n", t.nct).put("t", t.title).put("s", t.status).put("p", JSONArray(t.phases)).put("sp", t.sponsor)
        .put("lu", t.lastUpdate).put("fp", t.firstPost).put("r", t.hasResults).put("pc", t.primaryCompletion)
        .put("e", t.enrollment ?: JSONObject.NULL)

    fun trialFromJson(o: JSONObject): Trial {
        val p = o.optJSONArray("p") ?: JSONArray()
        return Trial(
            o.getString("n"), o.optString("t"), o.optString("s"), (0 until p.length()).map { p.optString(it) }, o.optString("sp"),
            o.optString("lu"), o.optString("fp"), o.optBoolean("r"), o.optString("pc"), if (o.isNull("e")) null else o.optInt("e"),
        )
    }

    fun subToJson(s: Sub): JSONObject = JSONObject()
        .put("id", s.id).put("type", s.type).put("kw", s.keyword).put("cond", s.cond).put("country", s.country).put("exact", s.exact)
        .put("phases", JSONArray(s.phases.toList())).put("statuses", JSONArray(s.statuses.toList()))
        .put("events", JSONArray(s.events.toList())).put("excluded", JSONArray(s.excluded.toList()))
        .put("total", s.total).put("lastCheck", s.lastCheck).put("err", s.lastError ?: JSONObject.NULL)

    private fun strSet(a: JSONArray?): Set<String> = (0 until (a?.length() ?: 0)).map { a!!.optString(it) }.toSet()

    fun subFromJson(o: JSONObject): Sub = Sub(
        id = o.getLong("id"), type = o.optString("type", "intr"), keyword = o.optString("kw"), cond = o.optString("cond"),
        country = o.optString("country"), exact = o.optBoolean("exact", true),
        phases = strSet(o.optJSONArray("phases")), statuses = strSet(o.optJSONArray("statuses")),
        events = if (o.has("events")) strSet(o.optJSONArray("events")) else DEFAULT_EVENTS,
        excluded = strSet(o.optJSONArray("excluded")), total = o.optInt("total"), lastCheck = o.optLong("lastCheck"),
        lastError = if (o.isNull("err")) null else o.optString("err"),
    )

    fun changeToJson(c: Change): JSONObject = JSONObject()
        .put("sub", c.subId).put("n", c.nct).put("t", c.title).put("k", c.kind).put("d", c.detail).put("at", c.atMs)

    fun changeFromJson(o: JSONObject) = Change(o.optLong("sub"), o.optString("n"), o.optString("t"), o.optString("k"), o.optString("d"), o.optLong("at"))

    /** 关注条件的一行描述。 */
    fun describe(s: Sub): String = buildString {
        append(TYPE_NAMES[s.type] ?: s.type)
        if (s.cond.isNotBlank() && s.type != "cond" && s.type != "nct") append(" + 疾病 ${s.cond}")
        append(" · ").append(s.country.ifBlank { "全部地区" })
        if (s.phases.isNotEmpty()) append(" · ").append(s.phases.sorted().joinToString("/") { roman(it) }).append(" 期")
        if (s.statuses.isNotEmpty()) append(" · ").append(s.statuses.mapNotNull { STATUS_FILTERS[it] }.joinToString("/"))
    }

    private fun roman(p: String) = when (p) { "1" -> "I"; "2" -> "II"; "3" -> "III"; "4" -> "IV"; else -> p }

    /** 两组条件的检索条件是否相同（不看提醒类型、排除项和检查状态）。 */
    fun sameQuery(a: Sub, b: Sub) = a.type == b.type && a.keyword.trim() == b.keyword.trim() && a.cond.trim() == b.cond.trim() &&
        a.country.trim() == b.country.trim() && a.exact == b.exact && a.phases == b.phases && a.statuses == b.statuses

    /** 关注条件的标题。 */
    fun title(s: Sub): String = when (s.type) {
        "nct" -> parseNctList(s.keyword).let { if (it.size == 1) it[0] else "${it.size} 个试验" }
        else -> s.keyword
    }
}

package com.eric.toolbox.modules.english.law

import android.content.Context
import com.eric.toolbox.core.Store
import org.json.JSONArray
import org.json.JSONObject

/**
 * 专业法律英语的内置数据（assets/law/ 下的 json，由 tools/law/build.py 从官方原文生成并逐字核对）。
 * 每段文字都是 en/zh 一对：中国法条 zh 是原文、en 是全国人大官方英译；美国法条 en 是原文、zh 是 Claude 学习译文。
 */
object LawData {
    data class Para(val en: String, val zh: String, val lvl: Int = 0, val num: String = "")
    data class Unit(
        val id: String, val label: String, val labelEn: String, val title: String, val group: String, val groupEn: String,
        val paras: List<Para>, val explain: List<Para>, val key: Boolean, val excerpt: Boolean, val zhOfficialOnly: Boolean,
    )
    data class Doc(
        val id: String, val title: String, val titleEn: String, val version: String, val versionEn: String, val orig: String,
        val enLabel: String, val zhLabel: String, val note: String, val sources: List<Pair<String, String>>, val checked: String, val units: List<Unit>,
    )
    data class Case(
        val id: String, val name: String, val nameZh: String, val court: String, val courtZh: String, val date: String, val cite: String,
        val url: String, val tags: List<String>, val statutes: List<String>, val node: String, val orig: String,
        val facts: List<Para>, val question: Para, val options: List<Para>, val answer: Int, val holding: List<Para>, val quotes: List<Para>, val explain: List<Para>,
    )
    data class Deadline(val en: String, val zh: String, val ref: String)
    data class Node(
        val id: String, val kind: String, val name: String, val nameZh: String, val desc: List<Para>, val deadlines: List<Deadline>,
        val next: List<String>, val sites: List<Pair<String, String>>, val cases: List<String>,
    )
    data class Juris(val id: String, val name: String, val nameZh: String, val nodes: List<Node>)
    data class Side(val ref: String, val en: String, val zh: String)
    data class Compare(val topic: String, val topicZh: String, val us: Side, val cn: Side, val diff: List<Para>)
    data class Term(val key: String, val en: String, val zh: String, val ref: String, val refLabel: String, val noteZh: String, val noteEn: String, val exEn: String, val exZh: String)
    data class Meta(val checked: String, val usCurrency: String, val cnVersion: String)

    private var cache: Bundle? = null
    class Bundle(val cn: Doc, val us: Doc, val cases: List<Case>, val map: List<Juris>, val compare: List<Compare>, val terms: List<Term>, val meta: Meta) {
        val units: Map<String, Unit> = (cn.units + us.units).associateBy { it.id }
        fun docOf(unitId: String): Doc = if (unitId.startsWith("cn")) cn else us
    }

    @Synchronized
    fun get(ctx: Context): Bundle = cache ?: load(ctx.applicationContext).also { cache = it }

    private fun read(ctx: Context, name: String) = ctx.assets.open("law/$name").bufferedReader().use { it.readText() }
    private fun JSONArray.objs() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strs() = (0 until length()).map { getString(it) }
    private fun para(o: JSONObject) = Para(o.optString("en"), o.optString("zh"), o.optInt("lvl"), o.optString("num"))
    private fun paras(a: JSONArray?) = a?.objs()?.map(::para) ?: emptyList()

    private fun doc(o: JSONObject) = Doc(
        o.getString("id"), o.getString("title"), o.getString("titleEn"), o.getString("version"), o.getString("versionEn"), o.getString("orig"),
        o.getString("enLabel"), o.getString("zhLabel"), o.getString("note"),
        o.getJSONArray("sources").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } } },
        o.getString("checked"),
        o.getJSONArray("units").objs().map { u ->
            Unit(u.getString("id"), u.getString("label"), u.getString("labelEn"), u.optString("title"), u.getString("group"), u.getString("groupEn"),
                paras(u.getJSONArray("paras")), paras(u.optJSONArray("explain")), u.optBoolean("key"), u.optBoolean("excerpt"), u.optBoolean("zhOfficialOnly"))
        },
    )

    private fun load(ctx: Context): Bundle {
        val cases = JSONArray(read(ctx, "cases.json")).objs().map { c ->
            Case(c.getString("id"), c.getString("name"), c.getString("nameZh"), c.getString("court"), c.getString("courtZh"), c.getString("date"), c.getString("cite"),
                c.getString("url"), c.getJSONArray("tags").strs(), c.getJSONArray("statutes").strs(), c.getString("node"), c.getString("orig"),
                paras(c.getJSONArray("facts")), para(c.getJSONObject("question")), paras(c.getJSONArray("options")), c.getInt("answer"),
                paras(c.getJSONArray("holding")), paras(c.getJSONArray("quotes")), paras(c.getJSONArray("explain")))
        }
        val map = JSONArray(read(ctx, "map.json")).objs().map { j ->
            Juris(j.getString("id"), j.getString("name"), j.getString("nameZh"), j.getJSONArray("nodes").objs().map { n ->
                Node(n.getString("id"), n.getString("kind"), n.getString("name"), n.getString("nameZh"), paras(n.getJSONArray("desc")),
                    n.getJSONArray("deadlines").objs().map { Deadline(it.getString("en"), it.getString("zh"), it.getString("ref")) },
                    n.getJSONArray("next").strs(), n.getJSONArray("sites").objs().map { it.getString("name") to it.getString("url") }, n.getJSONArray("cases").strs())
            })
        }
        val compare = JSONArray(read(ctx, "compare.json")).objs().map { c ->
            fun side(o: JSONObject) = Side(o.getString("ref"), o.getString("en"), o.getString("zh"))
            Compare(c.getString("topic"), c.getString("topicZh"), side(c.getJSONObject("us")), side(c.getJSONObject("cn")), paras(c.getJSONArray("diff")))
        }
        val terms = JSONArray(read(ctx, "glossary.json")).objs().map { t ->
            Term(t.getString("key"), t.getString("en"), t.getString("zh"), t.getString("ref"), t.getString("refLabel"), t.getString("noteZh"), t.getString("noteEn"), t.getString("exEn"), t.getString("exZh"))
        }
        val m = JSONObject(read(ctx, "meta.json"))
        return Bundle(doc(JSONObject(read(ctx, "law_cn.json"))), doc(JSONObject(read(ctx, "law_us.json"))), cases, map, compare, terms,
            Meta(m.getString("checked"), m.getString("usCurrency"), m.getString("cnVersion")))
    }

    // ---------------- 学习记录（存 mod_english，进备份） ----------------
    private fun prefs(ctx: Context) = Store.module(ctx, "english")
    fun isRead(ctx: Context, id: String) = id in (prefs(ctx).getStringSet("law_read", emptySet()) ?: emptySet())
    fun markRead(ctx: Context, id: String) {
        val s = (prefs(ctx).getStringSet("law_read", emptySet()) ?: emptySet()).toMutableSet()
        if (s.add(id)) prefs(ctx).edit().putStringSet("law_read", s).apply()
    }
    fun readCount(ctx: Context) = prefs(ctx).getStringSet("law_read", emptySet())?.size ?: 0
    /** 判例预测题：记录第一次的回答（-1 = 没做过）。 */
    fun caseAnswer(ctx: Context, id: String) = prefs(ctx).getInt("law_case_$id", -1)
    fun setCaseAnswer(ctx: Context, id: String, a: Int) { if (caseAnswer(ctx, id) < 0) prefs(ctx).edit().putInt("law_case_$id", a).apply() }

    /** 今日一条：在有延伸解读的重点条文里按天轮换，中美交替。 */
    fun todayUnit(b: Bundle, dayNo: Int): Unit {
        val cn = b.cn.units.filter { it.key }; val us = b.us.units.filter { it.key }
        val list = (0 until maxOf(cn.size, us.size)).flatMap { listOfNotNull(cn.getOrNull(it), us.getOrNull(it)) }
        return list[Math.floorMod(dayNo, list.size)]
    }
}

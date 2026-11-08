package com.eric.toolbox.modules.ai

import android.content.Context
import com.eric.toolbox.BuildConfig
import com.eric.toolbox.core.AlertLog
import com.eric.toolbox.core.ModuleRegistry
import com.eric.toolbox.core.MonitorService
import com.eric.toolbox.core.Store
import com.eric.toolbox.core.UpdateBadge
import com.eric.toolbox.core.Updater
import com.eric.toolbox.modules.datecalc.DateLogic
import com.eric.toolbox.modules.datecalc.Lunar
import com.eric.toolbox.modules.datecalc.Workdays
import com.eric.toolbox.modules.gold.Basis
import com.eric.toolbox.modules.gold.GoldModule
import com.eric.toolbox.modules.read.Library
import com.eric.toolbox.modules.read.Listen
import com.eric.toolbox.modules.read.Notes
import com.eric.toolbox.modules.trials.Sub
import com.eric.toolbox.modules.trials.TrialLogic
import com.eric.toolbox.modules.trials.TrialModule
import com.eric.toolbox.modules.trials.Translator
import com.eric.toolbox.ui.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * AI 能调用的百宝箱功能。只读的直接执行；会改东西的先生成 Plan（改动清单），用户点「确认执行」才 apply。
 * 只开放这里列出的操作，不碰任何密钥，也不执行任意代码。
 */
object AiTools {
    class Plan(
        val group: String,
        val summary: String,
        val changes: List<Pair<String, String>>,
        val parts: Map<String, Collection<String>?>,
        val apply: suspend () -> String,
    )

    class Tool(
        val name: String, val group: String, val title: String, val desc: String, val params: JSONObject,
        val exec: (suspend (Context, JSONObject) -> String)? = null,
        val plan: (suspend (Context, JSONObject) -> Plan)? = null,
    ) { val write get() = plan != null }

    private val BJ = TimeZone.getTimeZone("Asia/Shanghai")
    private fun t(ms: Long?) = if (ms == null || ms <= 0) "未知" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).apply { timeZone = BJ }.format(Date(ms))

    // ------- JSON Schema 小工具 -------
    private fun obj(vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()) = JSONObject().put("type", "object")
        .put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } }).put("required", JSONArray(required))
    private fun str(d: String, enum: List<String>? = null) = JSONObject().put("type", "string").put("description", d).apply { if (enum != null) put("enum", JSONArray(enum)) }
    private fun num(d: String) = JSONObject().put("type", "number").put("description", d)
    private fun int(d: String) = JSONObject().put("type", "integer").put("description", d)
    private fun bool(d: String) = JSONObject().put("type", "boolean").put("description", d)
    private fun strArr(d: String) = JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("description", d)

    private fun JSONObject.optStrOrNull(k: String) = if (has(k) && !isNull(k)) optString(k).trim().ifEmpty { null } else null
    private fun JSONObject.optNumOrNull(k: String) = if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null
    private fun JSONObject.optBoolOrNull(k: String) = if (has(k) && !isNull(k)) optBoolean(k) else null
    private fun JSONObject.strList(k: String): List<String> = optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() } } ?: emptyList()

    private fun fmt(v: Double) = GoldModule.fmtBase(v)

    val all: List<Tool> = listOf(
        // ================= 实时金价 =================
        Tool("get_gold_price", "gold", "查询 实时金价", "获取最新金价：大盘上海金 Au99.99、黄金 T+D、沪金期货、工行积存金、工行如意金积存（元/克），以及当前提醒档位。", obj(),
            exec = { ctx, _ ->
                val cur = GoldModule.snapshot.value
                if (cur == null || System.currentTimeMillis() - cur.fetchedAtMs > 120_000) GoldModule.refresh(ctx)
                val s = GoldModule.snapshot.value ?: return@Tool "取价失败：${GoldModule.status.value.error ?: "网络不通"}"
                val c = GoldModule.config(ctx)
                JSONObject().apply {
                    s.market?.let { put("大盘_上海金Au9999", JSONObject().put("价格", it.price).put("时间", t(it.timeMs)).put("来源", it.source).put("昨收", it.prevClose).put("今开", it.open).put("最高", it.high).put("最低", it.low)) }
                    s.td?.let { put("黄金T+D", JSONObject().put("价格", it.price).put("时间", t(it.timeMs))) }
                    s.futures?.let { put("沪金期货主连", JSONObject().put("价格", it.price).put("时间", t(it.timeMs))) }
                    s.bank?.let { b -> put("工行", JSONObject().put("积存金", b.jcj).put("如意金积存价", b.ryBuy).put("如意金赎回价", b.rySell).put("时间", t(b.timeMs))) }
                    put("多源一致", s.marketVerified()); put("数据新鲜", s.marketFresh())
                    put("提醒依据", c.basis.title); put("下一个提醒档位", GoldModule.lastAlerted(ctx) - c.step)
                    put("取数时间", t(s.fetchedAtMs))
                    if (s.errors.isNotEmpty()) put("部分来源出错", s.errors.joinToString("；"))
                }.toString()
            }),
        Tool("get_gold_settings", "gold", "读取 金价设置", "读取金价提醒设置。", obj(), exec = { ctx, _ ->
            val c = GoldModule.config(ctx)
            JSONObject().put("后台监控开启", c.enabled).put("提醒依据", c.basis.title).put("首档", c.startTier).put("每档间隔元", c.step)
                .put("上涨提醒价", c.riseTarget).put("智能刷新", c.smart).put("开盘提醒", c.openAlert)
                .put("说明", "下跌按档位提醒：跌破首档提醒一次，之后每再跌一个间隔提醒一次").toString()
        }),
        Tool("update_gold_settings", "gold", "修改 金价设置", "修改金价提醒设置，只传要改的字段。“跌破 X 提醒我”= 把首档设为 X。", obj(
            "enabled" to bool("是否开启后台监控"),
            "basis" to str("提醒依据", listOf("market", "jcj", "any")),
            "start_tier" to int("首档（元/克），跌破它时第一次提醒"),
            "step" to int("每档间隔（元）"),
            "rise_target" to num("上涨提醒价（元/克）"),
            "open_alert" to bool("开盘提醒"),
            "smart" to bool("智能刷新"),
        ), plan = { ctx, a ->
            val o = GoldModule.config(ctx)
            var n = o
            val ch = mutableListOf<Pair<String, String>>()
            a.optBoolOrNull("enabled")?.let { if (it != o.enabled) { n = n.copy(enabled = it); ch += "后台监控" to "${onOff(o.enabled)} → ${onOff(it)}" } }
            a.optStrOrNull("basis")?.let { Basis.of(it) }?.let { if (it != o.basis) { n = n.copy(basis = it); ch += "提醒依据" to "${o.basis.title} → ${it.title}" } }
            a.optNumOrNull("start_tier")?.toInt()?.let { require(it in 100..5000) { "首档不合理：$it" }; if (it != o.startTier) { n = n.copy(startTier = it); ch += "首档" to "${o.startTier} → $it 元/克" } }
            a.optNumOrNull("step")?.toInt()?.let { require(it in 1..200) { "间隔不合理：$it" }; if (it != o.step) { n = n.copy(step = it); ch += "每档间隔" to "${o.step} → $it 元" } }
            a.optNumOrNull("rise_target")?.let { require(it in 100.0..10000.0) { "上涨提醒价不合理" }; if (it != o.riseTarget) { n = n.copy(riseTarget = it); ch += "上涨提醒" to "${fmt(o.riseTarget)} → ${fmt(it)} 元/克" } }
            a.optBoolOrNull("open_alert")?.let { if (it != o.openAlert) { n = n.copy(openAlert = it); ch += "开盘提醒" to "${onOff(o.openAlert)} → ${onOff(it)}" } }
            a.optBoolOrNull("smart")?.let { if (it != o.smart) { n = n.copy(smart = it); ch += "智能刷新" to "${onOff(o.smart)} → ${onOff(it)}" } }
            require(ch.isNotEmpty()) { "和现在的设置一样，不用改" }
            if (n.startTier != o.startTier || n.step != o.step || n.basis != o.basis) ch += "已提醒档位" to "重置（按新档位重新开始提醒）"
            val final = n
            Plan("gold", "金价设置：" + ch.joinToString("；") { "${it.first} ${it.second}" }, ch,
                mapOf("mod_gold" to listOf("enabled", "basis", "base", "start", "step", "rise", "smart", "interval", "open_alert", "last", "rise_armed"))) {
                GoldModule.saveConfig(ctx, final); "已保存金价设置"
            }
        }),

        // ================= 临床试验 =================
        Tool("list_trial_watches", "trials", "查看 临床试验关注", "列出已关注的临床试验检索条件。", obj(), exec = { ctx, _ ->
            JSONArray().apply {
                TrialModule.subs(ctx).forEach { put(JSONObject().put("id", it.id).put("关键词", it.keyword).put("条件", TrialLogic.describe(it)).put("当前项数", it.total).put("上次检查", t(it.lastCheck)).put("错误", it.lastError)) }
            }.toString().let { if (it == "[]") "还没有关注" else it } + "；后台检查开启=${TrialModule.enabled(ctx)}"
        }),
        Tool("recent_trial_changes", "trials", "查看 试验变化", "最近的临床试验变化记录（新试验、状态变化、出结果等）。", obj("limit" to int("最多几条，默认 10")), exec = { ctx, a ->
            val n = a.optInt("limit", 10).coerceIn(1, 30)
            val list = TrialModule.changes(ctx).sortedByDescending { it.atMs }.take(n)
            if (list.isEmpty()) "没有变化记录" else JSONArray().apply { list.forEach { put(JSONObject().put("NCT", it.nct).put("标题", it.title).put("类型", it.kind).put("详情", it.detail).put("时间", t(it.atMs))) } }.toString()
        }),
        Tool("preview_trial_search", "trials", "预览 试验检索", "在 ClinicalTrials.gov 试搜一个关注条件，看能匹配多少项（不保存）。", trialParams(), exec = { _, a ->
            val s = subFrom(a)
            val r = TrialModule.search(s)
            JSONObject().put("条件", TrialLogic.describe(s)).put("关键词", s.keyword).put("匹配项数", r.total)
                .put("前几项", JSONArray().apply { r.trials.take(5).forEach { put("${it.nct} ${it.title}（${TrialLogic.statusCn(it.status)}）") } }).toString()
        }),
        Tool("add_trial_watch", "trials", "新增 试验关注", "新增一个临床试验关注（会先试搜，用户确认后保存，之后每天 9:00 / 18:00 自动检查）。", trialParams(), plan = { ctx, a ->
            val s = subFrom(a)
            require(TrialModule.subs(ctx).none { TrialLogic.sameQuery(it, s) }) { "已经有一模一样的关注了" }
            val r = TrialModule.search(s)
            Plan("trials", "新增临床试验关注「${s.keyword}」（${TrialLogic.describe(s)}）", listOf(
                "关键词" to s.keyword + if (s.exact) "（完整短语）" else "",
                "检索条件" to TrialLogic.describe(s),
                "试搜结果" to "当前匹配 ${r.total} 项（保存后每天 9:00 / 18:00 检查变化）",
            ), mapOf("mod_trials" to null)) {
                TrialModule.saveBaseline(ctx, s, r); "已保存关注，当前 ${r.total} 项作为基准"
            }
        }),
        Tool("set_trial_monitoring", "trials", "开关 试验监控", "开启或关闭临床试验的后台定时检查。", obj("on" to bool("开启为 true"), required = listOf("on")), plan = { ctx, a ->
            val on = a.optBoolean("on"); val old = TrialModule.enabled(ctx)
            require(on != old) { "现在已经是${onOff(on)}" }
            Plan("trials", "临床试验后台检查 ${onOff(old)} → ${onOff(on)}", listOf("后台检查" to "${onOff(old)} → ${onOff(on)}"), mapOf("mod_trials" to listOf("enabled"))) {
                TrialModule.setEnabled(ctx, on); "已${onOff(on)}"
            }
        }),

        // ================= 读书 =================
        Tool("list_books", "read", "查看 书架", "列出书架上的书和阅读/听书进度。", obj(), exec = { ctx, _ ->
            val list = Library.recent(ctx)
            if (list.isEmpty()) "书架是空的" else JSONArray().apply {
                list.take(30).forEach { b -> put(JSONObject().put("书名", b.title).put("作者", b.authors.joinToString("、")).put("阅读进度", "%.0f%%".format(b.progress * 100)).put("上次听到", if (b.listenChapter >= 0) "第 ${b.listenChapter + 1} 章第 ${b.listenSentence + 1} 句" else "没听过")) }
            }.toString()
        }),
        Tool("listen_book", "read", "开始 听书", "用机器朗读从上次的位置接着读一本书（书名可只写一部分）。", obj("title" to str("书名"), required = listOf("title")), exec = { ctx, a ->
            val q = a.optString("title").trim()
            val b = Library.recent(ctx).firstOrNull { it.title.contains(q, true) } ?: return@Tool "书架上没找到“$q”"
            val ch = if (b.listenChapter >= 0) b.listenChapter else b.chapter
            val se = if (b.listenChapter >= 0) b.listenSentence else 0
            withContext(Dispatchers.Main) { Listen.startTts(ctx, b, ch, se) }
            "已开始朗读《${b.title}》第 ${ch + 1} 章，语速 ${Listen.speed}×"
        }),
        Tool("pause_listen", "read", "暂停 听书", "暂停正在进行的听书。", obj(), exec = { _, _ ->
            withContext(Dispatchers.Main) { if (Listen.playing) { Listen.pause(); "已暂停" } else "现在没有在听书" }
        }),
        Tool("set_listen_speed", "read", "修改 听书语速", "修改机器朗读语速（0.5–2.0）。", obj("speed" to num("语速倍数"), required = listOf("speed")), plan = { ctx, a ->
            val v = a.optDouble("speed").toFloat()
            require(v in 0.5f..2.0f) { "语速要在 0.5 到 2.0 之间" }
            val old = Store.module(ctx, "read").getFloat("tts_speed", 1f)
            Plan("read", "听书语速 ${old}× → ${v}×", listOf("机器朗读语速" to "${old}× → ${v}×"), mapOf("mod_read" to listOf("tts_speed"))) {
                withContext(Dispatchers.Main) { if (Listen.active) Listen.changeSpeed(v) else Store.module(ctx, "read").edit().putFloat("tts_speed", v).apply() }
                "已改为 ${v}×"
            }
        }),
        Tool("vocab_status", "read", "查看 生词本", "生词本有多少词、今天该复习几个、最近加的词。", obj(), exec = { ctx, _ ->
            val all = Notes.words(ctx); val due = Notes.dueWords(ctx)
            JSONObject().put("总词数", all.size).put("今天该复习", due.size).put("该复习的词", JSONArray(due.take(20).map { "${it.word} ${it.meaning.lineSequence().firstOrNull().orEmpty()}" }))
                .put("最近加入", JSONArray(all.sortedByDescending { it.addedAt }.take(10).map { it.word })).toString()
        }),

        // ================= 翻译 =================
        Tool("translate_text", "translate", "翻译", "用百度翻译翻译文字（中英互译），会占用百度翻译每月额度。", obj("text" to str("要翻译的文字"), "to" to str("目标语言", listOf("en", "zh")), required = listOf("text", "to")), exec = { ctx, a ->
            val to = a.optString("to", "en"); val from = if (to == "en") "zh" else "en"
            val (r, e) = Translator.translateText(ctx, a.optString("text"), from, to)
            r ?: "翻译失败：$e"
        }),

        // ================= 日期计算 =================
        Tool("date_calc", "datecalc", "日期计算", "日期计算（中国法定节假日与调休已内置，农历 1901–2100）。op=diff 算两个日期相差多少天/工作日；op=add 从某天往后或往前推 n 天/工作日/周/月；op=info 查某天是星期几、是否工作日、农历。日期格式 yyyy-MM-dd。", obj(
            "op" to str("操作", listOf("diff", "add", "info")),
            "start" to str("起始日期 yyyy-MM-dd（info 时为要查的日期）"),
            "end" to str("结束日期（diff 用）"),
            "n" to int("推算数量，往前为负（add 用）"),
            "unit" to str("单位（add 用）", listOf("day", "workday", "week", "month")),
            "include_start" to bool("diff 时是否包含开始当天，默认否"),
            required = listOf("op", "start"),
        ), exec = { _, a ->
            val s = LocalDate.parse(a.optString("start"))
            fun info(d: LocalDate) = "$d ${DateLogic.weekName(d)}，${if (Workdays.isWorkday(d)) "工作日" else "休息日"}${if (Workdays.isMakeupWorkday(d)) "（调休上班）" else ""}，农历 ${Lunar.of(d) ?: "超出范围"}" +
                if (!Workdays.covered(d.year)) "（注意：${d.year} 年节假日安排还没公布，只按周末算）" else ""
            when (a.optString("op")) {
                "diff" -> {
                    val e = LocalDate.parse(a.optString("end"))
                    val r = DateLogic.diff(s, e, a.optBoolean("include_start", false))
                    "从 $s 到 $e：相差 ${r.days} 天（${r.weeks} 周 ${r.restDays} 天；${r.months} 个月 ${r.monthRestDays} 天），其中工作日 ${r.workdays} 天" +
                        if (r.uncovered.isNotEmpty()) "（${r.uncovered.joinToString("、")} 年节假日未公布，工作日只按周末算）" else ""
                }
                "add" -> {
                    val u = when (a.optString("unit", "day")) { "workday" -> DateLogic.Unit.WORKDAY; "week" -> DateLogic.Unit.WEEK; "month" -> DateLogic.Unit.MONTH; else -> DateLogic.Unit.DAY }
                    val d = DateLogic.add(s, a.optLong("n"), u)
                    "$s ${if (a.optLong("n") >= 0) "往后" else "往前"} ${kotlin.math.abs(a.optLong("n"))} ${u.name2}是 ${info(d)}"
                }
                else -> info(s)
            }
        }),

        // ================= 联网 =================
        Tool("web_search", "web", "联网搜索", "用 DeepSeek 搜索引擎搜网页，返回标题、链接和摘要。需要最新信息（新闻、公司动态、政策、价格原因等）时用。回答时在末尾列出用到的来源链接。", obj(
            "query" to str("搜索词（可用 site: 等语法），1-128 字"), "count" to int("返回条数，默认 6，最多 10"), required = listOf("query"),
        ), exec = { ctx, a ->
            val q = a.optString("query").trim().take(128); require(q.isNotEmpty()) { "搜索词为空" }
            val body = JSONObject().put("query", q).put("count", a.optInt("count", 6).coerceIn(1, 10)).toString()
            val hits = AiLogic.parseSearch(dsPost(ctx, "https://api.deepseek.com/v1/tools/search", body))
            if (hits.isNotEmpty()) AiStore.addCost(ctx, AiLogic.SEARCH_COST_YUAN)
            if (hits.isEmpty()) "没有搜到结果" else JSONArray().apply { hits.forEachIndexed { i, h -> put(JSONObject().put("n", i + 1).put("标题", h.title).put("链接", h.url).put("摘要", h.snippet)) } }.toString()
        }),
        Tool("web_fetch", "web", "打开网页", "打开一个网页，读出正文（Markdown）。用于细看 web_search 找到的某条结果。", obj("url" to str("网页地址 http(s)"), required = listOf("url")), exec = { ctx, a ->
            val u = a.optString("url").trim(); require(u.startsWith("http")) { "网址不对" }
            val o = JSONObject(dsPost(ctx, "https://api.deepseek.com/v1/tools/fetch", JSONObject().put("url", u).toString()))
            AiStore.addCost(ctx, AiLogic.SEARCH_COST_YUAN)
            "标题：${o.optString("title")}\n${o.optString("content").take(6000)}"
        }),

        // ================= 全局 =================
        Tool("app_status", "global", "查看 App 状态", "百宝箱版本、主题、首页模块顺序、各模块后台是否开启。", obj(), exec = { ctx, _ ->
            JSONObject().put("版本", BuildConfig.VERSION_NAME).put("主题", themeName(ThemeManager.key(ctx)))
                .put("首页顺序", JSONArray(ModuleRegistry.visibleOrdered(ctx).map { "${it.name}(${it.id})" }))
                .put("隐藏的模块", JSONArray(Store.global(ctx).getStringSet("home_hidden", emptySet())!!.toList()))
                .put("后台在跑", JSONArray(ModuleRegistry.all.filter { it.background?.isEnabled(ctx) == true }.map { it.name })).toString()
        }),
        Tool("recent_records", "global", "查看 提醒记录", "最近的提醒记录（金价提醒、试验变化、系统事件等）。", obj("limit" to int("最多几条，默认 10")), exec = { ctx, a ->
            val list = AlertLog.load(ctx).take(a.optInt("limit", 10).coerceIn(1, 30))
            if (list.isEmpty()) "没有记录" else JSONArray().apply { list.forEach { put("${t(it.timeMs)} ${it.title}：${it.body}") } }.toString()
        }),
        Tool("check_update", "global", "检查更新", "检查百宝箱有没有新版本。", obj(), exec = { ctx, _ ->
            val u = Updater.check(); UpdateBadge.set(ctx, u)
            if (u == null) "已是最新版本 v${BuildConfig.VERSION_NAME}" else "有新版本 v${u.versionName}（${u.source}），请到「我的 › 检查更新」点下载安装"
        }),
        Tool("set_theme", "global", "修改 主题", "切换 App 主题。", obj("theme" to str("主题", listOf("dark", "ivory", "system")), required = listOf("theme")), plan = { ctx, a ->
            val k = a.optString("theme"); val old = ThemeManager.key(ctx)
            require(k in listOf("dark", "ivory", "system")) { "没有这个主题" }
            require(k != old) { "现在已经是${themeName(k)}" }
            Plan("global", "主题 ${themeName(old)} → ${themeName(k)}", listOf("主题" to "${themeName(old)} → ${themeName(k)}"), mapOf("global" to listOf("theme"))) {
                withContext(Dispatchers.Main) { ThemeManager.set(ctx, k) }; "已切换"
            }
        }),
        Tool("arrange_home", "global", "整理 首页", "调整首页模块顺序或隐藏/显示模块。order 是模块 id 的新顺序（可只写想放前面的几个），hide / show 是要隐藏/显示的模块 id。先用 app_status 看有哪些 id。", obj(
            "order" to strArr("新的顺序（模块 id）"), "hide" to strArr("要隐藏的模块 id"), "show" to strArr("要重新显示的模块 id"),
        ), plan = { ctx, a ->
            val ids = ModuleRegistry.all.map { it.id }.toSet()
            val order = a.strList("order").filter { it in ids }
            val hide = a.strList("hide").filter { it in ids && it != "ai" }
            val show = a.strList("show").filter { it in ids }
            require(order.isNotEmpty() || hide.isNotEmpty() || show.isNotEmpty()) { "没有可识别的模块 id" }
            val g = Store.global(ctx)
            val curOrder = ModuleRegistry.allOrdered(ctx).map { it.id }
            val newOrder = order + curOrder.filter { it !in order }
            val hidden = (g.getStringSet("home_hidden", emptySet())!! + hide - show.toSet())
            fun nm(id: String) = ModuleRegistry.byId(id)?.name ?: id
            val ch = mutableListOf<Pair<String, String>>()
            if (order.isNotEmpty()) ch += "首页顺序" to newOrder.filter { it !in hidden && it != "ai" }.joinToString(" → ") { nm(it) }
            if (hide.isNotEmpty()) ch += "隐藏" to hide.joinToString("、") { nm(it) }
            if (show.isNotEmpty()) ch += "重新显示" to show.joinToString("、") { nm(it) }
            Plan("global", "整理首页：" + ch.joinToString("；") { "${it.first} ${it.second}" }, ch, mapOf("global" to listOf("home_order", "home_hidden"))) {
                g.edit().putString("home_order", newOrder.joinToString(",")).putStringSet("home_hidden", hidden).apply(); "已整理"
            }
        }),
    )

    private suspend fun dsPost(ctx: Context, url: String, body: String): String {
        val st = AiStore.settings(ctx)
        val key = AiStore.key(ctx, "deepseek")
        require(key.isNotBlank()) { "联网搜索要用 DeepSeek 的密钥（AI 管家 › 设置里选 DeepSeek 并保存密钥）" }
        require(AiStore.monthCost(ctx) < st.cap) { "本月 AI 花费已到上限" }
        return try { com.eric.toolbox.core.Http.postJson(url, body, mapOf("Authorization" to "Bearer $key"), timeoutMs = 30_000) }
        catch (e: com.eric.toolbox.core.Http.HttpError) { if (e.code == 404) throw IllegalStateException("这个网页读不出来") else throw IllegalStateException(AiLogic.explainError(e.code, e.body)) }
    }

    /** DeepSeek 余额（官方 GET /user/balance）。 */
    suspend fun balance(ctx: Context): AiLogic.Balance? {
        val key = AiStore.key(ctx, "deepseek"); if (key.isBlank()) return null
        return AiLogic.parseBalance(com.eric.toolbox.core.Http.get("https://api.deepseek.com/user/balance", mapOf("Authorization" to "Bearer $key"), timeoutMs = 15_000))
    }

    private fun onOff(b: Boolean) = if (b) "开" else "关"
    private fun themeName(k: String) = when (k) { "ivory" -> "浅色"; "system" -> "跟随系统"; else -> "深色" }

    private fun trialParams() = obj(
        "type" to str("关键词类型：intr 药物/干预，cond 疾病，spons 申办方，nct 编号", listOf("intr", "cond", "spons", "nct")),
        "keyword" to str("关键词（英文，如 Lutetium Lu 177）"),
        "cond" to str("附加疾病条件（英文，可空）"),
        "country" to str("国家或地区英文名，如 China（可空=全部）"),
        "phases" to strArr("阶段，取值 1 2 3 4（可空=全部）"),
        "statuses" to strArr("状态：RECRUITING NOT_YET_RECRUITING ACTIVE_NOT_RECRUITING COMPLETED（可空=全部）"),
        "exact" to bool("关键词按完整短语匹配，默认 true"),
        required = listOf("type", "keyword"),
    )

    private fun subFrom(a: JSONObject): Sub {
        val kw = a.optString("keyword").trim()
        require(kw.isNotEmpty()) { "关键词不能为空" }
        return Sub(
            id = System.currentTimeMillis(),
            type = a.optString("type", "intr").takeIf { it in TrialLogic.TYPE_NAMES } ?: "intr",
            keyword = kw, cond = a.optString("cond").trim(), country = a.optString("country").trim(),
            exact = a.optBoolean("exact", true),
            phases = a.strList("phases").filter { it in listOf("1", "2", "3", "4") }.toSet(),
            statuses = a.strList("statuses").filter { it in TrialLogic.STATUS_FILTERS }.toSet(),
        )
    }

    fun byName(n: String) = all.firstOrNull { it.name == n }

    fun specs(groups: Set<String>): JSONArray = JSONArray().apply {
        all.filter { it.group in groups }.forEach { tl ->
            put(JSONObject().put("type", "function").put("function", JSONObject().put("name", tl.name).put("description", tl.desc + if (tl.write) "（会改设置：App 会先请用户确认）" else "").put("parameters", tl.params)))
        }
    }

    /** 撤销后让界面和后台跟上恢复的设置。 */
    fun afterUndo(ctx: Context) {
        ThemeManager.apply(ctx)
        MonitorService.sync(ctx)
    }
}

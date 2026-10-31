@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.eric.toolbox.modules.trials

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.RunWindow
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.ListCard
import com.eric.toolbox.ui.Mono
import com.eric.toolbox.ui.Pill
import com.eric.toolbox.ui.SectionLabel
import com.eric.toolbox.ui.ToggleRow
import com.eric.toolbox.ui.TopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private sealed interface TPage {
    data object Main : TPage
    data class SubPage(val id: Long) : TPage
    data class Detail(val nct: String) : TPage
    data class Add(val editId: Long? = null, val presetNct: String? = null) : TPage
}

@Composable
fun TrialRoot(onBack: () -> Unit) {
    var stack by remember { mutableStateOf(listOf<TPage>(TPage.Main)) }
    val go: (TPage) -> Unit = { stack = stack + it }
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) else onBack() }
    BackHandler(enabled = stack.size > 1) { back() }
    when (val p = stack.last()) {
        TPage.Main -> TrialMain(onBack, go)
        is TPage.SubPage -> SubScreen(p.id, back, go)
        is TPage.Detail -> DetailScreen(p.nct, back, go)
        is TPage.Add -> AddScreen(p.editId, p.presetNct, back) { stack = listOf(TPage.Main) }
    }
}

private val bj = TimeZone.getTimeZone("Asia/Shanghai")
private fun fmt(p: String, ms: Long) = SimpleDateFormat(p, Locale.CHINA).apply { timeZone = bj }.format(Date(ms))
private fun shortDate(d: String) = if (d.startsWith(fmt("yyyy", System.currentTimeMillis()) + "-")) d.drop(5) else d

private fun statusColors(s: String): Pair<Color, Color> = when (s) {
    "RECRUITING" -> C.Ok to C.OkBg
    "ACTIVE_NOT_RECRUITING", "ENROLLING_BY_INVITATION" -> C.Gold to C.GoldBg
    "TERMINATED", "WITHDRAWN", "SUSPENDED" -> C.Warn to C.WarnBg
    else -> C.Muted to C.Surface
}

@Composable
private fun Screen(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        Box(Modifier.height(16.dp))
    }
}

@Composable
private fun Note(text: String, color: Color = C.Muted) {
    Text(text, color = color, fontSize = 12.sp, lineHeight = 18.sp,
        modifier = Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp))
}

@Composable
private fun TrialCard(t: Trial, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, extra: String? = null) {
    Column(
        Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.nct, color = C.Gold, fontSize = 13.sp, fontFamily = Mono, modifier = Modifier.weight(1f))
            if (t.lastUpdate.isNotBlank()) Text("官方更新 ${shortDate(t.lastUpdate)}", color = C.Muted, fontSize = 11.sp)
        }
        Text(t.title, color = C.Text, fontSize = 14.sp, lineHeight = 20.sp)
        if (extra != null) Text(extra, color = C.Gold, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val (fg, bg) = statusColors(t.status)
            Pill(TrialLogic.statusCn(t.status), fg, bg)
            Pill(TrialLogic.phaseCn(t.phases), C.Muted, C.Bg)
            if (t.hasResults) Pill("已有结果", C.Warn, C.WarnBg)
            Text(t.sponsor, color = C.Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
}

// ======================= 主页 =======================

@Composable
private fun TrialMain(onBack: () -> Unit, go: (TPage) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val v by TrialModule.version.collectAsState()
    val subs = remember(v) { TrialModule.subs(ctx) }
    val changes = remember(v) { TrialModule.changes(ctx) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    Screen {
        TopBar("临床试验监控", onBack) {
            IconButton(onClick = {
                if (busy) return@IconButton
                busy = true; msg = "正在检查…"
                scope.launch {
                    val failed = runCatching { TrialModule.checkAll(ctx, force = true) }.getOrDefault(subs.size)
                    msg = if (failed == 0) "检查完成" else "$failed 组检查失败，15 分钟后自动重试"
                    busy = false
                }
            }) { Icon(Icons.Filled.Refresh, "立即检查", tint = C.Muted) }
            IconButton(onClick = { go(TPage.Add()) }) { Icon(Icons.Filled.Add, "添加关注", tint = C.Gold) }
        }
        val enabled = TrialModule.enabled(ctx)
        val last = TrialModule.lastRun(ctx)
        val anyErr = subs.any { it.lastError != null }
        Row(
            Modifier.fillMaxWidth().background(if (anyErr) C.WarnBg else C.OkBg, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).background(if (anyErr) C.Warn else C.Ok, CircleShape))
            Text(
                when {
                    subs.isEmpty() -> "还没有关注，点右上角 ＋ 添加"
                    !enabled -> "自动检查已关闭"
                    anyErr -> "上次检查有失败，会自动重试"
                    else -> "已关注 ${subs.size} 组 · 每天 9:00、18:00 自动检查"
                },
                color = if (anyErr) C.Warn else C.Ok, fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            if (last > 0) Text("上次 ${fmt("MM-dd HH:mm", last)}", color = C.Muted, fontSize = 12.sp)
        }
        msg?.let { Text(it, color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp)) }

        SectionLabel("我的关注")
        subs.forEach { s ->
            Row(
                Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(16.dp)).clickable { go(TPage.SubPage(s.id)) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(TrialLogic.title(s), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(TrialLogic.describe(s), color = C.Muted, fontSize = 12.sp)
                    s.lastError?.let { Text("检查失败：$it（会自动重试）", color = C.Warn, fontSize = 11.sp) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(s.total.toString(), color = C.Gold, fontSize = 18.sp, fontFamily = Mono)
                    Text("项", color = C.Muted, fontSize = 11.sp)
                }
                Text("›", color = C.Muted)
            }
        }
        Text(
            "＋ 添加关注（药物、疾病、申办方或 NCT 编号）", color = C.Gold, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().border(1.dp, C.Line, RoundedCornerShape(16.dp)).clickable { go(TPage.Add()) }.padding(14.dp),
        )

        if (changes.isNotEmpty()) {
            SectionLabel("最近的变化（App 每次检查时对比得出）")
            changes.take(30).forEach { c ->
                Column(
                    Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(16.dp)).clickable { go(TPage.Detail(c.nct)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row {
                        Text(c.nct, color = C.Gold, fontSize = 13.sp, fontFamily = Mono, modifier = Modifier.weight(1f))
                        Text(fmt("MM-dd HH:mm", c.atMs), color = C.Muted, fontSize = 11.sp)
                    }
                    Text(c.detail, color = if (c.kind == "other") C.Muted else C.Text, fontSize = 13.sp)
                    Text(c.title, color = C.Muted, fontSize = 12.sp, maxLines = 2)
                }
            }
        } else if (subs.isNotEmpty()) {
            // 还没有变化记录时，展示各组里官方更新日期最近的几项
            val recent = remember(v) {
                subs.flatMap { s -> TrialModule.snapshot(ctx, s.id)?.values.orEmpty().filter { it.nct !in s.excluded } }
                    .distinctBy { it.nct }.sortedByDescending { it.lastUpdate }.take(5)
            }
            if (recent.isNotEmpty()) {
                SectionLabel("官方最近更新的试验")
                recent.forEach { t -> TrialCard(t, onClick = { go(TPage.Detail(t.nct)) }) }
            }
        }
        Note(
            "会提醒哪些变化：新登记、状态变化、结果发布、阶段变化、关键日期调整（每组可单独勾选）。App 每次检查时与上次记录对比；" +
                "刚添加关注时只记录现状，不会把已有试验当成“新登记”。提醒只在提醒时段（${RunWindow.describe(ctx)}）推送，" +
                "时段外发现的变化会在时段开始时补发。试验标题为 ClinicalTrials.gov 原文。",
        )
        ToggleRow("自动检查", "关闭后只在手动刷新时检查", enabled) { TrialModule.setEnabled(ctx, it) }
    }
}

// ======================= 关注详情 =======================

@Composable
private fun SubScreen(id: Long, back: () -> Unit, go: (TPage) -> Unit) {
    val ctx = LocalContext.current
    val v by TrialModule.version.collectAsState()
    val s = remember(v) { TrialModule.sub(ctx, id) }
    if (s == null) { LaunchedEffect(Unit) { back() }; return }
    val all = remember(v) { TrialModule.snapshot(ctx, id)?.values?.filter { it.nct !in s.excluded }?.sortedByDescending { it.lastUpdate }.orEmpty() }
    var filter by remember { mutableStateOf("all") }
    var toExclude by remember { mutableStateOf<Trial?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val shown = all.filter { filter == "all" || it.status == filter }

    Screen {
        TopBar(TrialLogic.title(s), back)
        Text(
            "${TrialLogic.describe(s)} · 共 ${s.total} 项" + (if (s.lastCheck > 0) "（${fmt("MM-dd HH:mm", s.lastCheck)} 检索）" else ""),
            color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp),
        )
        if (s.total > all.size + s.excluded.size) {
            Note("匹配结果超过 ${TrialLogic.PAGE} 项，App 只跟踪官方更新日期最近的 ${TrialLogic.PAGE} 项。建议加上地区、阶段或状态筛选缩小范围。", C.Warn)
        }
        val counts = all.groupingBy { it.status }.eachCount()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("全部 ${all.size}", filter == "all") { filter = "all" }
            counts.entries.sortedByDescending { it.value }.forEach { (st, n) ->
                Chip("${TrialLogic.statusCn(st)} $n", filter == st) { filter = st }
            }
        }
        shown.forEach { t -> TrialCard(t, onClick = { go(TPage.Detail(t.nct)) }, onLongClick = { toExclude = t }) }
        if (all.isEmpty()) Note(if (s.lastError != null) "检索失败：${s.lastError}。会自动重试，也可回上一页点刷新。" else "没有匹配的试验。")
        Note("关键词是按完整短语匹配的，偶尔仍会带入不相关的试验。长按某一项可“排除”，以后不再显示和提醒它" +
            (if (s.excluded.isNotEmpty()) "（已排除 ${s.excluded.size} 项）。" else "。"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton("编辑条件", C.Text, Modifier.weight(1f)) { go(TPage.Add(editId = id)) }
            OutlineButton("取消关注", C.Warn, Modifier.weight(1f)) { confirmDelete = true }
        }
    }
    toExclude?.let { t ->
        AlertDialog(
            onDismissRequest = { toExclude = null },
            title = { Text("排除 ${t.nct}？") },
            text = { Text("以后不再显示和提醒这项试验。") },
            confirmButton = { TextButton(onClick = { TrialModule.exclude(ctx, id, t.nct); toExclude = null }) { Text("排除") } },
            dismissButton = { TextButton(onClick = { toExclude = null }) { Text("取消") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("取消关注？") },
            text = { Text("这组条件和它的变化记录会一起删除。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; TrialModule.deleteSub(ctx, id); back() }) { Text("取消关注") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("保留") } },
        )
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, color = if (on) (if (C.palette.isLight) Color.White else C.Bg) else C.Muted, fontSize = 12.sp,
        modifier = Modifier.background(if (on) C.Gold else C.Surface, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun OutlineButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 46.dp).border(1.dp, C.Line, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontSize = 14.sp) }
}

@Composable
private fun FilledButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).background(if (enabled) C.Gold else C.Line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (C.palette.isLight) Color.White else C.Bg, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}

// ======================= 试验详情 =======================

@Composable
private fun DetailScreen(nct: String, back: () -> Unit, go: (TPage) -> Unit) {
    val ctx = LocalContext.current
    var d by remember { mutableStateOf<TrialModule.Detail?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(nct) {
        // 失败自动重试：10 秒、20 秒、40 秒……最长 5 分钟
        var wait = 10_000L
        while (d == null) {
            try {
                d = TrialModule.detail(nct); err = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                err = "加载失败：${e.message ?: "网络错误"}（${wait / 1000} 秒后自动重试）"
                delay(wait); wait = (wait * 2).coerceAtMost(5 * 60_000L)
            }
        }
    }
    val history = remember { TrialModule.changes(ctx).filter { it.nct == nct } }
    Screen {
        TopBar(nct, back)
        val x = d
        if (x == null) {
            Note(err ?: "加载中…", if (err != null) C.Warn else C.Muted)
        } else {
            val t = x.trial
            Text(t.title, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
            if (x.officialTitle.isNotBlank() && x.officialTitle != t.title) Text(x.officialTitle, color = C.Muted, fontSize = 12.sp, lineHeight = 18.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val (fg, bg) = statusColors(t.status)
                Pill(TrialLogic.statusCn(t.status), fg, bg)
                Pill(TrialLogic.phaseCn(t.phases), C.Muted, C.Bg)
                Pill(when (x.studyType) { "INTERVENTIONAL" -> "干预性研究"; "OBSERVATIONAL" -> "观察性研究"; "EXPANDED_ACCESS" -> "扩展使用"; else -> x.studyType }, C.Muted, C.Bg)
            }
            ListCard {
                KV("申办方", t.sponsor)
                KV("计划入组", t.enrollment?.let { "$it 人" } ?: "--")
                KV("开始日期", x.start.ifBlank { "--" })
                KV("主要完成日期", t.primaryCompletion.ifBlank { "--" })
                KV("主要终点", x.primaryOutcomes.joinToString("；").ifBlank { "--" })
                KV("适应症", x.conditions.take(3).joinToString("；").ifBlank { "--" } + if (x.conditions.size > 3) " 等 ${x.conditions.size} 项" else "")
                KV("研究地点", x.countries.entries.take(4).joinToString("、") { "${it.key} ${it.value} 家" }.ifBlank { "--" } + if (x.countries.size > 4) " 等" else "")
                KV("首次公布 / 官方更新", "${t.firstPost} / ${t.lastUpdate}")
                KV("是否已有结果", if (t.hasResults) "是" else "否")
            }
        }
        SectionLabel("App 记录的变化")
        if (history.isEmpty()) Note("还没有记录到变化。之后状态、阶段、结果发布或关键日期有变化时，会在这里按时间列出。")
        history.forEach { c ->
            Row(Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(fmt("MM-dd HH:mm", c.atMs), color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
                Text(c.detail, color = C.Text, fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledButton("在 ClinicalTrials.gov 打开", Modifier.weight(1f)) {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://clinicaltrials.gov/study/$nct"))) }
            }
            OutlineButton("单独关注此试验", C.Text, Modifier.weight(1f)) { go(TPage.Add(presetNct = nct)) }
        }
        Text("信息来自 ClinicalTrials.gov 官方接口（实时查询）。", color = C.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(end = 12.dp))
        Text(v, color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

// ======================= 添加 / 编辑关注 =======================

@Composable
private fun AddScreen(editId: Long?, presetNct: String?, back: () -> Unit, done: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val old = remember { editId?.let { TrialModule.sub(ctx, it) } }
    var type by remember { mutableStateOf(old?.type ?: if (presetNct != null) "nct" else "intr") }
    var kw by remember { mutableStateOf(old?.keyword ?: presetNct ?: "") }
    var cond by remember { mutableStateOf(old?.cond ?: "") }
    var country by remember { mutableStateOf(old?.country ?: "") }
    var exact by remember { mutableStateOf(old?.exact ?: true) }
    var phases by remember { mutableStateOf(old?.phases ?: emptySet()) }
    var statuses by remember { mutableStateOf(old?.statuses ?: emptySet()) }
    var events by remember { mutableStateOf(old?.events ?: TrialLogic.DEFAULT_EVENTS) }
    var preview by remember { mutableStateOf<SearchResult?>(null) }
    var previewFor by remember { mutableStateOf<Sub?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    fun build() = Sub(
        id = old?.id ?: System.currentTimeMillis(), type = type, keyword = kw.trim(), cond = cond.trim(), country = country.trim(),
        exact = exact, phases = phases, statuses = statuses, events = events, excluded = old?.excluded ?: emptySet(),
    )
    val valid = if (type == "nct") TrialLogic.parseNctList(kw).isNotEmpty() else kw.isNotBlank()
    val current = build()
    // 编辑时只改了提醒类型：不用重新检索，原有记录保留
    val onlyEvents = old != null && TrialLogic.sameQuery(old, current)
    // 条件改了，之前的预览就不算数了
    val previewValid = preview != null && previewFor?.let { TrialLogic.sameQuery(it, current) } == true

    Screen {
        TopBar(if (old != null) "编辑关注" else "添加关注", back)
        SectionLabel("按什么关注")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TrialLogic.TYPE_NAMES.forEach { (k, label) -> Chip(label, type == k) { type = k } }
        }
        ListCard {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    kw, { kw = it }, modifier = Modifier.fillMaxWidth(), singleLine = type != "nct",
                    label = { Text(if (type == "nct") "NCT 编号（可多个，用空格或逗号分隔）" else "关键词") },
                    placeholder = { Text(when (type) { "intr" -> "如 Lutetium Lu 177"; "cond" -> "如 prostate cancer"; "spons" -> "如 Sinotau"; else -> "如 NCT05773274" }) },
                )
                if (type == "intr" || type == "spons") {
                    OutlinedTextField(cond, { cond = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("疾病 / 适应症（可选）") }, placeholder = { Text("如 prostate cancer") })
                }
                if (type != "nct") {
                    OutlinedTextField(country, { country = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("国家 / 地区（可选，英文）") }, placeholder = { Text("如 China") })
                }
            }
            if (type != "nct") ToggleRow("按完整短语匹配", "推荐开启：关键词整体出现才算，减少不相关结果", exact) { exact = it }
        }
        if (type != "nct") {
            SectionLabel("筛选（可选，不选表示不限）")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("1" to "I 期", "2" to "II 期", "3" to "III 期", "4" to "IV 期").forEach { (k, l) ->
                    Chip(l, k in phases) { phases = if (k in phases) phases - k else phases + k }
                }
                TrialLogic.STATUS_FILTERS.forEach { (k, l) ->
                    Chip(l, k in statuses) { statuses = if (k in statuses) statuses - k else statuses + k }
                }
            }
        }
        SectionLabel("提醒哪些变化")
        ListCard {
            TrialLogic.EVENT_NAMES.forEach { (k, l) ->
                ToggleRow(l, null, k in events) { on -> events = if (on) events + k else events - k }
            }
        }
        err?.let { Note(it, C.Warn) }
        if (previewValid) {
            val r = preview!!
            Note("实时检索到 ${r.total} 项" + (if (r.total > TrialLogic.PAGE) "，超过 ${TrialLogic.PAGE} 项，App 只跟踪官方更新最近的 ${TrialLogic.PAGE} 项，建议加筛选" else "") +
                (if (r.total > 0) "。前几项如下，确认是你要的再保存。" else "。可以换个关键词或关闭“按完整短语匹配”。"),
                if (r.total > TrialLogic.PAGE) C.Warn else C.Muted)
            r.trials.take(5).forEach { t -> TrialCard(t, onClick = {}) }
        }
        Note("保存前会先实时检索一次。ClinicalTrials.gov 每天更新，App 每天检查 2 次（9:00、18:00），也可以手动刷新。")
        FilledButton(
            when { busy -> "检索中…"; onlyEvents || previewValid -> "保存"; else -> "预览匹配结果" },
            enabled = valid && !busy,
        ) {
            when {
                onlyEvents -> { TrialModule.upsertSub(ctx, old!!.copy(events = events)); done() }
                previewValid -> { TrialModule.saveBaseline(ctx, current, preview!!); done() }
                else -> {
                    busy = true; err = null
                    scope.launch {
                        try {
                            val s = build()
                            preview = TrialModule.search(s); previewFor = s
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            err = "检索失败：${e.message ?: "网络错误"}。请检查网络后再点一次。"
                        }
                        busy = false
                    }
                }
            }
        }
    }
}

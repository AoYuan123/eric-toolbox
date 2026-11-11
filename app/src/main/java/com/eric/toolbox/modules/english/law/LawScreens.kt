package com.eric.toolbox.modules.english.law

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.eric.toolbox.modules.english.Btn
import com.eric.toolbox.modules.english.EnVoice
import com.eric.toolbox.modules.english.EnglishLogic
import com.eric.toolbox.modules.english.EnglishStore
import com.eric.toolbox.modules.english.Header
import com.eric.toolbox.modules.english.onAcc
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.Caption
import com.eric.toolbox.ui.Tile
import com.eric.toolbox.ui.press
import com.eric.toolbox.ui.tr

/** 「专业法律英语」：法条、判例、诉讼地图、中美对照、术语练习。所有文字默认英文，点一下切中文。 */
@Composable
internal fun LawRoot(voice: EnVoice, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val data = remember { LawData.get(ctx) }
    val stack = remember { mutableStateListOf("home") }
    fun go(r: String) { stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() }
    BackHandler { back() }
    val r = stack.last()
    when {
        r == "home" -> LawHome(data, ::go, ::back)
        r.startsWith("doc:") -> DocScreen(if (r == "doc:cn") data.cn else data.us, ::go, ::back)
        r.startsWith("unit:") -> UnitScreen(data, r.removePrefix("unit:"), voice, ::go, ::back) { id -> stack[stack.lastIndex] = "unit:$id" }
        r == "cases" -> CaseList(data, ::go, ::back)
        r.startsWith("case:") -> CaseScreen(data, r.removePrefix("case:"), voice, ::go, ::back)
        r == "map" -> MapScreen(data, ::go, ::back)
        r == "compare" -> CompareScreen(data, ::go, ::back)
        r == "drill" -> DrillScreen(data, voice, ::back)
        r == "terms" -> TermsScreen(data, voice, ::go, ::back)
    }
}

internal fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(ctx, tr("没找到能打开网页的应用", "No browser found"), Toast.LENGTH_SHORT).show() }
}

/**
 * 一段中英对照文字：默认显示英文，点一下切到中文，再点切回。
 * enNote / zhNote 是来源标注（官方英译、中文原文、Claude 译……），小字显示在右上角。
 */
@Composable
internal fun Flip(p: LawData.Para, enNote: String, zhNote: String, size: Int = 16, allZh: Boolean = false, voice: EnVoice? = null, indent: Int = 0, bold: Boolean = false) {
    var zh by remember(p, allZh) { mutableStateOf(allZh) }
    Column(Modifier.fillMaxWidth().padding(start = (indent * 14).dp).press { zh = !zh }.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (zh) "中" else "EN", color = C.Gold, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.border(1.dp, C.Gold.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
            Spacer(Modifier.width(6.dp))
            Caption(if (zh) zhNote else enNote, Modifier.weight(1f))
            if (voice != null && !zh) Icon(Icons.AutoMirrored.Outlined.VolumeUp, tr("朗读", "Read aloud"), tint = C.Muted,
                modifier = Modifier.size(28.dp).press { voice.speak(p.en, rate = 0.9f) }.padding(4.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(if (zh) p.zh else p.en, color = C.Text, fontSize = size.sp, lineHeight = (size * 1.55).sp,
            fontFamily = if (zh) FontFamily.Default else FontFamily.Serif, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Text(text, color = C.Text, fontSize = 13.sp, modifier = Modifier.border(1.dp, C.Line, RoundedCornerShape(14.dp)).press(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp))
}

@Composable
private fun SectionTitle(t: String) { Caption(t, Modifier.padding(start = 4.dp, top = 10.dp), color = C.Gold) }

private fun docNotes(d: LawData.Doc) = d.enLabel to d.zhLabel

// ======================= 首页 =======================

@Composable
private fun LawHome(data: LawData.Bundle, go: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val today = remember { LawData.todayUnit(data, EnglishLogic.dayNo(System.currentTimeMillis())) }
    val read = remember { LawData.readCount(ctx) }
    Column(Modifier.fillMaxSize()) {
        Header(tr("专业法律英语", "Patent law English"), back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Caption(tr("所有内容默认显示英文，点一下任意段落切换中文。", "Everything shows English first; tap any paragraph for Chinese."), Modifier.padding(horizontal = 4.dp))
            Tile(strong = true, onClick = { go("unit:${today.id}") }) {
                Caption(tr("今日一条", "Today’s provision"), color = C.Gold)
                Spacer(Modifier.height(6.dp))
                Text(today.labelEn + (if (today.title.isNotBlank()) " · ${today.title}" else ""), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(today.paras.first().en, color = C.Muted, fontSize = 14.sp, maxLines = 3, fontFamily = FontFamily.Serif)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeTile(tr("中国专利法", "China Patent Law"), tr("82 条 · 官方英译", "82 articles · official EN"), Modifier.weight(1f)) { go("doc:cn") }
                HomeTile(tr("美国专利法", "U.S. Patent Act"), tr("35 U.S.C. 节选", "35 U.S.C. selected"), Modifier.weight(1f)) { go("doc:us") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeTile(tr("经典判例", "Leading cases"), tr("${data.cases.size} 个 · 先猜再看", "${data.cases.size} · predict first"), Modifier.weight(1f)) { go("cases") }
                HomeTile(tr("诉讼地图", "Litigation map"), tr("机构 · 期限 · 去哪查", "Bodies · deadlines · sources"), Modifier.weight(1f)) { go("map") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeTile(tr("中美对照", "US vs China"), tr("${data.compare.size} 个主题", "${data.compare.size} topics"), Modifier.weight(1f)) { go("compare") }
                HomeTile(tr("术语练习", "Term drills"), tr("填空 · 中译英", "Cloze · ZH→EN"), Modifier.weight(1f)) { go("drill") }
            }
            Tile(onClick = { go("terms") }) {
                Text(tr("法条术语表（${data.terms.size} 个）", "Statutory terms (${data.terms.size})"), color = C.Text, fontSize = 15.sp)
                Caption(tr("每个词的例句都是法条原句，可加入每日背词", "Every example is a real statutory sentence; add them to daily review"))
            }
            Tile {
                Caption(tr("来源与时效", "Sources and currency"), color = C.Gold)
                Spacer(Modifier.height(4.dp))
                Text(tr("美国：${data.us.version}\n中国：${data.cn.version}\n最后核对：${data.meta.checked}　已读 $read 条",
                    "US: ${data.us.versionEn}\nChina: ${data.cn.versionEn}\nLast checked: ${data.meta.checked} · read $read"), color = C.Muted, fontSize = 13.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(4.dp))
                Caption(tr("法条原文由官方网站下载后逐字核对。中国法条英文是全国人大官方译文；美国法条和判例的中文是 Claude 译，仅供学习。", "Texts downloaded from official sites and checked word for word."))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HomeTile(title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Tile(modifier.height(96.dp), onClick = onClick) {
        Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Caption(sub)
    }
}

// ======================= 法条目录 =======================

@Composable
private fun DocScreen(doc: LawData.Doc, go: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    var keyOnly by remember { mutableStateOf(false) }
    val units = doc.units.filter { !keyOnly || it.key }
    Column(Modifier.fillMaxSize()) {
        Header(doc.titleEn.substringBefore(" (").ifBlank { doc.title }, back)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Caption("${doc.title} · ${doc.version}", Modifier.padding(horizontal = 4.dp))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(if (keyOnly) tr("✓ 只看有解读的重点条文", "✓ Key provisions only") else tr("只看有解读的重点条文", "Key provisions only")) { keyOnly = !keyOnly }
                }
                Spacer(Modifier.height(4.dp))
            }
            var last = ""
            units.forEach { u ->
                if (u.group != last) { last = u.group; val g = u.groupEn; item(key = "g" + u.id) { SectionTitle(g) } }
                item(key = u.id) {
                    val read = remember(u.id) { LawData.isRead(ctx, u.id) }
                    Tile(onClick = { go("unit:${u.id}") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.labelEn, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            if (u.key) Caption(tr("解读", "notes"), color = C.Gold)
                            if (read) { Spacer(Modifier.width(8.dp)); Caption("✓") }
                        }
                        if (u.title.isNotBlank()) Caption(u.title)
                        Text(u.paras.firstOrNull()?.en.orEmpty(), color = C.Muted, fontSize = 13.sp, maxLines = 2, fontFamily = FontFamily.Serif)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ======================= 单条法条 =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitScreen(data: LawData.Bundle, id: String, voice: EnVoice, go: (String) -> Unit, back: () -> Unit, replace: (String) -> Unit) {
    val ctx = LocalContext.current
    val u = data.units[id] ?: return
    val doc = data.docOf(id)
    val (enNote, zhNote) = docNotes(doc)
    var allZh by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) { LawData.markRead(ctx, id) }
    val terms = remember(id) { data.terms.filter { it.ref == id } }
    val cases = remember(id) { data.cases.filter { id in it.statutes } }
    val siblings = doc.units
    val idx = siblings.indexOfFirst { it.id == id }
    Column(Modifier.fillMaxSize()) {
        Header(u.labelEn, back) { Chip(if (allZh) tr("全部英文", "All EN") else tr("全部中文", "All 中文")) { allZh = !allZh } }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Caption("${u.label} · ${u.group}" + if (u.excerpt) tr(" · 节选", " · excerpt") else "", Modifier.padding(horizontal = 4.dp))
            if (u.title.isNotBlank()) Text(u.title, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))
            Tile(strong = true) {
                val en = if (u.zhOfficialOnly) tr("Claude 英译", "English by Claude") else enNote
                u.paras.forEach { p -> Flip(p, en, zhNote, allZh = allZh, voice = voice, indent = p.lvl) }
            }
            if (u.explain.isNotEmpty()) {
                SectionTitle(tr("延伸解读", "Notes"))
                Tile { u.explain.forEach { Flip(it, tr("解读 · Claude", "Notes · Claude"), tr("解读 · Claude", "Notes · Claude"), size = 15, allZh = allZh) } }
            }
            if (terms.isNotEmpty()) {
                SectionTitle(tr("本条术语", "Terms in this provision"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    terms.forEach { t -> Chip("${t.en} · ${t.zh.substringBefore('（')}") { voice.speak(t.en) } }
                }
            }
            if (cases.isNotEmpty()) {
                SectionTitle(tr("相关判例", "Related cases"))
                cases.forEach { c -> Tile(onClick = { go("case:${c.id}") }) { Text(c.name, color = C.Text, fontSize = 14.sp); Caption("${c.courtZh} · ${c.date.take(4)}") } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(tr("上一条", "Previous"), modifier = Modifier.weight(1f)) { if (idx > 0) replace(siblings[idx - 1].id) }
                Btn(tr("下一条", "Next"), primary = true, modifier = Modifier.weight(1f)) { if (idx < siblings.lastIndex) replace(siblings[idx + 1].id) }
            }
            Caption(doc.note, Modifier.padding(horizontal = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                doc.sources.forEach { (n, url) -> Chip("↗ $n") { openUrl(ctx, url) } }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ======================= 判例 =======================

@Composable
private fun CaseList(data: LawData.Bundle, go: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Header(tr("经典判例", "Leading cases"), back)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Caption(tr("先读案情、猜结果，再看法院怎么判。猜过的会标出对错。", "Read the facts, predict, then see the holding."), Modifier.padding(horizontal = 4.dp)) }
            listOf("en" to tr("美国", "United States"), "zh" to tr("中国", "China")).forEach { (o, label) ->
                item(key = "h$o") { SectionTitle(label) }
                items(data.cases.filter { it.orig == o }, key = { it.id }) { c ->
                    val a = remember(c.id) { LawData.caseAnswer(ctx, c.id) }
                    Tile(onClick = { go("case:${c.id}") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            if (a >= 0) Caption(if (a == c.answer) tr("猜对", "✓") else tr("猜错", "✗"), color = if (a == c.answer) C.Gold else C.Warn)
                        }
                        Caption("${c.courtZh} · ${c.date.take(4)} · ${c.tags.joinToString(" · ")}")
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CaseScreen(data: LawData.Bundle, id: String, voice: EnVoice, go: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val c = data.cases.firstOrNull { it.id == id } ?: return
    var picked by remember(id) { mutableIntStateOf(LawData.caseAnswer(ctx, id)) }
    val us = c.orig == "en"
    val quoteEn = if (us) tr("判决原文", "Opinion text") else tr("Claude 英译", "English by Claude")
    val quoteZh = if (us) tr("Claude 译", "Chinese by Claude") else tr("判决原文", "Original text")
    val note = tr("Claude 撰写", "Written by Claude")
    Column(Modifier.fillMaxSize()) {
        Header(tr("判例", "Case"), back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(c.name, color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
            Caption("${c.nameZh}\n${c.court} · ${c.date} · ${c.cite}", Modifier.padding(horizontal = 4.dp))
            SectionTitle(tr("案情", "Facts"))
            Tile { c.facts.forEach { Flip(it, note, note, size = 15) } }
            SectionTitle(tr("你来判断", "Your call"))
            Tile(strong = true) {
                Flip(c.question, note, note, bold = true)
                Spacer(Modifier.height(6.dp))
                c.options.forEachIndexed { i, o ->
                    val shown = picked >= 0
                    val color = when { !shown -> C.Line; i == c.answer -> C.Gold; i == picked -> C.Warn; else -> C.Line }
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, color, RoundedCornerShape(12.dp))
                        .press(enabled = !shown) { picked = i; LawData.setCaseAnswer(ctx, id, i) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(o.en, color = C.Text, fontSize = 15.sp)
                        if (shown) Caption(o.zh)
                    }
                }
                if (picked < 0) Caption(tr("选一个再看结果", "Pick one to reveal the holding"))
            }
            if (picked >= 0) {
                Caption(if (picked == c.answer) tr("判断正确。", "Correct.") else tr("没猜中，看看法院的理由。", "Not this time — see the reasoning."), color = if (picked == c.answer) C.Gold else C.Warn, modifier = Modifier.padding(horizontal = 4.dp))
                SectionTitle(tr("判决结果", "Holding"))
                Tile { c.holding.forEach { Flip(it, note, note, size = 15) } }
                SectionTitle(tr("判决原句", "Key passages"))
                Tile(strong = true) { c.quotes.forEach { Flip(it, quoteEn, quoteZh, voice = voice) } }
                SectionTitle(tr("延伸解读", "Notes"))
                Tile { c.explain.forEach { Flip(it, note, note, size = 15) } }
            }
            SectionTitle(tr("相关法条", "Statutes"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                c.statutes.forEach { s -> data.units[s]?.let { u -> Chip(u.labelEn) { go("unit:$s") } } }
                Chip(tr("↗ 判决原文", "↗ Full text")) { openUrl(ctx, c.url) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ======================= 诉讼地图 =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapScreen(data: LawData.Bundle, go: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    var j by remember { mutableIntStateOf(0) }
    val juris = data.map[j]
    val open = remember(j) { mutableStateListOf<String>() }
    val names = juris.nodes.associate { it.id to it.name }
    val note = tr("Claude 整理", "Compiled by Claude")
    Column(Modifier.fillMaxSize()) {
        Header(tr("诉讼地图", "Litigation map"), back)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            data.map.forEachIndexed { i, x ->
                val on = i == j
                Text(x.name + " · " + x.nameZh, color = if (on) onAcc() else C.Text, fontSize = 14.sp,
                    modifier = Modifier.background(if (on) C.Gold else C.Surface, RoundedCornerShape(12.dp)).press { j = i }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Caption(tr("点机构展开：职能、期限（附法条出处）、去哪查、在这一级判的案例。", "Tap a body for its role, deadlines, sources and cases."))
            juris.nodes.forEach { n ->
                val expanded = n.id in open
                Tile(strong = expanded, onClick = { if (expanded) open.remove(n.id) else open.add(n.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (n.kind == "court") "⚖" else "▣", color = C.Gold, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.name, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Caption(n.nameZh)
                        }
                        Text(if (expanded) "–" else "+", color = C.Muted, fontSize = 18.sp)
                    }
                    if (expanded) {
                        Spacer(Modifier.height(6.dp))
                        n.desc.forEach { Flip(it, note, note, size = 14) }
                        if (n.deadlines.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp)); Caption(tr("期限", "Deadlines"), color = C.Gold)
                            n.deadlines.forEach { d ->
                                Flip(LawData.Para(d.en, d.zh), note, note, size = 14)
                                data.units[d.ref]?.let { u -> Text("→ ${u.labelEn}", color = C.Gold, fontSize = 12.sp, modifier = Modifier.press { go("unit:${d.ref}") }.padding(vertical = 2.dp)) }
                            }
                        }
                        if (n.cases.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp)); Caption(tr("判例", "Cases"), color = C.Gold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                n.cases.forEach { cid -> data.cases.firstOrNull { it.id == cid }?.let { c -> Chip(c.name.substringBefore(" v.").substringBefore(" (")) { go("case:$cid") } } }
                            }
                        }
                        if (n.sites.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp)); Caption(tr("去哪查", "Where to look"), color = C.Gold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                n.sites.forEach { (name, url) -> Chip("↗ $name") { openUrl(ctx, url) } }
                            }
                        }
                    }
                }
                if (n.next.isNotEmpty()) Caption("   ↓ " + tr("上诉到 ", "appeal to ") + n.next.joinToString(" / ") { names[it] ?: it })
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ======================= 中美对照 =======================

@Composable
private fun CompareScreen(data: LawData.Bundle, go: (String) -> Unit, back: () -> Unit) {
    val note = tr("Claude 整理", "Compiled by Claude")
    Column(Modifier.fillMaxSize()) {
        Header(tr("中美对照", "US vs China"), back)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(data.compare, key = { it.topic }) { c ->
                Tile {
                    Flip(LawData.Para(c.topic, c.topicZh), note, note, size = 16, bold = true)
                    listOf("US" to c.us, "CN" to c.cn).forEach { (flag, s) ->
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(flag, color = C.Gold, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(28.dp))
                            data.units[s.ref]?.let { u -> Text("→ ${u.labelEn}", color = C.Gold, fontSize = 12.sp, modifier = Modifier.press { go("unit:${s.ref}") }) }
                        }
                        Flip(LawData.Para(s.en, s.zh), note, note, size = 14)
                    }
                    Spacer(Modifier.height(4.dp))
                    c.diff.forEach { Flip(it, tr("差别", "Difference"), tr("差别", "Difference"), size = 14) }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ======================= 术语 =======================

@Composable
private fun TermsScreen(data: LawData.Bundle, voice: EnVoice, go: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    var inBooks by remember { mutableStateOf(LAW_BOOK in EnglishStore.books(ctx)) }
    Column(Modifier.fillMaxSize()) {
        Header(tr("法条术语", "Statutory terms"), back)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Btn(if (inBooks) tr("已加入每日背词 ✓（点击移除）", "In daily review ✓ (tap to remove)") else tr("把这些术语加入每日背词", "Add these to daily review"), primary = !inBooks, modifier = Modifier.fillMaxWidth()) {
                    val b = EnglishStore.books(ctx)
                    EnglishStore.setBooks(ctx, if (inBooks) b - LAW_BOOK else b + LAW_BOOK); inBooks = !inBooks
                }
            }
            items(data.terms, key = { it.key }) { t ->
                var zh by remember { mutableStateOf(false) }
                Tile(onClick = { zh = !zh }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.en, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, tr("朗读", "Read aloud"), tint = C.Muted, modifier = Modifier.size(28.dp).press { voice.speak(t.en) }.padding(4.dp))
                    }
                    if (zh) {
                        Text(t.zh, color = C.Gold, fontSize = 14.sp)
                        Caption(t.noteZh)
                    } else Caption(tr("点一下看中文", "Tap for Chinese"))
                    Spacer(Modifier.height(4.dp))
                    Text(if (zh) t.exZh else t.exEn, color = C.Muted, fontSize = 13.sp, maxLines = 4, fontFamily = if (zh) FontFamily.Default else FontFamily.Serif)
                    Text("→ " + (data.units[t.ref]?.labelEn ?: t.refLabel), color = C.Gold, fontSize = 12.sp, modifier = Modifier.press { go("unit:${t.ref}") }.padding(vertical = 2.dp))
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ======================= 练习 =======================

@Composable
private fun DrillScreen(data: LawData.Bundle, voice: EnVoice, back: () -> Unit) {
    var mode by remember { mutableIntStateOf(0) }  // 0 填空，1 中译英
    val pool = remember(mode) {
        if (mode == 0) data.terms.filter { Regex(Regex.escape(it.en), RegexOption.IGNORE_CASE).containsMatchIn(it.exEn) }.shuffled()
        else data.terms.shuffled()
    }
    var i by remember(mode) { mutableIntStateOf(0) }
    var picked by remember(mode, i) { mutableStateOf<String?>(null) }
    var shown by remember(mode, i) { mutableStateOf(false) }
    var right by remember(mode) { mutableIntStateOf(0) }
    var done by remember(mode) { mutableIntStateOf(0) }
    val t = pool[i % pool.size]
    val options = remember(mode, i) { (pool.filter { it.key != t.key }.shuffled().take(3).map { it.en } + t.en).shuffled() }
    Column(Modifier.fillMaxSize()) {
        Header(tr("术语练习", "Term drills"), back) { Caption("$right / $done") }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(tr("法条填空", "Cloze"), tr("中译英", "ZH → EN")).forEachIndexed { k, label ->
                val on = k == mode
                Text(label, color = if (on) onAcc() else C.Text, fontSize = 14.sp,
                    modifier = Modifier.background(if (on) C.Gold else C.Surface, RoundedCornerShape(12.dp)).press { mode = k }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (mode == 0) {
                Caption(tr("这是法条原句，空格处应填哪个术语？", "A real statutory sentence — which term fills the blank?"))
                val blanked = Regex(Regex.escape(t.en), RegexOption.IGNORE_CASE).replaceFirst(t.exEn, "_____")
                Tile(strong = true) {
                    Text(if (picked != null) t.exEn else blanked, color = C.Text, fontSize = 16.sp, lineHeight = 25.sp, fontFamily = FontFamily.Serif)
                    if (picked != null) { Spacer(Modifier.height(6.dp)); Text(t.exZh, color = C.Muted, fontSize = 14.sp, lineHeight = 22.sp) }
                    Caption("— " + (data.units[t.ref]?.labelEn ?: t.refLabel))
                }
                options.forEach { o ->
                    val color = when { picked == null -> C.Line; o == t.en -> C.Gold; o == picked -> C.Warn; else -> C.Line }
                    Text(o, color = C.Text, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().border(1.dp, color, RoundedCornerShape(12.dp))
                        .press(enabled = picked == null) { picked = o; done++; if (o == t.en) right++ }.padding(horizontal = 12.dp, vertical = 10.dp))
                }
            } else {
                Caption(tr("看中文，先在心里说出英文术语，再点开对答案。", "Say the English term, then reveal."))
                Tile(strong = true, onClick = { if (!shown) { shown = true; done++ } }) {
                    Text(t.zh, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    if (shown) {
                        Text(t.en, color = C.Gold, fontSize = 18.sp, fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(6.dp))
                        Text(t.exEn, color = C.Muted, fontSize = 13.sp, lineHeight = 20.sp, fontFamily = FontFamily.Serif)
                    } else Caption(tr("点击显示答案", "Tap to reveal"))
                }
                if (shown) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(tr("没想起来", "Missed"), modifier = Modifier.weight(1f)) { i++ }
                    Btn(tr("想起来了", "Got it"), primary = true, modifier = Modifier.weight(1f)) { right++; i++ }
                }
            }
            if (picked != null || (mode == 1 && shown)) {
                Tile {
                    Text("${t.en} · ${t.zh}", color = C.Text, fontSize = 14.sp)
                    Caption(t.noteZh)
                }
                if (mode == 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(tr("朗读原句", "Read aloud"), modifier = Modifier.weight(1f)) { voice.speak(t.exEn, rate = 0.9f) }
                    Btn(tr("下一题", "Next"), primary = true, modifier = Modifier.weight(1f)) { i++ }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 学英语里的词书 id。 */
internal const val LAW_BOOK = "law"

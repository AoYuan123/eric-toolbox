package com.eric.toolbox.modules.gold

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.eric.toolbox.ui.Mono
import com.eric.toolbox.ui.Pill
import com.eric.toolbox.ui.Segmented
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 金价模块内部的页面。 */
private enum class GoldPage { MAIN, SETTINGS, HOLD_JCJ, HOLD_RY }

@Composable
fun GoldRoot(onBack: () -> Unit) {
    var page by remember { mutableStateOf(GoldPage.MAIN) }
    BackHandler(enabled = page != GoldPage.MAIN) { page = GoldPage.MAIN }
    when (page) {
        GoldPage.MAIN -> GoldMain(onBack, onSettings = { page = GoldPage.SETTINGS }, onHold = { page = it })
        GoldPage.SETTINGS -> GoldSettingsScreen { page = GoldPage.MAIN }
        GoldPage.HOLD_JCJ -> HoldingsScreen("jcj") { page = GoldPage.MAIN }
        GoldPage.HOLD_RY -> HoldingsScreen("ry") { page = GoldPage.MAIN }
    }
}

internal fun f2(v: Double?) = v?.let { String.format(Locale.CHINA, "%.2f", it) } ?: "--"
internal fun signed(v: Double) = (if (v >= 0) "+" else "") + String.format(Locale.CHINA, "%.2f", v)
private val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).apply { timeZone = BJ }
private val hmFmt = SimpleDateFormat("HH:mm", Locale.CHINA).apply { timeZone = BJ }

/** K 线缓存（10 分钟），避免每次切换都重新下载。 */
private object KCache {
    private val map = mutableMapOf<String, Pair<Long, List<Bar>>>()
    suspend fun get(key: String, load: suspend () -> List<Bar>): List<Bar> {
        val now = System.currentTimeMillis()
        map[key]?.let { if (now - it.first < 10 * 60_000L) return it.second }
        val v = load()
        if (v.isNotEmpty()) map[key] = now to v
        return v
    }
}

@Composable
private fun GoldMain(onBack: () -> Unit, onSettings: () -> Unit, onHold: (GoldPage) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var product by remember { mutableStateOf("market") }
    var trend by remember { mutableStateOf("h") }
    var compareOpen by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    val snap by GoldModule.snapshot.collectAsState()
    val status by GoldModule.status.collectAsState()
    val bankSeries by GoldModule.bankSeries.collectAsState()
    val cfg = remember(tick) { GoldModule.config(ctx) }
    val last = remember(tick) { GoldModule.lastAlerted(ctx) }

    // 页面打开时自己刷新（即使后台监控关闭也能看价格）
    LaunchedEffect(Unit) {
        GoldModule.loadBankSeries(ctx)
        while (true) {
            runCatching { GoldModule.refresh(ctx) }
            tick++
            delay(15_000)
        }
    }

    var chart by remember { mutableStateOf<ChartData?>(null) }
    var chartErr by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(product, trend, if (trend == "h") tick / 4 else 0L, bankSeries.size) {
        chartErr = null
        chart = try {
            buildChart(product, trend, snap, bankSeries, GoldModule.jcjPrev(ctx))
        } catch (e: Exception) {
            chartErr = "走势加载失败：${e.message ?: "网络错误"}"; null
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 顶栏：返回 · 标题 · 品种切换 · 设置
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = C.Muted) }
            Text("实时金价", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Segmented(listOf("market" to "大盘", "jcj" to "积存金", "ry" to "如意金"), product, compact = true) { product = it }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "金价设置", tint = C.Muted) }
        }

        PriceHeader(product, snap, GoldModule.jcjPrev(ctx))
        CompareRow(product, snap, compareOpen) { compareOpen = !compareOpen }

        // 走势
        Column(Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(18.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Segmented(listOf("h" to "分时", "d" to "日K", "m" to "月K", "y" to "年K"), trend, Modifier.fillMaxWidth()) { trend = it }
            val c = chart
            when {
                c != null -> PriceChart(c)
                chartErr != null -> Text(chartErr!!, color = C.Warn, fontSize = 12.sp, modifier = Modifier.height(190.dp))
                else -> Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) { Text("加载中…", color = C.Muted, fontSize = 12.sp) }
            }
            StatsRow(product, snap, ctx)
            Text(chartNote(product, trend) + " · 双指缩放，长按看价格，双击还原", color = C.Muted, fontSize = 11.sp)
        }

        // 档位概况
        val next = TierLogic.nextTier(last, cfg.startTier, cfg.step)
        val basisPrice = when (cfg.basis) {
            Basis.MARKET -> snap?.market?.price
            Basis.JCJ -> snap?.bank?.jcj
            Basis.ANY -> listOfNotNull(snap?.market?.price, snap?.bank?.jcj).minOrNull()
        }
        Row(
            Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(14.dp)).clickable(onClick = onSettings).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "下一档 $next" + (basisPrice?.let { " · 还差 ${f2((it - next).coerceAtLeast(0.0))} 元" } ?: ""),
                    color = C.Text, fontSize = 14.sp,
                )
                Text(
                    "按${cfg.basis.title} · 已提醒到 ${if (last > cfg.startTier) "无" else "$last"} · 上涨提醒 ${GoldModule.fmtBase(cfg.riseTarget)}",
                    color = C.Muted, fontSize = 12.sp,
                )
            }
            Text("›", color = C.Muted, fontSize = 18.sp)
        }

        if (product != "market") {
            Row(
                Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(14.dp))
                    .clickable { onHold(if (product == "jcj") GoldPage.HOLD_JCJ else GoldPage.HOLD_RY })
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(36.dp).background(C.GoldBg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AccountBalanceWallet, null, tint = C.Gold, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(if (product == "jcj") "我的积存金" else "我的如意金积存", color = C.Text, fontSize = 15.sp)
                    Text(if (product == "jcj") "持仓、盈亏、交易明细" else "持仓、按赎回价算的市值、交易明细", color = C.Muted, fontSize = 12.sp)
                }
                Text("›", color = C.Muted, fontSize = 18.sp)
            }
        }

        // 运行状态
        val now = System.currentTimeMillis()
        val stale = status.lastOkMs == 0L || now - status.lastOkMs > 2 * 60_000L
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    status.lastOkMs == 0L && status.error != null -> "获取失败：${status.error}"
                    status.lastOkMs == 0L -> "正在获取…"
                    else -> "最后成功获取 ${timeFmt.format(Date(status.lastOkMs))} · " + if (stale) "已超过 2 分钟未更新" else if (status.error != null) "部分来源异常" else "正常"
                },
                color = if (stale && status.lastOkMs != 0L || (status.lastOkMs == 0L && status.error != null)) C.Warn else C.Muted,
                fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            Text("立即刷新", color = C.Gold, fontSize = 12.sp, modifier = Modifier.clickable {
                scope.launch { runCatching { GoldModule.refresh(ctx) }; tick++ }
            }.padding(6.dp))
        }
        if (status.error != null && status.lastOkMs != 0L) Text(status.error!!, color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
        if (!cfg.enabled) Text("后台监控已关闭，只有打开本页时才刷新。可在右上角设置里开启。", color = C.Warn, fontSize = 12.sp)
        else if (!RunWindow.isOpen(ctx)) Text("当前不在运行时段（${RunWindow.describe(ctx)}），后台不监控、不提醒。", color = C.Muted, fontSize = 12.sp)
        Box(Modifier.height(12.dp))
    }
}

@Composable
private fun PriceHeader(product: String, snap: Snapshot?, jcjPrev: Double?) {
    val m = snap?.market; val b = snap?.bank
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (product) {
            "market" -> {
                val chg = if (m?.prevClose != null) m.price - m.prevClose else null
                val color = when { chg == null -> C.Text; chg >= 0 -> C.Up; else -> C.Down }
                BigPrice(f2(m?.price), color, chg?.let { signed(it) }, chg?.let { String.format(Locale.CHINA, "%+.2f%%", it / m!!.prevClose!! * 100) })
                Text(
                    (m?.timeMs?.let { timeFmt.format(Date(it)) } ?: "--") + " · " + (if (Session.marketTrading(System.currentTimeMillis())) "交易中" else "休市") + " · 上海金 Au99.99（东方财富）",
                    color = C.Muted, fontSize = 12.sp,
                )
            }
            "jcj" -> {
                val chg = if (b?.jcj != null && jcjPrev != null) b.jcj - jcjPrev else null
                val color = when { chg == null -> C.Gold; chg >= 0 -> C.Up; else -> C.Down }
                BigPrice(f2(b?.jcj), color, chg?.let { signed(it) }, chg?.let { String.format(Locale.CHINA, "%+.2f%%", it / jcjPrev!! * 100) })
                Text(
                    (b?.timeMs?.let { timeFmt.format(Date(it)) } ?: "--") + " · " + (if (Session.bankTrading(System.currentTimeMillis())) "交易中" else "休市") + " · 工行积存金（每 3 秒更新）",
                    color = C.Muted, fontSize = 12.sp,
                )
                Text("卖出到手价（扣 0.5% 手续费）${f2(b?.jcj?.let { it * 0.995 })}", color = C.Muted, fontSize = 12.sp)
            }
            else -> {
                BigPrice(f2(b?.ryBuy), C.Gold, null, null)
                Text(
                    (b?.timeMs?.let { timeFmt.format(Date(it)) } ?: "--") + " · 工行如意金积存（积存价）",
                    color = C.Muted, fontSize = 12.sp,
                )
                val spread = if (b?.ryBuy != null && b.rySell != null) b.ryBuy - b.rySell else null
                Text("赎回价 ${f2(b?.rySell)} · 买卖价差 ${f2(spread)}", color = C.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BigPrice(price: String, color: Color, chg: String?, pct: String?) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(price, color = color, fontSize = 44.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
        Text("元/克", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        if (chg != null) Text(chg, color = color, fontSize = 16.sp, fontFamily = Mono, modifier = Modifier.padding(bottom = 7.dp))
        if (pct != null) Text(pct, color = color, fontSize = 16.sp, fontFamily = Mono, modifier = Modifier.padding(bottom = 7.dp))
    }
}

@Composable
private fun CompareRow(product: String, snap: Snapshot?, open: Boolean, onToggle: () -> Unit) {
    val m = snap?.market; val b = snap?.bank
    val (ok, label) = when (product) {
        "market" -> snap?.marketVerified() to null
        "jcj" -> snap?.bankVerified() to null
        else -> null to "仅供参考"
    }
    val mismatch = ok == false && snap != null
    val expanded = open || mismatch
    fun rel(a: Double?, base: Double?) = if (a != null && base != null) String.format(Locale.CHINA, "%+.2f%%", (a - base) / base * 100) else ""
    val rows: List<Triple<String, Double?, String>> = when (product) {
        "market" -> listOf(
            Triple("黄金 T+D（东方财富）", snap?.td?.price, rel(snap?.td?.price, m?.price)),
            Triple("沪金连续（新浪）", snap?.futures?.price, rel(snap?.futures?.price, m?.price)),
            Triple("工行积存金", b?.jcj, rel(b?.jcj, m?.price)),
        )
        "jcj" -> listOf(
            Triple("大盘 Au99.99", m?.price, rel(m?.price, b?.jcj)),
            Triple("黄金 T+D", snap?.td?.price, rel(snap?.td?.price, b?.jcj)),
            Triple("如意金积存 赎回价", b?.rySell, rel(b?.rySell, b?.jcj)),
        )
        else -> listOf(
            Triple("工行积存金", b?.jcj, ""),
            Triple("大盘 Au99.99", m?.price, ""),
        )
    }
    Column(Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(14.dp))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onToggle).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("多源比对", color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            when {
                label != null -> Pill(label, C.Muted, C.Line)
                snap == null -> Pill("获取中", C.Muted, C.Line)
                mismatch -> Pill("不一致 · 已暂停提醒", C.Warn, C.WarnBg)
                else -> Pill("一致 ✓", C.Ok, C.OkBg)
            }
            Text(if (expanded) "  ▴" else "  ▾", color = C.Muted, fontSize = 14.sp)
        }
        if (expanded) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
                rows.forEach { (name, v, d) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = C.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(f2(v), color = C.Text, fontSize = 13.sp, fontFamily = Mono)
                        Text("  $d", color = if (mismatch) C.Warn else C.Muted, fontSize = 12.sp)
                    }
                }
                if (product == "ry") Text("如意金是实物积存报价，买卖价差较大，不参与一致性校验。", color = C.Muted, fontSize = 11.sp)
                if (mismatch) Text("与其他来源偏差超过容差，可能是数据异常，此时暂停提醒。", color = C.Warn, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StatsRow(product: String, snap: Snapshot?, ctx: android.content.Context) {
    val m = snap?.market; val b = snap?.bank
    val items: List<Triple<String, String, Color>> = when (product) {
        "market" -> listOf(
            Triple("最高", f2(m?.high), C.Up), Triple("最低", f2(m?.low), C.Down),
            Triple("昨收", f2(m?.prevClose), C.Text),
            Triple("今开", f2(m?.open), if (m?.open != null && m.prevClose != null && m.open < m.prevClose) C.Down else C.Up),
        )
        "jcj" -> listOf(
            Triple("最高", f2(b?.jcjHigh), C.Up), Triple("最低", f2(b?.jcjLow), C.Down),
            Triple("昨收", GoldModule.jcjPrev(ctx)?.let { f2(it) } ?: "记录中", C.Text),
            Triple("今开", GoldModule.jcjOpen(ctx)?.let { f2(it) } ?: "记录中", C.Text),
        )
        else -> listOf(
            Triple("积存价", f2(b?.ryBuy), C.Gold), Triple("赎回价", f2(b?.rySell), C.Text),
            Triple("价差", f2(if (b?.ryBuy != null && b.rySell != null) b.ryBuy - b.rySell else null), C.Muted),
            Triple("更新", b?.timeMs?.let { hmFmt.format(Date(it)) } ?: "--", C.Muted),
        )
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        items.forEach { (l, v, c) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(l, color = C.Muted, fontSize = 12.sp)
                Text(v, color = c, fontSize = 15.sp, fontFamily = Mono)
            }
        }
    }
}

private fun chartNote(product: String, trend: String): String = when {
    trend == "h" && product == "market" -> "分时：当日每分钟价格，虚线为昨收"
    trend == "h" -> "分时：工行无公开历史，由 App 每次刷新时记录"
    product != "market" -> "K 线显示大盘 Au99.99（工行无公开历史）"
    else -> "K 线：大盘 Au99.99，元/克"
}

/** 根据品种和周期生成走势图数据。 */
private suspend fun buildChart(
    product: String, trend: String, snap: Snapshot?, bank: List<GoldModule.BankPoint>, jcjPrev: Double?,
): ChartData {
    if (trend == "h") {
        if (product == "market") {
            val bars = KCache.get("min-${System.currentTimeMillis() / 60_000}") { GoldSource.klines(1, 900) }
            if (bars.isEmpty()) return ChartData.Line(emptyList(), emptyList(), null, "20:00" to "15:30")
            val start = Session.marketDayStart(bars.last().timeMs)
            val day = bars.filter { it.timeMs >= start }
            val cur = snap?.market?.price ?: day.last().close
            val pts = day.mapNotNull { b ->
                if (abs(b.close - cur) / cur > 0.05) return@mapNotNull null // 过滤异常成交
                Session.marketAxisMinute(b.timeMs)?.let { (it / 780f) to b.close }
            }
            val tips = day.filter { abs(it.close - cur) / cur <= 0.05 && Session.marketAxisMinute(it.timeMs) != null }.map { hmFmt.format(Date(it.timeMs)) }
            return ChartData.Line(pts, tips, snap?.market?.prevClose, "20:00 夜盘" to "次日 15:30")
        }
        val pts = bank.mapNotNull { p ->
            val v = if (product == "jcj") p.jcj else p.ryBuy
            val x = Session.bankAxisMinute(p.t)
            if (v != null && x != null) (x / 800f) to v else null
        }
        val tips = bank.filter { (if (product == "jcj") it.jcj else it.ryBuy) != null && Session.bankAxisMinute(it.t) != null }
            .map { hmFmt.format(Date(it.t)) }
        return ChartData.Line(pts, tips, if (product == "jcj") jcjPrev else null, "09:10" to "22:30")
    }
    return when (trend) {
        "d" -> {
            val d = GoldSource.clean(KCache.get("d") { GoldSource.klines(101, 120) })
            ChartData.Candles(d, (d.firstOrNull()?.label ?: "") to (d.lastOrNull()?.label ?: ""))
        }
        "m" -> {
            val m = GoldSource.clean(KCache.get("m") { GoldSource.klines(103, 240) }).takeLast(36).map { it.copy(label = it.label.take(7)) }
            ChartData.Candles(m, (m.firstOrNull()?.label ?: "") to (m.lastOrNull()?.label ?: ""))
        }
        else -> {
            val y = GoldSource.yearly(GoldSource.clean(KCache.get("m") { GoldSource.klines(103, 240) }))
            ChartData.Candles(y, (y.firstOrNull()?.label ?: "") to (y.lastOrNull()?.label ?: ""))
        }
    }
}

private fun abs(x: Double) = kotlin.math.abs(x)

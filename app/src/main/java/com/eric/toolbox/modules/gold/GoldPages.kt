package com.eric.toolbox.modules.gold

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.Notifier
import com.eric.toolbox.core.RunWindow
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.CardBox
import com.eric.toolbox.ui.ListCard
import com.eric.toolbox.ui.Mono
import com.eric.toolbox.ui.Pill
import com.eric.toolbox.ui.RowItem
import com.eric.toolbox.ui.SectionLabel
import com.eric.toolbox.ui.Segmented
import com.eric.toolbox.ui.ToggleRow
import com.eric.toolbox.ui.TopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ======================= 我的积存金 / 我的如意金积存 =======================

@Composable
fun HoldingsScreen(product: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val isJcj = product == "jcj"
    val snap by GoldModule.snapshot.collectAsState()
    var trades by remember { mutableStateOf(GoldModule.trades(ctx, product)) }
    var open by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("all") }
    var editing by remember { mutableStateOf<Trade?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Trade?>(null) }
    LaunchedEffect(Unit) { if (snap == null) runCatching { GoldModule.refresh(ctx) } }

    val h = LedgerMath.summarize(trades)
    val b = snap?.bank
    // 积存金：卖出到手 = 实时价 × 0.995；如意金：按工行赎回价
    val unitValue: Double? = if (isJcj) b?.jcj?.let { it * 0.995 } else b?.rySell
    val value = unitValue?.let { it * h.grams }
    val pnl = value?.let { it - h.cost }
    val pct = if (pnl != null && h.cost > 0) pnl / h.cost * 100 else null
    val save: (List<Trade>) -> Unit = { trades = it; GoldModule.saveTrades(ctx, product, it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(if (isJcj) "我的积存金" else "我的如意金积存", onBack)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardBox {
                Text(if (isJcj) "持仓盈亏（按卖出到手价 = 实时价 × 0.995）" else "持仓盈亏（按工行赎回价）", color = C.Muted, fontSize = 13.sp)
                val color = when { pnl == null || h.grams == 0.0 -> C.Muted; pnl >= 0 -> C.Up; else -> C.Down }
                Text(if (h.grams == 0.0) "暂无持仓" else pnl?.let { signed(it) } ?: "--", color = color, fontSize = 32.sp, fontFamily = Mono)
                Text(
                    (pct?.let { String.format(Locale.CHINA, "收益率 %+.2f%% · ", it) } ?: "") +
                        (if (isJcj) "积存金实时价 ${f2(b?.jcj)}" else "赎回价 ${f2(b?.rySell)} · 积存价 ${f2(b?.ryBuy)}"),
                    color = C.Muted, fontSize = 13.sp,
                )
                if (h.realized != 0.0) Text("已实现盈亏 ${signed(h.realized)}", color = C.Muted, fontSize = 12.sp)
            }
            CardBox {
                Row { Stat("持有克数", if (h.grams == 0.0) "0" else String.format(Locale.CHINA, "%.4f", h.grams).trimEnd('0').trimEnd('.'), Modifier.weight(1f)); Stat("平均成本（含费）", if (h.grams == 0.0) "--" else f2(h.avgCost), Modifier.weight(1f)) }
                if (isJcj) {
                    Row { Stat("当前市值", f2(b?.jcj?.let { it * h.grams }), Modifier.weight(1f)); Stat("卖出到手金额", f2(value), Modifier.weight(1f)) }
                    Row { Stat("保本卖出价", if (h.grams == 0.0) "--" else f2(h.avgCost / 0.995), Modifier.weight(1f)); Stat("今日盈亏", todayPnl(h.grams, b?.jcj, GoldModule.jcjPrev(ctx)), Modifier.weight(1f)) }
                } else {
                    Row { Stat("按赎回价市值", f2(value), Modifier.weight(1f)); Stat("保本赎回价", if (h.grams == 0.0) "--" else f2(h.avgCost), Modifier.weight(1f)) }
                    Row { Stat("当前积存价", f2(b?.ryBuy), Modifier.weight(1f)); Stat("买卖价差", f2(if (b?.ryBuy != null && b.rySell != null) b.ryBuy - b.rySell else null), Modifier.weight(1f)) }
                }
            }

            // 交易明细（默认折叠）
            Column(Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(18.dp))) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { open = !open }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("交易明细", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("共 ${trades.size} 笔  ${if (open) "收起 ▴" else "展开 ▾"}", color = C.Muted, fontSize = 13.sp)
                }
                if (open) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                        Segmented(listOf("all" to "全部", "buy" to "买入", "sell" to "卖出"), filter, Modifier.fillMaxWidth()) { filter = it }
                        val shown = trades.filter { filter == "all" || (filter == "buy") == it.buy }.sortedWith(compareByDescending<Trade> { it.date }.thenByDescending { it.id })
                        if (shown.isEmpty()) Text("还没有记录，点下面按钮记一笔", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp))
                        shown.forEach { t -> TradeRow(t, onEdit = { editing = t }, onDelete = { confirmDelete = t }) }
                        Text("点某一笔可编辑或删除" + if (isJcj) " · 积存金买卖手续费各 0.5%" else " · 如意金手续费以工行实际收取为准", color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Button(
                onClick = { adding = true },
                colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = if (C.palette.isLight) Color.White else C.Bg),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("＋ 记一笔交易") }
            Text("克数、平均成本、盈亏都由交易明细自动计算；数据只保存在你手机里，备份时一起导出。", color = C.Muted, fontSize = 12.sp)
            Box(Modifier.height(16.dp))
        }
    }

    if (adding || editing != null) {
        TradeDialog(isJcj, editing, b?.let { if (isJcj) it.jcj else it.ryBuy }, onDismiss = { adding = false; editing = null }) { t ->
            save(trades.filter { it.id != t.id } + t)
            adding = false; editing = null
        }
    }
    confirmDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除这笔交易？") },
            text = { Text("${t.date} ${if (t.buy) "买入" else "卖出"} ${fmtG(t.grams)} 克 @ ${f2(t.price)}") },
            confirmButton = { TextButton(onClick = { save(trades.filter { it.id != t.id }); confirmDelete = null }) { Text("删除", color = C.Warn) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }
}

private fun fmtG(g: Double) = String.format(Locale.CHINA, "%.4f", g).trimEnd('0').trimEnd('.')

private fun todayPnl(grams: Double, price: Double?, prev: Double?): String =
    if (grams == 0.0) "--" else if (price == null || prev == null) "记录中" else signed((price - prev) * grams)

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 2.dp)) {
        Text(label, color = C.Muted, fontSize = 12.sp)
        Text(value, color = C.Text, fontSize = 16.sp, fontFamily = Mono)
    }
}

@Composable
private fun TradeRow(t: Trade, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val fg = if (t.buy) C.Up else C.Down
    Column {
        HorizontalDivider(color = C.Line)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { menu = !menu },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(34.dp).background(fg.copy(alpha = 0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(if (t.buy) "买" else "卖", color = fg, fontSize = 13.sp)
            }
            Column(Modifier.weight(1f)) {
                Text("${fmtG(t.grams)} 克 · 成交价 ${f2(t.price)}", color = C.Text, fontSize = 14.sp)
                Text("${t.date} · 手续费 ${f2(t.fee)}", color = C.Muted, fontSize = 12.sp)
            }
            Text((if (t.buy) "−" else "+") + f2(if (t.buy) t.amount + t.fee else t.amount - t.fee), color = fg, fontSize = 14.sp, fontFamily = Mono)
        }
        if (menu) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                OutlinedButton(onClick = { menu = false; onEdit() }) { Text("编辑", color = C.Text) }
                OutlinedButton(onClick = { menu = false; onDelete() }) { Text("删除", color = C.Warn) }
            }
        }
    }
}

@Composable
private fun TradeDialog(isJcj: Boolean, initial: Trade?, currentPrice: Double?, onDismiss: () -> Unit, onSave: (Trade) -> Unit) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply { timeZone = BJ }.format(Date()) }
    var buy by remember { mutableStateOf(initial?.buy ?: true) }
    var date by remember { mutableStateOf(initial?.date ?: today) }
    var grams by remember { mutableStateOf(initial?.grams?.let { fmtG(it) } ?: "") }
    var price by remember { mutableStateOf(initial?.price?.let { f2(it) } ?: currentPrice?.let { f2(it) } ?: "") }
    var fee by remember { mutableStateOf(initial?.fee?.let { f2(it) } ?: "") }
    var feeTouched by remember { mutableStateOf(initial != null) }
    var err by remember { mutableStateOf("") }
    // 积存金：手续费默认按成交金额 0.5% 自动填入（可改）
    val autoFee = if (isJcj) {
        val g = grams.toDoubleOrNull(); val p = price.toDoubleOrNull()
        if (g != null && p != null) f2(g * p * 0.005) else ""
    } else ""
    val feeShown = if (!feeTouched && isJcj) autoFee else fee

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "记一笔交易" else "编辑交易") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Segmented(listOf("buy" to "买入", "sell" to "卖出"), if (buy) "buy" else "sell", Modifier.fillMaxWidth()) { buy = it == "buy" }
                OutlinedTextField(date, { date = it }, label = { Text("日期（yyyy-MM-dd）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(grams, { grams = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("克数") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(price, { price = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("成交价（元/克）") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(feeShown, { fee = it.filter { c -> c.isDigit() || c == '.' }; feeTouched = true },
                    label = { Text(if (isJcj) "手续费（元，默认 0.5%）" else "手续费（元，可不填）") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                if (err.isNotBlank()) Text(err, color = C.Warn, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val g = grams.toDoubleOrNull(); val p = price.toDoubleOrNull(); val f = feeShown.ifBlank { "0" }.toDoubleOrNull()
                err = when {
                    !Regex("""\d{4}-\d{2}-\d{2}""").matches(date) -> "日期格式应为 2026-10-30"
                    g == null || g <= 0 -> "克数需大于 0"
                    p == null || p <= 0 -> "成交价需大于 0"
                    f == null || f < 0 -> "手续费不能为负"
                    else -> ""
                }
                if (err.isEmpty()) onSave(Trade(initial?.id ?: System.currentTimeMillis(), buy, date, g!!, p!!, f!!))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ======================= 金价设置（含档位） =======================

@Composable
fun GoldSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c0 = remember { GoldModule.config(ctx) }
    var version by remember { mutableIntStateOf(0) }
    var basis by remember { mutableStateOf(c0.basis) }
    var base by remember { mutableStateOf(GoldModule.fmtBase(c0.basePrice)) }
    var start by remember { mutableStateOf(c0.startTier.toString()) }
    var step by remember { mutableStateOf(c0.step.toString()) }
    var rise by remember { mutableStateOf(GoldModule.fmtBase(c0.riseTarget)) }
    var enabled by remember { mutableStateOf(c0.enabled) }
    var smart by remember { mutableStateOf(c0.smart) }
    var near by remember { mutableIntStateOf(c0.nearSec) }
    var openAlert by remember { mutableStateOf(c0.openAlert) }
    var msg by remember { mutableStateOf("") }
    val cfg = remember(version) { GoldModule.config(ctx) }
    val last = remember(version) { GoldModule.lastAlerted(ctx) }
    val snap by GoldModule.snapshot.collectAsState()
    val price = when (cfg.basis) {
        Basis.MARKET -> snap?.market?.price
        Basis.JCJ -> snap?.bank?.jcj
        Basis.ANY -> listOfNotNull(snap?.market?.price, snap?.bank?.jcj).minOrNull()
    }
    val next = TierLogic.nextTier(last, cfg.startTier, cfg.step)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("金价设置", onBack)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("提醒依据")
            Segmented(Basis.entries.map { it.key to it.title }, basis.key, Modifier.fillMaxWidth()) { basis = Basis.of(it) }

            SectionLabel("下跌档位")
            ListCard {
                NumberRow("基准价（元/克）", base) { base = it }
                NumberRow("首个提醒档位", start) { start = it }
                NumberRow("每档间隔（元）", step) { step = it }
            }
            ListCard {
                val bottom = minOf(next, cfg.startTier) - cfg.step * 3
                generateSequence(cfg.startTier) { it - cfg.step }.takeWhile { it >= bottom }.forEach { t ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$t", color = C.Text, fontSize = 18.sp, fontFamily = Mono, modifier = Modifier.weight(1f))
                        when {
                            t >= last -> Pill("已提醒", C.Muted, C.Line)
                            t == next -> Pill("下一档" + (price?.let { " · 还差 ${f2((it - t).coerceAtLeast(0.0))}" } ?: ""), if (C.palette.isLight) Color.White else C.Bg, C.Gold)
                            else -> Pill("待触发", C.Muted, Color.Transparent)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("无下限，继续向下 · 回升超一档自动重置", color = C.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("手动重置", color = C.Gold, fontSize = 13.sp, modifier = Modifier.clickable { GoldModule.resetTiers(ctx); version++ }.padding(6.dp))
                }
            }

            SectionLabel("上涨提醒")
            ListCard { NumberRow("涨到此价提醒（元/克）", rise) { rise = it } }

            SectionLabel("监控与刷新")
            ListCard {
                RowItem("提醒时段", RunWindow.describe(ctx), sub = "数据全天更新，只在这个时段提醒；在“我的 › 提醒时段”里修改")
                ToggleRow("后台监控", "关闭后不再提醒", enabled) { enabled = it }
                ToggleRow("智能刷新间隔", "离档位越近刷新越快：>8 元每 3 分钟，3–8 元每 60 秒，3 元内按下面设置", smart) { smart = it }
                Row(Modifier.fillMaxWidth().heightIn(min = 50.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("接近档位时刷新", color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Segmented(listOf("15" to "15 秒", "30" to "30 秒"), near.toString(), compact = true) { near = it.toInt() }
                }
                ToggleRow("开盘提醒", "开盘时若隔夜已跌破档位，用“开盘提醒”单独标注", openAlert) { openAlert = it }
            }

            SectionLabel("数据源")
            ListCard {
                RowItem("大盘金价", "东方财富 Au99.99")
                RowItem("积存金 / 如意金", "工商银行官网行情页")
                RowItem("校验源", "东财 T+D · 新浪沪金")
            }
            Text("只有数据新鲜（大盘 10 分钟内、工行 5 分钟内）且多源一致时才会提醒。", color = C.Muted, fontSize = 12.sp)

            OutlinedButton(onClick = { Notifier.test(ctx); msg = if (Notifier.canPost(ctx)) "测试提醒已发送" else "通知未开启，请到“我的 › 后台保活设置”开启" },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("发送一条测试提醒", color = C.Text) }
            Button(
                onClick = {
                    val b = base.toDoubleOrNull(); val s = start.toIntOrNull(); val st = step.toIntOrNull(); val r = rise.toDoubleOrNull()
                    msg = when {
                        b == null || b <= 0 -> "基准价需要是大于 0 的数字"
                        s == null || s <= 0 -> "首个档位需要是大于 0 的整数"
                        st == null || st <= 0 || st > s -> "每档间隔需要是大于 0、且不超过首个档位的整数"
                        r == null || r < 0 -> "上涨提醒价不能为负（填 0 表示关闭）"
                        else -> {
                            GoldModule.saveConfig(ctx, GoldConfig(enabled, basis, b, s, st, r, smart, near, openAlert))
                            version++
                            "已保存"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = if (C.palette.isLight) Color.White else C.Bg),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("保存设置") }
            if (msg.isNotBlank()) Text(msg, color = if (msg == "已保存" || msg.startsWith("测试")) C.Ok else C.Warn, fontSize = 13.sp)
            Box(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NumberRow(label: String, value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value, onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }) },
            singleLine = true, modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(0.4f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = androidx.compose.ui.text.TextStyle(color = C.Gold, fontFamily = Mono, fontSize = 16.sp),
        )
    }
}

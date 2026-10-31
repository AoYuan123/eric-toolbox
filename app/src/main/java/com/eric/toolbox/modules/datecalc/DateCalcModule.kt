package com.eric.toolbox.modules.datecalc

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.Category
import com.eric.toolbox.core.ToolModule
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.ListCard
import com.eric.toolbox.ui.Mono
import com.eric.toolbox.ui.Segmented
import com.eric.toolbox.ui.ToggleRow
import com.eric.toolbox.ui.TopBar
import java.time.LocalDate
import java.time.ZoneId

/** 日期计算：日期间隔（含工作日）+ 日期推算（日/工作日/星期/月），结果附农历。纯本地计算，不联网。 */
object DateCalcModule : ToolModule {
    override val id = "datecalc"
    override val name = "日期计算"
    override val category = Category.TOOL
    override val icon: ImageVector = Icons.Filled.CalendarMonth

    @Composable
    override fun HomeSummary() {
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
        Text("${today.monthValue}月${today.dayOfMonth}日 ${DateLogic.weekName(today)}", color = C.Text, fontSize = 13.sp)
        Text("农历 ${Lunar.of(today)?.let { Lunar.monthName(it.month).let { m -> (if (it.leap) "闰" else "") + m } + Lunar.dayName(it.day) } ?: "--"}", color = C.Muted, fontSize = 12.sp)
    }

    @Composable
    override fun Screen(onBack: () -> Unit) = DateCalcScreen(onBack)
}

private fun today() = LocalDate.now(ZoneId.of("Asia/Shanghai"))

private fun dateSub(d: LocalDate): String =
    DateLogic.weekName(d) + (if (d == today()) " · 今天" else "") + (if (Workdays.isMakeupWorkday(d)) " · 调休上班" else "")

@Composable
private fun DateCalcScreen(onBack: () -> Unit) {
    var tab by remember { mutableStateOf("diff") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar("日期计算", onBack)
        Segmented(listOf("diff" to "日期间隔", "add" to "日期推算"), tab, Modifier.fillMaxWidth()) { tab = it }
        if (tab == "diff") DiffPane() else AddPane()
        Text(
            "支持 1901–2100 年的农历。节假日数据为 ${CalendarData.HOLIDAY_FIRST_YEAR}–${CalendarData.HOLIDAY_LAST_YEAR} 年（按国务院办公厅放假通知）；" +
                "2027 年放假安排国务院尚未公布，公布后随版本更新，在此之前 2027 年及以后的工作日只按周末计算并会标注。",
            color = C.Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 4.dp),
        )
        Box(Modifier.height(16.dp))
    }
}

@Composable
private fun DateRow(label: String, d: LocalDate, last: Boolean = false, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(d.toString(), color = C.Gold, fontSize = 16.sp, fontFamily = Mono)
        Text("  " + dateSub(d), color = C.Muted, fontSize = 12.sp)
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
    if (open) PickDate(d, { open = false }) { onPick(it); open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDate(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    // DatePicker 用 UTC 零点的毫秒数表示日期
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L,
        yearRange = 1901..2100,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))) } ?: onDismiss()
            }) { Text("确定") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPicked(today()) }) { Text("今天") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    ) { DatePicker(state = state, showModeToggle = true) }
}

@Composable
private fun ResultCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

private fun lunarText(d: LocalDate) = Lunar.of(d)?.toString() ?: "超出农历范围"

private fun uncoveredNote(years: List<Int>): String =
    if (years.isEmpty()) "工作日已扣除周末和法定节假日，并计入调休上班日。"
    else "注意：${years.joinToString("、")} 年的放假安排不在数据范围内（未公布或过早），这些年份的工作日只按周末计算。"

@Composable
private fun DiffPane() {
    var start by remember { mutableStateOf(today()) }
    var end by remember { mutableStateOf(today().plusMonths(2)) }
    var incl by remember { mutableStateOf(false) }
    val r = remember(start, end, incl) { DateLogic.diff(start, end, incl) }

    Text("计算两个日期之间相差多少天", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    ListCard {
        DateRow("开始日期", start) { start = it }
        DateRow("结束日期", end) { end = it }
        ToggleRow("包含开始当天", "算天数和工作日时是否把第一天也算上", incl) { incl = it }
    }
    ResultCard {
        Text(if (r.days < 0) "结束日期早于开始日期，相差" else "相差", color = C.Muted, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(kotlin.math.abs(r.days).toString(), color = C.Gold, fontSize = 44.sp, fontFamily = Mono)
            Text(" 天", color = C.Muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("折合周", "${r.weeks} 周" + if (r.restDays > 0) " ${r.restDays} 天" else "", Modifier.weight(1f))
            Stat("折合月", "${r.months} 个月" + if (r.monthRestDays > 0) " ${r.monthRestDays} 天" else "", Modifier.weight(1f))
            Stat("其中工作日", "${r.workdays} 天", Modifier.weight(1f))
        }
        Text(
            "农历：${lunarText(start)} → ${lunarText(end)}\n" + uncoveredNote(r.uncovered),
            color = if (r.uncovered.isEmpty()) C.Muted else C.Warn, fontSize = 12.sp, lineHeight = 18.sp,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = C.Muted, fontSize = 12.sp)
        Text(value, color = C.Text, fontSize = 15.sp)
    }
}

@Composable
private fun AddPane() {
    var base by remember { mutableStateOf(today()) }
    var dir by remember { mutableStateOf("after") }
    var numText by remember { mutableStateOf("10") }
    var unit by remember { mutableStateOf(DateLogic.Unit.WORKDAY) }
    val n = numText.toLongOrNull()?.coerceIn(0, 100_000) ?: 0
    val signed = if (dir == "after") n else -n
    val result = remember(base, signed, unit) { runCatching { DateLogic.add(base, signed, unit) }.getOrNull() }

    Text("推算几天后或几天前是哪一天", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    ListCard {
        DateRow("基准日期", base) { base = it }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("方向", color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Segmented(listOf("after" to "往后", "before" to "往前"), dir, Modifier.width(150.dp)) { dir = it }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("数量", color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            OutlinedTextField(
                numText, { v -> numText = v.filter(Char::isDigit).take(6) },
                singleLine = true, modifier = Modifier.width(120.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("单位", color = C.Text, fontSize = 15.sp)
            Segmented(DateLogic.Unit.entries.map { it.name to it.label }, unit.name, Modifier.fillMaxWidth()) { unit = DateLogic.Unit.valueOf(it) }
        }
    }
    ResultCard {
        Text("$base ${if (dir == "after") "往后" else "往前"} $n ${unit.name2} 是", color = C.Muted, fontSize = 13.sp)
        if (result == null || result.year !in 1..9999) {
            Text("超出可计算范围", color = C.Warn, fontSize = 16.sp)
        } else {
            Text(result.toString(), color = C.Gold, fontSize = 34.sp, fontFamily = Mono)
            Text("${dateSub(result)} · 农历 ${lunarText(result)}", color = C.Text, fontSize = 15.sp)
            val note = when (unit) {
                DateLogic.Unit.DAY -> "按自然日计算。"
                DateLogic.Unit.WEEK -> "1 周 = 7 天。"
                DateLogic.Unit.MONTH -> "按月推算；若目标月没有对应日期（如 1 月 31 日加 1 个月），取该月最后一天。"
                DateLogic.Unit.WORKDAY -> uncoveredNote(Workdays.uncoveredYears(base, result))
            }
            Text(note, color = C.Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

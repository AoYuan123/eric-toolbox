package com.eric.toolbox.modules.family

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eric.toolbox.core.Notifier
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.TopBar
import java.time.LocalDate

/**
 * 时间表。全屏折页：当前这一章展开，后面的只露标题和还剩几项。
 *
 * 刻意的取舍：
 *  - 不显示"你落后了几项"。已经过期的条目只是变灰，不红、不报数、不弹窗。
 *  - 划掉就是划掉，没有撤销、也没有确认弹窗——这是给自己看的，不是交给别人的作业。
 *  - 每一章一句"这段在忙什么"，比一串日期有用。
 */
@Composable
fun FamilyPlanScreen(
    edd: LocalDate?,
    birth: LocalDate?,
    done: Set<String>,
    onToggleDone: (String, Boolean) -> Unit,
    onSetup: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val today = FamilyDate.today()
    val rows = remember(edd, birth, done) { FamilyPlan.rows(edd, birth, done) }
    val groups = remember(rows) { FamilyPlan.groups(rows) }

    // 一进来先展开"下一件没做的事"所在的章，而不是第一章。
    val initial = remember(rows) {
        val nextCh = rows.firstOrNull { !it.done && it.due >= today }?.let { FamilyPlan.chapterOf(it.item) }
        nextCh ?: groups.lastOrNull()?.first
    }
    var open by remember { mutableStateOf(setOfNotNull(initial)) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(C.Bg)) {
            TopBar("时间表", onClose) {
                Text(
                    if (rows.isEmpty()) "" else "共 ${rows.size} 项",
                    color = C.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }

            if (rows.isEmpty()) {
                EmptyPlan(onSetup)
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
                    item(key = "start") { StartRow(edd, birth, onSetup) }
                    item(key = "remind") { ReminderRow(ctx) }

                    groups.forEach { (ch, list) ->
                        val isOpen = ch in open
                        item(key = "ch_${ch.name}") {
                            ChapterHeader(
                                chapter = ch,
                                rows = list,
                                open = isOpen,
                                onToggle = { open = if (isOpen) open - ch else open + ch },
                            )
                        }
                        if (isOpen) {
                            items(list, key = { it.item.key }) { row ->
                                PlanLine(
                                    row = row,
                                    today = today,
                                    onToggle = { onToggleDone(row.item.key, !row.done) },
                                    onCalendar = { addToCalendar(ctx, row) },
                                )
                            }
                        }
                    }

                    item(key = "foot") { FootNote() }
                }
            }
        }
    }
}

@Composable
private fun EmptyPlan(onSetup: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("先定一个起点", color = C.Text, fontSize = 15.sp)
        Text(
            "填了预产期，这张表才知道每一项排在哪天。",
            color = C.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.clip(RoundedCornerShape(24.dp)).background(C.Surface)
                .clickable(onClick = onSetup).padding(horizontal = 20.dp, vertical = 10.dp),
        ) { Text("去设置", color = C.Gold, fontSize = 13.sp) }
    }
}

@Composable
private fun StartRow(edd: LocalDate?, birth: LocalDate?, onSetup: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onSetup)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("绳子的起点", color = C.Text, fontSize = 14.sp)
                val line = when {
                    birth != null -> "生日 ${FamilyDate.long(birth)}"
                    edd != null -> "预产期 ${FamilyDate.long(edd)} · 还没出生"
                    else -> "还没设置"
                }
                Text(line, color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Text("改", color = C.Muted, fontSize = 12.sp)
        }
        Divider2()
    }
}

@Composable
private fun ReminderRow(ctx: Context) {
    var on by remember { mutableStateOf(FamilyReminder.enabled(ctx)) }
    var canPost by remember { mutableStateOf(Notifier.canPost(ctx)) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("每天早上一声", color = C.Text, fontSize = 14.sp)
                Text(
                    "只在有产检、疫苗、儿保的日子提醒",
                    color = C.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Switch(checked = on, onCheckedChange = {
                on = it
                FamilyReminder.setEnabled(ctx, it)
            })
        }

        val hintMod = Modifier.padding(start = 20.dp, bottom = 12.dp)
        if (on && !canPost) {
            Text(
                "系统里还没允许通知，点这里去开",
                color = C.Warn,
                fontSize = 12.sp,
                modifier = hintMod.clickable {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        } else if (on) {
            Text(
                "发一条试试",
                color = C.Muted.copy(alpha = 0.75f),
                fontSize = 12.sp,
                modifier = hintMod.clickable {
                    FamilyReminder.test(ctx)
                    canPost = Notifier.canPost(ctx)
                },
            )
        }
        Divider2()
    }
}

@Composable
private fun ChapterHeader(
    chapter: Chapter,
    rows: List<PlanRow>,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val left = rows.count { !it.done }
    val next = rows.firstOrNull { !it.done }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chapter.title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (left == 0) "都做完了" else "$left 项",
                    color = C.Muted.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(if (open) "收起" else "展开", color = C.Muted, fontSize = 11.sp)
            }
            if (!open) {
                Text(
                    next?.let { "${FamilyDate.short(it.due)} · ${it.item.title}" } ?: chapter.hint,
                    color = C.Muted.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        Divider2()
    }
}

@Composable
private fun PlanLine(
    row: PlanRow,
    today: LocalDate,
    onToggle: () -> Unit,
    onCalendar: () -> Unit,
) {
    val past = !row.done && row.due.isBefore(today)

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.size(36.dp).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) { PlanDot(row.item.weight, row.done, past) }

            Column(Modifier.weight(1f).padding(top = 6.dp, end = 8.dp)) {
                Text(
                    row.item.title,
                    color = if (row.done) C.Muted.copy(alpha = 0.55f) else C.Text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textDecoration = if (row.done) TextDecoration.LineThrough else null,
                )
                Text(
                    row.item.note,
                    color = if (row.done) C.Muted.copy(alpha = 0.45f) else C.Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(FamilyDate.short(row.due), color = C.Muted.copy(alpha = 0.8f), fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (row.done) "记下了" else FamilyPlan.daysLabel(row.due, today),
                        color = when {
                            row.done -> C.Muted.copy(alpha = 0.5f)
                            row.due == today -> C.Gold
                            past -> C.Muted.copy(alpha = 0.5f)
                            else -> C.Muted
                        },
                        fontSize = 11.sp,
                    )
                }
            }

            Box(
                Modifier.size(36.dp).clickable(onClick = onCalendar),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    "加进日历",
                    tint = C.Muted.copy(alpha = 0.6f),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Divider2(leading = 56.dp)
    }
}

@Composable
private fun FootNote() {
    Text(
        "这些是国家规范里的产检、儿保和免疫规划，具体以你建档医院的安排为准。\n" +
            "做不了就划掉，不会一直催。生日照不用这里提醒——那天长按绳子挂一张就行。",
        color = C.Muted.copy(alpha = 0.6f),
        fontSize = 11.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
    )
}

/**
 * 三种点：实心金=必须做，空心金=建议做，小灰点=知道一下就行。
 * 做完的变成对勾——不跟"过期了"共用样式，免得混成一片灰。
 */
@Composable
private fun PlanDot(weight: Int, done: Boolean, past: Boolean) {
    Canvas(Modifier.size(17.dp)) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        when {
            done -> {
                drawCircle(C.Muted.copy(alpha = 0.35f), r * 0.9f, c)
                val p = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.45f, size.height * 0.68f)
                    lineTo(size.width * 0.74f, size.height * 0.34f)
                }
                drawPath(p, C.Bg, style = Stroke(width = 1.6.dp.toPx()))
            }
            weight == FamilyPlan.MUST -> drawCircle(
                if (past) C.Gold.copy(alpha = 0.45f) else C.Gold, r * 0.62f, c,
            )
            weight == FamilyPlan.SHOULD -> drawCircle(
                if (past) C.Gold.copy(alpha = 0.4f) else C.Gold.copy(alpha = 0.8f), r * 0.6f, c,
                style = Stroke(width = 1.2.dp.toPx()),
            )
            else -> drawCircle(C.Muted.copy(alpha = 0.35f), r * 0.28f, c)
        }
    }
}

@Composable
private fun Divider2(leading: Dp = 20.dp) {
    Box(
        Modifier.fillMaxWidth().padding(start = leading)
            .height(0.5.dp).background(C.Line.copy(alpha = 0.5f)),
    )
}

/** 系统日历的新建事件界面，预填标题、日期和那句解释。不需要任何日历权限。 */
private fun addToCalendar(ctx: Context, row: PlanRow) {
    val begin = FamilyDate.atDay(row.due)
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, row.item.title)
        putExtra(CalendarContract.Events.DESCRIPTION, row.item.note)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 3_600_000L)
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(intent) }.onFailure {
        Toast.makeText(ctx, "这台手机上没找到日历", Toast.LENGTH_SHORT).show()
    }
}

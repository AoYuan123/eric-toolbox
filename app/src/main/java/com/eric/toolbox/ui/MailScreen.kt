package com.eric.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.core.MailConfig
import com.eric.toolbox.core.MailRecipient
import com.eric.toolbox.core.Mailer
import kotlinx.coroutines.launch

/** 我的 › 邮件提醒。 */
@Composable
fun MailScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val cfg = remember(tick) { Mailer.config(ctx) }
    var from by remember { mutableStateOf(cfg.from) }
    var code by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Int?>(null) } // -1 = 新增
    var testMsg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun save(c: MailConfig) { Mailer.save(ctx, c); tick++ }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar("邮件提醒", onBack)
        val ready = remember(tick) { Mailer.ready(ctx) }
        val status = remember(tick, testMsg) { Mailer.lastStatus(ctx) }
        val (fg, bg) = when {
            ready && status?.contains("失败") != true -> C.Ok to C.OkBg
            ready -> C.Warn to C.WarnBg
            else -> C.Muted to C.Surface
        }
        Row(
            Modifier.fillMaxWidth().background(bg, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).background(fg, CircleShape))
            Text(
                when {
                    !cfg.enabled -> "未开启"
                    !ready -> "已开启，但还没填完（邮箱、授权码、收件人）"
                    else -> "已开启" + (status?.let { " · $it" } ?: "")
                },
                color = fg, fontSize = 13.sp,
            )
        }

        ListCard {
            ToggleRow("发邮件提醒", "提醒同时发到邮箱", cfg.enabled) { save(cfg.copy(enabled = it)) }
            ToggleRow("同时弹手机通知", "关掉后只发邮件（邮件设置没填完时仍会弹通知）", cfg.alsoNotify) { save(cfg.copy(alsoNotify = it)) }
        }

        SectionLabel("发件邮箱（QQ 邮箱）")
        ListCard {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    from, { from = it.trim() }, label = { Text("QQ 邮箱") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("如 123456789@qq.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = from.isNotBlank() && !Mailer.isEmail(from),
                )
                OutlinedTextField(
                    code, { code = it }, label = { Text(if (Mailer.hasAuthCode(ctx)) "授权码（已保存，重新填写可替换）" else "授权码") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("16 位，不是 QQ 密码") },
                )
                Text(
                    "保存", color = C.Gold, fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        if (Mailer.isEmail(from)) save(cfg.copy(from = from))
                        if (code.isNotBlank()) { Mailer.setAuthCode(ctx, code); code = ""; tick++ }
                        testMsg = if (Mailer.isEmail(from)) "已保存" else "邮箱格式不对"
                    }.padding(vertical = 6.dp),
                )
            }
        }
        Text(
            "授权码在 QQ 邮箱网页版「设置 › 账户」开启 IMAP/SMTP 服务后生成。只保存在本机，备份导出时不包含。",
            color = C.Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp),
        )

        SectionLabel("收件人（可以加多个，每人单独选收哪些提醒）")
        if (cfg.recipients.isNotEmpty()) ListCard {
            cfg.recipients.forEachIndexed { i, r ->
                Column(
                    Modifier.fillMaxWidth().clickable { editing = i }.padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.address, color = C.Text, fontSize = 14.sp, fontFamily = Mono, modifier = Modifier.weight(1f))
                        Text((r.note.ifBlank { "" }) + " ›", color = C.Muted, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Mailer.CATEGORIES.forEach { (k, name) -> CatChip(name, k in r.categories) {} }
                    }
                }
                if (i < cfg.recipients.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
            }
        }
        Text(
            "＋ 添加收件人", color = C.Gold, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().border(1.dp, C.Line, RoundedCornerShape(14.dp)).clickable { editing = -1 }.padding(12.dp),
        )

        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, C.Line, RoundedCornerShape(12.dp))
                .clickable(enabled = !busy) {
                    busy = true; testMsg = "正在发送…"
                    scope.launch { testMsg = Mailer.sendTest(ctx); busy = false; tick++ }
                },
            contentAlignment = Alignment.Center,
        ) { Text(if (busy) "正在发送…" else "发一封测试邮件", color = C.Text, fontSize = 15.sp) }
        testMsg?.let { Text(it, color = C.Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp)) }

        Text(
            "· 和手机通知一样，只在提醒时段发送（金价、临床试验各自按时段规则）。\n" +
                "· 每个收件人单独发一封，彼此看不到对方地址。\n" +
                "· 发送失败会自动重试 3 次，仍失败就记进「记录」页。\n" +
                "· 手机上装 QQ 邮箱 App 并开启新邮件通知，就能第一时间看到。",
            color = C.Muted, fontSize = 12.sp, lineHeight = 19.sp,
            modifier = Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(14.dp)).padding(14.dp),
        )
        Box(Modifier.height(16.dp))
    }

    editing?.let { idx ->
        val orig = cfg.recipients.getOrNull(idx)
        RecipientDialog(
            orig,
            onDismiss = { editing = null },
            onDelete = if (orig != null) ({ save(cfg.copy(recipients = cfg.recipients.filterIndexed { i, _ -> i != idx })); editing = null }) else null,
        ) { r ->
            val list = cfg.recipients.toMutableList()
            if (orig != null) list[idx] = r else list += r
            save(cfg.copy(recipients = list)); editing = null
        }
    }
}

@Composable
private fun CatChip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, color = if (on) (if (C.palette.isLight) Color.White else C.Bg) else C.Muted, fontSize = 12.sp,
        modifier = Modifier.background(if (on) C.Gold else C.Bg, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun RecipientDialog(orig: MailRecipient?, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (MailRecipient) -> Unit) {
    var addr by remember { mutableStateOf(orig?.address ?: "") }
    var note by remember { mutableStateOf(orig?.note ?: "") }
    var cats by remember { mutableStateOf(orig?.categories ?: Mailer.CATEGORIES.keys.toSet()) }
    val ok = Mailer.isEmail(addr)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (orig == null) "添加收件人" else "修改收件人") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    addr, { addr = it.trim() }, label = { Text("邮箱地址") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), isError = addr.isNotBlank() && !ok,
                )
                OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, singleLine = true)
                Text("收哪些提醒", color = C.Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Mailer.CATEGORIES.forEach { (k, name) ->
                        CatChip(name, k in cats) { cats = if (k in cats) cats - k else cats + k }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onSave(MailRecipient(addr, note.trim(), cats)) }) { Text("保存") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("删除", color = C.Warn) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

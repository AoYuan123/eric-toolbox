package com.eric.toolbox.modules.vod

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.modules.vod.core.ConfigRepo
import com.eric.toolbox.modules.vod.core.VodPrefs
import com.eric.toolbox.modules.vod.model.Episode
import com.eric.toolbox.modules.vod.model.Subscription
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.ListCard
import com.eric.toolbox.ui.RowItem
import com.eric.toolbox.ui.SectionLabel
import com.eric.toolbox.ui.ToggleRow
import com.eric.toolbox.ui.TopBar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 「我的」页：订阅入口、TMDB 配置、解析开关、播放记录。 */
@Composable
fun MinePane(onOpenSubs: () -> Unit) {
    val ctx = LocalContext.current
    val v by VodModule.state.collectAsState()
    val hasKey = VodPrefs.tmdbKey(ctx).isNotBlank()

    var tmdbInput by remember { mutableStateOf(VodPrefs.tmdbKey(ctx)) }
    var parse by remember { mutableStateOf(VodPrefs.useParser(ctx)) }
    var history by remember { mutableStateOf(VodPrefs.history(ctx)) }
    var note by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ListCard {
            RowItem("订阅管理", "", "添加 / 刷新 / 删除配置源", onOpenSubs)
        }
        if (v.staleSubIds.isNotEmpty()) {
            Text(
                "有 ${v.staleSubIds.size} 个订阅还没拉到内容，去订阅页点刷新",
                color = C.Warn, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Text(
            "App 本身不含任何内容。影视数据来自你自己导入的配置，请先在两个地方都确认合法性。",
            color = C.Muted, fontSize = 12.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        SectionLabel("元数据（可选）")
        ListCard {
            OutlinedTextField(
                tmdbInput, { tmdbInput = it },
                label = { Text("TMDB API Key") },
                placeholder = { Text(if (hasKey) "已配置，留空则不改" else "留空则不用 TMDB 海报") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Row(Modifier.padding(bottom = 8.dp)) {
                Button(
                    onClick = {
                        VodPrefs.setTmdbKey(ctx, tmdbInput)
                        note = "已保存"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Bg),
                ) { Text("保存") }
                Spacer(Modifier.width(10.dp))
                if (note.isNotBlank()) {
                    Text(note, color = C.Ok, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
            ToggleRow("线路打不开时用解析器", "关闭后始终直接播放原始地址", parse) {
                parse = it
                VodPrefs.setUseParser(ctx, it)
            }
        }

        SectionLabel("播放记录")
        if (history.isEmpty()) {
            EmptyHint("还没有播放记录")
        } else {
            ListCard {
                history.forEach { h ->
                    RowItem(
                        h.title, h.episodeName,
                        "${fmtTime(h.updatedAt)} · ${h.sourceName}",
                    ) {
                        PlayerActivity.start(
                            ctx, h.title, h.pic, h.sourceName, "", "",
                            listOf(Episode(h.episodeName, h.url)), 0,
                        )
                    }
                }
            }
            Row {
                TextButton("清空记录") {
                    VodPrefs.clearHistory(ctx)
                    history = emptyList()
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 订阅管理页：远程订阅的增删改 + 本地配置粘贴导入。 */
@Composable
fun SubscriptionScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var subs by remember { mutableStateOf(ConfigRepo.subscriptions(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var localMode by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf("") }
    var urlText by remember { mutableStateOf("") }
    var localJson by remember { mutableStateOf("") }

    fun add(site: Subscription) {
        ConfigRepo.upsert(ctx, site)
        subs = ConfigRepo.subscriptions(ctx)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("订阅", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (subs.isEmpty()) {
                EmptyHint("一份订阅都没有。\n在下面填一个远程地址，或者切到本地粘贴。")
            } else {
                SectionLabel("已添加的订阅 ${subs.size}")
                ListCard {
                    subs.forEach { s ->
                        SubscriptionRow(
                            s = s,
                            onToggle = { on -> add(s.copy(enabled = on)) },
                            onRefresh = {
                                scope.launch {
                                    busy = true
                                    note = ""
                                    val r = ConfigRepo.fetchRemote(ctx, s)
                                    note = if (r.isSuccess) {
                                        "「${s.name}」${r.getOrNull()?.sites?.size ?: 0} 个站点"
                                    } else {
                                        "拉取失败：${r.exceptionOrNull()?.message ?: "未知错误"}"
                                    }
                                    subs = ConfigRepo.subscriptions(ctx)
                                    busy = false
                                }
                            },
                            onDelete = {
                                ConfigRepo.remove(ctx, s.id)
                                subs = ConfigRepo.subscriptions(ctx)
                            },
                        )
                    }
                }
            }

            if (busy) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), color = C.Gold, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在拉取", color = C.Muted, fontSize = 13.sp)
                }
            }
            if (note.isNotBlank()) {
                Text(note, color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }

            SectionLabel(if (localMode) "粘贴本地配置" else "添加远程订阅")
            if (localMode) {
                OutlinedTextField(
                    localJson, { localJson = it },
                    label = { Text("配置 JSON 原文") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
                Button(
                    onClick = {
                        if (localJson.isBlank()) {
                            note = "内容是空的"
                            return@Button
                        }
                        val result = runCatching { ConfigRepo.parseCheck(localJson) }
                        val info = result.getOrNull()
                        if (info == null) {
                            note = "解析失败：${result.exceptionOrNull()?.message ?: "格式不对"}"
                            return@Button
                        }
                        add(
                            Subscription(
                                id = "local_" + System.currentTimeMillis(),
                                name = nameText.trim().ifBlank { "本地导入" },
                                rawJson = localJson.trim(),
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                        note = "已导入，站点 ${info.first} 个，直播 ${info.second} 条"
                        localJson = ""
                        nameText = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Bg),
                ) {
                    Icon(Icons.Filled.ContentPaste, null, tint = C.Bg)
                    Spacer(Modifier.width(6.dp))
                    Text("导入", color = C.Bg)
                }
            } else {
                OutlinedTextField(
                    nameText, { nameText = it },
                    label = { Text("名称（可留空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    urlText, { urlText = it },
                    label = { Text("配置地址") },
                    placeholder = { Text("https://.../config.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val u = urlText.trim()
                        if (!u.startsWith("https://")) {
                            note = "地址必须是 https 开头"
                            return@Button
                        }
                        val sub = Subscription(
                            id = "sub_" + System.currentTimeMillis(),
                            name = nameText.trim().ifBlank { u },
                            url = u,
                        )
                        add(sub)
                        scope.launch {
                            busy = true
                            val r = ConfigRepo.fetchRemote(ctx, sub)
                            note = if (r.isSuccess) {
                                "「${sub.name}」${r.getOrNull()?.sites?.size ?: 0} 个站点已加载"
                            } else {
                                "拉取失败：${r.exceptionOrNull()?.message ?: "未知错误"}"
                            }
                            subs = ConfigRepo.subscriptions(ctx)
                            busy = false
                        }
                        urlText = ""
                        nameText = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Bg),
                ) {
                    Icon(Icons.Filled.Add, null, tint = C.Bg)
                    Spacer(Modifier.width(6.dp))
                    Text("添加并拉取", color = C.Bg)
                }
            }
            Row {
                TextButton(if (localMode) "切到远程订阅" else "切到本地粘贴") { localMode = !localMode }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SubscriptionRow(
    s: Subscription,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.name.ifBlank { s.id }, color = C.Text, fontSize = 15.sp, maxLines = 1)
            Text(
                when {
                    s.lastError.isNotBlank() -> s.lastError
                    s.isRemote -> s.url
                    else -> "本地导入 · ${s.rawJson.length} 字符"
                },
                color = if (s.lastError.isNotBlank()) C.Warn else C.Muted,
                fontSize = 11.sp, maxLines = 1,
            )
        }
        Switch(
            checked = s.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = C.Gold, checkedThumbColor = Color.White),
        )
        if (s.isRemote) {
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "刷新", tint = C.Muted) }
        } else {
            IconButton(onClick = { }) { Icon(Icons.Filled.Link, "本地", tint = C.Muted.copy(alpha = 0.4f)) }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除", tint = C.Warn) }
    }
}

/** 轻量的文字按钮，跟整体风格一致，不想再引一层 Material 组件。 */
@Composable
private fun TextButton(text: String, onClick: () -> Unit) {
    Text(
        text, color = C.Muted, fontSize = 13.sp,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun fmtTime(ms: Long): String =
    if (ms <= 0L) "" else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ms))

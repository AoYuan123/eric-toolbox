package com.eric.toolbox.modules.pan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.modules.pan.core.CloudType
import com.eric.toolbox.modules.pan.model.PanItem
import com.eric.toolbox.modules.pan.model.VerifyState
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.CardBox
import com.eric.toolbox.ui.Mono
import com.eric.toolbox.ui.Pill
import com.eric.toolbox.ui.SectionLabel
import com.eric.toolbox.ui.Segmented
import com.eric.toolbox.ui.ToggleRow
import com.eric.toolbox.ui.TopBar
import kotlinx.coroutines.launch

/** 网盘筛选项：key 为空串表示「全部」 */
private val CLOUD_TABS = listOf(
    "" to "全部",
    "quark" to "夸克",
    "baidu" to "百度",
    "aliyun" to "阿里",
    "xunlei" to "迅雷",
    "uc" to "UC",
    "115" to "115",
)

@Composable
fun PanRoot(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val st by PanModule.state.collectAsState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(PanModule.lastKeyword(ctx)) }
    var showSettings by remember { mutableStateOf(false) }
    // 这些是 SharedPreferences 里的设置，读写都顺手 bump 一下 state.version
    val ver = st.version
    val selected = remember(ver) { PanModule.cloudFilter(ctx) }
    val engines = remember(ver) { PanModule.engines(ctx) }
    val limit = remember(ver) { PanModule.limitPerCloud(ctx) }
    val dups = remember(ver) { PanModule.allDups(ctx) }
    val cooling = remember(ver, st.done) { PanModule.coolingEngines(ctx) }

    val doSearch: () -> Unit = {
        if (input.isNotBlank() && !st.loading) {
            scope.launch { PanModule.search(ctx, input) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("网盘搜片", onBack, trailing = {
            IconButton(onClick = { showSettings = !showSettings }) {
                Icon(Icons.Filled.Tune, "设置", tint = if (showSettings) C.Gold else C.Muted)
            }
        })

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---------------- 搜索框 ----------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("片名 / 关键词") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                )
                Box(Modifier.width(8.dp))
                Button(onClick = doSearch, enabled = !st.loading) {
                    Text(if (st.loading) "搜…" else "搜索")
                }
            }

            // ---------------- 网盘筛选 ----------------
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CLOUD_TABS.forEach { (key, label) ->
                    val on = if (key.isEmpty()) selected.isEmpty() else key in selected
                    Box(
                        Modifier.background(if (on) C.Gold else C.Surface, RoundedCornerShape(50))
                            .clickable {
                                PanModule.setCloudFilter(
                                    ctx,
                                    when {
                                        key.isEmpty() -> emptySet()
                                        on -> selected - key
                                        else -> selected + key
                                    },
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            color = if (on) (if (C.palette.isLight) androidx.compose.ui.graphics.Color.White else C.Bg) else C.Muted,
                        )
                    }
                }
            }

            if (showSettings) SettingsCard(engines, limit, dups)

            // ---------------- 状态行 ----------------
            if (st.loading) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = C.Gold, strokeWidth = 2.dp)
                    Text("  正在向 ${engines.size} 个源查询…", color = C.Muted, fontSize = 12.sp)
                }
            } else if (st.done) {
                Text(
                    buildString {
                        append("共 ${st.items.size} 条")
                        if (st.variant.isNotBlank()) append(" · 换了关键词「${st.variant}」才搜到")
                        if (st.skipped.isNotEmpty()) append(" · 跳过 ${st.skipped.joinToString("/")}（冷却中）")
                    },
                    color = C.Muted, fontSize = 12.sp,
                )
            }
            if (cooling.isNotEmpty() && !st.loading) {
                Text("这些源正在风控冷却：${cooling.joinToString("/")}", color = C.Warn, fontSize = 12.sp)
            }
            if (st.error.isNotBlank()) Text(st.error, color = C.Warn, fontSize = 13.sp)

            // ---------------- 结果 ----------------
            if (st.items.isEmpty() && st.done && !st.loading) {
                EmptyHint(st.errors)
            }

            for ((cloud, list) in st.items.groupBy { it.cloud }.toList().sortedBy { CloudType.orderIndex(it.first) }) {
                SectionLabel("${CloudType.displayName(cloud)} · ${list.size} 条")
                list.forEach { item ->
                    ItemCard(item)
                }
            }

            // ---------------- 底部：各源耗时 / 报错 ----------------
            if (st.done && (st.elapsed.isNotEmpty() || st.errors.isNotEmpty())) {
                FootNote(st.elapsed, st.errors)
            }
            Box(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun SettingsCard(engines: List<String>, limit: Int, dups: Boolean) {
    val ctx = LocalContext.current
    CardBox {
        Text("数据源", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        PanModule.ENGINE_CHOICES.forEach { (key, name, desc) ->
            ToggleRow(
                title = name,
                sub = desc,
                checked = key in engines,
                onChange = { on ->
                    val next = if (on) engines + key else engines - key
                    // 至少留一个源，否则搜出来永远是空的
                    if (next.isNotEmpty()) PanModule.setEngines(ctx, next)
                },
            )
        }
        Text("每个网盘最多保留", color = C.Text, fontSize = 15.sp)
        Segmented(
            options = listOf("5" to "5 条", "8" to "8 条", "12" to "12 条"),
            selected = limit.toString(),
            modifier = Modifier.fillMaxWidth(),
        ) { PanModule.setLimit(ctx, it.toInt()) }
        ToggleRow(
            title = "展开全部副本",
            sub = "关闭时同名资源折叠成一条，展开能看到同资源的不同链接",
            checked = dups,
            onChange = { PanModule.setAllDups(ctx, it) },
        )
    }
}

@Composable
private fun EmptyHint(errors: List<String>) {
    CardBox {
        Text("没搜到结果", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(
            "可以试试：换更短的关键词 / 只留片名不带年份 / 在右上角多勾几个源 / 稍后再搜（有的源会限流）。",
            color = C.Muted, fontSize = 13.sp,
        )
        if (errors.isNotEmpty()) {
            Text("各源返回：", color = C.Muted, fontSize = 12.sp)
            errors.take(4).forEach { Text("· $it", color = C.Warn, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun FootNote(elapsed: Map<String, Long>, errors: List<String>) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (elapsed.isNotEmpty()) {
            Text(
                elapsed.entries.joinToString("  ") { "${it.key} ${it.value}ms" },
                color = C.Muted, fontSize = 11.sp, fontFamily = Mono,
            )
        }
        errors.take(3).forEach { Text("! $it", color = C.Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun ItemCard(item: PanItem) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxWidth()
            .background(C.Surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            item.note.ifBlank { CloudType.displayName(item.cloud) },
            color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.datetime.isNotBlank()) Meta(item.datetime)
            if (item.sizeText().isNotBlank()) Meta(item.sizeText())
            if (item.files > 0) Meta("${item.files} 个文件")
            if (item.hot) Meta("热门")
            if (item.password.isNotBlank()) Meta("码 ${item.password}")
            if (item.dups.isNotEmpty()) Meta("另有 ${item.dups.size} 个")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VerifyPill(item.verify)
            Text(item.source, color = C.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                "复制",
                color = C.Gold, fontSize = 13.sp, modifier = Modifier.clickable {
                    copy(ctx, buildString {
                        append(item.url)
                        if (item.password.isNotBlank()) append("\n提取码：").append(item.password)
                    })
                },
            )
            Text(
                "打开",
                color = C.Gold, fontSize = 13.sp, modifier = Modifier.clickable { open(ctx, item.url) },
            )
            if (CloudType.supportsVerify(item.cloud)) {
                Text(
                    if (item.verify == VerifyState.UNCHECKED) "核验" else "重验",
                    color = C.Muted, fontSize = 13.sp,
                    modifier = Modifier.clickable { scope.launch { PanModule.verify(item) } },
                )
            }
        }
    }
}

@Composable
private fun Meta(text: String) {
    Text(text, color = C.Muted, fontSize = 11.sp)
}

@Composable
private fun VerifyPill(state: VerifyState) {
    val (fg, bg) = when (state) {
        VerifyState.OK -> C.Ok to C.OkBg
        VerifyState.LOCKED -> C.Gold to C.GoldBg
        VerifyState.SUSPECT, VerifyState.BAD -> C.Warn to C.WarnBg
        VerifyState.UNCHECKED -> C.Muted to C.Surface
    }
    Pill(state.label, fg, bg)
}

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("网盘链接", text))
    Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
}

private fun open(ctx: Context, url: String) {
    if (url.isBlank()) return
    val ok = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!ok) Toast.makeText(ctx, "没有能打开这个链接的应用", Toast.LENGTH_SHORT).show()
}

package com.eric.toolbox.modules.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.modules.vod.core.QuickAdd
import com.eric.toolbox.modules.vod.model.VodSite
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.ListCard
import com.eric.toolbox.ui.SectionLabel
import com.eric.toolbox.ui.TopBar
import kotlinx.coroutines.launch

/**
 * 添加影视接口。
 *
 * 这一页存在的理由：影视模块原来只有「导入整份配置」一条路，
 * 而流传的配置多半是全 Spider，导进去等于白导。
 * 这里改成粘一条链接、当场告诉你能不能搜、能搜就加进来。
 */
@Composable
fun QuickAddScreen(onBack: () -> Unit, onChanged: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<QuickAdd.Result?>(null) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var manual by remember { mutableStateOf(QuickAdd.manualSites(ctx)) }
    var note by remember { mutableStateOf("") }

    fun detect() {
        val t = input.trim()
        if (t.isBlank()) {
            note = "先粘一个地址"
            return
        }
        scope.launch {
            busy = true
            note = ""
            result = null
            picked = emptySet()
            val r = QuickAdd.sniff(t)
            result = r
            // 淘金结果默认把实测通过的都勾上，用户只需要取消不想要的
            if (r is QuickAdd.Result.Harvest) {
                picked = r.candidates.filter { it.ok }.map { it.site.key }.toSet()
            }
            busy = false
        }
    }

    fun save(list: List<VodSite>) {
        list.forEach { QuickAdd.addSite(ctx, it) }
        manual = QuickAdd.manualSites(ctx)
        note = if (list.size == 1) "已添加「${list[0].name}」" else "已添加 ${list.size} 个站点"
        onChanged()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("添加影视接口", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                input,
                { input = it },
                label = { Text("粘贴接口地址或配置链接") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth().height(84.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { detect() },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Bg),
                ) { Text(if (busy) "检测中" else "检测") }

                Text(
                    "粘贴",
                    color = C.Muted, fontSize = 13.sp,
                    modifier = Modifier
                        .clickable {
                            val t = clipboard.getText()?.text.orEmpty()
                            if (t.isNotBlank()) input = t
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )

                if (busy) {
                    CircularProgressIndicator(
                        Modifier.height(14.dp).width(14.dp),
                        color = C.Gold, strokeWidth = 2.dp,
                    )
                }
            }

            if (note.isNotBlank()) {
                Text(note, color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }

            when (val r = result) {
                null -> Unit
                is QuickAdd.Result.Direct -> {
                    // 提成局部变量：lambda 里靠 smart cast 引用分支变量容易被编译器判成未转型
                    val d = r
                    DirectCard(d) { save(listOf(d.site)) }
                }
                is QuickAdd.Result.Harvest -> {
                    val h = r
                    HarvestBlock(
                        r = h,
                        picked = picked,
                        onToggle = { key ->
                            picked = if (key in picked) picked - key else picked + key
                        },
                        onSaveAll = {
                            save(h.candidates.filter { it.ok && it.site.key in picked }.map { it.site })
                        },
                        onSaveOne = { c -> save(listOf(c.site)) },
                    )
                }
                is QuickAdd.Result.Failed -> FailedCard(r)
            }

            if (manual.isNotEmpty()) {
                SectionLabel("手动添加 ${manual.size} 个")
                ListCard {
                    manual.forEachIndexed { i, s ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.Line))
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, color = C.Text, fontSize = 14.sp)
                                Text(hostOf(s), color = C.Muted, fontSize = 11.sp, maxLines = 1)
                            }
                            Text(
                                "删除", color = C.Muted, fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable {
                                        QuickAdd.removeSite(ctx, s.key)
                                        manual = QuickAdd.manualSites(ctx)
                                        note = "已删除「${s.name}」"
                                        onChanged()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "能接什么",
                    color = C.Muted, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                HintText("标准接口：形如 https://xxx.com/api.php/provide/vod，粘进来就能搜能播")
                HintText("整份配置：影视仓「接口设置」里那个地址，会把里面能直连的挑出来")
                HintText("Spider 脚本：需要下载并执行远程代码，本 App 不支持，这是安全红线")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ------------------------------------------------------------------ 结果卡片

@Composable
private fun DirectCard(r: QuickAdd.Result.Direct, onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(C.Surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(C.Ok)
            Text(
                if (r.xml) "标准接口 · XML" else "标准接口 · JSON",
                color = C.Ok, fontSize = 12.sp,
            )
        }
        Text(r.site.name, color = C.Text, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            MiniStat(r.hits.toString(), "部影片")
            MiniStat(r.cats.toString(), "个分类")
            MiniStat(if (r.ms > 0) "${r.ms}ms" else "通", "响应")
        }
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = C.GoldBg, contentColor = C.Gold),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("添加为站点") }
    }
}

@Composable
private fun HarvestBlock(
    r: QuickAdd.Result.Harvest,
    picked: Set<String>,
    onToggle: (String) -> Unit,
    onSaveAll: () -> Unit,
    onSaveOne: (QuickAdd.Cand) -> Unit,
) {
    val st = r.stats
    Column(
        Modifier
            .fillMaxWidth()
            .background(C.Surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(if (r.okCount > 0) C.Ok else C.Warn)
            Text("整份配置", color = C.Muted, fontSize = 12.sp)
        }

        // 一行说清总量，避免「导入成功但搜不出东西」被当成 bug
        Text(
            "共 ${st.total} 个站点 · 可直连 ${st.usable} 个 · 实测通 ${r.okCount} 个",
            color = C.Text, fontSize = 14.sp,
        )

        if (st.spider > 0) {
            Text(
                "其中 ${st.spider} 个是 Spider 脚本，要在 App 内执行远程代码，已跳过。",
                color = C.Muted, fontSize = 12.sp, lineHeight = 18.sp,
            )
        }

        if (r.candidates.isEmpty()) {
            Text(
                "这份配置里没有能直连的接口。换一个地址试试，或者直接从影视仓里找 api.php/provide/vod 那种。",
                color = C.Warn, fontSize = 12.sp, lineHeight = 18.sp,
            )
            return@Column
        }

        SectionLabel("可直连的接口")
        r.candidates.forEach { c ->
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(c.site.key) }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckMark(c.site.key in picked && c.ok)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.site.name, color = C.Text, fontSize = 14.sp)
                    Text(
                        if (c.ok) "${hostOf(c.site)} · ${c.hits} 部 · ${c.ms}ms" else "${hostOf(c.site)} · ${c.err}",
                        color = if (c.ok) C.Muted else C.Warn,
                        fontSize = 11.sp,
                    )
                }
                if (!c.ok) {
                    Text(
                        "仍要加", color = C.Muted, fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { onSaveOne(c) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        if (r.okCount > 0) {
            Button(
                onClick = onSaveAll,
                colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Bg),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("添加选中的 ${picked.count { k -> r.candidates.any { it.ok && it.site.key == k } }} 个") }
        }
    }
}

@Composable
private fun FailedCard(r: QuickAdd.Result.Failed) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(C.Surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(C.Warn)
            Text(r.reason, color = C.Warn, fontSize = 13.sp)
        }
        if (r.hint.isNotBlank()) {
            Text(r.hint, color = C.Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

// -------------------------------------------------------------------- 小组件

@Composable
private fun MiniStat(value: String, label: String) {
    Column {
        Text(value, color = C.Text, fontSize = 15.sp)
        Text(label, color = C.Muted, fontSize = 10.sp)
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.height(7.dp).width(7.dp).background(color, RoundedCornerShape(4.dp)))
}

@Composable
private fun CheckMark(on: Boolean) {
    Box(
        Modifier
            .height(18.dp)
            .width(18.dp)
            .background(if (on) C.Gold else C.Surface, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Text("✓", color = C.Bg, fontSize = 11.sp)
    }
}

@Composable
private fun HintText(text: String) {
    Text(text, color = C.Muted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

/** 列表里只显示主机名，完整地址太长会把一行挤爆 */
private fun hostOf(s: VodSite): String =
    s.api.substringAfter("://", s.api).substringBefore("/").ifBlank { s.api }

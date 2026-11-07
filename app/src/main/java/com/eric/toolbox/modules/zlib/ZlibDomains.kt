package com.eric.toolbox.modules.zlib

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.modules.zlib.core.SiteProbe
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.ListCard
import com.eric.toolbox.ui.Pill
import com.eric.toolbox.ui.SectionLabel
import com.eric.toolbox.ui.TopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ZlibDomainsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val v by ZlibModule.state.collectAsState()

    var sites by remember(v.version) { mutableStateOf(ZlibModule.sites(ctx)) }
    var cur by remember(v.version) { mutableStateOf(ZlibModule.current(ctx)) }
    var probes by remember(v.version) { mutableStateOf(ZlibModule.probes(ctx)) }
    var probedAt by remember(v.version) { mutableStateOf(ZlibModule.probedAt(ctx)) }
    var input by remember { mutableStateOf("") }
    var tpl by remember(v.version) { mutableStateOf(ZlibModule.searchTpl(ctx)) }
    var note by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TopBar("站点管理", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().background(C.WarnBg, RoundedCornerShape(12.dp)).padding(12.dp),
            ) {
                Text(
                    "打不开不代表失效。域名会被污染或阻断，换网络环境结论就反过来——所以这里只记「上次能不能连上」，不判死活。",
                    color = C.Warn, fontSize = 11.sp, lineHeight = 17.sp,
                )
            }

            SectionLabel("已添加 ${sites.size} 个")
            ListCard {
                sites.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                SiteProbe.host(s),
                                color = if (s == cur) C.Text else C.Muted,
                                fontSize = 15.sp,
                            )
                            Text(
                                statusText(probes[s], probedAt),
                                color = C.Muted, fontSize = 11.sp,
                            )
                        }
                        if (s == cur) {
                            Pill("使用中", C.Ok, C.OkBg)
                        } else {
                            Text(
                                "切换", color = C.Gold, fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable {
                                        ZlibModule.setCurrent(ctx, s)
                                        cur = s
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        IconButton(onClick = {
                            if (ZlibModule.removeSite(ctx, s)) {
                                sites = ZlibModule.sites(ctx)
                                cur = ZlibModule.current(ctx)
                                note = "已删除，可以重新加回来"
                            } else {
                                note = "至少要留一个站点"
                            }
                        }) { Icon(Icons.Filled.Delete, "删除", tint = C.Warn) }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("粘贴新域名") },
                    placeholder = { Text("z-library.xxx") },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val err = ZlibModule.addSite(ctx, input)
                        if (err == null) {
                            input = ""
                            sites = ZlibModule.sites(ctx)
                            note = "已添加"
                        } else {
                            note = err
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Bg),
                ) { Text("添加") }
            }

            if (note.isNotBlank()) {
                Text(note, color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }

            SectionLabel("高级")
            ListCard {
                OutlinedTextField(
                    value = tpl,
                    onValueChange = { tpl = it },
                    singleLine = true,
                    label = { Text("搜索路径模板") },
                    placeholder = { Text("/s/{q}") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            ZlibModule.setSearchTpl(ctx, tpl)
                            tpl = ZlibModule.searchTpl(ctx)
                            note = "已保存"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Bg),
                    ) { Text("保存") }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "试搜「三体」", color = C.Gold, fontSize = 13.sp,
                        modifier = Modifier
                            .clickable {
                                ZlibModule.setSearchTpl(ctx, tpl)
                                tpl = ZlibModule.searchTpl(ctx)
                                if (!ZlibModule.open(ctx, ZlibModule.searchUrl(ctx, "三体"))) {
                                    note = "没找到可用的浏览器"
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
            Text(
                "{q} 会被替换成书名。点「试搜」直接看效果：打开的是搜索结果页就说明模板对，落到首页就换一个再试。",
                color = C.Muted, fontSize = 11.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                "备选：/search?q={q}　/　?s={q}",
                color = C.Muted, fontSize = 11.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                "默认只带 z-library.biz 和 z-library.sk，剩下的自己粘。",
                color = C.Muted, fontSize = 11.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun statusText(st: SiteProbe.State?, probedAt: Long): String = when (st) {
    SiteProbe.State.OK -> "上次测通 · " + fmtShort(probedAt)
    SiteProbe.State.UNREACHABLE -> "上次没连上 · 可能需代理"
    else -> "还没测过"
}

private fun fmtShort(ms: Long): String =
    if (ms <= 0L) "时间未知"
    else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ms))

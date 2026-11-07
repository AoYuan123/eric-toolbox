package com.eric.toolbox.modules.zlib

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.modules.zlib.core.SiteProbe
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.ListCard
import com.eric.toolbox.ui.RowItem
import com.eric.toolbox.ui.SectionLabel
import com.eric.toolbox.ui.TopBar
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun ZlibRoot(onBack: () -> Unit) {
    var page by remember { mutableStateOf("main") }
    when (page) {
        "domains" -> ZlibDomainsScreen(onBack = { page = "main" })
        else -> ZlibMain(onBack = onBack, onOpenDomains = { page = "domains" })
    }
}

@Composable
private fun ZlibMain(onBack: () -> Unit, onOpenDomains: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val v by ZlibModule.state.collectAsState()

    var input by remember { mutableStateOf("") }
    var sites by remember(v.version) { mutableStateOf(ZlibModule.sites(ctx)) }
    var cur by remember(v.version) { mutableStateOf(ZlibModule.current(ctx)) }
    var recent by remember(v.version) { mutableStateOf(ZlibModule.recent(ctx)) }
    var probes by remember(v.version) { mutableStateOf(ZlibModule.probes(ctx)) }
    var pinging by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    fun doSearch(kw: String) {
        val k = kw.trim()
        if (k.isEmpty()) {
            note = "先输入书名或作者"
            return
        }
        ZlibModule.recordSearch(ctx, k)
        recent = ZlibModule.recent(ctx)
        if (!ZlibModule.open(ctx, ZlibModule.searchUrl(ctx, k))) note = "没找到可用的浏览器"
    }

    fun pingAll() {
        if (pinging) return
        scope.launch {
            pinging = true
            note = ""
            val results = coroutineScope {
                sites.map { s -> async { s to SiteProbe.probe(s) } }.awaitAll()
            }
            results.forEach { (s, st) -> ZlibModule.saveProbe(ctx, s, st) }
            probes = ZlibModule.probes(ctx)
            pinging = false
            note = "检测完成。连不上只说明当前网络不通，先确认代理开着。"
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Z-Library", onBack, trailing = {
            Text(
                "${sites.size} 个站点", color = C.Muted, fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
        })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("书名 / 作者") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch(input) }),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { doSearch(input) },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Bg),
                ) { Text("搜书") }
            }

            SectionLabel("当前站点")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sites.forEach { s ->
                    val on = s == cur
                    val st = probes[s] ?: SiteProbe.State.UNKNOWN
                    Row(
                        Modifier.background(if (on) C.Gold else C.Surface, RoundedCornerShape(50))
                            .clickable {
                                ZlibModule.setCurrent(ctx, s)
                                cur = s
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).background(dotColor(st), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            SiteProbe.host(s),
                            color = if (on) (if (C.palette.isLight) Color.White else C.Bg) else C.Text,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            if (probes.isNotEmpty()) {
                Text(
                    "圆点只代表上次检测时能不能连上，灰点照样可以点开。",
                    color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp),
                )
            }

            if (recent.isNotEmpty()) {
                SectionLabel("最近搜过")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recent.forEach { k ->
                        Text(
                            k, color = C.Gold, fontSize = 12.sp,
                            modifier = Modifier.background(C.GoldBg, RoundedCornerShape(50))
                                .clickable { doSearch(k) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }

            if (note.isNotBlank()) {
                Text(note, color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }

            ListCard {
                RowItem("打开首页", sub = "用浏览器打开当前站点", onClick = {
                    if (!ZlibModule.open(ctx, ZlibModule.homeUrl(ctx))) note = "没找到可用的浏览器"
                })
                RowItem(
                    if (pinging) "正在检测…" else "检测连通性",
                    sub = "只看当前网络通不通，不代表站点失效",
                    onClick = { pingAll() },
                )
                RowItem("站点管理", "${sites.size}", "域名会轮换，打不开就换一个", onOpenDomains)
            }

            Text(
                "只提供浏览器入口，不含任何书籍内容。下载请优先选 EPUB，微信读书和 Kindle 都能直接吃。",
                color = C.Muted, fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                "Z-Library 没有唯一官网，域名是一组轮换镜像，所以这里不内置「官方清单」——你存的才是你自己的。",
                color = C.Muted, fontSize = 11.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun dotColor(st: SiteProbe.State): Color = when (st) {
    SiteProbe.State.OK -> C.Ok
    SiteProbe.State.UNREACHABLE -> C.Muted
    SiteProbe.State.UNKNOWN -> C.Line
}

package com.eric.toolbox.modules.vod

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eric.toolbox.modules.vod.core.LineProbe
import com.eric.toolbox.modules.vod.core.ParseResolver
import com.eric.toolbox.modules.vod.core.Quality
import com.eric.toolbox.modules.vod.core.VodPrefs
import com.eric.toolbox.modules.vod.model.Episode
import com.eric.toolbox.modules.vod.model.PlayGroup
import com.eric.toolbox.modules.vod.model.VodItem
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.SectionLabel
import com.eric.toolbox.ui.TopBar
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState

/**
 * 详情页：海报、元信息、线路切换、剧集列表、播放。
 *
 * 列表页拿不到播放串（CMS 的标准行为），所以进来先补一次详情请求；
 * 站点没返回任何播放地址时如实说明，不给用户一个点下去没反应的按钮。
 */
@Composable
fun DetailScreen(item: VodItem, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val v by VodModule.state.collectAsState()

    var full by remember { mutableStateOf(item) }
    var loading by remember { mutableStateOf(item.playUrl.isBlank()) }
    var groupIndex by remember { mutableIntStateOf(0) }
    var useParse by remember { mutableStateOf(VodPrefs.useParser(ctx)) }
    /** 详情没拿到播放串时的真实原因 + 插件原文，直接显示，别让用户猜 */
    var detailError by remember { mutableStateOf("") }
    var rawHead by remember { mutableStateOf("") }
    // 每条线路的实测情况：码率、分辨率、能不能连。探测是异步的，失败不影响播放
    var probes by remember { mutableStateOf<Map<Int, LineProbe.Info>>(emptyMap()) }

    LaunchedEffect(item.id) {
        if (item.playUrl.isBlank()) {
            val r = VodModule.detail(ctx, item)
            full = r.item
            detailError = r.error
            rawHead = r.rawHead
        }
        loading = false
    }

    val groups = remember(full, detailError) { VodModule.playGroups(ctx, full) }
    if (groupIndex > groups.lastIndex) groupIndex = 0

    // 线路定了之后再探测：站点标的清晰度不可信，实测码率才是真的
    LaunchedEffect(groups) {
        if (groups.isEmpty()) return@LaunchedEffect
        probes = emptyMap()
        val referer = VodModule.originFor(ctx, full.siteKey)
        val ua = v.ua
        val out = coroutineScope {
            groups.mapIndexed { i, g ->
                async {
                    val u = g.episodes.firstOrNull()?.url.orEmpty()
                    // 云盘站这一层给的是 token，探测没意义，等播放时换成真地址再算
                    if (!u.startsWith("http")) {
                        i to LineProbe.Info(reachable = true, note = "云盘线路")
                    } else {
                        i to LineProbe.probe(u, ua, referer)
                    }
                }
            }.map { it.await() }
        }
        probes = out.toMap()
    }

    fun start(episodes: List<Episode>, index: Int, sourceName: String) {
        if (episodes.isEmpty()) {
            Toast.makeText(ctx, "没有可播放的地址", Toast.LENGTH_SHORT).show()
            return
        }
        val referer = VodModule.originFor(ctx, full.siteKey)
        val ua = v.ua
        val siteKey = full.siteKey
        val title = full.name
        val poster = full.pic
        scope.launch {
            // Spider 站点这一集给的可能是站内标识，要问一次插件才变成媒体地址
            val ep = episodes.getOrNull(index) ?: return@launch
            val r = VodModule.resolvePlayUrl(ctx, siteKey, sourceName, ep.url)
            if (!r.ok) {
                Toast.makeText(
                    ctx,
                    r.error.ifBlank { "这个站点没有给出可播放的地址" },
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            PlayerActivity.start(
                ctx = ctx,
                title = title,
                pic = poster,
                sourceName = sourceName,
                referer = referer,
                ua = ua,
                siteKey = siteKey,
                playFlag = sourceName,
                // 当前这一集的 token 已经换成真地址，其余等播到再换
                episodes = episodes.toMutableList().also { it[index] = Episode(ep.name, r.url) },
                startIndex = index,
                startHeaders = r.headers,
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(full.name, onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.width(110.dp).aspectRatio(0.72f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(C.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    if (full.pic.isBlank()) {
                        Text(full.name.take(2), color = C.Muted, fontSize = 22.sp)
                    } else {
                        AsyncImage(
                            model = full.pic, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(full.name, color = C.Text, fontSize = 16.sp)
                    MetaLine("清晰度", Quality.of(full).label.ifBlank { "站点未标注" })
                    MetaLine("类型", full.typeName)
                    MetaLine("年份", full.year)
                    MetaLine("地区", full.area)
                    MetaLine("来源", full.siteName)
                }
            }

            if (full.actor.isNotBlank()) SectionLabel("演员")
            if (full.actor.isNotBlank()) Text(full.actor, color = C.Muted, fontSize = 13.sp, lineHeight = 20.sp)

            if (full.content.isNotBlank()) {
                SectionLabel("简介")
                Text(full.content, color = C.Muted, fontSize = 13.sp, lineHeight = 20.sp)
            }

            if (loading) {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), color = C.Gold, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("读取播放地址", color = C.Muted, fontSize = 13.sp)
                }
            } else if (groups.isEmpty()) {
                // 这里以前只有一句「站点没有返回播放地址」，用户只能干瞪眼。
                // 现在把插件给的原话与原文带上，一眼能看出是真没数据还是解析不了。
                EmptyHint(
                    detailError.ifBlank { "这个站点没有返回播放地址。\n换一个站点，或者看看其它同类资源。" },
                )
                if (rawHead.isNotBlank()) {
                    Text(
                        "站点原文：${rawHead.take(200)}",
                        color = C.Muted, fontSize = 10.sp, lineHeight = 14.sp,
                    )
                }
            } else {
                SectionLabel("线路")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 用下标而不是 source 名判选中：站点给出的两条线路同名时会一起高亮
                    itemsIndexed(groups) { i, g ->
                        LineChip(lineLabel(g, probes[i]), i == groupIndex) { groupIndex = i }
                    }
                }

                // 当前线路的实测情况。站点标的「1080P」经常是 1080×608、800kbps，
                // 这里给的是真实数字，换线之前至少知道自己换的是什么
                probes[groupIndex]?.let { info ->
                    Text(
                        "实测：" + info.summary(),
                        color = if (info.kbps in 1..999) C.Warn else C.Muted,
                        fontSize = 12.sp,
                    )
                }

                // 同一部片往往挂着好几条线路，给个一键到最优的那条。
                // 优先按实测码率选：站点标的清晰度靠不住，实测才作数
                val bestIdx = remember(groups, probes) { bestLine(groups, probes) }
                if (bestIdx >= 0 && bestIdx != groupIndex) {
                    Row(
                        Modifier
                            .clickable { groupIndex = bestIdx }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.HighQuality, null, tint = C.Gold,
                            modifier = Modifier.height(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "换到最好的一条：${groups[bestIdx].source}",
                            color = C.Gold, fontSize = 13.sp,
                        )
                    }
                }

                SectionLabel("选集")
                val current = groups.getOrNull(groupIndex)
                if (current != null) {
                    EpisodeGrid(current.episodes) { index ->
                        start(current.episodes, index, current.source)
                    }
                }

                if (useParse && v.parses.isNotEmpty()) {
                    val current2 = groups.getOrNull(groupIndex)
                    val index = 0
                    Button(
                        onClick = {
                            val eps = current2?.episodes ?: return@Button
                            if (eps.isEmpty()) return@Button
                            scope.launch {
                                val rule = v.parses.first()
                                val r = ParseResolver.resolve(rule, eps[index].url, v.timeoutMs)
                                val resolved = r.getOrNull()
                                if (resolved.isNullOrBlank()) {
                                    Toast.makeText(
                                        ctx,
                                        "解析失败：${r.exceptionOrNull()?.message ?: "解析器没返回地址"}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    start(listOf(Episode(eps[index].name, resolved)), 0, rule.name)
                                }
                            }
                            Unit
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = C.Surface, contentColor = C.Text),
                    ) {
                        Icon(Icons.Filled.AutoFixHigh, "解析", tint = C.Gold)
                        Spacer(Modifier.width(6.dp))
                        Text("第一集打不开？用解析器试一次", color = C.Text, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 线路名带上画质，比如「蓝光4K线路 · 4K」。探测到实测数据就一并带上。 */
private fun lineLabel(g: PlayGroup, info: LineProbe.Info?): String {
    val q = Quality.of(g)
    val base = if (q.known) "${g.source} · ${q.label}" else g.source
    return when {
        info == null -> base
        !info.reachable -> "$base · 连不上"
        info.hasBandwidth -> {
            val tag = if (info.kbps >= 1000) {
                String.format(java.util.Locale.US, "%.1fM", info.kbps / 1000.0)
            } else {
                "${info.kbps}k"
            }
            "$base · $tag"
        }
        else -> base
    }
}

/**
 * 选最好的一条线路。
 *
 * 有实测码率就按码率排——站点标的「4K」经常是假的，实测数字不会骗人。
 * 全都探不到才退回原来的按清晰度标签判断。
 */
private fun bestLine(groups: List<PlayGroup>, probes: Map<Int, LineProbe.Info>): Int {
    val measured = groups.indices.filter { probes[it]?.kbps ?: 0 > 0 }
    if (measured.isNotEmpty()) {
        return measured.maxByOrNull { probes[it]?.kbps ?: 0 } ?: -1
    }
    return Quality.bestIndex(groups)
}

@Composable
private fun MetaLine(label: String, value: String) {
    if (value.isBlank()) return
    Row {
        Text("$label：", color = C.Muted, fontSize = 12.sp)
        Text(value, color = C.Text, fontSize = 12.sp)
    }
}

/** 选集网格，每行四个，够放下「第 12 集」这种长度。 */
@Composable
private fun EpisodeGrid(episodes: List<Episode>, onPick: (Int) -> Unit) {
    val columns = 4
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var i = 0
        while (i < episodes.size) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0 until columns) {
                    val index = i + c
                    if (index < episodes.size) {
                        Box(
                            Modifier
                                .weight(1f)
                                .background(C.Surface, RoundedCornerShape(8.dp))
                                .clickable { onPick(index) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.PlayArrow, null, tint = C.Muted,
                                    modifier = Modifier.height(14.dp),
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(episodes[index].name, color = C.Text, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            i += columns
        }
    }
}

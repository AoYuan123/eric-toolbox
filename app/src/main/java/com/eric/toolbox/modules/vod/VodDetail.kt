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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
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
import com.eric.toolbox.modules.vod.core.ParseResolver
import com.eric.toolbox.modules.vod.core.VodPrefs
import com.eric.toolbox.modules.vod.model.Episode
import com.eric.toolbox.modules.vod.model.VodItem
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.SectionLabel
import com.eric.toolbox.ui.TopBar
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

    LaunchedEffect(item.id) {
        if (item.playUrl.isBlank()) full = VodModule.detail(ctx, item)
        loading = false
    }

    val groups = remember(full) { VodModule.playGroups(full) }
    if (groupIndex > groups.lastIndex) groupIndex = 0

    fun start(episodes: List<Episode>, index: Int, sourceName: String) {
        if (episodes.isEmpty()) {
            Toast.makeText(ctx, "没有可播放的地址", Toast.LENGTH_SHORT).show()
            return
        }
        PlayerActivity.start(
            ctx = ctx,
            title = full.name,
            pic = full.pic,
            sourceName = sourceName,
            referer = VodModule.originFor(ctx, full.siteKey),
            ua = v.ua,
            episodes = episodes,
            startIndex = index,
        )
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
                EmptyHint("这个站点没有返回播放地址。\n换一个站点，或者看看其它同类资源。")
            } else {
                SectionLabel("线路")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups) { g ->
                        LineChip(g.source, groups.getOrNull(groupIndex)?.source == g.source) {
                            groupIndex = groups.indexOf(g)
                        }
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

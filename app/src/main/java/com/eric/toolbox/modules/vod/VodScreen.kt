package com.eric.toolbox.modules.vod

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eric.toolbox.modules.vod.core.Quality
import com.eric.toolbox.modules.vod.core.VodPrefs
import com.eric.toolbox.modules.vod.model.VodItem
import com.eric.toolbox.ui.C
import com.eric.toolbox.ui.Segmented
import com.eric.toolbox.ui.TopBar
import kotlinx.coroutines.launch

private enum class VodTab(val key: String, val label: String) {
    Browse("browse", "浏览"),
    Search("search", "搜索"),
    Mine("mine", "我的"),
}

private val TAB_OPTIONS = VodTab.entries.map { it.key to it.label }

/** 影视模块主界面。三个 Tab + 详情页 / 订阅页两层跳转。 */
@Composable
fun VodRoot(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val v by VodModule.state.collectAsState()

    var tab by remember { mutableStateOf(VodTab.Browse) }
    var strict by remember { mutableStateOf(true) }
    var detailItem by remember { mutableStateOf<VodItem?>(null) }
    var subsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        VodModule.reload(ctx)
        if (VodModule.hasTmdb(ctx)) VodModule.trending(ctx)
    }

    when {
        subsOpen -> SubscriptionScreen(
            onBack = {
                subsOpen = false
                scope.launch { VodModule.reload(ctx) }
            },
        )

        detailItem != null -> DetailScreen(item = detailItem!!, onBack = { detailItem = null })

        else -> Column(Modifier.fillMaxSize()) {
            TopBar(
                "影视", onBack,
                trailing = {
                    IconButton(onClick = { tab = VodTab.Mine }) {
                        Icon(Icons.Filled.Settings, "设置", tint = C.Muted)
                    }
                },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                Segmented(TAB_OPTIONS, tab.key) { k -> tab = VodTab.entries.first { it.key == k } }
                Spacer(Modifier.height(12.dp))
                when (tab) {
                    VodTab.Browse -> BrowsePane(v, strict) { detailItem = it }
                    VodTab.Search -> SearchPane(v, strict, { strict = it }) { detailItem = it }
                    VodTab.Mine -> MinePane(onOpenSubs = { subsOpen = true })
                }
            }
        }
    }
}

// -------------------------------------------------------------------- 浏览

@Composable
private fun BrowsePane(
    v: VodModule.VodUiState,
    strict: Boolean,
    onOpen: (VodItem) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    if (v.sites.isEmpty()) {
        EmptyHint("还没有站点。\n去「我的 › 订阅」导入一份配置。")
        return
    }

    // 选中站点后自动拉一次首页
    LaunchedEffect(v.currentSiteKey) {
        if (v.currentSiteKey.isNotBlank()) VodModule.browse(ctx)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(v.sites) { s ->
                LineChip(s.name, s.key == v.currentSiteKey) {
                    VodModule.selectSite(ctx, s.key)
                    scope.launch { VodModule.browse(ctx) }
                }
            }
        }
        if (v.categories.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(v.categories) { c ->
                    LineChip(c.name, c.id == v.currentTid) {
                        scope.launch { VodModule.browse(ctx, c.id) }
                    }
                }
            }
        }
        if (v.loading) LoadingRow()
        if (v.error.isNotBlank() && v.items.isEmpty()) EmptyHint(v.error)
        if (v.warnings.isNotEmpty()) {
            Text(v.warnings.first(), color = C.Warn, fontSize = 12.sp, lineHeight = 18.sp)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(v.items) { item ->
                PosterCard(
                    title = item.name,
                    pic = item.pic,
                    badge = Quality.of(item).label.ifBlank { item.remarks.ifBlank { item.typeName } },
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

// -------------------------------------------------------------------- 搜索

@Composable
private fun SearchPane(
    v: VodModule.VodUiState,
    strict: Boolean,
    onStrictChange: (Boolean) -> Unit,
    onOpen: (VodItem) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var kw by remember { mutableStateOf("") }
    var minRank by remember { mutableIntStateOf(VodPrefs.minQuality(ctx)) }
    var requireKnown by remember { mutableStateOf(VodPrefs.requireKnownQuality(ctx)) }

    fun doSearch() {
        if (kw.isBlank()) return
        scope.launch { VodModule.search(ctx, kw, strict) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = kw,
            onValueChange = { kw = it },
            placeholder = { Text("输入片名，回车搜索") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            trailingIcon = {
                IconButton(onClick = { doSearch() }) {
                    Icon(Icons.Filled.Search, "搜索", tint = C.Muted)
                }
            },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("严格匹配", color = C.Text, fontSize = 14.sp)
                Text("关掉会多出一些不太相关的结果", color = C.Muted, fontSize = 12.sp)
            }
            Switch(
                checked = strict,
                onCheckedChange = onStrictChange,
                colors = SwitchDefaults.colors(checkedTrackColor = C.Gold, checkedThumbColor = Color.White),
            )
        }

        // 画质门槛。站点标了清晰度才有效，没标的一律按「未标注」处理
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("画质", color = C.Text, fontSize = 14.sp, modifier = Modifier.width(40.dp))
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(Quality.Level.thresholds()) { lvl ->
                    LineChip(
                        text = if (lvl == Quality.Level.UNKNOWN) "不限" else "${lvl.label}+",
                        selected = lvl.rank == minRank,
                        onClick = {
                            minRank = lvl.rank
                            VodPrefs.setMinQuality(ctx, lvl.rank)
                            doSearch()
                        },
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("只看标了清晰度的", color = C.Text, fontSize = 14.sp)
                Text(
                    if (requireKnown) "未标注画质的内容会被剔除，结果少但每条都对得上" else "未标注画质的内容会保留（大多数站点不标）",
                    color = C.Muted, fontSize = 12.sp,
                )
            }
            Switch(
                checked = requireKnown,
                onCheckedChange = {
                    requireKnown = it
                    VodPrefs.setRequireKnownQuality(ctx, it)
                    doSearch()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = C.Gold, checkedThumbColor = Color.White),
            )
        }

        if (v.loading) LoadingRow()

        if (v.searchedCount > 0 && v.items.isNotEmpty()) {
            var line = "${v.searchedCount} 个站点 · ${v.items.size} 条结果"
            if (v.droppedCount > 0) line += " · 过滤 ${v.droppedCount} 条弱相关"
            if (v.droppedByQuality > 0) line += " · 过滤 ${v.droppedByQuality} 条低画质"
            Text(line, color = C.Muted, fontSize = 12.sp)
        }
        if (v.error.isNotBlank() && v.items.isEmpty()) EmptyHint(v.error)

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(v.items) { item ->
                PosterCard(
                    title = item.name,
                    pic = item.pic,
                    badge = Quality.of(item).label.ifBlank { item.remarks.ifBlank { item.siteName } },
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 小组件

@Composable
private fun LoadingRow() {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(18.dp), color = C.Gold, strokeWidth = 2.dp)
        Spacer(Modifier.size(8.dp))
        Text("加载中", color = C.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun LoadingBox(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), color = C.Gold, strokeWidth = 2.dp)
        Text(text, color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 40.dp))
    }
}

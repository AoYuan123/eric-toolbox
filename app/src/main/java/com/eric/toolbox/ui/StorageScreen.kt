package com.eric.toolbox.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** 存储空间：分类显示占用，能安全删除的给「清理」。 */
private data class Part(val zh: String, val en: String, val subZh: String, val subEn: String, val files: (Context) -> List<File>, val cleanable: Boolean)

private val PARTS = listOf(
    Part("下载的书", "Books", "书架上的书，删书时一起删", "Removed when you delete a book", { c -> listOf(File(c.filesDir, "read/books")) }, false),
    Part("真人朗读音频", "Audiobook files", "可以清理，需要时再下载", "Safe to clear; re-download when needed", { c -> listOf(File(c.filesDir, "read/audio")) }, true),
    Part("离线词典", "Offline dictionary", "查词要用，清理后下次查词会自动重新解压", "Re-extracted automatically when needed", { c -> c.filesDir.resolve("read").listFiles { f -> f.name.startsWith("ecdict") }?.toList().orEmpty() }, false),
    Part("走势缓存", "Chart cache", "金价 K 线缓存，可以清理", "Gold chart cache, safe to clear", { c -> c.filesDir.listFiles { f -> f.name.startsWith("kline_") }?.toList().orEmpty() }, true),
    Part("翻译缓存", "Translation cache", "清理后重新翻译会再占用百度额度", "Clearing uses translation quota again", { c -> listOf(File(c.filesDir, "trans_cache.json")) }, true),
    Part("系统缓存与更新包", "Cache & update files", "装完的安装包、网页缓存，可以清理", "Installed update files, web cache", { c -> listOf(c.cacheDir) }, true),
)

private fun size(f: File): Long = if (!f.exists()) 0 else if (f.isFile) f.length() else f.listFiles()?.sumOf { size(it) } ?: 0
private fun wipe(f: File) { if (f.isDirectory) f.listFiles()?.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() } else f.delete() }

fun appDataBytes(ctx: Context): Long = size(ctx.filesDir) + size(ctx.cacheDir)

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<Part?>(null) }
    val sizes = remember(tick) { PARTS.associateWith { p -> p.files(ctx).sumOf { size(it) } } }
    val total = remember(tick) { appDataBytes(ctx) }
    val known = sizes.values.sum()
    Column(Modifier.fillMaxSize()) {
        TopBar(tr("存储空间", "Storage"), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(strong = true) {
                Caption(tr("百宝箱数据共占用", "Toolbox data in total"))
                Text(formatBytes(total), color = C.Text, fontSize = 30.sp, style = NumStyle)
                Caption(tr("不含安装包本身。卸载 App 会一起删除。", "Excludes the app itself."))
            }
            ListCard {
                PARTS.forEach { p ->
                    RowItem(tr(p.zh, p.en), formatBytes(sizes[p] ?: 0), sub = tr(p.subZh, p.subEn) + if (p.cleanable && (sizes[p] ?: 0) > 0) tr(" · 点击清理", " · tap to clear") else "",
                        onClick = if (p.cleanable && (sizes[p] ?: 0) > 0) ({ confirm = p }) else null)
                }
                RowItem(tr("其他（设置、生词、笔记、记录等）", "Other (settings, notes, logs)"), formatBytes((total - known).coerceAtLeast(0)))
            }
            Caption(tr("设置、生词本、划线笔记、交易明细这类你自己的数据不会被清理。", "Your own data (settings, words, notes) is never cleared here."), Modifier.padding(horizontal = 4.dp))
        }
    }
    confirm?.let { p ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(tr("清理「${p.zh}」？", "Clear ${p.en}?")) },
            text = { Text(tr(p.subZh, p.subEn)) },
            confirmButton = { TextButton(onClick = { p.files(ctx).forEach { wipe(it) }; confirm = null; tick++; Toast.makeText(ctx, tr("已清理", "Cleared"), Toast.LENGTH_SHORT).show() }) { Text(tr("清理", "Clear")) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("取消", "Cancel")) } },
        )
    }
}

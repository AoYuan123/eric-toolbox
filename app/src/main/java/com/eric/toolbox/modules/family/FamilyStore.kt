package com.eric.toolbox.modules.family

import android.content.Context
import com.eric.toolbox.core.Store
import org.json.JSONArray
import java.io.File
import java.time.LocalDate

/**
 * 记忆绳的存储。
 *
 * 索引是一个 JSON 文件，媒体文件放在 files/family/media/ 下面。
 * 全部在本机私有目录里，没有任何上传——这个模块从头到尾不碰网络。
 *
 * 写索引用「先写 tmp 再 rename」，避免拍到一半断电把整条绳子变成半截 JSON。
 * 单条挂点出错只丢那一条，不影响其余。
 */
object FamilyStore {
    private const val DIR = "family"
    private const val MEDIA = "media"
    private const val INDEX = "entries.json"

    fun root(ctx: Context): File = File(ctx.filesDir, DIR).apply { mkdirs() }

    fun mediaDir(ctx: Context): File = File(root(ctx), MEDIA).apply { mkdirs() }

    fun fileOf(ctx: Context, name: String): File = File(mediaDir(ctx), name)

    private fun indexFile(ctx: Context): File = File(root(ctx), INDEX)

    fun load(ctx: Context): MutableList<Entry> {
        val f = indexFile(ctx)
        if (!f.isFile) return mutableListOf()
        val text = runCatching { f.readText() }.getOrNull() ?: return mutableListOf()
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return mutableListOf()
        return Entry.fromJsonArray(arr)
            .filter { fileOf(ctx, it.file).isFile } // 文件被手动删掉的孤儿条目不再显示
            .sortedBy { it.atMs }
            .toMutableList()
    }

    fun save(ctx: Context, list: List<Entry>) {
        val f = indexFile(ctx)
        val tmp = File(f.parentFile, "$INDEX.tmp")
        runCatching {
            tmp.writeText(Entry.toJsonArray(list.sortedBy { it.atMs }).toString())
            if (f.exists()) f.delete()
            tmp.renameTo(f)
        }
    }

    /** 生成不冲突的文件名：p_20261121_143012_4721.jpg */
    fun newName(ctx: Context, kind: Kind, ext: String): String {
        val prefix = when (kind) {
            Kind.PHOTO -> "p"
            Kind.VIDEO -> "v"
            Kind.AUDIO -> "a"
        }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.CHINA).format(java.util.Date())
        val salt = (System.nanoTime() % 10000).toString().padStart(4, '0')
        val base = "${prefix}_${stamp}_$salt.$ext"
        // 极小概率撞名时兜底
        return if (fileOf(ctx, base).exists()) "${prefix}_${stamp}_${salt}_2.$ext" else base
    }

    fun add(ctx: Context, e: Entry) {
        val list = load(ctx)
        list.add(e)
        save(ctx, list)
    }

    fun update(ctx: Context, e: Entry) {
        val list = load(ctx)
        val i = list.indexOfFirst { it.id == e.id }
        if (i >= 0) list[i] = e else return
        save(ctx, list)
    }

    fun remove(ctx: Context, id: String) {
        val list = load(ctx)
        val target = list.firstOrNull { it.id == id } ?: return
        list.remove(target)
        save(ctx, list)
        runCatching { fileOf(ctx, target.file).delete() }
    }

    // ======================= 起点与出生 =======================

    private fun prefs(ctx: Context) = Store.module(ctx, "family")

    fun edd(ctx: Context): LocalDate? {
        val v = prefs(ctx).getLong("edd", 0L)
        return if (v > 0) FamilyDate.dayOf(v) else null
    }

    fun setEdd(ctx: Context, d: LocalDate?) {
        prefs(ctx).edit().putLong("edd", d?.let { FamilyDate.atDay(it) } ?: 0L).apply()
    }

    fun birth(ctx: Context): LocalDate? {
        val v = prefs(ctx).getLong("birth", 0L)
        return if (v > 0) FamilyDate.dayOf(v) else null
    }

    fun setBirth(ctx: Context, d: LocalDate?) {
        prefs(ctx).edit().putLong("birth", d?.let { FamilyDate.atDay(it) } ?: 0L).apply()
    }

    /** 正文以外的零碎偏好放这里，避免再开一个 SharedPreferences。 */
    fun flag(ctx: Context, key: String, def: Boolean = false): Boolean = prefs(ctx).getBoolean(key, def)

    fun setFlag(ctx: Context, key: String, v: Boolean) {
        prefs(ctx).edit().putBoolean(key, v).apply()
    }

    /** 占用的空间（KB），运行状态里显示用。 */
    fun usedKb(ctx: Context): Long {
        val dir = mediaDir(ctx)
        val files = dir.listFiles() ?: return 0
        return files.sumOf { it.length() } / 1024
    }
}

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

    /**
     * 升级前挂上去的照片没有"是不是实况"的标记（live = -1）。
     * 这里补判一次并落盘，用 flag 兜住，之后不再扫——几百张照片全扫一遍要几秒，
     * 不能每次打开都扫。
     */
    fun migrateLive(ctx: Context) {
        if (flag(ctx, "live_scanned")) return
        val list = load(ctx)
        var changed = false
        for (i in list.indices) {
            val e = list[i]
            if (e.kind == Kind.PHOTO && e.live < 0) {
                val v = if (MotionPhoto.isLive(fileOf(ctx, e.file))) 1 else 0
                list[i] = e.copy(live = v)
                changed = true
            }
        }
        if (changed) save(ctx, list)
        setFlag(ctx, "live_scanned", true)
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

    // ======================= 时间表 =======================

    /**
     * 已经划掉的条目。按 key 存，不按日期——日期会随预产期改，key 不会。
     * 建议先 copy 一份再改：getStringSet 返回的是内部引用，直接 mutate 在某些机型上会丢写入。
     */
    fun doneKeys(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("plan_done", emptySet())?.toSet() ?: emptySet()

    fun setDone(ctx: Context, key: String, done: Boolean) {
        val cur = doneKeys(ctx).toMutableSet()
        if (done) cur.add(key) else cur.remove(key)
        prefs(ctx).edit().putStringSet("plan_done", cur).apply()
    }

    /** 预产期或生日改了以后，旧的划掉记录就没意义了——留着会挡住新的日期。 */
    fun clearDone(ctx: Context) {
        prefs(ctx).edit().remove("plan_done").apply()
    }

    /**
     * 每条时间表自己的备注：医生交代的话、自己想问的事、抽血单上记下的数字。
     * 这些是"我们家"的信息，不属于通用参考，也不该被写死在代码里。
     * 按 key 存，跟划掉的记录一样，改预产期不会把它弄丢。
     */
    fun planNote(ctx: Context, key: String): String =
        prefs(ctx).getString("pn_$key", "").orEmpty()

    fun setPlanNote(ctx: Context, key: String, text: String) {
        val t = text.trim()
        prefs(ctx).edit().apply {
            if (t.isEmpty()) remove("pn_$key") else putString("pn_$key", t)
        }.apply()
    }

    /** 占用的空间（KB），运行状态里显示用。 */
    fun usedKb(ctx: Context): Long {
        val dir = mediaDir(ctx)
        val files = dir.listFiles() ?: return 0
        return files.sumOf { it.length() } / 1024
    }
}

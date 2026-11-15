package com.eric.toolbox.modules.english.ielts

import android.content.Context
import com.eric.toolbox.core.Store
import com.eric.toolbox.modules.english.EnglishStore

/**
 * 雅思场景模块的进度存储。和背单词共用一份 prefs（Store.module("english")），
 * 但键不重叠，清空背词不会把场景进度一起清掉。
 *
 * 三个进度各自独立，不互相绑死：
 * - 读：进过这个场景的详情页就记一次
 * - 词：这个场景的词有没有进背词队列（= 词书被选中 且 场景开着）
 * - 说：跟读的最好命中率（百分制），没过 0 就算没练过
 */
object IeltsStore {
    private fun prefs(ctx: Context) = Store.module(ctx, "english")

    // ---------------- 场景开关（默认全开） ----------------

    /**
     * 开着哪些场景。键不存在 = 全开（第一次进来就是 18 个场景的词都在队列里，
     * 不需要先手动勾一遍）；一旦用户动过开关，就按存下来的集合走。
     */
    fun on(ctx: Context): Set<String> {
        val p = prefs(ctx)
        if (!p.contains("ielts_on")) return IeltsData.scenes.map { it.id }.toSet()
        return p.getStringSet("ielts_on", emptySet())!!.toSet()
    }

    fun setOn(ctx: Context, set: Set<String>) {
        prefs(ctx).edit().putStringSet("ielts_on", set).apply()
    }

    fun isOn(ctx: Context, id: String): Boolean = id in on(ctx)

    /** 切换一个场景；返回切换后的状态。 */
    fun toggle(ctx: Context, id: String): Boolean {
        val cur = on(ctx).toMutableSet()
        val now = if (id in cur) { cur.remove(id); false } else { cur.add(id); true }
        setOn(ctx, cur)
        return now
    }

    /**
     * 这个场景的词在背词队列里吗。要同时满足：词书被选中 + 场景开着。
     * 只把场景打开但词书没选，队列里不会出现——所以「加入背词」按钮要两个都置上。
     */
    fun inReview(ctx: Context, id: String): Boolean = IeltsData.BOOK in EnglishStore.books(ctx) && isOn(ctx, id)

    /** 把这个场景的词加进背词（词书选中 + 场景打开）。返回现在的状态。 */
    fun addToReview(ctx: Context, id: String): Boolean {
        val books = EnglishStore.books(ctx)
        if (IeltsData.BOOK !in books) EnglishStore.setBooks(ctx, books + IeltsData.BOOK)
        val cur = on(ctx).toMutableSet()
        if (cur.add(id)) setOn(ctx, cur)
        return true
    }

    fun removeFromReview(ctx: Context, id: String): Boolean {
        val cur = on(ctx).toMutableSet()
        if (cur.remove(id)) setOn(ctx, cur)
        return false
    }

    // ---------------- 已读 ----------------

    fun read(ctx: Context): Set<String> = prefs(ctx).getStringSet("ielts_read", emptySet())!!.toSet()

    fun markRead(ctx: Context, id: String) {
        val cur = read(ctx)
        if (id in cur) return
        prefs(ctx).edit().putStringSet("ielts_read", (cur + id).toSet()).apply()
    }

    // ---------------- 跟读成绩 ----------------

    fun shadow(ctx: Context, id: String): Int = shadowAll(ctx)[id] ?: 0

    private fun shadowAll(ctx: Context): Map<String, Int> =
        prefs(ctx).getString("ielts_shadow", "").orEmpty().split(',')
            .mapNotNull { s ->
                val i = s.indexOf('=')
                if (i <= 0) null else s.substring(0, i) to (s.substring(i + 1).toIntOrNull() ?: return@mapNotNull null)
            }.toMap()

    /** 只记最好成绩，退步不覆盖。 */
    fun setShadow(ctx: Context, id: String, pct: Int) {
        val m = shadowAll(ctx).toMutableMap()
        if (pct <= (m[id] ?: 0)) return
        m[id] = pct.coerceIn(0, 100)
        prefs(ctx).edit().putString("ielts_shadow", m.entries.joinToString(",") { "${it.key}=${it.value}" }).apply()
    }

    // ---------------- 队列用到的键 ----------------

    /**
     * 进背词队列的词键（小写）。只取开着的场景，同一个词在多个场景出现只留一条，
     * 否则背单词那边会出两张卡（卡片 id 是 book:key）。
     */
    fun keys(ctx: Context): List<String> {
        val onIds = on(ctx)
        return IeltsData.scenes.filter { it.id in onIds }
            .flatMap { s -> s.words.map { it.en.lowercase() } }
            .distinct()
    }
}

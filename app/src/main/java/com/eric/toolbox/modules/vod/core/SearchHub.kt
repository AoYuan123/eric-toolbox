package com.eric.toolbox.modules.vod.core

import com.eric.toolbox.modules.vod.model.VodItem
import com.eric.toolbox.modules.vod.model.VodSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 多站搜索聚合。
 *
 * 这里刻意把「相关性」做成**硬门槛**而不是排序权重：低于阈值直接丢掉。
 * 教训来自网盘模块——那里 kwHits 只参与排序不参与剔除，结果无关项照样上榜，
 * 最后只能靠人肉筛。搜索这类场景，宁可少给几条，也不要给用户一堆噪音。
 */
object SearchHub {

    /** 相关性低于这个值就不展示。放宽到 0.4 左右会明显多出大量不相关结果。 */
    const val MIN_SCORE = 0.6

    data class SearchOutcome(
        val hits: List<VodItem>,
        val errors: List<String>,
        val searchedCount: Int,
        /** 被门槛刷掉多少条，用于在 UI 上说明「还有 N 条弱相关结果没显示」 */
        val droppedCount: Int,
    )

    suspend fun search(
        sites: List<VodSite>,
        keyword: String,
        ua: String = "",
        timeoutMs: Int = 15_000,
        strict: Boolean = true,
        limitPerSite: Int = 12,
    ): SearchOutcome = withContext(Dispatchers.IO) {
        val targets = sites.filter { it.searchable && it.supported && it.hasApi }
        if (targets.isEmpty() || keyword.isBlank()) {
            return@withContext SearchOutcome(emptyList(), listOf("没有可搜索的站点"), 0, 0)
        }
        coroutineScope {
            val jobs = targets.map { site ->
                async { runCatching { SiteClient(site, ua, timeoutMs).search(keyword) } to site }
            }
            val scored = ArrayList<Pair<VodItem, Double>>()
            val errors = ArrayList<String>()
            var dropped = 0
            for (job in jobs) {
                val (res, site) = job.await()
                val page = res.getOrNull()
                if (page == null || !page.ok) {
                    val msg = res.exceptionOrNull()?.let { VodHttp.messageOf(it) }
                        ?: page?.error.orEmpty()
                    errors += "「${site.name}」${msg.ifBlank { "无结果" }}"
                    continue
                }
                for (item in page.items.take(limitPerSite)) {
                    val score = relevance(item.name, keyword)
                    if (strict && score < MIN_SCORE) {
                        dropped++
                        continue
                    }
                    scored += item to score
                }
            }
            // 同名不同站的归并留下相关性最高的那条
            val hits = scored
                .groupBy { dedupKey(it.first) }
                .map { (_, list) -> list.maxBy { it.second } }
                .sortedByDescending { it.second }
                .map { it.first }
            SearchOutcome(hits, errors, targets.size, dropped)
        }
    }

    suspend fun browse(
        site: VodSite,
        tid: String,
        page: Int,
        ua: String = "",
        timeoutMs: Int = 15_000,
    ): SiteClient.PageResult = withContext(Dispatchers.IO) {
        val client = SiteClient(site, ua, timeoutMs)
        if (tid.isBlank()) client.home(page) else client.category(tid, page)
    }

    // ------------------------------------------------------------ 相关性算法

    /**
     * 标题与关键词的相关性，0~1。
     *
     * 做两级判断：整串包含直接满分；否则拆成二元组算命中比例。
     * 二元组对中文效果不错，既能容忍「一年级 毕业季」这种带空格的写法，
     * 也能挡住只蹭到一两个字的结果。
     */
    fun relevance(title: String, keyword: String): Double {
        // 只取前 60 字：个别站点会把简介塞进标题字段参与判断
        val t = normalize(title).take(60)
        val k = normalize(keyword)
        if (t.isEmpty() || k.isEmpty()) return 0.0
        if (t.contains(k)) return 1.0
        val grams = grams(k)
        if (grams.isEmpty()) return 0.0
        val hit = grams.count { t.contains(it) }
        return hit.toDouble() / grams.size
    }

    /** 归一化：只留字母数字和汉字，标点空格全丢，统一小写 */
    fun normalize(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            if (c.isLetterOrDigit()) sb.append(c.lowercaseChar())
        }
        return sb.toString()
    }

    private fun grams(s: String): List<String> {
        if (s.length <= 2) return listOf(s)
        return (0..s.length - 2).map { s.substring(it, it + 2) }
    }

    private fun dedupKey(item: VodItem): String {
        val n = normalize(item.name)
        return if (n.isBlank()) "${item.siteKey}_${item.id}" else "$n|${item.year}"
    }
}

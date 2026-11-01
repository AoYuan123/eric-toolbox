package com.eric.toolbox.modules.pan.core

import com.eric.toolbox.modules.pan.model.Dup
import com.eric.toolbox.modules.pan.model.PanItem
import com.eric.toolbox.modules.pan.model.SearchOptions

/**
 * 聚合器：把多个源的结果揉成一份干净列表。
 *
 * 四件事，顺序不能乱：
 *   1. 过滤（网盘类型 / include / exclude / 空链接）
 *   2. 一级去重（URL 完全相同只留一条）
 *   3. 二级聚合（同一网盘 + 归一化标题相同的，折叠成一条，其余进 dups）
 *   4. 排序 + 每个网盘限条
 */
object Aggregator {

    fun aggregate(
        raw: List<PanItem>,
        keyword: String,
        opts: SearchOptions,
        /** 本地确认失效的 URL（规范化后），这些沉底 */
        deadUrls: Set<String> = emptySet(),
        /** 被相关性硬门槛剔除了多少条，回调给调用方用于在界面上说明 */
        onDroppedRelevance: (Int) -> Unit = {},
    ): List<PanItem> {
        if (raw.isEmpty()) return emptyList()

        var droppedRelevance = 0

        // 1. 过滤 + 填归一化标题
        val filtered = ArrayList<PanItem>(raw.size)
        for (item in raw) {
            if (!passFilters(item, opts)) continue
            item.normTitle = TitleNormalizer.normalize(item.note)
            // 严格匹配：标题一个分词都没命中的直接丢掉，而不是只排到后面。
            // 判定的依据是**纯标题**，不带简介——详见 pureTitle 的注释。
            if (opts.strict && kwHits(pureTitle(item.note), keyword) == 0) {
                droppedRelevance++
                continue
            }
            filtered.add(item)
        }
        onDroppedRelevance(droppedRelevance)
        if (filtered.isEmpty()) return emptyList()

        // 2. 一级去重：URL 规范化后相同的只留第一条
        val seenUrl = HashSet<String>(filtered.size)
        val deduped = ArrayList<PanItem>(filtered.size)
        for (item in filtered) {
            val key = normUrl(item.url)
            if (key.isEmpty()) continue
            if (seenUrl.add(key)) deduped.add(item)
        }

        // 3. 二级聚合：(cloud, normTitle) 相同视为同一资源
        val groups = LinkedHashMap<String, MutableList<PanItem>>()
        for (item in deduped) {
            val nt = item.normTitle
            val key = if (nt.isNotEmpty()) "${item.cloud}\u0001$nt" else "${item.cloud}\u0001${normUrl(item.url)}"
            groups.getOrPut(key) { mutableListOf() }.add(item)
        }

        val primaries = ArrayList<PanItem>(groups.size)
        val pairs = ArrayList<Pair<PanItem, List<PanItem>>>(groups.size)
        for ((_, members) in groups) {
            // 组内选主：关键词命中多的优先 → 日期新的优先 → 来源等级高的优先
            val sorted = members.sortedWith(groupComparator(keyword))
            val primary = sorted.first()
            if (sorted.size > 1) {
                primary.dups.clear()
                primary.dups.addAll(sorted.drop(1).map { Dup(it.url, it.note, it.source) })
            }
            primaries.add(primary)
            pairs.add(primary to sorted)
        }

        // 4. 全局排序。
        //    注意：这里是多次稳定排序，最后调用的优先级最高。
        //    不要合并成一个 comparator 就完事，语义会变。
        //    最终优先级：内容价值 → 失效沉底 → 关键词命中/来源等级 → 日期。
        var list = sortGlobal(primaries, keyword, deadUrls)

        // 5. 展开副本（可选）：把折叠起来的同资源副本平铺出来，再按同样规则重排
        if (opts.allDups) {
            val aliveUrl = list.map { normUrl(it.url) }.toHashSet()
            val expanded = ArrayList<PanItem>()
            for ((primary, members) in pairs) {
                if (normUrl(primary.url) !in aliveUrl) continue
                for (m in members) {
                    m.dups.clear()   // 平铺模式下不再带「另有 N 个」提示
                    expanded.add(m)
                }
            }
            list = sortGlobal(expanded, keyword, deadUrls)
        }

        // 6. 每个网盘限条
        if (opts.limitPerCloud > 0) {
            val counter = HashMap<String, Int>()
            list = list.filter { item ->
                val n = (counter[item.cloud] ?: 0) + 1
                counter[item.cloud] = n
                n <= opts.limitPerCloud
            }.toMutableList()
        }

        return list
    }

    // ---------------------------------------------------------------- 过滤

    fun passFilters(item: PanItem, opts: SearchOptions): Boolean {
        if (item.url.isBlank()) return false
        if (opts.cloudTypes.isNotEmpty() && item.cloud !in opts.cloudTypes) return false

        val blob = buildString {
            append(item.note)
            append(' ')
            append(item.url)
            if (item.contentTag.isNotEmpty()) {
                append(' ')
                append(item.contentTag)
            }
            if (item.contentPeek.isNotEmpty()) {
                append(' ')
                append(item.contentPeek)
            }
        }.lowercase()

        if (opts.include.isNotEmpty()) {
            for (w in opts.include) {
                if (w.isNotBlank() && !blob.contains(w.lowercase())) return false
            }
        }
        if (opts.exclude.isNotEmpty()) {
            for (w in opts.exclude) {
                if (w.isNotBlank() && blob.contains(w.lowercase())) return false
            }
        }
        return true
    }

    // ---------------------------------------------------------------- 排序键

    /**
     * 全局排序。必须按这个顺序连续做四次稳定排序，不能合并成一个 comparator：
     * 合并后「日期」会变成同权重维度，和原实现的优先级语义不一样。
     */
    private fun sortGlobal(
        items: List<PanItem>,
        keyword: String,
        deadUrls: Set<String>,
    ): MutableList<PanItem> {
        val list = items.toMutableList()
        list.sortByDescending { dateNum(it.datetime) }          // 3) 日期新的在前
        list.sortWith(globalComparator(keyword))                 // 2) 关键词命中 > 来源等级
        if (deadUrls.isNotEmpty()) {
            list.sortBy { normUrl(it.url) in deadUrls }           // 1) 确认失效的沉底
        }
        list.sortBy { isLowValueContent(it) }                    // 0) 小说/引流 沉底（最高优先级）
        return list
    }

    private fun groupComparator(kw: String): Comparator<PanItem> =
        compareByDescending<PanItem> { kwHits(pureTitle(it.note), kw) }
            .thenByDescending { dateNum(it.datetime) }
            .thenBy { CloudType.engineRank(it.source) }

    private fun globalComparator(kw: String): Comparator<PanItem> =
        compareByDescending<PanItem> { kwHits(pureTitle(it.note), kw) }
            .thenBy { CloudType.engineRank(it.source) }

    /** 「描述：」「简介：」「详情：」这类分隔符，之后的内容全是正文，不是标题 */
    private val DESC_SPLIT = Regex("[描简详][述介情]\\s*[:：]")

    /**
     * 取纯标题：note 里标题和简介经常被拼成一个字符串。
     *
     * 这不是洁癖。真实案例：搜「一年级毕业季」搜出了「恶作剧之吻」，
     * 因为它的简介里有「……一年级……毕业季……」的相关描述，
     * 全文匹配就把它召回了。判相关性之前必须先把这段正文切掉。
     */
    fun pureTitle(note: String?): String {
        if (note.isNullOrBlank()) return ""
        val m = DESC_SPLIT.find(note) ?: return note
        return note.substring(0, m.range.first).trim()
    }

    /** 关键词命中数：note 里出现了几个分词。调用前请先用 [pureTitle] 剥掉简介 */
    fun kwHits(note: String?, kw: String?): Int {
        val n = note ?: return 0
        val tokens = (kw ?: "").split(Regex("""[\s,，]+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return 0
        return tokens.count { n.contains(it) }
    }

    /** yyyy-MM-dd → 20260910，用于比大小；无法解析返回 0 */
    fun dateNum(datetime: String?): Long {
        val d = (datetime ?: "").take(10).replace("-", "")
        return d.toLongOrNull() ?: 0L
    }

    /** 纯小说/引流资源在影视搜索里价值低，排后面 */
    private fun isLowValueContent(item: PanItem): Boolean {
        val tag = item.contentTag
        return tag.contains("小说") || tag.contains("引流")
    }

    // ---------------------------------------------------------------- 工具

    /**
     * URL 规范化：去 fragment、去尾斜杠、scheme 与 host 小写（路径保持原样，分享 id 可能区分大小写）。
     * 注意：fragment 形如 `#/w/i/xxx`（移动云盘就是这个写法）是路由的一部分，不能删，
     * 否则会把不同分享链成一模一样的地址。
     */
    fun normUrl(url: String?): String {
        var u = (url ?: "").trim()
        if (u.isEmpty()) return ""
        if (u.contains('#') && !u.contains("#/")) u = u.substringBefore('#')
        u = u.trimEnd('/')
        val idx = u.indexOf("://")
        if (idx <= 0) return u
        val scheme = u.substring(0, idx).lowercase()
        val rest = u.substring(idx + 3)
        val slash = rest.indexOf('/')
        return if (slash < 0) {
            "$scheme://${rest.lowercase()}"
        } else {
            "$scheme://${rest.substring(0, slash).lowercase()}${rest.substring(slash)}"
        }
    }
}

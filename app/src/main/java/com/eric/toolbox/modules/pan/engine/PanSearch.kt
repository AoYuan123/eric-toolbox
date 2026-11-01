package com.eric.toolbox.modules.pan.engine

import com.eric.toolbox.modules.pan.core.Aggregator
import com.eric.toolbox.modules.pan.core.CloudType
import com.eric.toolbox.modules.pan.core.EngineCircuit
import com.eric.toolbox.modules.pan.core.EngineState
import com.eric.toolbox.modules.pan.core.KeywordVariants
import com.eric.toolbox.modules.pan.model.EngineResult
import com.eric.toolbox.modules.pan.model.PanItem
import com.eric.toolbox.modules.pan.model.SearchOptions
import com.eric.toolbox.modules.pan.model.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 搜索编排器 —— 对应 Python `run()` + `_search_once()`。
 *
 * 完整流程（顺序不能改）：
 *   1. 熔断过滤：冷却中的引擎直接跳过（`fresh=true` 可强制全跑）
 *   2. 并行跑所有引擎，单源失败只记错误，不中断
 *   3. 失败回写熔断状态：成功清零、失败累加、强风控一次即熔断
 *   4. 0 结果 → 换关键词变体重试一轮（本轮刚撞风控的引擎不参与，避免立刻再撞）
 *   5. 默认源全是无效链接 → 用 TA搜 兜底一次
 *   6. 交给 [Aggregator] 做过滤 / 两级去重 / 折叠 / 排序 / 限条
 *
 * App 只需要：
 * ```
 * val r = PanSearch().search("星际穿越")
 * r.grouped().forEach { (cloud, list) -> ... }
 * ```
 *
 * @param circuit 熔断状态，可长期持有；配合 [EngineCircuit.toJson] 存起来就能跨次生效
 * @param onCircuitChanged 熔断状态变化回调（存 DataStore 用）
 */
class PanSearch(
    private val engines: List<PanEngine> = defaultEngines(),
    /** 本地已确认失效的链接（规范化后），这些只沉底不删除 */
    private val deadUrlsProvider: () -> Set<String> = { emptySet() },
    /** 熔断状态表，传入你自己持有的那个 Map 即可跨次搜索生效 */
    private val circuit: MutableMap<String, EngineState> = mutableMapOf(),
    /** 熔断状态变更时回调，用于持久化 */
    private val onCircuitChanged: (MutableMap<String, EngineState>) -> Unit = {},
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    suspend fun search(
        keyword: String,
        opts: SearchOptions = SearchOptions(),
    ): SearchResult {
        val kw = keyword.trim()
        if (kw.isEmpty()) return SearchResult(keyword = "", items = emptyList())

        val errors = mutableListOf<String>()
        val totals = LinkedHashMap<String, Int>()
        val elapsed = LinkedHashMap<String, Long>()
        val risky = mutableSetOf<String>()
        val skipped = mutableListOf<String>()
        val dead = deadUrlsProvider()

        // 1. 熔断过滤
        val active = ArrayList<PanEngine>()
        for (e in engines) {
            if (e.name !in opts.engines) continue
            if (!opts.fresh && EngineCircuit.isBlocked(e.name, circuit, clock())) {
                skipped.add(e.name)
                continue
            }
            active.add(e)
        }

        // 2-3. 第一轮
        var raw = runOnce(active, kw, opts, errors, totals, elapsed, risky)
        var variantUsed = ""

        // 4. 变体兜底：只有在「一条可用结果都没有」时才做
        if (opts.useVariants && Aggregator.aggregate(raw, kw, opts, dead).isEmpty()) {
            val vEngines = active.filter { it.name !in risky }
            if (vEngines.isNotEmpty()) {
                for (v in KeywordVariants.build(kw)) {
                    if (v == kw) continue
                    val vErrors = mutableListOf<String>()
                    val vTotals = LinkedHashMap<String, Int>()
                    val vRaw = runOnce(vEngines, v, opts, vErrors, vTotals, elapsed, risky)
                    if (vRaw.isEmpty()) continue
                    if (Aggregator.aggregate(vRaw, v, opts, dead).isNotEmpty()) {
                        variantUsed = v
                        raw = vRaw
                        totals.clear()
                        totals.putAll(vTotals)
                        errors.addAll(vErrors.map { clip("变体「$v」: $it") })
                        break
                    }
                }
            }
        }

        // 5. TA搜兜底：默认源全都没抓到有效分享链接时，补一次 ataw
        var atawFallback = false
        if (shouldAtawFallback(active.map { it.name }, raw)) {
            if (EngineCircuit.isRiskBlocked(ATSW, circuit, clock())) {
                errors.add("ataw(fallback): 风控冷却中，已跳过")
            } else {
                val ataw = engines.firstOrNull { it.name == ATSW } ?: AtawEngine()
                val r = try {
                    ataw.search(kw, opts)
                } catch (e: Exception) {
                    EngineResult(ATSW, error = e.message ?: "兜底失败")
                }
                val err = r.error
                if (err != null) {
                    errors.add(clip("ataw(fallback): $err"))
                    val ri = EngineCircuit.riskInfoOfMessage(err)
                    if (ri.level >= 2) {
                        EngineCircuit.markFailure(ATSW, circuit, ri.level, ri.retryAfterSec, clock())
                        onCircuitChanged(circuit)
                    }
                } else if (r.items.isNotEmpty()) {
                    atawFallback = true
                    totals[ATSW] = r.total
                    raw = raw + r.items
                }
            }
        }

        // 6. 聚合
        var droppedRelevance = 0
        val items = if (raw.isEmpty()) {
            emptyList()
        } else {
            Aggregator.aggregate(raw, kw, opts, dead) { droppedRelevance = it }
        }
        errors.sort()

        return SearchResult(
            keyword = kw,
            items = items,
            totals = totals,
            errors = errors,
            skippedEngines = skipped,
            variantUsed = variantUsed,
            elapsedMs = elapsed,
            atawFallback = atawFallback,
            droppedRelevance = droppedRelevance,
        )
    }

    /**
     * 跑一轮引擎（并行），并把成功/失败回写到熔断状态。
     * @return 未聚合的原始条目（按 [engines] 顺序拼接，与原实现的 ENGINE_ORDER 一致）
     */
    private suspend fun runOnce(
        active: List<PanEngine>,
        keyword: String,
        opts: SearchOptions,
        errors: MutableList<String>,
        totals: MutableMap<String, Int>,
        elapsed: MutableMap<String, Long>,
        risky: MutableSet<String>,
    ): List<PanItem> {
        if (active.isEmpty()) return emptyList()

        val results = coroutineScope {
            active.map { engine ->
                async {
                    val t0 = clock()
                    val r = try {
                        engine.search(keyword, opts)
                    } catch (t: Throwable) {
                        // 引擎内部应当自己 catch；这里再兜一层，保证一个源挂了不影响别的
                        EngineResult(engine.name, error = t.message ?: "未捕获异常")
                    }
                    Triple(engine.name, r, clock() - t0)
                }
            }.awaitAll()
        }

        val raw = ArrayList<PanItem>()
        var circuitDirty = false
        for ((name, r, ms) in results) {
            elapsed[name] = ms
            val err = r.error
            if (err != null) {
                errors.add(clip("$name: $err"))
                val ri = EngineCircuit.riskInfoOfMessage(err)
                if (ri.level > 0) risky.add(name)
                EngineCircuit.markFailure(name, circuit, ri.level, ri.retryAfterSec, clock())
                circuitDirty = true
            } else {
                totals[name] = r.total
                raw.addAll(r.items)
                if (circuit.containsKey(name)) {
                    EngineCircuit.markSuccess(name, circuit)
                    circuitDirty = true
                }
            }
        }
        if (circuitDirty) onCircuitChanged(circuit)
        return raw
    }

    /** 默认源（不含 ataw）全都没抓到「分享链接形态」的地址时，才值得用 ataw 兜底 */
    private fun shouldAtawFallback(enabledNames: List<String>, raw: List<PanItem>): Boolean {
        if (ATSW in enabledNames) return false
        if (DEFAULT_ENGINES.none { it in enabledNames }) return false
        return raw.none { CloudType.isShareUrl(it.url) }
    }

    /** 错误脱敏+截断：去掉换行、压掉超长 JSON，单行显示（对应 Python clip_err） */
    private fun clip(msg: String, limit: Int = 120): String {
        var s = msg.replace(Regex("""[\r\n\t]+"""), " ").trim()
        s = s.replace(Regex("""\s+"""), " ")
        if (s.length <= limit) return s
        val suffix = "…(共${s.length}字符)"
        return s.take(maxOf(0, limit - suffix.length)).trimEnd() + suffix
    }

    companion object {
        private const val ATSW = "ataw"

        private val DEFAULT_ENGINES = setOf("pansou", "haisou", "yunso", "ataw")

        /**
         * 全部可用引擎。实际跑哪几个由 `SearchOptions.engines` 决定。
         * 域名都在各自 Engine 的构造参数里，想换地址就 `PansouEngine(base = "...")`。
         */
        fun defaultEngines(): List<PanEngine> = listOf(
            PansouEngine(),
            HaisouEngine(),
            YunsoEngine(),
            AtawEngine(),
            PanxiaoziEngine(),
            MovieEngine(),
        )
    }
}

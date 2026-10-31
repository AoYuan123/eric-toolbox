package com.eric.toolbox.modules.pan.core

import org.json.JSONObject

/**
 * 单个引擎的熔断记录。对应 Python 版 `engine_state.json` 里的一个条目。
 *
 * 与 Python 版的唯一差别：那边存的是 ISO 时间字符串，这里存 epoch 毫秒（Android 上用毫秒更省事）。
 */
data class EngineState(
    /** 连续失败次数 */
    var fails: Int = 0,
    /** 最近一次失败时间（毫秒时间戳） */
    var lastFailMs: Long = 0L,
    /** 是否风控型失败（429 / 412 / 验证码页） */
    var risk: Boolean = false,
    /** 风控时的冷却时长（分钟），0 表示用默认值 */
    var cooldownMin: Int = 0,
)

/**
 * 风控分级结果：level 2=强风控（一次即熔断）、1=弱风控（按普通失败计数）、0=普通失败。
 */
data class RiskInfo(val level: Int = 0, val retryAfterSec: Int = 0)

/**
 * 引擎熔断器。对应 Python 的 engine_blocked / mark_engine_failure / mark_engine_success / risk_info。
 *
 * 规则（原样照搬）：
 *   - 普通失败连续 2 次 → 冷却 30 分钟
 *   - 强风控（429/412/验证码）1 次即冷却，时长取 Retry-After，夹在 30 分钟 ~ 24 小时之间
 *   - 成功一次 → 记录清空
 *   - 冷却期满 → 自动解封（并把脏数据就地清掉）
 *
 * 状态怎么持久化不归它管：调用方把 Map 存进 DataStore / SharedPreferences / Room 都行，
 * 用 [toJson] / [fromJson] 可以直接存字符串。
 */
object EngineCircuit {

    const val FAIL_LIMIT = 2
    const val COOLDOWN_MIN = 30
    const val RISK_COOLDOWN_MIN = 30
    const val RISK_COOLDOWN_MAX_MIN = 24 * 60

    private const val MINUTE_MS = 60_000L

    private val RISK_STRONG = Regex(
        "HTTP (?:Error )?(?:412|429)\\b|验证码|人机验证|安全验证|captcha|风控|too many requests|请求过于频繁|rate.?limit",
        RegexOption.IGNORE_CASE,
    )
    private val RISK_WEAK = Regex("HTTP (?:Error )?403\\b", RegexOption.IGNORE_CASE)

    /**
     * 该引擎当前是否应跳过。会就地清理已过冷却期的记录。
     */
    fun isBlocked(
        name: String,
        state: MutableMap<String, EngineState>,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val e = state[name] ?: return false
        // 非风控型：失败次数不够就不熔断
        if (!e.risk && e.fails < FAIL_LIMIT) return false
        if (e.lastFailMs <= 0L) return false

        val cd = if (e.risk) {
            if (e.cooldownMin > 0) e.cooldownMin else RISK_COOLDOWN_MIN
        } else {
            COOLDOWN_MIN
        }
        if (now - e.lastFailMs >= cd * MINUTE_MS) {
            state.remove(name)   // 冷却期满，解封
            return false
        }
        return true
    }

    /** 该引擎是否处于风控冷却中（用于 UI 提示「风控中」而不只是「跳过」） */
    fun isRiskBlocked(
        name: String,
        state: MutableMap<String, EngineState>,
        now: Long = System.currentTimeMillis(),
    ): Boolean = state[name]?.risk == true && isBlocked(name, state, now)

    /** 记录一次失败。level >= 2 时立刻拉到熔断阈值。 */
    fun markFailure(
        name: String,
        state: MutableMap<String, EngineState>,
        level: Int = 0,
        retryAfterSec: Int = 0,
        now: Long = System.currentTimeMillis(),
    ) {
        val e = state[name] ?: EngineState()
        e.fails += 1
        e.lastFailMs = now
        if (level >= 2) {
            e.fails = maxOf(e.fails, FAIL_LIMIT)
            e.risk = true
            e.cooldownMin = riskCooldownMin(retryAfterSec)
        } else {
            e.risk = false
            e.cooldownMin = 0
        }
        state[name] = e
    }

    /** 成功一次就把记录彻底清掉 */
    fun markSuccess(name: String, state: MutableMap<String, EngineState>) {
        state.remove(name)
    }

    /** Retry-After 秒 → 冷却分钟数，夹在 [RISK_COOLDOWN_MIN, RISK_COOLDOWN_MAX_MIN] */
    fun riskCooldownMin(retryAfterSec: Int): Int {
        val ra = retryAfterSec.coerceIn(0, RISK_COOLDOWN_MAX_MIN * 60)
        val minutes = Math.ceil(ra / 60.0).toInt()
        return minutes.coerceIn(RISK_COOLDOWN_MIN, RISK_COOLDOWN_MAX_MIN)
    }

    /**
     * 异常 → 风控分级。先看异常链里的 [HttpException] 状态码，再按消息兜底匹配
     * （ataw / 盘小子会把子请求的错误拼进消息里，兜底匹配是必要的）。
     */
    fun riskInfo(e: Throwable?): RiskInfo {
        var t = e
        var depth = 0
        while (t != null && depth < 6) {
            if (t is HttpException) {
                when (t.status) {
                    429, 412 -> return RiskInfo(2, retryAfterOf(t.message))
                    403 -> return RiskInfo(1, 0)
                }
            }
            t = t.cause
            depth += 1
        }
        return riskInfoOfMessage(e?.message)
    }

    /** 只有错误消息字符串时的分级（引擎把异常吃成 EngineResult.error 之后用这个） */
    fun riskInfoOfMessage(msg: String?): RiskInfo {
        val m = msg ?: return RiskInfo(0, 0)
        if (RISK_STRONG.containsMatchIn(m)) return RiskInfo(2, retryAfterOf(m))
        if (RISK_WEAK.containsMatchIn(m)) return RiskInfo(1, 0)
        return RiskInfo(0, 0)
    }

    private val RETRY_AFTER = Regex("""retry[-_]?after["'\s:=]+(\d+)""", RegexOption.IGNORE_CASE)

    private fun retryAfterOf(msg: String?): Int =
        RETRY_AFTER.find(msg ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    // ------------------------------------------------------------ 持久化

    fun toJson(state: Map<String, EngineState>): String {
        val root = JSONObject()
        for ((name, e) in state) {
            root.put(
                name,
                JSONObject().apply {
                    put("fails", e.fails)
                    put("last_fail_ms", e.lastFailMs)
                    put("risk", e.risk)
                    put("cooldown_min", e.cooldownMin)
                },
            )
        }
        return root.toString()
    }

    fun fromJson(json: String?): MutableMap<String, EngineState> {
        val out = mutableMapOf<String, EngineState>()
        if (json.isNullOrBlank()) return out
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return out
        val keys = root.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val o = root.optJSONObject(name) ?: continue
            out[name] = EngineState(
                fails = o.optInt("fails", 0),
                lastFailMs = o.optLong("last_fail_ms", 0L),
                risk = o.optBoolean("risk", false),
                cooldownMin = o.optInt("cooldown_min", 0),
            )
        }
        return out
    }
}

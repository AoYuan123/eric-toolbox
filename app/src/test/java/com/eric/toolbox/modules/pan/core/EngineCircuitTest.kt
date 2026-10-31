package com.eric.toolbox.modules.pan.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 熔断器回归测试。规则全部来自原实现的 engine_blocked / mark_engine_failure，
 * 时间点固定传入，不依赖真实时钟。
 */
class EngineCircuitTest {

    private val t0 = 1_700_000_000_000L
    private val minute = 60_000L

    @Test
    fun 普通失败一次不熔断() {
        val st = mutableMapOf<String, EngineState>()
        EngineCircuit.markFailure("yunso", st, level = 0, now = t0)
        assertFalse(EngineCircuit.isBlocked("yunso", st, t0))
    }

    @Test
    fun 连续两次失败后熔断三十分钟() {
        val st = mutableMapOf<String, EngineState>()
        EngineCircuit.markFailure("yunso", st, now = t0)
        EngineCircuit.markFailure("yunso", st, now = t0)
        assertTrue(EngineCircuit.isBlocked("yunso", st, t0 + 29 * minute))
        // 到点即解封（>= 判定）
        assertFalse(EngineCircuit.isBlocked("yunso", st, t0 + 30 * minute))
    }

    @Test
    fun 强风控一次就熔断并按RetryAfter计时() {
        val st = mutableMapOf<String, EngineState>()
        EngineCircuit.markFailure("ataw", st, level = 2, retryAfterSec = 3600, now = t0)
        assertTrue(EngineCircuit.isBlocked("ataw", st, t0))
        assertTrue(EngineCircuit.isBlocked("ataw", st, t0 + 59 * minute))
        assertFalse(EngineCircuit.isBlocked("ataw", st, t0 + 60 * minute))
    }

    @Test
    fun 成功一次把记录清零() {
        val st = mutableMapOf<String, EngineState>()
        EngineCircuit.markFailure("haisou", st, level = 2, retryAfterSec = 0, now = t0)
        assertTrue(EngineCircuit.isBlocked("haisou", st, t0))
        EngineCircuit.markSuccess("haisou", st)
        assertFalse(EngineCircuit.isBlocked("haisou", st, t0))
    }

    @Test
    fun 冷却时长夹在半小时到一天之间() {
        assertEquals(30, EngineCircuit.riskCooldownMin(0))
        assertEquals(30, EngineCircuit.riskCooldownMin(60))
        assertEquals(60, EngineCircuit.riskCooldownMin(3600))
        assertEquals(1440, EngineCircuit.riskCooldownMin(10 * 24 * 3600))
    }

    @Test
    fun 状态可以存成JSON再读回来() {
        val st = mutableMapOf<String, EngineState>()
        EngineCircuit.markFailure("pansou", st, level = 2, retryAfterSec = 120, now = t0)
        val back = EngineCircuit.fromJson(EngineCircuit.toJson(st))
        assertEquals(1, back.size)
        assertEquals(true, back["pansou"]?.risk)
        assertTrue(EngineCircuit.isBlocked("pansou", back, t0 + minute))
    }

    @Test
    fun 错误消息分级() {
        assertEquals(2, EngineCircuit.riskInfoOfMessage("HTTP 429 too many requests").level)
        assertEquals(2, EngineCircuit.riskInfoOfMessage("站点返回 验证码 页面").level)
        assertEquals(1, EngineCircuit.riskInfoOfMessage("HTTP 403 Forbidden").level)
        assertEquals(0, EngineCircuit.riskInfoOfMessage("Connection reset by peer").level)
    }
}

package com.eric.toolbox.modules.pan.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 日期归一化：各源给的时间格式五花八门，统一成 yyyy-MM-dd。
 * 支持：
 *   - 毫秒/秒时间戳（小整数或 13 位）
 *   - 2023-09-10T12:00:00Z / +08:00
 *   - 2023-09-10 12:00:00
 *   - 2023-09-10
 * 解析不出来返回空串，UI 上就不显示日期，不要瞎猜。
 */
object Dates {

    private val OUT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun normalize(raw: String?): String {
        val s = (raw ?: "").trim()
        if (s.isEmpty()) return ""

        // 纯数字：可能是秒或毫秒时间戳
        if (s.all { it.isDigit() }) {
            val n = s.toLongOrNull() ?: return ""
            if (n <= 0) return ""
            val millis = if (s.length >= 12) n else n * 1000
            return runCatching {
                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(OUT)
            }.getOrDefault("")
        }

        // ISO 带时区
        if (s.contains('T') && (s.endsWith("Z") || s.contains('+'))) {
            runCatching { Instant.parse(s) }.getOrNull()?.let {
                return it.atZone(ZoneId.systemDefault()).toLocalDate().format(OUT)
            }
            runCatching { java.time.OffsetDateTime.parse(s) }.getOrNull()?.let {
                return it.toLocalDate().format(OUT)
            }
        }

        val head = s.replace(' ', 'T').take(19)
        runCatching { LocalDateTime.parse(head) }.getOrNull()?.let { return it.toLocalDate().format(OUT) }
        runCatching { LocalDate.parse(s.take(10)) }.getOrNull()?.let { return it.format(OUT) }
        return ""
    }

    /** 距今多少小时，用于展示「3 小时前检测」 */
    fun hoursAgo(timestampMs: Long): Long {
        if (timestampMs <= 0L) return Long.MAX_VALUE
        return (System.currentTimeMillis() - timestampMs) / 3_600_000L
    }
}

package com.eric.toolbox.modules.gold

import com.eric.toolbox.core.Http
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.nio.charset.Charset
import kotlin.math.abs
import kotlin.math.pow

/** 一个报价。timeMs 为行情源给出的报价时间（不是本机时间）。 */
data class Quote(
    val source: String,
    val code: String,
    val price: Double,
    val high: Double? = null,
    val low: Double? = null,
    val prevClose: Double? = null,
    val timeMs: Long? = null,
)

data class Snapshot(
    val primary: Quote?,
    val checks: List<Quote>,
    val verified: Boolean,       // 至少一个独立校验源与主源偏差在容差内
    val fresh: Boolean,          // 主源报价时间在 10 分钟内
    val fetchedAtMs: Long,
    val error: String? = null,
) {
    val usableForAlert: Boolean get() = primary != null && verified && fresh
}

/**
 * 行情源（2026-10-30 已实测可用）：
 * - 主源：东方财富 push2 接口，上海金 Au99.99（secid 118.AU9999）
 * - 校验源 1：同接口的黄金 T+D（118.AUTD），容差 1.5%
 * - 校验源 2：新浪财经沪金连续合约 nf_AU0（不同厂商、不同品种），容差 3%
 * 这些是网站公开使用的接口，并非官方开放 API，改版后可能需要更新这里。
 */
object GoldSource {
    private const val EM_URL =
        "https://push2.eastmoney.com/api/qt/ulist.np/get?secids=118.AU9999,118.AUTD&fields=f1,f2,f12,f15,f16,f18,f124"
    private const val SINA_URL = "https://hq.sinajs.cn/list=nf_AU0"
    private const val TOL_TD = 0.015
    private const val TOL_FUT = 0.03
    const val FRESH_MS = 10 * 60_000L

    suspend fun fetch(nowMs: Long = System.currentTimeMillis()): Snapshot = coroutineScope {
        val em = async { runCatching { parseEastmoney(Http.get(EM_URL)) } }
        val sina = async {
            runCatching {
                parseSina(Http.get(SINA_URL, headers = mapOf("Referer" to "https://finance.sina.com.cn/"), charset = Charset.forName("GBK")))
            }
        }
        val emRes = em.await()
        val sinaRes = sina.await()
        val emQuotes = emRes.getOrNull().orEmpty()
        val primary = emQuotes.firstOrNull { it.code == "AU9999" }
        val checks = listOfNotNull(emQuotes.firstOrNull { it.code == "AUTD" }, sinaRes.getOrNull())
        buildSnapshot(primary, checks, nowMs, emRes.exceptionOrNull()?.message)
    }

    fun buildSnapshot(primary: Quote?, checks: List<Quote>, nowMs: Long, error: String? = null): Snapshot {
        val verified = primary != null && checks.any { c ->
            val tol = if (c.code == "AUTD") TOL_TD else TOL_FUT
            abs(c.price - primary.price) / primary.price <= tol
        }
        val fresh = primary?.timeMs?.let { nowMs - it in -FRESH_MS..FRESH_MS } ?: false
        return Snapshot(primary, checks, verified, fresh, nowMs, if (primary == null) (error ?: "主源无数据") else null)
    }

    /** 东方财富：价格为整数，需除以 10^f1（小数位数）；f124 为秒级时间戳。无数据时字段为 "-"。 */
    fun parseEastmoney(body: String): List<Quote> {
        val diff = JSONObject(body).optJSONObject("data")?.optJSONArray("diff") ?: return emptyList()
        val out = mutableListOf<Quote>()
        for (i in 0 until diff.length()) {
            val o = diff.getJSONObject(i)
            val scale = 10.0.pow(o.optInt("f1", 2))
            fun num(k: String): Double? = o.opt(k).let { if (it is Number) it.toDouble() / scale else null }
            val price = num("f2") ?: continue
            if (price <= 0) continue
            val ts = o.opt("f124").let { if (it is Number) it.toLong() * 1000 else null }
            out += Quote("东方财富", o.optString("f12"), price, num("f15"), num("f16"), num("f18"), ts)
        }
        return out
    }

    /** 新浪：var hq_str_nf_AU0="黄金连续,时间,开,高,低,,买,卖,最新,...,日期(第17位),..." */
    fun parseSina(body: String): Quote? {
        val inner = body.substringAfter('"', "").substringBefore('"', "")
        val f = inner.split(',')
        if (f.size < 18) return null
        val price = f[8].toDoubleOrNull() ?: return null
        if (price <= 0) return null
        return Quote("新浪财经", "AU0", price, f[3].toDoubleOrNull(), f[4].toDoubleOrNull())
    }
}

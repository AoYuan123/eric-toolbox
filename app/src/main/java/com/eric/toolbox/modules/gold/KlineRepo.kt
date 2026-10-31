package com.eric.toolbox.modules.gold

import android.content.Context
import com.eric.toolbox.core.Http
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 走势数据仓库：
 * 1. 优先东方财富（大盘 Au99.99）；
 * 2. 连不上时用上金所官网延时行情（也是 Au99.99：当日分钟价、2016-12 起的日线）；
 * 3. 再不行用新浪沪金期货主连（价格有差价，界面会标注）；
 * 4. 都失败时显示上次存在手机上的数据，并注明截至时间。
 *
 * 为避免被行情网站限流：分钟线只增量拉取，日/月线 10 分钟内不重复请求；
 * 东方财富失败后 10 分钟内不再尝试，直接走新浪。
 */
object KlineRepo {
    enum class Src { EM, SGE, SINA }
    enum class Kind(val key: String) { MIN("min"), DAY("day"), MONTH("month") }

    data class Result(val bars: List<Bar>, val src: Src, val fetchedMs: Long, val stale: Boolean, val error: String?)

    private const val SINA_DAY = "https://stock2.finance.sina.com.cn/futures/api/jsonp.php/var%20_k=/InnerFuturesNewService.getDailyKLine?symbol=AU0"
    private const val SINA_MIN = "https://stock2.finance.sina.com.cn/futures/api/jsonp.php/var%20_k=/InnerFuturesNewService.getFewMinLine?symbol=AU0&type=1"
    private val SINA_HDR = mapOf("Referer" to "https://finance.sina.com.cn/")

    private val mem = mutableMapOf<Kind, Result>()
    @Volatile private var emBlockedUntil = 0L

    private fun ttl(k: Kind) = if (k == Kind.MIN) 60_000L else 10 * 60_000L

    suspend fun get(ctx: Context, kind: Kind, now: Long = System.currentTimeMillis()): Result {
        mem[kind]?.let { if (!it.stale && now - it.fetchedMs < ttl(kind)) return it }
        val prev = mem[kind] ?: load(ctx, kind)?.also { mem[kind] = it }
        var emErr: String? = null
        if (now >= emBlockedUntil) {
            try {
                val bars = when (kind) {
                    Kind.MIN -> emMinuteMerged(prev?.takeIf { it.src == Src.EM }?.bars, now)
                    Kind.DAY -> GoldSource.klines(101, 120)
                    Kind.MONTH -> GoldSource.klines(103, 240)
                }
                if (bars.isNotEmpty()) return save(ctx, kind, Result(bars, Src.EM, now, false, null))
                emErr = "东方财富无数据"
            } catch (e: Exception) {
                emErr = e.message ?: "连接失败"
                emBlockedUntil = now + 10 * 60_000L
            }
        } else emErr = "东方财富暂不可用"
        try {
            val bars = when (kind) {
                Kind.MIN -> GoldSource.sgeMinutes(now)
                Kind.DAY -> GoldSource.sgeDaily().takeLast(120)
                Kind.MONTH -> monthly(GoldSource.sgeDaily()).takeLast(240)
            }
            if (bars.isNotEmpty()) return save(ctx, kind, Result(bars, Src.SGE, now, false, emErr))
        } catch (_: Exception) {
        }
        try {
            val bars = sina(kind, now)
            if (bars.isNotEmpty()) return save(ctx, kind, Result(bars, Src.SINA, now, false, emErr))
        } catch (_: Exception) {
        }
        prev?.let { return it.copy(stale = true, error = emErr).also { r -> mem[kind] = r } }
        throw java.io.IOException("行情网站暂时连不上（$emErr），且手机上还没有存过走势数据")
    }

    /** 补漏用：只取东方财富的大盘分钟线（新浪是期货价格，有差价，不能用来判断档位）。 */
    suspend fun emMinutes(minutes: Int, now: Long = System.currentTimeMillis()): List<Bar> {
        if (now < emBlockedUntil) return emptyList()
        return try {
            GoldSource.klines(1, minutes)
        } catch (e: Exception) {
            emBlockedUntil = now + 10 * 60_000L
            emptyList()
        }
    }

    private suspend fun emMinuteMerged(old: List<Bar>?, now: Long): List<Bar> {
        val lastT = old?.lastOrNull()?.timeMs
        if (old.isNullOrEmpty() || lastT == null || now - lastT > 12 * 3600_000L) return GoldSource.klines(1, 900)
        val need = ((now - lastT) / 60_000L + 3).toInt().coerceIn(3, 900)
        return mergeBars(old, GoldSource.klines(1, need), 1500)
    }

    /** 按时间合并，新数据覆盖同一分钟的旧数据。 */
    fun mergeBars(old: List<Bar>, new: List<Bar>, keep: Int): List<Bar> {
        val m = LinkedHashMap<Long, Bar>()
        (old + new).sortedBy { it.timeMs }.forEach { m[it.timeMs] = it }
        return m.values.toList().takeLast(keep)
    }

    private suspend fun sina(kind: Kind, now: Long): List<Bar> = when (kind) {
        Kind.MIN -> parseSina(Http.get(SINA_MIN, SINA_HDR), minute = true, now = now)
        Kind.DAY -> parseSina(Http.get(SINA_DAY, SINA_HDR), minute = false, now = now).takeLast(120)
        Kind.MONTH -> monthly(parseSina(Http.get(SINA_DAY, SINA_HDR), minute = false, now = now)).takeLast(240)
    }

    /** 解析新浪 JSONP：var _k=([{"d":"2026-10-30","o":"..","h":"..","l":"..","c":".."},...]); 丢弃时间在未来的异常条目。 */
    fun parseSina(body: String, minute: Boolean, now: Long): List<Bar> {
        val a = body.indexOf('['); val b = body.lastIndexOf(']')
        if (a < 0 || b <= a) return emptyList()
        val arr = JSONArray(body.substring(a, b + 1))
        val f = SimpleDateFormat(if (minute) "yyyy-MM-dd HH:mm:ss" else "yyyy-MM-dd", Locale.CHINA).apply { timeZone = BJ }
        val out = ArrayList<Bar>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val d = o.optString("d")
            val t = runCatching { f.parse(d)?.time }.getOrNull() ?: continue
            if (t > now + 5 * 60_000L) continue
            val op = o.optString("o").toDoubleOrNull() ?: continue
            val c = o.optString("c").toDoubleOrNull() ?: continue
            val h = o.optString("h").toDoubleOrNull() ?: continue
            val l = o.optString("l").toDoubleOrNull() ?: continue
            if (op <= 0 || c <= 0) continue
            out += Bar(if (minute) d.take(16) else d, t, op, c, h, l)
        }
        return out.sortedBy { it.timeMs }
    }

    /** 日线合成月线。 */
    fun monthly(daily: List<Bar>): List<Bar> = daily.groupBy { it.label.take(7) }.map { (m, ds) ->
        Bar(m, ds.first().timeMs, ds.first().open, ds.last().close, ds.maxOf { it.high }, ds.minOf { it.low })
    }.sortedBy { it.timeMs }

    // ---------- 手机本地缓存 ----------

    private fun file(ctx: Context, k: Kind) = File(ctx.filesDir, "kline_${k.key}.json")

    private fun save(ctx: Context, k: Kind, r: Result): Result {
        mem[k] = r
        runCatching {
            val arr = JSONArray()
            r.bars.forEach { arr.put(JSONArray().put(it.label).put(it.timeMs).put(it.open).put(it.close).put(it.high).put(it.low)) }
            file(ctx, k).writeText(JSONObject().put("src", r.src.name).put("t", r.fetchedMs).put("bars", arr).toString())
        }
        return r
    }

    private fun load(ctx: Context, k: Kind): Result? = runCatching {
        val f = file(ctx, k)
        if (!f.isFile) return null
        val o = JSONObject(f.readText())
        val arr = o.getJSONArray("bars")
        val bars = (0 until arr.length()).map { i ->
            val b = arr.getJSONArray(i)
            Bar(b.getString(0), b.getLong(1), b.getDouble(2), b.getDouble(3), b.getDouble(4), b.getDouble(5))
        }
        Result(bars, Src.valueOf(o.getString("src")), o.getLong("t"), stale = true, error = null)
    }.getOrNull()
}

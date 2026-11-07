package com.eric.toolbox.modules.trials

import android.content.Context
import com.eric.toolbox.core.Http
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 临床试验英文翻译成中文：百度翻译开放平台「通用文本翻译」（高级版，个人实名认证后每月 100 万字符免费，
 * 超出按 49 元/百万字符计费——11-02 官方定价页）。
 * - APP ID 和密钥由用户在 App 里填写，存在 "trials_secret" 分区，不进备份。
 * - 同一句话只翻译一次，结果存在手机上（trans_cache.json）。
 * - App 自己统计本月用量，到上限（默认 80 万字符）就停，只显示英文，避免超出免费额度产生费用。
 */
object Translator {
    const val API = "https://fanyi-api.baidu.com/api/trans/vip/translate"
    const val DEFAULT_CAP = 800_000
    private const val BATCH_CHARS = 4500   // 高级版单次请求上限 6000 字符，留余量
    private const val BATCH_LINES = 40

    private val lock = Mutex()
    private var cache: MutableMap<String, String>? = null

    private fun secret(ctx: Context) = ctx.getSharedPreferences("trials_secret", Context.MODE_PRIVATE)
    private fun prefs(ctx: Context) = TrialModule.prefs(ctx)

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("tr_enabled", true)
    fun setEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("tr_enabled", on).apply()
    fun appId(ctx: Context) = secret(ctx).getString("appid", "") ?: ""
    fun hasKey(ctx: Context) = !secret(ctx).getString("key", null).isNullOrBlank()
    fun setCredentials(ctx: Context, appId: String?, key: String?) {
        val e = secret(ctx).edit()
        appId?.let { e.putString("appid", it.trim()) }
        key?.let { if (it.isNotBlank()) e.putString("key", it.trim()) }
        e.apply()
    }
    fun ready(ctx: Context) = enabled(ctx) && appId(ctx).isNotBlank() && hasKey(ctx)

    fun cap(ctx: Context) = prefs(ctx).getInt("tr_cap", DEFAULT_CAP)
    fun setCap(ctx: Context, v: Int) = prefs(ctx).edit().putInt("tr_cap", v.coerceIn(10_000, 1_000_000)).apply()

    private fun month() = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }.format(Date())
    fun usedThisMonth(ctx: Context): Int = if (prefs(ctx).getString("tr_month", "") == month()) prefs(ctx).getInt("tr_chars", 0) else 0
    private fun addUsage(ctx: Context, n: Int) {
        prefs(ctx).edit().putString("tr_month", month()).putInt("tr_chars", usedThisMonth(ctx) + n).apply()
    }
    fun lastError(ctx: Context): String? = prefs(ctx).getString("tr_err", null)
    private fun setError(ctx: Context, e: String?) = prefs(ctx).edit().putString("tr_err", e).apply()

    // ---------- 本地缓存 ----------

    private fun file(ctx: Context) = File(ctx.filesDir, "trans_cache.json")

    private fun load(ctx: Context): MutableMap<String, String> {
        cache?.let { return it }
        val m = LinkedHashMap<String, String>()
        runCatching {
            val o = JSONObject(file(ctx).readText())
            o.keys().forEach { k -> m[k] = o.getString(k) }
        }
        cache = m
        return m
    }

    private fun persist(ctx: Context, m: MutableMap<String, String>) {
        // 只留最近 5000 条，避免无限增长
        while (m.size > 5000) m.remove(m.keys.first())
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k, v) }
        runCatching { file(ctx).writeText(o.toString()) }
    }

    /** 已有的中文（没翻译过返回 null，不联网）。 */
    fun cached(ctx: Context, en: String): String? = if (enabled(ctx)) load(ctx)[norm(en)] else null

    private fun norm(s: String) = s.replace(Regex("\\s+"), " ").trim()

    /**
     * 把还没翻译过的句子翻译掉。返回本次新翻译的条数。
     * 不满足条件（没开、没填密钥、本月到上限）时直接返回 0，不报错。
     */
    suspend fun translateMissing(ctx: Context, texts: Collection<String>): Int = lock.withLock {
        if (!ready(ctx)) return 0
        val m = load(ctx)
        val todo = texts.map { norm(it) }.filter { it.isNotBlank() && it !in m }.distinct()
        if (todo.isEmpty()) return 0
        var done = 0
        for (batch in batches(todo)) {
            val q = batch.joinToString("\n")
            if (usedThisMonth(ctx) + q.length > cap(ctx)) {
                setError(ctx, "本月已用 ${usedThisMonth(ctx)} 字符，到达上限 ${cap(ctx)}，暂停翻译（下月自动恢复）")
                break
            }
            val res = try {
                request(appId(ctx), secret(ctx).getString("key", "") ?: "", q)
            } catch (e: Exception) {
                setError(ctx, e.message ?: "网络错误")
                break
            }
            addUsage(ctx, q.length)
            res.forEach { (src, dst) -> m[norm(src)] = dst }
            done += res.size
            setError(ctx, null)
            delay(150) // 高级版 QPS=10，留余量
        }
        persist(ctx, m)
        done
    }

    // ---------- 翻译板块：任意方向、整段翻译 ----------

    /** 只要填了 APP ID 和密钥就能用（不受临床试验里“翻译开关”影响）。 */
    fun hasCredentials(ctx: Context) = appId(ctx).isNotBlank() && hasKey(ctx)

    private fun dirKey(from: String, to: String, line: String) = if (from == "en" && to == "zh") norm(line) else "$from>$to|${norm(line)}"

    /**
     * 翻译一段文字（可多行，保留分行）。from 可为 auto。返回 (译文, 错误)。
     * 每行单独缓存：同一句话翻过一次，再翻不联网、不计额度。
     */
    suspend fun translateText(ctx: Context, text: String, from: String, to: String): Pair<String?, String?> = lock.withLock {
        if (!hasCredentials(ctx)) return null to "还没填百度翻译的 APP ID 和密钥：到「临床试验 › 翻译设置」填写（各板块共用）"
        val lines = text.replace("\r\n", "\n").split('\n')
        val m = load(ctx)
        val todo = lines.map { it.trim() }.filter { it.isNotEmpty() && dirKey(from, to, it) !in m }.distinct()
        // 超长的行（> 单次上限）切成小块
        val pieces = todo.flatMap { l -> if (l.length <= BATCH_CHARS) listOf(l) else l.chunked(BATCH_CHARS) }
        var err: String? = null
        for (batch in batches(pieces)) {
            val q = batch.joinToString("\n")
            if (usedThisMonth(ctx) + q.length > cap(ctx)) { err = "本月已用 ${usedThisMonth(ctx)} 字符，到达上限 ${cap(ctx)}（可在临床试验 › 翻译设置里调整）"; break }
            val res = try { request(appId(ctx), secret(ctx).getString("key", "") ?: "", q, from, to) } catch (e: Exception) { err = e.message ?: "网络错误"; break }
            addUsage(ctx, q.length)
            res.forEach { (src, dst) -> m[dirKey(from, to, src)] = dst }
            delay(150)
        }
        persist(ctx, m)
        val out = lines.map { l -> val t = l.trim(); if (t.isEmpty()) "" else m[dirKey(from, to, t)] ?: (if (t.length > BATCH_CHARS) t.chunked(BATCH_CHARS).joinToString("") { m[dirKey(from, to, it)] ?: "" } else null) }
        if (out.any { it == null }) return (null to (err ?: "部分内容没翻译出来，请重试"))
        out.joinToString("\n").trim() to null
    }

    fun monthUsed(ctx: Context) = usedThisMonth(ctx)

    /** 测试：翻译一句示例标题。 */
    suspend fun test(ctx: Context): String {
        if (appId(ctx).isBlank() || !hasKey(ctx)) return "请先填写 APP ID 和密钥"
        val sample = "Comparing Retreatment of 177Lu-DOTATATE PRRT Versus the Usual Treatment"
        return try {
            val r = request(appId(ctx), secret(ctx).getString("key", "") ?: "", sample)
            addUsage(ctx, sample.length)
            setError(ctx, null)
            "翻译成功：${r.firstOrNull()?.second ?: "（无结果）"}"
        } catch (e: Exception) {
            setError(ctx, e.message); "翻译失败：${e.message}"
        }
    }

    private suspend fun request(appId: String, key: String, q: String, from: String = "en", to: String = "zh"): List<Pair<String, String>> {
        val salt = System.currentTimeMillis().toString()
        val form = "q=${enc(q)}&from=$from&to=$to&appid=${enc(appId)}&salt=$salt&sign=${sign(appId, q, salt, key)}"
        return parse(Http.postForm(API, form, timeoutMs = 15_000))
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---------- 纯函数（可单测） ----------

    /** 签名 = MD5(appid + q + salt + 密钥)，q 用原文（UTF-8），不做 URL 编码。 */
    fun sign(appId: String, q: String, salt: String, key: String): String =
        MessageDigest.getInstance("MD5").digest((appId + q + salt + key).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** 解析返回：{"from":"en","to":"zh","trans_result":[{"src":"…","dst":"…"}]}；出错时为 {"error_code":"54001","error_msg":"…"}。 */
    fun parse(body: String): List<Pair<String, String>> {
        val o = JSONObject(body)
        if (o.has("error_code") && o.optString("error_code") != "52000") {
            val c = o.optString("error_code")
            throw java.io.IOException(errorText(c) + "（$c）")
        }
        val arr = o.optJSONArray("trans_result") ?: throw java.io.IOException("翻译结果为空")
        return (0 until arr.length()).map { arr.getJSONObject(it).let { r -> r.optString("src") to r.optString("dst") } }
    }

    fun errorText(code: String) = when (code) {
        "52001" -> "请求超时，请重试"
        "52002" -> "百度翻译系统错误，请重试"
        "52003" -> "APP ID 不对或服务未开通"
        "54000" -> "缺少必填参数"
        "54001" -> "签名错误，请检查密钥"
        "54003" -> "访问太频繁，请稍后再试"
        "54004" -> "账户余额不足"
        "54005" -> "长文本请求太频繁"
        "58000" -> "客户端 IP 不在百度后台允许范围内"
        "58001" -> "不支持该语言方向"
        "58002" -> "服务已关闭，请到百度翻译控制台开启"
        "90107" -> "认证未通过或未生效"
        else -> "翻译失败"
    }

    /** 按字符数和行数分批。 */
    fun batches(lines: List<String>): List<List<String>> {
        val out = mutableListOf<List<String>>()
        var cur = mutableListOf<String>(); var len = 0
        for (l in lines) {
            val add = l.length + 1
            if (cur.isNotEmpty() && (len + add > BATCH_CHARS || cur.size >= BATCH_LINES)) { out += cur; cur = mutableListOf(); len = 0 }
            cur += l; len += add
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }
}

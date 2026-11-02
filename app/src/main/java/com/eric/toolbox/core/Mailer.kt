package com.eric.toolbox.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** 一个收件人：收哪些类别的提醒（gold 金价 / trials 临床试验 / system 系统）。 */
data class MailRecipient(val address: String, val note: String, val categories: Set<String>)

data class MailConfig(
    val enabled: Boolean,
    val alsoNotify: Boolean,      // 同时弹手机通知
    val from: String,             // 发件 QQ 邮箱
    val recipients: List<MailRecipient>,
)

/**
 * 邮件提醒：用用户自己的 QQ 邮箱（SMTP + 授权码）给收件人发信。
 * - 服务器 smtp.qq.com:465，SSL；用户名为完整邮箱，密码为 16 位授权码。
 * - 授权码单独存在 "mail_secret" 分区，不在备份导出范围内；其余设置存 global，可备份。
 * - 每个收件人单独发一封（互相看不到对方地址）；失败重试 3 次，仍失败写进「记录」。
 */
object Mailer {
    const val HOST = "smtp.qq.com"
    const val PORT = 465
    val CATEGORIES = linkedMapOf("gold" to "金价", "trials" to "临床试验", "system" to "系统")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun secret(ctx: Context) = ctx.getSharedPreferences("mail_secret", Context.MODE_PRIVATE)

    fun config(ctx: Context): MailConfig {
        val p = Store.global(ctx)
        return MailConfig(
            enabled = p.getBoolean("mail_enabled", false),
            alsoNotify = p.getBoolean("mail_also_notify", true),
            from = p.getString("mail_from", "") ?: "",
            recipients = parseRecipients(p.getString("mail_rcpts", "[]") ?: "[]"),
        )
    }

    fun save(ctx: Context, c: MailConfig) {
        Store.global(ctx).edit().putBoolean("mail_enabled", c.enabled).putBoolean("mail_also_notify", c.alsoNotify)
            .putString("mail_from", c.from.trim()).putString("mail_rcpts", recipientsJson(c.recipients)).apply()
    }

    fun hasAuthCode(ctx: Context) = !secret(ctx).getString("code", null).isNullOrBlank()
    fun setAuthCode(ctx: Context, code: String) = secret(ctx).edit().putString("code", code.replace(" ", "").trim()).apply()

    fun lastStatus(ctx: Context): String? = Store.global(ctx).getString("mail_last", null)
    private fun setStatus(ctx: Context, s: String) = Store.global(ctx).edit().putString("mail_last", s).apply()

    /** 设置齐全、开着邮件提醒时返回 true。 */
    fun ready(ctx: Context): Boolean {
        val c = config(ctx)
        return c.enabled && isEmail(c.from) && hasAuthCode(ctx) && c.recipients.isNotEmpty()
    }

    /** 邮件提醒开着时，是否还要弹手机通知。 */
    fun shouldNotifyPhone(ctx: Context) = !ready(ctx) || config(ctx).alsoNotify

    fun isEmail(s: String) = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""").matches(s.trim())

    /** 发提醒邮件（后台进行，不阻塞调用方）。category 见 [CATEGORIES]。 */
    fun sendAlert(ctx: Context, category: String, title: String, body: String) {
        if (!ready(ctx)) return
        val c = config(ctx)
        val to = c.recipients.filter { category in it.categories && isEmail(it.address) }
        if (to.isEmpty()) return
        val app = ctx.applicationContext
        val stamp = fmt("MM-dd HH:mm:ss", System.currentTimeMillis())
        scope.launch {
            for (r in to) {
                var err: Exception? = null
                for (attempt in 0 until 4) {
                    try {
                        send(app, r.address, "【百宝箱】$title", "$body\n\n时间：$stamp\n（来自 Eric的百宝箱）")
                        err = null
                        break
                    } catch (e: Exception) {
                        err = e
                        delay(listOf(10_000L, 30_000L, 60_000L).getOrElse(attempt) { 0L })
                    }
                }
                if (err == null) {
                    setStatus(app, "上次发送 ${fmt("MM-dd HH:mm", System.currentTimeMillis())} 成功")
                } else {
                    val msg = err.message ?: "网络错误"
                    setStatus(app, "上次发送 ${fmt("MM-dd HH:mm", System.currentTimeMillis())} 失败：$msg")
                    AlertLog.add(app, AlertEntry(System.currentTimeMillis(), AlertLog.SYSTEM, "邮件发送失败（${r.address}）", "「$title」没能发出：$msg"))
                }
            }
        }
    }

    /** 测试邮件：发给全部收件人，返回结果说明。 */
    suspend fun sendTest(ctx: Context): String {
        val c = config(ctx)
        if (!isEmail(c.from)) return "请先填写正确的 QQ 邮箱"
        if (!hasAuthCode(ctx)) return "请先填写授权码"
        if (c.recipients.isEmpty()) return "请先添加收件人"
        val fails = mutableListOf<String>()
        for (r in c.recipients) {
            try {
                send(ctx, r.address, "【百宝箱】测试邮件", "能收到这封邮件，说明百宝箱的邮件提醒可以正常送达。\n\n这个收件人会收到：${r.categories.mapNotNull { CATEGORIES[it] }.joinToString("、").ifBlank { "（没有选任何类别）" }}")
            } catch (e: Exception) {
                fails += "${r.address}：${e.message ?: "网络错误"}"
            }
        }
        val now = fmt("MM-dd HH:mm", System.currentTimeMillis())
        return if (fails.isEmpty()) {
            setStatus(ctx, "上次发送 $now 成功"); "已发送给 ${c.recipients.size} 个收件人，请查收"
        } else {
            setStatus(ctx, "上次发送 $now 失败：${fails.first()}"); "发送失败：\n" + fails.joinToString("\n")
        }
    }

    /** 通过 SMTP 发一封纯文本邮件。 */
    suspend fun send(ctx: Context, to: String, subject: String, body: String) = withContext(Dispatchers.IO) {
        val from = config(ctx).from
        val code = secret(ctx).getString("code", "") ?: ""
        val sock = SSLSocketFactory.getDefault().createSocket(HOST, PORT) as SSLSocket
        sock.soTimeout = 20_000
        sock.use { s ->
            // 校验证书确实属于 smtp.qq.com，防止授权码被中间人截获
            s.startHandshake()
            if (!javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(HOST, s.session)) {
                throw java.io.IOException("邮件服务器证书不匹配，已停止发送")
            }
            val input = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val out = s.getOutputStream()
            expect(input, 220)
            cmd(out, input, "EHLO eric-toolbox", 250)
            cmd(out, input, "AUTH LOGIN", 334)
            cmd(out, input, b64(from), 334)
            try {
                cmd(out, input, b64(code), 235)
            } catch (e: SmtpException) {
                throw java.io.IOException("邮箱或授权码不对（${e.code}），请检查授权码是否已开启 SMTP 服务")
            }
            cmd(out, input, "MAIL FROM:<$from>", 250)
            cmd(out, input, "RCPT TO:<$to>", 250)
            cmd(out, input, "DATA", 354)
            write(out, buildMessage(from, to, subject, body, System.currentTimeMillis()) + "\r\n.\r\n")
            expect(input, 250)
            runCatching { write(out, "QUIT\r\n") }
        }
    }

    class SmtpException(val code: Int, msg: String) : java.io.IOException(msg)

    private fun write(out: OutputStream, s: String) { out.write(s.toByteArray(Charsets.UTF_8)); out.flush() }

    private fun cmd(out: OutputStream, input: BufferedReader, line: String, ok: Int): String {
        write(out, "$line\r\n")
        return expect(input, ok)
    }

    /** 读一条（可能多行）SMTP 回复，回复码不是 ok 就抛错。 */
    private fun expect(input: BufferedReader, ok: Int): String {
        val sb = StringBuilder()
        while (true) {
            val l = input.readLine() ?: throw java.io.IOException("邮件服务器断开连接")
            sb.append(l).append('\n')
            if (l.length < 4 || l[3] != '-') {
                val code = l.take(3).toIntOrNull() ?: throw java.io.IOException("邮件服务器回复异常")
                if (code != ok) throw SmtpException(code, "邮件服务器拒绝：${l.take(120)}")
                return sb.toString()
            }
        }
    }

    // ---------- 纯函数（可单测） ----------

    fun b64(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    /** 邮件头里的中文：=?UTF-8?B?…?= */
    fun encodeHeader(s: String): String = if (s.all { it.code < 128 }) s else "=?UTF-8?B?${b64(s)}?="

    /** 组装 MIME 邮件（正文 UTF-8 + base64，每行 76 字符）。 */
    fun buildMessage(from: String, to: String, subject: String, body: String, timeMs: Long): String {
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(Date(timeMs))
        val encoded = b64(body.replace("\r\n", "\n").replace("\n", "\r\n")).chunked(76).joinToString("\r\n")
        return listOf(
            "From: ${encodeHeader("Eric的百宝箱")} <$from>",
            "To: <$to>",
            "Subject: ${encodeHeader(subject)}",
            "Date: $date",
            "MIME-Version: 1.0",
            "Content-Type: text/plain; charset=UTF-8",
            "Content-Transfer-Encoding: base64",
            "",
            encoded,
        ).joinToString("\r\n")
    }

    fun recipientsJson(list: List<MailRecipient>): String {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("a", it.address.trim()).put("n", it.note).put("c", JSONArray(it.categories.toList()))) }
        return a.toString()
    }

    fun parseRecipients(s: String): List<MailRecipient> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val c = o.optJSONArray("c") ?: JSONArray()
            MailRecipient(o.optString("a"), o.optString("n"), (0 until c.length()).map { c.optString(it) }.toSet())
        }
    }.getOrDefault(emptyList())

    private fun fmt(p: String, ms: Long) = SimpleDateFormat(p, Locale.CHINA).apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }.format(Date(ms))
}

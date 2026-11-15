package com.eric.toolbox.modules.gold

import com.eric.toolbox.core.Http
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 消息面：会影响金价波动的实时快讯。
 *
 * 两个源，互为备份：
 * 1. 金十数据 flash-api（channel=-8200 = 全部快讯）：地缘、美联储、数据发布都在这里，
 *    带 important 标记；VIP 加密的条目内容为空，直接丢弃。
 * 2. 华尔街见闻 gold-channel（黄金频道）：数据类快讯更全，带 score 重要级别。
 *
 * 站点改版后这里的地址/头部会失效，届时只需要改这个文件。
 *
 * 打标签是按关键词规则判断的（不花钱、离线也能算），所以界面上要写明"自动判断"：
 * 「不急于降息」「降息预期降温」这类否定句式必须排在「降息」前面判断，否则会把利空
 * 标成利多——这是规则判定最容易错的地方，单测里锁了例子。
 */
enum class Impact(val label: String) {
    BULL("利多"),
    BEAR("利空"),
    DATA("数据"),
    NEUTRAL("中性"),
}

data class GoldNewsItem(
    val atMs: Long,
    val text: String,
    val source: String,
    val url: String,
    val important: Boolean,
    val impact: Impact,
    /** 判定依据，如「避险」「鹰派」，界面上跟标签一起显示 */
    val reason: String = "",
)

object GoldNews {

    private const val JIN10 = "https://flash-api.jin10.com/get_flash_list?channel=-8200&vip=1"
    private const val WSCN = "https://api-one.wallstcn.com/apiv1/content/lives?channel=gold-channel&limit=50"

    /** 金十网页版公开使用的头部；失效时不影响见闻那条源 */
    private val JIN10_HDR = mapOf(
        "x-app-id" to "bVBF4FyRTn5NJF5n",
        "x-version" to "1.0.0",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14)",
    )
    private val WSCN_HDR = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 14)")

    const val MAX = 40

    data class Result(val items: List<GoldNewsItem>, val error: String? = null)

    suspend fun fetch(now: Long = System.currentTimeMillis()): Result = coroutineScope {
        val jin = async { runCatching { parseJin10(Http.get(JIN10, JIN10_HDR)) } }
        val wscn = async { runCatching { parseWscn(Http.get(WSCN, WSCN_HDR)) } }
        val a = jin.await()
        val b = wscn.await()
        val errors = buildList {
            a.exceptionOrNull()?.let { add("金十：${it.message ?: "网络错误"}") }
            b.exceptionOrNull()?.let { add("见闻：${it.message ?: "网络错误"}") }
        }
        val items = merge(a.getOrDefault(emptyList()), b.getOrDefault(emptyList()))
        Result(items, if (items.isEmpty()) errors.joinToString("；").ifBlank { "两个源都没有返回内容" } else null)
    }

    // ------------------------------------------------------------------ 解析

    /** 金十：data[] 每项 { time:"yyyy-MM-dd HH:mm:ss", important:0/1, data:{content,title} } */
    fun parseJin10(body: String): List<GoldNewsItem> {
        val arr = JSONObject(body).optJSONArray("data") ?: return emptyList()
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).apply { timeZone = BJ }
        val out = ArrayList<GoldNewsItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val d = o.optJSONObject("data") ?: continue
            // VIP 加密条目：data 里只有 vip_title，没有正文
            if (d.optString("lock").isNotBlank() || d.optString("vip_title").isNotBlank()) continue
            val text = clean(d.optString("content").ifBlank { d.optString("title") })
            if (text.isBlank()) continue
            val t = runCatching { f.parse(o.optString("time"))?.time }.getOrNull() ?: continue
            val id = o.optLong("id", 0)
            val impact = classify(text)
            out += GoldNewsItem(
                atMs = t,
                text = text,
                source = "金十",
                url = if (id > 0) "https://flash.jin10.com/detail/$id" else "https://www.jin10.com/",
                important = o.optInt("important", 0) == 1,
                impact = impact.first,
                reason = impact.second,
            )
        }
        return out
    }

    /** 见闻：data.items[] 每项 { content(HTML), display_time(秒), score, uri } */
    fun parseWscn(body: String): List<GoldNewsItem> {
        val arr = JSONObject(body).optJSONObject("data")?.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<GoldNewsItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val text = clean(o.optString("content").ifBlank { o.optString("title") })
            if (text.isBlank()) continue
            val t = o.optLong("display_time", 0) * 1000
            if (t <= 0) continue
            val score = o.optInt("score", 0)
            val impact = classify(text)
            out += GoldNewsItem(
                atMs = t,
                text = text,
                source = "见闻",
                url = o.optString("uri").ifBlank { "https://wallstreetcn.com/livenews" },
                important = score >= 3,
                impact = impact.first,
                reason = impact.second,
            )
        }
        return out
    }

    fun clean(html: String): String = html
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(240)

    // -------------------------------------------------------------- 相关性

    /** 与金价相关的关键词（宏观、美元、地缘、央行）。命中即收，不看重要级别。 */
    private val GOLD_WORDS = listOf(
        "黄金", "金价", "贵金属", "白银", "美联储", "降息", "加息", "利率决议", "鹰派", "鸽派",
        "通胀", "CPI", "PPI", "非农", "就业", "GDP", "PMI", "美元", "美债", "收益率",
        "避险", "地缘", "冲突", "战争", "袭击", "制裁", "关税", "央行", "购金", "ETF",
        "俄乌", "中东", "伊朗", "以色列", "霍尔木兹", "衰退", "鲍威尔", "白宫", "财政部",
    )

    fun goldRelated(text: String): Boolean = GOLD_WORDS.any { text.contains(it, ignoreCase = true) }

    // ---------------------------------------------------------------- 打标

    /**
     * 有序规则，**先命中先算**。
     *
     * 顺序就是语义优先级：否定句（不急于降息 / 预期降温）必须排在「降息」前面，
     * 否则「降息预期降温」会先被「降息」抓住、标成利多——这是规则打标最典型的错误。
     * 同理「停火 / 缓和」要排在「冲突」前面：消息里常常两者同时出现，缓和才是方向。
     */
    private val RULES: List<Triple<String, Impact, String>> = listOf(
        Triple("不急于降息", Impact.BEAR, "鹰派"),
        Triple("降息预期降温", Impact.BEAR, "预期降温"),
        Triple("降息预期减弱", Impact.BEAR, "预期降温"),
        Triple("降息预期下降", Impact.BEAR, "预期降温"),
        Triple("推迟降息", Impact.BEAR, "推迟降息"),
        Triple("加息预期升温", Impact.BEAR, "加息预期"),
        Triple("加息", Impact.BEAR, "加息"),
        Triple("鹰派", Impact.BEAR, "鹰派"),
        Triple("缩表", Impact.BEAR, "缩表"),
        Triple("美元走强", Impact.BEAR, "美元走强"),
        Triple("美元上涨", Impact.BEAR, "美元走强"),
        Triple("美元指数反弹", Impact.BEAR, "美元走强"),
        Triple("收益率上升", Impact.BEAR, "收益率上行"),
        Triple("收益率上涨", Impact.BEAR, "收益率上行"),
        Triple("非农强劲", Impact.BEAR, "就业强劲"),
        Triple("就业强劲", Impact.BEAR, "就业强劲"),
        Triple("数据好于预期", Impact.BEAR, "数据强劲"),
        Triple("数据超预期", Impact.BEAR, "数据强劲"),
        Triple("停火", Impact.BEAR, "避险降温"),
        Triple("缓和", Impact.BEAR, "避险降温"),
        Triple("风险偏好回升", Impact.BEAR, "避险降温"),
        Triple("降息", Impact.BULL, "降息"),
        Triple("鸽派", Impact.BULL, "鸽派"),
        Triple("避险", Impact.BULL, "避险"),
        Triple("冲突", Impact.BULL, "地缘"),
        Triple("战争", Impact.BULL, "地缘"),
        Triple("袭击", Impact.BULL, "地缘"),
        Triple("制裁", Impact.BULL, "制裁"),
        Triple("衰退", Impact.BULL, "衰退"),
        Triple("购金", Impact.BULL, "央行购金"),
        Triple("增持", Impact.BULL, "增持"),
        Triple("美元走弱", Impact.BULL, "美元走弱"),
        Triple("美元下跌", Impact.BULL, "美元走弱"),
        Triple("收益率下跌", Impact.BULL, "收益率下行"),
        Triple("收益率下降", Impact.BULL, "收益率下行"),
        Triple("不及预期", Impact.BULL, "数据疲弱"),
    )

    private val DATA_HINTS = listOf("预期", "前值", "公布", "数据", "讲话", "会议纪要", "利率决议", "初值", "库存")

    fun classify(text: String): Pair<Impact, String> {
        RULES.firstOrNull { text.contains(it.first) }?.let { return it.second to it.third }
        return if (DATA_HINTS.any { text.contains(it) }) Impact.DATA to "待观察"
        else Impact.NEUTRAL to ""
    }

    // ---------------------------------------------------------------- 合并

    /**
     * 合并两个源：时间倒序、按正文前 16 字去重（两家常报同一条数据）、
     * 只保留"与金价相关"或"重要"的条目。
     */
    fun merge(a: List<GoldNewsItem>, b: List<GoldNewsItem>, max: Int = MAX): List<GoldNewsItem> {
        val kept = (a + b)
            .filter { it.important || goldRelated(it.text) }
            .sortedByDescending { it.atMs }
        val seen = HashSet<String>()
        val out = ArrayList<GoldNewsItem>(minOf(kept.size, max))
        for (it in kept) {
            val k = it.text.replace(Regex("[\\s，。、：；！？,.%（）()【】\\[\\]]"), "").take(16)
            if (k.isBlank() || !seen.add(k)) continue
            out += it
            if (out.size >= max) break
        }
        return out
    }

    /** 界面上显示的相对时间：刚刚 / n 分钟前 / HH:mm / MM-dd HH:mm */
    fun timeText(atMs: Long, now: Long = System.currentTimeMillis()): String {
        val d = now - atMs
        return when {
            d < 60_000L -> "刚刚"
            d < 3600_000L -> "${d / 60_000L} 分钟前"
            d < 6 * 3600_000L -> hm(atMs)
            else -> mmdd(atMs)
        }
    }

    private fun hm(t: Long) = SimpleDateFormat("HH:mm", Locale.CHINA).apply { timeZone = BJ }.format(Date(t))
    private fun mmdd(t: Long) = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).apply { timeZone = BJ }.format(Date(t))
}

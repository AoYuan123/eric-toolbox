package com.eric.toolbox.modules.family

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 时间表：什么时候该做什么。
 *
 * 这张表不是"待办清单"，它只回答一个问题——下一件事是什么、大概什么时候。
 * 所以每条都带一句"为什么是这个时间"，没有一句是"你要加油"。
 *
 * 依据（写下来免得以后被当成拍脑袋）：
 *  - 孕期产检节奏与项目：《孕前和孕期保健指南（2018）》＋国家基本公共卫生服务规范（第三版）
 *    的 5 次孕期保健，叠加国内医院实际执行的 7–11 次产检。
 *  - 0–6 岁儿童保健 8 次：国家基本公共卫生服务规范（第三版），满月/3/6/8/12/18/24/30/36 月龄。
 *  - 疫苗：国家免疫规划疫苗儿童免疫程序（2021 年版）。各省加针（如水痘、轮状）不写死在这里，
 *    因为各地不一样，写了就会跟接种门诊的单子对不上。
 */
enum class Chapter(val title: String, val hint: String) {
    EARLY("孕早期", "先确认宫内、有心跳，再把建档和 NT 排上"),
    MID("孕中期", "最舒服的一段，也是检查最密的一段"),
    LATE("孕晚期", "两周一次，最后一个月改成每周一次"),
    BIRTH("出生与满月", "第一针疫苗在产房就打，满月去体检"),
    HALF("半岁前", "几乎每个月都有针或体检"),
    ONE("半岁到一岁", "麻腮风、乙脑都在这段，第一次发烧多半也在这段"),
    TOD("一岁到三岁", "针变少了，主要看走路、说话、吃饭"),
    BIG("三岁以后", "入园体检、视力筛查，一年一次就够"),
}

/**
 * 时间锚点。刻意不用绝对日期——预产期和生日都是用户自己填的，
 * 表里的每一条都应该跟着它们自动走。
 */
sealed class Anchor {
    /** 孕 w 周 d 天。 */
    data class Gest(val week: Int, val day: Int = 0) : Anchor()

    /** 出生后第几个月第几天。 */
    data class Post(val months: Int, val days: Int = 0) : Anchor()
}

/**
 * weight：0 必须做（产检、疫苗），1 建议做（数胎动、待产包），2 知道一下就行（预产期、百天）。
 * 只有 0 和 1 会出通知——"知道就行"的东西一响铃就变成催命。
 */
data class PlanItem(
    val key: String,
    val title: String,
    val note: String,
    val weight: Int,
    val anchor: Anchor,
)

data class PlanRow(
    val item: PlanItem,
    val due: LocalDate,
    val done: Boolean,
)

object FamilyPlan {
    const val MUST = 0
    const val SHOULD = 1
    const val KNOW = 2

    /**
     * 全表。顺序即阅读顺序，日期由锚点算出来后再排一次序，所以这里不用严格按时间写。
     * key 一旦发出去就不能改——用户划掉的记录按 key 存，改了等于把记录弄丢。
     */
    val ALL: List<PlanItem> = listOf(
        // ---------------- 孕早期 ----------------
        PlanItem(
            "g_scan", "第一次 B 超",
            "孕 6–8 周。看是不是宫内、有没有胎心。去太早可能还看不到，白紧张一次。",
            MUST, Anchor.Gest(6),
        ),
        PlanItem(
            "g_record", "建档 + 首次产检",
            "最迟 13 周前。空腹去，一次抽好几管血：血常规、肝肾功能、甲功、血型、传染病筛查。",
            MUST, Anchor.Gest(9),
        ),
        PlanItem(
            "g_nt", "NT 检查",
            "11 周–13 周+6 天，B 超量颈后透明层。过了这段就做不了，是孕期少数几个「过期不候」的检查。",
            MUST, Anchor.Gest(12),
        ),
        PlanItem(
            "g_nipt", "无创 DNA 或早期唐筛",
            "12 周起抽一管血，和 NT 同一次去就能一起做。35 岁以上医生一般直接建议无创。",
            SHOULD, Anchor.Gest(12, 3),
        ),
        // ---------------- 孕中期 ----------------
        PlanItem(
            "g_tang", "中期唐筛",
            "15–20 周+6 天。已经做了无创的就不用再做这项，别重复抽血。",
            SHOULD, Anchor.Gest(16),
        ),
        PlanItem(
            "g_anomaly", "大排畸",
            "20–24 周，系统 B 超看结构。要做半小时以上，宝宝不配合还得出去走一圈再回来。",
            MUST, Anchor.Gest(21),
        ),
        PlanItem(
            "g_ogtt", "糖耐（OGTT）",
            "24–28 周。前一晚 10 点后别吃东西，早上空腹去，抽三次血，中间那杯糖水要五分钟内喝完。",
            MUST, Anchor.Gest(25),
        ),
        // ---------------- 孕晚期 ----------------
        PlanItem(
            "g_2w", "产检改成两周一次",
            "28 周起频率加密。到 36 周之前，都是两周去一次。",
            SHOULD, Anchor.Gest(28),
        ),
        PlanItem(
            "g_kick", "开始数胎动",
            "28 周起，每天固定一小时。突然明显变少、或者先狂动再不动，都要马上去医院，不要等到第二天。",
            SHOULD, Anchor.Gest(28, 1),
        ),
        PlanItem(
            "g_grow", "小排畸 / 生长评估",
            "28–32 周。看长得够不够、羊水多不多、脐血流顺不顺。",
            SHOULD, Anchor.Gest(30),
        ),
        PlanItem(
            "g_nst", "胎心监护（NST）",
            "34 周起，每次产检都做，绑着带子躺二十分钟。",
            SHOULD, Anchor.Gest(34),
        ),
        PlanItem(
            "g_gbs", "GBS 筛查",
            "35–37 周取分泌物。阳性不用慌，生的时候挂抗生素就行，但得让产房知道。",
            MUST, Anchor.Gest(36),
        ),
        PlanItem(
            "g_bag", "收拾待产包",
            "36 周前收拾好放门口。证件、产检本、产褥垫、宝宝衣服、充电线，夜里发动也不会乱。",
            SHOULD, Anchor.Gest(36, 1),
        ),
        PlanItem(
            "g_weekly", "产检改成每周一次",
            "37 周起每周一次，评估胎位、宫颈条件和分娩方式。",
            SHOULD, Anchor.Gest(37),
        ),
        PlanItem(
            "g_edd", "预产期",
            "只有 5% 的人当天生，前后两周都算正常。这天以后还没动静，医生会安排评估。",
            KNOW, Anchor.Gest(40),
        ),
        // ---------------- 出生与满月 ----------------
        PlanItem(
            "b_birth", "出生",
            "乙肝疫苗第 1 针 + 卡介苗，出生 24 小时内就打。顺手拍一张，那是绳子的第一张。",
            MUST, Anchor.Post(0),
        ),
        PlanItem(
            "b_1m", "满月体检 + 乙肝第 2 针",
            "满月（30 天）去儿保，称体重、量头围、看黄疸退没退。",
            MUST, Anchor.Post(1),
        ),
        // ---------------- 半岁前 ----------------
        PlanItem(
            "b_2m", "脊灰第 1 针 + 百白破第 1 针",
            "2 月龄。打完可能低烧、针眼红肿，多喂奶多抱，38.5℃ 以上再考虑退烧药。",
            MUST, Anchor.Post(2),
        ),
        PlanItem(
            "b_3m", "脊灰第 2 针 + 3 月龄儿保",
            "3 月龄，医生会看抬头稳不稳、眼睛追不追人。",
            MUST, Anchor.Post(3),
        ),
        PlanItem(
            "b_4m", "脊灰第 3 针 + 百白破第 2 针",
            "4 月龄。",
            MUST, Anchor.Post(4),
        ),
        PlanItem(
            "b_5m", "百白破第 3 针",
            "5 月龄。",
            MUST, Anchor.Post(5),
        ),
        // ---------------- 半岁到一岁 ----------------
        PlanItem(
            "b_6m", "乙肝第 3 针 + A 群流脑第 1 针 + 6 月龄儿保",
            "6 月龄，同时该加辅食了：强化铁米粉先上，一次只加一样，观察三天。",
            MUST, Anchor.Post(6),
        ),
        PlanItem(
            "b_8m", "麻腮风第 1 针 + 乙脑第 1 针 + 8 月龄儿保",
            "8 月龄。麻腮风打完 5–12 天可能低烧出疹子，是疫苗反应，不用跑医院。",
            MUST, Anchor.Post(8),
        ),
        PlanItem(
            "b_9m", "A 群流脑第 2 针",
            "9 月龄，和上一针间隔三个月。",
            MUST, Anchor.Post(9),
        ),
        PlanItem(
            "b_12m", "周岁体检",
            "12 月龄，查血常规看有没有缺铁性贫血。这天顺手拍一张，一年一张，放一起看最明显。",
            MUST, Anchor.Post(12),
        ),
        // ---------------- 一岁到三岁 ----------------
        PlanItem(
            "b_18m", "百白破第 4 针 + 麻腮风第 2 针 + 甲肝第 1 针 + 18 月龄儿保",
            "18 月龄，一次打三针，医生会分开胳膊和腿打。",
            MUST, Anchor.Post(18),
        ),
        PlanItem(
            "b_24m", "乙脑第 2 针 + 2 岁儿保",
            "24 月龄，看走路稳不稳、会不会说两三个字的词。",
            MUST, Anchor.Post(24),
        ),
        PlanItem(
            "b_30m", "30 月龄儿保",
            "看会不会说短句、能不能自己拿勺吃饭。",
            SHOULD, Anchor.Post(30),
        ),
        PlanItem(
            "b_36m", "A 群 C 群流脑第 1 针 + 3 岁儿保",
            "36 月龄，儿保会查视力，这是第一次正式查。",
            MUST, Anchor.Post(36),
        ),
        // ---------------- 三岁以后 ----------------
        PlanItem(
            "b_48m", "脊灰第 4 针",
            "4 岁，口服或注射看当地安排。",
            MUST, Anchor.Post(48),
        ),
        PlanItem(
            "b_72m", "白破 + A 群 C 群流脑第 2 针",
            "6 岁，入学前把这两针补齐，小学报名会查接种证。",
            MUST, Anchor.Post(72),
        ),
    )

    /** 这条在哪天。没有能用的日期（既没预产期也没生日）时返回 null。 */
    fun dueOf(item: PlanItem, edd: LocalDate?, birth: LocalDate?): LocalDate? = when (val a = item.anchor) {
        is Anchor.Gest -> FamilyDate.gestationStart(edd)?.plusDays(a.week * 7L + a.day)
        // 还没出生时按预产期当预计出生日，这样整张表在孕期就能排出来
        is Anchor.Post -> (birth ?: edd)?.plusMonths(a.months.toLong())?.plusDays(a.days.toLong())
    }

    fun chapterOf(item: PlanItem): Chapter = when (val a = item.anchor) {
        is Anchor.Gest -> when {
            a.week < 14 -> Chapter.EARLY
            a.week < 28 -> Chapter.MID
            else -> Chapter.LATE
        }
        is Anchor.Post -> when {
            a.months < 1 -> Chapter.BIRTH
            a.months < 6 -> Chapter.HALF
            a.months < 12 -> Chapter.ONE
            a.months < 36 -> Chapter.TOD
            else -> Chapter.BIG
        }
    }

    fun rows(edd: LocalDate?, birth: LocalDate?, done: Set<String>): List<PlanRow> {
        if (edd == null && birth == null) return emptyList()
        return ALL.mapNotNull { item ->
            val d = dueOf(item, edd, birth) ?: return@mapNotNull null
            PlanRow(item, d, item.key in done)
        }.sortedBy { it.due }
    }

    /**
     * 绳尾那张纸片写的这一件。
     *
     * 只往前看 30 天，且过期 7 天以上就不再追——产检拖过一周基本就等于没做，
     * 一直挂在纸片上只会让人不想打开这个 App。
     */
    fun next(rows: List<PlanRow>, today: LocalDate, horizon: Long = 30, grace: Long = 7): PlanRow? =
        rows.asSequence()
            .filter { !it.done && it.item.weight <= SHOULD }
            .filter { ChronoUnit.DAYS.between(today, it.due) in -grace..horizon }
            .minByOrNull { it.due }

    /** 该响铃的：今天到期，以及三天后到期（留出预约的时间）。 */
    fun bumps(rows: List<PlanRow>, today: LocalDate): List<PlanRow> =
        rows.filter {
            !it.done && it.item.weight <= SHOULD &&
                (it.due == today || it.due == today.plusDays(3))
        }

    /** 按章分组，空章不返回。 */
    fun groups(rows: List<PlanRow>): List<Pair<Chapter, List<PlanRow>>> =
        Chapter.entries.map { ch -> ch to rows.filter { chapterOf(it.item) == ch } }
            .filter { it.second.isNotEmpty() }

    /** 「今天」「明天」「还有 5 天」「过了 3 天」。 */
    fun daysLabel(due: LocalDate, today: LocalDate): String {
        val d = ChronoUnit.DAYS.between(today, due)
        return when {
            d == 0L -> "今天"
            d == 1L -> "明天"
            d == -1L -> "昨天"
            d > 1L -> "还有 $d 天"
            else -> "过了 ${-d} 天"
        }
    }
}

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
 *    的 5 次孕期保健，叠加国内医院实际执行的 7–11 次产检；每次的项目与关注指标
 *    参照各院通用的产检流程单（HCG→B 超→NT→唐筛/无创→大排畸→糖耐→小排畸→NST→GBS→足月评估）。
 *  - 0–6 岁儿童保健 8 次：国家基本公共卫生服务规范（第三版），满月/3/6/8/12/18/24/30/36 月龄。
 *  - 疫苗：国家免疫规划疫苗儿童免疫程序（2021 年版）。各省加针（如水痘、轮状）不写死在这里，
 *    因为各地不一样，写了就会跟接种门诊的单子对不上。
 *
 * 为什么不写"哪家医院、几楼几号诊室、先缴费还是先抽血"：
 * 那是各医院自己的排期和走法，写死就不是"参考"了，而是一份会过期的、别人家的单子。
 * 这里只放不受医院影响的：查什么、看哪几个数、去之前要不要空腹憋尿、大致先后。
 * 有出入的地方留给用户自己记一句（见 FamilyStore.planNote）。
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
 *
 * items / watch / prep / flow 是"到了那天该干什么"的四层：
 *  - items：这次要做的检查（抽不抽血、做不做 B 超）
 *  - watch：看哪几个数字，心里有个底，不至于拿到单子只会看箭头
 *  - prep：去之前要准备的（空腹、憋尿、带什么证件）——这几条也会进通知
 *  - flow：大致的顺序（挂号→开单→缴费→检查→回诊），各家不同，只用来说明先做什么
 */
data class PlanItem(
    val key: String,
    val title: String,
    val note: String,
    val weight: Int,
    val anchor: Anchor,
    val items: String = "",
    val watch: String = "",
    val prep: List<String> = emptyList(),
    val flow: String = "",
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
            "g_hcg", "抽血确认怀孕",
            "孕 4–5 周。验孕棒两条杠之后再抽一管血，看 HCG 和孕酮，这是确认怀孕的第一步。",
            SHOULD, Anchor.Gest(5),
            items = "血 HCG、孕酮",
            watch = "HCG 隔天翻倍情况；孕酮是否偏低",
            prep = listOf("不用空腹"),
            flow = "挂号 → 开单 → 抽血（一般当天或次日出结果）",
        ),
        PlanItem(
            "g_scan", "第一次 B 超",
            "孕 6–8 周。看是不是宫内、有没有胎心。去太早可能还看不到，白紧张一次。",
            MUST, Anchor.Gest(6),
            items = "腹部 B 超（看不清时改阴道 B 超）",
            watch = "孕囊位置、胎芽、胎心",
            prep = listOf("经腹 B 超要憋尿", "不用空腹"),
            flow = "挂号 → 开单 → 缴费 → B 超室排号",
        ),
        PlanItem(
            "g_record", "建档 + 首次产检",
            "最迟 13 周前。空腹去，一次抽好几管血：血常规、肝肾功能、甲功、血型、传染病筛查。",
            MUST, Anchor.Gest(9),
            items = "血常规、血型、肝肾功能、甲功、空腹血糖、乙肝/梅毒/HIV、尿常规、心电图",
            watch = "有没有贫血、甲减、血糖偏高、尿蛋白",
            prep = listOf("空腹 8 小时", "建议上午去", "带夫妻双方身份证", "带近期 B 超单", "带水杯（要留尿）", "建档号多数要提前预约"),
            flow = "先挂号建档拿到保健卡，再开单缴费；抽血、尿检在检验科，心电图另约",
        ),
        PlanItem(
            "g_nt", "NT 检查",
            "11 周–13 周+6 天，B 超量颈后透明层。过了这段就做不了，是孕期少数几个「过期不候」的检查。",
            MUST, Anchor.Gest(12),
            items = "NT 彩超",
            watch = "颈后透明层厚度（一般以 2.5mm 为界）",
            prep = listOf("不用空腹", "不用憋尿", "带上次 B 超单", "要提前预约，排到几周后很常见"),
            flow = "挂号 → 开单 → 缴费 → B 超室；宝宝位置不好时要出去走一圈再回来",
        ),
        PlanItem(
            "g_nipt", "无创 DNA 或早期唐筛",
            "12 周起抽一管血，和 NT 同一次去就能一起做。35 岁以上医生一般直接建议无创。",
            SHOULD, Anchor.Gest(12, 3),
            items = "抽血（无创 DNA 或早唐）",
            watch = "21 / 18 / 13 三体的风险值",
            prep = listOf("不用空腹"),
            flow = "和 NT 同一次去，先抽血再做 B 超，少跑一趟",
        ),
        // ---------------- 孕中期 ----------------
        PlanItem(
            "g_tang", "中期唐筛",
            "15–20 周+6 天。已经做了无创的就不用再做这项，别重复抽血。",
            SHOULD, Anchor.Gest(16),
            items = "抽血（AFP、hCG、游离雌三醇）",
            watch = "风险值；高风险会建议进一步做羊水穿刺",
            prep = listOf("空腹"),
            flow = "挂号 → 开单 → 抽血；结果一般一周后取",
        ),
        PlanItem(
            "g_anomaly", "大排畸",
            "20–24 周，系统 B 超看结构。要做半小时以上，宝宝不配合还得出去走一圈再回来。",
            MUST, Anchor.Gest(21),
            items = "系统彩超（三维 / 四维）",
            watch = "头颅、面部、脊柱、心脏、四肢、内脏结构",
            prep = listOf("不用空腹", "带点吃的，宝宝不动时吃点走一走", "要提前预约，一次占半小时以上"),
            flow = "挂号 → 开单 → 缴费 → B 超室预约；一次半小时以上，别约在要赶时间的上午",
        ),
        PlanItem(
            "g_ogtt", "糖耐（OGTT）",
            "24–28 周。前一晚 10 点后别吃东西，早上空腹去，抽三次血，中间那杯糖水要五分钟内喝完。",
            MUST, Anchor.Gest(25),
            items = "空腹血糖 + 喝糖水后 1 小时、2 小时血糖",
            watch = "三项任一超标即诊断：空腹 ≥5.1、1 小时 ≥10.0、2 小时 ≥8.5 mmol/L",
            prep = listOf("前一晚 10 点后禁食", "早上空腹", "带水杯", "带点吃的，抽完最后一次就能吃"),
            flow = "先抽空腹血 → 5 分钟内喝完 300ml 糖水（从第一口开始计时）→ 1 小时、2 小时各抽一次，中间坐着别走动",
        ),
        // ---------------- 孕晚期 ----------------
        PlanItem(
            "g_2w", "产检改成两周一次",
            "28 周起频率加密。到 36 周之前，都是两周去一次。",
            SHOULD, Anchor.Gest(28),
            items = "产科常规：量血压、体重、宫高、腹围，听胎心",
            watch = "血压、体重涨得太快、尿蛋白",
            prep = listOf("带产检本"),
            flow = "挂号 → 诊室报到 → 医生开单 → 缴费 → 检查",
        ),
        PlanItem(
            "g_kick", "开始数胎动",
            "28 周起，每天固定一小时。突然明显变少、或者先狂动再不动，都要马上去医院，不要等到第二天。",
            SHOULD, Anchor.Gest(28, 1),
            items = "每天固定一小时数胎动",
            watch = "一小时 ≥3 次为正常；比平时少一半以上、或先剧烈后消失，立刻去医院",
            prep = listOf("不用去医院", "手机里记一下每天的数字"),
        ),
        PlanItem(
            "g_grow", "小排畸 / 生长评估",
            "28–32 周。看长得够不够、羊水多不多、脐血流顺不顺。",
            SHOULD, Anchor.Gest(30),
            items = "B 超（生长评估）",
            watch = "胎儿发育、羊水量、胎盘成熟度、脐血流",
            prep = listOf("不用空腹", "不用憋尿"),
            flow = "和这次产检一起做，先诊室开单再 B 超",
        ),
        PlanItem(
            "g_nst", "胎心监护（NST）",
            "34 周起，每次产检都做，绑着带子躺二十分钟。",
            SHOULD, Anchor.Gest(34),
            items = "胎心监护 20 分钟",
            watch = "胎心基线、变异、加速；不合格会让你换个时间再做一次",
            prep = listOf("不用空腹", "别在太饿或刚吃饱时做", "带点甜的，宝宝不动时吃一点", "带纸巾"),
            flow = "诊室开单 → 缴费 → 胎监室排队；宝宝不动时侧躺或吃点东西",
        ),
        PlanItem(
            "g_gbs", "GBS 筛查",
            "35–37 周取分泌物。阳性不用慌，生的时候挂抗生素就行，但得让产房知道。",
            MUST, Anchor.Gest(36),
            items = "阴道分泌物培养（B 族链球菌）",
            watch = "阴性 / 阳性；阳性要在分娩时预防性用抗生素",
            prep = listOf("不用空腹", "避开出血时", "取样前 24 小时别同房、别冲洗阴道"),
            flow = "诊室取样 → 送检验科；结果一般 2–3 天",
        ),
        PlanItem(
            "g_bag", "收拾待产包",
            "36 周前收拾好放门口。证件、产检本、产褥垫、宝宝衣服、充电线，夜里发动也不会乱。",
            SHOULD, Anchor.Gest(36, 1),
            items = "证件袋、产检本、产褥垫、一次性内裤、宝宝衣服和包被、奶瓶、充电线、拖鞋",
            prep = listOf("放门口", "车上再放一套换洗"),
        ),
        PlanItem(
            "g_weekly", "产检改成每周一次",
            "37 周起每周一次。这一次会评估胎位、骨盆条件和分娩方式，把想问的提前写下来。",
            MUST, Anchor.Gest(37),
            items = "产科常规 + 胎心监护；这次加骨盆测量和 B 超（胎儿发育、羊水、胎盘成熟度）",
            watch = "胎位、估计胎儿体重、宫颈条件、羊水",
            prep = listOf("带产检本", "把想问的写下来"),
            flow = "挂号 → 诊室 → 开单 → 缴费 → B 超 / 胎监；这次医生会跟你谈顺产还是剖",
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
            items = "乙肝疫苗第 1 针、卡介苗、新生儿疾病筛查、听力筛查",
            watch = "体重、身长、Apgar 评分、黄疸",
            prep = listOf("带证件", "相机 / 手机充好电"),
        ),
        PlanItem(
            "b_1m", "满月体检 + 乙肝第 2 针",
            "满月（30 天）去儿保，称体重、量头围、看黄疸退没退。",
            MUST, Anchor.Post(1),
            items = "满月儿保（体重、身长、头围、黄疸）+ 乙肝第 2 针",
            watch = "体重比出生时涨了多少、黄疸是否退净",
            prep = listOf("带接种证", "带出生证明", "喂奶后再去，别空腹"),
        ),
        // ---------------- 半岁前 ----------------
        PlanItem(
            "b_2m", "脊灰第 1 针 + 百白破第 1 针",
            "2 月龄。打完可能低烧、针眼红肿，多喂奶多抱，38.5℃ 以上再考虑退烧药。",
            MUST, Anchor.Post(2),
            items = "脊灰灭活疫苗第 1 针、百白破第 1 针",
            watch = "接种后 48 小时内的体温和针眼",
            prep = listOf("带接种证", "宝宝没有发烧、拉肚子"),
        ),
        PlanItem(
            "b_3m", "脊灰第 2 针 + 3 月龄儿保",
            "3 月龄，医生会看抬头稳不稳、眼睛追不追人。",
            MUST, Anchor.Post(3),
            items = "脊灰第 2 针 + 儿保（体重、身长、头围、抬头、追视）",
            watch = "抬头是否稳、会不会对人笑",
            prep = listOf("带接种证", "带儿保手册"),
        ),
        PlanItem(
            "b_4m", "脊灰第 3 针 + 百白破第 2 针",
            "4 月龄。",
            MUST, Anchor.Post(4),
            items = "脊灰第 3 针、百白破第 2 针",
            prep = listOf("带接种证"),
        ),
        PlanItem(
            "b_5m", "百白破第 3 针",
            "5 月龄。",
            MUST, Anchor.Post(5),
            items = "百白破第 3 针",
            prep = listOf("带接种证"),
        ),
        // ---------------- 半岁到一岁 ----------------
        PlanItem(
            "b_6m", "乙肝第 3 针 + A 群流脑第 1 针 + 6 月龄儿保",
            "6 月龄，同时该加辅食了：强化铁米粉先上，一次只加一样，观察三天。",
            MUST, Anchor.Post(6),
            items = "乙肝第 3 针、A 群流脑第 1 针 + 儿保（含血常规查贫血）",
            watch = "有没有缺铁性贫血；会不会独坐",
            prep = listOf("带接种证", "带儿保手册"),
        ),
        PlanItem(
            "b_8m", "麻腮风第 1 针 + 乙脑第 1 针 + 8 月龄儿保",
            "8 月龄。麻腮风打完 5–12 天可能低烧出疹子，是疫苗反应，不用跑医院。",
            MUST, Anchor.Post(8),
            items = "麻腮风第 1 针、乙脑第 1 针 + 儿保",
            watch = "接种后 5–12 天的低烧和皮疹；会不会爬",
            prep = listOf("带接种证", "带儿保手册"),
        ),
        PlanItem(
            "b_9m", "A 群流脑第 2 针",
            "9 月龄，和上一针间隔三个月。",
            MUST, Anchor.Post(9),
            items = "A 群流脑第 2 针",
            prep = listOf("带接种证"),
        ),
        PlanItem(
            "b_12m", "周岁体检",
            "12 月龄，查血常规看有没有缺铁性贫血。这天顺手拍一张，一年一张，放一起看最明显。",
            MUST, Anchor.Post(12),
            items = "儿保（体重、身长、头围、囟门、出牙、走路）+ 血常规",
            watch = "血红蛋白、会不会独走、会不会叫爸妈",
            prep = listOf("带儿保手册", "带接种证"),
        ),
        // ---------------- 一岁到三岁 ----------------
        PlanItem(
            "b_18m", "百白破第 4 针 + 麻腮风第 2 针 + 甲肝第 1 针 + 18 月龄儿保",
            "18 月龄，一次打三针，医生会分开胳膊和腿打。",
            MUST, Anchor.Post(18),
            items = "百白破第 4 针、麻腮风第 2 针、甲肝第 1 针 + 儿保",
            watch = "会不会说几个词、能不能指认五官",
            prep = listOf("带接种证", "带儿保手册"),
        ),
        PlanItem(
            "b_24m", "乙脑第 2 针 + 2 岁儿保",
            "24 月龄，看走路稳不稳、会不会说两三个字的词。",
            MUST, Anchor.Post(24),
            items = "乙脑第 2 针 + 儿保（含视力筛查）",
            watch = "走路、说话、自己吃饭",
            prep = listOf("带接种证", "带儿保手册"),
        ),
        PlanItem(
            "b_30m", "30 月龄儿保",
            "看会不会说短句、能不能自己拿勺吃饭。",
            SHOULD, Anchor.Post(30),
            items = "儿保（体重、身长、语言、动作）",
            watch = "说短句、跑跳、自己用勺",
            prep = listOf("带儿保手册"),
        ),
        PlanItem(
            "b_36m", "A 群 C 群流脑第 1 针 + 3 岁儿保",
            "36 月龄，儿保会查视力，这是第一次正式查。",
            MUST, Anchor.Post(36),
            items = "A 群 C 群流脑第 1 针 + 儿保（视力筛查、血常规）",
            watch = "视力、血红蛋白、能不能双脚跳",
            prep = listOf("带接种证", "带儿保手册"),
        ),
        // ---------------- 三岁以后 ----------------
        PlanItem(
            "b_48m", "脊灰第 4 针",
            "4 岁，口服或注射看当地安排。",
            MUST, Anchor.Post(48),
            items = "脊灰第 4 针",
            prep = listOf("带接种证"),
        ),
        PlanItem(
            "b_72m", "白破 + A 群 C 群流脑第 2 针",
            "6 岁，入学前把这两针补齐，小学报名会查接种证。",
            MUST, Anchor.Post(72),
            items = "白破、A 群 C 群流脑第 2 针",
            prep = listOf("带接种证"),
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

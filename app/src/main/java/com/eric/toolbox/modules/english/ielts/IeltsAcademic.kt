package com.eric.toolbox.modules.english.ielts

/** 学术类场景：雅思口语 Part 2/3 与留学生活的实际对话。 */
object IeltsAcademic {

    val list: List<Scene> = listOf(enrol, supervisor, seminar, present, library, feedback)

    private val enrol = Scene(
        id = "enrol", zh = "校园注册与选课", en = "Campus enrolment", cat = Cat.ACADEMIC,
        goal = "办理注册、问必修与选修、处理课程冲突",
        lines = listOf(
            Ln(
                a = "Next, please. Are you here to enrol?",
                aZh = "下一位。来注册的吗？",
                b = "Yes, I'm doing the MSc in Data Science — I got an offer last month.",
                bZh = "是的，我读数据科学硕士，上个月拿到的 offer。",
                tip = "报专业用 I'm doing / I'm on the MSc in…，比 I study 更口语",
            ),
            Ln(
                a = "Could I have your offer letter and passport? You'll need to choose your modules online by Friday.",
                aZh = "给我 offer 和护照。周五前要在网上选课。",
                b = "Right. Is there a compulsory module list I should follow?",
                bZh = "好。有必修课清单可以参考吗？",
                tip = "compulsory / core module = 必修；optional / elective = 选修",
            ),
            Ln(
                a = "Two core modules, then you pick three optional ones.",
                aZh = "两门必修，然后选三门选修。",
                b = "Would it be possible to swap one if it clashes with my lab hours?",
                bZh = "如果和我的实验时间冲突，能换一门吗？",
                tip = "clash with = 时间冲突；swap = 换课",
            ),
            Ln(
                a = "Usually, yes — just email the programme office.",
                aZh = "一般可以，发邮件给项目办公室就行。",
                b = "Great, I'll do that today. Where do I collect my student card?",
                bZh = "好，我今天就发。学生卡在哪儿领？",
                tip = "collect（英）= 领取；美式常说 pick up",
            ),
        ),
        words = listOf(
            Wd("enrol", "注册入学", "enrol on a course", "I enrolled on the programme in September.", "我九月注册了这个项目。"),
            Wd("offer letter", "录取通知", "receive an offer letter", "The offer letter came with a deposit deadline.", "录取通知里写了押金截止日。"),
            Wd("module", "课程模块", "choose your modules", "I'm taking four modules this term.", "这学期我修四门课。"),
            Wd("core module", "必修课", "core / optional modules", "Research methods is a core module.", "研究方法是必修课。"),
            Wd("swap", "调换", "swap one module for another", "Can I swap Monday's class?", "周一的课能换吗？"),
            Wd("clash with", "与……时间冲突", "clash with a lab session", "The lecture clashes with my lab.", "这门课和我的实验冲突。"),
            Wd("programme", "（学位）项目", "the programme office", "The programme lasts twelve months.", "这个项目为期十二个月。"),
            Wd("student card", "学生证", "collect your student card", "You need your student card to enter the library.", "进图书馆要学生证。"),
            Wd("induction", "入学介绍（周）", "induction week", "Induction week starts on the 15th.", "新生周 15 号开始。"),
            Wd("timetable", "课程表", "check your timetable", "The timetable is published online.", "课表在网上发布。"),
        ),
        chunks = listOf(
            Ck("说明身份", "I'm here to enrol on the MSc in Data Science.", "注册、报到都用 be here to + 动词"),
            Ck("问必选清单", "Is there a compulsory module list I should follow?", "一次问清必修和选修，省得来回跑"),
            Ck("说明冲突", "It clashes with my lab hours — could I swap it?", "冲突 + 请求，一句话说完"),
            Ck("问办理地点", "Where do I collect my student card?", "collect / pick up 都行，别用 take"),
            Ck("确认下一步", "So I choose the modules online by Friday, right?", "把听到的流程复述一遍，避免记错"),
        ),
        asks = listOf(
            Qs("What would you like to study if you went back to university?", "如果重返校园你想学什么？", 3, "专业 + 两个理由（interest, career）+ 一个顾虑（cost, time）"),
            Qs("Describe a course you enjoyed.", "描述一门你喜欢的课程。", 2, "什么课 + 谁教 + 学了什么 + 为什么喜欢（practical, engaging）"),
            Qs("Is university education worth the cost?", "大学教育值这个钱吗？", 3, "值：长期收入 + 视野 + 人脉；不值：债务 + 技能课替代；给判断"),
        ),
    )

    private val supervisor = Scene(
        id = "supervisor", zh = "导师会面", en = "Supervisor meeting", cat = Cat.ACADEMIC,
        goal = "汇报进度、说清卡点、接受收窄建议、约定下次交付",
        lines = listOf(
            Ln(
                a = "So, where are you with the literature review?",
                aZh = "文献综述做到哪一步了？",
                b = "I've drafted about half — I'm still narrowing down the scope, to be honest.",
                bZh = "写了一半，说实话还在收窄范围。",
                tip = "to be honest 放句尾，承认卡点时很自然",
            ),
            Ln(
                a = "That's the right instinct. What's your research question at the moment?",
                aZh = "这个直觉是对的。现在的研究问题是什么？",
                b = "Whether remote work affects team innovation — but it may be too broad.",
                bZh = "远程办公是否影响团队创新，但可能太宽了。",
                tip = "用 whether 引出研究问题，学术口语的标准说法",
            ),
            Ln(
                a = "It is. Try focusing on one industry and one type of team.",
                aZh = "确实。试着聚焦一个行业、一类团队。",
                b = "That helps. Shall I send you a revised outline by next Tuesday?",
                bZh = "这有帮助。我下周二前发修订后的大纲给您？",
                tip = "用 Shall I… 主动提交付时间，比等导师催好",
            ),
            Ln(
                a = "Tuesday works. Keep it to one page.",
                aZh = "周二可以。控制在一页。",
                b = "Will do. I'll also add a short note on the method I'm planning to use.",
                bZh = "好。我还会附一小段计划使用的方法。",
                tip = "Will do + 补充说明，是接受任务的标准回应",
            ),
        ),
        words = listOf(
            Wd("literature review", "文献综述", "draft the literature review", "The literature review covers thirty papers.", "文献综述涵盖三十篇论文。"),
            Wd("scope", "研究范围", "narrow down the scope", "The scope is too broad for a master's thesis.", "对硕士论文来说范围太宽。"),
            Wd("research question", "研究问题", "a clear research question", "Everything should follow from the research question.", "一切都应围绕研究问题展开。"),
            Wd("narrow down", "收窄", "narrow it down to one sector", "Narrow it down to one sector first.", "先收窄到一个行业。"),
            Wd("outline", "提纲", "a revised outline", "Send me an outline before you write.", "写之前先发我提纲。"),
            Wd("supervisor", "导师", "meet your supervisor", "My supervisor suggested two changes.", "导师提了两处修改。"),
            Wd("feedback", "反馈", "give / get feedback", "I got detailed feedback on chapter one.", "我拿到第一章的详细反馈。"),
            Wd("draft", "草稿", "a first draft", "The first draft is due in March.", "初稿三月交。"),
            Wd("methodology", "研究方法", "the methodology section", "Explain the methodology in plain language.", "用平实的语言解释研究方法。"),
            Wd("deadline", "截止时间", "a hard deadline", "The submission deadline is the 30th.", "提交截止日是 30 号。"),
        ),
        chunks = listOf(
            Ck("汇报进度", "I've drafted about half of the literature review.", "完成度 + 具体位置，导师最需要这个"),
            Ck("承认卡点", "I'm still narrowing down the scope, to be honest.", "承认不确定反而显得可靠"),
            Ck("主动提交付", "Shall I send you a revised outline by Tuesday?", "主动定时间，别让导师追"),
            Ck("接受建议", "That helps — I'll focus on one industry.", "接受 + 复述具体做法，确认理解一致"),
            Ck("约定下次", "Same time next week?", "固定节奏，比每次约时间省事"),
        ),
        asks = listOf(
            Qs("What makes a good supervisor?", "好导师的标准是什么？", 3, "给方向不替你写 + 及时反馈 + 人脉/资源"),
            Qs("Describe a teacher who influenced you.", "描述一位影响过你的老师。", 2, "谁 + 教什么 + 一件事 + 对你的影响"),
            Qs("Is independent research better than taught courses?", "独立研究比授课型课程更好吗？", 3, "独立研究：深度 + 主动性；授课：体系 + 反馈；看目标"),
        ),
    )

    private val seminar = Scene(
        id = "seminar", zh = "小组讨论", en = "Seminar discussion", cat = Cat.ACADEMIC,
        goal = "礼貌质疑、区分局限与缺陷、提出下一步",
        lines = listOf(
            Ln(
                a = "What did you make of the reading? I found the argument a bit one-sided.",
                aZh = "你怎么看这篇阅读材料？我觉得论证有点片面。",
                b = "I see what you mean, though the data does support their main claim.",
                bZh = "我明白你的意思，不过数据确实支持他们的主要观点。",
                tip = "I see what you mean, though… 先接住再补充，讨论里最常用",
            ),
            Ln(
                a = "Maybe, but they only looked at one region.",
                aZh = "也许吧，但他们只研究了一个地区。",
                b = "That's true — it's a limitation rather than a flaw, though.",
                bZh = "确实，不过这更像是局限，而不是硬伤。",
                tip = "limitation vs flaw 的区分是学术讨论的加分点",
            ),
            Ln(
                a = "Shall we bring that up in the seminar?",
                aZh = "我们研讨课上提一下这点？",
                b = "Yes, and I can find a study from another region to compare.",
                bZh = "好，我可以找一篇别的地区的研究来对比。",
                tip = "bring up = 在会上提出；compare 是承接讨论的自然动作",
            ),
            Ln(
                a = "That would strengthen our point. Could you send it round before Thursday?",
                aZh = "这样我们的观点更有力。周四前能发给大家吗？",
                b = "No problem — I'll post it in the group chat.",
                bZh = "没问题，我发到群里。",
                tip = "send round / post in the group 都是“发给大家”",
            ),
        ),
        words = listOf(
            Wd("argument", "论证 / 论点", "a convincing argument", "The argument rests on two assumptions.", "这个论证建立在两个假设上。"),
            Wd("one-sided", "片面的", "a one-sided view", "The chapter gives a rather one-sided view.", "这一章的观点相当片面。"),
            Wd("claim", "主张", "support the main claim", "The data supports their claim.", "数据支持他们的主张。"),
            Wd("limitation", "局限", "a key limitation", "Sample size is the main limitation.", "样本量是主要局限。"),
            Wd("flaw", "缺陷 / 硬伤", "a fundamental flaw", "There's a flaw in the method.", "方法上有个硬伤。"),
            Wd("evidence", "证据", "evidence for / against", "There's little evidence for that.", "那一点几乎没有证据。"),
            Wd("seminar", "研讨课", "in the seminar", "I'll raise it in the seminar.", "我会在研讨课上提出来。"),
            Wd("bring up", "提出（话题）", "bring up a point", "She brought up an interesting point.", "她提了一个有意思的点。"),
            Wd("compare", "比较", "compare A with B", "Let's compare the two studies.", "我们把这两项研究比一比。"),
            Wd("assumption", "假设", "rest on an assumption", "That conclusion rests on a shaky assumption.", "那个结论建立在一个不牢靠的假设上。"),
        ),
        chunks = listOf(
            Ck("礼貌质疑", "I see what you mean, though the data suggests otherwise.", "先接住对方，再给不同看法"),
            Ck("区分程度", "It's a limitation rather than a flaw.", "学术讨论里把问题分级，显得判断准"),
            Ck("承接并加码", "Yes, and I can find a study to compare.", "同意 + 补一个动作，讨论才往前走"),
            Ck("推进到行动", "Shall we bring that up on Thursday?", "把讨论变成研讨课上的议题"),
            Ck("认领任务", "I'll post it in the group chat.", "主动认领，比 someone should 有效"),
        ),
        asks = listOf(
            Qs("Do you prefer studying alone or in a group?", "你更喜欢独自学习还是小组学习？", 1, "选择 + 理由（focus vs. ideas）+ 场景区分"),
            Qs("Why is critical thinking important at university?", "为什么大学里批判性思维重要？", 3, "不轻信结论 + 看证据 + 工作中判断信息"),
            Qs("Describe a discussion that changed your mind.", "描述一次改变了你想法的讨论。", 2, "话题 + 对方观点 + 哪个证据说服了你 + 之后怎么想"),
        ),
    )

    private val present = Scene(
        id = "present", zh = "课堂展示与问答", en = "Presentation & Q&A", cat = Cat.ACADEMIC,
        goal = "讲清方法、正面回应质疑、承认局限",
        lines = listOf(
            Ln(
                a = "Thanks for the presentation. How did you collect the sample?",
                aZh = "谢谢你的展示。样本是怎么收集的？",
                b = "We surveyed 120 students, mostly through an online form.",
                bZh = "我们调查了 120 名学生，主要通过在线问卷。",
                tip = "survey（v.）比 did a survey 简洁；样本量一定要说清",
            ),
            Ln(
                a = "Isn't that a biased sample?",
                aZh = "这样本不是有偏差吗？",
                b = "It is to some extent — we acknowledge that in the limitations.",
                bZh = "某种程度上是的，我们在局限部分承认了这一点。",
                tip = "被质疑时先承认部分成立，再说明已经处理，别急着辩解",
            ),
            Ln(
                a = "What would you do differently?",
                aZh = "你会怎么改进？",
                b = "I'd add face-to-face interviews to reach people who don't use the platform.",
                bZh = "我会加上面对面访谈，覆盖不用这个平台的人。",
                tip = "I'd + 动词，谈改进的标准句式",
            ),
            Ln(
                a = "Good. One last question — how confident are you in the finding?",
                aZh = "好。最后一个问题：你对这个结论有多大把握？",
                b = "Moderately — the effect is consistent, but the sample is small.",
                bZh = "中等。效应是一致的，但样本偏小。",
                tip = "moderately / fairly confident 比 I think 更专业",
            ),
        ),
        words = listOf(
            Wd("sample", "样本", "a representative sample", "The sample was small but diverse.", "样本不大但很多样。"),
            Wd("survey", "问卷调查", "conduct a survey", "We surveyed 120 students.", "我们调查了 120 名学生。"),
            Wd("biased", "有偏差的", "a biased sample", "Online-only responses can be biased.", "只收线上问卷可能带来偏差。"),
            Wd("acknowledge", "承认", "acknowledge a limitation", "We acknowledge this limitation.", "我们承认这一局限。"),
            Wd("limitation", "局限", "in the limitations section", "The limitations section is on the last slide.", "局限部分在最后一页。"),
            Wd("finding", "研究结果", "the main finding", "The main finding was unexpected.", "主要结果出乎意料。"),
            Wd("hypothesis", "假设", "test a hypothesis", "The data supports our hypothesis.", "数据支持我们的假设。"),
            Wd("methodology", "研究方法", "explain the methodology", "I'll explain the methodology briefly.", "我简要说明一下方法。"),
            Wd("follow-up", "后续（研究/提问）", "a follow-up study", "A follow-up study could test this.", "后续研究可以验证这一点。"),
            Wd("confident", "有把握的", "fairly confident", "I'm fairly confident in the trend.", "我对这个趋势比较有把握。"),
        ),
        chunks = listOf(
            Ck("讲样本", "We surveyed 120 students through an online form.", "数量 + 方式，一句话交代清楚"),
            Ck("回应质疑", "It is to some extent — we acknowledge that.", "先承认部分成立，可信度立刻上去"),
            Ck("谈改进", "I'd add interviews to reach non-users.", "I'd + 具体动作，别只说 improve"),
            Ck("表达把握", "Moderately — the effect is consistent, but the sample is small.", "把握程度 + 理由，别给空泛的 I think"),
            Ck("收尾", "That's a good question — could I follow up by email?", "现场答不好时的体面处理"),
        ),
        asks = listOf(
            Qs("Is it easy for you to speak in public?", "当众讲话对你来说容易吗？", 1, "态度 + 以前怎么样 + 现在怎么练（prepare, practise）"),
            Qs("Describe a presentation you gave.", "描述一次你做展示的经历。", 2, "主题 + 听众 + 你怎么准备 + 结果/收获"),
            Qs("Why do some people fear public speaking?", "为什么有些人害怕当众讲话？", 3, "怕被评判 + 经验少 + 生理反应；解决办法（小场合练起）"),
        ),
    )

    private val library = Scene(
        id = "library", zh = "图书馆与文献", en = "Library & sources", cat = Cat.ACADEMIC,
        goal = "问检索方法、筛同行评审、校外访问全文",
        lines = listOf(
            Ln(
                a = "I'm looking for journal articles on remote work.",
                aZh = "我在找关于远程办公的期刊论文。",
                b = "You could start with the database search — do you know the title?",
                bZh = "可以先从数据库检索开始，你知道标题吗？",
                tip = "journal article = 期刊论文，别用 paper book 混",
            ),
            Ln(
                a = "Not exactly. Can I search by keyword?",
                aZh = "不太确定。能按关键词搜吗？",
                b = "Yes, but use the advanced search and limit it to peer-reviewed.",
                bZh = "可以，但用高级检索，并限定同行评审。",
                tip = "peer-reviewed = 同行评审，学术检索的关键过滤条件",
            ),
            Ln(
                a = "And can I access the full text off campus?",
                aZh = "校外能看全文吗？",
                b = "Through the library login, yes. You'll need your student number.",
                bZh = "用图书馆账号登录可以。需要学号。",
                tip = "off campus = 校外；full text = 全文",
            ),
            Ln(
                a = "Great. How many can I borrow at once?",
                aZh = "好。一次能借几本？",
                b = "Ten books for three weeks, and you can renew twice online.",
                bZh = "十本，三周，可以在线续借两次。",
                tip = "renew = 续借，不是“更新”",
            ),
        ),
        words = listOf(
            Wd("journal", "学术期刊", "a peer-reviewed journal", "It was published in a leading journal.", "它发表在一流期刊上。"),
            Wd("database", "数据库", "search the database", "The database covers 2,000 journals.", "这个数据库收录两千种期刊。"),
            Wd("keyword", "关键词", "search by keyword", "Try a broader keyword first.", "先试一个更宽的关键词。"),
            Wd("peer-reviewed", "同行评审的", "peer-reviewed articles", "Limit the results to peer-reviewed.", "把结果限定为同行评审。"),
            Wd("full text", "全文", "access the full text", "The full text is behind a paywall.", "全文要付费才能看。"),
            Wd("off campus", "校外", "off-campus access", "Off-campus access needs a login.", "校外访问需要登录。"),
            Wd("citation", "引用", "a citation style", "Check the citation format.", "检查引用格式。"),
            Wd("abstract", "摘要", "read the abstract first", "Read the abstract before downloading.", "下载前先看摘要。"),
            Wd("reference", "参考文献", "a reference list", "The reference list is incomplete.", "参考文献不全。"),
            Wd("renew", "续借", "renew a book online", "I renewed the book twice.", "我续借了两次。"),
        ),
        chunks = listOf(
            Ck("说明需求", "I'm looking for journal articles on…", "找资料的开场，比 I want to find 正式"),
            Ck("问检索方式", "Can I search by keyword?", "不确定标题时的标准问法"),
            Ck("要求过滤", "Could you limit it to peer-reviewed?", "把要求说具体，馆员才知道怎么帮"),
            Ck("问访问权限", "Can I access the full text off campus?", "留学生最常问的一句"),
            Ck("问借阅规则", "How many can I borrow at once?", "at once = 一次"),
        ),
        asks = listOf(
            Qs("Do you prefer paper books or e-books?", "你更喜欢纸质书还是电子书？", 1, "选择 + 理由（focus vs. searchable）+ 场景"),
            Qs("Describe a library you have used.", "描述一个你用过的图书馆。", 2, "在哪 + 环境 + 你用它做什么 + 一个细节"),
            Qs("Will libraries disappear in the future?", "图书馆会消失吗？", 3, "数字化冲击 + 但空间/社群功能不可替代 + 判断（转型而非消失）"),
        ),
    )

    private val feedback = Scene(
        id = "feedback", zh = "论文反馈", en = "Tutorial feedback", cat = Cat.ACADEMIC,
        goal = "问清哪里弱、确认改法、约定重交时间",
        lines = listOf(
            Ln(
                a = "I've read your draft. The argument is clear, but the structure needs work.",
                aZh = "草稿看过了，论证清楚，但结构要改。",
                b = "Which section feels weakest to you?",
                bZh = "您觉得哪一部分最弱？",
                tip = "问具体位置，比问 how can I improve 有用得多",
            ),
            Ln(
                a = "The jump from the literature review to your method — it isn't signposted.",
                aZh = "从文献综述跳到方法那一段，没有过渡提示。",
                b = "So I should add a transition paragraph?",
                bZh = "所以我该加一段过渡？",
                tip = "signpost = 给读者指路（过渡句/小标题）",
            ),
            Ln(
                a = "Exactly. And tighten the introduction by a page.",
                aZh = "对。另外引言压缩一页。",
                b = "Understood. Could I send you a revised version next week?",
                bZh = "明白。下周能给您修订版吗？",
                tip = "tighten = 压缩（文字），学术修改高频动词",
            ),
            Ln(
                a = "Send it by Wednesday and I'll have a look.",
                aZh = "周三前发我，我看一下。",
                b = "Thanks — I'll mark the changes so you can see what I've done.",
                bZh = "谢谢，我会标出改动，方便您看。",
                tip = "mark the changes = 标出改动，方便对方快速看",
            ),
        ),
        words = listOf(
            Wd("draft", "草稿", "a second draft", "The second draft is much tighter.", "第二稿紧凑多了。"),
            Wd("structure", "结构", "the overall structure", "The structure needs work.", "结构需要改。"),
            Wd("signpost", "（用过渡句）指路", "signpost the argument", "Signpost each section clearly.", "每一部分都要有清晰过渡。"),
            Wd("transition", "过渡", "a transition paragraph", "Add a transition between the two sections.", "两部分之间加一段过渡。"),
            Wd("tighten", "压缩（文字）", "tighten the introduction", "Tighten it by a page.", "压缩一页。"),
            Wd("revise", "修改", "a revised version", "I'll send a revised version on Monday.", "我周一发修订版。"),
            Wd("section", "章节", "the methods section", "The methods section is too long.", "方法部分太长。"),
            Wd("argument", "论证", "a clear argument", "Your argument is clear but repetitive.", "你的论证清楚但重复。"),
            Wd("clarity", "清晰度", "improve clarity", "Short sentences improve clarity.", "短句能提升清晰度。"),
            Wd("cite", "引用", "cite the source", "You need to cite that claim.", "那个主张需要引用来源。"),
        ),
        chunks = listOf(
            Ck("问具体问题", "Which section feels weakest to you?", "把“怎么改”变成“哪里弱”，导师才给得具体"),
            Ck("确认改法", "So I should add a transition paragraph?", "复述你理解的动作，避免改错方向"),
            Ck("约定时间", "Could I send you a revised version next week?", "主动给时间点，显示执行力"),
            Ck("接受批评", "Understood — I'll tighten it and mark the changes.", "接受 + 两个具体动作"),
            Ck("感谢收尾", "Thanks, that's really helpful.", "简短感谢，别过度道歉"),
        ),
        asks = listOf(
            Qs("How do you feel about getting criticism?", "你怎么看待被批评？", 3, "不舒服但有用 + 区分人身与工作 + 具体做法（先听完再问）"),
            Qs("Describe a piece of work you improved after feedback.", "描述一次你根据反馈改进的作品。", 2, "什么作品 + 反馈是什么 + 怎么改 + 结果"),
            Qs("Should students grade their teachers?", "学生应该给老师打分吗？", 3, "应该（改进教学）+ 但要防报复性打分 + 用匿名和多项指标"),
        ),
    )
}

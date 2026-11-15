package com.eric.toolbox.modules.english.ielts

/** 职场类场景：用户点名要的「日常职场对话、跟领导汇报、开会」都在这里。 */
object IeltsWork {

    val list: List<Scene> = listOf(smalltalk, report, meeting, call, negotiate, interview)

    private val smalltalk = Scene(
        id = "smalltalk", zh = "职场日常寒暄", en = "Office small talk", cat = Cat.WORK,
        goal = "开启寒暄、接话反问、自然结束对话",
        lines = listOf(
            Ln(
                a = "Morning! How was your weekend?",
                aZh = "早！周末过得怎么样？",
                b = "Pretty good, thanks — I finally got round to sorting out my flat. How about you?",
                bZh = "挺好的，谢谢。我终于把房子收拾了。你呢？",
                tip = "回答完一定回问 How about you?，否则对话就断了",
            ),
            Ln(
                a = "Not bad. We took the kids to the coast.",
                aZh = "还行。我们带孩子去了海边。",
                b = "Nice. Was the weather decent?",
                bZh = "不错啊。天气还好吗？",
                tip = "decent 在口语里就是“还不错”，比 good 更随意",
            ),
            Ln(
                a = "Better than expected. Anyway, have you met the new designer?",
                aZh = "比预想的好。对了，你见过新来的设计师了吗？",
                b = "Not yet — I'm hoping to catch her at lunch.",
                bZh = "还没，我想午饭时碰一下她。",
                tip = "catch sb = 找机会见到某人，办公室高频",
            ),
            Ln(
                a = "She seems lovely. Right, I'd better get back to it.",
                aZh = "她看起来挺好。好了，我得回去干活了。",
                b = "Same here. Catch you later.",
                bZh = "我也是。回头聊。",
                tip = "I'd better get back to it 是结束闲聊最自然的说法",
            ),
        ),
        words = listOf(
            Wd("catch up", "聊近况 / 补进度", "catch up with sb / on work", "Let's catch up over coffee sometime.", "找时间喝咖啡聊聊近况。"),
            Wd("get round to", "终于抽出时间做（拖了很久的事）", "get round to doing sth", "I finally got round to replying to emails.", "我终于把邮件回了。"),
            Wd("decent", "还不错的", "a decent result", "We had a decent quarter.", "我们这个季度还不错。"),
            Wd("how about you", "你呢（反问）", "… How about you?", "I'm fine, thanks. How about you?", "我挺好，谢谢。你呢？"),
            Wd("catch you later", "回头聊", "catch you later / see you", "I'm off to a meeting — catch you later.", "我去开会了，回头聊。"),
            Wd("take a break", "休息一下", "take a short break", "Shall we take a break?", "我们休息一下？"),
            Wd("run into", "偶遇", "run into sb", "I ran into her in the lift.", "我在电梯里碰到她了。"),
            Wd("colleague", "同事", "a close colleague", "She's a colleague from the Shanghai office.", "她是上海办公室的同事。"),
            Wd("busy period", "忙季", "during the busy period", "We're in our busiest period right now.", "我们现在正是最忙的时候。"),
            Wd("small talk", "闲聊", "make small talk", "He's not great at small talk.", "他不太擅长闲聊。"),
        ),
        chunks = listOf(
            Ck("开启寒暄", "How was your weekend?", "周一最常用；周五可换成 Any plans for the weekend?"),
            Ck("接话并反问", "Pretty good, thanks — how about you?", "回答 + 反问是寒暄的基本结构"),
            Ck("表示共鸣", "Same here. / Tell me about it.", "对方抱怨时用，表示“我也一样”"),
            Ck("自然脱身", "Right, I'd better get back to it.", "比 I have to go 委婉，也不显得冷淡"),
            Ck("约后续", "Let's catch up properly later.", "把闲聊收尾成一次正式沟通"),
        ),
        asks = listOf(
            Qs("Is it important to make small talk with colleagues?", "和同事闲聊重要吗？", 3, "重要：建立信任 + 降低沟通成本 + 举例（跨部门合作更顺）"),
            Qs("Why do some people find small talk difficult?", "为什么有些人觉得闲聊很难？", 3, "性格（introverted）+ 语言/文化差异 + 解决办法（问开放式问题）"),
            Qs("Describe a colleague you get on well with.", "描述一个和你合得来的同事。", 2, "怎么认识 + 一起做过什么 + 他/她哪一点让你舒服"),
        ),
    )

    private val report = Scene(
        id = "report", zh = "向领导汇报进度", en = "Reporting to your manager", cat = Cat.WORK,
        goal = "说清进展、主动提示风险、要资源、约定同步节奏",
        lines = listOf(
            Ln(
                a = "How's the project going?",
                aZh = "项目进展怎么样？",
                b = "We're on track for the end of the month, but there's one risk I want to flag.",
                bZh = "月底完工没问题，但有一个风险我要提一下。",
                tip = "on track for + 时间 = 按计划推进；flag a risk = 主动上报风险",
            ),
            Ln(
                a = "Go on.",
                aZh = "你说。",
                b = "The vendor's delivery has slipped by a week, which could push testing back.",
                bZh = "供应商交货晚了一周，可能把测试往后推。",
                tip = "slip by a week = 晚了一周；push back = 推迟",
            ),
            Ln(
                a = "What do you need from me?",
                aZh = "需要我做什么？",
                b = "Two things: approval to bring in a second tester, and a nudge to the vendor from your side.",
                bZh = "两件事：批准我再加一个测试，以及您那边催一下供应商。",
                tip = "a nudge = 轻轻催一下，比 ask them to hurry 得体",
            ),
            Ln(
                a = "I'll chase them today. Keep me posted.",
                aZh = "我今天去催。随时告诉我进展。",
                b = "Will do — I'll send a short update every Friday.",
                bZh = "好，我每周五发一份简短进展。",
                tip = "Keep me posted / Will do 是职场确认的高频搭配",
            ),
        ),
        words = listOf(
            Wd("on track", "按计划推进", "on track for / to do", "We're on track to launch in June.", "我们按计划六月上线。"),
            Wd("behind schedule", "落后于计划", "fall behind schedule", "Testing is two weeks behind schedule.", "测试比计划晚两周。"),
            Wd("flag", "主动提出（风险/问题）", "flag a risk / an issue", "I want to flag a potential delay.", "我想提一下可能延期。"),
            Wd("slip", "（时间）推迟", "slip by a week", "The deadline slipped to Friday.", "截止日推到了周五。"),
            Wd("push back", "推迟", "push sth back", "We may have to push the launch back.", "我们可能得推迟上线。"),
            Wd("keep sb posted", "随时告知进展", "keep me posted", "Keep me posted on the vendor.", "供应商那边随时告诉我。"),
            Wd("escalate", "升级（上报给更高层）", "escalate the issue", "If it's not fixed by Thursday, we'll escalate.", "周四还没解决就上报。"),
            Wd("milestone", "里程碑", "hit a milestone", "We hit the first milestone early.", "我们提前完成了第一个里程碑。"),
            Wd("nudge", "轻推 / 提醒一下", "give sb a nudge", "Could you give them a nudge?", "你能提醒他们一下吗？"),
            Wd("bandwidth", "可投入的精力/人手", "have the bandwidth", "We don't have the bandwidth this week.", "我们这周抽不出人手。"),
        ),
        chunks = listOf(
            Ck("汇报进展", "We're on track for the end of the month.", "先说结论，再说细节，领导最省时间"),
            Ck("提示风险", "There's one risk I want to flag.", "主动说风险，比等出事再说专业得多"),
            Ck("要资源", "I'd need approval to bring in a second tester.", "要人/要钱都给方案，不要只提问题"),
            Ck("承诺跟进", "I'll send a short update every Friday.", "约定固定节奏，减少反复追问"),
            Ck("请求支持", "Could you give the vendor a nudge from your side?", "让领导出面时用 from your side，给足面子"),
        ),
        asks = listOf(
            Qs("How do you usually report progress at work?", "你平时怎么汇报进展？", 3, "方式（weekly email / stand-up）+ 内容（progress, risks, asks）+ 为什么有效"),
            Qs("Is it better to report bad news early?", "坏消息应该早说吗？", 3, "应该：留出解决时间 + 信任 + 举例；让步：要先想好方案"),
            Qs("Describe a time you had to give someone bad news.", "描述一次你不得不传达坏消息的经历。", 2, "背景 + 你怎么说（soften, then facts）+ 对方反应 + 结果"),
        ),
    )

    private val meeting = Scene(
        id = "meeting", zh = "开会与礼貌反对", en = "Meetings & disagreeing politely", cat = Cat.WORK,
        goal = "表达不同意见、提出替代方案、收尾定行动项",
        lines = listOf(
            Ln(
                a = "So the proposal is to launch in March. Any thoughts?",
                aZh = "方案是三月上线。大家怎么看？",
                b = "I can see the logic, but I'm not sure we've got the data to back it up yet.",
                bZh = "我理解这个思路，但我不确定我们现在的数据能不能支撑。",
                tip = "反对先认可 I can see the logic，再说 but，冲突感立刻降一半",
            ),
            Ln(
                a = "We can use last quarter's numbers.",
                aZh = "我们可以用上季度的数据。",
                b = "That's a fair point, though the market's changed quite a bit since then.",
                bZh = "有道理，不过市场从那时起变化不小。",
                tip = "though 放句中比 but 委婉，适合会议",
            ),
            Ln(
                a = "What would you suggest?",
                aZh = "你有什么建议？",
                b = "A soft launch in two cities first, then scale up if the numbers hold.",
                bZh = "先在两个城市小范围上线，数据站得住再扩大。",
                tip = "soft launch = 试水上线；scale up = 扩大规模",
            ),
            Ln(
                a = "All right, let's try that. Anything else?",
                aZh = "好，那就这么试。还有别的吗？",
                b = "Just one action item: I'll draft the rollout plan by Thursday.",
                bZh = "就一个行动项：我周四前把推广计划草拟出来。",
                tip = "会议结尾用 action item 收口，谁做什么、什么时候，一次说清",
            ),
        ),
        words = listOf(
            Wd("agenda", "议程", "on the agenda", "That's not on today's agenda.", "那不在今天的议程上。"),
            Wd("proposal", "提案", "put forward a proposal", "The proposal needs a cost estimate.", "提案需要一份成本估算。"),
            Wd("back up", "支撑（观点/数据）", "back it up with data", "Can you back that up with numbers?", "能用数据支撑一下吗？"),
            Wd("fair point", "有道理（认可对方）", "that's a fair point", "That's a fair point, but timing matters.", "有道理，但时机很关键。"),
            Wd("push back", "提出反对/质疑", "push back on sth", "Several people pushed back on the timeline.", "好几个人对时间表提出了质疑。"),
            Wd("on the same page", "达成共识", "get everyone on the same page", "Let's make sure we're on the same page.", "确认一下我们理解一致。"),
            Wd("action item", "待办事项（会议）", "assign action items", "Let's list the action items.", "我们把待办列一下。"),
            Wd("follow up", "跟进", "follow up on sth", "I'll follow up with the legal team.", "我去跟法务跟进。"),
            Wd("wrap up", "收尾", "wrap up the meeting", "Let's wrap up in five minutes.", "我们五分钟内收尾。"),
            Wd("take five", "休息五分钟", "take five", "Shall we take five?", "休息五分钟？"),
        ),
        chunks = listOf(
            Ck("礼貌反对", "I can see the logic, but I'm not sure…", "先认可再转折，会议里最安全的反对句式"),
            Ck("部分同意", "That's a fair point, though…", "though 比 but 软，适合当众讨论"),
            Ck("提出替代", "What if we did a soft launch first?", "What if we… 把反对变成方案"),
            Ck("拉回共识", "Just to make sure we're on the same page…", "会议跑偏时把人拉回来"),
            Ck("收尾定人", "So the action item is on me, by Thursday.", "确认归属和时间，避免会后没人动"),
        ),
        asks = listOf(
            Qs("Do you think meetings are always necessary?", "开会总是必要的吗？", 3, "不一定：目标不清的会浪费时间 + 替代（email, stand-up）+ 什么会必须开"),
            Qs("How do people disagree politely in your culture?", "你们文化里怎么礼貌地表达不同意见？", 3, "先肯定 + 用问题代替否定 + 私下沟通，最后对比西方直接表达"),
            Qs("Describe a meeting that went well.", "描述一次开得好的会。", 2, "什么会 + 谁主持 + 为什么好（clear goal, decisions made）+ 结果"),
        ),
    )

    private val call = Scene(
        id = "call", zh = "电话与视频会议", en = "Phone & video calls", cat = Cat.WORK,
        goal = "处理听不清、约回拨、确认结论",
        lines = listOf(
            Ln(
                a = "Can you hear me okay?",
                aZh = "能听清吗？",
                b = "You're a bit choppy — could you say that again?",
                bZh = "你那边有点卡，能再说一遍吗？",
                tip = "choppy = 声音断续；也可以说 you're breaking up",
            ),
            Ln(
                a = "Sure. I was saying we need the signed contract by Friday.",
                aZh = "好。我刚说我们周五前要拿到签好的合同。",
                b = "Right. I'll chase the client today and confirm by email.",
                bZh = "明白。我今天催客户，邮件确认。",
                tip = "I was saying 用于重说刚才那句，比 repeat 自然",
            ),
            Ln(
                a = "Perfect. Sorry, I'm losing you — shall I call you back?",
                aZh = "好。抱歉信号不行了，我打回来？",
                b = "Yes, give me five minutes and I'll be at my desk.",
                bZh = "好，给我五分钟，我到座位上。",
                tip = "give me five minutes = 给我五分钟，比 wait 礼貌",
            ),
            Ln(
                a = "Great, talk in a bit.",
                aZh = "好，一会儿聊。",
                b = "Thanks — I'll send the file before we speak.",
                bZh = "谢谢，我把文件在通话前发过去。",
                tip = "talk in a bit / speak soon 是电话结束语",
            ),
        ),
        words = listOf(
            Wd("choppy", "（声音）断续", "the line is choppy", "The line's choppy — can you hear me?", "信号断断续续，你能听到吗？"),
            Wd("break up", "（通话）中断/断续", "you're breaking up", "Sorry, you're breaking up.", "抱歉，你那边断断续续。"),
            Wd("mute", "静音", "you're on mute", "You're on mute, we can't hear you.", "你静音了，我们听不到。"),
            Wd("share screen", "共享屏幕", "share your screen", "Could you share your screen?", "能共享一下屏幕吗？"),
            Wd("drop out", "掉线", "drop out of the call", "He dropped out halfway through.", "他中途掉线了。"),
            Wd("call back", "回拨", "call you back", "I'll call you back in five minutes.", "我五分钟后再打给你。"),
            Wd("on hold", "等待接通", "put sb on hold", "I was on hold for ten minutes.", "我等了十分钟。"),
            Wd("dial in", "拨入（会议）", "dial in with a code", "You can dial in with the code below.", "可以用下面的号码拨入。"),
            Wd("lag", "延迟", "there's a lag", "There's a two-second lag.", "有两秒延迟。"),
            Wd("background noise", "背景噪音", "cut the background noise", "Could you cut the background noise?", "能降低一下背景噪音吗？"),
        ),
        chunks = listOf(
            Ck("确认能听见", "Can you hear me okay?", "开场必问，比 Can you hear me 自然"),
            Ck("请对方重说", "Sorry, you cut out — could you say that again?", "cut out / choppy / breaking up 三种说法都自然"),
            Ck("确认结论", "So just to confirm, we need it by Friday.", "电话里最容易漏信息，确认一句很关键"),
            Ck("约回拨", "Shall I call you back in five minutes?", "信号不好时主动提议"),
            Ck("结束通话", "Thanks — I'll send a follow-up email.", "电话结束前说清下一步书面确认"),
        ),
        asks = listOf(
            Qs("Do you prefer phone calls or messages?", "你更喜欢打电话还是发消息？", 1, "选择 + 理由（efficient vs. record）+ 场景区分"),
            Qs("What are the problems with online meetings?", "线上会议有什么问题？", 3, "技术（lag, mute）+ 注意力（multitasking）+ 关系建立（less small talk）+ 建议"),
            Qs("Describe a difficult phone call you had.", "描述一次难打的电话。", 2, "和谁 + 为什么难（bad line / difficult topic）+ 怎么处理 + 结果"),
        ),
    )

    private val negotiate = Scene(
        id = "negotiate", zh = "谈判与催进度", en = "Negotiating & chasing", cat = Cat.WORK,
        goal = "回应压价、用条件换条件、把承诺落到书面",
        lines = listOf(
            Ln(
                a = "We'd need a 10% discount to sign this quarter.",
                aZh = "这个季度要签，我们得拿到九折。",
                b = "I understand the pressure, but 10% is beyond what we can do at this volume.",
                bZh = "我理解你们的压力，但这个量要做到九折我们做不到。",
                tip = "先共情 I understand… 再给边界，比直接拒绝好谈",
            ),
            Ln(
                a = "What could you offer?",
                aZh = "那你们能给什么？",
                b = "If you commit to twelve months, we could do 6% and include onboarding.",
                bZh = "如果签十二个月，我们可以让 6% 并送 onboarding。",
                tip = "If you…, we could… 是典型的条件换条件",
            ),
            Ln(
                a = "Could you hold that price for sixty days?",
                aZh = "这个价格能保留六十天吗？",
                b = "I can hold it for thirty, and I'll confirm in writing.",
                bZh = "我可以保留三十天，并书面确认。",
                tip = "in writing = 书面确认，谈判里必须落实的一步",
            ),
            Ln(
                a = "All right, send it over and I'll take it to my boss.",
                aZh = "好，发给我，我拿去给老板看。",
                b = "Will do. I'll put the pricing and the timeline in one page so it's easy to forward.",
                bZh = "好，我把价格和时间表写成一页，方便你转给老板。",
                tip = "one page / easy to forward，帮对方内部推动，成交率更高",
            ),
        ),
        words = listOf(
            Wd("discount", "折扣", "a 10% discount", "Can you do a volume discount?", "能有批量折扣吗？"),
            Wd("volume", "采购量", "at this volume", "At this volume, the margin is thin.", "这个量利润很薄。"),
            Wd("commit to", "承诺（期限/数量）", "commit to twelve months", "We can commit to two years.", "我们可以签两年。"),
            Wd("quote", "报价", "send a quote", "I'll send a formal quote today.", "我今天发正式报价。"),
            Wd("in writing", "书面形式", "confirm in writing", "Please confirm the terms in writing.", "请书面确认条款。"),
            Wd("hold a price", "保价", "hold the price for 30 days", "We can hold the price until Friday.", "价格可以保留到周五。"),
            Wd("bottom line", "底线", "our bottom line", "That's our bottom line.", "这是我们的底线。"),
            Wd("sweeten the deal", "加码（让条件更有吸引力）", "sweeten the deal with training", "We sweetened the deal with free training.", "我们用免费培训加码。"),
            Wd("terms", "条款", "payment terms", "The payment terms are 30 days.", "付款条件是 30 天。"),
            Wd("walk away", "放弃交易", "be prepared to walk away", "We were ready to walk away.", "我们做好了不谈的准备。"),
        ),
        chunks = listOf(
            Ck("共情后设边界", "I understand the pressure, but that's beyond what we can do.", "谈判里最稳的拒绝结构"),
            Ck("条件换条件", "If you commit to twelve months, we could do 6%.", "永远别白给折扣，换点东西回来"),
            Ck("争取时间", "I can hold that for thirty days.", "保价期是谈判筹码，别一次给太长"),
            Ck("落实书面", "I'll confirm that in writing today.", "口头承诺必须落纸，避免后面扯皮"),
            Ck("催进度", "Just checking in on the signed copy — any news?", "催件用 checking in，不像在施压"),
        ),
        asks = listOf(
            Qs("Is negotiation a skill everyone needs?", "谈判是每个人都需要的能力吗？", 3, "是：工作、租房、甚至带孩子 + 核心是听懂对方真正在意什么"),
            Qs("Describe a time you negotiated something.", "描述一次你谈判的经历。", 2, "谈什么 + 你的底线 + 怎么换条件 + 结果"),
            Qs("Why do some people avoid negotiating?", "为什么有些人回避谈判？", 3, "怕冲突 + 不了解市场价 + 文化因素，最后说怎么克服"),
        ),
    )

    private val interview = Scene(
        id = "interview", zh = "求职面试", en = "Job interview", cat = Cat.WORK,
        goal = "讲清一个项目、说方法和结果、承认不足并给改进",
        lines = listOf(
            Ln(
                a = "Tell me about a project you're proud of.",
                aZh = "说一个你引以为豪的项目。",
                b = "I led a team that cut the patent review cycle from six weeks to three.",
                bZh = "我带团队把专利审核周期从六周压到三周。",
                tip = "一句话给结果 + 数字，比先讲背景有效得多",
            ),
            Ln(
                a = "How did you manage that?",
                aZh = "怎么做到的？",
                b = "We standardised the templates and introduced a weekly triage meeting, which removed most of the back-and-forth.",
                bZh = "我们把模板标准化，又加了每周分诊会，减少了大部分来回沟通。",
                tip = "讲方法用两件具体事 + which 从句说效果",
            ),
            Ln(
                a = "And what would you do differently?",
                aZh = "有什么你会做得不一样？",
                b = "I'd involve the legal team earlier — we lost time fixing wording at the end.",
                bZh = "我会更早让法务介入，最后改措辞浪费了时间。",
                tip = "承认不足 + 具体改进，不要说 I can't think of anything",
            ),
            Ln(
                a = "Why do you want this role?",
                aZh = "为什么想要这个职位？",
                b = "It's the mix I'm after: hands-on work with clients and room to build the team's process.",
                bZh = "这正是我想要的组合：直接做客户项目，又有空间搭团队流程。",
                tip = "the mix I'm after 比 I like it 具体，显示你想清楚了",
            ),
        ),
        words = listOf(
            Wd("strength", "优势", "a key strength", "My strength is simplifying complex cases.", "我的优势是把复杂案子讲简单。"),
            Wd("weakness", "短板", "a real weakness", "Public speaking used to be a weakness.", "公开演讲以前是我的短板。"),
            Wd("take ownership", "主动担责", "take ownership of sth", "I took ownership of the client relationship.", "我主动接手了客户关系。"),
            Wd("deliver", "交付（成果）", "deliver on time", "We delivered two weeks early.", "我们提前两周交付。"),
            Wd("stakeholder", "利益相关方", "manage stakeholders", "I kept five stakeholders aligned.", "我让五个相关方保持一致。"),
            Wd("deadline", "截止期限", "meet a tight deadline", "We met a very tight deadline.", "我们赶上了很紧的期限。"),
            Wd("track record", "过往业绩", "a strong track record", "She has a strong track record in IP.", "她在知识产权方面业绩很硬。"),
            Wd("hands-on", "亲力亲为的", "hands-on experience", "I want hands-on work, not just oversight.", "我想要亲手做，不只是监督。"),
            Wd("step up", "挺身承担", "step up when needed", "He stepped up when the lead left.", "负责人走了，他顶了上来。"),
            Wd("fit", "（与岗位的）匹配度", "a good fit for the team", "I think I'm a good fit for this team.", "我觉得我很适合这个团队。"),
        ),
        chunks = listOf(
            Ck("一句话讲成果", "I led a team that cut the cycle from six weeks to three.", "动词 + 数字，面试官最容易记住"),
            Ck("讲方法", "We did two things: … and …, which removed most of the back-and-forth.", "两件事 + which 说效果，结构清楚"),
            Ck("承认不足", "I'd involve the legal team earlier next time.", "不足必须配改进，否则像在找借口"),
            Ck("说动机", "It's the mix I'm after: … and …", "动机拆成两个具体点，比 I like challenges 可信"),
            Ck("收尾反问", "What does success look like in the first six months?", "反问岗位期望，显得你在想怎么干好"),
        ),
        asks = listOf(
            Qs("What do you think makes a good interview?", "你觉得什么样的面试算好？", 3, "双向了解 + 具体例子 + 坦诚（包括不足）"),
            Qs("Describe a skill you would like to improve.", "描述一项你想提升的技能。", 2, "什么技能 + 为什么重要 + 现在怎么练 + 目标"),
            Qs("Is a university degree still important for getting a job?", "学历对找工作还重要吗？", 3, "仍然重要（筛选门槛）+ 但技能/项目更关键 + 行业差异"),
        ),
    )
}

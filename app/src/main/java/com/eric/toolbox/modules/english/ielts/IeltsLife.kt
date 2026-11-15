package com.eric.toolbox.modules.english.ielts

/** 生活类场景：雅思口语 Part 1 的高频题，也是听力 Section 1 的典型对话。 */
object IeltsLife {

    // by lazy 不只是省时间：单例初始化是按声明顺序走的，直接写 = listOf(...) 会去读
    // 还没初始化的 restaurant/hotel…（Kotlin 报 Variable must be initialized）
    val list: List<Scene> by lazy { listOf(restaurant, hotel, renting, airport, bank, clinic, transit, shopping, post, complaint) }

    private val restaurant = Scene(
        id = "restaurant", zh = "餐厅点餐", en = "Ordering food", cat = Cat.LIFE,
        goal = "订位、问推荐、说明忌口、点单、买单分账",
        lines = listOf(
            Ln(
                a = "Good evening — do you have a reservation?",
                aZh = "晚上好，有预订吗？",
                b = "Yes, under Chen — a table for two. I booked online this morning.",
                bZh = "有的，姓陈，两位。我今天早上在网上订的。",
                tip = "报预订用 under + 姓氏，比 My name is Chen 地道",
            ),
            Ln(
                a = "Any dietary requirements I should know about?",
                aZh = "有什么忌口吗？",
                b = "I'm allergic to shellfish, so nothing with prawns or crab, please.",
                bZh = "我对贝类过敏，所以请不要放虾或蟹。",
                tip = "说 I cannot eat shrimp 不算错，但 I'm allergic to… 更自然，也更容易被重视",
            ),
            Ln(
                a = "What can I get you to start?",
                aZh = "前菜要点什么？",
                b = "I'll have the mushroom soup to start, then the lamb — medium rare, with the vegetables on the side.",
                bZh = "前菜我要蘑菇汤，然后要羊肉，三分熟，配菜另放。",
                tip = "点单说 I'll have…，不说 I want…；on the side = 另放，不浇在菜上",
            ),
            Ln(
                a = "Would you like to see the dessert menu, or shall I bring the bill?",
                aZh = "要看甜点单吗，还是我直接拿账单来？",
                b = "Just the bill, thanks. Could we split it two ways?",
                bZh = "直接结账吧，谢谢。能分开付吗？两个人各付一半。",
                tip = "split the bill = AA；英式还可以说 the bill，美式说 the check",
            ),
        ),
        words = listOf(
            Wd("reservation", "预订（餐厅/酒店）", "make / cancel a reservation", "I made a reservation for two at seven.", "我订了七点两位的位置。"),
            Wd("dietary requirement", "饮食禁忌（过敏、素食等）", "any dietary requirements?", "Do you have any dietary requirements?", "您有什么忌口吗？"),
            Wd("allergic to", "对……过敏", "be allergic to shellfish / nuts", "She's allergic to nuts, so we avoid satay sauce.", "她对坚果过敏，所以我们不点沙嗲酱。"),
            Wd("starter", "前菜", "for starters / to start", "I'll skip the starter and go straight to the main.", "我不要前菜，直接上主菜。"),
            Wd("main course", "主菜", "the main course", "The main course came with a side salad.", "主菜配了一份沙拉。"),
            Wd("on the side", "（酱汁/配菜）另放", "dressing on the side", "Could I have the dressing on the side?", "酱汁能另放吗？"),
            Wd("medium rare", "三分熟（牛排）", "medium rare / medium / well done", "I'd like my steak medium rare, please.", "我的牛排要三分熟。"),
            Wd("split the bill", "分开付账 / AA", "split the bill two ways", "Let's split the bill — it's easier.", "我们分开付吧，简单点。"),
            Wd("still water", "不加气的矿泉水", "still or sparkling", "Still water for the table, please.", "给桌上拿瓶不加气的矿泉水。"),
            Wd("speciality", "招牌菜 / 特色菜", "the house speciality", "What's your speciality tonight?", "今晚的招牌菜是什么？"),
        ),
        chunks = listOf(
            Ck("第一次来，问推荐", "What would you recommend?", "菜单看不懂或想尝特色时，比 What is good? 自然"),
            Ck("委婉提要求", "Could I have the sauce on the side?", "任何“能不能改成……”都用 Could I have…，不要用 I want"),
            Ck("说明忌口", "I'm allergic to… / I can't have…", "过敏说 allergic，宗教/习惯不吃说 I don't eat…"),
            Ck("提出分账", "Could we split the bill two ways?", "结账时说，服务生一般直接拿两台 POS"),
            Ck("结束用餐", "That was lovely, thank you. Could we get the bill?", "先夸一句再要账单，英式礼貌的常见顺序"),
        ),
        asks = listOf(
            Qs("Do you often eat out?", "你经常在外面吃饭吗？", 1, "频率（once or twice a week）+ 场合（friends / after work）+ 原因（too tired to cook）"),
            Qs("What kind of food do you prefer?", "你更喜欢什么菜？", 1, "口味（spicy / light）+ 举例（Sichuan, Japanese）+ 为什么（fresh ingredients）"),
            Qs("Why do you think people enjoy eating out these days?", "为什么现在的人喜欢外出就餐？", 3, "两点原因（convenience, socialising）+ 对比过去（busier lives）+ 一句让步（home cooking is healthier）"),
        ),
    )

    private val hotel = Scene(
        id = "hotel", zh = "酒店入住与投诉", en = "Hotel check-in & complaints", cat = Cat.LIFE,
        goal = "报预订、确认含早与退房时间、换房、投诉噪音",
        lines = listOf(
            Ln(
                a = "Good afternoon, welcome to the Riverside. Checking in?",
                aZh = "下午好，欢迎来到河畔酒店。办理入住吗？",
                b = "Yes — reservation under Chen, for three nights.",
                bZh = "是的，姓陈，订了三晚。",
                tip = "check in 是动词，check-in 是名词；口语里两个都能听到",
            ),
            Ln(
                a = "Could I see your passport? You're in room 812 — breakfast is on the seventh floor.",
                aZh = "能看一下护照吗？您住 812，早餐在七楼。",
                b = "Great. Is breakfast included, and what time does it finish?",
                bZh = "好。早餐含在房费里吗？到几点结束？",
                tip = "included 单独用就表示“含在价格里”，不用加 in the price",
            ),
            Ln(
                a = "It's included, served from seven to ten. Anything else?",
                aZh = "含的，七点到十点。还有别的事吗？",
                b = "Actually, the room faces the main road — would it be possible to move to a quieter one?",
                bZh = "其实这间房朝主路，能不能换一间安静点的？",
                tip = "投诉先 Actually 或 I'm afraid 起头，比直接说 The room is noisy 缓和",
            ),
            Ln(
                a = "Let me check… I can move you tomorrow morning. Would that be all right?",
                aZh = "我查一下……明早可以给您换。可以吗？",
                b = "That works. And could you note that I'd like a late check-out on Sunday?",
                bZh = "可以。另外能备注一下我周日想延迟退房吗？",
                tip = "That works 是接受方案最省事的一句，比 OK 正式一点",
            ),
        ),
        words = listOf(
            Wd("check in", "办理入住", "check in / check out", "We checked in around nine in the evening.", "我们晚上九点左右办的入住。"),
            Wd("late check-out", "延迟退房", "request a late check-out", "Could I have a late check-out at two?", "能延迟到两点退房吗？"),
            Wd("included", "包含在房费里", "breakfast is included", "Is the city tax included in the rate?", "城市税含在房价里吗？"),
            Wd("upgrade", "升级房型", "a free upgrade", "They gave us an upgrade to a suite.", "他们给我们免费升级到套房。"),
            Wd("room service", "客房送餐", "order room service", "We ordered room service at midnight.", "我们半夜叫了客房送餐。"),
            Wd("face", "（房间）朝向", "face the street / the sea", "The room faces the main road, so it's noisy.", "这间房朝主路，所以很吵。"),
            Wd("move to", "换到（另一个房间）", "move to a quieter room", "Could we move to a room on a higher floor?", "能换到高层的房间吗？"),
            Wd("deposit", "押金", "pay / return a deposit", "The deposit is returned when you check out.", "押金退房时退还。"),
            Wd("complaint", "投诉", "make a complaint", "I'd like to make a complaint about the noise.", "我要投诉噪音问题。"),
            Wd("housekeeping", "客房清洁（部门）", "call housekeeping", "Could you ask housekeeping for extra towels?", "能让客房部多送几条毛巾吗？"),
        ),
        chunks = listOf(
            Ck("报预订", "I have a reservation under Chen for three nights.", "报姓氏 + 晚数，前台最想听的两条信息"),
            Ck("委婉投诉", "I'm afraid the room is quite noisy — would it be possible to move?", "I'm afraid + 事实 + 请求，三段式，语气不冲"),
            Ck("确认细节", "Just to confirm, breakfast is included, right?", "Just to confirm 后面跟你要核对的那一条"),
            Ck("提出请求", "Could you note that I'd like a late check-out?", "要备注要求用 note / put a note on my booking"),
            Ck("接受方案", "That works for me, thanks.", "接受对方给的安排，比 OK 更有回应感"),
        ),
        asks = listOf(
            Qs("Do you prefer hotels or apartments when you travel?", "旅行时你更喜欢酒店还是民宿？", 1, "选择 + 两点理由（service / space）+ 例外（long stays）"),
            Qs("Describe a hotel you enjoyed staying at.", "描述一次住得舒服的酒店经历。", 2, "在哪 + 和谁 + 房间/服务细节 + 为什么记得（one thing that stood out）"),
            Qs("What makes a hotel good in your opinion?", "你觉得什么样的酒店算好？", 3, "位置 + 服务响应速度 + 隔音/床这类细节 + 价格是否匹配"),
        ),
    )

    private val renting = Scene(
        id = "renting", zh = "看房租房", en = "Renting a flat", cat = Cat.LIFE,
        goal = "问租金与包含项、谈押金退还、争取短租条款、约搬入时间",
        lines = listOf(
            Ln(
                a = "So this is the flat — one bedroom, fully furnished, ten minutes' walk to the tube.",
                aZh = "这就是房子，一居室，家具齐全，走到地铁十分钟。",
                b = "It looks great. How much is the rent, and what does it include?",
                bZh = "看起来不错。租金多少？包含什么？",
                tip = "问“包含什么”用 what does it include，比 is it include 正确",
            ),
            Ln(
                a = "£1,450 a month, bills not included. There's a deposit of one month's rent.",
                aZh = "每月 1450 镑，不含水电。押金是一个月租金。",
                b = "Is the deposit refundable if nothing's damaged?",
                bZh = "如果没损坏东西，押金能退吗？",
                tip = "refundable 比 can return 准确；押金在英式口语里常说 deposit",
            ),
            Ln(
                a = "Fully refundable, yes. The contract runs for twelve months.",
                aZh = "全额退。合同是十二个月。",
                b = "Would it be possible to have a six-month break clause? My contract might be extended.",
                bZh = "能不能加一个六个月解约条款？我的合同可能会延长。",
                tip = "break clause = 提前解约条款，租房谈判的核心词",
            ),
            Ln(
                a = "I'd have to check with the landlord, but it's usually possible.",
                aZh = "我得问一下房东，不过一般可以。",
                b = "No problem. And when could I move in?",
                bZh = "没问题。那我什么时候能搬进来？",
                tip = "move in / move out 是搬家入住和搬走的固定说法",
            ),
        ),
        words = listOf(
            Wd("rent", "租金", "pay rent monthly", "The rent is due on the first of each month.", "房租每月一号交。"),
            Wd("deposit", "押金", "a refundable deposit", "We paid a deposit of one month's rent.", "我们付了一个月租金当押金。"),
            Wd("refundable", "可退还的", "fully refundable", "The deposit is fully refundable.", "押金全额可退。"),
            Wd("bills included", "含水电煤网", "bills included / excluded", "The rent is 900 with bills included.", "租金 900，含水电网。"),
            Wd("furnished", "带家具的", "fully / partly furnished", "The flat comes fully furnished.", "这套房子家具齐全。"),
            Wd("tenancy agreement", "租赁合同", "sign a tenancy agreement", "Read the tenancy agreement before you sign.", "签之前先看租赁合同。"),
            Wd("break clause", "提前解约条款", "a six-month break clause", "The break clause lets you leave after six months.", "解约条款允许你六个月后搬走。"),
            Wd("landlord", "房东", "contact the landlord", "The landlord pays for repairs.", "维修由房东出钱。"),
            Wd("move in", "搬入", "move in on Saturday", "We can move in at the end of the month.", "我们月底可以搬进去。"),
            Wd("viewing", "看房", "arrange a viewing", "Can I arrange a viewing this weekend?", "这周末能约看房吗？"),
        ),
        chunks = listOf(
            Ck("问包含项", "What does the rent include?", "租房、买服务都能用，比 What is included 更口语"),
            Ck("谈条件", "Would it be possible to have a six-month break clause?", "谈判万能句式，比 I want 软很多"),
            Ck("确认退款", "Is the deposit refundable if nothing's damaged?", "把条件说清楚，避免以后扯皮"),
            Ck("表达意向", "I'm quite keen on it, but I'd need to think about the price.", "想租又嫌贵时的标准说法，留了还价空间"),
            Ck("约时间", "When could I move in?", "问入住时间，比 When can I live here 自然"),
        ),
        asks = listOf(
            Qs("Do you live in a house or a flat?", "你住房子还是公寓？", 1, "类型 + 和谁住 + 一个细节（view / size）"),
            Qs("Describe a home you would like to live in.", "描述你想住的房子。", 2, "位置 + 房型 + 两个细节（study, balcony）+ 为什么"),
            Qs("Why is housing so expensive in big cities?", "为什么大城市房价这么贵？", 3, "需求集中 + 土地有限 + 投资属性，最后给一个可能的缓解办法"),
        ),
    )

    private val airport = Scene(
        id = "airport", zh = "机场值机与海关", en = "Airport & customs", cat = Cat.LIFE,
        goal = "托运行李、处理超重、确认登机口、回答入境目的",
        lines = listOf(
            Ln(
                a = "Passport and boarding pass, please. Any bags to check in?",
                aZh = "请给护照和登机牌。有行李要托运吗？",
                b = "Just this one, and I'd like to keep the smaller one as hand luggage.",
                bZh = "就这一件，小的我想随身带。",
                tip = "hand luggage（英）/ carry-on（美），雅思听力两个都出现",
            ),
            Ln(
                a = "Could you put it on the scale? … It's two kilos over.",
                aZh = "放秤上好吗？……超了两公斤。",
                b = "Oh — how much would it cost to pay for the extra weight?",
                bZh = "哦，超重费多少钱？",
                tip = "extra weight / excess baggage 都指超重行李",
            ),
            Ln(
                a = "Forty pounds, or you could move a couple of items into your carry-on.",
                aZh = "四十镑，或者您可以把几件东西挪到随身行李里。",
                b = "I'll move a few things across, thanks.",
                bZh = "那我挪几样过去，谢谢。",
                tip = "move sth across / into 是“挪过去”，比 put 更具体",
            ),
            Ln(
                a = "Any liquids or sharp objects? Gate 22, boarding at 14:40.",
                aZh = "有液体或尖锐物品吗？22 号登机口，14:40 登机。",
                b = "Thanks. Is the gate far from security?",
                bZh = "谢谢。登机口离安检远吗？",
                tip = "问距离用 Is it far from…，回答可以说 It's a ten-minute walk",
            ),
        ),
        words = listOf(
            Wd("check in", "办理值机 / 托运", "check in a bag", "I need to check in two bags.", "我要托运两件行李。"),
            Wd("hand luggage", "随身行李", "hand luggage only", "I'm travelling with hand luggage only.", "我只带随身行李。"),
            Wd("boarding pass", "登机牌", "show your boarding pass", "Keep your boarding pass handy.", "登机牌拿在手边。"),
            Wd("excess baggage", "超重行李", "pay for excess baggage", "Excess baggage costs forty pounds.", "超重行李要四十镑。"),
            Wd("scale", "秤", "put it on the scale", "Could you put your bag on the scale?", "能把包放秤上吗？"),
            Wd("liquid", "液体（安检限制）", "liquids under 100 ml", "Liquids must be under 100 millilitres.", "液体不能超过 100 毫升。"),
            Wd("gate", "登机口", "gate 22 / boarding gate", "The flight leaves from gate 22.", "航班在 22 号登机口。"),
            Wd("boarding time", "登机时间", "boarding starts at…", "Boarding time is 14:40.", "登机时间是 14:40。"),
            Wd("security check", "安检", "go through security", "It took twenty minutes to get through security.", "过安检花了二十分钟。"),
            Wd("connecting flight", "转机航班", "miss a connecting flight", "I have a connecting flight in Dubai.", "我在迪拜转机。"),
        ),
        chunks = listOf(
            Ck("说明行李", "I'd like to check in this one and keep the other as hand luggage.", "值机柜台最常听的一句"),
            Ck("问超重费用", "How much would it cost for the extra weight?", "问价格用 How much would it cost…"),
            Ck("确认登机口", "Which gate is it, and what time does boarding start?", "两个信息一起问，省一轮"),
            Ck("回答入境目的", "I'm here on business for a week.", "海关问 purpose of visit，一句话答完目的 + 时长"),
            Ck("请求帮助", "Could you tell me where the transfer desk is?", "机场问路万能句"),
        ),
        asks = listOf(
            Qs("Do you enjoy travelling by plane?", "你喜欢坐飞机出行吗？", 1, "态度 + 理由（fast, comfortable）+ 一个不便（airports are far）"),
            Qs("Describe a journey that didn't go as planned.", "描述一次不顺利的行程。", 2, "去哪 + 出了什么事（delay / missed connection）+ 怎么解决 + 感受"),
            Qs("How has air travel changed in your country?", "你国家的航空出行有什么变化？", 3, "更便宜/更多航线 + 高铁竞争 + 对普通人的影响"),
        ),
    )

    private val bank = Scene(
        id = "bank", zh = "银行开户", en = "Opening a bank account", cat = Cat.LIFE,
        goal = "说明来意、提供住址证明、问月费、同时开通网银",
        lines = listOf(
            Ln(
                a = "Good morning. How can I help?",
                aZh = "早上好，有什么可以帮您？",
                b = "I'd like to open a current account, please. I've just moved here for work.",
                bZh = "我想开一个活期账户。我刚搬来这里工作。",
                tip = "current account（英）= 活期账户；美式叫 checking account",
            ),
            Ln(
                a = "Do you have proof of address and a form of ID?",
                aZh = "您有住址证明和身份证件吗？",
                b = "I've got my passport and a tenancy agreement — would that be enough?",
                bZh = "我有护照和租房合同，够吗？",
                tip = "would that be enough 是确认材料够不够的固定问法",
            ),
            Ln(
                a = "That should be fine. Do you want a debit card with contactless?",
                aZh = "应该可以。您要带闪付的借记卡吗？",
                b = "Yes, please. And could I set up online banking at the same time?",
                bZh = "要的。能同时开通网银吗？",
                tip = "set up 用于开通服务；at the same time 表示一并办",
            ),
            Ln(
                a = "Certainly. There's a monthly fee unless you pay in a salary.",
                aZh = "当然。如果不代发工资，会有月费。",
                b = "That's fine — my salary will be paid in every month.",
                bZh = "没问题，我每月工资会打进来。",
                tip = "pay in 是“存入（账户）”，工资代发常用 have your salary paid in",
            ),
        ),
        words = listOf(
            Wd("current account", "活期账户（英）", "open a current account", "I'd like to open a current account.", "我想开个活期账户。"),
            Wd("savings account", "储蓄账户", "a savings account", "The savings account pays a little interest.", "储蓄账户有一点利息。"),
            Wd("proof of address", "住址证明", "provide proof of address", "A utility bill works as proof of address.", "水电账单可以当住址证明。"),
            Wd("debit card", "借记卡", "a contactless debit card", "The debit card arrived in five days.", "借记卡五天就到了。"),
            Wd("contactless", "闪付 / 非接触支付", "pay contactless", "You can just tap — it's contactless.", "直接贴一下就行，是闪付。"),
            Wd("online banking", "网银", "set up online banking", "I do most of my payments through online banking.", "我大部分付款都用网银。"),
            Wd("monthly fee", "月费", "charge a monthly fee", "There's no monthly fee on this account.", "这个账户没有月费。"),
            Wd("pay in", "存入（账户）", "have your salary paid in", "My salary is paid in on the 28th.", "我工资 28 号到账。"),
            Wd("transfer", "转账", "make a transfer", "I need to make an international transfer.", "我要转一笔国际汇款。"),
            Wd("statement", "对账单", "a bank statement", "The bank asked for three months' statements.", "银行要了三个月的流水。"),
        ),
        chunks = listOf(
            Ck("说明来意", "I'd like to open a current account.", "银行业务开场，I'd like to 比 I want 礼貌"),
            Ck("提供材料", "I've got my passport — would that be enough?", "材料不确定够不够时的标准问法"),
            Ck("问费用", "Is there a monthly fee?", "问任何收费都能用 Is there a… fee?"),
            Ck("一并办理", "Could I set up online banking at the same time?", "省一趟的关键句"),
            Ck("问时长", "How long does it take for the card to arrive?", "问办理时间用 How long does it take"),
        ),
        asks = listOf(
            Qs("Do you prefer paying by cash or by card?", "你更喜欢现金还是刷卡？", 1, "选择 + 理由（convenient, track spending）+ 例外（small shops）"),
            Qs("Describe a time you had a problem with money.", "描述一次和钱有关的麻烦。", 2, "什么时候 + 什么问题（lost card / transfer delayed）+ 怎么解决 + 学到什么"),
            Qs("What are the advantages and disadvantages of a cashless society?", "无现金社会的利弊？", 3, "利（convenience, records）+ 弊（privacy, elderly excluded）+ 你的判断"),
        ),
    )

    private val clinic = Scene(
        id = "clinic", zh = "看病买药", en = "At the clinic", cat = Cat.LIFE,
        goal = "描述症状与持续时间、说明在吃什么药、问副作用、要病假条",
        lines = listOf(
            Ln(
                a = "What seems to be the problem?",
                aZh = "哪里不舒服？",
                b = "I've had a sore throat for about four days, and it's getting worse when I swallow.",
                bZh = "我喉咙痛了大约四天，吞咽时更疼。",
                tip = "I've had… for four days 用现在完成时，强调持续到现在",
            ),
            Ln(
                a = "Any fever or cough? Are you taking anything at the moment?",
                aZh = "有发烧或咳嗽吗？目前在吃什么药？",
                b = "No fever, but I've been taking paracetamol twice a day.",
                bZh = "没发烧，但我一直在吃扑热息痛，一天两次。",
                tip = "I've been taking 表示“一直在吃”，比 I take 更贴合现状",
            ),
            Ln(
                a = "I'll prescribe antibiotics — one tablet three times a day after meals.",
                aZh = "我给你开抗生素，一天三次，一次一片，饭后吃。",
                b = "Is it safe to take with the paracetamol? And can I drink coffee?",
                bZh = "和扑热息痛一起吃安全吗？能喝咖啡吗？",
                tip = "问相互作用用 Is it safe to take with…",
            ),
            Ln(
                a = "Yes to both. Come back if it hasn't improved in a week.",
                aZh = "都可以。一周内没好转就再来。",
                b = "Will do — could I get a sick note for work as well?",
                bZh = "好的。另外能给我开一张病假条吗？",
                tip = "sick note（英）/ doctor's note；公司请假常要这个",
            ),
        ),
        words = listOf(
            Wd("symptom", "症状", "develop symptoms", "The first symptom was a headache.", "最早的症狀是头痛。"),
            Wd("sore throat", "喉咙痛", "have a sore throat", "I've had a sore throat since Monday.", "我从周一开始喉咙痛。"),
            Wd("swallow", "吞咽", "it hurts when I swallow", "It hurts when I swallow.", "我咽东西的时候疼。"),
            Wd("fever", "发烧", "run a fever", "She's running a mild fever.", "她有点低烧。"),
            Wd("prescribe", "开（药）", "prescribe antibiotics", "The doctor prescribed a course of antibiotics.", "医生开了一个疗程的抗生素。"),
            Wd("dose", "剂量", "a double dose", "Don't exceed the recommended dose.", "不要超过建议剂量。"),
            Wd("tablet", "药片", "one tablet a day", "Take one tablet after breakfast.", "早饭后吃一片。"),
            Wd("after meals", "饭后", "take it after meals", "Take it three times a day after meals.", "一天三次，饭后服用。"),
            Wd("side effect", "副作用", "common side effects", "Drowsiness is a common side effect.", "嗜睡是常见副作用。"),
            Wd("sick note", "病假条", "ask for a sick note", "Could I get a sick note for three days?", "能给我开三天病假条吗？"),
        ),
        chunks = listOf(
            Ck("描述症状", "I've had a sore throat for four days.", "症状 + 持续时长，医生最想听这两点"),
            Ck("说明用药", "I've been taking paracetamol twice a day.", "现在完成进行时，表示还在吃"),
            Ck("问副作用", "Are there any side effects I should watch out for?", "watch out for 让问题更具体"),
            Ck("问注意事项", "Is it OK to drink coffee while I'm on this?", "be on medication = 正在服药"),
            Ck("要病假条", "Could I get a sick note for work?", "明确说 for work，医生才知道怎么写"),
        ),
        asks = listOf(
            Qs("Do you often go to the doctor?", "你经常看医生吗？", 1, "频率 + 原因（check-ups / minor issues）+ 态度"),
            Qs("Describe a time when you were ill.", "描述一次生病的经历。", 2, "什么时候 + 什么病 + 怎么处理 + 之后的改变（sleep, exercise）"),
            Qs("Should healthcare be free for everyone?", "医疗应该对所有人免费吗？", 3, "支持理由（equality）+ 现实问题（cost, waiting lists）+ 折中方案（free for basic care）"),
        ),
    )

    private val transit = Scene(
        id = "transit", zh = "交通出行", en = "Getting around", cat = Cat.LIFE,
        goal = "买单程/往返票、问换乘、打车报地址、赶时间改路线",
        lines = listOf(
            Ln(
                a = "Single or return?",
                aZh = "单程还是往返？",
                b = "A return to King's Cross, please — I'm coming back this evening.",
                bZh = "一张到国王十字的往返票，我今晚回来。",
                tip = "英国说 single / return，美国说 one-way / round trip；买了return当天回不来要说明",
            ),
            Ln(
                a = "You'll need to change at Oxford Circus. Platform three.",
                aZh = "你要在牛津广场换乘，三号站台。",
                b = "Do I need a separate ticket for the change, or does this cover it?",
                bZh = "换乘要另外买票吗，还是这张就够？",
                tip = "换乘是 change at + 站名，不说 transfer at（那是机场/酒店摆渡的用法）",
            ),
            Ln(
                a = "Where to?",
                aZh = "去哪儿？",
                b = "The Science Museum, please — could you drop me at the main entrance?",
                bZh = "科学博物馆，麻烦把我放在正门。",
                tip = "打车说 drop me at…，比 take me to… 更常见；报地点先说名字再说具体门",
            ),
            Ln(
                a = "About twenty minutes if the traffic's kind.",
                aZh = "路况好的话大概二十分钟。",
                b = "Any chance you could take a quieter route? I'm a bit pressed for time.",
                bZh = "能不能走条顺一点的路？我有点赶时间。",
                tip = "press for time = 赶时间；Any chance you could… 是最不容易得罪司机的请求方式",
            ),
        ),
        words = listOf(
            Wd("single", "单程票", "a single to…", "A single to Cambridge, please.", "一张到剑桥的单程票。"),
            Wd("return", "往返票", "a return to…", "He bought a return to Leeds.", "他买了张到利兹的往返票。"),
            Wd("platform", "站台", "platform three / on the platform", "The train leaves from platform six.", "车从六号站台发。"),
            Wd("fare", "车费 / 票价", "bus fare / fare increase", "The fare went up again in January.", "票价一月份又涨了。"),
            Wd("change at", "在……换乘", "change at Oxford Circus", "Change at Bank for the DLR.", "去轻轨在银行站换乘。"),
            Wd("top up", "充值（交通卡）", "top up my card", "I need to top up my travel card.", "我得给交通卡充点钱。"),
            Wd("drop off", "（把人/货）放下", "drop me at the entrance", "Just drop me at the corner.", "就把我放在路口吧。"),
            Wd("rush hour", "高峰期", "during rush hour", "Avoid the Tube during rush hour.", "高峰期别坐地铁。"),
            Wd("detour", "绕路", "take a detour", "We had to take a detour because of roadworks.", "因为施工我们只好绕路。"),
            Wd("get off", "下车", "get off at the next stop", "Get off at the next stop and walk back.", "下一站下车再走回来。"),
        ),
        chunks = listOf(
            Ck("买票", "A return to…, please.", "把目的地直接接在 to 后面，不用整句"),
            Ck("确认换乘", "Do I need to change?", "不确定线路时先问这句，比看地图快"),
            Ck("打车报地址", "Could you drop me at…?", "补一句 main entrance / the corner 更省事"),
            Ck("赶时间", "I'm a bit pressed for time.", "比 I'm busy 更具体，司机知道要认真走"),
            Ck("问末班车", "What time's the last train back?", "末班车一定问清楚，回程没车很麻烦"),
        ),
        asks = listOf(
            Qs("How do you usually get around your city?", "你平时在城市里怎么出行？", 1, "方式（metro / bike）+ 时间成本 + 为什么选它"),
            Qs("Describe a journey that didn't go to plan.", "描述一次不顺利的出行。", 2, "出发 + 出什么问题（delay, missed stop）+ 怎么解决 + 感受"),
            Qs("Should cities limit cars in the centre?", "城市应该限制市中心车辆吗？", 3, "支持（air quality, congestion）+ 替代（public transport）+ 但需照顾（disabled, deliveries）"),
        ),
    )

    private val shopping = Scene(
        id = "shopping", zh = "购物退换", en = "Shopping & returns", cat = Cat.LIFE,
        goal = "试穿、问尺码、退换货、问保修",
        lines = listOf(
            Ln(
                a = "Can I help you with anything?",
                aZh = "需要帮忙吗？",
                b = "I'm just browsing, thanks — but do you have this in a medium?",
                bZh = "我先看看，谢谢。不过这件有中号吗？",
                tip = "I'm just browsing 让店员不再跟着；后面用 but 接真需求，很自然",
            ),
            Ln(
                a = "The fitting rooms are at the back.",
                aZh = "试衣间在后面。",
                b = "Thanks. It's a bit tight across the shoulders — do you have the next size up?",
                bZh = "谢谢。肩这儿有点紧，有大一号的吗？",
                tip = "size up / size down 是最短的表达；紧说 tight，松说 loose，不说 small/big",
            ),
            Ln(
                a = "Do you have the receipt?",
                aZh = "有小票吗？",
                b = "Yes, here. I'd rather exchange it than get a refund, if that's possible.",
                bZh = "有，在这儿。如果可以的话，我更想换货而不是退款。",
                tip = "exchange 换货、refund 退款；I'd rather A than B 是雅思口语很吃香的句型",
            ),
            Ln(
                a = "That's within thirty days, so no problem.",
                aZh = "在三十天内，没问题。",
                b = "Great. And does the warranty cover accidental damage?",
                bZh = "太好了。保修包括意外损坏吗？",
                tip = "warranty 是保修，guarantee 更像质量保证；cover 表示「保不保这一项」",
            ),
        ),
        words = listOf(
            Wd("browse", "随便看看", "just browsing", "I'm just browsing, thanks.", "我先看看，谢谢。"),
            Wd("fitting room", "试衣间", "in the fitting room", "The fitting rooms are upstairs.", "试衣间在楼上。"),
            Wd("size up", "大一码", "go one size up", "If it's tight, size up.", "要是紧就大一码。"),
            Wd("try on", "试穿", "try it on", "Can I try these on?", "这些我能试穿吗？"),
            Wd("refund", "退款", "ask for a refund", "I'd like a full refund.", "我想全额退款。"),
            Wd("exchange", "换货", "exchange it for…", "Can I exchange it for a larger size?", "能换成大一号吗？"),
            Wd("receipt", "小票", "keep the receipt", "Keep your receipt in case you return it.", "留着小票以防退货。"),
            Wd("warranty", "保修", "under warranty / two-year warranty", "It's still under warranty.", "它还在保修期内。"),
            Wd("in stock", "有现货", "in stock / out of stock", "The blue one is out of stock.", "蓝色的没货了。"),
            Wd("non-refundable", "不可退", "non-refundable deposit", "Sale items are non-refundable.", "打折商品不可退。"),
        ),
        chunks = listOf(
            Ck("表示先自己看", "I'm just browsing, thanks.", "店员一问就说，省得一直被推荐"),
            Ck("问尺码", "Do you have this in a medium?", "in + 尺码/颜色，一句话换条件"),
            Ck("说合不合身", "It's a bit tight / loose around…", "around the waist / across the shoulders 精确到部位"),
            Ck("要求换而不是退", "I'd rather exchange it.", "比 I want to exchange 委婉，店员更愿意配合"),
            Ck("问保修范围", "Does the warranty cover…?", "cover 后面直接跟损坏类型"),
        ),
        asks = listOf(
            Qs("Do you enjoy shopping?", "你喜欢购物吗？", 1, "态度 + 线上还是线下 + 具体买什么"),
            Qs("Describe something you bought that you were pleased with.", "描述一件你买得很满意的商品。", 2, "是什么 + 在哪买的 + 为什么满意 + 用了多久"),
            Qs("Why do people buy things they don't need?", "人为什么会买不需要的东西？", 3, "营销（discounts, ads）+ 情绪（stress, reward）+ 社交压力 + 一句自己的做法"),
        ),
    )

    private val post = Scene(
        id = "post", zh = "邮局与快递", en = "Post office & parcels", cat = Cat.LIFE,
        goal = "寄包裹、选时效、要单号、问签收与清关",
        lines = listOf(
            Ln(
                a = "How would you like to send it — standard or express?",
                aZh = "怎么寄——普通还是加急？",
                b = "Express, please. It needs to arrive by Friday.",
                bZh = "加急。它最晚周五要到。",
                tip = "by Friday 是「不晚于周五」；说 on Friday 就变成「周五那天到」，意思不同",
            ),
            Ln(
                a = "Any liquids or batteries inside?",
                aZh = "里面有液体或电池吗？",
                b = "No, just documents and a small gift.",
                bZh = "没有，只有文件和一个小礼物。",
                tip = "海关/安检固定会问这句，如实说，瞒报会被扣件",
            ),
            Ln(
                a = "That comes to twelve pounds forty.",
                aZh = "一共十二镑四十。",
                b = "Could I get a tracking number for that?",
                bZh = "能给我一个查询单号吗？",
                tip = "tracking number 单号；问进度说 Can I track it?",
            ),
            Ln(
                a = "It should be there in two working days.",
                aZh = "大概两个工作日到。",
                b = "What happens if nobody's in to sign for it?",
                bZh = "如果没人在家签收怎么办？",
                tip = "sign for it = 签收；working days 不含周末，别按自然日算",
            ),
        ),
        words = listOf(
            Wd("parcel", "包裹", "send a parcel", "I'm sending a parcel to Shanghai.", "我要寄一个包裹到上海。"),
            Wd("express", "加急", "express delivery", "Express is twice the price.", "加急要贵一倍。"),
            Wd("standard delivery", "普通配送", "standard delivery", "Standard delivery takes a week.", "普通配送要一周。"),
            Wd("tracking number", "查询单号", "the tracking number", "What's the tracking number?", "单号是多少？"),
            Wd("sign for", "签收", "sign for the parcel", "Someone has to sign for it.", "得有人签收。"),
            Wd("customs", "海关", "go through customs", "It's stuck in customs.", "它卡在海关了。"),
            Wd("declare", "申报", "declare the value", "Do I need to declare the value?", "需要申报价值吗？"),
            Wd("working days", "工作日", "two working days", "Allow three working days.", "预留三个工作日。"),
            Wd("postcode", "邮编", "the postcode", "I'll need the postcode.", "我需要邮编。"),
            Wd("collect", "取件", "collect it from the depot", "You can collect it from the depot.", "你可以去站点取。"),
        ),
        chunks = listOf(
            Ck("说明时间要求", "It needs to arrive by…", "by + 时间点，把要求说死，邮局会给你对的时效"),
            Ck("要单号", "Could I get a tracking number?", "寄完立刻要，事后补要很麻烦"),
            Ck("问签收", "What if nobody's in?", "英式超常用缩写句，等于 What happens if…"),
            Ck("问清关", "Will it go through customs?", "跨境件必问，涉及时效和关税"),
            Ck("改地址", "Can I redirect it to another address?", "还没派送前改地址免费，派送后要收费"),
        ),
        asks = listOf(
            Qs("Do you often order things online?", "你经常网购吗？", 1, "频率 + 买什么 + 为什么（cheaper, wider choice）"),
            Qs("Describe a time when a delivery went wrong.", "描述一次配送出问题。", 2, "买了什么 + 出了什么问题 + 怎么解决 + 结果"),
            Qs("Will physical shops disappear?", "实体店会消失吗？", 3, "部分会（书、电子产品）+ 但体验类不会（clothes, food）+ 线上线下融合"),
        ),
    )

    private val complaint = Scene(
        id = "complaint", zh = "投诉与维权", en = "Making a complaint", cat = Cat.LIFE,
        goal = "陈述问题、要求退款或补偿、要求书面确认",
        lines = listOf(
            Ln(
                a = "How can I help?",
                aZh = "有什么可以帮您？",
                b = "I'd like to make a complaint about a delivery that never arrived.",
                bZh = "我要投诉一个一直没到的快递。",
                tip = "make a complaint about + 事，不要用 I want to complain you",
            ),
            Ln(
                a = "I'm sorry to hear that. Do you have the order number?",
                aZh = "很抱歉。您有订单号吗？",
                b = "Yes, it's in this email. I've been waiting three weeks now.",
                bZh = "有，在这封邮件里。我已经等了三周了。",
                tip = "把时间量说清楚（three weeks）比说 a long time 有力得多",
            ),
            Ln(
                a = "We can either resend it or refund you.",
                aZh = "我们可以重发或者退款。",
                b = "I'd rather have a refund, and I'd like the delivery fee back as well.",
                bZh = "我更想退款，而且运费也要退。",
                tip = "as well 加码要求，语气不冲但明确；I'd rather 比 I want 更稳",
            ),
            Ln(
                a = "I'll escalate it to a supervisor.",
                aZh = "我帮您转给主管处理。",
                b = "Thank you. Could you confirm that in writing?",
                bZh = "谢谢。能书面确认一下吗？",
                tip = "in writing 是要邮件/短信凭证，口头承诺事后常不认",
            ),
        ),
        words = listOf(
            Wd("complaint", "投诉", "make / file a complaint", "I want to make a complaint.", "我要投诉。"),
            Wd("refund", "退款", "a full refund", "I'm expecting a full refund.", "我要求全额退款。"),
            Wd("compensation", "赔偿", "ask for compensation", "They offered compensation for the delay.", "他们为延误提供了赔偿。"),
            Wd("escalate", "上报 / 升级处理", "escalate it to a manager", "Please escalate this.", "请把这事上报。"),
            Wd("order number", "订单号", "the order number", "Quote your order number.", "报一下你的订单号。"),
            Wd("keep me posted", "随时告知进展", "keep me posted", "Could you keep me posted?", "能随时告诉我进展吗？"),
            Wd("unacceptable", "不可接受", "simply unacceptable", "Three weeks is unacceptable.", "三周太离谱了。"),
            Wd("resolve", "解决", "resolve the issue", "I'd like this resolved today.", "我希望今天解决。"),
            Wd("in writing", "以书面形式", "confirm in writing", "Confirm it in writing, please.", "请书面确认。"),
            Wd("faulty", "有质量问题的", "a faulty item", "The item arrived faulty.", "东西到的时候是坏的。"),
        ),
        chunks = listOf(
            Ck("开口", "I'd like to make a complaint about…", "直接说事，不铺垫情绪，对方更容易进入流程"),
            Ck("把事实说死", "I've been waiting three weeks.", "用具体数字和时间，比形容词有效"),
            Ck("提要求", "I'd like a refund, and the delivery fee as well.", "要求分开列，别一口气说太杂"),
            Ck("要凭证", "Could you confirm that in writing?", "口头承诺必须落字，这是维权关键"),
            Ck("收尾", "If it's not sorted by Friday, I'll take it further.", "英式「升级处理」说法，不威胁但明确"),
        ),
        asks = listOf(
            Qs("Do you complain when service is bad?", "服务不好时你会投诉吗？", 1, "习惯 + 原因 + 举例"),
            Qs("Describe a time you had a problem with a product or service.", "描述一次产品或服务出问题。", 2, "什么问题 + 怎么沟通 + 结果 + 学到什么"),
            Qs("How should companies handle complaints?", "公司应该怎么处理投诉？", 3, "快速回应 + 补偿 + 改进流程 + 但也要防恶意投诉"),
        ),
    )
}

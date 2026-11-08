package com.eric.toolbox.modules.english

/**
 * 内置词书（V2.0）。专业词书由 Claude 按专利实务与生物医药常用术语编写：释义给“专业语境下的意思”，
 * 不照搬普通词典（普通词典里 prosecution=起诉、enablement=允许，专利里意思完全不同）。
 * 例句为编写的示范句，不是引自具体专利文件。
 */
data class Term(
    val key: String,       // 唯一键（=英文，小写）
    val en: String,
    val zh: String,        // 专业语境释义
    val ex: String,        // 例句
    val exZh: String,
    val note: String = "", // 易混 / 搭配
    val group: String = "",
)

object EnglishBooks {
    const val IP = "ip"
    const val DAILY = "daily"
    const val MINE = "mine"

    private fun t(en: String, zh: String, ex: String, exZh: String, note: String = "", group: String = "") =
        Term(en.lowercase(), en, zh, ex, exZh, note, group)

    // ======================= 生物医药知识产权 =======================
    val ip: List<Term> = listOf(
        // —— 专利程序 ——
        t("prosecution", "专利审查程序（申请人与专利局之间的往来，直至授权或驳回）", "We handle the prosecution of this application before the EPO.", "我们负责这件申请在欧洲专利局的审查程序。", "≠ litigation 诉讼；prosecution history 审查历史", "程序"),
        t("office action", "审查意见通知书", "We received an office action rejecting claims 1 to 5.", "我们收到一份驳回权利要求 1 至 5 的审查意见。", "美国叫 office action；中国叫审查意见通知书", "程序"),
        t("examiner", "审查员", "The examiner considered the amendment persuasive.", "审查员认为修改有说服力。", "", "程序"),
        t("rejection", "驳回（审查意见中对权利要求的否定）", "The rejection under 35 U.S.C. 103 was maintained.", "基于 35 U.S.C. 103 的驳回被维持。", "final rejection 最终驳回", "程序"),
        t("response", "（对审查意见的）答复", "The response is due within three months.", "答复期限为三个月。", "file a response 提交答复", "程序"),
        t("amendment", "修改（对权利要求或说明书）", "We propose an amendment to narrow claim 1.", "我们建议修改以缩小权利要求 1 的范围。", "", "程序"),
        t("allowance", "（美国）准予授权", "A notice of allowance was issued last week.", "上周下发了授权通知。", "notice of allowance 授权通知", "程序"),
        t("grant", "授权（专利被批准）", "The patent was granted in March.", "该专利于三月获得授权。", "", "程序"),
        t("priority date", "优先权日", "The prior art must be published before the priority date.", "现有技术必须在优先权日之前公开。", "", "程序"),
        t("priority claim", "优先权要求", "The priority claim to the Chinese application is valid.", "要求中国申请优先权是有效的。", "", "程序"),
        t("filing date", "申请日", "The filing date is 15 March 2024.", "申请日为 2024 年 3 月 15 日。", "", "程序"),
        t("PCT application", "PCT 国际申请", "We filed a PCT application within twelve months.", "我们在十二个月内提交了 PCT 国际申请。", "", "程序"),
        t("national phase", "（PCT）国家阶段", "The national phase entry deadline is 30 months from priority.", "进入国家阶段的期限是自优先权日起 30 个月。", "enter the national phase 进入国家阶段", "程序"),
        t("divisional application", "分案申请", "We may file a divisional application to pursue the method claims.", "我们可以提交分案申请来继续争取方法权利要求。", "", "程序"),
        t("continuation", "（美国）继续申请", "The client wants to keep a continuation pending.", "客户希望保留一件在审的继续申请。", "", "程序"),
        t("publication", "（申请）公开", "The application will be published 18 months after the priority date.", "申请将在优先权日起 18 个月后公开。", "", "程序"),
        t("restriction requirement", "（美国）限制性要求 / 分案要求", "The examiner issued a restriction requirement between the antibody and the method.", "审查员对抗体和方法发出了限制性要求。", "≈ 中国的单一性问题", "程序"),
        t("unity of invention", "发明单一性", "The claims lack unity of invention.", "这些权利要求不具备单一性。", "", "程序"),
        t("annuity", "年费", "The annuity is due at the end of the month.", "年费月底到期。", "也说 maintenance fee（美国）", "程序"),
        t("patent term extension", "专利期限延长（PTE）", "The product may qualify for patent term extension.", "该产品可能符合专利期限延长的条件。", "", "程序"),
        t("supplementary protection certificate", "（欧洲）补充保护证书（SPC）", "An SPC can extend protection for up to five years.", "SPC 最多可以将保护延长五年。", "", "程序"),
        t("opposition", "（欧洲）异议程序", "A third party filed an opposition against the European patent.", "第三方对该欧洲专利提出了异议。", "", "程序"),
        t("invalidation", "无效宣告", "The competitor requested invalidation of the Chinese patent.", "竞争对手请求宣告该中国专利无效。", "中国叫无效宣告；美国常见 IPR", "程序"),
        t("inter partes review", "（美国）双方复审（IPR）", "The petition for inter partes review was instituted.", "双方复审请求已被受理立案。", "", "程序"),
        t("appeal", "上诉 / 复审请求", "We recommend filing an appeal against the decision.", "我们建议对该决定提出上诉。", "", "程序"),

        // —— 权利要求 ——
        t("claim", "权利要求", "Claim 1 is directed to an antibody that binds CD19.", "权利要求 1 要求保护一种结合 CD19 的抗体。", "independent / dependent claim 独立/从属权利要求", "权利要求"),
        t("independent claim", "独立权利要求", "The independent claim defines the broadest scope.", "独立权利要求限定了最宽的保护范围。", "", "权利要求"),
        t("dependent claim", "从属权利要求", "Dependent claim 5 adds the dosing regimen.", "从属权利要求 5 增加了给药方案。", "", "权利要求"),
        t("claim scope", "权利要求保护范围", "The amendment significantly narrows the claim scope.", "这一修改显著缩小了保护范围。", "", "权利要求"),
        t("claim construction", "权利要求解释", "The case turned on the claim construction of \"binds specifically\".", "该案取决于对“特异性结合”的权利要求解释。", "", "权利要求"),
        t("comprising", "包含（开放式表述）", "A composition comprising A and B also covers A, B and C.", "“包含 A 和 B 的组合物”也涵盖 A、B 加 C。", "≠ consisting of 由……组成（封闭式）", "权利要求"),
        t("consisting of", "由……组成（封闭式表述）", "\"Consisting of\" excludes any unrecited ingredient.", "“由……组成”排除未记载的成分。", "", "权利要求"),
        t("Markush group", "马库什组", "The claim recites a Markush group of substituents.", "该权利要求记载了取代基的马库什组。", "", "权利要求"),
        t("genus", "上位概念 / 属", "The genus claim covers thousands of compounds.", "这个上位权利要求涵盖数千种化合物。", "↔ species 下位概念", "权利要求"),
        t("species", "下位概念 / 具体化合物", "Disclosure of a species can anticipate a genus claim.", "公开一个具体化合物可以破坏上位权利要求的新颖性。", "", "权利要求"),
        t("use claim", "用途权利要求", "Use claims are not allowed in the United States.", "美国不允许用途权利要求。", "", "权利要求"),
        t("Swiss-type claim", "瑞士型权利要求（“X 在制备治疗 Y 的药物中的用途”）", "In China, second medical use is claimed in Swiss-type format.", "在中国，第二医药用途以瑞士型权利要求撰写。", "", "权利要求"),
        t("method of treatment", "治疗方法（权利要求）", "Method of treatment claims are not patentable in China.", "治疗方法权利要求在中国不可授权。", "", "权利要求"),
        t("product-by-process", "方法限定的产品（权利要求）", "A product-by-process claim is judged by the product itself.", "方法限定产品权利要求以产品本身判断。", "", "权利要求"),
        t("dosage regimen", "给药方案", "The new dosage regimen may support a separate patent.", "新的给药方案可能支撑单独的专利。", "", "权利要求"),

        // —— 可专利性 ——
        t("novelty", "新颖性", "The claim lacks novelty over D1.", "该权利要求相对 D1 不具备新颖性。", "", "可专利性"),
        t("anticipation", "（美国）预见 / 破坏新颖性", "D1 is an anticipation of claim 1.", "D1 破坏了权利要求 1 的新颖性。", "anticipate 动词", "可专利性"),
        t("inventive step", "创造性（欧洲/中国说法）", "The unexpected effect supports inventive step.", "预料不到的效果支持创造性。", "美国叫 non-obviousness", "可专利性"),
        t("obviousness", "显而易见性（美国：缺乏创造性）", "The examiner raised an obviousness rejection over D1 in view of D2.", "审查员以 D1 结合 D2 显而易见为由驳回。", "in view of 结合（某对比文件）", "可专利性"),
        t("prior art", "现有技术", "We searched the prior art before the priority date.", "我们检索了优先权日之前的现有技术。", "", "可专利性"),
        t("reference", "对比文件", "Reference D2 discloses a similar linker.", "对比文件 D2 公开了类似的连接子。", "也说 citation / document", "可专利性"),
        t("closest prior art", "最接近的现有技术", "D1 is considered the closest prior art.", "D1 被认为是最接近的现有技术。", "问题-解决法的第一步", "可专利性"),
        t("problem-solution approach", "问题-解决法（欧洲判断创造性的方法）", "Under the problem-solution approach, we first identify the closest prior art.", "按问题-解决法，先确定最接近的现有技术。", "", "可专利性"),
        t("unexpected effect", "预料不到的技术效果", "The data show an unexpected effect on tumor regression.", "数据显示了对肿瘤消退的预料不到的效果。", "", "可专利性"),
        t("motivation to combine", "结合的动机（启示）", "There is no motivation to combine D1 with D2.", "没有将 D1 与 D2 结合的动机。", "", "可专利性"),
        t("reasonable expectation of success", "合理的成功预期", "A skilled person would not have had a reasonable expectation of success.", "本领域技术人员不会有合理的成功预期。", "", "可专利性"),
        t("person skilled in the art", "本领域技术人员", "The term would be clear to a person skilled in the art.", "该术语对本领域技术人员是清楚的。", "美国常说 POSITA", "可专利性"),
        t("enablement", "可实施性（说明书充分公开）", "The broad genus claim may fail the enablement requirement.", "宽泛的上位权利要求可能不满足可实施性要求。", "普通词典里是“允许/启动”，专利里不同", "可专利性"),
        t("written description", "（美国）书面描述要求", "The antibody claims lack written description support.", "抗体权利要求缺乏书面描述支持。", "", "可专利性"),
        t("sufficiency of disclosure", "充分公开", "The opposition argued insufficiency of disclosure.", "异议方主张公开不充分。", "", "可专利性"),
        t("support", "（权利要求）得到说明书支持", "The claim is not supported by the description.", "该权利要求得不到说明书的支持。", "", "可专利性"),
        t("added matter", "（欧洲）修改超范围", "The amendment introduces added matter.", "这一修改超出了原始公开范围。", "中国叫修改超范围", "可专利性"),
        t("clarity", "清楚（权利要求的清楚性要求）", "The examiner objected to the clarity of \"about\".", "审查员对“约”的清楚性提出异议。", "", "可专利性"),
        t("patent eligibility", "（美国）可专利主题", "Natural products may raise patent eligibility issues.", "天然产物可能涉及可专利主题问题。", "", "可专利性"),

        // —— 侵权与 FTO ——
        t("infringement", "侵权", "The biosimilar would likely infringe claim 3.", "该生物类似药很可能侵犯权利要求 3。", "infringe 动词", "侵权与FTO"),
        t("freedom to operate", "自由实施（FTO）", "We completed a freedom-to-operate analysis for the product.", "我们完成了该产品的自由实施分析。", "", "侵权与FTO"),
        t("literal infringement", "字面侵权", "There is no literal infringement because the linker differs.", "因为连接子不同，不构成字面侵权。", "", "侵权与FTO"),
        t("doctrine of equivalents", "等同原则", "Infringement may still be found under the doctrine of equivalents.", "按等同原则仍可能构成侵权。", "", "侵权与FTO"),
        t("design-around", "规避设计", "The client asked for design-around options.", "客户希望得到规避设计方案。", "", "侵权与FTO"),
        t("blocking patent", "障碍专利（阻碍上市的专利）", "Two blocking patents expire in 2031.", "两件障碍专利在 2031 年到期。", "", "侵权与FTO"),
        t("expiry", "（专利）到期", "The compound patent's expiry is the key date.", "化合物专利的到期日是关键日期。", "expire 动词", "侵权与FTO"),
        t("validity", "（专利）有效性", "We have serious doubts about the validity of claim 1.", "我们对权利要求 1 的有效性有严重疑虑。", "invalid 无效的", "侵权与FTO"),
        t("injunction", "禁令", "The patentee sought a preliminary injunction.", "专利权人申请了诉前禁令。", "", "侵权与FTO"),
        t("safe harbor", "（研发）安全港 / Bolar 例外", "Clinical trials fall within the safe harbor exemption.", "临床试验属于安全港豁免范围。", "", "侵权与FTO"),
        t("Orange Book", "（美国）橙皮书（小分子药专利登记）", "The patent is listed in the Orange Book.", "该专利登记在橙皮书中。", "生物药对应 Purple Book", "侵权与FTO"),
        t("Paragraph IV certification", "第 IV 段声明（仿制药挑战专利）", "The generic company filed a Paragraph IV certification.", "仿制药公司提交了第 IV 段声明。", "", "侵权与FTO"),
        t("patent linkage", "专利链接", "China introduced a patent linkage system in 2021.", "中国在 2021 年建立了专利链接制度。", "", "侵权与FTO"),

        // —— 交易与权属 ——
        t("assignee", "受让人 / 专利权人", "The assignee of record is the parent company.", "登记的专利权人是母公司。", "assignor 转让人", "权属交易"),
        t("inventor", "发明人", "Inventorship must be determined claim by claim.", "发明人身份须逐项权利要求确定。", "inventorship 发明人身份", "权属交易"),
        t("assignment", "（权利）转让", "The assignment was recorded at the USPTO.", "该转让已在美国专利商标局登记。", "", "权属交易"),
        t("license", "许可", "They granted an exclusive license in Greater China.", "他们授予了大中华区的独占许可。", "licensor 许可方 / licensee 被许可方", "权属交易"),
        t("due diligence", "尽职调查", "The investor requested IP due diligence before the deal.", "投资方要求在交易前做知识产权尽职调查。", "", "权属交易"),
        t("patent family", "专利族", "The patent family covers 28 jurisdictions.", "该专利族覆盖 28 个法域。", "", "权属交易"),
        t("patent landscape", "专利全景 / 专利布局分析", "We prepared a patent landscape of in vivo CAR-T.", "我们做了体内 CAR-T 的专利全景分析。", "", "权属交易"),
        t("jurisdiction", "法域 / 国家地区", "Protection is needed in key jurisdictions.", "需要在主要法域获得保护。", "", "权属交易"),
        t("patent portfolio", "专利组合", "Their patent portfolio is strong in linker chemistry.", "他们的专利组合在连接子化学方面很强。", "", "权属交易"),

        // —— 生物药 ——
        t("antibody", "抗体", "The antibody binds an epitope on HER2.", "该抗体结合 HER2 上的一个表位。", "", "生物药"),
        t("epitope", "表位", "Epitope claims are difficult to support.", "表位权利要求很难得到支持。", "", "生物药"),
        t("antigen", "抗原", "CD19 is the target antigen.", "CD19 是靶抗原。", "", "生物药"),
        t("binding affinity", "结合亲和力", "The variant shows ten-fold higher binding affinity.", "该变体的结合亲和力高十倍。", "Kd 解离常数", "生物药"),
        t("CDR", "互补决定区", "The claim defines the antibody by its six CDR sequences.", "该权利要求以六个 CDR 序列限定抗体。", "", "生物药"),
        t("heavy chain", "重链", "SEQ ID NO: 1 is the heavy chain variable region.", "SEQ ID NO: 1 是重链可变区。", "light chain 轻链", "生物药"),
        t("sequence identity", "序列同一性", "The claim covers variants with at least 90% sequence identity.", "该权利要求涵盖至少 90% 序列同一性的变体。", "", "生物药"),
        t("antibody-drug conjugate", "抗体偶联药物（ADC）", "The ADC uses a cleavable linker.", "该 ADC 使用可裂解连接子。", "", "生物药"),
        t("linker", "连接子", "The linker is stable in plasma.", "该连接子在血浆中稳定。", "", "生物药"),
        t("payload", "（ADC）载荷 / 毒素", "The payload is a topoisomerase I inhibitor.", "载荷是一种拓扑异构酶 I 抑制剂。", "", "生物药"),
        t("bispecific antibody", "双特异性抗体", "The bispecific antibody engages T cells and tumor cells.", "该双抗同时结合 T 细胞和肿瘤细胞。", "", "生物药"),
        t("chimeric antigen receptor", "嵌合抗原受体（CAR）", "The chimeric antigen receptor comprises a 4-1BB domain.", "该嵌合抗原受体包含 4-1BB 结构域。", "", "生物药"),
        t("in vivo", "体内", "In vivo CAR-T avoids ex vivo cell manufacturing.", "体内 CAR-T 无需体外细胞制备。", "↔ ex vivo 体外", "生物药"),
        t("lentiviral vector", "慢病毒载体", "T cells were transduced with a lentiviral vector.", "T 细胞用慢病毒载体转导。", "", "生物药"),
        t("lipid nanoparticle", "脂质纳米颗粒（LNP）", "The mRNA is delivered by lipid nanoparticles.", "mRNA 通过脂质纳米颗粒递送。", "", "生物药"),
        t("tumor-infiltrating lymphocytes", "肿瘤浸润淋巴细胞（TIL）", "TIL therapy expands lymphocytes from the patient's tumor.", "TIL 疗法扩增来自患者肿瘤的淋巴细胞。", "", "生物药"),
        t("radioligand", "放射性配体", "The radioligand targets PSMA.", "该放射性配体靶向 PSMA。", "RDC 放射性核素偶联药物", "生物药"),
        t("biosimilar", "生物类似药", "The biosimilar launch depends on patent expiry.", "生物类似药的上市取决于专利到期。", "", "生物药"),
        t("formulation", "制剂 / 配方", "A new formulation may extend the product's lifecycle.", "新制剂可能延长产品生命周期。", "", "生物药"),
        t("excipient", "辅料", "The excipient improves oral absorption.", "这种辅料改善口服吸收。", "", "生物药"),
        t("permeation enhancer", "吸收促进剂", "SNAC acts as a permeation enhancer for oral semaglutide.", "SNAC 是口服司美格鲁肽的吸收促进剂。", "", "生物药"),
        t("peptide", "多肽", "The peptide is a GLP-1 and GIP dual agonist.", "该多肽是 GLP-1/GIP 双激动剂。", "", "生物药"),
        t("agonist", "激动剂", "A dual agonist activates both receptors.", "双激动剂同时激活两个受体。", "antagonist 拮抗剂", "生物药"),
        t("half-life", "半衰期", "Fc fusion extends the half-life.", "Fc 融合延长了半衰期。", "", "生物药"),
        t("pharmacokinetics", "药代动力学（PK）", "The pharmacokinetics support weekly dosing.", "药代动力学数据支持每周给药。", "", "生物药"),
        t("crystalline form", "晶型", "A new crystalline form can be separately patented.", "新晶型可以单独申请专利。", "polymorph 多晶型", "生物药"),

        // —— 临床 ——
        t("efficacy", "疗效（受控试验中的效果）", "The trial evaluated the efficacy and safety of the drug.", "该试验评估了药物的有效性和安全性。", "≠ effectiveness（真实世界有效性）", "临床"),
        t("primary endpoint", "主要终点", "The primary endpoint is progression-free survival.", "主要终点是无进展生存期。", "secondary endpoint 次要终点", "临床"),
        t("progression-free survival", "无进展生存期（PFS）", "Median progression-free survival was 11 months.", "中位无进展生存期为 11 个月。", "overall survival 总生存期", "临床"),
        t("overall response rate", "客观缓解率（ORR）", "The overall response rate reached 62%.", "客观缓解率达到 62%。", "", "临床"),
        t("adverse event", "不良事件", "No new adverse events were observed.", "未观察到新的不良事件。", "", "临床"),
        t("enrollment", "入组", "Enrollment is expected to complete next year.", "入组预计明年完成。", "", "临床"),
        t("inclusion criteria", "入选标准", "The inclusion criteria require prior chemotherapy.", "入选标准要求既往接受过化疗。", "exclusion criteria 排除标准", "临床"),
        t("placebo", "安慰剂", "Patients were randomized to drug or placebo.", "患者被随机分到药物组或安慰剂组。", "", "临床"),
        t("randomized", "随机的", "It is a randomized, double-blind study.", "这是一项随机双盲研究。", "", "临床"),
        t("sponsor", "申办方", "The sponsor terminated the trial early.", "申办方提前终止了试验。", "", "临床"),
        t("indication", "适应症", "The drug is approved for a second indication.", "该药获批第二个适应症。", "", "临床"),
        t("relapsed or refractory", "复发或难治", "The study enrolls relapsed or refractory patients.", "该研究入组复发或难治患者。", "", "临床"),
        t("marketing authorization", "上市许可", "The company obtained marketing authorization in the EU.", "该公司在欧盟获得了上市许可。", "", "临床"),
        t("regulatory exclusivity", "（药品）监管独占期 / 数据保护", "Regulatory exclusivity runs independently of patents.", "监管独占期与专利相互独立。", "", "临床"),
    )

    // ======================= 日常口语高频表达 =======================
    val daily: List<Term> = listOf(
        // 寒暄与介绍
        t("Nice to meet you.", "很高兴认识你。", "Hi, I'm Eric. Nice to meet you.", "你好，我是 Eric，很高兴认识你。", "", "寒暄"),
        t("How have you been?", "最近怎么样？（久未见面）", "Long time no see! How have you been?", "好久不见！最近怎么样？", "", "寒暄"),
        t("I work in IP consulting.", "我做知识产权咨询。", "I work in IP consulting, mainly in biopharma.", "我做知识产权咨询，主要是生物医药方向。", "", "寒暄"),
        t("What do you do?", "你是做什么工作的？", "So, what do you do?", "那你是做什么工作的？", "比 What's your job? 自然", "寒暄"),
        t("Let's keep in touch.", "保持联系。", "It was great talking to you. Let's keep in touch.", "和你聊得很开心，保持联系。", "", "寒暄"),
        t("Thanks for having me.", "谢谢邀请 / 招待。", "Thanks for having me today.", "谢谢今天邀请我。", "", "寒暄"),
        // 会议与电话
        t("Can you hear me okay?", "能听清我说话吗？（线上会议）", "Before we start, can you hear me okay?", "开始前确认一下，能听清我说话吗？", "", "会议"),
        t("Sorry, you're breaking up.", "抱歉，你那边声音断断续续。", "Sorry, you're breaking up. Could you say that again?", "抱歉，你声音断了，能再说一遍吗？", "", "会议"),
        t("Could you repeat that?", "能再说一遍吗？", "Sorry, could you repeat that last point?", "抱歉，最后那点能再说一遍吗？", "", "会议"),
        t("Let me share my screen.", "我来共享一下屏幕。", "Let me share my screen so we can look at claim 1.", "我共享一下屏幕，我们一起看权利要求 1。", "", "会议"),
        t("Just to clarify,", "我确认一下……", "Just to clarify, are we filing in Europe as well?", "确认一下，我们也要在欧洲提交吗？", "", "会议"),
        t("That makes sense.", "有道理 / 明白了。", "That makes sense. Let's go with option B.", "有道理，那就用方案 B。", "", "会议"),
        t("I'd suggest that…", "我建议……", "I'd suggest that we amend claim 1 first.", "我建议先修改权利要求 1。", "比 You should 委婉", "会议"),
        t("Let me get back to you on that.", "这个我稍后回复你。", "Good question. Let me get back to you on that.", "好问题，这个我稍后回复你。", "", "会议"),
        t("Could you send me the details by email?", "能把细节邮件发我吗？", "Could you send me the details by email after the call?", "会后能把细节邮件发我吗？", "", "会议"),
        t("We're running out of time.", "时间快到了。", "We're running out of time, so let's wrap up.", "时间快到了，我们收个尾。", "wrap up 收尾", "会议"),
        t("What's the deadline?", "截止日期是什么时候？", "What's the deadline for the response?", "答复的截止日期是哪天？", "", "会议"),
        t("I'm on the same page.", "我和你想法一致。", "Great, I think we're on the same page.", "好，我觉得我们想法一致。", "", "会议"),
        // 出行
        t("I have a reservation under Eric.", "我用 Eric 的名字订了房 / 位。", "Hi, I have a reservation under Eric for two nights.", "你好，我用 Eric 的名字订了两晚。", "", "出行"),
        t("What time is check-out?", "几点退房？", "What time is check-out tomorrow?", "明天几点退房？", "", "出行"),
        t("Is breakfast included?", "含早餐吗？", "Is breakfast included in the rate?", "房价含早餐吗？", "", "出行"),
        t("Could you call a taxi for me?", "能帮我叫辆出租车吗？", "Could you call a taxi for me to the airport?", "能帮我叫辆去机场的出租车吗？", "", "出行"),
        t("How do I get to…?", "……怎么走？", "How do I get to the convention center?", "会展中心怎么走？", "", "出行"),
        t("Is it within walking distance?", "走路能到吗？", "Is the office within walking distance?", "办公室走路能到吗？", "", "出行"),
        t("My flight was delayed.", "我的航班延误了。", "Sorry I'm late. My flight was delayed.", "抱歉来晚了，航班延误了。", "", "出行"),
        // 餐厅
        t("A table for two, please.", "两位，谢谢。", "Hi, a table for two, please.", "你好，两位。", "", "餐厅"),
        t("What do you recommend?", "你推荐什么？", "It's my first time here. What do you recommend?", "第一次来，你推荐什么？", "", "餐厅"),
        t("I'll have the…", "我要……", "I'll have the salmon, please.", "我要三文鱼。", "比 I want 礼貌", "餐厅"),
        t("Could we get the check, please?", "麻烦结账。", "Could we get the check, please?", "麻烦结一下账。", "英式说 the bill", "餐厅"),
        t("It's on me.", "我请客。", "Put your wallet away. It's on me.", "把钱包收起来，我请。", "", "餐厅"),
        // 日常
        t("No worries.", "没关系 / 不客气。", "No worries, take your time.", "没关系，你慢慢来。", "", "日常"),
        t("I'm afraid…", "恐怕……（委婉说坏消息）", "I'm afraid I can't make it on Friday.", "恐怕我周五去不了。", "", "日常"),
        t("Would you mind…?", "你介意……吗？", "Would you mind closing the window?", "你介意关一下窗吗？", "回答“不介意”说 Not at all", "日常"),
        t("It depends.", "看情况。", "It depends on the examiner's response.", "要看审查员的答复。", "", "日常"),
        t("I'm not sure, but…", "我不太确定，但……", "I'm not sure, but I think the deadline is next week.", "我不太确定，但截止日好像是下周。", "", "日常"),
        t("Sounds good.", "听起来不错 / 好的。", "Lunch at noon? Sounds good.", "中午吃饭？好的。", "", "日常"),
        t("Take care.", "保重 / 再见。", "See you next week. Take care!", "下周见，保重！", "", "日常"),
    )

    fun book(id: String): List<Term> = when (id) { IP -> ip; DAILY -> daily; else -> emptyList() }

    fun findIp(word: String): Term? = ip.firstOrNull { it.key == word.trim().lowercase() }
}

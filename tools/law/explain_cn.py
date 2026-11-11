# 中国专利法重点条文的延伸解读（Claude 撰写；依据为条文本身及下列官方文件，写明出处）
# 每条：[(中文, English), ...]

EXPLAIN_CN = {
2: [
    ("“发明”是对产品、方法或者其改进所提出的新的技术方案。注意它和美国 35 U.S.C. §100(a) 的写法不同：美国把“发现”（discovery）也写进了发明的定义，中国则在第二十五条把“科学发现”明确排除。",
     "An “invention” is a new technical solution for a product, a process or an improvement. Contrast 35 U.S.C. §100(a), which includes “discovery” in the definition; China expressly excludes “scientific discoveries” in Article 25."),
],
9: [
    ("两条规则：禁止重复授权（同样的发明创造只授一项专利），以及先申请原则。第一款但书是中国特有的“发明+实用新型同日申请”策略的法律基础：先拿实用新型快速获权，发明授权时声明放弃实用新型。",
     "Two rules: no double patenting for the same invention-creation, and first-to-file. The proviso in paragraph 1 underpins the Chinese same-day utility-model-plus-invention filing strategy: obtain the utility model quickly, then surrender it when the invention patent is granted."),
],
11: [
    ("专利权的核心是“排他权”：未经许可不得制造、使用、许诺销售、销售、进口。方法专利延伸保护到“依照该专利方法直接获得的产品”。对比美国 §271(a) 和 §271(g)：美国对方法所得产品的保护规定在单独一款里。",
     "The patent right is a right to exclude: no making, using, offering to sell, selling or importing without permission. Process patents extend to “the product directly obtained by the patented process”. Compare 35 U.S.C. §271(a) and the separate product-by-process provision in §271(g)."),
],
22: [
    ("授权三要件：新颖性、创造性、实用性。中国的“现有技术”是申请日以前国内外为公众所知的技术，采用绝对新颖性。",
     "Three requirements for grant: novelty, inventiveness and practical use. “Prior art” is technology known to the public at home or abroad before the filing date (absolute novelty)."),
    ("创造性的官方英译是 inventiveness；美国对应的是 §103 的 non-obviousness（非显而易见性），欧洲叫 inventive step。中国审查实践用“三步法”（确定最接近的现有技术、确定区别特征和实际解决的技术问题、判断是否显而易见），思路接近欧洲的问题—解决法，和美国 Graham / KSR 的框架不一样。",
     "The official translation is “inventiveness”; the US counterpart is non-obviousness under §103, and Europe says “inventive step”. Chinese examination uses the three-step test (closest prior art, distinguishing features and the technical problem actually solved, obviousness), close to the European problem-solution approach and different from the US Graham/KSR framework."),
    ("医药案件里，“预料不到的技术效果”和申请日后补交的实验数据能否被采信，往往决定创造性的结论。",
     "In pharmaceutical cases, unexpected technical effects and whether post-filing experimental data are accepted often decide inventiveness."),
],
24: [
    ("中国的不丧失新颖性宽限期只有六个月，而且只限四种法定情形。第（一）项“紧急状态下为公共利益首次公开”是 2020 年修改新增的。",
     "China’s novelty grace period is six months and covers only four statutory situations; item (1), first disclosure for public interest in a state emergency, was added in the 2020 amendment."),
    ("美国 §102(b)(1) 是一年，并且发明人自己的任何公开都在宽限范围内。所以“先发论文再申请”在美国可能还来得及，在中国基本会丧失新颖性。这是给研发型客户做专利布局时最常提醒的一点。",
     "The US grace period under §102(b)(1) is one year and covers any disclosure by the inventor. Publishing first may still be safe in the US but will usually destroy novelty in China, which is one of the most common warnings to R&D clients."),
],
25: [
    ("第（三）项“疾病的诊断和治疗方法”不授予专利权，所以医药用途在中国要写成“制药用途”权利要求：“化合物 X 在制备治疗疾病 Y 的药物中的应用”。美国没有这项排除，可以直接写治疗方法权利要求（method of treating）。",
     "Item (3) excludes methods for diagnosing or treating diseases, so medical uses in China are claimed in Swiss-type form: “use of compound X in the manufacture of a medicament for treating disease Y”. The US has no such exclusion and allows method-of-treatment claims."),
    ("第（四）项排除动植物品种，但最后一款规定其生产方法可以获得专利。",
     "Item (4) excludes animal and plant varieties, but the last paragraph allows patents on processes for producing them."),
],
26: [
    ("第三款“清楚、完整，以所属技术领域的技术人员能够实现为准”对应美国 §112(a) 的可实施性（enablement）要求；第四款“权利要求书应当以说明书为依据”在功能上接近美国的书面描述（written description）要求，也接近欧洲 EPC 第 84 条的“得到说明书支持”。",
     "Paragraph 3 (clear and complete, enabling the skilled person) corresponds to US enablement under §112(a). Paragraph 4 (claims supported by the description) functions much like US written description and EPC Art. 84 support."),
    ("抗体、细胞治疗类申请用功能限定的大范围权利要求时，这两款最容易被挑战。可以对照美国 Amgen v. Sanofi（2023）的判决一起看。",
     "Broad functionally defined antibody and cell-therapy claims are most exposed under these two paragraphs; read together with Amgen v. Sanofi (2023)."),
    ("第五款要求披露遗传资源的直接来源和原始来源，美国没有对应规定。",
     "Paragraph 5 requires disclosure of the direct and original source of genetic resources, which has no US counterpart."),
],
29: [
    ("优先权期限：发明和实用新型十二个月，外观设计六个月。2020 年修改把本国优先权扩展到外观设计。",
     "Priority periods: twelve months for inventions and utility models, six months for designs; the 2020 amendment extended domestic priority to designs."),
],
33: [
    ("修改不得超出原说明书和权利要求书记载的范围。中国审查对此把握较严，判断标准是“能否直接、毫无疑义地确定”。美国对应的是不得引入新内容（new matter，35 U.S.C. §132）。",
     "Amendments may not go beyond the original description and claims. Chinese practice is strict, applying a “directly and unambiguously derivable” standard; the US counterpart is the new-matter bar (35 U.S.C. §132)."),
],
35: [
    ("发明专利申请要在申请日起三年内提出实质审查请求，否则视为撤回。",
     "Substantive examination of an invention application must be requested within three years from filing, or the application is deemed withdrawn."),
],
42: [
    ("第二款是专利权期限补偿（授权过程中的不合理延迟），对应美国 §154(b) 的专利期调整（PTA）。第三款是新药相关专利的期限补偿，对应美国 §156 的专利期延长（PTE）。",
     "Paragraph 2 compensates unreasonable delay in examination (cf. US patent term adjustment under §154(b)); paragraph 3 compensates regulatory review time for new drugs (cf. US patent term extension under §156)."),
    ("两国的上限数字一样：补偿或延长不超过五年，新药上市后的总有效期不超过十四年（对照 35 U.S.C. §156(c)(3) 和 §156(g)(6)(A)）。但计算方法和适用的药品范围不同，具体以专利法实施细则（2023 年修订）和审查指南为准。",
     "Both countries cap the extension at five years and the post-approval effective term at fourteen years (cf. 35 U.S.C. §156(c)(3) and §156(g)(6)(A)), but the calculation and eligible drugs differ; see the 2023 Implementing Regulations and the Examination Guidelines."),
],
45: [
    ("任何单位或者个人都可以请求宣告专利权无效，不受期限限制。负责审理的是国家知识产权局的复审和无效审理部。",
     "Anyone may request invalidation at any time after grant; requests are handled by CNIPA’s Reexamination and Invalidation Department."),
    ("美国没有一个完全对应的程序。PTAB 的 IPR 只能以第 102 条、第 103 条为理由，只能用专利文献和出版物作证据，还有提出时间的限制；PGR 只能在授权后九个月内提出。",
     "There is no exact US equivalent: PTAB inter partes review is limited to §102/§103 grounds based on patents and printed publications and has timing bars, and post-grant review must be filed within nine months of grant."),
],
46: [
    ("对无效决定不服的，三个月内向人民法院起诉，一审由北京知识产权法院管辖。这类案件是行政诉讼，国家知识产权局是被告，无效程序中的对方当事人作为第三人参加诉讼。",
     "A party dissatisfied with an invalidation decision may sue within three months; the Beijing IP Court hears these administrative cases at first instance, with CNIPA as defendant and the other party joining as a third party."),
    ("二审：2023 年 11 月 1 日起，发明专利的二审仍由最高人民法院知识产权法庭审理（《最高人民法院关于知识产权法庭若干问题的规定》2023 年修正）。",
     "Appeal: since 1 November 2023, second-instance invention-patent cases are still heard by the SPC IP Tribunal (SPC Provisions on the IP Tribunal, as amended in 2023)."),
],
47: [
    ("无效的专利权视为自始不存在，但已经执行的侵权判决、已经履行的许可合同等原则上不追溯，这是为了维护交易安全。专利权人有恶意的，应当赔偿。",
     "An invalidated patent is deemed void from the outset, but enforced judgments and performed licences are generally not unwound, for transactional certainty; a patentee acting in bad faith must compensate."),
],
64: [
    ("保护范围以权利要求的内容为准，说明书和附图用来解释权利要求。侵权判断要比对全部技术特征（全面覆盖原则），具体规则见《最高人民法院关于审理侵犯专利权纠纷案件应用法律若干问题的解释》。",
     "Scope is defined by the claims, interpreted with the description and drawings; infringement requires all claim features (all-elements rule), as elaborated in the SPC Interpretation on patent infringement disputes."),
],
66: [
    ("新产品制造方法专利实行举证责任倒置：由被诉侵权人证明自己的方法不同于专利方法。指导案例 84 号（礼来诉华生）就是在这一规则下审理的，被告最终完成了举证。",
     "For process patents on new products the burden shifts: the accused infringer must prove its process differs. Guiding Case No. 84 (Eli Lilly v. Huasheng) applied this rule, and the defendant discharged that burden."),
],
67: [
    ("现有技术抗辩：被控侵权人证明自己实施的是现有技术，就不构成侵权，不需要先把专利无效掉。美国侵权诉讼中没有直接对应的抗辩，被告通常是主张专利无效（§282(b)）。",
     "Prior-art defence: proof that the accused technology is prior art defeats infringement without first invalidating the patent; US defendants typically plead invalidity instead (§282(b))."),
],
71: [
    ("赔偿顺序：实际损失或侵权获利，然后参照许可使用费的倍数，最后是法定赔偿（三万元以上五百万元以下）。故意侵权、情节严重的，可以判一到五倍的惩罚性赔偿，这是 2020 年修改新增的。美国 §284 是最高三倍的增加赔偿。",
     "Damages run from actual loss or infringer’s profit, to a multiple of a reasonable royalty, to statutory damages of RMB 30,000–5,000,000. Wilful and serious infringement may attract one to five times punitive damages (added in 2020); US §284 allows enhancement up to three times."),
],
74: [
    ("侵权诉讼时效三年，从知道或者应当知道侵权行为和侵权人之日起算。美国 §286 不是诉讼时效，而是只能追溯起诉前六年内的赔偿。",
     "Three-year limitation from when the patentee knew or should have known of the infringement and infringer. US §286 instead bars recovery for infringement more than six years before suit."),
],
75: [
    ("第（五）项就是中国的 Bolar 例外：为提供行政审批所需的信息，制造、使用、进口专利药品或专利医疗器械，以及专门为其制造、进口的，不视为侵权。对应美国 §271(e)(1)。",
     "Item (5) is China’s Bolar exemption: making, using or importing patented drugs or medical devices to provide information for regulatory approval, and making or importing them exclusively for such a party, is not infringement; cf. US §271(e)(1)."),
    ("例外的边界：最高法院在“利伐沙班”案（(2021)最高法知行终451号）中认定，通过网站、展会许诺销售不在这一例外范围内。",
     "Limits: in the rivaroxaban cases ((2021) Zui Gao Fa Zhi Xing Zhong No. 451) the SPC held that offering to sell through websites and trade fairs falls outside this exemption."),
],
76: [
    ("药品专利链接制度（2020 年修改新增）。仿制药申请人和专利权人可以向法院起诉，请求确认申请注册的药品技术方案是否落入专利保护范围；也可以向国家知识产权局请求行政裁决。",
     "Drug patent linkage (added in 2020): the generic applicant and the patentee may ask a court to determine whether the generic’s technical solution falls within the patent, or request administrative adjudication from CNIPA."),
    ("配套规定《药品专利纠纷早期解决机制实施办法（试行）》（国家药监局、国家知识产权局 2021 年第 89 号公告）：专利权人收到四类声明后，须在药品审评机构公开上市申请之日起 45 日内起诉或请求裁决；药监部门对化学仿制药设置 9 个月等待期。",
     "Under the Implementing Measures (NMPA/CNIPA Announcement No. 89 of 2021), the patentee must sue or seek adjudication within 45 days after the drug evaluation centre publishes the application following a Category 4 certification, and a nine-month stay applies to chemical generics."),
    ("对比美国 Hatch-Waxman：起诉期都是 45 日，但起算点不同（美国从专利权人收到第四段声明通知之日起算），美国的等待期是 30 个月（21 U.S.C. §355(j)(5)(B)(iii)），而且提交 ANDA 本身就被拟制为侵权（35 U.S.C. §271(e)(2)）。中国的链接诉讼是“确认是否落入保护范围”之诉，不是侵权之诉。",
     "Compared with Hatch-Waxman: also 45 days, but counted from receipt of the Paragraph IV notice, and a 30-month stay in the US (21 U.S.C. §355(j)(5)(B)(iii)), and filing an ANDA is itself an artificial act of infringement (35 U.S.C. §271(e)(2)); the Chinese linkage action seeks a declaration on scope rather than infringement relief."),
],
}

# 美国专利法重点条文的延伸解读（Claude 撰写）

EXPLAIN_US = {
"101": [
    ("条文本身很宽：方法、机器、制品、物质组合物都可以申请专利。限制来自判例确立的三项司法例外：自然规律、自然现象、抽象概念。医药领域最相关的判例是 Mayo（诊断方法）和 Myriad（分离的天然 DNA）。",
     "The text is broad; the limits come from three judicial exceptions: laws of nature, natural phenomena and abstract ideas. Mayo (diagnostics) and Myriad (isolated DNA) are the key pharma cases."),
    ("和中国第二十五条比较：美国不排除治疗方法，但诊断方法常因“自然规律”被否定；中国直接排除“疾病的诊断和治疗方法”。",
     "Compare China Art. 25: the US does not exclude treatment methods, but diagnostic methods often fail as laws of nature; China expressly excludes diagnosis and treatment methods."),
],
"102": [
    ("AIA（2011 年《美国发明法案》）之后，美国改为“发明人先申请制”。判断时点是“有效申请日”（§100(i)），现有技术不分国内外。",
     "After the AIA (2011) the US is first-inventor-to-file; the reference date is the effective filing date (§100(i)) and prior art is worldwide."),
    ("§102(b)(1) 的一年宽限期覆盖发明人自己的任何公开；中国只有六个月，而且只限四种法定情形（专利法第二十四条）。",
     "The one-year grace period in §102(b)(1) covers any disclosure by the inventor; China allows six months for four statutory situations only (Art. 24)."),
],
"103": [
    ("非显而易见性按 Graham 案确立的因素判断：现有技术的范围和内容、与权利要求的区别、本领域普通技术水平，以及商业成功、长期需求等“客观指标”（又称次要考虑因素）。KSR（2007）强调判断应当灵活，不能僵化地要求现有技术中有明确的“教导—启示—动机”（TSM）。",
     "Obviousness follows the Graham factors (scope and content of the prior art, differences, level of ordinary skill, and objective indicia such as commercial success). KSR (2007) requires a flexible inquiry rather than a rigid teaching-suggestion-motivation test."),
    ("化合物案件常用“先导化合物”分析（Takeda v. Alphapharm, 2007）：本领域技术人员是否会选择某个现有化合物作为改进起点，又是否有理由作出特定修饰。",
     "Compound cases often use lead-compound analysis (Takeda v. Alphapharm, 2007): would a skilled person select a prior-art compound as a starting point and have reason to make the specific modification?"),
],
"112": [
    ("§112(a) 里有三项相互独立的要求：书面描述（written description）、可实施性（enablement）、最佳方式（best mode）。Ariad（2010，联邦巡回法院全院庭审）确认书面描述是独立于可实施性的要求。",
     "§112(a) contains three separate requirements: written description, enablement and best mode. Ariad (en banc, 2010) confirmed that written description is distinct from enablement."),
    ("最佳方式仍是申请时的义务，但根据 §282(b)(3)(A)，未披露最佳方式已不能作为撤销专利或者认定专利无效的理由。",
     "Best mode remains a filing requirement, but under §282(b)(3)(A) failure to disclose it is no longer a ground to cancel or invalidate a claim."),
    ("Amgen v. Sanofi（2023）：权利要求要求保护一整类（例如以功能限定的抗体）时，说明书必须使技术人员能够制造和使用这一整类，不能让人“反复试错”。",
     "Amgen v. Sanofi (2023): when a claim covers an entire class (e.g. functionally defined antibodies), the specification must enable the full class, not leave the skilled person to trial and error."),
],
"154": [
    ("专利期限自申请日起二十年；国外优先权不计入期限起算（§154(a)(3)）。§154(b) 规定专利期调整（PTA），用来补偿 USPTO 造成的审查延误。",
     "Term is twenty years from filing; foreign priority does not count (§154(a)(3)). §154(b) provides patent term adjustment (PTA) for USPTO delays."),
],
"156": [
    ("专利期延长（PTE）补偿药品监管审查占用的时间：试验阶段计一半，审批阶段全额计入，延长不超过五年，药品获批后的剩余专利期加延长期不超过十四年，同一监管审查期只能延长一项专利。这里只收录了第 156 条的节选，完整条文见原文链接。",
     "PTE compensates regulatory review: half of the testing phase plus the full approval phase, capped at five years and at fourteen years of remaining term after approval, one patent per regulatory review period. Only excerpts are included here."),
],
"271": [
    ("(a) 直接侵权；(b) 诱导侵权；(c) 帮助侵权。医药领域最常见的是诱导侵权，例如仿制药说明书指导医生按专利用途用药。",
     "(a) direct, (b) induced and (c) contributory infringement. Inducement is common in pharma, e.g. generic labels instructing a patented use."),
    ("(e)(1) 是 Bolar 例外（研究和申报豁免），对应中国专利法第七十五条第（五）项；(e)(2) 把提交 ANDA 或 505(b)(2) 申请拟制为侵权行为，这样诉讼可以在仿制药上市之前进行；(e)(2)(C) 和 (e)(6) 是 BPCIA 下生物类似药“专利舞蹈”的配套规定。",
     "(e)(1) is the Bolar safe harbor (cf. China Art. 75(5)); (e)(2) makes filing an ANDA or 505(b)(2) application an artificial act of infringement so litigation can happen before launch; (e)(2)(C) and (e)(6) implement the BPCIA patent dance for biosimilars."),
],
"282": [
    ("专利推定有效。最高法院在 Microsoft v. i4i（2011）中认定，在法院主张无效须达到“清楚且有说服力的证据”标准；而在 PTAB 的 IPR/PGR 中，请求人只需达到“优势证据”标准（§316(e)、§326(e)）。这是很多被告选择去 PTAB 的原因。",
     "Patents are presumed valid. In court, invalidity must be proved by clear and convincing evidence (Microsoft v. i4i, 2011), while in IPR/PGR the petitioner needs only a preponderance (§316(e), §326(e)), one reason defendants turn to the PTAB."),
],
"284": [
    ("补偿性赔偿不得低于合理许可费；法院可以把赔偿额提高到三倍。何时提高赔偿以 Halo v. Pulse（2016）为准，主要看是否属于恶意、故意的侵权。中国是一到五倍惩罚性赔偿（专利法第七十一条）。",
     "Damages are at least a reasonable royalty; courts may enhance up to three times, under Halo v. Pulse (2016) for egregious wilful conduct. China allows one to five times punitive damages (Art. 71)."),
],
"311": [
    ("IPR：只能以第 102 条、第 103 条为理由，证据只能是专利和印刷出版物。可以挑战可实施性、书面描述，或者使用销售、使用类证据的，只有 PGR（授权后九个月内）和法院诉讼。",
     "IPR is limited to §102/§103 grounds on patents and printed publications. Enablement, written description or on-sale/public-use evidence require PGR (within nine months of grant) or litigation."),
],
"314": [
    ("立案门槛是“合理可能胜诉”；立案决定不可上诉（§314(d)）。自 2025 年 10 月 20 日起，USPTO 局长在咨询至少三名 PTAB 法官后亲自决定 IPR/PGR 是否立案，多数决定以只写结果、不写理由的简要通知形式发出（资料来源：USPTO 2025 年 10 月 17 日公布的局长备忘录，经多家律所报道）。",
     "The institution threshold is a reasonable likelihood of prevailing and the decision is nonappealable (§314(d)). Since 20 October 2025 the USPTO Director, consulting at least three PTAB judges, decides institution personally, mostly by summary notices without reasons (per the Director’s memorandum of 17 October 2025 as reported by several law firms)."),
],
"315": [
    ("最重要的两个限制：(b) 被送达侵权起诉状后超过一年，不能再提 IPR；(e) 作出最终书面决定后，请求人对“已经提出或者合理地本可以提出”的理由产生禁止反悔，不能再在法院或 ITC 主张。",
     "Two key limits: (b) no IPR if filed more than one year after service of an infringement complaint; (e) after a final written decision the petitioner is estopped in court and the ITC on grounds raised or reasonably could have been raised."),
],
"316": [
    ("时限：自立案起一年内作出最终书面决定，有正当理由可延长不超过六个月（(a)(11)）。证明标准为优势证据（(e)）。专利权人可以提出一次修改动议，但不得扩大权利要求范围或引入新内容（(d)）。",
     "Timing: final written decision within one year of institution, extendable by up to six months ((a)(11)); preponderance standard ((e)); one motion to amend that may not broaden claims or add new matter ((d))."),
],
"321": [
    ("PGR 必须在授权后九个月内提出，但可以使用 §282(b)(2)(3) 的任何无效理由，包括 §101、§112。对生物医药专利，这是挑战功能性权利要求的重要窗口。",
     "PGR must be filed within nine months of grant but may raise any invalidity ground under §282(b)(2)–(3), including §101 and §112 — an important window against functional biopharma claims."),
],
}

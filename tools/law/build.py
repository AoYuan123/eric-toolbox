#!/usr/bin/env python3
"""
生成 App 内置的法条学习数据：app/src/main/assets/law/*.json
运行：python3 tools/law/build.py
所有引文、期限、术语例句都在这里和 src/ 里的官方原文逐字核对，对不上就报错退出。

原文来源（2026-11-11 由 GitHub Actions 从官方网站下载，见 sources-data 分支）：
- 美国法典第 35 编：uscode.house.gov 发布点 Public Law 119-138（XML）
- 21 U.S.C. §355：govinfo.gov 2024 年版
- 中国专利法中文：国家知识产权局 cnipa.gov.cn；英文：全国人大官方译文（仅供参考）
- 药品专利纠纷早期解决机制实施办法（试行）：gov.cn 附件
- 判决：supremecourt.gov、loc.gov（U.S. Reports）、court.gov.cn
"""
import json, os, re, sys, importlib.util

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "src")
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "law")
CHECKED = "2026-11-11"
errors = []

def load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m); return m

def norm_en(t):
    t = t.replace("­\n", "").replace("­", "").replace("ﬁ", "fi").replace("ﬂ", "fl").replace("ﬀ", "ff")
    t = re.sub(r"(\w)-\n(\w)", r"\1\2", t)
    t = t.replace("’", "'").replace("‘", "'").replace("“", '"').replace("”", '"')
    return re.sub(r"\s+", " ", t).lower()

def norm_zh(t): return re.sub(r"\s+", "", t)

def P(en, zh, lvl=0, num=""): return {"en": en, "zh": zh, "lvl": lvl, "num": num}

# ---------------- 中国专利法 ----------------
cn = json.load(open(os.path.join(SRC, "cn_clean.json")))
NUM = "零一二三四五六七八九"
def cnum(n): return NUM[n] if n < 10 else ("十" + (NUM[n % 10] if n % 10 else "") if n < 20 else NUM[n // 10] + "十" + (NUM[n % 10] if n % 10 else ""))
CHAPTERS = [(1, "第一章 总则", "Chapter I General Provisions"), (22, "第二章 授予专利权的条件", "Chapter II Requirements for Granting Patent Rights"),
            (26, "第三章 专利的申请", "Chapter III Applications for Patents"), (34, "第四章 专利申请的审查和批准", "Chapter IV Examination and Approval of Patent Applications"),
            (42, "第五章 专利权的期限、终止和无效", "Chapter V Terms, Termination and Invalidation of Patent Rights"), (48, "第六章 专利实施的特别许可", "Chapter VI Special License for the Exploitation of a Patent"),
            (64, "第七章 专利权的保护", "Chapter VII Protection of Patent Rights"), (81, "第八章 附则", "Chapter VIII Supplementary Provisions")]
def chapter(n): return [c for c in CHAPTERS if c[0] <= n][-1]
EX_CN = load("explain_cn").EXPLAIN_CN
cn_units = []
for n in range(1, 83):
    zh, en = cn["zh"][str(n)], cn["en"][str(n)]
    if len(zh) != len(en): errors.append(f"cn:{n} 中英段落数不一致 {len(zh)}/{len(en)}")
    paras = []
    for i, (z, e) in enumerate(zip(zh, en)):
        if i == 0:
            z = re.sub(r"^第[一二三四五六七八九十]+条\s*", "", z); e = re.sub(r"^Article \d+\.\s*", "", e)
        paras.append(P(e, z))
    ch = chapter(n)
    cn_units.append({"id": f"cn:{n}", "label": f"第{cnum(n)}条", "labelEn": f"Article {n}", "group": ch[1], "groupEn": ch[2],
                     "paras": paras, "explain": [P(e, z) for z, e in EX_CN.get(n, [])], "key": n in EX_CN})
# 药品专利链接实施办法第七、八条（中文原文 + Claude 英译）
lk = open(os.path.join(SRC, "cn_linkage_measures.txt"), encoding="utf-8").read()
LK = {
 7: ("Article 7 Where the patentee or an interested party objects to a Category 4 patent certification, it may, within 45 days from the date on which the national drug evaluation institution publishes the application for drug marketing authorisation, file a lawsuit with the people's court, or request administrative adjudication from the patent administration department under the State Council, on whether the relevant technical solution of the drug applied for marketing falls within the scope of protection of the relevant patent right. A party dissatisfied with the administrative adjudication made by the patent administration department under the State Council may sue in the people's court in accordance with law after receiving the written adjudication.",),
 8: ("Article 8 After receiving a copy of the case acceptance notice of the people's court or of the acceptance notice of the patent administration department under the State Council, the drug regulatory department under the State Council shall set a nine-month waiting period for the registration application of the chemical generic drug. The waiting period runs from the date of acceptance by the people's court or by the patent administration department under the State Council and is set only once. The national drug evaluation institution does not stop the technical evaluation during the waiting period.",),
}
link_units = []
for n, (en,) in LK.items():
    m = re.search(r"第%s条\s+([^\n]+)" % cnum(n), lk)
    if not m: errors.append(f"cnlink:{n} 原文没找到"); continue
    link_units.append({"id": f"cnlink:{n}", "label": f"实施办法第{cnum(n)}条", "labelEn": f"Linkage Measures Art. {n}",
                       "group": "药品专利纠纷早期解决机制实施办法（试行）", "groupEn": "Implementing Measures for the Early Resolution Mechanism for Drug Patent Disputes (Trial)",
                       "paras": [P(re.sub(r"^Article \d+\s*", "", en), m.group(1).strip())], "explain": [], "key": True, "zhOfficialOnly": True})

law_cn = {"id": "cn", "title": "中华人民共和国专利法", "titleEn": "Patent Law of the People's Republic of China",
          "version": "2020 年第四次修正，2021 年 6 月 1 日起施行", "versionEn": "Fourth amendment 2020, in force 1 June 2021",
          "orig": "zh", "enLabel": "全国人大官方英译（仅供参考）", "zhLabel": "中文原文（国家知识产权局）",
          "note": "法律以中文文本为准；英文为全国人民代表大会发布的参考译文。实施办法第七、八条的英文为 Claude 译。",
          "sources": [["中文原文", "https://www.cnipa.gov.cn/art/2020/11/23/art_97_155167.html"],
                      ["全国人大英译（上）", "https://subsites.chinadaily.com.cn/npc/2020-10/17/c_674693_2.htm"],
                      ["全国人大英译（下）", "https://subsites.chinadaily.com.cn/npc/2020-10/17/c_674693_3.htm"],
                      ["药品专利链接实施办法", "https://www.gov.cn/zhengce/zhengceku/2021-07/04/content_5622330.htm"]],
          "checked": CHECKED, "units": cn_units + link_units}

# ---------------- 美国法典第 35 编 ----------------
usc = json.load(open(os.path.join(SRC, "usc35.json")))
ZH = {}; EXC = {}
for f in ("us_zh_1", "us_zh_2", "us_zh_3"): ZH.update(load(f).US_ZH)
m4 = load("us_zh_4"); EXC = m4.EXCERPT; ZH4 = m4.US_ZH
EX_US = load("explain_us").EXPLAIN_US
ORDER = ["100", "101", "102", "103", "112", "154", "156", "271", "282", "283", "284", "285", "286",
         "311", "312", "313", "314", "315", "316", "317", "318", "319", "321", "322", "323", "324", "325", "326", "327", "328", "329", "141"]
GROUPS = {"100": ("Part II — Patentability", "第二部分 可专利性"), "154": ("Term and extension", "专利期限与延长"), "271": ("Part III — Infringement and remedies", "第三部分 侵权与救济"),
          "311": ("Chapter 31 — Inter partes review", "第 31 章 多方复审（IPR）"), "321": ("Chapter 32 — Post-grant review", "第 32 章 授权后复审（PGR）"), "141": ("Appeals", "上诉")}
us_units = []; grp = None
for s in ORDER:
    sec = usc[s]; lines = sec["lines"]; grp = GROUPS.get(s, grp)
    if s in EXC:
        idx = EXC[s]; zh = ZH4[s]
        paras = [P(lines[i]["text"], zh[i], lines[i]["lvl"], lines[i]["num"]) for i in idx]
    else:
        zh = ZH[s]
        if len(zh) != len(lines): errors.append(f"us:{s} 译文行数 {len(zh)} ≠ 原文 {len(lines)}"); continue
        paras = []
        for l, z in zip(lines, zh):
            if not l["text"].strip() and not z.strip(): continue
            if bool(l["text"].strip()) != bool(z.strip()): errors.append(f"us:{s} 有一行原文/译文一空一不空：{l['text'][:40]}")
            paras.append(P(l["text"], z, l["lvl"], l["num"]))
    us_units.append({"id": f"us:{s}", "label": f"§ {s}", "labelEn": f"35 U.S.C. § {s}", "title": sec["heading"],
                     "group": grp[1], "groupEn": grp[0], "paras": paras, "excerpt": s in EXC,
                     "explain": [P(e, z) for z, e in EX_US.get(s, [])], "key": s in EX_US, "source": sec["source"]})
# 21 U.S.C. §355(j)（Hatch-Waxman）节选
t21 = re.sub(r"\s+", " ", open(os.path.join(SRC, "usc21_355.txt")).read())
def cut(a, b):
    i = t21.find(a); j = t21.find(b, i)
    if i < 0 or j < 0: errors.append("21 USC 355 节选定位失败：" + a[:40]); return ""
    return t21[i:j]
vii = cut("(vii) a certification, in the opinion of the applicant", "; and (viii) if with respect to the listed drug") + ";"
iii = cut("(iii) If the applicant made a certification described in subclause (IV) of paragraph (2)(A)(vii)", " except that— (I) if before the expiration")
us_units.append({"id": "us21:355j5Biii", "label": "21 U.S.C. § 355(j)", "labelEn": "21 U.S.C. § 355(j) (excerpt)", "title": "Abbreviated new drug applications (Hatch-Waxman)",
                 "group": "Hatch-Waxman（《联邦食品、药品和化妆品法》第 505 条）", "groupEn": "Hatch-Waxman (FD&C Act § 505)", "excerpt": True, "key": True,
                 "paras": [P(vii, "（vii）申请人依其意见并尽其所知，就每一项要求保护第（i）目所述参比药品、或者要求保护申请人依本款寻求批准的该参比药品用途、且须依第（b）款或者第（c）款提交信息的专利，作出如下声明——（I）该专利信息未提交，（II）该专利已经期满，（III）该专利将于某日期满，或者（IV）该专利无效，或者申请所涉新药的制造、使用或者销售不会侵犯该专利；", 0, "(2)(A)(vii)"),
                           P(iii, "（iii）申请人作出第（2）（A）（vii）目第（IV）分目所述声明的，批准应当立即生效；但在收到第（2）（B）项所述通知之日起 45 日届满前，就作为声明对象、且在申请（不包括对申请的修改或者补充，部长事后认定其实质完整者）提交之日前已依第（b）（1）款或者第（c）（2）款向部长提交信息的专利提起侵权诉讼的除外。在上述期限届满前提起此类诉讼的，批准应当在自收到第（2）（B）（i）目规定的通知之日起三十个月期间届满时生效，或者在法院因任何一方未合理配合加快诉讼而裁定的更短或者更长期间届满时生效，", 0, "(5)(B)(iii)")],
                 "explain": [P("Paragraph IV is the generic applicant’s challenge to a listed patent. Suing within 45 days triggers the 30-month stay; the exceptions after “except that—” (court decisions within the period) are omitted here.",
                               "第四段声明是仿制药申请人对登记专利的挑战。专利权人在 45 日内起诉，就触发 30 个月的等待期。“except that——”之后的例外情形（等待期内法院作出判决等）这里没有收录。")],
                 "source": "21 U.S.C. 355, United States Code 2024 edition (govinfo.gov)"})
law_us = {"id": "us", "title": "美国专利法（美国法典第 35 编节选）", "titleEn": "U.S. Patent Act (35 U.S.C., selected sections)",
          "version": "现行至 Public Law 119-138（2026-11-03）", "versionEn": "Current through Public Law 119-138 (Nov. 3, 2026)",
          "orig": "en", "enLabel": "英文原文（美国众议院法律修订办公室）", "zhLabel": "中文为 Claude 译，仅供学习",
          "note": "英文为官方现行文本。中文是 Claude 按国内实务通行译法翻译的学习译文，不是官方译本。21 U.S.C. §355 取自 2024 年版。",
          "sources": [["美国法典第 35 编", "https://uscode.house.gov/browse/prelim@title35&edition=prelim"],
                      ["USPTO 合并版专利法", "https://www.uspto.gov/web/offices/pac/mpep/consolidated_laws.pdf"],
                      ["21 U.S.C. §355（2024 年版）", "https://www.govinfo.gov/content/pkg/USCODE-2024-title21/html/USCODE-2024-title21-chap9-subchapV-partA-sec355.htm"]],
          "checked": CHECKED, "units": us_units}

UNIT = {u["id"]: u for u in law_cn["units"] + law_us["units"]}
def unit_text(ref, lang):
    u = UNIT.get(ref)
    if not u: errors.append("不存在的出处 " + ref); return ""
    return " ".join(p[lang] for p in u["paras"])

# ---------------- 术语 ----------------
gl = []
for en, zh, ref, find, note_zh, note_en in load("glossary").TERMS:
    u = UNIT.get(ref)
    if not u: errors.append("术语出处不存在 " + ref); continue
    hit = [p for p in u["paras"] if norm_en(find) in norm_en(p["en"])]
    if not hit: errors.append(f"术语「{en}」在 {ref} 里找不到：{find}"); continue
    gl.append({"key": en.lower(), "en": en, "zh": zh, "ref": ref, "refLabel": u["label"], "noteZh": note_zh, "noteEn": note_en,
               "exEn": hit[0]["en"], "exZh": hit[0]["zh"]})

# ---------------- 判例 ----------------
CASE_SRC = {k: norm_en(open(os.path.join(SRC, f"us_{k}.txt")).read()) for k in ("amgen", "ksr", "mayo", "myriad", "chakrabarty", "festo")}
CASE_SRC["guiding84"] = norm_zh(open(os.path.join(SRC, "cn_guiding84.txt")).read())
CASE_SRC["typical2022"] = norm_zh(open(os.path.join(SRC, "cn_spc_typical2022.txt")).read())
cases = []
for c in load("cases").CASES:
    zh_orig = c.get("orig") == "zh"; src = CASE_SRC[c["src"]]
    for q in c["quotes"]:
        orig = q[0]
        ok = (norm_zh(orig) in src) if zh_orig else (norm_en(orig) in src)
        if not ok: errors.append(f"判例 {c['id']} 引文在原文里找不到：{orig[:60]}")
    for r in c["statutes"]:
        if r not in UNIT: errors.append(f"判例 {c['id']} 关联的法条不存在 {r}")
    def pp(lst): return [P(e, z) for z, e in lst]
    cases.append({"id": c["id"], "name": c["name"][0], "nameZh": c["name"][1], "court": c["court"][0], "courtZh": c["court"][1],
                  "date": c["date"], "cite": c["cite"], "url": c["url"], "tags": c["tags"], "statutes": c["statutes"], "node": c["node"],
                  "orig": "zh" if zh_orig else "en",
                  "facts": pp(c["facts"]), "question": P(c["question"][1], c["question"][0]),
                  "options": [P(e, z) for z, e in c["options"]], "answer": c["answer"],
                  "holding": pp(c["holding"]),
                  "quotes": [P(q[1], q[0]) if zh_orig else P(q[0], q[1]) for q in c["quotes"]],
                  "explain": pp(c["explain"])})

# ---------------- 诉讼地图、中美对照 ----------------
mc = load("map_compare")
LINK_TXT = norm_zh(lk)
juris = []
for j in mc.MAP:
    nodes = []
    for n in j["nodes"]:
        dls = []
        for en, zh, ref, check in n["deadlines"]:
            if ref.startswith("cnlink"):
                ok = norm_zh(check) in LINK_TXT
            elif ref.startswith("cn"):
                ok = norm_zh(check) in norm_zh(unit_text(ref, "zh"))
            else:
                ok = norm_en(check) in norm_en(unit_text(ref, "en"))
            if not ok: errors.append(f"期限「{zh}」在 {ref} 里找不到「{check}」")
            dls.append({"en": en, "zh": zh, "ref": ref})
        for cid in n["cases"]:
            if cid not in [c["id"] for c in cases]: errors.append("地图引用了不存在的判例 " + cid)
        nodes.append({"id": n["id"], "kind": n["kind"], "name": n["name"][0], "nameZh": n["name"][1],
                      "desc": [P(e, z) for e, z in n["desc"]], "deadlines": dls, "next": n["next"],
                      "sites": [{"name": a, "url": b} for a, b in n["sites"]], "cases": n["cases"]})
    juris.append({"id": j["id"], "name": j["name"][0], "nameZh": j["name"][1], "nodes": nodes})
compare = []
for c in mc.COMPARE:
    for side in ("us", "cn"):
        if c[side][0] not in UNIT: errors.append("对照引用了不存在的法条 " + c[side][0])
    compare.append({"topic": c["topic"][0], "topicZh": c["topic"][1],
                    "us": {"ref": c["us"][0], "en": c["us"][1], "zh": c["us"][2]},
                    "cn": {"ref": c["cn"][0], "en": c["cn"][1], "zh": c["cn"][2]},
                    "diff": [P(e, z) for e, z in c["diff"]]})

if errors:
    print("核对没通过：\n  " + "\n  ".join(errors)); sys.exit(1)
os.makedirs(OUT, exist_ok=True)
def dump(name, obj): json.dump(obj, open(os.path.join(OUT, name), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
dump("law_cn.json", law_cn); dump("law_us.json", law_us); dump("cases.json", cases)
dump("map.json", juris); dump("compare.json", compare); dump("glossary.json", gl)
dump("meta.json", {"checked": CHECKED, "usCurrency": "Public Law 119-138 (2026-11-03)", "cnVersion": "2020 年修正",
                   "counts": {"cn": len(law_cn["units"]), "us": len(law_us["units"]), "cases": len(cases), "terms": len(gl)}})
print("OK", {k: len(v) for k, v in [("cn", law_cn["units"]), ("us", law_us["units"]), ("cases", cases), ("terms", gl), ("compare", compare)]})

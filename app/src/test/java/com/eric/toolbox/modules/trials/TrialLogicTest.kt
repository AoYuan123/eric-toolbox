package com.eric.toolbox.modules.trials

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialLogicTest {
    // 2026-10-31 实测：GET /api/v2/studies?filter.ids=NCT05773274,NCT06398444,NCT07484269&fields=…&sort=LastUpdatePostDate:desc
    private val body = """{"totalCount":3,"studies":[
{"protocolSection":{"identificationModule":{"nctId":"NCT07484269","briefTitle":"PULSE Registry: for Patients Receiving Lutetium (177Lu) Vipivotide Tetraxetan"},"statusModule":{"overallStatus":"RECRUITING","primaryCompletionDateStruct":{"date":"2031-07-31"},"studyFirstPostDateStruct":{"date":"2026-03-20"},"lastUpdatePostDateStruct":{"date":"2026-10-30"}},"sponsorCollaboratorsModule":{"leadSponsor":{"name":"Novartis Pharmaceuticals"}},"designModule":{"enrollmentInfo":{"count":753}}},"hasResults":false},
{"protocolSection":{"identificationModule":{"nctId":"NCT05773274","briefTitle":"Comparing Retreatment of 177Lu-DOTATATE PRRT Versus the Usual Treatment in Patients With Metastatic Unresectable Gastroenteropancreatic Neuroendocrine Tumors, NET RETREAT Trial"},"statusModule":{"overallStatus":"RECRUITING","primaryCompletionDateStruct":{"date":"2029-04-30"},"studyFirstPostDateStruct":{"date":"2023-03-17"},"lastUpdatePostDateStruct":{"date":"2026-10-28"}},"sponsorCollaboratorsModule":{"leadSponsor":{"name":"National Cancer Institute (NCI)"}},"designModule":{"phases":["PHASE2"],"enrollmentInfo":{"count":100}}},"hasResults":false},
{"protocolSection":{"identificationModule":{"nctId":"NCT06398444","briefTitle":"A Clinical Study of Lutetium[177Lu] Oxodotreotide Injection in Patients With Advanced Neuroendocrine Neoplasms"},"statusModule":{"overallStatus":"RECRUITING","primaryCompletionDateStruct":{"date":"2027-06-01"},"studyFirstPostDateStruct":{"date":"2024-05-03"},"lastUpdatePostDateStruct":{"date":"2026-07-17"}},"sponsorCollaboratorsModule":{"leadSponsor":{"name":"Sinotau Pharmaceutical Group"}},"designModule":{"phases":["PHASE2","PHASE3"],"enrollmentInfo":{"count":85}}},"hasResults":false}
]}"""

    @Test fun parseRealResponse() {
        val r = TrialLogic.parseSearch(body)
        assertEquals(3, r.total); assertEquals(3, r.trials.size)
        val net = r.trials[1]
        assertEquals("NCT05773274", net.nct)
        assertEquals("RECRUITING", net.status); assertEquals(listOf("PHASE2"), net.phases)
        assertEquals("National Cancer Institute (NCI)", net.sponsor)
        assertEquals("2026-10-28", net.lastUpdate); assertEquals("2023-03-17", net.firstPost)
        assertEquals("2029-04-30", net.primaryCompletion); assertEquals(100, net.enrollment)
        assertFalse(net.hasResults)
        assertEquals("阶段不适用", TrialLogic.phaseCn(r.trials[0].phases)) // 观察性登记研究没有阶段
        assertEquals("II/III 期", TrialLogic.phaseCn(r.trials[2].phases))
        assertEquals("招募中", TrialLogic.statusCn(net.status))
    }

    @Test fun searchUrl() {
        val u = TrialLogic.searchUrl(Sub(1, "intr", "Lutetium Lu 177", country = "China", phases = setOf("3", "2"), statuses = setOf("RECRUITING")))
        assertTrue(u, u.contains("query.intr=%22Lutetium+Lu+177%22")) // 完整短语匹配（10-31 实测：加引号 6 项，不加 21 项）
        assertTrue(u.contains("query.locn=China"))
        assertTrue(u.contains("aggFilters=phase%3A2+3"))
        assertTrue(u.contains("filter.overallStatus=RECRUITING"))
        assertTrue(u.contains("sort=LastUpdatePostDate%3Adesc"))
        val n = TrialLogic.searchUrl(Sub(2, "nct", "nct05773274, 06398444"))
        assertTrue(n, n.contains("filter.ids=NCT05773274%2CNCT06398444"))
        assertFalse(TrialLogic.searchUrl(Sub(3, "intr", "PSMA", exact = false)).contains("%22"))
    }

    @Test fun parseNct() {
        assertEquals(listOf("NCT05773274", "NCT06398444"), TrialLogic.parseNctList("NCT05773274 06398444\nnct05773274"))
        assertTrue(TrialLogic.parseNctList("abc").isEmpty())
    }

    private val base = TrialLogic.parseSearch(body).trials

    @Test fun firstCheckRecordsOnly() {
        assertTrue(TrialLogic.diff(1, null, base, emptySet(), "2026-10-31", 0).isEmpty())
        assertTrue(TrialLogic.diff(1, base.associateBy { it.nct }, base, emptySet(), "2026-10-31", 0).isEmpty())
    }

    @Test fun detectsChanges() {
        val old = base.associateBy { it.nct }
        val now = listOf(
            base[0].copy(lastUpdate = "2026-11-03"),                                            // 只有官方更新日期变了
            base[1].copy(status = "ACTIVE_NOT_RECRUITING", primaryCompletion = "2029-10-31"),    // 状态 + 日期
            base[2].copy(hasResults = true, phases = listOf("PHASE3")),                          // 结果 + 阶段
            base[2].copy(nct = "NCT09999991", firstPost = "2026-11-02"),                         // 新登记
            base[2].copy(nct = "NCT09999992", firstPost = "2025-01-01"),                         // 旧试验新纳入
            base[2].copy(nct = "NCT09999993", firstPost = "2026-11-02"),                         // 已排除
        )
        val ch = TrialLogic.diff(1, old, now, setOf("NCT09999993"), "2026-10-31", 123)
        val kinds = ch.map { it.nct to it.kind }
        assertEquals(
            listOf(
                "NCT07484269" to "other", "NCT05773274" to "status", "NCT05773274" to "dates",
                "NCT06398444" to "results", "NCT06398444" to "phase", "NCT09999991" to "new", "NCT09999992" to "new",
            ),
            kinds,
        )
        assertEquals("状态：招募中 → 进行中·不招募", ch[1].detail)
        assertEquals("主要完成日期：2029-04-30 → 2029-10-31", ch[2].detail)
        assertTrue(ch[5].detail.startsWith("新登记"))
        assertTrue(ch[6].detail.startsWith("新纳入"))
        // 默认不推送“关键日期调整”和“其他更新”
        assertEquals(listOf("status", "results", "phase", "new", "new"), TrialLogic.notifiable(ch, TrialLogic.DEFAULT_EVENTS).map { it.kind })
    }

    @Test fun storageRoundTrip() {
        val t = base[1]
        assertEquals(t, TrialLogic.trialFromJson(TrialLogic.trialToJson(t)))
        val noEnroll = t.copy(enrollment = null)
        assertNull(TrialLogic.trialFromJson(TrialLogic.trialToJson(noEnroll)).enrollment)
        val s = Sub(5, "spons", "Sinotau", cond = "NET", country = "China", exact = false, phases = setOf("2"),
            statuses = setOf("RECRUITING"), events = setOf("new"), excluded = setOf("NCT1"), total = 24, lastCheck = 9, lastError = "x")
        assertEquals(s, TrialLogic.subFromJson(TrialLogic.subToJson(s)))
        assertTrue(TrialLogic.sameQuery(s, s.copy(events = emptySet(), total = 1, excluded = emptySet())))
        assertFalse(TrialLogic.sameQuery(s, s.copy(country = "")))
    }
}

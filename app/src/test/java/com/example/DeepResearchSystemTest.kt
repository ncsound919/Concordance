package com.example

import com.example.concordance.model.Anchor
import com.example.concordance.model.Capsule
import com.example.concordance.model.EvidenceGrade
import com.example.concordance.model.Verdict
import com.example.concordance.research.DeepResearchSystem
import com.example.concordance.research.ResearchPaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deep research round is the top of the research loop: it grades the
 * papers, derives the insights, proposes the next experiments, and hands the
 * whole thing to the learning system. It has to work for the empty case,
 * because a research round with nothing to grade is a normal state, not an
 * error.
 */
class DeepResearchSystemTest {

    private fun anchor(id: String = "a1", source: String = "source-a") = Anchor(
        id = id,
        label = "Anchor $id",
        source = source,
        evidenceGrade = EvidenceGrade.COUNTS,
        brainEvents = 12,
        brainTotal = 200,
        otherEvents = 40,
        otherTotal = 400,
        oddsRatio = 0.68,
        orCiLow = 0.45,
        orCiHigh = 0.95
    )

    private fun capsule(id: String = "c1") = Capsule(
        id = id,
        permalink = "https://example.org/$id",
        createdUtc = "2026-10-19T00:00:00Z",
        sha256 = "a".repeat(64),
        question = "Does the marker carry?",
        anchorLabel = "Anchor",
        verdict = Verdict.SUPPORTED,
        citableArgument = "argument",
        plainLanguageVerdict = "plain",
        rNull = 0.2,
        rSignificance = 0.3,
        snAssumed = 0.9,
        rLow = 0.3,
        rHigh = 0.9,
        observedOr = 0.68,
        rawJson = "{}",
        markdown = "# capsule",
        provenanceDigest = "digest",
        provenanceRecordIds = listOf(1L)
    )

    private fun paper(
        id: String = "p1",
        timestampUtc: String = "2026-10-19T00:00:00Z",
        verdict: Verdict? = Verdict.SUPPORTED
    ) = ResearchPaper(
        id = id,
        title = "Paper $id",
        timestampUtc = timestampUtc,
        abstract = "An abstract.",
        anchorIds = listOf("a1"),
        capsuleIds = listOf("c1"),
        challengeCount = 1,
        verdict = verdict,
        rNull = 0.2,
        rSignificance = 0.3,
        modelRevisionId = null,
        keywords = listOf("a", "b", "c")
    )

    @Test
    fun `an empty round produces an empty report rather than failing`() {
        val report = DeepResearchSystem.run(emptyList(), emptyList(), emptyList(), 0.5)
        assertEquals(0, report.papersGraded)
        assertTrue(report.grades.isEmpty())
        assertTrue(report.insights.isEmpty())
        assertTrue(report.experimentProposals.isEmpty())
        assertEquals(0.0, report.overallBreakthroughScore, 1e-9)
        assertEquals("No data", report.topWeakness)
    }

    @Test
    fun `a round grades every paper it was given`() {
        val report = DeepResearchSystem.run(
            listOf(paper("p1"), paper("p2"), paper("p3")),
            listOf(anchor()),
            listOf(capsule()),
            0.9
        )
        assertEquals(3, report.papersGraded)
        assertEquals(3, report.grades.size)
    }

    @Test
    fun `the grades are ordered best first`() {
        val report = DeepResearchSystem.run(
            listOf(paper("p1"), paper("p2")),
            listOf(anchor("a1", "s1"), anchor("a2", "s2"), anchor("a3", "s3")),
            listOf(capsule("c1"), capsule("c2")),
            1.0
        )
        val composites = report.grades.map { it.composite }
        assertEquals(composites.sortedDescending(), composites)
    }

    @Test
    fun `the report summary names the counts and the best paper`() {
        val report = DeepResearchSystem.run(
            listOf(paper("p1"), paper("p2")),
            listOf(anchor()),
            listOf(capsule()),
            0.9
        )
        assertTrue(report.summary.contains("2 papers graded"))
        assertTrue(report.summary.contains("Breakthrough score"))
        assertTrue(report.summary.contains("Top weakness"))
        assertTrue(report.summary.contains("Best paper"))
    }

    @Test
    fun `the report summary does not claim a best paper when there is none`() {
        val report = DeepResearchSystem.run(emptyList(), emptyList(), emptyList(), 0.0)
        assertTrue(!report.summary.contains("Best paper"))
    }

    @Test
    fun `the report carries a timestamp`() {
        val report = DeepResearchSystem.run(emptyList(), emptyList(), emptyList(), 0.0)
        assertNotNull(report.timestampUtc)
        assertTrue(report.timestampUtc.isNotEmpty())
    }

    @Test
    fun `the breakthrough score is carried through from the grades`() {
        val report = DeepResearchSystem.run(
            listOf(paper("p1")),
            listOf(anchor()),
            listOf(capsule()),
            1.0
        )
        val expected = report.grades.map { it.breakthroughPotential }.average() / 10.0
        assertEquals(expected, report.overallBreakthroughScore, 1e-9)
    }

    @Test
    fun `conflicting verdicts produce a contradiction and therefore a proposal`() {
        val report = DeepResearchSystem.run(
            listOf(paper("p1", verdict = Verdict.SUPPORTED), paper("p2", verdict = Verdict.NOT_SUPPORTED)),
            listOf(anchor()),
            listOf(capsule()),
            0.9
        )
        assertTrue(report.insights.any { it.type.name == "CONTRADICTION" })
        assertTrue(report.experimentProposals.isNotEmpty())
    }

    @Test
    fun `a round is deterministic apart from the identifiers it mints`() {
        val papers = listOf(paper("p1"), paper("p2"))
        val anchors = listOf(anchor())
        val capsules = listOf(capsule())
        val first = DeepResearchSystem.run(papers, anchors, capsules, 0.9)
        val second = DeepResearchSystem.run(papers, anchors, capsules, 0.9)
        assertEquals(first.grades, second.grades)
        assertEquals(first.overallBreakthroughScore, second.overallBreakthroughScore, 1e-9)
        assertEquals(first.topWeakness, second.topWeakness)
    }
}

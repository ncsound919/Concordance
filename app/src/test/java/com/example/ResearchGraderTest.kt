package com.example

import com.example.concordance.model.Anchor
import com.example.concordance.model.Capsule
import com.example.concordance.model.EvidenceGrade
import com.example.concordance.model.Verdict
import com.example.concordance.research.ResearchGrader
import com.example.concordance.research.ResearchPaper
import com.example.concordance.research.PaperGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The research grader scores a paper on ten dimensions, each 0-10, and sums
 * them into a composite out of 100. The composite is what the learning system
 * reads to decide which experiments are worth running next, so the arithmetic
 * and the clamps are worth pinning.
 */
class ResearchGraderTest {

    private fun anchor(
        id: String = "a1",
        source: String = "source-a",
        grade: EvidenceGrade = EvidenceGrade.COUNTS,
        withCounts: Boolean = true
    ) = Anchor(
        id = id,
        label = "Anchor $id",
        source = source,
        evidenceGrade = grade,
        brainEvents = if (withCounts) 12 else null,
        brainTotal = if (withCounts) 200 else null,
        otherEvents = if (withCounts) 40 else null,
        otherTotal = if (withCounts) 400 else null,
        oddsRatio = if (withCounts) 0.68 else null,
        orCiLow = if (withCounts) 0.45 else null,
        orCiHigh = if (withCounts) 0.95 else null
    )

    private fun capsule(
        id: String = "c1",
        provenanceDigest: String? = "digest",
        supersedes: String? = null,
        modelRevisionId: String? = null
    ) = Capsule(
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
        provenanceDigest = provenanceDigest,
        provenanceRecordIds = if (provenanceDigest != null) listOf(1L) else emptyList(),
        modelRevisionId = modelRevisionId,
        supersedesCapsuleId = supersedes
    )

    private fun paper(
        id: String = "p1",
        anchorIds: List<String> = listOf("a1"),
        capsuleIds: List<String> = listOf("c1"),
        verdict: Verdict? = Verdict.SUPPORTED,
        challengeCount: Int = 1,
        keywords: List<String> = listOf("a", "b", "c"),
        modelRevisionId: String? = null
    ) = ResearchPaper(
        id = id,
        title = "Paper $id",
        timestampUtc = "2026-10-19T00:00:00Z",
        abstract = "An abstract.",
        anchorIds = anchorIds,
        capsuleIds = capsuleIds,
        challengeCount = challengeCount,
        verdict = verdict,
        rNull = 0.2,
        rSignificance = 0.3,
        modelRevisionId = modelRevisionId,
        keywords = keywords
    )

    // -----------------------------------------------------------------------
    // Shape
    // -----------------------------------------------------------------------

    @Test
    fun `every dimension is scored between zero and ten`() {
        val grade = ResearchGrader.grade(paper(), listOf(anchor()), listOf(capsule()), 0.9)
        for ((name, score) in grade.dimensionScores) {
            assertTrue("$name was $score", score in 0..10)
        }
    }

    @Test
    fun `the composite is the sum of the ten dimensions`() {
        val grade = ResearchGrader.grade(paper(), listOf(anchor()), listOf(capsule()), 0.9)
        assertEquals(grade.dimensionScores.values.sum(), grade.composite)
        assertTrue(grade.composite in 0..100)
    }

    @Test
    fun `the composite is normalised to a fraction`() {
        val grade = ResearchGrader.grade(paper(), listOf(anchor()), listOf(capsule()), 0.9)
        assertEquals(grade.composite / 100.0, grade.compositeNormalized, 1e-9)
    }

    @Test
    fun `the grade letter follows the documented bands`() {
        assertEquals("A (Breakthrough-grade)", gradeWithComposite(85).grade)
        assertEquals("B (Strong)", gradeWithComposite(70).grade)
        assertEquals("C (Moderate)", gradeWithComposite(55).grade)
        assertEquals("D (Weak)", gradeWithComposite(40).grade)
        assertEquals("F (Insufficient)", gradeWithComposite(39).grade)
    }

    @Test
    fun `the grade band boundaries are exact`() {
        assertEquals("F (Insufficient)", gradeWithComposite(0).grade)
        assertEquals("D (Weak)", gradeWithComposite(40).grade)
        assertEquals("C (Moderate)", gradeWithComposite(55).grade)
        assertEquals("B (Strong)", gradeWithComposite(70).grade)
        assertEquals("A (Breakthrough-grade)", gradeWithComposite(100).grade)
    }

    @Test
    fun `the weakest and strongest dimensions are reported`() {
        val grade = ResearchGrader.grade(paper(), listOf(anchor()), listOf(capsule()), 0.0)
        assertTrue(grade.dimensionScores.containsKey(grade.weakestDimension))
        assertTrue(grade.dimensionScores.containsKey(grade.strongestDimension))
        assertTrue(grade.dimensionScores.getValue(grade.weakestDimension) <=
            grade.dimensionScores.getValue(grade.strongestDimension))
    }

    // -----------------------------------------------------------------------
    // Individual dimensions
    // -----------------------------------------------------------------------

    @Test
    fun `challenge survival is the resolution rate on a ten point scale`() {
        assertEquals(10, ResearchGrader.grade(paper(), listOf(anchor()), listOf(capsule()), 1.0).challengeSurvival)
        assertEquals(5, ResearchGrader.grade(paper(), listOf(anchor()), listOf(capsule()), 0.5).challengeSurvival)
        assertEquals(0, ResearchGrader.grade(paper(), listOf(anchor()), listOf(capsule()), 0.0).challengeSurvival)
    }

    @Test
    fun `a challenge resolution rate above one is clamped`() {
        val grade = ResearchGrader.grade(paper(), listOf(anchor()), listOf(capsule()), 4.0)
        assertEquals(10, grade.challengeSurvival)
    }

    @Test
    fun `a negative challenge resolution rate is clamped`() {
        val grade = ResearchGrader.grade(paper(), listOf(anchor()), listOf(capsule()), -2.0)
        assertEquals(0, grade.challengeSurvival)
    }

    @Test
    fun `novelty rewards distinct sources and penalises abstract-only evidence`() {
        val diverse = ResearchGrader.grade(
            paper(anchorIds = listOf("a1", "a2", "a3")),
            listOf(anchor("a1", "s1"), anchor("a2", "s2"), anchor("a3", "s3")),
            emptyList(),
            0.0
        )
        val sameSource = ResearchGrader.grade(
            paper(anchorIds = listOf("a1", "a2")),
            listOf(anchor("a1", "s1"), anchor("a2", "s1")),
            emptyList(),
            0.0
        )
        assertTrue(diverse.novelty > sameSource.novelty)
    }

    @Test
    fun `abstract-only anchors reduce novelty`() {
        val counts = ResearchGrader.grade(paper(), listOf(anchor(grade = EvidenceGrade.COUNTS)), emptyList(), 0.0)
        val abstract = ResearchGrader.grade(paper(), listOf(anchor(grade = EvidenceGrade.ABSTRACT_ONLY)), emptyList(), 0.0)
        assertTrue(abstract.novelty < counts.novelty)
    }

    @Test
    fun `rigour rewards counts, capsules and provenance`() {
        val bare = ResearchGrader.grade(paper(anchorIds = emptyList(), capsuleIds = emptyList()), emptyList(), emptyList(), 0.0)
        val full = ResearchGrader.grade(paper(), listOf(anchor()), listOf(capsule()), 0.0)
        assertTrue(full.methodologicalRigor > bare.methodologicalRigor)
    }

    @Test
    fun `statistical significance rewards a narrow confidence interval and strong evidence`() {
        val strong = ResearchGrader.grade(paper(), listOf(anchor()), listOf(capsule()), 0.0)
        val bare = ResearchGrader.grade(paper(anchorIds = emptyList(), capsuleIds = emptyList()), emptyList(), emptyList(), 0.0)
        assertTrue(strong.statisticalSignificance > bare.statisticalSignificance)
    }

    @Test
    fun `reproducibility requires a capsule with a hash`() {
        val withCapsule = ResearchGrader.grade(paper(), listOf(anchor()), listOf(capsule()), 0.0)
        val without = ResearchGrader.grade(paper(capsuleIds = emptyList()), listOf(anchor()), emptyList(), 0.0)
        assertTrue(withCapsule.reproducibility > without.reproducibility)
    }

    @Test
    fun `a supported verdict carries more impact than a withheld one`() {
        val supported = ResearchGrader.grade(paper(verdict = Verdict.SUPPORTED), listOf(anchor()), listOf(capsule()), 0.0)
        val withheld = ResearchGrader.grade(paper(verdict = Verdict.WITHHELD), listOf(anchor()), listOf(capsule()), 0.0)
        assertTrue(supported.impactPotential > withheld.impactPotential)
    }

    @Test
    fun `keywords and challenges both raise impact`() {
        val rich = ResearchGrader.grade(paper(challengeCount = 3, keywords = listOf("a", "b", "c")), listOf(anchor()), listOf(capsule()), 0.0)
        val bare = ResearchGrader.grade(paper(challengeCount = 0, keywords = emptyList()), listOf(anchor()), listOf(capsule()), 0.0)
        assertTrue(rich.impactPotential > bare.impactPotential)
    }

    @Test
    fun `generalizability rewards breadth of evidence and cohorts`() {
        val broad = ResearchGrader.grade(
            paper(anchorIds = listOf("a1", "a2")),
            listOf(anchor("a1", "s1"), anchor("a2", "s2")),
            emptyList(),
            0.0
        )
        val narrow = ResearchGrader.grade(paper(anchorIds = listOf("a1")), listOf(anchor("a1")), emptyList(), 0.0)
        assertTrue(broad.generalizability > narrow.generalizability)
    }

    @Test
    fun `data quality rewards counts and confidence intervals`() {
        val withCounts = ResearchGrader.grade(paper(), listOf(anchor()), emptyList(), 0.0)
        val without = ResearchGrader.grade(
            paper(),
            listOf(anchor(withCounts = false, grade = EvidenceGrade.SUMMARY)),
            emptyList(),
            0.0
        )
        assertTrue(withCounts.dataQuality > without.dataQuality)
    }

    @Test
    fun `innovation rewards provenance, supersession and revision tracking`() {
        val innovative = ResearchGrader.grade(
            paper(modelRevisionId = "rev-1"),
            listOf(anchor()),
            listOf(capsule(supersedes = "c0")),
            0.0
        )
        val plain = ResearchGrader.grade(
            paper(modelRevisionId = null),
            listOf(anchor()),
            listOf(capsule(provenanceDigest = null, modelRevisionId = null)),
            0.0
        )
        assertTrue(innovative.innovation > plain.innovation)
    }

    @Test
    fun `breakthrough potential is a weighted mix of the top dimensions`() {
        val grade = ResearchGrader.grade(paper(), listOf(anchor()), listOf(capsule()), 0.0)
        val expected = (grade.novelty * 0.3 + grade.impactPotential * 0.3 +
            grade.innovation * 0.2 + grade.statisticalSignificance * 0.2).toInt()
        assertEquals(expected, grade.breakthroughPotential)
    }

    // -----------------------------------------------------------------------
    // The batch API
    // -----------------------------------------------------------------------

    @Test
    fun `gradeAll sorts by composite, best first`() {
        val strong = ResearchGrader.grade(
            paper("strong", anchorIds = listOf("a1", "a2", "a3"), capsuleIds = listOf("c1", "c2")),
            listOf(anchor("a1", "s1"), anchor("a2", "s2"), anchor("a3", "s3")),
            listOf(capsule("c1"), capsule("c2")),
            1.0
        )
        val weak = ResearchGrader.grade(
            paper("weak", anchorIds = emptyList(), capsuleIds = emptyList(), challengeCount = 0, keywords = emptyList()),
            emptyList(),
            emptyList(),
            0.0
        )
        val sorted = ResearchGrader.gradeAll(
            listOf(paper("weak", anchorIds = emptyList(), capsuleIds = emptyList()), paper("strong", anchorIds = listOf("a1", "a2", "a3"), capsuleIds = listOf("c1", "c2"))),
            listOf(anchor("a1", "s1"), anchor("a2", "s2"), anchor("a3", "s3")),
            listOf(capsule("c1"), capsule("c2")),
            1.0
        )
        assertEquals(2, sorted.size)
        assertEquals("strong", sorted.first().paperId)
        assertTrue(sorted.first().composite >= sorted.last().composite)
        assertTrue(strong.composite > weak.composite)
    }

    @Test
    fun `grading an empty list of papers yields an empty list`() {
        assertTrue(ResearchGrader.gradeAll(emptyList(), emptyList(), emptyList(), 0.5).isEmpty())
    }

    @Test
    fun `anchors that belong to another paper are not counted`() {
        val grade = ResearchGrader.grade(
            paper(anchorIds = listOf("a1")),
            listOf(anchor("a1", "s1"), anchor("a2", "s2"), anchor("a3", "s3")),
            emptyList(),
            0.0
        )
        // Only one anchor is in scope, so the source diversity is 1, not 3.
        assertEquals(2, grade.novelty)
    }

    /** A [PaperGrade] whose ten dimensions sum to exactly [composite]. */
    private fun gradeWithComposite(composite: Int) = PaperGrade(
        paperId = "p",
        title = "t",
        timestampUtc = "2026-10-19T00:00:00Z",
        novelty = composite,
        methodologicalRigor = 0,
        statisticalSignificance = 0,
        reproducibility = 0,
        impactPotential = 0,
        generalizability = 0,
        dataQuality = 0,
        innovation = 0,
        breakthroughPotential = 0,
        challengeSurvival = 0
    )
}

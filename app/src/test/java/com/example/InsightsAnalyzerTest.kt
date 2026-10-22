package com.example

import com.example.concordance.model.Verdict
import com.example.concordance.research.InsightsAnalyzer
import com.example.concordance.research.PaperGrade
import com.example.concordance.research.ResearchInsight
import com.example.concordance.research.ResearchPaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The insights analyzer turns a pile of graded papers into the handful of
 * signals worth a researcher's attention: a breakthrough, a recurring
 * weakness, a direction of travel, a contradiction, or an opportunity.
 */
class InsightsAnalyzerTest {

    private fun grade(
        paperId: String = "p1",
        timestampUtc: String = "2026-10-19T00:00:00Z",
        novelty: Int = 5,
        methodologicalRigor: Int = 5,
        statisticalSignificance: Int = 5,
        reproducibility: Int = 5,
        impactPotential: Int = 5,
        generalizability: Int = 5,
        dataQuality: Int = 5,
        innovation: Int = 5,
        breakthroughPotential: Int = 5,
        challengeSurvival: Int = 5
    ) = PaperGrade(
        paperId = paperId,
        title = "Paper $paperId",
        timestampUtc = timestampUtc,
        novelty = novelty,
        methodologicalRigor = methodologicalRigor,
        statisticalSignificance = statisticalSignificance,
        reproducibility = reproducibility,
        impactPotential = impactPotential,
        generalizability = generalizability,
        dataQuality = dataQuality,
        innovation = innovation,
        breakthroughPotential = breakthroughPotential,
        challengeSurvival = challengeSurvival
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
        anchorIds = emptyList(),
        capsuleIds = emptyList(),
        challengeCount = 0,
        verdict = verdict,
        rNull = null,
        rSignificance = null,
        modelRevisionId = null
    )

    private fun types(insights: List<ResearchInsight>) = insights.map { it.type }.toSet()

    /** A [PaperGrade] whose ten dimensions sum to exactly [composite]. */
    private fun gradeWithComposite(composite: Int, paperId: String = "p1"): PaperGrade {
        val base = composite / 10
        val remainder = composite % 10
        val dims = IntArray(10) { base }
        for (i in 0 until remainder) dims[i]++
        return PaperGrade(
            paperId = paperId,
            title = "Paper $paperId",
            timestampUtc = "2026-10-19T00:00:00Z",
            novelty = dims[0],
            methodologicalRigor = dims[1],
            statisticalSignificance = dims[2],
            reproducibility = dims[3],
            impactPotential = dims[4],
            generalizability = dims[5],
            dataQuality = dims[6],
            innovation = dims[7],
            breakthroughPotential = dims[8],
            challengeSurvival = dims[9]
        )
    }

    // -----------------------------------------------------------------------
    // Empty input
    // -----------------------------------------------------------------------

    @Test
    fun `no grades produce no insights`() {
        assertTrue(InsightsAnalyzer.analyzeTrends(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `no grades produce a zero breakthrough score`() {
        assertEquals(0.0, InsightsAnalyzer.breakthroughScore(emptyList()), 1e-9)
    }

    @Test
    fun `no grades produce a no-data weakness`() {
        assertEquals("No data", InsightsAnalyzer.topWeakness(emptyList()))
    }

    // -----------------------------------------------------------------------
    // Breakthrough
    // -----------------------------------------------------------------------

    @Test
    fun `a composite of eighty five or more is a breakthrough`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(gradeWithComposite(85)),
            listOf(paper())
        )
        assertTrue(ResearchInsight.InsightType.BREAKTHROUGH in types(insights))
    }

    @Test
    fun `a composite just below eighty five is not a breakthrough`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(gradeWithComposite(84)),
            listOf(paper())
        )
        assertTrue(ResearchInsight.InsightType.BREAKTHROUGH !in types(insights))
    }

    @Test
    fun `a composite of exactly eighty five is a breakthrough`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(gradeWithComposite(85)),
            listOf(paper())
        )
        assertTrue(ResearchInsight.InsightType.BREAKTHROUGH in types(insights))
    }

    @Test
    fun `a breakthrough insight names the papers that triggered it`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(gradeWithComposite(90)),
            listOf(paper("p1"))
        )
        val breakthrough = insights.first { it.type == ResearchInsight.InsightType.BREAKTHROUGH }
        assertEquals(listOf("p1"), breakthrough.supportingPaperIds)
        assertTrue(breakthrough.actionable)
    }

    // -----------------------------------------------------------------------
    // Gaps
    // -----------------------------------------------------------------------

    @Test
    fun `dimensions averaging below five are reported as a gap`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(grade(novelty = 1, methodologicalRigor = 1, statisticalSignificance = 1)),
            listOf(paper())
        )
        val gap = insights.first { it.type == ResearchInsight.InsightType.GAP }
        assertTrue(gap.title.contains("Recurring weakness"))
        assertTrue(gap.description.contains("Novelty"))
    }

    @Test
    fun `strong papers produce no gap`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(grade(
                novelty = 10, methodologicalRigor = 10, statisticalSignificance = 10,
                reproducibility = 10, impactPotential = 10, generalizability = 10,
                dataQuality = 10, innovation = 10, breakthroughPotential = 10, challengeSurvival = 10
            )),
            listOf(paper())
        )
        assertTrue(ResearchInsight.InsightType.GAP !in types(insights))
    }

    // -----------------------------------------------------------------------
    // Trends
    // -----------------------------------------------------------------------

    @Test
    fun `a rising composite is an improving trend`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(
                grade("p1", "2026-10-01T00:00:00Z", novelty = 2, methodologicalRigor = 2, statisticalSignificance = 2),
                grade("p2", "2026-10-19T00:00:00Z", novelty = 9, methodologicalRigor = 9, statisticalSignificance = 9)
            ),
            listOf(paper("p1"), paper("p2"))
        )
        val trend = insights.first { it.type == ResearchInsight.InsightType.TREND }
        assertTrue(trend.title.contains("improving"))
        assertTrue(!trend.actionable)
    }

    @Test
    fun `a falling composite is a declining trend and is actionable`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(
                grade("p1", "2026-10-01T00:00:00Z", novelty = 9, methodologicalRigor = 9, statisticalSignificance = 9),
                grade("p2", "2026-10-19T00:00:00Z", novelty = 2, methodologicalRigor = 2, statisticalSignificance = 2)
            ),
            listOf(paper("p1"), paper("p2"))
        )
        val trend = insights.first { it.type == ResearchInsight.InsightType.TREND }
        assertTrue(trend.title.contains("declining"))
        assertTrue(trend.actionable)
    }

    @Test
    fun `a flat composite is a stable trend`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(grade("p1", "2026-10-01T00:00:00Z"), grade("p2", "2026-10-19T00:00:00Z")),
            listOf(paper("p1"), paper("p2"))
        )
        val trend = insights.first { it.type == ResearchInsight.InsightType.TREND }
        assertTrue(trend.title.contains("stable"))
    }

    @Test
    fun `a single paper produces no trend`() {
        val insights = InsightsAnalyzer.analyzeTrends(listOf(grade()), listOf(paper()))
        assertTrue(ResearchInsight.InsightType.TREND !in types(insights))
    }

    // -----------------------------------------------------------------------
    // Contradictions
    // -----------------------------------------------------------------------

    @Test
    fun `conflicting verdicts are a contradiction`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(grade("p1"), grade("p2")),
            listOf(paper("p1", verdict = Verdict.SUPPORTED), paper("p2", verdict = Verdict.NOT_SUPPORTED))
        )
        val contradiction = insights.first { it.type == ResearchInsight.InsightType.CONTRADICTION }
        assertEquals(listOf("p1", "p2"), contradiction.supportingPaperIds)
        assertTrue(contradiction.actionable)
    }

    @Test
    fun `a single verdict is not a contradiction`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(grade("p1"), grade("p2")),
            listOf(paper("p1", verdict = Verdict.SUPPORTED), paper("p2", verdict = Verdict.SUPPORTED))
        )
        assertTrue(ResearchInsight.InsightType.CONTRADICTION !in types(insights))
    }

    @Test
    fun `papers with no verdict are not a contradiction`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(grade("p1"), grade("p2")),
            listOf(paper("p1", verdict = null), paper("p2", verdict = null))
        )
        assertTrue(ResearchInsight.InsightType.CONTRADICTION !in types(insights))
    }

    // -----------------------------------------------------------------------
    // Optimisation
    // -----------------------------------------------------------------------

    @Test
    fun `high innovation with low impact is an optimisation opportunity`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(grade(innovation = 8, impactPotential = 2)),
            listOf(paper())
        )
        val optimisation = insights.first { it.type == ResearchInsight.InsightType.OPTIMIZATION }
        assertEquals(listOf("p1"), optimisation.supportingPaperIds)
    }

    @Test
    fun `high innovation with high impact is not an optimisation opportunity`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(grade(innovation = 8, impactPotential = 8)),
            listOf(paper())
        )
        assertTrue(ResearchInsight.InsightType.OPTIMIZATION !in types(insights))
    }

    // -----------------------------------------------------------------------
    // Aggregates
    // -----------------------------------------------------------------------

    @Test
    fun `the breakthrough score is the mean breakthrough potential over ten`() {
        val score = InsightsAnalyzer.breakthroughScore(
            listOf(grade("p1", breakthroughPotential = 10), grade("p2", breakthroughPotential = 4))
        )
        assertEquals(0.7, score, 1e-9)
    }

    @Test
    fun `the top weakness is the most common weakest dimension`() {
        // p1 is weakest on Novelty, p2 is weakest on Novelty too.
        val weakness = InsightsAnalyzer.topWeakness(
            listOf(
                grade("p1", novelty = 1, methodologicalRigor = 9, statisticalSignificance = 9, reproducibility = 9, impactPotential = 9, generalizability = 9, dataQuality = 9, innovation = 9, breakthroughPotential = 9, challengeSurvival = 9),
                grade("p2", novelty = 2, methodologicalRigor = 9, statisticalSignificance = 9, reproducibility = 9, impactPotential = 9, generalizability = 9, dataQuality = 9, innovation = 9, breakthroughPotential = 9, challengeSurvival = 9)
            )
        )
        assertEquals("Novelty", weakness)
    }

    @Test
    fun `every insight has a unique id and a timestamp`() {
        val insights = InsightsAnalyzer.analyzeTrends(
            listOf(
                grade("p1", "2026-10-19T00:00:00Z", novelty = 1, methodologicalRigor = 1, statisticalSignificance = 1),
                grade("p2", "2026-10-20T00:00:00Z", novelty = 1, methodologicalRigor = 1, statisticalSignificance = 1)
            ),
            listOf(paper("p1", verdict = Verdict.SUPPORTED), paper("p2", verdict = Verdict.NOT_SUPPORTED))
        )
        // A gap, a trend and a contradiction.
        assertTrue(insights.size >= 3)
        assertEquals(insights.size, insights.map { it.id }.toSet().size)
        for (insight in insights) {
            assertNotEquals("", insight.timestampUtc)
            assertTrue(insight.confidence in 0.0..1.0)
        }
    }
}

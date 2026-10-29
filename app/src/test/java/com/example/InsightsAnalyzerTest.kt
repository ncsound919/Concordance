package com.example

import com.example.concordance.model.Verdict
import com.example.concordance.research.InsightsAnalyzer
import com.example.concordance.research.PaperGrade
import com.example.concordance.research.ResearchInsight
import com.example.concordance.research.ResearchPaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsAnalyzerTest {

    private fun grade(
        paperId: String,
        composite: Int = 60,
        novelty: Int? = null, rigor: Int? = null, sig: Int? = null, repro: Int? = null,
        impact: Int? = null, gen: Int? = null, dataQ: Int? = null, innov: Int? = null,
        breakthrough: Int? = null, challenge: Int? = null, timestamp: String = "2026-10-28T00:00:00Z",
    ): PaperGrade {
        val base = composite / 10
        return PaperGrade(
            paperId = paperId, title = "Paper $paperId", timestampUtc = timestamp,
            novelty = novelty ?: base, methodologicalRigor = rigor ?: base,
            statisticalSignificance = sig ?: base, reproducibility = repro ?: base,
            impactPotential = impact ?: base, generalizability = gen ?: base,
            dataQuality = dataQ ?: base, innovation = innov ?: base,
            breakthroughPotential = breakthrough ?: base, challengeSurvival = challenge ?: base,
        )
    }

    private fun paper(id: String, verdict: Verdict? = null): ResearchPaper = ResearchPaper(
        id = id, title = "Paper $id", timestampUtc = "2026-10-28T00:00:00Z",
        abstract = "", anchorIds = emptyList(), capsuleIds = emptyList(), challengeCount = 0,
        verdict = verdict, rNull = null, rSignificance = null, modelRevisionId = null,
    )

    @Test
    fun emptyGradesYieldNoInsights() {
        assertTrue(InsightsAnalyzer.analyzeTrends(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun breakthroughPapersProduceABreakthroughInsight() {
        val grades = listOf(grade("p1", breakthrough = 9, composite = 90))
        val insights = InsightsAnalyzer.analyzeTrends(grades, listOf(paper("p1")))
        assertTrue(insights.any { it.type == ResearchInsight.InsightType.BREAKTHROUGH })
        val bi = insights.first { it.type == ResearchInsight.InsightType.BREAKTHROUGH }
        assertTrue("confidence ${bi.confidence}", bi.confidence in 0.5..0.95)
        assertEquals(listOf("p1"), bi.supportingPaperIds)
    }

    @Test
    fun aStrongerBreakthroughHasHigherConfidence() {
        val low = InsightsAnalyzer.analyzeTrends(
            listOf(grade("p1", breakthrough = 9, composite = 90)),
            listOf(paper("p1")),
        ).first { it.type == ResearchInsight.InsightType.BREAKTHROUGH }
        val high = InsightsAnalyzer.analyzeTrends(
            listOf(grade("p1", breakthrough = 10, composite = 100)),
            listOf(paper("p1")),
        ).first { it.type == ResearchInsight.InsightType.BREAKTHROUGH }
        assertTrue("high ${high.confidence} <= low ${low.confidence}", high.confidence > low.confidence)
    }

    @Test
    fun aConsistentlyWeakDimensionProducesAGapInsight() {
        val grades = listOf(
            grade("p1", rigor = 2, sig = 2, repro = 2),
            grade("p2", rigor = 3, sig = 2, repro = 3),
        )
        val insights = InsightsAnalyzer.analyzeTrends(grades, listOf(paper("p1"), paper("p2")))
        assertTrue(insights.any { it.type == ResearchInsight.InsightType.GAP })
        val gap = insights.first { it.type == ResearchInsight.InsightType.GAP }
        assertTrue("confidence ${gap.confidence}", gap.confidence in 0.4..0.9)
    }

    @Test
    fun conflictingVerdictsProduceAContradictionInsight() {
        val grades = listOf(grade("p1"), grade("p2"))
        val papers = listOf(paper("p1", Verdict.SUPPORTED), paper("p2", Verdict.NOT_SUPPORTED))
        val insights = InsightsAnalyzer.analyzeTrends(grades, papers)
        assertTrue(insights.any { it.type == ResearchInsight.InsightType.CONTRADICTION })
    }

    @Test
    fun identicalVerdictsDoNotProduceAContradiction() {
        val grades = listOf(grade("p1"), grade("p2"))
        val papers = listOf(paper("p1", Verdict.SUPPORTED), paper("p2", Verdict.SUPPORTED))
        val insights = InsightsAnalyzer.analyzeTrends(grades, papers)
        assertFalse(insights.any { it.type == ResearchInsight.InsightType.CONTRADICTION })
    }

    @Test
    fun highInnovationLowImpactProducesAnOptimizationInsight() {
        val grades = listOf(grade("p1", innov = 9, impact = 3))
        val insights = InsightsAnalyzer.analyzeTrends(grades, listOf(paper("p1")))
        assertTrue(insights.any { it.type == ResearchInsight.InsightType.OPTIMIZATION })
    }

    @Test
    fun aDecliningTrendIsActionable() {
        val g1 = grade("p1", composite = 80, timestamp = "2026-10-01T00:00:00Z")
        val g2 = grade("p2", composite = 50, timestamp = "2026-10-10T00:00:00Z")
        val trend = InsightsAnalyzer.analyzeTrends(listOf(g1, g2), listOf(paper("p1"), paper("p2")))
            .first { it.type == ResearchInsight.InsightType.TREND }
        assertTrue(trend.actionable)
        assertTrue(trend.title.contains("declining"))
    }

    @Test
    fun breakthroughScoreIsTheMeanBreakthroughPotential() {
        val grades = listOf(grade("p1", breakthrough = 8), grade("p2", breakthrough = 6))
        assertEquals(0.7, InsightsAnalyzer.breakthroughScore(grades), 0.0001)
    }

    @Test
    fun topWeaknessIsTheMostCommonWeakestDimension() {
        val grades = listOf(
            grade("p1", novelty = 2, rigor = 8, sig = 8, repro = 8, impact = 8, gen = 8, dataQ = 8, innov = 8, breakthrough = 8, challenge = 8),
            grade("p2", novelty = 3, rigor = 8, sig = 8, repro = 8, impact = 8, gen = 8, dataQ = 8, innov = 8, breakthrough = 8, challenge = 8),
        )
        assertEquals("Novelty", InsightsAnalyzer.topWeakness(grades))
    }

}

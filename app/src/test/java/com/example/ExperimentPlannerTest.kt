package com.example

import com.example.concordance.research.ExperimentPlanner
import com.example.concordance.research.PaperGrade
import com.example.concordance.research.ResearchInsight
import com.example.concordance.research.ResearchPaper
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentPlannerTest {

    private fun grade(paperId: String, composite: Int = 60): PaperGrade {
        val base = composite / 10
        return PaperGrade(
            paperId = paperId, title = "Paper $paperId",
            timestampUtc = "2026-10-28T00:00:00Z",
            novelty = base, methodologicalRigor = base, statisticalSignificance = base,
            reproducibility = base, impactPotential = base, generalizability = base,
            dataQuality = base, innovation = base, breakthroughPotential = base,
            challengeSurvival = base,
        )
    }

    private fun paper(id: String): ResearchPaper = ResearchPaper(
        id = id, title = "Paper $id", timestampUtc = "2026-10-28T00:00:00Z",
        abstract = "", anchorIds = emptyList(), capsuleIds = emptyList(), challengeCount = 0,
        verdict = null, rNull = null, rSignificance = null, modelRevisionId = null,
    )

    private fun gapInsight(id: String, confidence: Double): ResearchInsight = ResearchInsight(
        id = id, timestampUtc = "2026-10-28T00:00:00Z",
        type = ResearchInsight.InsightType.GAP,
        title = "Recurring weakness: Novelty",
        description = "Novelty=3.0",
        supportingPaperIds = listOf("p1"),
        confidence = confidence, actionable = true,
    )

    private fun breakthroughInsight(id: String, confidence: Double): ResearchInsight = ResearchInsight(
        id = id, timestampUtc = "2026-10-28T00:00:00Z",
        type = ResearchInsight.InsightType.BREAKTHROUGH,
        title = "Breakthrough signal",
        description = "desc",
        supportingPaperIds = listOf("p1"),
        confidence = confidence, actionable = true,
    )

    @Test
    fun aHighConfidenceGapHasHigherImpactThanALowConfidenceOne() {
        val high = ExperimentPlanner.propose(
            grades = listOf(grade("p1")),
            insights = listOf(gapInsight("g1", confidence = 0.9)),
            papers = listOf(paper("p1")),
        ).first()
        val low = ExperimentPlanner.propose(
            grades = listOf(grade("p1")),
            insights = listOf(gapInsight("g2", confidence = 0.4)),
            papers = listOf(paper("p1")),
        ).first()
        assertTrue("high ${high.estimatedImpact} <= low ${low.estimatedImpact}",
            high.estimatedImpact > low.estimatedImpact)
    }

    @Test
    fun impactIsNotAHardcodedConstant() {
        val a = ExperimentPlanner.propose(listOf(grade("p1")), listOf(gapInsight("g1", 0.5)), listOf(paper("p1"))).first()
        val b = ExperimentPlanner.propose(listOf(grade("p1")), listOf(gapInsight("g2", 0.8)), listOf(paper("p1"))).first()
        assertTrue(a.estimatedImpact != b.estimatedImpact)
        assertTrue(a.estimatedImpact in 0.0..1.0)
        assertTrue(b.estimatedImpact in 0.0..1.0)
    }

    @Test
    fun aWeakPaperHasMoreRoomToImprove() {
        val weak = ExperimentPlanner.propose(
            grades = listOf(grade("p1", composite = 30)),
            insights = emptyList(),
            papers = listOf(paper("p1")),
        ).firstOrNull { it.title.startsWith("Refine") }
        val moderate = ExperimentPlanner.propose(
            grades = listOf(grade("p1", composite = 50)),
            insights = emptyList(),
            papers = listOf(paper("p1")),
        ).firstOrNull { it.title.startsWith("Refine") }
        if (weak != null && moderate != null) {
            assertTrue("weak ${weak.estimatedImpact} <= moderate ${moderate.estimatedImpact}",
                weak.estimatedImpact > moderate.estimatedImpact)
        }
    }

    @Test
    fun proposalsAreSortedByPriorityTimesImpact() {
        val proposals = ExperimentPlanner.propose(
            grades = listOf(grade("p1")),
            insights = listOf(gapInsight("g1", 0.8), breakthroughInsight("b1", 0.9)),
            papers = listOf(paper("p1")),
        )
        for (i in 0 until proposals.size - 1) {
            val left = proposals[i].priority.weight * proposals[i].estimatedImpact
            val right = proposals[i + 1].priority.weight * proposals[i + 1].estimatedImpact
            assertTrue("sort order violated at $i: $left < $right", left >= right - 1e-9)
        }
    }

    @Test
    fun aBreakthroughHasHigherPriorityThanAGap() {
        val proposals = ExperimentPlanner.propose(
            grades = listOf(grade("p1")),
            insights = listOf(gapInsight("g1", 0.8), breakthroughInsight("b1", 0.9)),
            papers = listOf(paper("p1")),
        )
        val breakthrough = proposals.first { it.title.startsWith("Extend") }
        val gap = proposals.first { it.title.startsWith("Address") }
        assertTrue(breakthrough.priority.weight >= gap.priority.weight)
    }

    @Test
    fun manyProposalsAreDampened() {
        val insights = (1..10).map { gapInsight("g$it", 0.8) }
        val proposals = ExperimentPlanner.propose(
            grades = listOf(grade("p1")),
            insights = insights,
            papers = listOf(paper("p1")),
        )
        assertTrue("expected > 5 proposals", proposals.size > 5)
        proposals.forEach { p ->
            assertTrue("impact ${p.estimatedImpact} too high", p.estimatedImpact < 0.9)
        }
    }

    @Test
    fun anEmptyInputYieldsNoProposals() {
        assertTrue(ExperimentPlanner.propose(emptyList(), emptyList(), emptyList()).isEmpty())
    }
}

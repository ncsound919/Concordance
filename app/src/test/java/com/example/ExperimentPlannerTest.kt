package com.example

import com.example.concordance.research.ExperimentPlanner
import com.example.concordance.research.ExperimentProposal
import com.example.concordance.research.PaperGrade
import com.example.concordance.research.ResearchInsight
import com.example.concordance.research.ResearchPaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The experiment planner turns insights into concrete next experiments, and
 * orders them by priority weighted by estimated impact. The ordering is what
 * the learning loop acts on, so it is worth pinning exactly.
 */
class ExperimentPlannerTest {

    /** A [PaperGrade] whose ten dimensions sum to exactly [composite]. */
    private fun grade(paperId: String = "p1", composite: Int = 50): PaperGrade {
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

    private fun insight(
        id: String = "i1",
        type: ResearchInsight.InsightType = ResearchInsight.InsightType.GAP,
        supporting: List<String> = listOf("p1")
    ) = ResearchInsight(
        id = id,
        timestampUtc = "2026-10-19T00:00:00Z",
        type = type,
        title = "Insight $id",
        description = "Description of $id",
        supportingPaperIds = supporting,
        confidence = 0.8,
        actionable = true
    )

    private val paper = ResearchPaper(
        id = "p1",
        title = "Paper p1",
        timestampUtc = "2026-10-19T00:00:00Z",
        abstract = "",
        anchorIds = emptyList(),
        capsuleIds = emptyList(),
        challengeCount = 0,
        verdict = null,
        rNull = null,
        rSignificance = null,
        modelRevisionId = null
    )

    // -----------------------------------------------------------------------
    // One proposal per insight kind
    // -----------------------------------------------------------------------

    @Test
    fun `a gap insight produces a high priority proposal`() {
        val proposals = ExperimentPlanner.propose(emptyList(), listOf(insight(type = ResearchInsight.InsightType.GAP)), emptyList())
        assertEquals(1, proposals.size)
        assertEquals(ExperimentProposal.Priority.HIGH, proposals.first().priority)
        assertEquals(0.7, proposals.first().estimatedImpact, 1e-9)
    }

    @Test
    fun `a breakthrough insight produces a critical proposal`() {
        val proposals = ExperimentPlanner.propose(emptyList(), listOf(insight(type = ResearchInsight.InsightType.BREAKTHROUGH)), emptyList())
        assertEquals(ExperimentProposal.Priority.CRITICAL, proposals.first().priority)
        assertEquals(0.9, proposals.first().estimatedImpact, 1e-9)
    }

    @Test
    fun `a contradiction insight produces a high priority proposal`() {
        val proposals = ExperimentPlanner.propose(emptyList(), listOf(insight(type = ResearchInsight.InsightType.CONTRADICTION)), emptyList())
        assertEquals(ExperimentProposal.Priority.HIGH, proposals.first().priority)
        assertEquals(0.8, proposals.first().estimatedImpact, 1e-9)
    }

    @Test
    fun `an optimisation insight produces a medium priority proposal`() {
        val proposals = ExperimentPlanner.propose(emptyList(), listOf(insight(type = ResearchInsight.InsightType.OPTIMIZATION)), emptyList())
        assertEquals(ExperimentProposal.Priority.MEDIUM, proposals.first().priority)
        assertEquals(0.6, proposals.first().estimatedImpact, 1e-9)
    }

    @Test
    fun `a trend insight produces no proposal`() {
        val proposals = ExperimentPlanner.propose(emptyList(), listOf(insight(type = ResearchInsight.InsightType.TREND)), emptyList())
        assertTrue(proposals.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Weak papers
    // -----------------------------------------------------------------------

    @Test
    fun `a paper scoring below fifty five produces a refinement proposal`() {
        val proposals = ExperimentPlanner.propose(listOf(grade("p1", 20)), emptyList(), emptyList())
        assertEquals(1, proposals.size)
        assertEquals(ExperimentProposal.Priority.MEDIUM, proposals.first().priority)
        assertEquals(0.5, proposals.first().estimatedImpact, 1e-9)
        assertEquals(listOf("p1"), proposals.first().basedOnPaperIds)
    }

    @Test
    fun `a paper scoring fifty five or more produces no refinement proposal`() {
        val proposals = ExperimentPlanner.propose(listOf(grade("p1", 60)), emptyList(), emptyList())
        assertTrue(proposals.isEmpty())
    }

    @Test
    fun `at most three weak papers are refined`() {
        val weak = (1..6).map { grade("p$it", 20) }
        val proposals = ExperimentPlanner.propose(weak, emptyList(), emptyList())
        assertEquals(3, proposals.size)
    }

    // -----------------------------------------------------------------------
    // Ordering
    // -----------------------------------------------------------------------

    @Test
    fun `proposals are ordered by priority weight times estimated impact`() {
        val insights = listOf(
            insight("gap", ResearchInsight.InsightType.GAP),
            insight("break", ResearchInsight.InsightType.BREAKTHROUGH),
            insight("contra", ResearchInsight.InsightType.CONTRADICTION),
            insight("opt", ResearchInsight.InsightType.OPTIMIZATION)
        )
        val proposals = ExperimentPlanner.propose(emptyList(), insights, emptyList())
        assertEquals(4, proposals.size)
        // CRITICAL(4) * 0.9 = 3.6, HIGH(3) * 0.8 = 2.4, HIGH(3) * 0.7 = 2.1,
        // MEDIUM(2) * 0.6 = 1.2
        val weights = proposals.map { it.priority.weight * it.estimatedImpact }
        assertEquals(weights.sortedDescending(), weights)
        assertTrue(proposals.first().title.startsWith("Extend:"))
    }

    @Test
    fun `a critical proposal outranks a high priority one`() {
        val insights = listOf(
            insight("gap", ResearchInsight.InsightType.GAP),
            insight("break", ResearchInsight.InsightType.BREAKTHROUGH)
        )
        val proposals = ExperimentPlanner.propose(emptyList(), insights, emptyList())
        assertEquals(ExperimentProposal.Priority.CRITICAL, proposals.first().priority)
    }

    // -----------------------------------------------------------------------
    // Provenance
    // -----------------------------------------------------------------------

    @Test
    fun `a proposal records the papers and insights it came from`() {
        val insights = listOf(insight("gap", ResearchInsight.InsightType.GAP, supporting = listOf("p1", "p2")))
        val proposal = ExperimentPlanner.propose(emptyList(), insights, emptyList()).first()
        assertEquals(listOf("p1", "p2"), proposal.basedOnPaperIds)
        assertEquals(listOf("gap"), proposal.basedOnInsightIds)
    }

    @Test
    fun `a refinement proposal names the weakest dimension`() {
        val proposal = ExperimentPlanner.propose(listOf(grade("p1", 20)), emptyList(), emptyList()).first()
        assertTrue(proposal.title.startsWith("Refine:"))
        assertTrue(proposal.hypothesis.contains("weakest dimension"))
        assertTrue(proposal.rationale.contains("Weakest:"))
    }

    @Test
    fun `no insights and no grades produce no proposals`() {
        assertTrue(ExperimentPlanner.propose(emptyList(), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `every proposal has a unique id and a timestamp`() {
        val insights = listOf(
            insight("gap", ResearchInsight.InsightType.GAP),
            insight("break", ResearchInsight.InsightType.BREAKTHROUGH)
        )
        val proposals = ExperimentPlanner.propose(emptyList(), insights, listOf(paper))
        assertEquals(proposals.size, proposals.map { it.id }.toSet().size)
        for (proposal in proposals) {
            assertTrue(proposal.timestampUtc.isNotEmpty())
            assertTrue(proposal.estimatedImpact > 0.0)
        }
    }
}

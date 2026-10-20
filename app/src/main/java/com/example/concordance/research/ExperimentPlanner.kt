package com.example.concordance.research

import java.util.UUID

/**
 * Proposes next experiments based on graded papers and insights.
 * Pure, side-effect-free. The learning system consumes proposals to guide
 * the next round of experiments.
 */
object ExperimentPlanner {

    /** Generate experiment proposals from grades and insights. */
    fun propose(
        grades: List<PaperGrade>,
        insights: List<ResearchInsight>,
        papers: List<ResearchPaper>
    ): List<ExperimentProposal> {
        val proposals = mutableListOf<ExperimentProposal>()
        val now = com.example.concordance.integration.api.ProvenanceRecorder.nowUtc()

        // 1. From research gaps: propose experiments targeting weak dimensions
        val gapInsights = insights.filter { it.type == ResearchInsight.InsightType.GAP }
        for (gap in gapInsights) {
            proposals.add(
                ExperimentProposal(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    title = "Address: ${gap.title}",
                    hypothesis = "Improving ${gap.title.lowercase()} will raise composite grade.",
                    rationale = gap.description,
                    priority = ExperimentProposal.Priority.HIGH,
                    estimatedImpact = 0.7,
                    basedOnPaperIds = gap.supportingPaperIds,
                    basedOnInsightIds = listOf(gap.id)
                )
            )
        }

        // 2. From breakthrough signals: propose follow-up experiments
        val breakthroughInsights = insights.filter { it.type == ResearchInsight.InsightType.BREAKTHROUGH }
        for (bi in breakthroughInsights) {
            proposals.add(
                ExperimentProposal(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    title = "Extend: ${bi.title}",
                    hypothesis = "Replicate and extend the breakthrough finding with larger cohort.",
                    rationale = bi.description,
                    priority = ExperimentProposal.Priority.CRITICAL,
                    estimatedImpact = 0.9,
                    basedOnPaperIds = bi.supportingPaperIds,
                    basedOnInsightIds = listOf(bi.id)
                )
            )
        }

        // 3. From low-scoring papers: propose refinement
        val weakPapers = grades.filter { it.composite < 55 }
        for (wp in weakPapers.take(3)) {
            proposals.add(
                ExperimentProposal(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    title = "Refine: ${wp.title}",
                    hypothesis = "Address weakest dimension (${wp.weakestDimension}) to improve grade.",
                    rationale = "Paper scored ${wp.composite}/100. Weakest: ${wp.weakestDimension} (${wp.dimensionScores[wp.weakestDimension]}).",
                    priority = ExperimentProposal.Priority.MEDIUM,
                    estimatedImpact = 0.5,
                    basedOnPaperIds = listOf(wp.paperId),
                    basedOnInsightIds = emptyList()
                )
            )
        }

        // 4. From contradictions: propose reconciliation experiment
        val contradictions = insights.filter { it.type == ResearchInsight.InsightType.CONTRADICTION }
        for (c in contradictions) {
            proposals.add(
                ExperimentProposal(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    title = "Reconcile: ${c.title}",
                    hypothesis = "Resolve conflicting verdicts with additional evidence.",
                    rationale = c.description,
                    priority = ExperimentProposal.Priority.HIGH,
                    estimatedImpact = 0.8,
                    basedOnPaperIds = c.supportingPaperIds,
                    basedOnInsightIds = listOf(c.id)
                )
            )
        }

        // 5. Optimization opportunities
        val optInsights = insights.filter { it.type == ResearchInsight.InsightType.OPTIMIZATION }
        for (o in optInsights) {
            proposals.add(
                ExperimentProposal(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    title = "Optimize: ${o.title}",
                    hypothesis = "Improve framing/validation to increase impact.",
                    rationale = o.description,
                    priority = ExperimentProposal.Priority.MEDIUM,
                    estimatedImpact = 0.6,
                    basedOnPaperIds = o.supportingPaperIds,
                    basedOnInsightIds = listOf(o.id)
                )
            )
        }

        return proposals.sortedByDescending { it.priority.weight * it.estimatedImpact }
    }
}

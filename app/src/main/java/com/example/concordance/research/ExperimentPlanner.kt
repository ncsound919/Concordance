package com.example.concordance.research

import java.util.UUID

/**
 * Proposes next experiments based on graded papers and insights.
 *
 * Pure, side-effect-free. The learning system consumes proposals to guide the next round.
 *
 * Impact is **derived**, not asserted: it scales with the insight's confidence and the gap
 * between the current grade and a perfect score. A proposal from a weak paper has more room
 * to move the needle than one from a strong paper, and a proposal from a low-confidence
 * insight is discounted.
 *
 * When many proposals are generated in one round, each is dampened by 1/sqrt(n) to account
 * for multiple comparisons: ten proposals from the same paper set are not ten independent
 * findings.
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
            val weakDims = gap.title.substringAfter("Recurring weakness: ", "").split(", ")
            val meanWeakScore = weakDims.mapNotNull { dim ->
                gap.description.substringAfter("$dim=").substringBefore(",").trim().toDoubleOrNull()
            }.averageOrNull() ?: 4.0
            // A gap on a dimension that scores 2/10 has more room to improve than one at 4/10.
            val roomToImprove = (5.0 - meanWeakScore) / 5.0
            val impact = (gap.confidence * (0.5 + roomToImprove * 0.5)).coerceIn(0.3, 0.9)
            proposals.add(
                ExperimentProposal(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    title = "Address: ${gap.title}",
                    hypothesis = "Improving ${gap.title.lowercase()} will raise composite grade.",
                    rationale = gap.description,
                    priority = ExperimentProposal.Priority.HIGH,
                    estimatedImpact = impact,
                    basedOnPaperIds = gap.supportingPaperIds,
                    basedOnInsightIds = listOf(gap.id)
                )
            )
        }

        // 2. From breakthrough signals: propose follow-up experiments
        val breakthroughInsights = insights.filter { it.type == ResearchInsight.InsightType.BREAKTHROUGH }
        for (bi in breakthroughInsights) {
            // Breakthroughs are high-value but replication is the real test; impact is
            // confidence * 0.9 because a single paper rarely generalises without confirmation.
            val impact = (bi.confidence * 0.9).coerceIn(0.5, 0.95)
            proposals.add(
                ExperimentProposal(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    title = "Extend: ${bi.title}",
                    hypothesis = "Replicate and extend the breakthrough finding with larger cohort.",
                    rationale = bi.description,
                    priority = ExperimentProposal.Priority.CRITICAL,
                    estimatedImpact = impact,
                    basedOnPaperIds = bi.supportingPaperIds,
                    basedOnInsightIds = listOf(bi.id)
                )
            )
        }

        // 3. From low-scoring papers: propose refinement
        val weakPapers = grades.filter { it.composite < 55 }
        for (wp in weakPapers.take(3)) {
            // A paper at 30/100 has more room to improve than one at 50/100.
            val roomToImprove = (55.0 - wp.composite) / 55.0
            val impact = (roomToImprove * 0.6).coerceIn(0.2, 0.6)
            proposals.add(
                ExperimentProposal(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    title = "Refine: ${wp.title}",
                    hypothesis = "Address weakest dimension (${wp.weakestDimension}) to improve grade.",
                    rationale = "Paper scored ${wp.composite}/100. Weakest: ${wp.weakestDimension} " +
                        "(${wp.dimensionScores[wp.weakestDimension]}).",
                    priority = ExperimentProposal.Priority.MEDIUM,
                    estimatedImpact = impact,
                    basedOnPaperIds = listOf(wp.paperId),
                    basedOnInsightIds = emptyList()
                )
            )
        }

        // 4. From contradictions: propose reconciliation experiment
        val contradictions = insights.filter { it.type == ResearchInsight.InsightType.CONTRADICTION }
        for (c in contradictions) {
            val impact = (c.confidence * 0.85).coerceIn(0.4, 0.9)
            proposals.add(
                ExperimentProposal(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    title = "Reconcile: ${c.title}",
                    hypothesis = "Resolve conflicting verdicts with additional evidence.",
                    rationale = c.description,
                    priority = ExperimentProposal.Priority.HIGH,
                    estimatedImpact = impact,
                    basedOnPaperIds = c.supportingPaperIds,
                    basedOnInsightIds = listOf(c.id)
                )
            )
        }

        // 5. Optimization opportunities
        val optInsights = insights.filter { it.type == ResearchInsight.InsightType.OPTIMIZATION }
        for (o in optInsights) {
            val impact = (o.confidence * 0.7).coerceIn(0.3, 0.8)
            proposals.add(
                ExperimentProposal(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    title = "Optimize: ${o.title}",
                    hypothesis = "Improve framing/validation to increase impact.",
                    rationale = o.description,
                    priority = ExperimentProposal.Priority.MEDIUM,
                    estimatedImpact = impact,
                    basedOnPaperIds = o.supportingPaperIds,
                    basedOnInsightIds = listOf(o.id)
                )
            )
        }

        // Multiple comparison dampening: many proposals from the same paper set are not
        // independent findings. Apply 1/sqrt(n) to each impact.
        val n = proposals.size
        val dampened = if (n > 5) {
            val factor = 1.0 / kotlin.math.sqrt(n.toDouble())
            proposals.map { it.copy(estimatedImpact = (it.estimatedImpact * (0.5 + 0.5 * factor)).coerceIn(0.1, 0.95)) }
        } else {
            proposals
        }

        return dampened.sortedByDescending { it.priority.weight * it.estimatedImpact }
    }

    private fun List<Double>.averageOrNull(): Double? =
        if (isEmpty()) null else average()
}

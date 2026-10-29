package com.example.concordance.research

import com.example.concordance.learning.ModelRevision
import java.util.UUID
import kotlin.math.abs

/**
 * Analyzes graded papers to produce research insights and trends.
 *
 * Confidence values are **derived** from the consistency and strength of the signal, not
 * hard-coded constants. A flat 0.9 on every breakthrough would make the ranker unable to
 * distinguish a single borderline paper from a cluster of strong ones.
 */
object InsightsAnalyzer {

    /** Detect trends from a set of graded papers. */
    fun analyzeTrends(grades: List<PaperGrade>, papers: List<ResearchPaper>): List<ResearchInsight> {
        if (grades.isEmpty()) return emptyList()
        val insights = mutableListOf<ResearchInsight>()
        val now = com.example.concordance.integration.api.ProvenanceRecorder.nowUtc()

        // 1. Breakthrough signals: papers with composite >= 85
        val breakthroughs = grades.filter { it.composite >= 85 }
        if (breakthroughs.isNotEmpty()) {
            val meanComposite = breakthroughs.map { it.composite }.average()
            val confidence = ((meanComposite - 85.0) / 15.0).coerceIn(0.5, 0.95)
            insights.add(
                ResearchInsight(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    type = ResearchInsight.InsightType.BREAKTHROUGH,
                    title = "Breakthrough-grade papers detected",
                    description = "${breakthroughs.size} paper(s) scored >= 85/100: " +
                        breakthroughs.joinToString { "${it.title} (${it.composite})" },
                    supportingPaperIds = breakthroughs.map { it.paperId },
                    confidence = confidence,
                    actionable = true
                )
            )
        }

        // 2. Research gaps: dimensions consistently scoring low
        val avgByDim = grades.flatMap { it.dimensionScores.entries }.groupBy({ it.key }, { it.value })
            .mapValues { (_, vals) -> vals.average() }
        val weakDims = avgByDim.filter { it.value < 5.0 }
        if (weakDims.isNotEmpty()) {
            val meanWeakScore = weakDims.values.average()
            val confidence = (1.0 - meanWeakScore / 10.0).coerceIn(0.4, 0.9)
            insights.add(
                ResearchInsight(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    type = ResearchInsight.InsightType.GAP,
                    title = "Recurring weakness: ${weakDims.keys.joinToString()}",
                    description = "Dimensions scoring below 5/10 on average: " +
                        weakDims.entries.joinToString { "${it.key}=${"%.1f".format(it.value)}" },
                    supportingPaperIds = grades.map { it.paperId },
                    confidence = confidence,
                    actionable = true
                )
            )
        }

        // 3. Trends: improving or declining composite over time
        if (grades.size >= 2) {
            val sorted = grades.sortedBy { it.timestampUtc }
            val first = sorted.first().composite
            val last = sorted.last().composite
            val trend = if (last > first) "improving" else if (last < first) "declining" else "stable"
            val magnitude = abs(last - first).toDouble() / 100.0
            val confidence = (0.5 + magnitude).coerceIn(0.5, 0.9)
            insights.add(
                ResearchInsight(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    type = ResearchInsight.InsightType.TREND,
                    title = "Grade trend: $trend",
                    description = "Composite grade moved from $first to $last over ${grades.size} papers.",
                    supportingPaperIds = grades.map { it.paperId },
                    confidence = confidence,
                    actionable = trend == "declining"
                )
            )
        }

        // 4. Contradictions: papers with conflicting verdicts
        val verdicts = papers.mapNotNull { it.verdict }.distinct()
        if (verdicts.size > 1) {
            val confidence = (0.7 + (verdicts.size - 2) * 0.05).coerceIn(0.7, 0.9)
            insights.add(
                ResearchInsight(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    type = ResearchInsight.InsightType.CONTRADICTION,
                    title = "Conflicting verdicts across papers",
                    description = "Papers report different verdicts: ${verdicts.joinToString()}. Requires reconciliation.",
                    supportingPaperIds = papers.map { it.id },
                    confidence = confidence,
                    actionable = true
                )
            )
        }

        // 5. Optimization opportunities: papers with high innovation but low impact
        val optPapers = grades.filter { it.innovation >= 7 && it.impactPotential < 5 }
        if (optPapers.isNotEmpty()) {
            val gap = optPapers.map { it.innovation - it.impactPotential }.average()
            val confidence = (0.6 + gap / 20.0).coerceIn(0.6, 0.85)
            insights.add(
                ResearchInsight(
                    id = UUID.randomUUID().toString(),
                    timestampUtc = now,
                    type = ResearchInsight.InsightType.OPTIMIZATION,
                    title = "High innovation, low impact: ${optPapers.size} paper(s)",
                    description = "Papers with strong innovation but low impact potential need better framing or broader validation.",
                    supportingPaperIds = optPapers.map { it.paperId },
                    confidence = confidence,
                    actionable = true
                )
            )
        }

        return insights
    }

    /** Compute the overall breakthrough score (0-1) from grades. */
    fun breakthroughScore(grades: List<PaperGrade>): Double {
        if (grades.isEmpty()) return 0.0
        return grades.map { it.breakthroughPotential }.average() / 10.0
    }

    /** Identify the most common weakness across papers. */
    fun topWeakness(grades: List<PaperGrade>): String {
        if (grades.isEmpty()) return "No data"
        return grades.groupingBy { it.weakestDimension }.eachCount()
            .maxByOrNull { it.value }?.key ?: "Unknown"
    }
}
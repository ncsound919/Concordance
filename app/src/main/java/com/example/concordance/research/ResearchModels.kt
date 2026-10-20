package com.example.concordance.research

import com.example.concordance.model.Anchor
import com.example.concordance.model.Capsule
import com.example.concordance.model.Verdict

/**
 * A 10-point grading system for research papers / experiment results.
 *
 * Each dimension is scored 0-10. The composite grade (0-100) feeds back into
 * the recursive learning system so the agent knows which experiments push the
 * needle toward breakthrough and which need refinement.
 */
data class PaperGrade(
    val paperId: String,
    val title: String,
    val timestampUtc: String,
    // --- 10 grading dimensions (each 0-10) ---
    /** How novel is the finding relative to existing literature. */
    val novelty: Int,
    /** Quality of experimental design and controls. */
    val methodologicalRigor: Int,
    /** Strength of statistical evidence (p-values, CIs, effect sizes). */
    val statisticalSignificance: Int,
    /** Can an independent lab reproduce the result. */
    val reproducibility: Int,
    /** Does the finding change clinical or research practice. */
    val impactPotential: Int,
    /** Does it apply beyond the specific cohort studied. */
    val generalizability: Int,
    /** Quality and completeness of underlying data. */
    val dataQuality: Int,
    /** Does it introduce new methods or frameworks. */
    val innovation: Int,
    /** How much it advances the field toward breakthrough. */
    val breakthroughPotential: Int,
    /** Challenge resolution: does the result survive human challenge. */
    val challengeSurvival: Int
) {
    val composite: Int
        get() = novelty + methodologicalRigor + statisticalSignificance + reproducibility +
            impactPotential + generalizability + dataQuality + innovation +
            breakthroughPotential + challengeSurvival

    val compositeNormalized: Double get() = composite / 100.0

    val grade: String
        get() = when {
            composite >= 85 -> "A (Breakthrough-grade)"
            composite >= 70 -> "B (Strong)"
            composite >= 55 -> "C (Moderate)"
            composite >= 40 -> "D (Weak)"
            else -> "F (Insufficient)"
        }

    val dimensionScores: Map<String, Int>
        get() = mapOf(
            "Novelty" to novelty,
            "Methodological Rigor" to methodologicalRigor,
            "Statistical Significance" to statisticalSignificance,
            "Reproducibility" to reproducibility,
            "Impact Potential" to impactPotential,
            "Generalizability" to generalizability,
            "Data Quality" to dataQuality,
            "Innovation" to innovation,
            "Breakthrough Potential" to breakthroughPotential,
            "Challenge Survival" to challengeSurvival
        )

    /** Weakest dimension (for improvement targeting). */
    val weakestDimension: String
        get() = dimensionScores.minByOrNull { it.value }?.key ?: "Unknown"

    /** Strongest dimension. */
    val strongestDimension: String
        get() = dimensionScores.maxByOrNull { it.value }?.key ?: "Unknown"
}

/**
 * Research paper / experiment artifact that gets graded.
 */
data class ResearchPaper(
    val id: String,
    val title: String,
    val timestampUtc: String,
    val abstract: String,
    val anchorIds: List<String>,
    val capsuleIds: List<String>,
    val challengeCount: Int,
    val verdict: Verdict?,
    val rNull: Double?,
    val rSignificance: Double?,
    val modelRevisionId: String?,
    val keywords: List<String> = emptyList()
)

/**
 * An insight/trend derived from graded papers.
 */
data class ResearchInsight(
    val id: String,
    val timestampUtc: String,
    val type: InsightType,
    val title: String,
    val description: String,
    val supportingPaperIds: List<String>,
    val confidence: Double,
    val actionable: Boolean
) {
    enum class InsightType(val label: String) {
        TREND("Trend"),
        GAP("Research Gap"),
        BREAKTHROUGH("Breakthrough Signal"),
        CONTRADICTION("Contradiction"),
        OPTIMIZATION("Optimization Opportunity")
    }
}

/**
 * A proposed next experiment derived from insights and grades.
 */
data class ExperimentProposal(
    val id: String,
    val timestampUtc: String,
    val title: String,
    val hypothesis: String,
    val rationale: String,
    val priority: Priority,
    val estimatedImpact: Double,
    val basedOnPaperIds: List<String>,
    val basedOnInsightIds: List<String>,
    val suggestedParameters: Map<String, String> = emptyMap()
) {
    enum class Priority(val label: String, val weight: Int) {
        CRITICAL("Critical", 4),
        HIGH("High", 3),
        MEDIUM("Medium", 2),
        LOW("Low", 1)
    }
}

/**
 * Deep research report: grades + insights + experiment proposals.
 */
data class DeepResearchReport(
    val timestampUtc: String,
    val papersGraded: Int,
    val grades: List<PaperGrade>,
    val insights: List<ResearchInsight>,
    val experimentProposals: List<ExperimentProposal>,
    val overallBreakthroughScore: Double,
    val topWeakness: String,
    val summary: String
)

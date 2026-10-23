package com.example.concordance.learning

import com.example.concordance.engine.StatisticalEngine
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import com.example.concordance.model.Verdict

/**
 * Tunable parameters of Concordance's detection-correction model.
 *
 * Only parameters that are genuinely free knobs are exposed here; derived
 * quantities (rNull, rSignificance) are recomputed by the engine. Keeping the
 * surface small is what makes the search bounded and auditable (Phase 0).
 */
data class ModelParameters(
    /** Assumed relative sensitivity of brain-lesion assays vs. non-CNS assays. */
    val sn: Double = 0.90,
    /** Reference (non-CNS) prevalence used for abstract-only / summary anchors. */
    val referencePrevalence: Double = 0.2075,
    /** Two-sided z for Wilson / delta-method intervals. */
    val z95: Double = StatisticalEngine.Z95
) {
    fun inBounds(): Boolean =
        sn in 0.01..1.0 &&
            referencePrevalence in 0.01..0.99 &&
            z95 in 1.0..3.0
}

/** A persisted, append-only model revision. */
data class ModelRevision(
    val id: String,
    val parentRevisionId: String?,
    val createdAtUtc: String,
    val parameters: ModelParameters,
    val metricE: Double,
    val promotedBy: String? = null,
    val notes: String? = null
) {
    val isPromoted: Boolean get() = promotedBy != null
}

/** Corrected counts supplied by a human-authored challenge. */
data class ChallengeLabel(
    val anchorId: String,
    val updatedBrainEvents: Int,
    val updatedBrainTotal: Int,
    val updatedOtherEvents: Int,
    val updatedOtherTotal: Int
)

/** One evaluation case: an anchor, its baseline verdict, and any challenges. */
data class EvaluationCase(
    val anchor: Anchor,
    val baselineVerdict: Verdict,
    val challenges: List<ChallengeLabel> = emptyList(),
    val heldOut: Boolean = false
)

/** Decomposed evaluation result. Lower [total] is better. */
data class EvaluationResult(
    val verdictStabilityPenalty: Double,
    val challengeResolutionMissRate: Double,
    val calibrationError: Double,
    val parsimony: Double,
    val total: Double,
    val casesEvaluated: Int
)

/** A dry-run report produced by [LearningController]. */
data class DryRunReport(
    val candidate: ModelParameters,
    val baseline: ModelParameters,
    val candidateResult: EvaluationResult,
    val baselineResult: EvaluationResult,
    val improved: Boolean,
    val promoted: Boolean = false,
    val notes: String
)

/** Outcome recorded for every learning decision (plan §6.5). */
enum class LearningDecision { PROPOSED, PROMOTED, REJECTED, REVERTED, READJUDICATED }

/** Append-only audit entry for one learning decision. */
data class LearningAudit(
    val pk: Long = 0,
    val candidate: ModelParameters,
    val baseline: ModelParameters,
    val result: EvaluationResult,
    val decision: LearningDecision,
    val approver: String?,
    val revisionId: String?,
    val createdAtUtc: String,
    val notes: String
)

package com.example.concordance.learning

import com.example.concordance.engine.StatisticalEngine
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import com.example.concordance.model.Verdict

/**
 * Pure, side-effect-free evaluation of a candidate [ModelParameters] against a set
 * of [EvaluationCase]s. This is the automated evaluation function required by the
 * recursive self-learning plan (Phase 0): no learning happens here, only scoring.
 */
object LearningEvaluator {

    /** Canonical sensitivity range used when an anchor carries none of its own. */
    const val DEFAULT_R_LOW = 0.30
    const val DEFAULT_R_HIGH = 0.90

    /**
     * Adjudicate [anchor] under [params]. Returns the resulting [Verdict], or null
     * if the engine could not produce one.
     *
     * Fail-closed: an anchor the correction cannot estimate (zero-event group, 100%-event
     * group, half a 2x2 table, summary OR missing its interval or prevalence) yields null,
     * never a WITHHELD verdict. Callers must skip such an anchor rather than score it — a
     * verdict that is fixed by bad inputs can only pollute a metric.
     */
    fun adjudicate(anchor: Anchor, params: ModelParameters): Verdict? {
        if (!params.inBounds()) return null
        if (anchor.validationError() != null) return null
        val adjusted = if (anchor.hasCounts) {
            anchor
        } else {
            anchor.copy(referencePrevalence = params.referencePrevalence)
        }
        return try {
            StatisticalEngine.adjudicate(
                anchor = adjusted,
                sn = params.sn,
                rLow = DEFAULT_R_LOW,
                rHigh = DEFAULT_R_HIGH
            ).verdict
        } catch (e: Exception) {
            null
        }
    }

    /** The anchor a challenge claims, assembled from its corrected 2x2 counts. */
    fun challengeAnchor(challenge: ChallengeLabel): Anchor = Anchor(
        id = challenge.anchorId,
        label = "challenge-corrected",
        source = "challenge",
        evidenceGrade = EvidenceGrade.COUNTS,
        brainEvents = challenge.updatedBrainEvents,
        brainTotal = challenge.updatedBrainTotal,
        otherEvents = challenge.updatedOtherEvents,
        otherTotal = challenge.updatedOtherTotal
    )

    /**
     * Null when the corrected counts a challenge supplies can be estimated, otherwise the
     * reason they cannot. A human labelling a correction with zero events has not produced
     * a label the loop may score.
     */
    fun challengeError(challenge: ChallengeLabel): String? =
        challengeAnchor(challenge).validationError()

    /**
     * Implied verdict for a challenge: rebuild the anchor from the corrected
     * counts and adjudicate it. A challenge "resolves" if the candidate's
     * verdict on the *original* anchor matches this implied verdict. Returns null
     * when the challenge's own counts are not estimable.
     */
    fun impliedVerdict(challenge: ChallengeLabel, params: ModelParameters): Verdict? {
        if (challengeError(challenge) != null) return null
        return adjudicate(challengeAnchor(challenge), params)
    }

    /**
     * Score [candidate] against [cases], using [baseline] as the reference for
     * verdict stability and parsimony. Lower [EvaluationResult.total] is better.
     */
    fun evaluate(
        cases: List<EvaluationCase>,
        candidate: ModelParameters,
        baseline: ModelParameters
    ): EvaluationResult {
        if (cases.isEmpty()) {
            return EvaluationResult(0.0, 0.0, 0.0, 0.0, 0.0, 0)
        }
        if (!candidate.inBounds()) {
            // Out-of-bounds parameters have no meaningful score. Return the worst possible
            // total so no promotion rule can read a rejected candidate as an improvement.
            return EvaluationResult(1.0, 1.0, 1.0, 0.0, 1.0, 0)
        }

        // A verdict fixed by protocol (an abstract-only source is withheld whatever the
        // parameters) cannot move with any candidate and cannot be matched by a counts-only
        // challenge. Such anchors stay in the training set for audit, but scoring them would
        // invent stability and miss-rate data points that carry no information. An anchor the
        // correction cannot estimate is never scored either, whatever baseline verdict the
        // caller handed in.
        val scorable = cases.filter {
            it.baselineVerdict != Verdict.WITHHELD && it.anchor.validationError() == null
        }

        // 1. Verdict stability: fraction of unchallenged cases whose verdict
        //    flipped relative to the baseline verdict (lower is better).
        val unchallenged = scorable.filter { it.challenges.isEmpty() }
        val stabilityFlips = unchallenged.count { c ->
            val v = adjudicate(c.anchor, candidate)
            v != null && v != c.baselineVerdict
        }
        val stabilityPenalty = if (unchallenged.isEmpty()) 0.0
        else stabilityFlips.toDouble() / unchallenged.size

        // 2. Challenge resolution: fraction of challenges whose implied verdict
        //    matches the candidate's verdict on the original anchor.
        var resolved = 0
        var totalChallenges = 0
        for (c in scorable) {
            val candVerdict = adjudicate(c.anchor, candidate) ?: continue
            for (ch in c.challenges) {
                // Never counted as a miss when the correction has no verdict of its own.
                val implied = impliedVerdict(ch, candidate) ?: continue
                totalChallenges++
                if (implied == candVerdict) resolved++
            }
        }
        val resolutionRate = if (totalChallenges == 0) 1.0
        else resolved.toDouble() / totalChallenges
        val resolutionMiss = 1.0 - resolutionRate

        // 3. Calibration: |predicted P(artifact) - empirical P(artifact)|.
        val predictedArtifact = scorable.mapNotNull { adjudicate(it.anchor, candidate) }
            .count { it == Verdict.NOT_SUPPORTED }
            .toDouble() / scorable.size.coerceAtLeast(1)
        val empiricalArtifact = if (totalChallenges == 0) predictedArtifact else {
            var impliedArtifact = 0
            for (c in scorable) for (ch in c.challenges) {
                if (impliedVerdict(ch, candidate) == Verdict.NOT_SUPPORTED) impliedArtifact++
            }
            impliedArtifact.toDouble() / totalChallenges
        }
        val calibrationError = kotlin.math.abs(predictedArtifact - empiricalArtifact)

        // 4. Parsimony: L1 distance from baseline parameters.
        val parsimony = kotlin.math.abs(candidate.sn - baseline.sn) +
            kotlin.math.abs(candidate.referencePrevalence - baseline.referencePrevalence) +
            kotlin.math.abs(candidate.z95 - baseline.z95)

        val total = 0.3 * stabilityPenalty +
            0.4 * resolutionMiss +
            0.2 * calibrationError +
            0.1 * parsimony.coerceAtMost(1.0)

        return EvaluationResult(
            verdictStabilityPenalty = stabilityPenalty,
            challengeResolutionMissRate = resolutionMiss,
            calibrationError = calibrationError,
            parsimony = parsimony,
            total = total,
            casesEvaluated = scorable.size
        )
    }
}

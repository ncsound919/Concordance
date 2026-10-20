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
     */
    fun adjudicate(anchor: Anchor, params: ModelParameters): Verdict? {
        if (!params.inBounds()) return null
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

    /**
     * Implied verdict for a challenge: rebuild the anchor from the corrected
     * counts and adjudicate it. A challenge "resolves" if the candidate's
     * verdict on the *original* anchor matches this implied verdict.
     */
    fun impliedVerdict(challenge: ChallengeLabel, params: ModelParameters): Verdict? {
        val anchor = Anchor(
            id = challenge.anchorId,
            label = "challenge-corrected",
            source = "challenge",
            evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = challenge.updatedBrainEvents,
            brainTotal = challenge.updatedBrainTotal,
            otherEvents = challenge.updatedOtherEvents,
            otherTotal = challenge.updatedOtherTotal
        )
        return adjudicate(anchor, params)
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

        // 1. Verdict stability: fraction of unchallenged cases whose verdict
        //    flipped relative to the baseline verdict (lower is better).
        val unchallenged = cases.filter { it.challenges.isEmpty() }
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
        for (c in cases) {
            val candVerdict = adjudicate(c.anchor, candidate) ?: continue
            for (ch in c.challenges) {
                totalChallenges++
                val implied = impliedVerdict(ch, candidate)
                if (implied != null && implied == candVerdict) resolved++
            }
        }
        val resolutionRate = if (totalChallenges == 0) 1.0
        else resolved.toDouble() / totalChallenges
        val resolutionMiss = 1.0 - resolutionRate

        // 3. Calibration: |predicted P(artifact) - empirical P(artifact)|.
        val predictedArtifact = cases.mapNotNull { adjudicate(it.anchor, candidate) }
            .count { it == Verdict.NOT_SUPPORTED }
            .toDouble() / cases.size.coerceAtLeast(1)
        val empiricalArtifact = if (totalChallenges == 0) predictedArtifact else {
            var impliedArtifact = 0
            for (c in cases) for (ch in c.challenges) {
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
            casesEvaluated = cases.size
        )
    }
}

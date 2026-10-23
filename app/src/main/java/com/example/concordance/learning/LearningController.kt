package com.example.concordance.learning

import com.example.concordance.model.Anchor

/**
 * Dry-run orchestrator for one learning round (Phase 0/1 skeleton).
 *
 * It never mutates state and never publishes a revision. It returns a
 * [DryRunReport] describing what *would* happen, so a human can decide whether
 * to proceed. Promotion is intentionally out of scope here.
 */
object LearningController {

    /** Improvement threshold (epsilon) from the plan's promotion rule. */
    const val EPSILON = 1e-4
    const val STABILITY_THRESHOLD = 0.25

    /**
     * Run a dry round: score [candidate] against [cases] using [baseline] as
     * the reference. Returns a report; [DryRunReport.promoted] is always false
     * in dry-run mode.
     */
    fun dryRun(
        cases: List<EvaluationCase>,
        candidate: ModelParameters,
        baseline: ModelParameters,
        stabilityThreshold: Double = STABILITY_THRESHOLD
    ): DryRunReport {
        val candResult = LearningEvaluator.evaluate(cases, candidate, baseline)
        val baseResult = LearningEvaluator.evaluate(cases, baseline, baseline)

        val improved = candResult.total < baseResult.total - EPSILON

        // Safety: a candidate that flips > stabilityThreshold of unchallenged verdicts
        // is rejected regardless of metric (stability guard).
        val stabilityOk = candResult.verdictStabilityPenalty <= stabilityThreshold

        val actuallyImproved = improved && stabilityOk && candidate.inBounds()

        val notes = buildString {
            append("candidate E=${"%.4f".format(candResult.total)} vs baseline E=${"%.4f".format(baseResult.total)}; ")
            append("stabilityPenalty=${"%.3f".format(candResult.verdictStabilityPenalty)}; ")
            append("resolutionMiss=${"%.3f".format(candResult.challengeResolutionMissRate)}; ")
            append("calibration=${"%.3f".format(candResult.calibrationError)}; ")
            if (!candidate.inBounds()) append("REJECTED: candidate out of bounds. ")
            if (!stabilityOk) append("REJECTED: stability guard tripped. ")
            if (actuallyImproved) append("WOULD PROMOTE (pending human approval).")
            else append("No promotion.")
        }

        return DryRunReport(
            candidate = candidate,
            baseline = baseline,
            candidateResult = candResult,
            baselineResult = baseResult,
            improved = actuallyImproved,
            promoted = false,
            notes = notes
        )
    }

    /** Convenience: build a small candidate grid around [baseline]. */
    fun candidateGrid(
        baseline: ModelParameters,
        snSteps: Int = 5,
        refSteps: Int = 5
    ): List<ModelParameters> {
        val out = mutableListOf<ModelParameters>()
        val snVals = (0..snSteps).map { baseline.sn + (it - snSteps / 2) * 0.02 }
        val refVals = (0..refSteps).map { baseline.referencePrevalence + (it - refSteps / 2) * 0.02 }
        for (s in snVals) for (r in refVals) {
            val c = ModelParameters(sn = s, referencePrevalence = r)
            if (c.inBounds()) out.add(c)
        }
        return out.distinct()
    }
}

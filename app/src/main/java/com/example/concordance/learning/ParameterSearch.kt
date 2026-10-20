package com.example.concordance.learning

import com.example.concordance.engine.StatisticalEngine

/**
 * Bounded parameter search for one learning round.
 *
 * The search space is deliberately small and declared up front (plan §6.3).
 * `z95` is NOT searched: widening or narrowing the confidence interval is a
 * statistical-integrity decision, not something an evaluation metric should be
 * allowed to buy.
 */
object ParameterSearch {

    /** Declared bounds. Candidates outside these are never generated. */
    const val SN_MIN = 0.50
    const val SN_MAX = 1.00
    const val SN_STEP = 0.05

    const val REF_MIN = 0.10
    const val REF_MAX = 0.30
    const val REF_STEP = 0.02

    data class CandidateScore(
        val parameters: ModelParameters,
        val result: EvaluationResult
    )

    /** Generate the bounded candidate grid. */
    fun grid(baseline: ModelParameters): List<ModelParameters> {
        val out = mutableListOf<ModelParameters>()
        var sn = SN_MIN
        while (sn <= SN_MAX + 1e-9) {
            var ref = REF_MIN
            while (ref <= REF_MAX + 1e-9) {
                val c = ModelParameters(
                    sn = round3(sn),
                    referencePrevalence = round3(ref),
                    z95 = StatisticalEngine.Z95
                )
                if (c.inBounds()) out.add(c)
                ref += REF_STEP
            }
            sn += SN_STEP
        }
        return out
    }

    /** Score every candidate; results sorted ascending by total (best first). */
    fun search(
        cases: List<EvaluationCase>,
        candidates: List<ModelParameters>,
        baseline: ModelParameters
    ): List<CandidateScore> {
        return candidates
            .map { CandidateScore(it, LearningEvaluator.evaluate(cases, it, baseline)) }
            .sortedBy { it.result.total }
    }

    /** Score the bounded grid and return the single best candidate. */
    fun best(
        cases: List<EvaluationCase>,
        baseline: ModelParameters
    ): CandidateScore? = search(cases, grid(baseline), baseline).firstOrNull()

    private fun round3(v: Double): Double = Math.round(v * 1000.0) / 1000.0
}
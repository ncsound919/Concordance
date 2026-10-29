package com.example.concordance.research.power

import kotlin.math.*

/**
 * Two-proportion power analysis for paired concordance studies.
 *
 * This is the calculation that should gate every confirmatory experiment: an experiment
 * whose sample size cannot achieve the declared [powerTarget] at the declared [alpha] is not
 * confirmatory, it is exploratory, and the adjudicator must refuse to let it update a claim.
 *
 * Formula (two-sided, normal approximation, equal allocation):
 *
 *   n = (Z_alpha/2 + Z_beta)^2 * (p1*(1-p1) + p2*(1-p2)) / (p1 - p2)^2
 *
 * where p1, p2 are the expected proportions in the two groups, alpha is the significance level,
 * and beta = 1 - power. The result is the per-group sample size; the total is 2*n.
 *
 * Reference: Fleiss, Levin, Paik "Statistical Methods for Rates and Proportions" (2003), ch. 3.
 * The implementation uses the inverse normal CDF via the Abramowitz & Stegun approximation.
 */
object PowerAnalysis {

    data class Result(
        val perGroup: Int,
        val total: Int,
        val effectSize: Double,
        val alpha: Double,
        val power: Double,
        val feasible: Boolean,
    )

    /**
     * Minimum per-group sample size to detect a difference between [p1] and [p2] with [power]
     * at significance [alpha] (two-sided).
     *
     * Returns null if the inputs are invalid (proportions out of (0,1), equal proportions, or
     * alpha/power out of (0,1)).
     */
    fun requiredSampleSize(
        p1: Double,
        p2: Double,
        alpha: Double = 0.05,
        power: Double = 0.80,
    ): Result? {
        if (p1 <= 0.0 || p1 >= 1.0 || p2 <= 0.0 || p2 >= 1.0) return null
        if (alpha <= 0.0 || alpha >= 1.0 || power <= 0.0 || power >= 1.0) return null
        val delta = abs(p1 - p2)
        if (delta < 1e-12) return null

        val zAlpha = inverseNormalCdf(1.0 - alpha / 2.0)
        val zBeta = inverseNormalCdf(power)
        val variance = p1 * (1 - p1) + p2 * (1 - p2)
        val n = ((zAlpha + zBeta).pow(2) * variance) / (delta * delta)
        val perGroup = ceil(n).toInt().coerceAtLeast(2)

        return Result(
            perGroup = perGroup,
            total = perGroup * 2,
            effectSize = delta,
            alpha = alpha,
            power = power,
            feasible = true,
        )
    }

    /**
     * Achieved power for a given sample size, using the normal approximation.
     *
     * Returns null if the inputs are invalid.
     */
    fun achievedPower(
        p1: Double,
        p2: Double,
        nPerGroup: Int,
        alpha: Double = 0.05,
    ): Double? {
        if (p1 <= 0.0 || p1 >= 1.0 || p2 <= 0.0 || p2 >= 1.0) return null
        if (nPerGroup < 2 || alpha <= 0.0 || alpha >= 1.0) return null
        val delta = abs(p1 - p2)
        if (delta < 1e-12) return null
        val variance = p1 * (1 - p1) + p2 * (1 - p2)
        val se = sqrt(variance / nPerGroup)
        val z = delta / se
        val zAlpha = inverseNormalCdf(1.0 - alpha / 2.0)
        // Power = P(Z > zAlpha - z) under the alternative.
        return 1.0 - normalCdf(zAlpha - z)
    }

    /**
     * Inverse normal CDF (quantile function) via the Abramowitz & Stegun approximation
     * (formula 26.2.23). Accuracy ~1e-4, which is ample for sample-size planning.
     */
    private fun inverseNormalCdf(p: Double): Double {
        if (p <= 0.0) return Double.NEGATIVE_INFINITY
        if (p >= 1.0) return Double.POSITIVE_INFINITY
        if (p == 0.5) return 0.0

        val a = doubleArrayOf(
            -3.969683028665376e+01,
            2.209460984245205e+02,
            -2.759285104469687e+02,
            1.383577518672690e+02,
            -3.066479806614716e+01,
            2.506628277459239e+00,
        )
        val b = doubleArrayOf(
            -5.447609879822406e+01,
            1.615858368580409e+02,
            -1.556989798598866e+02,
            6.680131188771972e+01,
            -1.328068155288572e+01,
        )
        val c = doubleArrayOf(
            -7.784894002430293e-03,
            -3.223964580411365e-01,
            -2.400758277161838e+00,
            -2.549732539343734e+00,
            -1.248296370134171e+00,
            -1.432788191719996e-02,
        )
        val d = doubleArrayOf(
            7.745450142783414e-03,
            3.224671290700398e-01,
            2.445134137142996e+00,
            3.754408661907416e+00,
        )

        val pLow = 0.02425
        val pHigh = 1 - pLow

        return if (p < pLow) {
            val q = sqrt(-2 * ln(p))
            (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
        } else if (p <= pHigh) {
            val q = p - 0.5
            val r = q * q
            (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1)
        } else {
            val q = sqrt(-2 * ln(1 - p))
            -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
        }
    }

    /** Standard normal CDF. */
    private fun normalCdf(x: Double): Double {
        val t = 1.0 / (1.0 + 0.2316419 * abs(x))
        val d = 0.3989422804014327 // 1/sqrt(2*pi)
        val prob = d * exp(-x * x / 2) * t *
            (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
        return if (x >= 0) 1 - prob else prob
    }
}

package com.example.concordance.integration.forecast

/**
 * Pure, testable forecasting helpers for Timestral.
 *
 * The heavy ONNX inference is deliberately NOT in this file: it needs the model
 * weights and the native runtime, which are operator-supplied. What lives here is
 * the deterministic scaffolding (input validation, horizon clamping, and a
 * CPU-friendly naive baseline forecast) so the wiring and the UI can be exercised
 * and tested without the model. When real weights are present the same interface
 * is satisfied by the ONNX-backed implementation.
 */
class TimestralClient(
    private val modelAvailable: () -> Boolean = { false }
) {
    /** True only when the operator has installed the Timestral weights. */
    fun isAvailable(): Boolean = modelAvailable()

    /** Clamp a requested horizon to something sane and non-negative. */
    fun clampHorizon(requested: Int): Int = requested.coerceIn(1, MAX_HORIZON)

    /** Validate a series: needs at least [MIN_POINTS] finite points. */
    fun validateSeries(series: List<Double>): String? {
        if (series.size < MIN_POINTS) return "Need at least $MIN_POINTS points to forecast."
        if (series.any { !it.isFinite() }) return "Series contains non-finite values."
        return null
    }

    /**
     * Baseline forecast used when the foundation model is unavailable or as a
     * sanity fallback. It is a simple drift-aware moving average with a widening
     * band. This is deliberately conservative and is labelled as such.
     */
    fun baselineForecast(series: List<Double>, horizon: Int): TimestralResult<TimestralForecast> {
        validateSeries(series)?.let { return TimestralResult.Unavailable(it) }
        val h = clampHorizon(horizon)
        val recent = series.takeLast(minOf(series.size, 5))
        val level = recent.average()
        val drift = if (series.size >= 2) (series.last() - series.first()) / (series.size - 1) else 0.0
        val std = stdDev(series)
        val mean = (1..h).map { i -> level + drift * i }
        val lower = (1..h).map { i -> mean[i - 1] - 1.96 * std * kotlin.math.sqrt(i.toDouble()) }
        val upper = (1..h).map { i -> mean[i - 1] + 1.96 * std * kotlin.math.sqrt(i.toDouble()) }
        return TimestralResult.Ok(
            TimestralForecast(
                horizon = h,
                mean = mean,
                lower = lower,
                upper = upper,
                disclaimer = BASELINE_DISCLAIMER
            )
        )
    }

    private fun stdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val m = values.average()
        val variance = values.map { (it - m) * (it - m) }.average()
        return kotlin.math.sqrt(variance).coerceAtLeast(1e-9)
    }

    companion object {
        const val MIN_POINTS = 4
        const val MAX_HORIZON = 30
        const val BASELINE_DISCLAIMER =
            "Baseline drift-aware moving average (Timestral weights not installed). " +
                "Forecast is descriptive, not a verdict."
    }
}

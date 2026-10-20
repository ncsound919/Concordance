package com.example.concordance.integration.forecast

/**
 * Timestral (Mistral) time-series foundation model client surface.
 *
 * Timestral is a family of small time-series foundation models (5M/28M/123M/209M)
 * that produce probabilistic, multivariate forecasts and run on CPU. In Concordance
 * it is used for sparkline forecasting of a claim's support over time and for the
 * trends engine. It is a *forecaster*, never an adjudicator: it describes drift,
 * it does not decide verdicts.
 *
 * This module is the on-device/on-host contract. The model weights are NOT bundled
 * in the APK (same policy as Kokoro): they are downloaded into app storage by the
 * operator and loaded by ONNX Runtime. When weights are absent the client reports
 * [TimestralResult.Unavailable] honestly rather than fabricating a forecast.
 */
data class TimestralPoint(val t: Double, val value: Double)

data class TimestralForecast(
    val horizon: Int,
    val mean: List<Double>,
    val lower: List<Double>,
    val upper: List<Double>,
    val disclaimer: String
)

sealed interface TimestralResult<out T> {
    data class Ok<T>(val value: T) : TimestralResult<T>
    data class Unavailable(val reason: String) : TimestralResult<Nothing>
    data class Refused(val reason: String) : TimestralResult<Nothing>
}

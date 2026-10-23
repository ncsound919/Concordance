package com.example.concordance.telemetry

import com.example.concordance.integration.aws.CloudWatchLogsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.concordance.integration.api.ProvenanceRecorder

/**
 * High-level telemetry facade: records events, analyzes them for optimization
 * suggestions, and flushes to AWS CloudWatch Logs when credentials are available.
 */
object TelemetryManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _suggestions = MutableStateFlow<List<OptimizationSuggestion>>(emptyList())
    val suggestions: StateFlow<List<OptimizationSuggestion>> = _suggestions.asStateFlow()

    private val _flushCount = MutableStateFlow(0L)
    val flushCount: StateFlow<Long> = _flushCount.asStateFlow()

    /** Record a telemetry event. */
    fun record(event: TelemetryEvent) = TelemetryCollector.record(event)

    /** Convenience: record with category and metric. */
    fun record(category: TelemetryEvent.Category, metric: String, value: Double = 1.0, unit: String = "count", tags: Map<String, String> = emptyMap()) {
        TelemetryCollector.record(TelemetryEvent(timestampUtc = com.example.concordance.integration.api.ProvenanceRecorder.nowUtc(), category = category, metric = metric, value = value, unit = unit, tags = tags))
    }

    /** Convenience recorders. */
    fun recordApiCall(provider: String, success: Boolean, latencyMs: Long) {
        TelemetryCollector.record(
            TelemetryEvent.Category.API_CALL, "api_call", if (success) 1.0 else 0.0, "count",
            mapOf("provider" to provider, "latencyMs" to latencyMs.toString())
        )
    }

    fun recordCacheHit(hit: Boolean) {
        TelemetryCollector.record(
            TelemetryEvent.Category.CACHE, if (hit) "cache_hit" else "cache_miss", 1.0
        )
    }

    fun recordCircuitBreaker(opened: Boolean) {
        if (opened) TelemetryCollector.record(TelemetryEvent.Category.CIRCUIT_BREAKER, "circuit_opened", 1.0)
    }

    fun recordLearningRound(improved: Boolean, promoted: Boolean) {
        TelemetryCollector.record(TelemetryEvent.Category.LEARNING, "learning_round", if (improved) 1.0 else 0.0)
        if (promoted) TelemetryCollector.record(TelemetryEvent.Category.LEARNING, "revision_promoted", 1.0)
    }

    fun recordCapsuleSync(success: Boolean) {
        TelemetryCollector.record(TelemetryEvent.Category.SYNC, "capsule_sync", if (success) 1.0 else 0.0)
    }

    fun recordChallenge() {
        TelemetryCollector.record(TelemetryEvent.Category.CHALLENGE, "challenge_logged", 1.0)
    }

    /** Run analysis and update suggestions. */
    fun analyze() {
        _suggestions.value = TelemetryAnalyzer.analyze(TelemetryCollector.snapshot())
    }

    /** Flush buffered events to CloudWatch Logs (if configured) and clear. */
    fun flushToCloud(accessKey: String?, secretKey: String?, region: String?) {
        scope.launch {
            val events = TelemetryCollector.flush()
            if (events.isEmpty()) return@launch
            val lines = events.map { it.toJson() }
            val ok = if (accessKey != null && secretKey != null && region != null) {
                CloudWatchLogsManager.putLogEvents(accessKey, secretKey, region, lines)
            } else {
                false
            }
            _flushCount.value++
            // Re-analyze after flush
            analyze()
        }
    }

    /** Health summary for the UI. */
    fun summary(): String = TelemetryAnalyzer.summarize(TelemetryCollector.snapshot())

    /** Latency stats for a metric (for UI display). */
    fun latencyStats(metric: String): Map<String, Double?> = TelemetryCollector.latencyStats(metric)
}

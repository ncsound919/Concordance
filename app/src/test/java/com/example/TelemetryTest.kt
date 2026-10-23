package com.example

import com.example.concordance.telemetry.OptimizationSuggestion
import com.example.concordance.telemetry.TelemetryAnalyzer
import com.example.concordance.telemetry.TelemetryCollector
import com.example.concordance.telemetry.TelemetryEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryTest {

    private fun ev(cat: TelemetryEvent.Category, metric: String, value: Double = 1.0, tags: Map<String, String> = emptyMap()) =
        TelemetryEvent(
            timestampUtc = "2026-10-19T00:00:00Z",
            category = cat,
            metric = metric,
            value = value,
            tags = tags
        )

    @Test
    fun collectorBuffersAndFlushes() {
        TelemetryCollector.clear()
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call", 1.0))
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call", 0.0))
        assertEquals(2, TelemetryCollector.snapshot().size)
        val flushed = TelemetryCollector.flush()
        assertEquals(2, flushed.size)
        assertTrue(TelemetryCollector.snapshot().isEmpty())
    }

    @Test
    fun analyzerDetectsHighFailureRate() {
        TelemetryCollector.clear()
        val events = (1..10).map { ev(TelemetryEvent.Category.API_CALL, "api_call", 0.0, mapOf("provider" to "bad-provider")) }
        val suggestions = TelemetryAnalyzer.analyze(events)
        assertTrue(suggestions.any { it.metric == "api_failure_rate" })
        assertTrue(suggestions.any { it.severity == OptimizationSuggestion.Severity.CRITICAL || it.severity == OptimizationSuggestion.Severity.WARNING })
    }

    @Test
    fun analyzerDetectsCircuitBreakerTrips() {
        val events = listOf(ev(TelemetryEvent.Category.CIRCUIT_BREAKER, "circuit_opened", 1.0))
        val suggestions = TelemetryAnalyzer.analyze(events)
        assertTrue(suggestions.any { it.metric == "circuit_breaker_trips" })
    }

    @Test
    fun analyzerDetectsNoPromotions() {
        val events = listOf(ev(TelemetryEvent.Category.LEARNING, "learning_round", 0.0))
        val suggestions = TelemetryAnalyzer.analyze(events)
        assertTrue(suggestions.any { it.metric == "learning_promotion_rate" })
    }

    @Test
    fun analyzerDetectsSyncFailures() {
        val events = listOf(ev(TelemetryEvent.Category.SYNC, "capsule_sync", 0.0))
        val suggestions = TelemetryAnalyzer.analyze(events)
        assertTrue(suggestions.any { it.metric == "sync_failure_count" })
    }

    @Test
    fun analyzerReturnsEmptyForNoEvents() {
        assertTrue(TelemetryAnalyzer.analyze(emptyList()).isEmpty())
    }

    @Test
    fun latencyStatsComputedFromTags() {
        TelemetryCollector.clear()
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call", 1.0, mapOf("latencyMs" to "100")))
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call", 1.0, mapOf("latencyMs" to "200")))
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call", 1.0, mapOf("latencyMs" to "300")))
        val stats = TelemetryCollector.latencyStats("api_call")
        assertEquals(3.0, stats["count"]!!, 1e-9)
        assertTrue(stats["p50"]!! >= 100.0)
        assertTrue(stats["max"]!! >= 300.0)
    }

    @Test
    fun ringBufferEvictsOldest() {
        TelemetryCollector.clear()
        // Record more than RING_CAPACITY (1000) events
        repeat(1005) { i ->
            TelemetryCollector.record(ev(TelemetryEvent.Category.SYSTEM, "sys", i.toDouble()))
        }
        assertTrue(TelemetryCollector.snapshot().size <= 1000)
    }

    @Test
    fun percentileReturnsNullForEmpty() {
        TelemetryCollector.clear()
        assertEquals(null, TelemetryCollector.percentile("nonexistent", 0.5))
    }
}

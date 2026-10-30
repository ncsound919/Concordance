package com.example

import com.example.concordance.telemetry.TelemetryAnalyzer
import com.example.concordance.telemetry.TelemetryCollector
import com.example.concordance.telemetry.TelemetryEvent
import com.example.concordance.telemetry.TelemetryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The telemetry buffer and the facade over it.
 *
 * The buffer is a bounded ring: it must never grow without limit, and it must
 * never lose the latency samples the latency panel depends on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TelemetryManagerTest {

    private fun ev(
        category: TelemetryEvent.Category,
        metric: String,
        value: Double = 1.0,
        timestampUtc: String = "2026-10-19T00:00:00Z",
        tags: Map<String, String> = emptyMap()
    ) = TelemetryEvent(
        timestampUtc = timestampUtc,
        category = category,
        metric = metric,
        value = value,
        tags = tags
    )

    @Before
    fun setUp() {
        TelemetryCollector.clear()
    }

    // -----------------------------------------------------------------------
    // The buffer
    // -----------------------------------------------------------------------

    @Test
    fun `a recorded event is buffered`() {
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call"))
        assertEquals(1, TelemetryCollector.snapshot().size)
        assertEquals(1L, TelemetryCollector.eventCount.value)
    }

    @Test
    fun `the event count tracks the buffer`() {
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call"))
        TelemetryCollector.record(ev(TelemetryEvent.Category.CACHE, "cache_hit"))
        assertEquals(2L, TelemetryCollector.eventCount.value)
    }

    @Test
    fun `the buffer is bounded at a thousand events`() {
        repeat(1100) {
            TelemetryCollector.record(ev(TelemetryEvent.Category.SYSTEM, "tick"))
        }
        assertEquals(1000, TelemetryCollector.snapshot().size)
    }

    @Test
    fun `flushing returns the events and empties the buffer`() {
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call"))
        val flushed = TelemetryCollector.flush()
        assertEquals(1, flushed.size)
        assertTrue(TelemetryCollector.snapshot().isEmpty())
        assertEquals(0L, TelemetryCollector.eventCount.value)
        assertNotNull(TelemetryCollector.lastFlushUtc.value)
    }

    @Test
    fun `clearing empties the buffer`() {
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call"))
        TelemetryCollector.clear()
        assertTrue(TelemetryCollector.snapshot().isEmpty())
    }

    // -----------------------------------------------------------------------
    // Aggregates
    // -----------------------------------------------------------------------

    @Test
    fun `events are counted by category`() {
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call"))
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call"))
        TelemetryCollector.record(ev(TelemetryEvent.Category.CACHE, "cache_hit"))
        val counts = TelemetryCollector.countsByCategory()
        assertEquals(2, counts[TelemetryEvent.Category.API_CALL])
        assertEquals(1, counts[TelemetryEvent.Category.CACHE])
    }

    @Test
    fun `the average of a metric is computed over its events only`() {
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call", 2.0))
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call", 4.0))
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "other", 100.0))
        assertEquals(3.0, TelemetryCollector.averageFor("api_call"), 1e-9)
    }

    @Test
    fun `the average of an unknown metric is zero`() {
        assertEquals(0.0, TelemetryCollector.averageFor("nothing"), 1e-9)
    }

    @Test
    fun `the success rate counts values of at least one`() {
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call", 1.0))
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call", 0.0))
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call", 2.0))
        assertEquals(2.0 / 3.0, TelemetryCollector.successRate("api_call"), 1e-9)
    }

    @Test
    fun `the success rate of an unknown metric is one`() {
        assertEquals(1.0, TelemetryCollector.successRate("nothing"), 1e-9)
    }

    // -----------------------------------------------------------------------
    // Latency
    // -----------------------------------------------------------------------

    @Test
    fun `latency samples are taken from the latency tag`() {
        TelemetryCollector.record(
            ev(TelemetryEvent.Category.API_CALL, "api_call", tags = mapOf("latencyMs" to "100"))
        )
        TelemetryCollector.record(
            ev(TelemetryEvent.Category.API_CALL, "api_call", tags = mapOf("latencyMs" to "300"))
        )
        val stats = TelemetryCollector.latencyStats("api_call")
        assertEquals(2.0, stats["count"]!!, 1e-9)
        assertEquals(100.0, stats["p50"]!!, 1e-9)
        assertEquals(300.0, stats["max"]!!, 1e-9)
    }

    @Test
    fun `an event with no latency tag is not a latency sample`() {
        TelemetryCollector.record(ev(TelemetryEvent.Category.API_CALL, "api_call"))
        val stats = TelemetryCollector.latencyStats("api_call")
        assertEquals(0.0, stats["count"]!!, 1e-9)
        assertNull(stats["p50"])
    }

    @Test
    fun `the latency stats of an unknown metric are empty rather than absent`() {
        val stats = TelemetryCollector.latencyStats("nothing")
        assertNull(stats["p50"])
        assertNull(stats["p95"])
        assertNull(stats["p99"])
        assertNull(stats["max"])
        assertEquals(0.0, stats["count"]!!, 1e-9)
    }

    @Test
    fun `a percentile of an unknown metric is null`() {
        assertNull(TelemetryCollector.percentile("nothing", 0.5))
    }

    @Test
    fun `a percentile is taken from the sorted samples`() {
        listOf(10L, 20L, 30L, 40L, 50L).forEach {
            TelemetryCollector.record(
                ev(TelemetryEvent.Category.API_CALL, "api_call", tags = mapOf("latencyMs" to it.toString()))
            )
        }
        assertEquals(30.0, TelemetryCollector.percentile("api_call", 0.5)!!, 1e-9)
        assertEquals(50.0, TelemetryCollector.percentile("api_call", 1.0)!!, 1e-9)
    }

    // -----------------------------------------------------------------------
    // The time series
    // -----------------------------------------------------------------------

    @Test
    fun `a series buckets events by time`() {
        TelemetryCollector.record(
            ev(TelemetryEvent.Category.API_CALL, "api_call", timestampUtc = "2026-10-19T00:00:00Z")
        )
        TelemetryCollector.record(
            ev(TelemetryEvent.Category.API_CALL, "api_call", timestampUtc = "2026-10-19T00:30:00Z")
        )
        TelemetryCollector.record(
            ev(TelemetryEvent.Category.API_CALL, "api_call", timestampUtc = "2026-10-19T02:00:00Z")
        )
        val series = TelemetryCollector.series("api_call", bucketMs = 3_600_000L)
        assertEquals(2, series.size)
        assertEquals(2, series.first().second)
        assertEquals(1, series.last().second)
    }

    @Test
    fun `a series of an unknown metric is empty`() {
        assertTrue(TelemetryCollector.series("nothing").isEmpty())
    }

    @Test
    fun `a timestamp that cannot be parsed lands in the zero bucket rather than throwing`() {
        TelemetryCollector.record(
            ev(TelemetryEvent.Category.API_CALL, "api_call", timestampUtc = "not-a-timestamp")
        )
        val series = TelemetryCollector.series("api_call")
        assertEquals(1, series.size)
        assertEquals(0L, series.first().first)
    }

    // -----------------------------------------------------------------------
    // The event serialisation
    // -----------------------------------------------------------------------

    @Test
    fun `an event serialises to json with its fields`() {
        val event = ev(TelemetryEvent.Category.API_CALL, "api_call", 0.5, tags = mapOf("provider" to "civic"))
        val json = event.toJson()
        assertTrue(json.contains("\"metric\":\"api_call\""))
        assertTrue(json.contains("\"value\":0.5"))
        assertTrue(json.contains("\"provider\":\"civic\""))
        assertTrue(json.contains("\"cat\":\"api_call\""))
    }

    @Test
    fun `a quote in a tag value is escaped`() {
        val event = ev(TelemetryEvent.Category.API_CALL, "api_call", tags = mapOf("provider" to "say \"hi\""))
        val json = event.toJson()
        assertTrue(json.contains("\\\"hi\\\""))
        // The raw, unescaped quote must not appear inside the value.
        assertTrue(!json.contains("\"provider\":\"say \"hi\"\""))
    }

    // -----------------------------------------------------------------------
    // The manager facade
    // -----------------------------------------------------------------------

    @Test
    fun `the manager records through to the collector`() {
        TelemetryManager.recordApiCall("civic", success = true, latencyMs = 120)
        val events = TelemetryCollector.snapshot()
        assertEquals(1, events.size)
        assertEquals("civic", events.first().tags["provider"])
        assertEquals(1.0, events.first().value, 1e-9)
    }

    @Test
    fun `a failed api call is recorded as a zero`() {
        TelemetryManager.recordApiCall("civic", success = false, latencyMs = 120)
        assertEquals(0.0, TelemetryCollector.snapshot().first().value, 1e-9)
    }

    @Test
    fun `a cache hit and a cache miss are different metrics`() {
        TelemetryManager.recordCacheHit(hit = true)
        TelemetryManager.recordCacheHit(hit = false)
        val metrics = TelemetryCollector.snapshot().map { it.metric }
        assertTrue(metrics.contains("cache_hit"))
        assertTrue(metrics.contains("cache_miss"))
    }

    @Test
    fun `a circuit breaker is only recorded when it opens`() {
        TelemetryManager.recordCircuitBreaker(opened = false)
        assertTrue(TelemetryCollector.snapshot().isEmpty())
        TelemetryManager.recordCircuitBreaker(opened = true)
        assertEquals(1, TelemetryCollector.snapshot().size)
    }

    @Test
    fun `a promoted revision is recorded alongside the learning round`() {
        TelemetryManager.recordLearningRound(improved = true, promoted = true)
        val metrics = TelemetryCollector.snapshot().map { it.metric }
        assertTrue(metrics.contains("learning_round"))
        assertTrue(metrics.contains("revision_promoted"))
    }

    @Test
    fun `a learning round that was not promoted is recorded on its own`() {
        TelemetryManager.recordLearningRound(improved = false, promoted = false)
        val metrics = TelemetryCollector.snapshot().map { it.metric }
        assertTrue(metrics.contains("learning_round"))
        assertTrue(!metrics.contains("revision_promoted"))
    }

    @Test
    fun `a capsule sync records whether it succeeded`() {
        TelemetryManager.recordCapsuleSync(success = false)
        assertEquals(0.0, TelemetryCollector.snapshot().first().value, 1e-9)
    }

    @Test
    fun `a challenge is recorded`() {
        TelemetryManager.recordChallenge()
        assertEquals("challenge_logged", TelemetryCollector.snapshot().first().metric)
    }

    @Test
    fun `analysing updates the suggestions`() {
        repeat(5) { TelemetryManager.recordApiCall("civic", success = false, latencyMs = 100) }
        TelemetryManager.analyze()
        assertTrue(TelemetryManager.suggestions.value.isNotEmpty())
        assertEquals("api_failure_rate", TelemetryManager.suggestions.value.first().metric)
    }

    @Test
    fun `the summary of an empty buffer says so`() {
        assertEquals("No telemetry events recorded.", TelemetryManager.summary())
    }

    @Test
    fun `the summary counts the events by category`() {
        TelemetryManager.recordApiCall("civic", success = true, latencyMs = 10)
        TelemetryManager.recordChallenge()
        val summary = TelemetryManager.summary()
        assertTrue(summary.contains("api_call=1"))
        assertTrue(summary.contains("challenge=1"))
    }

    // -----------------------------------------------------------------------
    // The analyzer
    // -----------------------------------------------------------------------

    @Test
    fun `an empty event list produces no suggestions`() {
        assertTrue(TelemetryAnalyzer.analyze(emptyList()).isEmpty())
    }

    @Test
    fun `a high failure rate is a warning`() {
        // Two failures out of five calls is a 40% failure rate: above the 30%
        // threshold but below the 60% that makes it critical.
        val events = listOf(
            ev(TelemetryEvent.Category.API_CALL, "api_call", 0.0, tags = mapOf("provider" to "civic")),
            ev(TelemetryEvent.Category.API_CALL, "api_call", 0.0, tags = mapOf("provider" to "civic")),
            ev(TelemetryEvent.Category.API_CALL, "api_call", 1.0, tags = mapOf("provider" to "civic")),
            ev(TelemetryEvent.Category.API_CALL, "api_call", 1.0, tags = mapOf("provider" to "civic")),
            ev(TelemetryEvent.Category.API_CALL, "api_call", 1.0, tags = mapOf("provider" to "civic"))
        )
        val suggestions = TelemetryAnalyzer.analyze(events)
        val failure = suggestions.first { it.metric == "api_failure_rate" }
        assertEquals(
            com.example.concordance.telemetry.OptimizationSuggestion.Severity.WARNING,
            failure.severity
        )
    }

    @Test
    fun `a very high failure rate is critical`() {
        val events = (1..5).map {
            ev(TelemetryEvent.Category.API_CALL, "api_call", 0.0, tags = mapOf("provider" to "civic"))
        } + listOf(
            ev(TelemetryEvent.Category.API_CALL, "api_call", 1.0, tags = mapOf("provider" to "civic"))
        )
        val failure = TelemetryAnalyzer.analyze(events).first { it.metric == "api_failure_rate" }
        assertEquals(
            com.example.concordance.telemetry.OptimizationSuggestion.Severity.CRITICAL,
            failure.severity
        )
    }

    @Test
    fun `a low failure rate is not a suggestion`() {
        val events = (1..5).map {
            ev(TelemetryEvent.Category.API_CALL, "api_call", 1.0, tags = mapOf("provider" to "civic"))
        }
        assertTrue(TelemetryAnalyzer.analyze(events).none { it.metric == "api_failure_rate" })
    }

    @Test
    fun `a low cache hit rate is a suggestion`() {
        val events = listOf(
            ev(TelemetryEvent.Category.CACHE, "cache_miss"),
            ev(TelemetryEvent.Category.CACHE, "cache_miss")
        )
        assertTrue(TelemetryAnalyzer.analyze(events).any { it.metric == "cache_hit_rate" })
    }

    @Test
    fun `a high cache hit rate is not a suggestion`() {
        val events = listOf(
            ev(TelemetryEvent.Category.CACHE, "cache_hit"),
            ev(TelemetryEvent.Category.CACHE, "cache_hit")
        )
        assertTrue(TelemetryAnalyzer.analyze(events).none { it.metric == "cache_hit_rate" })
    }

    @Test
    fun `a tripped circuit breaker is a suggestion`() {
        val events = listOf(ev(TelemetryEvent.Category.CIRCUIT_BREAKER, "circuit_opened"))
        val suggestion = TelemetryAnalyzer.analyze(events).first { it.metric == "circuit_breaker_trips" }
        assertEquals(1.0, suggestion.currentValue, 1e-9)
    }

    @Test
    fun `a failed sync is a suggestion`() {
        val events = listOf(ev(TelemetryEvent.Category.SYNC, "capsule_sync", 0.0))
        assertTrue(TelemetryAnalyzer.analyze(events).any { it.metric == "sync_failure_count" })
    }

    @Test
    fun `a learning round with no promotion is a suggestion`() {
        val events = listOf(ev(TelemetryEvent.Category.LEARNING, "learning_round"))
        assertTrue(TelemetryAnalyzer.analyze(events).any { it.metric == "learning_promotion_rate" })
    }
}

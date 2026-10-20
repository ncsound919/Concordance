package com.example.concordance.telemetry

import com.example.concordance.integration.api.ProvenanceRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * In-memory telemetry buffer with bounded ring eviction and per-metric latency
 * tracking. Thread-safe. Inspired by sieve's telemetry ring buffer pattern.
 */
object TelemetryCollector {

    private const val RING_CAPACITY = 1000

    private val buffer = ConcurrentLinkedQueue<TelemetryEvent>()
    private val latencySamples = mutableMapOf<String, MutableList<Double>>()
    private val lock = Any()

    private val _eventCount = MutableStateFlow(0L)
    val eventCount: StateFlow<Long> = _eventCount.asStateFlow()

    private val _lastFlushUtc = MutableStateFlow<String?>(null)
    val lastFlushUtc: StateFlow<String?> = _lastFlushUtc.asStateFlow()

    /** Record a single event. Thread-safe with bounded eviction. */
    fun record(event: TelemetryEvent) {
        synchronized(lock) {
            buffer.add(event)
            if (buffer.size > RING_CAPACITY) buffer.poll()
            event.tags["latencyMs"]?.toDoubleOrNull()?.let { ms ->
                val list = latencySamples.getOrPut(event.metric) { mutableListOf() }
                list.add(ms)
                if (list.size > RING_CAPACITY) list.removeAt(0)
            }
            _eventCount.value = buffer.size.toLong()
        }
    }

    /** Convenience: record with a category and metric. */
    fun record(
        category: TelemetryEvent.Category,
        metric: String,
        value: Double = 1.0,
        unit: String = "count",
        tags: Map<String, String> = emptyMap()
    ) {
        record(
            TelemetryEvent(
                timestampUtc = ProvenanceRecorder.nowUtc(),
                category = category,
                metric = metric,
                value = value,
                unit = unit,
                tags = tags
            )
        )
    }

    /** Snapshot of buffered events (copy). */
    fun snapshot(): List<TelemetryEvent> = buffer.toList()

    /** Aggregate: count events by category. */
    fun countsByCategory(): Map<TelemetryEvent.Category, Int> =
        snapshot().groupingBy { it.category }.eachCount()

    /** Aggregate: average value for a metric. */
    fun averageFor(metric: String): Double {
        val vals = snapshot().filter { it.metric == metric }.map { it.value }
        return if (vals.isEmpty()) 0.0 else vals.average()
    }

    /** Aggregate: success rate (value >= 1.0) for a metric. */
    fun successRate(metric: String): Double {
        val vals = snapshot().filter { it.metric == metric }
        if (vals.isEmpty()) return 1.0
        return vals.count { it.value >= 1.0 }.toDouble() / vals.size
    }

    /** Percentile of latency samples for a metric (0.0-1.0). */
    fun percentile(metric: String, p: Double): Double? {
        val samples = synchronized(lock) { latencySamples[metric]?.sorted() ?: emptyList() }
        if (samples.isEmpty()) return null
        val idx = (p * (samples.size - 1)).toInt().coerceIn(0, samples.size - 1)
        return samples[idx]
    }

    /** Latency stats (p50/p95/p99/max/count) for a metric. */
    fun latencyStats(metric: String): Map<String, Double?> {
        val samples = synchronized(lock) { latencySamples[metric]?.sorted() ?: emptyList() }
        if (samples.isEmpty()) return mapOf("p50" to null, "p95" to null, "p99" to null, "max" to null, "count" to 0.0)
        fun pct(p: Double): Double {
            val idx = (p * (samples.size - 1)).toInt().coerceIn(0, samples.size - 1)
            return samples[idx]
        }
        return mapOf(
            "p50" to pct(0.5),
            "p95" to pct(0.95),
            "p99" to pct(0.99),
            "max" to samples.last(),
            "count" to samples.size.toDouble()
        )
    }

    /** Time-bucketed series: count events per bucket for a given metric. */
    fun series(metric: String, bucketMs: Long = 3_600_000L): List<Pair<Long, Int>> {
        val events = snapshot().filter { it.metric == metric }
        val buckets = mutableMapOf<Long, Int>()
        for (e in events) {
            val ts = try { java.time.Instant.parse(e.timestampUtc).toEpochMilli() } catch (ex: Exception) { 0L }
            val bucket = (ts / bucketMs) * bucketMs
            buckets[bucket] = (buckets[bucket] ?: 0) + 1
        }
        return buckets.entries.sortedBy { it.key }.map { it.key to it.value }
    }

    /** Clear the buffer and latency samples. */
    fun clear() {
        synchronized(lock) {
            buffer.clear()
            latencySamples.clear()
            _eventCount.value = 0
        }
    }

    /** Flush: return buffered events and clear. */
    fun flush(): List<TelemetryEvent> {
        synchronized(lock) {
            val events = buffer.toList()
            buffer.clear()
            latencySamples.clear()
            _eventCount.value = 0
            _lastFlushUtc.value = ProvenanceRecorder.nowUtc()
            return events
        }
    }
}

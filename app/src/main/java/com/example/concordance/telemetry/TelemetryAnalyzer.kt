package com.example.concordance.telemetry

/**
 * Analyzes aggregated telemetry to produce optimization suggestions that the
 * recursive learning system and the agent can act on.
 *
 * Each analyzer is a pure function over a list of events, so it is fully
 * testable without AWS or a running app.
 */
object TelemetryAnalyzer {

    /** Analyze a batch of events and return optimization suggestions. */
    fun analyze(events: List<TelemetryEvent>): List<OptimizationSuggestion> {
        if (events.isEmpty()) return emptyList()
        val suggestions = mutableListOf<OptimizationSuggestion>()

        // 1. API provider failure rates
        val apiEvents = events.filter { it.category == TelemetryEvent.Category.API_CALL }
        val byProvider = apiEvents.groupBy { it.tags["provider"] ?: "unknown" }
        for ((provider, evs) in byProvider) {
            val total = evs.size
            val failures = evs.count { it.value < 1.0 }
            val failRate = if (total == 0) 0.0 else failures.toDouble() / total
            if (failRate > 0.3 && total >= 5) {
                suggestions.add(
                    OptimizationSuggestion(
                        severity = if (failRate > 0.6) OptimizationSuggestion.Severity.CRITICAL else OptimizationSuggestion.Severity.WARNING,
                        title = "High failure rate: $provider",
                        description = "$provider has a ${(failRate * 100).toInt()}% failure rate over $total calls.",
                        metric = "api_failure_rate",
                        currentValue = failRate,
                        suggestedAction = "Consider disabling $provider or checking its credentials/endpoint.",
                        estimatedImpact = "Reduce wasted API calls and improve latency."
                    )
                )
            }
            // Latency outlier
            val latencies = evs.mapNotNull { it.tags["latencyMs"]?.toDoubleOrNull() }
            if (latencies.isNotEmpty()) {
                val avg = latencies.average()
                val max = latencies.maxOrNull() ?: 0.0
                if (max > avg * 3 && latencies.size >= 5) {
                    suggestions.add(
                        OptimizationSuggestion(
                            severity = OptimizationSuggestion.Severity.INFO,
                            title = "Latency spike: $provider",
                            description = "$provider max latency ${max.toInt()}ms vs avg ${avg.toInt()}ms.",
                            metric = "api_latency",
                            currentValue = max,
                            suggestedAction = "Investigate $provider endpoint or increase timeout.",
                            estimatedImpact = "Improve response times."
                        )
                    )
                }
            }
        }

        // 2. Cache hit rate
        val cacheEvents = events.filter { it.category == TelemetryEvent.Category.CACHE }
        if (cacheEvents.isNotEmpty()) {
            val hits = cacheEvents.count { it.metric == "cache_hit" }
            val total = cacheEvents.size
            val hitRate = hits.toDouble() / total
            if (hitRate < 0.5) {
                suggestions.add(
                    OptimizationSuggestion(
                        severity = OptimizationSuggestion.Severity.INFO,
                        title = "Low cache hit rate",
                        description = "Cache hit rate is ${(hitRate * 100).toInt()}% over $total lookups.",
                        metric = "cache_hit_rate",
                        currentValue = hitRate,
                        suggestedAction = "Increase cache TTL or review cache key strategy.",
                        estimatedImpact = "Reduce redundant API calls."
                    )
                )
            }
        }

        // 3. Circuit breaker trips
        val cbEvents = events.filter { it.category == TelemetryEvent.Category.CIRCUIT_BREAKER }
        val trips = cbEvents.count { it.metric == "circuit_opened" }
        if (trips > 0) {
            suggestions.add(
                OptimizationSuggestion(
                    severity = if (trips > 3) OptimizationSuggestion.Severity.CRITICAL else OptimizationSuggestion.Severity.WARNING,
                    title = "Circuit breaker tripped $trips time(s)",
                    description = "Circuit breaker opened $trips time(s), indicating provider instability.",
                    metric = "circuit_breaker_trips",
                    currentValue = trips.toDouble(),
                    suggestedAction = "Review provider health and consider increasing failure threshold.",
                    estimatedImpact = "Improve system resilience."
                )
            )
        }

        // 4. Learning round efficiency
        val learningEvents = events.filter { it.category == TelemetryEvent.Category.LEARNING }
        val rounds = learningEvents.count { it.metric == "learning_round" }
        val promotions = learningEvents.count { it.metric == "revision_promoted" }
        if (rounds > 0 && promotions == 0) {
            suggestions.add(
                OptimizationSuggestion(
                    severity = OptimizationSuggestion.Severity.INFO,
                    title = "No promotions from $rounds learning round(s)",
                    description = "Learning rounds ran but none produced a promotable revision.",
                    metric = "learning_promotion_rate",
                    currentValue = 0.0,
                    suggestedAction = "Collect more challenges or widen the search space.",
                    estimatedImpact = "Unlock model improvements."
                )
            )
        }

        // 5. Sync failures
        val syncEvents = events.filter { it.category == TelemetryEvent.Category.SYNC }
        val syncFailures = syncEvents.count { it.value < 1.0 }
        if (syncFailures > 0) {
            suggestions.add(
                OptimizationSuggestion(
                    severity = OptimizationSuggestion.Severity.WARNING,
                    title = "Cloud sync failures",
                    description = "$syncFailures sync operation(s) failed.",
                    metric = "sync_failure_count",
                    currentValue = syncFailures.toDouble(),
                    suggestedAction = "Check cloud credentials and network connectivity.",
                    estimatedImpact = "Ensure capsules are backed up."
                )
            )
        }

        return suggestions
    }

    /** Summarize telemetry as a human-readable health report. */
    fun summarize(events: List<TelemetryEvent>): String {
        if (events.isEmpty()) return "No telemetry events recorded."
        val byCat = events.groupingBy { it.category }.eachCount()
        return byCat.entries.joinToString(" | ") { "${it.key.label}=${it.value}" }
    }
}

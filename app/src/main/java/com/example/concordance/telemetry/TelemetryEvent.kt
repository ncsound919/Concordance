package com.example.concordance.telemetry

import java.util.UUID

/**
 * A single telemetry event captured across the Concordance system.
 *
 * Events are buffered in [TelemetryCollector] and flushed to AWS CloudWatch Logs
 * (or S3) in batches. The recursive learning system consumes aggregated
 * telemetry to propose efficiency optimizations.
 */
data class TelemetryEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestampUtc: String,
    val category: Category,
    val metric: String,
    val value: Double,
    val unit: String = "count",
    val tags: Map<String, String> = emptyMap(),
    val source: String = "concordance-app"
) {
    enum class Category(val label: String) {
        API_CALL("api_call"),
        CACHE("cache"),
        CIRCUIT_BREAKER("circuit_breaker"),
        LEARNING("learning"),
        CAPSULE("capsule"),
        CHALLENGE("challenge"),
        SYNC("sync"),
        SYSTEM("system")
    }

    fun toJson(): String {
        val tagsJson = tags.entries.joinToString(",") { "\"${escapeJson(it.key)}\":\"${escapeJson(it.value)}\"" }
        return """{"id":"${escapeJson(id)}","ts":"${escapeJson(timestampUtc)}","cat":"${category.label}","metric":"${escapeJson(metric)}","value":$value,"unit":"${escapeJson(unit)}","tags":{$tagsJson},"src":"${escapeJson(source)}"}"""
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

/**
 * An optimization suggestion produced by [TelemetryAnalyzer] from aggregated
 * telemetry. Surfaced in the UI and (optionally) fed into the learning loop.
 */
data class OptimizationSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val severity: Severity,
    val title: String,
    val description: String,
    val metric: String,
    val currentValue: Double,
    val suggestedAction: String,
    val estimatedImpact: String
) {
    enum class Severity(val label: String, val colorHex: Long) {
        INFO("Info", 0xFF00E5FF),
        WARNING("Warning", 0xFFFFD740),
        CRITICAL("Critical", 0xFFFF5252)
    }
}

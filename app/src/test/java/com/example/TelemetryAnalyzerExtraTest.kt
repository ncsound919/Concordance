package com.example

import com.example.concordance.telemetry.OptimizationSuggestion
import com.example.concordance.telemetry.TelemetryAnalyzer
import com.example.concordance.telemetry.TelemetryEvent
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TelemetryAnalyzerExtraTest {
    @Test
    fun analyzeEmpty() {
        val res = TelemetryAnalyzer.analyze(emptyList())
        assertTrue(res is List<OptimizationSuggestion>)
    }

    @Test
    fun summarizeEmpty() {
        val s = TelemetryAnalyzer.summarize(emptyList())
        assertTrue(s is String)
    }

    @Test
    fun eventToJson() {
        val e = TelemetryEvent(timestampUtc = "t", category = TelemetryEvent.Category.API_CALL, metric = "m", value = 1.0)
        val json = e.toJson()
        assertTrue(json.contains("\"cat\":\"api_call\""))
        assertTrue(json.contains("\"metric\":\"m\""))
    }
}

package com.example

import com.example.concordance.telemetry.EcosystemTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone's telemetry envelope, tested without a socket.
 *
 * The assertions are string-level on purpose: org.json is an Android stub in a
 * plain JVM unit test, so parsing here would test the stub, not the envelope.
 */
class EcosystemTelemetryTest {

    private fun event(
        kind: String = "agent.call",
        subjectKind: String? = "agent-endpoint",
        subjectId: String? = "research",
        status: String = "ok",
        severity: String = "info",
        durationMs: Long? = 42,
        props: Map<String, Any?> = mapOf("method" to "POST", "http_status" to 200),
        nowMs: Long = 1_700_000_000_000
    ) = EcosystemTelemetry.buildEvent(
        kind = kind,
        subjectKind = subjectKind,
        subjectId = subjectId,
        status = status,
        severity = severity,
        durationMs = durationMs,
        props = props,
        nowMs = nowMs
    )

    @Test
    fun envelopeCarriesTheSharedSchemaAndAggregateClass() {
        val json = event()
        assertTrue(json.contains("\"schema\":\"overlay.ecosystem.event.v1\""))
        assertTrue(json.contains("\"source\":\"concordance-android\""))
        assertTrue(json.contains("\"kind\":\"agent.call\""))
        assertTrue(json.contains("\"data_class\":\"aggregate\""))
        assertTrue(json.contains("\"ts\":1700000000000"))
        assertTrue(json.contains("\"duration_ms\":42"))
        assertTrue(json.contains("\"subject\":{\"kind\":\"agent-endpoint\",\"id\":\"research\"}"))
        assertTrue(json.contains("\"http_status\":200"))
    }

    @Test
    fun idIsDeterministicAndIndependentOfPropOrder() {
        val a = event(props = linkedMapOf("method" to "POST", "http_status" to 200))
        val b = event(props = linkedMapOf("http_status" to 200, "method" to "POST"))
        val idA = Regex("\"id\":\"(evt_[0-9a-f]{24})\"").find(a)!!.groupValues[1]
        val idB = Regex("\"id\":\"(evt_[0-9a-f]{24})\"").find(b)!!.groupValues[1]
        assertEquals("sorted props give the same event id", idA, idB)

        val changed = event(props = mapOf("method" to "GET", "http_status" to 200))
        assertFalse("a changed call is a different event", changed.contains(idA))
    }

    @Test
    fun subjectIdsAreSanitizedForTheReceiver() {
        val sanitized = EcosystemTelemetry.sanitize("claim 42 / arm #3")
        assertFalse("no spaces reach the receiver", sanitized.contains(" "))
        assertFalse("no fragment marker reaches the receiver", sanitized.contains("#"))
        assertTrue(event(subjectId = "claim 42 / arm #3").contains("\"id\":\"$sanitized\""))
        assertEquals("leading", EcosystemTelemetry.sanitize("-leading"))
        assertEquals("agent-endpoint", EcosystemTelemetry.sanitize("agent-endpoint"))
    }

    @Test
    fun stringsAreEscapedAndNullsSurvive() {
        val json = event(props = mapOf("note" to "a \"quoted\"\nline", "empty" to null))
        assertTrue(json.contains("a \\\"quoted\\\"\\nline"))
        assertTrue(json.contains("\"empty\":null"))
    }

    @Test
    fun recordingWithoutAConfiguredSinkIsANoOp() {
        EcosystemTelemetry.resetForTests()
        assertFalse(EcosystemTelemetry.isConfigured())
        EcosystemTelemetry.record(kind = "agent.call", subjectId = "research")
    }

    @Test
    fun nullSubjectAndDurationAreEncodedExplicitly() {
        val json = event(subjectKind = null, subjectId = null, durationMs = null)
        assertTrue(json.contains("\"subject\":null"))
        assertTrue(json.contains("\"duration_ms\":null"))
    }
}

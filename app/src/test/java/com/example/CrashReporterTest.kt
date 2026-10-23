package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.concordance.telemetry.CrashReporter
import com.example.concordance.telemetry.TelemetryCollector
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The handler must record a crash without itself throwing, and it must not grow
 * the file without bound.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrashReporterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CrashReporter.clear(context)
        TelemetryCollector.clear()
    }

    @After
    fun tearDown() {
        CrashReporter.clear(context)
        TelemetryCollector.clear()
    }

    @Test
    fun `record persists type message and thread`() {
        val error = IllegalStateException("model failed to load")
        val line = CrashReporter.record(context, "main", error)

        assertTrue(line.contains("java.lang.IllegalStateException"))
        assertTrue(line.contains("model failed to load"))
        assertTrue(line.contains("\"thread\":\"main\""))
        assertEquals(1, CrashReporter.count(context))
    }

    @Test
    fun `record escapes quotes and newlines`() {
        val error = IllegalArgumentException("bad \"value\"\nnext line")
        val line = CrashReporter.record(context, "worker", error)

        assertTrue(line.contains("bad \\\"value\\\"\\nnext line"))
        assertTrue(!line.contains("next line\"}"))
    }

    @Test
    fun `frames are bounded`() {
        val deep = errorWithFrames(40)
        val line = CrashReporter.record(context, "main", deep)
        val frameCount = line.substringAfter("\"frames\":\"").substringBefore("\"")
            .split("|").count { it.isNotBlank() }

        assertTrue("expected <= 12 frames, got $frameCount", frameCount <= 12)
    }

    @Test
    fun `crash is visible in telemetry`() {
        CrashReporter.record(context, "main", RuntimeException("boom"))

        val crash = TelemetryCollector.snapshot().filter { it.metric == "crash" }
        assertEquals(1, crash.size)
        assertEquals("RuntimeException", crash.single().tags["type"])
    }

    @Test
    fun `clear removes persisted crashes`() {
        CrashReporter.record(context, "main", RuntimeException("one"))
        CrashReporter.clear(context)

        assertEquals(0, CrashReporter.count(context))
    }

    private fun errorWithFrames(count: Int): Throwable {
        val frames = Array(count) { StackTraceElement("com.example.Frame$it", "run", "Frame$it.kt", it) }
        return RuntimeException("deep").apply { stackTrace = frames }
    }
}

package com.example.concordance.telemetry

import android.content.Context
import android.os.Process
import com.example.concordance.integration.api.ProvenanceRecorder
import java.io.File
import kotlin.system.exitProcess

/**
 * Captures uncaught exceptions so a field failure leaves a record instead of a
 * screenshot.
 *
 * The handler writes one JSON line to `filesDir/crashes.jsonl` (bounded), records
 * a SYSTEM telemetry event, and then delegates to the previous handler so the
 * process still dies the way the platform expects. Nothing is uploaded here; the
 * existing telemetry flush to CloudWatch carries the event when credentials are
 * configured.
 *
 * The handler itself must never throw: a failure inside it is swallowed, because
 * an exception in an uncaught-exception handler loses the original crash.
 */
object CrashReporter {

    const val FILE_NAME = "crashes.jsonl"
    private const val MAX_BYTES = 256 * 1024
    private const val MAX_FRAMES = 12

    @Volatile
    private var installed = false

    /** Install once, at process start. Safe to call more than once. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                record(app, thread?.name ?: "unknown", error)
            } catch (_: Throwable) {
                // Never mask the original crash with a reporting failure.
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /**
     * Persist one crash and record it in telemetry. Returns the JSON line that was
     * written, which is what the tests assert on.
     */
    fun record(context: Context, threadName: String, error: Throwable): String {
        val line = describe(threadName, error)
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        synchronized(this) {
            if (file.exists() && file.length() > MAX_BYTES) {
                val kept = file.readLines().takeLast(20)
                file.writeText(kept.joinToString("\n", postfix = "\n"))
            }
            file.appendText(line + "\n")
        }
        TelemetryCollector.record(
            TelemetryEvent.Category.SYSTEM,
            "crash",
            1.0,
            "count",
            mapOf(
                "type" to (error.javaClass.simpleName ?: "Throwable"),
                "thread" to threadName
            )
        )
        return line
    }

    /** Build the one-line record. Pure apart from the timestamp. */
    fun describe(threadName: String, error: Throwable): String {
        val frames = error.stackTrace.take(MAX_FRAMES).joinToString("|") { frame ->
            "${frame.className}.${frame.methodName}:${frame.lineNumber}"
        }
        val type = error.javaClass.name ?: "java.lang.Throwable"
        val message = error.message ?: ""
        return buildString {
            append("{")
            append("\"ts\":\"").append(escape(ProvenanceRecorder.nowUtc())).append("\",")
            append("\"type\":\"").append(escape(type)).append("\",")
            append("\"thread\":\"").append(escape(threadName)).append("\",")
            append("\"message\":\"").append(escape(message.take(500))).append("\",")
            append("\"frames\":\"").append(escape(frames)).append("\"")
            append("}")
        }
    }

    /** Persisted crashes, oldest first. */
    fun crashes(context: Context): List<String> {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotBlank() }
    }

    fun count(context: Context): Int = crashes(context).size

    fun clear(context: Context) {
        synchronized(this) {
            File(context.applicationContext.filesDir, FILE_NAME).delete()
        }
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

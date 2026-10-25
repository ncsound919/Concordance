package com.example.concordance.telemetry

import androidx.annotation.VisibleForTesting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The phone's lane into the Sieve telemetry spine.
 *
 * The envelope is `overlay.ecosystem.event.v1`, shared with the website and the
 * Referee engine (see `wiring/contracts/ecosystem-event.v1.schema.json`). Two
 * rules, both deliberate:
 *
 *  - **No PHI, ever.** The app carries patient-level images and corrections; the
 *    telemetry lane carries only operational facts (which endpoint, how long,
 *    what status). `data_class` is hard-coded to `aggregate`, and no request or
 *    response body is ever included.
 *  - **Never block or fail the work.** Delivery runs on a single daemon thread
 *    with a 2 second budget; an unreachable spine is silent. A doctor's research
 *    call must not fail because a metrics hop did.
 *
 * Configured from `BuildConfig.SIEVE_TELEMETRY_URL`, which is intentionally
 * empty by default: this build must not pretend to have a sink. Point it at the
 * rate-limited ingest endpoint (an edge Worker that holds the Sieve key), not at
 * the Sieve HTTP process directly - the app must never embed a read-capable key.
 */
object EcosystemTelemetry {

    const val SCHEMA = "overlay.ecosystem.event.v1"
    const val SOURCE = "concordance-android"

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val UNSAFE = Regex("[^A-Za-z0-9_.:@/-]+")

    @Volatile
    private var baseUrl: String = ""

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ecosystem-telemetry").apply { isDaemon = true }
    }

    /** Point the lane at an ingest base URL, or pass null/blank to disable it. */
    fun configure(url: String?) {
        baseUrl = url.orEmpty().trim().trimEnd('/')
    }

    fun isConfigured(): Boolean = baseUrl.isNotEmpty()

    @VisibleForTesting
    fun resetForTests() {
        baseUrl = ""
    }

    /**
     * Record one event. Returns immediately; delivery (and its failure) never
     * reaches the caller.
     */
    fun record(
        kind: String,
        subjectKind: String? = null,
        subjectId: String? = null,
        status: String = "ok",
        severity: String = "info",
        durationMs: Long? = null,
        props: Map<String, Any?> = emptyMap()
    ) {
        val url = baseUrl
        if (url.isEmpty()) return
        val payload = buildEvent(
            kind = kind,
            subjectKind = subjectKind,
            subjectId = subjectId,
            status = status,
            severity = severity,
            durationMs = durationMs,
            props = props,
            nowMs = System.currentTimeMillis()
        )
        executor.execute { post(url, payload) }
    }

    @VisibleForTesting
    internal fun buildEvent(
        kind: String,
        subjectKind: String?,
        subjectId: String?,
        status: String,
        severity: String,
        durationMs: Long?,
        props: Map<String, Any?>,
        nowMs: Long
    ): String {
        val safeKind = sanitize(kind).ifEmpty { "unknown" }
        val safeSubject = subjectId?.let { sanitize(it) }
        val subjectJson = if (safeSubject == null) {
            "null"
        } else {
            """{"kind":"${escape(sanitize(subjectKind ?: "subject"))}","id":"${escape(safeSubject)}"}"""
        }
        val propsJson = propsJson(props)
        val id = eventId(safeKind, subjectJson, propsJson)

        val duration = durationMs?.coerceAtLeast(0)?.toString() ?: "null"
        return buildString {
            append("{")
            append(""""schema":"$SCHEMA",""")
            append(""""id":"$id",""")
            append(""""ts":$nowMs,""")
            append(""""source":"$SOURCE",""")
            append(""""kind":"${escape(safeKind)}",""")
            append(""""severity":"${escape(severity)}",""")
            append(""""status":"${escape(status)}",""")
            append(""""subject":$subjectJson,""")
            append(""""session":null,""")
            append(""""duration_ms":$duration,""")
            append(""""data_class":"aggregate",""")
            append(""""props":$propsJson""")
            append("}")
        }
    }

    /** Deterministic over content, so a retried call is one event, not two. */
    @VisibleForTesting
    internal fun eventId(kind: String, subjectJson: String, propsJson: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$SOURCE|$kind|$subjectJson|$propsJson".toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xff) }
        return "evt_${hex.take(24)}"
    }

    @VisibleForTesting
    internal fun sanitize(value: String): String {
        val cleaned = UNSAFE.replace(value.trim(), "-").trim('-').take(120)
        if (cleaned.isEmpty()) return ""
        return if (cleaned[0].isLetterOrDigit()) cleaned else "x-$cleaned".take(120)
    }

    private fun propsJson(props: Map<String, Any?>): String {
        if (props.isEmpty()) return "{}"
        return props.toSortedMap().entries.joinToString(",", "{", "}") { (key, value) ->
            "\"${escape(key)}\":${valueJson(value)}"
        }
    }

    private fun valueJson(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Int, is Long, is Double, is Float -> value.toString()
        else -> "\"${escape(value.toString())}\""
    }

    private fun escape(value: String): String {
        val out = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (ch < ' ') out.append(String.format(Locale.US, "\\u%04x", ch.code)) else out.append(ch)
            }
        }
        return out.toString()
    }

    private fun post(url: String, payload: String) {
        try {
            val request = Request.Builder()
                .url("$url/ecosystem/event")
                .header("User-Agent", "Concordance-Android/1.0 (telemetry)")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                // A refused or duplicate event is not worth surfacing in the app.
                response.close()
            }
        } catch (_: Exception) {
            // Telemetry is best-effort by contract; the research call already succeeded.
        }
    }
}

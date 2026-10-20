package com.example.concordance.local

import com.example.concordance.engine.LocalModelExtractionResult
import com.example.concordance.model.EvidenceGrade
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * One flat field the extraction prompt asks the model to fill. Every value is
 * optional: the model must be allowed to say "not present" instead of guessing.
 */
data class LlmExtraction(
    val gene: String? = null,
    val variant: String? = null,
    val brainEvents: Int? = null,
    val brainTotal: Int? = null,
    val otherEvents: Int? = null,
    val otherTotal: Int? = null,
    val oddsRatio: Double? = null,
    val ciLow: Double? = null,
    val ciHigh: Double? = null,
    val confidence: Double? = null,
    val summary: String? = null,
)

/**
 * Structured paper extraction through the embedded llama.cpp server
 * ([LocalModels.LLM_PORT]), used instead of the rule extractor when the
 * operator has started an on-device model.
 *
 * The client is deliberately dumb and total: any failure — server down, slow
 * answer, malformed JSON — yields `null` and the caller falls back to the
 * deterministic rule extractor. A local model assists; it never blocks.
 */
object LocalExtractionClient {

    private const val TAG = "LocalExtractionClient"

    private const val SYSTEM_PROMPT =
        "You extract oncology statistics from a paper abstract. Reply with ONE JSON object " +
            "and no other text. Use null for any field you cannot find. Never invent numbers."

    /** Tolerant read timeout: a 4B CPU model at ~23 tok/s needs head-room for prompt eval. */
    private const val READ_TIMEOUT_S = 90L
    private const val MAX_TOKENS = 512

    fun buildExtractionPrompt(abstractText: String): String =
        "Extract the following from this abstract as JSON with exactly these keys: " +
            "gene (string, HGNC symbol), variant (string, e.g. Y537S, or null), " +
            "brain_events (integer), brain_total (integer), other_events (integer), " +
            "other_total (integer), odds_ratio (number), ci_low (number), ci_high (number), " +
            "confidence (number 0.0-1.0), summary (one sentence in plain language). " +
            "Brain/CNS counts first, the comparison (other/systemic) arm second:\n\n" +
            abstractText.take(4000)

    /**
     * Calls `/v1/chat/completions` on the loopback llama.cpp server and maps the reply.
     * Returns `null` on any failure — the caller must fall back to the rule extractor.
     */
    suspend fun extract(
        abstractText: String,
        port: Int = LocalModels.LLM_PORT,
        timeoutOverrideS: Long? = null
    ): LocalModelExtractionResult? {
        val start = System.currentTimeMillis()
        val raw = withContext(Dispatchers.IO) {
            runCatching { postChat(abstractText, port, timeoutOverrideS) }.getOrNull()
        } ?: return null
        val parsed = parseExtractionJson(raw)
        if (parsed == null) return null
        return toResult(parsed, System.currentTimeMillis() - start)
    }

    /** Blocking HTTP call — only from [Dispatchers.IO]. Returns the model's message content. */
    private fun postChat(abstractText: String, port: Int, timeoutOverrideS: Long?): String {
        val readTimeout = TimeUnit.SECONDS.toMillis(timeoutOverrideS ?: READ_TIMEOUT_S)
        val client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
            .build()
        val bodyJson = """
            {
              "messages": [
                {"role": "system", "content": ${jsonString(SYSTEM_PROMPT)}},
                {"role": "user", "content": ${jsonString(buildExtractionPrompt(abstractText))}}
              ],
              "temperature": 0,
              "max_tokens": $MAX_TOKENS,
              "stream": false,
              "chat_template_kwargs": {"enable_thinking": false}
            }
        """.trimIndent()
        val body = bodyJson.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("http://127.0.0.1:$port/v1/chat/completions")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.w(TAG, "Local LLM HTTP ${response.code}")
                return ""
            }
            // choices[0].message.content — located without a JSON library so this
            // stays testable on the plain JVM toolchain.
            return extractChatContent(text)
        }
    }

    /**
     * Reads `choices[0].message.content` out of an OpenAI-compatible response
     * envelope. The value is a JSON string, so it must be unescaped; the model
     * usually wraps its object in a Markdown fence, which the caller tolerates.
     */
    fun extractChatContent(responseText: String): String {
        val contentStart = responseText.indexOf("\"content\"")
        if (contentStart < 0) return ""
        val colon = responseText.indexOf(':', contentStart + "\"content\"".length)
        if (colon < 0) return ""
        return extractString(responseText, colon + 1).orEmpty()
    }

    /**
     * Pulls the first balanced JSON object out of a model reply, tolerating
     * markdown fences and stray prose. Returns null when no balanced object exists.
     */
    fun parseExtractionJson(raw: String): LlmExtraction? {
        if (raw.isBlank()) return null
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var end = -1
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            when {
                escaped -> escaped = false
                c == '\\' -> if (inString) escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        if (end < 0) return null
        val json = raw.substring(start, end + 1)

        val gene = stringField(json, "gene")?.uppercase(Locale.US)?.takeIf { it.length in 2..8 }
        val variant = stringField(json, "variant")
        val brainEvents = intField(json, "brain_events")
        val brainTotal = intField(json, "brain_total")
        val otherEvents = intField(json, "other_events")
        val otherTotal = intField(json, "other_total")
        val hasAnyNumber = listOf(brainEvents, brainTotal, otherEvents, otherTotal, doubleField(json, "odds_ratio"))
            .any { it != null }
        if (gene == null && !hasAnyNumber) return null

        return LlmExtraction(
            gene = gene,
            variant = variant?.uppercase(Locale.US),
            brainEvents = brainEvents,
            brainTotal = brainTotal,
            otherEvents = otherEvents,
            otherTotal = otherTotal,
            oddsRatio = doubleField(json, "odds_ratio"),
            ciLow = doubleField(json, "ci_low"),
            ciHigh = doubleField(json, "ci_high"),
            confidence = doubleField(json, "confidence"),
            summary = stringField(json, "summary")
        )
    }

    /** Maps a parsed reply onto the extraction contract the rest of the app already uses. */
    fun toResult(e: LlmExtraction, elapsedMs: Long): LocalModelExtractionResult {
        val hasCounts = e.brainEvents != null && e.brainTotal != null &&
            e.otherEvents != null && e.otherTotal != null
        val evidenceGrade = when {
            hasCounts -> EvidenceGrade.COUNTS
            e.oddsRatio != null -> EvidenceGrade.SUMMARY
            else -> EvidenceGrade.ABSTRACT_ONLY
        }
        val modality = when {
            e.summary?.contains("ctDNA", true) == true || e.summary?.contains("plasma", true) == true ->
                "Liquid Biopsy / Plasma ctDNA Only"
            e.summary?.contains("tissue", true) == true -> "Tissue Biopsy Only"
            else -> "Genomic Panel Assays"
        }
        val summary = e.summary?.takeIf { it.isNotBlank() }
            ?: "Local LLM extraction completed."
        val confidence = (e.confidence ?: if (hasCounts) 0.95 else 0.70).coerceIn(0.0, 1.0)
        return LocalModelExtractionResult(
            gene = e.gene,
            variant = e.variant,
            brainEvents = e.brainEvents,
            brainTotal = e.brainTotal,
            otherEvents = e.otherEvents,
            otherTotal = e.otherTotal,
            oddsRatio = e.oddsRatio,
            ciLow = e.ciLow,
            ciHigh = e.ciHigh,
            evidenceGrade = evidenceGrade,
            modalityCompared = modality,
            qualitativeSummary = summary,
            confidenceScore = confidence,
            executionTimeMs = elapsedMs
        )
    }

    // ---- tiny, dependency-free JSON field readers ---------------------------

    private fun stringField(json: String, key: String): String? {
        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return null
        val colon = json.indexOf(':', keyIdx + key.length + 2)
        if (colon < 0) return null
        return extractString(json, colon + 1)
    }

    /** Reads a quoted string starting at [from], handling escapes. */
    private fun extractString(text: String, from: Int): String? {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != '"') return null
        val sb = StringBuilder()
        i++
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> {
                    when (val next = text[i + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = text.drop(i + 2).take(4)
                            hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                            i += 4
                        }
                        else -> sb.append(next)
                    }
                    i += 2
                }
                c == '"' -> return sb.toString()
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return null
    }

    /** Reads a number (or a numeric string) after the key, or null. */
    private fun numericField(json: String, key: String): String? {
        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return null
        var i = json.indexOf(':', keyIdx + key.length + 2)
        if (i < 0) return null
        i++
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length) return null
        return when {
            json[i] == '"' -> extractString(json, i)?.trim()?.takeIf { it.isNotEmpty() }
            else -> {
                val sb = StringBuilder()
                while (i < json.length && (json[i].isDigit() || json[i] in "+-.")) {
                    sb.append(json[i])
                    i++
                }
                sb.toString().takeIf { it.isNotEmpty() }
            }
        }
    }

    private fun intField(json: String, key: String): Int? =
        numericField(json, key)?.toDoubleOrNull()?.let { if (it.isFinite()) it.toInt() else null }

    private fun doubleField(json: String, key: String): Double? =
        numericField(json, key)?.toDoubleOrNull()?.takeIf { it.isFinite() }

    /** Minimal JSON string escaping for the request body. */
    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                '\r' -> append("\\r")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}

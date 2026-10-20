package com.example.concordance.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Typed decisions on the phone.
 *
 * A decision is a question with a closed answer set - never free text. The rules
 * backend is deterministic and always available; the on-device LLM is used only
 * when the operator has started it, and only when its answer clears a confidence
 * floor. Anything malformed, slow, or unsure falls back to the rules, with the
 * reason carried in the result so the UI can say what actually happened.
 *
 * This mirrors the System One contract used by the agent (typed questions in,
 * calibrated answers out) without needing any server off the device.
 */

enum class DecisionType { CHOICE, NOUL, SCORE }

data class DecisionQuestion(
    val name: String,
    val type: DecisionType,
    val instructions: String,
    /** Allowed keys for CHOICE, in display order. Ignored for NOUL/SCORE. */
    val criteria: Map<String, String> = emptyMap()
)

data class DecisionAnswer(
    val name: String,
    val type: DecisionType,
    val choice: String? = null,
    val noul: Double? = null,
    val score: Int? = null,
    val confidence: Double = 0.0,
    val abstained: Boolean = false
)

enum class DecisionBackend(val label: String) {
    RULES("Rules (offline)"),
    ON_DEVICE_LLM("On-device LLM")
}

data class DecisionOutcome(
    val answers: Map<String, DecisionAnswer>,
    val backend: DecisionBackend,
    /** Null when the backend answered cleanly. */
    val fallbackReason: String? = null
)

/** Everything the review gate is allowed to look at. No patient identifiers. */
data class ExtractionState(
    val abstractText: String,
    val gene: String?,
    val variant: String?,
    val brainEvents: Int?,
    val brainTotal: Int?,
    val otherEvents: Int?,
    val otherTotal: Int?,
    val oddsRatio: Double?,
    val reportedConfidence: Double
)

/** What the UI shows next to an extracted claim. */
data class ExtractionReview(
    val route: String,
    val reason: String,
    val support: Double,
    val confidence: Double,
    val backend: DecisionBackend,
    val fallbackReason: String? = null
) {
    val needsHuman: Boolean get() = route == ROUTE_REVIEW
}

const val ROUTE_ACCEPT = "accept"
const val ROUTE_REVIEW = "review"

object DecisionModel {

    /** Below this, an answer is not trusted enough to replace the rules. */
    const val CONFIDENCE_FLOOR = 0.6

    /** Tall enough for a 4B CPU model to answer three short questions. */
    private const val READ_TIMEOUT_S = 60L

    private val REVIEW_QUESTIONS = listOf(
        DecisionQuestion(
            name = "tier",
            type = DecisionType.CHOICE,
            instructions = "Which evidence tier does the extracted data actually reach?",
            criteria = linkedMapOf(
                "counts" to "Both arms have event and total counts",
                "summary" to "Only a summary effect size (e.g. an odds ratio)",
                "abstract_only" to "Neither counts nor an effect size were found"
            )
        ),
        DecisionQuestion(
            name = "route",
            type = DecisionType.CHOICE,
            instructions = "Should this extraction be auto-accepted or checked by a human?",
            criteria = linkedMapOf(
                "accept" to "Grounded in the abstract and internally consistent",
                ROUTE_REVIEW to "Thin, inconsistent, or only partly grounded"
            )
        ),
        DecisionQuestion(
            name = "support",
            type = DecisionType.NOUL,
            instructions = "Is every extracted number traceable to the abstract text?"
        )
    )

    /**
     * Review an extraction. [llmPort] is non-null only when the embedded llama.cpp
     * server is running and the operator selected it.
     */
    suspend fun review(state: ExtractionState, llmPort: Int?): ExtractionReview {
        val rules = ruleAnswers(state)
        if (llmPort == null) return toReview(rules, DecisionBackend.RULES, null)
        val remote = withContext(Dispatchers.IO) {
            runCatching { askLocalModel(llmPort, state) }.getOrNull()
        } ?: return toReview(rules, DecisionBackend.RULES, "llm_unusable")
        return mergeWithRemote(rules, remote)
    }

    /** Accept a remote answer only when it is valid and confident enough. */
    internal fun mergeWithRemote(
        rules: Map<String, DecisionAnswer>,
        remote: Map<String, DecisionAnswer>
    ): ExtractionReview {
        val route = remote["route"]
        if (route == null || route.abstained || route.choice == null) {
            return toReview(rules, DecisionBackend.RULES, "llm_unusable")
        }
        if (route.confidence < CONFIDENCE_FLOOR) {
            return toReview(rules, DecisionBackend.RULES, "low_confidence")
        }
        return toReview(remote, DecisionBackend.ON_DEVICE_LLM, null)
    }

    private fun toReview(
        answers: Map<String, DecisionAnswer>,
        backend: DecisionBackend,
        fallbackReason: String?
    ): ExtractionReview {
        val tier = answers["tier"]?.choice ?: "abstract_only"
        val route = answers["route"]?.choice ?: ROUTE_REVIEW
        val support = answers["support"]?.noul ?: 0.0
        val confidence = answers["route"]?.confidence ?: 0.0
        return ExtractionReview(
            route = route,
            reason = reasonFor(route, tier, support),
            support = support,
            confidence = confidence,
            backend = backend,
            fallbackReason = fallbackReason
        )
    }

    /** The sentence is written here, not by the model: the model only picks a key. */
    internal fun reasonFor(route: String, tier: String, support: Double): String {
        val pct = (support * 100).toInt()
        return if (route == ROUTE_ACCEPT) {
            "Grounded in the abstract (support $pct%, tier $tier). Auto-accepted; you can still edit it."
        } else {
            "Support $pct%, tier $tier. Check the abstract before importing."
        }
    }

    // ---- deterministic rules ------------------------------------------------

    internal fun ruleAnswers(state: ExtractionState): Map<String, DecisionAnswer> {
        val tier = ruleTier(state)
        val support = ruleSupport(state)
        val review = support < 0.65 || tier == "abstract_only" || state.reportedConfidence < 0.6
        return linkedMapOf(
            "tier" to DecisionAnswer(
                name = "tier", type = DecisionType.CHOICE, choice = tier, confidence = 0.8
            ),
            "route" to DecisionAnswer(
                name = "route",
                type = DecisionType.CHOICE,
                choice = if (review) ROUTE_REVIEW else ROUTE_ACCEPT,
                confidence = 0.85
            ),
            "support" to DecisionAnswer(
                name = "support", type = DecisionType.NOUL, noul = support
            )
        )
    }

    internal fun ruleTier(state: ExtractionState): String {
        val counts = consistentCounts(state)
        return when {
            counts -> "counts"
            state.oddsRatio != null && state.oddsRatio > 0.0 -> "summary"
            else -> "abstract_only"
        }
    }

    internal fun ruleSupport(state: ExtractionState): Double {
        var support = 0.50
        val hasCounts = state.brainEvents != null && state.brainTotal != null &&
            state.otherEvents != null && state.otherTotal != null
        when {
            consistentCounts(state) -> support += 0.20
            // Counts that contradict themselves (events above the total) are
            // worse than no counts: they are a concrete sign of a bad extraction.
            hasCounts -> support -= 0.25
        }
        if (state.oddsRatio != null && state.oddsRatio > 0.0 && state.oddsRatio < 50.0) support += 0.15
        val text = state.abstractText.lowercase(Locale.US)
        val gene = state.gene?.lowercase(Locale.US)?.takeIf { it.isNotBlank() }
        if (gene != null) {
            support += if (text.contains(gene)) 0.10 else -0.25
        }
        return (support.coerceIn(0.0, 1.0) * 100).toInt() / 100.0
    }

    private fun consistentCounts(state: ExtractionState): Boolean {
        val be = state.brainEvents ?: return false
        val bt = state.brainTotal ?: return false
        val oe = state.otherEvents ?: return false
        val ot = state.otherTotal ?: return false
        return bt > 0 && ot > 0 && be in 0..bt && oe in 0..ot && be + oe <= bt + ot
    }

    // ---- on-device model ----------------------------------------------------

    private const val SYSTEM_PROMPT =
        "You answer closed questions about an extracted claim. Reply with ONE JSON object " +
            "and no other text. Use only the allowed keys. Never invent numbers."

    /** Flat schema: easy to parse, easy to reject. Returns null when unusable. */
    fun buildPrompt(state: ExtractionState): String {
        val allowedTiers = REVIEW_QUESTIONS.first { it.name == "tier" }.criteria.keys.joinToString("|")
        return buildString {
            append("Abstract:\n")
            append(state.abstractText.take(3000))
            append("\n\nExtraction: gene=").append(state.gene ?: "null")
            append(", variant=").append(state.variant ?: "null")
            append(", brain=").append(state.brainEvents ?: "null").append("/").append(state.brainTotal ?: "null")
            append(", other=").append(state.otherEvents ?: "null").append("/").append(state.otherTotal ?: "null")
            append(", odds_ratio=").append(state.oddsRatio ?: "null")
            append("\n\nReply exactly:\n")
            append("{\"support\": <0.0-1.0>, \"tier\": \"<$allowedTiers>\", ")
            append("\"route\": \"<accept|review>\", \"confidence\": <0.0-1.0>}")
            append("\nsupport = how well the abstract backs the extraction; ")
            append("confidence = how sure you are of route.")
        }
    }

    internal fun askLocalModel(
        port: Int,
        state: ExtractionState
    ): Map<String, DecisionAnswer>? {
        val client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
        val bodyJson = """
            {
              "messages": [
                {"role": "system", "content": ${jsonString(SYSTEM_PROMPT)}},
                {"role": "user", "content": ${jsonString(buildPrompt(state))}}
              ],
              "temperature": 0,
              "max_tokens": 160,
              "stream": false,
              "chat_template_kwargs": {"enable_thinking": false}
            }
        """.trimIndent()
        val request = Request.Builder()
            .url("http://127.0.0.1:$port/v1/chat/completions")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body?.string().orEmpty()
            val content = LocalExtractionClient.extractChatContent(text)
            return parseFlatDecision(content)
        }
    }

    /**
     * Parse and validate the flat reply. Anything outside the allowed keys, any
     * missing field, or any out-of-range number rejects the whole answer - a
     * partially-readable decision is not a decision.
     */
    fun parseFlatDecision(raw: String): Map<String, DecisionAnswer>? {
        if (raw.isBlank()) return null
        val json = firstJsonObject(raw) ?: return null
        val tier = JsonFields.string(json, "tier")?.lowercase(Locale.US) ?: return null
        val route = JsonFields.string(json, "route")?.lowercase(Locale.US) ?: return null
        if (tier !in setOf("counts", "summary", "abstract_only")) return null
        if (route !in setOf(ROUTE_ACCEPT, ROUTE_REVIEW)) return null
        val support = JsonFields.number(json, "support")?.takeIf { it in 0.0..1.0 } ?: return null
        val confidence = JsonFields.number(json, "confidence")?.takeIf { it in 0.0..1.0 } ?: return null
        return linkedMapOf(
            "tier" to DecisionAnswer("tier", DecisionType.CHOICE, choice = tier, confidence = confidence),
            "route" to DecisionAnswer("route", DecisionType.CHOICE, choice = route, confidence = confidence),
            "support" to DecisionAnswer("support", DecisionType.NOUL, noul = support)
        )
    }

    /** First balanced JSON object, tolerating fences and prose around it. */
    internal fun firstJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
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
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /** Minimal readers: org.json is not available on the plain JVM test toolchain. */
    internal object JsonFields {
        fun string(json: String, key: String): String? {
            val idx = json.indexOf("\"$key\"")
            if (idx < 0) return null
            val colon = json.indexOf(':', idx + key.length + 2)
            if (colon < 0) return null
            var i = colon + 1
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] != '"') return null
            val end = json.indexOf('"', i + 1)
            if (end < 0) return null
            return json.substring(i + 1, end)
        }

        fun number(json: String, key: String): Double? {
            val idx = json.indexOf("\"$key\"")
            if (idx < 0) return null
            val colon = json.indexOf(':', idx + key.length + 2)
            if (colon < 0) return null
            var i = colon + 1
            while (i < json.length && json[i].isWhitespace()) i++
            val start = i
            while (i < json.length && (json[i].isDigit() || json[i] in "+-.")) i++
            return json.substring(start, i).toDoubleOrNull()?.takeIf { it.isFinite() }
        }
    }

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

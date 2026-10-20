package com.example.concordance.integration.knowledge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Read-only INDRA client that runs on the phone.
 *
 * The public database is slow (measured 13-84 s for a filtered query), so:

 *  - results are cached in memory for [CACHE_TTL_MS] and repeats are instant;
 *  - every failure is an [IndraResult.Unavailable] rather than an exception;
 *  - only short entity tokens are accepted, so free text or patient material
 *    can never be sent to a public resource by accident.
 *
 * No PHI: the query is a gene/protein name and nothing else.
 */
class IndraClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    timeoutSeconds: Long = 150
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    private val cache = object :
        LinkedHashMap<String, Pair<Long, List<IndraStatement>>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Pair<Long, List<IndraStatement>>>?
        ): Boolean = size > CACHE_MAX
    }
    private val cacheLock = Any()

    /** What public sources already report about [subject] (optionally towards [objectName]). */
    suspend fun priorKnowledge(
        subject: String,
        objectName: String? = null,
        limit: Int = 10,
        minBelief: Double = 0.0
    ): IndraResult<IndraKnowledge> {
        val s = subject.trim()
        val o = objectName?.trim()?.takeIf { it.isNotBlank() }
        entityError(s)?.let { return IndraResult.Refused(it) }
        o?.let { entityError(it)?.let { error -> return IndraResult.Refused(error) } }

        val fetched = when (val result = statements(s, o, limit)) {
            is IndraResult.Ok -> result.value
            is IndraResult.Unavailable -> return result
            is IndraResult.Refused -> return result
        }
        val kept = fetched.filter { it.belief >= minBelief }
        val types = kept.groupingBy { it.statementType }.eachCount()
            .entries.sortedByDescending { it.value }
            .associate { it.key to it.value }
        return IndraResult.Ok(
            IndraKnowledge(
                subject = s,
                objectName = o,
                statementCount = kept.size,
                types = types,
                statements = kept,
                disclaimer = PRIOR_DISCLAIMER
            )
        )
    }

    /** Opposing statement types about the same pair; a review trigger, not a resolution. */
    suspend fun contradictions(
        subject: String,
        objectName: String,
        limit: Int = 40,
        minBelief: Double = 0.5
    ): IndraResult<IndraContradictions> {
        val s = subject.trim()
        val o = objectName.trim()
        entityError(s)?.let { return IndraResult.Refused(it) }
        entityError(o)?.let { return IndraResult.Refused(it) }

        val fetched = when (val result = statements(s, o, limit)) {
            is IndraResult.Ok -> result.value
            is IndraResult.Unavailable -> return result
            is IndraResult.Refused -> return result
        }
        val byType = fetched.filter { it.belief >= minBelief }.groupBy { it.statementType }
        val pairs = mutableListOf<IndraContradictionPair>()
        val seen = mutableSetOf<Set<String>>()
        for ((type, items) in byType) {
            for (opposite in OPPOSITE_TYPES[type].orEmpty()) {
                val opposing = byType[opposite] ?: continue
                val key = setOf(type, opposite)
                if (!seen.add(key)) continue
                pairs += IndraContradictionPair(
                    types = key.sorted(),
                    positive = items.first(),
                    negative = opposing.first(),
                    beliefGap = round4(kotlin.math.abs(items.first().belief - opposing.first().belief))
                )
            }
        }
        return IndraResult.Ok(
            IndraContradictions(
                subject = s,
                objectName = o,
                contradicted = pairs.isNotEmpty(),
                pairs = pairs.sortedBy { it.beliefGap },
                disclaimer = CONTRADICTION_DISCLAIMER
            )
        )
    }

    /** Visible for tests: rejects anything that is not a short public entity token. */
    fun entityError(value: String): String? = when {
        value.isBlank() -> "Enter a gene or protein name."
        !ENTITY_TOKEN.matches(value) ->
            "INDRA accepts gene/protein names only (letters, digits, - . _). " +
                "Free text and patient material stay on this device."
        else -> null
    }

    private suspend fun statements(
        subject: String,
        objectName: String?,
        limit: Int
    ): IndraResult<List<IndraStatement>> {
        val url = buildString {
            append(baseUrl.trimEnd('/'))
            append("/statements/from_agents?format=json")
            append("&limit=").append(limit.coerceIn(1, MAX_LIMIT))
            append("&ev_limit=2")
            append("&subject=").append(encode(subject))
            if (objectName != null) append("&object=").append(encode(objectName))
        }
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            cache[url]?.let { (at, cached) ->
                if (now - at < CACHE_TTL_MS) return IndraResult.Ok(cached)
            }
        }

        return try {
            val parsed = withContext(Dispatchers.IO) {
                http.newCall(
                    Request.Builder().url(url).header("Accept", "application/json").build()
                ).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use IndraResult.Unavailable("INDRA returned HTTP ${response.code}.")
                    }
                    val body = response.body?.string().orEmpty()
                    try {
                        IndraResult.Ok(parseStatements(body))
                    } catch (t: Throwable) {
                        IndraResult.Unavailable("INDRA returned an unreadable response.")
                    }
                }
            }
            if (parsed is IndraResult.Ok) {
                synchronized(cacheLock) { cache[url] = now to parsed.value }
            }
            parsed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            IndraResult.Unavailable(t.message ?: "INDRA request failed.")
        }
    }

    /** Accepts both {"results": {...}} and a bare {hash: statement} payload. */
    internal fun parseStatements(body: String): List<IndraStatement> {
        val root = JSONObject(body)
        val results = root.optJSONObject("results") ?: root
        val out = mutableListOf<IndraStatement>()
        results.keys().forEach { hash ->
            val stmt = results.optJSONObject(hash) ?: return@forEach
            val type = stmt.optString("type").clean()
            if (type.isBlank()) return@forEach
            val subject = agentName(stmt.opt("sub")).ifBlank {
                (stmt.opt("members") as? JSONArray)
                    ?.let { members ->
                        (0 until members.length())
                            .map { agentName(members.opt(it)) }
                            .filter { it.isNotBlank() }
                            .joinToString(", ")
                    }
                    .orEmpty()
            }
            val evidence = evidence(stmt.optJSONArray("evidence"))
            out += IndraStatement(
                statementType = type,
                subject = subject,
                objectName = agentName(stmt.opt("obj")),
                belief = round4(stmt.optDouble("belief", 0.0)),
                sources = evidence.map { it.source }.filter { it.isNotBlank() }.distinct().sorted(),
                evidence = evidence,
                url = "${baseUrl.trimEnd('/')}/statements/from_hash/$hash"
            )
        }
        return out.sortedByDescending { it.belief }
    }

    private fun evidence(items: JSONArray?): List<IndraEvidence> {
        if (items == null) return emptyList()
        val out = mutableListOf<IndraEvidence>()
        for (i in 0 until minOf(items.length(), MAX_EVIDENCE)) {
            val item = items.optJSONObject(i) ?: continue
            val refs = item.optJSONObject("text_refs")
            out += IndraEvidence(
                text = item.optString("text").clean().take(600),
                source = item.optString("source_api").clean(),
                pmid = item.optString("pmid").clean()
                    .ifBlank { refs?.optString("PMID").orEmpty().clean() },
                doi = item.optString("doi").clean()
                    .ifBlank { refs?.optString("DOI").orEmpty().clean() }
            )
        }
        return out
    }

    private fun agentName(value: Any?): String = when (value) {
        null -> ""
        is JSONObject -> value.optString("name").clean()
        else -> value.toString().clean()
    }

    private fun String.clean(): String = if (this == "null") "" else this

    private fun round4(value: Double): Double = kotlin.math.round(value * 10_000) / 10_000

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val DEFAULT_BASE_URL = "https://db.indra.bio"
        const val CACHE_TTL_MS = 15 * 60 * 1000L
        const val CACHE_MAX = 32
        const val MAX_LIMIT = 50
        const val MAX_EVIDENCE = 5

        private val ENTITY_TOKEN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,23}$")

        /**
         * Statement types that oppose each other. Only pairs with a direction are
         * listed; a Phosphorylation has no single negation in INDRA's vocabulary.
         */
        val OPPOSITE_TYPES: Map<String, Set<String>> = mapOf(
            "Activation" to setOf("Inhibition"),
            "Inhibition" to setOf("Activation"),
            "IncreaseAmount" to setOf("DecreaseAmount"),
            "DecreaseAmount" to setOf("IncreaseAmount"),
            "Phosphorylation" to setOf("Dephosphorylation"),
            "Dephosphorylation" to setOf("Phosphorylation"),
            "Ubiquitination" to setOf("Deubiquitination"),
            "Deubiquitination" to setOf("Ubiquitination"),
            "Methylation" to setOf("Demethylation"),
            "Demethylation" to setOf("Methylation"),
            "Acetylation" to setOf("Deacetylation"),
            "Deacetylation" to setOf("Acetylation"),
            "Gef" to setOf("Gap"),
            "Gap" to setOf("Gef")
        )

        private const val PRIOR_DISCLAIMER =
            "Public literature/database knowledge with model-estimated belief; " +
                "background only, not a clinical conclusion."

        private const val CONTRADICTION_DISCLAIMER =
            "Opposing statements in public sources are a review trigger, not a " +
                "resolved answer. Human review decides."
    }
}

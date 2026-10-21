package com.example.concordance.integration.agent

import com.example.concordance.integration.api.Provenance
import com.example.concordance.integration.api.ProvenanceRecorder
import com.example.concordance.integration.rotation.LicenseClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Typed client for the self-hosted oncology adjudication agent.
 *
 * Design rules carried over from the agent:
 * - A refusal is a normal outcome ([AgentOutcome.Refused]), never an error.
 * - Response bodies are never logged; only a SHA-256 digest is recorded (provenance).
 * - Uploaded figures go to `POST /api/imaging/ocr`, which OCRs **on the agent host**
 *   (PaddleOCR). No managed platform is involved in any path here.
 */
class AgentClient(
    private val http: OkHttpClient = defaultHttpClient()
) {

    companion object {
        const val PROVIDER_ID = "oncology_agent"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // OCR of a large PDF page can take a while; the read timeout must not
            // cut off a slow-but-legitimate local inference.
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        private val JSON = "application/json".toMediaType()

        /** Keys of `what_we_found` / `the_numbers`, in a stable display order. */
        private val FOUND_ORDER = listOf(
            "verdict", "why", "reads_as", "difference_after_correction", "likely_range",
            "sample_size", "power"
        )
        private val NUMBERS_ORDER = listOf(
            "verdict", "evidence_tier", "corrected_odds_ratio", "likely_range",
            "leak_factor_r", "leak_threshold_r_star", "difference_after_correction", "status"
        )
    }

    // ---- endpoints ----

    suspend fun health(config: AgentConfig): AgentOutcome<String> =
        call(config, "GET", "/health", null, "health")

    suspend fun research(config: AgentConfig, query: String, useLlm: Boolean = true): AgentOutcome<AgentResearchResult> {
        val body = JSONObject().apply {
            put("query", query)
            put("data_class", "research")
            put("use_llm", useLlm)
        }.toString().toRequestBody(JSON)
        return parseBody(call(config, "POST", "/api/research", body, "research"), "research") { text ->
            val o = JSONObject(text)
            val report = o.optJSONObject("report")
            val experiments = mutableListOf<AgentExperiment>()
            report?.optJSONArray("experiments")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    experiments.add(
                        AgentExperiment(
                            title = e.optString("title"),
                            design = e.optString("design"),
                            controls = e.optString("controls"),
                            endpoints = e.optString("endpoints"),
                            estimatedDuration = e.optString("estimated_duration"),
                            risk = e.optString("risk")
                        )
                    )
                }
            }
            fun jsonArray(key: String): List<String> {
                val a = report?.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).map { a.optString(it) }
            }
            val decision = o.optJSONObject("decision")
            val actionCounts = mutableMapOf<String, Int>()
            decision?.optJSONObject("actions")?.let { a ->
                a.keys().forEach { k -> actionCounts[k] = a.optInt(k) }
            }
            val decisions = mutableListOf<AgentDecision>()
            decision?.optJSONArray("decisions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val d = arr.optJSONObject(i) ?: continue
                    val reasons = mutableListOf<String>()
                    d.optJSONArray("reasons")?.let { r ->
                        for (j in 0 until r.length()) reasons.add(r.optString(j))
                    }
                    decisions.add(
                        AgentDecision(
                            candidate = d.optString("candidate"),
                            step = d.optString("step"),
                            action = d.optString("action"),
                            score = d.optDouble("score", 0.0),
                            confidence = d.optDouble("confidence", 0.0),
                            reasons = reasons
                        )
                    )
                }
            }
            val critic = o.optJSONObject("critic")
            val findings = mutableListOf<String>()
            critic?.optJSONArray("findings")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val f = arr.optJSONObject(i) ?: continue
                    findings.add("${f.optString("severity")}: ${f.optString("message")}")
                }
            }
            val paper = o.optJSONObject("paper")
            val llm = o.optJSONObject("llm")
            AgentResearchResult(
                query = o.optString("query", query),
                runId = o.optString("run_id"),
                summary = report?.optString("summary").orEmpty(),
                literatureQueries = jsonArray("literature_queries"),
                hypotheses = jsonArray("hypotheses"),
                experiments = experiments,
                nextSteps = jsonArray("next_steps"),
                actionCounts = actionCounts,
                decisions = decisions,
                criticVerdict = critic?.optString("verdict").orEmpty(),
                criticScore = critic?.optDouble("score", 0.0) ?: 0.0,
                criticFindings = findings,
                paperDir = paper?.optString("dir").orEmpty(),
                paperArtifacts = paper?.optInt("artifacts", 0) ?: 0,
                llmUsed = llm?.optBoolean("used", false) ?: false,
                llmOk = llm?.optBoolean("ok", false) ?: false,
                scaffold = o.optBoolean("scaffold", false),
                rawJson = text
            )
        }
    }

    suspend fun anchors(config: AgentConfig): AgentOutcome<List<AgentAnchor>> =
        parseBody(call(config, "GET", "/api/anchors", null, "anchors"), "anchors") { body ->
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AgentAnchor(
                    id = o.optString("id"),
                    label = o.optString("label"),
                    evidenceGrade = o.optString("evidence_grade", "unknown"),
                    source = o.optString("source")
                )
            }
        }

    suspend fun adjudicate(
        config: AgentConfig,
        anchorId: String,
        dataClass: String = "aggregate",
        counts: Map<String, Int>? = null,
        pairedCsvPath: String? = null
    ): AgentOutcome<AgentJob> {
        val payload = JSONObject().apply {
            put("anchor_id", anchorId)
            put("data_class", dataClass)
            if (counts != null && counts.isNotEmpty()) {
                put("counts", JSONObject().apply { counts.forEach { (k, v) -> put(k, v) } })
            }
            if (!pairedCsvPath.isNullOrBlank()) put("paired_csv_path", pairedCsvPath)
        }
        return parseBody(
            call(config, "POST", "/api/adjudicate", payload.toString().toRequestBody(JSON), "adjudicate:$anchorId"),
            "adjudicate"
        ) { parseJob(it) }
    }

    suspend fun job(config: AgentConfig, jobId: String): AgentOutcome<AgentJob> =
        parseBody(call(config, "GET", "/api/jobs/$jobId", null, "job:$jobId"), "job") { parseJob(it) }

    suspend fun refereeAudit(
        config: AgentConfig,
        claim: JSONObject,
        csvText: String,
        dataClass: String = "phi",
        targetBackend: String = "local"
    ): AgentOutcome<AgentRefereeReport> {
        val payload = JSONObject().apply {
            put("claim", claim)
            put("csv_text", csvText)
            put("data_class", dataClass)
            put("target_backend", targetBackend)
        }
        return parseBody(
            call(config, "POST", "/api/referee/audit", payload.toString().toRequestBody(JSON), "referee:${claim.optString("claim_id")}"),
            "referee_audit"
        ) { body ->
            val o = JSONObject(body)
            AgentRefereeReport(
                verdict = o.optString("verdict", "UNVERIFIABLE"),
                reasons = (o.optJSONArray("reasons") ?: JSONArray()).let { a -> (0 until a.length()).map { a.optString(it) } },
                dimensions = (o.optJSONObject("dimensions") ?: JSONObject()).let { d -> d.keys().asSequence().associateWith { d.optString(it) } },
                inputSha256 = o.optString("input_sha256").ifBlank { null },
                decisionSha256 = o.optString("decision_sha256").ifBlank { null },
                reportSha256 = o.optString("report_sha256").ifBlank { null },
                provenance = o.optString("provenance").ifBlank { null },
                disposition = o.optString("disposition").ifBlank { null }
            )
        }
    }

    /** The agent's own plain-language translation of a completed job. */
    suspend fun explainJob(config: AgentConfig, jobId: String): AgentOutcome<EasyModeView> =
        parseBody(call(config, "GET", "/api/jobs/$jobId/explain", null, "explain:$jobId"), "explain") {
            parseEasyMode(JSONObject(it))
        }

    suspend fun correction(
        config: AgentConfig,
        anchorId: String,
        site: String? = null,
        sn: Double? = null,
        explain: Boolean = false
    ): AgentOutcome<CorrectionResult> {
        val payload = JSONObject().apply {
            put("anchor_id", anchorId)
            if (site != null) put("site", site)
            if (sn != null) put("s_n", sn)
        }
        val path = if (explain) "/api/correction?explain=true" else "/api/correction"
        return parseBody(
            call(config, "POST", path, payload.toString().toRequestBody(JSON), "correction:$anchorId"),
            "correction"
        ) { body ->
            val obj = JSONObject(body)
            // With explain=true the agent returns {mode, result, plain}.
            val result = obj.optJSONObject("result") ?: obj
            parseCorrection(result, obj.optJSONObject("plain"))
        }
    }

    suspend fun correctionEasyMode(
        config: AgentConfig,
        anchorId: String,
        site: String? = null,
        sn: Double? = null
    ): AgentOutcome<EasyModeView> {
        val payload = JSONObject().apply {
            put("anchor_id", anchorId)
            if (site != null) put("site", site)
            if (sn != null) put("s_n", sn)
        }
        return parseBody(
            call(config, "POST", "/api/explain/correction", payload.toString().toRequestBody(JSON), "explain-correction:$anchorId"),
            "explain-correction"
        ) { parseEasyMode(JSONObject(it)) }
    }

    suspend fun imagingStatus(config: AgentConfig): AgentOutcome<ImagingStatus> =
        parseBody(call(config, "GET", "/api/imaging/status", null, "imaging-status"), "imaging-status") { body ->
            val o = JSONObject(body)
            val types = o.optJSONArray("accepted_media_types") ?: JSONArray()
            ImagingStatus(
                ocrAvailable = o.optBoolean("ocr_available", false),
                pdfAvailable = o.optBoolean("pdf_available", false),
                maxUploadMb = o.optInt("max_upload_mb", 25),
                acceptedMediaTypes = (0 until types.length()).map { types.optString(it) },
                notes = o.optString("notes")
            )
        }

    /**
     * Upload a figure or PDF for OCR on the agent host.
     *
     * `dataClass` defaults to `phi` on the server when omitted; PHI is only accepted
     * for `local`/`aws_baa` backends, so a misrouted upload is refused by the agent.
     */
    suspend fun uploadFigure(
        config: AgentConfig,
        bytes: ByteArray,
        fileName: String,
        contentType: String,
        dataClass: String = "phi",
        targetBackend: String = "local"
    ): AgentOutcome<UploadedFigure> {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(contentType.toMediaType()))
            .addFormDataPart("data_class", dataClass)
            .addFormDataPart("target_backend", targetBackend)
            .build()
        return parseBody(
            call(config, "POST", "/api/imaging/ocr", multipart, "imaging-ocr:$fileName"),
            "imaging-ocr"
        ) { body ->
            val o = JSONObject(body)
            val pages = o.optJSONArray("pages")
            UploadedFigure(
                uploadSha256 = o.optString("upload_sha256"),
                contentType = o.optString("content_type"),
                bytes = o.optInt("bytes"),
                dataClass = o.optString("data_class"),
                engine = o.optString("engine", "none"),
                text = o.optString("text"),
                confidence = o.optDouble("confidence", 0.0),
                pageCount = pages?.length() ?: 0,
                evidenceTier = o.optString("evidence_tier", "derived"),
                humanVerified = o.optBoolean("human_verified", false),
                adjudicationReady = o.optBoolean("adjudication_ready", false),
                disclaimer = o.optString("disclaimer")
            )
        }
    }

    // ---- transport ----

    private val clients = java.util.concurrent.ConcurrentHashMap<Long, OkHttpClient>()

    /**
     * One client per configured timeout: the read timeout must be long enough for a slow
     * OCR pass, but rebuilding a client per request would throw away connection reuse.
     */
    private fun clientFor(timeoutSeconds: Long): OkHttpClient {
        val safe = timeoutSeconds.coerceIn(5L, 600L)
        return clients.computeIfAbsent(safe) {
            http.newBuilder()
                .readTimeout(it, TimeUnit.SECONDS)
                .writeTimeout(it, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * Perform one call and map the HTTP status onto an [AgentOutcome].
     *
     * Returns [AgentOutcome.Ok] carrying the raw body text for 2xx responses.
     */
    private suspend fun call(
        config: AgentConfig,
        method: String,
        path: String,
        body: RequestBody?,
        label: String
    ): AgentOutcome<String> {
        if (!config.isConfigured) {
            return AgentOutcome.Failed("No agent URL configured. Set it to your self-hosted agent, e.g. http://10.0.2.2:8000")
        }
        val url = config.normalizedBaseUrl + path
        val builder = Request.Builder().url(url)
            .header("User-Agent", "Concordance-Android/0.1 (self-hosted research tool)")
            .header("Accept", "application/json")
        config.apiKey?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        when (method) {
            "POST" -> builder.post(body ?: "".toRequestBody(JSON))
            "PUT" -> builder.put(body ?: "".toRequestBody(JSON))
            else -> builder.get()
        }

        val start = System.currentTimeMillis()
        val response = try {
            withContext(Dispatchers.IO) {
                clientFor(config.timeoutSeconds)
                    .newCall(builder.build())
                    .execute()
                    .use { r -> r.code to r.body?.string().orEmpty() }
            }
        } catch (e: Exception) {
            // Never surface a stack trace or the URL's query to the UI.
            ProvenanceRecorder.record(
                Provenance(
                    providerId = PROVIDER_ID,
                    endpoint = url,
                    query = label,
                    httpStatus = 0,
                    retrievedUtc = ProvenanceRecorder.nowUtc(),
                    responseSha256 = ProvenanceRecorder.sha256(""),
                    licenseClass = LicenseClass.OPEN,
                    elapsedMs = System.currentTimeMillis() - start
                )
            )
            val reason = if (e is java.net.UnknownServiceException) {
                "Android blocked the cleartext HTTP connection. Use an HTTPS agent URL, " +
                    "or run a debug build that permits cleartext on the local network."
            } else {
                "Cannot reach the agent at ${config.normalizedBaseUrl} " +
                    "(${e.javaClass.simpleName}). Is it running?"
            }
            return AgentOutcome.Failed(reason)
        }

        val (status, text) = response
        ProvenanceRecorder.record(
            Provenance(
                providerId = PROVIDER_ID,
                endpoint = url,
                query = label,
                httpStatus = status,
                retrievedUtc = ProvenanceRecorder.nowUtc(),
                responseSha256 = ProvenanceRecorder.sha256(text),
                licenseClass = LicenseClass.OPEN,
                elapsedMs = System.currentTimeMillis() - start
            )
        )

        return when {
            status in 200..299 -> AgentOutcome.Ok(text, status)
            status == 401 -> AgentOutcome.Failed("The agent rejected the credentials (401). Check the API key.", status)
            status == 403 -> AgentOutcome.Failed("This key lacks the required role (403).", status)
            status == 409 -> AgentOutcome.Failed(detailOr(text, "That request conflicts with an earlier one (409)."), status)
            status == 422 -> AgentOutcome.Refused(detailOr(text, "The agent refused this request."), status)
            status == 503 -> AgentOutcome.Failed(detailOr(text, "The agent is not ready (503)."), status)
            status == 413 -> AgentOutcome.Failed(detailOr(text, "The upload is too large (413)."), status)
            status == 415 -> AgentOutcome.Failed(detailOr(text, "Unsupported file type (415)."), status)
            status in 500..599 -> AgentOutcome.Failed(detailOr(text, "The agent failed ($status)."), status)
            else -> AgentOutcome.Failed(detailOr(text, "Unexpected response ($status)."), status)
        }
    }

    private fun detailOr(body: String, fallback: String): String =
        try {
            JSONObject(body).optString("detail").ifBlank { fallback }
        } catch (e: Exception) {
            fallback
        }

    private inline fun <T> parseBody(
        outcome: AgentOutcome<String>,
        what: String,
        parse: (String) -> T
    ): AgentOutcome<T> = when (outcome) {
        is AgentOutcome.Ok -> try {
            AgentOutcome.Ok(parse(outcome.value), outcome.status)
        } catch (e: Exception) {
            AgentOutcome.Failed("Could not read the agent's $what response (${e.javaClass.simpleName}).")
        }
        is AgentOutcome.Refused -> outcome
        is AgentOutcome.Failed -> outcome
    }

    // ---- parsing ----

    private fun parseJob(body: String): AgentJob {
        val o = JSONObject(body)
        val capsule = o.optJSONObject("capsule")
        return AgentJob(
            jobId = o.optString("job_id"),
            status = o.optString("status", "unknown"),
            capsule = capsule?.let { parseCapsule(it) },
            error = o.optString("error").takeIf { it.isNotBlank() },
            rawJson = body
        )
    }

    private fun parseCapsule(c: JSONObject): AgentCapsule {
        val thresholds = c.optJSONObject("threshold_values")
        val uncertainty = c.optJSONObject("uncertainty")
        val trace = c.optJSONArray("decision_trace")
        return AgentCapsule(
            anchorId = c.optString("anchor_id"),
            verdict = c.optString("verdict", "withheld"),
            reason = c.optString("reason"),
            evidenceTier = c.optString("evidence_tier", "unmeasured"),
            rubricVersion = c.optString("rubric_version", "1.1"),
            correctedOddsRatio = thresholds?.optDoubleOrNull("corrected_or"),
            correctedCi = thresholds?.optDoublePair("corrected_ci"),
            significanceThresholdR = thresholds?.optDoubleOrNull("significance_threshold_r"),
            power = uncertainty?.optDoubleOrNull("power"),
            decisionTrace = (0 until (trace?.length() ?: 0)).map { trace!!.optString(it) }
        )
    }

    private fun parseCorrection(r: JSONObject, plain: JSONObject?): CorrectionResult = CorrectionResult(
        anchorId = r.optString("anchor_id"),
        status = r.optString("status", "unknown"),
        evidenceTier = r.optString("evidence_tier", "unmeasured"),
        r = r.optDoubleOrNull("r"),
        correctedOddsRatio = r.optDoubleOrNull("corrected_or"),
        correctedCi = r.optDoublePair("corrected_ci"),
        significanceThresholdR = r.optDoubleOrNull("significance_threshold_r"),
        minFeasibleR = r.optDoubleOrNull("min_feasible_r"),
        significantAtR = r.optBoolean("significant_at_r", false),
        feasible = r.optBoolean("feasible", false),
        interpretation = r.optString("interpretation").takeIf { it.isNotBlank() },
        reason = r.optString("reason").takeIf { it.isNotBlank() }
            ?: plain?.optString("headline")?.takeIf { it.isNotBlank() },
        rawJson = r.toString()
    )

    /**
     * Read the agent's plain-language payload. Numbers are only *reformatted* for
     * display, never recalculated, so easy mode cannot drift from expert mode.
     */
    fun parseEasyMode(o: JSONObject): EasyModeView {
        val signal = o.optJSONObject("signal") ?: JSONObject()
        val quality = o.optJSONObject("evidence_quality") ?: JSONObject()
        val terms = o.optJSONArray("words_we_used") ?: JSONArray()
        return EasyModeView(
            headline = o.optString("headline"),
            signalBand = signal.optString("band", "cannot_tell"),
            signalLabel = signal.optString("label"),
            signalDetail = signal.optString("detail"),
            evidenceTier = quality.optString("tier", "unmeasured"),
            qualityLabel = quality.optString("label"),
            qualityDetail = quality.optString("detail"),
            whatWeDid = o.optJSONArray("what_we_did").toStringList(),
            whatWeFound = o.optJSONObject("what_we_found").orderedPairs(FOUND_ORDER),
            doesNotMean = o.optJSONArray("what_it_does_not_mean").toStringList(),
            nextSteps = o.optJSONArray("next_steps").toStringList(),
            wordsWeUsed = (0 until terms.length()).map { i ->
                val t = terms.getJSONObject(i)
                GlossaryTerm(t.optString("term"), t.optString("plain"), t.optString("why"))
            },
            numbers = o.optJSONObject("the_numbers").orderedPairs(NUMBERS_ORDER)
        )
    }

    private fun JSONObject?.optDoubleOrNull(key: String): Double? =
        if (this != null && has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONObject?.optDoublePair(key: String): Pair<Double, Double>? {
        val arr = this?.optJSONArray(key) ?: return null
        if (arr.length() < 2) return null
        return arr.optDouble(0) to arr.optDouble(1)
    }
}

// ---- json helpers (kept internal so the app's org.json usage stays consistent) ----

internal fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i ->
        if (isNull(i)) null else optString(i).takeIf { it.isNotBlank() }
    }
}

/**
 * Flatten a JSON object into label/value pairs in a stable order: the preferred keys
 * first (in the given order), then anything unexpected, so no field is silently lost.
 */
internal fun JSONObject?.orderedPairs(preferred: List<String>): List<Pair<String, String>> {
    if (this == null) return emptyList()
    val seen = LinkedHashSet<String>()
    val out = mutableListOf<Pair<String, String>>()
    for (key in preferred) {
        if (has(key) && !isNull(key)) {
            seen.add(key)
            out.add(prettifyKey(key) to formatJsonValue(get(key)))
        }
    }
    val rest = keys().asSequence().filterNot { it in seen || isNull(it) }.sorted()
    for (key in rest) {
        out.add(prettifyKey(key) to formatJsonValue(get(key)))
    }
    return out
}

private fun prettifyKey(key: String): String =
    key.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

private fun formatJsonValue(value: Any?): String = when (value) {
    null, JSONObject.NULL -> ""
    is JSONArray -> (0 until value.length())
        .map { formatJsonValue(value.opt(it)) }
        .filter { it.isNotEmpty() }
        .joinToString(" – ")
    is Double -> if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e15) {
        value.toLong().toString()
    } else {
        value.toString()
    }
    else -> value.toString()
}

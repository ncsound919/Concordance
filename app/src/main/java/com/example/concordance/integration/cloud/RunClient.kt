package com.example.concordance.integration.cloud

import com.example.concordance.research.experiment.RunPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * The app side of `docs/EDGE_RUN_API.md`.
 *
 * The phone never holds a Kaggle key and never polls Kaggle. It submits a compiled [RunPlan] to
 * the edge, which owns the credential, the kernel push, the polling and the artifact download.
 *
 * Every failure is typed. A refusal (`422`) is a result the UI shows as an answer, not an error
 * to retry; an `UNAVAILABLE` carries whether a retry is safe.
 */
object RunClient {

    private val _config = MutableStateFlow(RunClientConfig())
    val config: StateFlow<RunClientConfig> = _config.asStateFlow()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun configure(baseUrl: String, token: String) {
        _config.value = RunClientConfig(normalise(baseUrl), token.trim())
    }

    fun clear() {
        _config.value = RunClientConfig()
    }

    suspend fun submit(plan: RunPlan): CloudOutcome<SubmittedRun> =
        call("POST", "/api/runs", RunPlanJson.encode(plan)) { code, body ->
            when (code) {
                201 -> CloudOutcome.Ok(parseSubmitted(body))
                409 -> {
                    val json = JSONObject(body)
                    CloudOutcome.Refused(
                        "DUPLICATE_PLAN",
                        "run " + json.optString("runId", "?") + " already exists for this plan",
                    )
                }
                422 -> refusal(body)
                else -> CloudOutcome.Unavailable("HTTP $code", code >= 500)
            }
        }

    suspend fun status(runId: String): CloudOutcome<RunStatusView> =
        call("GET", "/api/runs/$runId", null) { code, body ->
            when (code) {
                200 -> CloudOutcome.Ok(parseStatus(body))
                404 -> CloudOutcome.Refused("RUN_NOT_FOUND", "no run '$runId'")
                else -> CloudOutcome.Unavailable("HTTP $code", code >= 500)
            }
        }

    suspend fun artifacts(runId: String): CloudOutcome<ArtifactSet> =
        call("GET", "/api/runs/$runId/artifacts", null) { code, body ->
            when (code) {
                200 -> CloudOutcome.Ok(parseArtifacts(body))
                404 -> CloudOutcome.Refused("RUN_NOT_FOUND", "no run '$runId'")
                409 -> CloudOutcome.Refused("NOT_READY", "artifacts are not ready yet")
                else -> CloudOutcome.Unavailable("HTTP $code", code >= 500)
            }
        }

    suspend fun cancel(runId: String): CloudOutcome<RunStatusView> =
        call("POST", "/api/runs/$runId/cancel", "{}") { code, body ->
            when (code) {
                200, 202 -> CloudOutcome.Ok(parseStatus(body))
                409 -> CloudOutcome.Refused("ALREADY_TERMINAL", "the run has already finished")
                404 -> CloudOutcome.Refused("RUN_NOT_FOUND", "no run '$runId'")
                else -> CloudOutcome.Unavailable("HTTP $code", code >= 500)
            }
        }

    private suspend fun <T> call(
        method: String,
        path: String,
        body: String?,
        map: (Int, String) -> CloudOutcome<T>,
    ): CloudOutcome<T> = withContext(Dispatchers.IO) {
        val config = _config.value
        if (!config.isConfigured) {
            return@withContext CloudOutcome.Unavailable(
                "the run service is not configured",
                retryable = false,
            )
        }
        val builder = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/json")
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((body ?: "{}").toRequestBody(JSON))
            else -> builder.method(method, (body ?: "").toRequestBody(JSON))
        }
        try {
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.code == 401 || response.code == 403) {
                    return@withContext CloudOutcome.Unavailable(
                        "the run service rejected this token",
                        retryable = false,
                    )
                }
                map(response.code, text)
            }
        } catch (e: IOException) {
            CloudOutcome.Unavailable(e.message ?: "network error", retryable = true)
        } catch (e: Exception) {
            CloudOutcome.Unavailable(e.javaClass.simpleName + ": " + (e.message ?: ""), retryable = false)
        }
    }

    private fun refusal(body: String): CloudOutcome.Refused = try {
        val json = JSONObject(body)
        CloudOutcome.Refused(
            json.optString("code", "REFUSED"),
            json.optString("detail", body.take(200)),
        )
    } catch (e: Exception) {
        CloudOutcome.Refused("REFUSED", body.take(200))
    }

    private fun parseSubmitted(body: String): SubmittedRun {
        val json = JSONObject(body)
        return SubmittedRun(
            runId = json.optString("runId"),
            state = json.optString("state", "QUEUED"),
            quota = json.optJSONObject("quota")?.let(::parseQuota),
        )
    }

    private fun parseStatus(body: String): RunStatusView {
        val json = JSONObject(body)
        val ledger = json.optJSONArray("ledger")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        } ?: emptyList()
        return RunStatusView(
            runId = json.optString("runId"),
            state = json.optString("state", "QUEUED"),
            kernelVersion = if (json.has("kernelVersion")) json.optInt("kernelVersion") else null,
            progress = if (json.has("progress")) json.optDouble("progress") else null,
            ledger = ledger,
            quota = json.optJSONObject("quota")?.let(::parseQuota),
        )
    }

    private fun parseArtifacts(body: String): ArtifactSet {
        val arr = JSONObject(body).optJSONArray("artifacts")
        val items = if (arr == null) emptyList() else (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ArtifactRef(
                name = o.optString("name"),
                sha256 = o.optString("sha256"),
                bytes = o.optLong("bytes", 0L),
                url = o.optString("url"),
            )
        }
        return ArtifactSet(items)
    }

    private fun parseQuota(o: JSONObject): QuotaView = QuotaView(
        gpuHoursUsed = if (o.has("gpuHoursUsed")) o.optDouble("gpuHoursUsed") else null,
        gpuHoursRemaining = if (o.has("gpuHoursRemaining")) o.optDouble("gpuHoursRemaining") else null,
        refreshesAt = o.optString("refreshesAt").ifBlank { null },
    )

    private fun normalise(baseUrl: String): String = baseUrl.trim().trimEnd('/')
}

data class RunClientConfig(
    val baseUrl: String = "",
    val token: String = "",
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
}

sealed interface CloudOutcome<out T> {
    data class Ok<T>(val value: T) : CloudOutcome<T>

    /** A defined answer: duplicate, quota, not ready. Show it; do not retry it as an error. */
    data class Refused(val code: String, val detail: String) : CloudOutcome<Nothing>

    /** Transport or server failure. [retryable] says whether a retry is safe. */
    data class Unavailable(val message: String, val retryable: Boolean) : CloudOutcome<Nothing>
}

data class QuotaView(
    val gpuHoursUsed: Double?,
    val gpuHoursRemaining: Double?,
    val refreshesAt: String?,
)

data class SubmittedRun(
    val runId: String,
    val state: String,
    val quota: QuotaView?,
)

data class RunStatusView(
    val runId: String,
    val state: String,
    val kernelVersion: Int?,
    val progress: Double?,
    val ledger: List<String>,
    val quota: QuotaView?,
)

data class ArtifactRef(
    val name: String,
    val sha256: String,
    val bytes: Long,
    val url: String,
)

data class ArtifactSet(val artifacts: List<ArtifactRef>)

/**
 * Canonical JSON for a [RunPlan]. Kept next to the client so the wire shape and the contract in
 * `docs/EDGE_RUN_API.md` cannot drift apart silently.
 */
object RunPlanJson {

    fun encode(plan: RunPlan): String {
        val datasets = plan.datasets.sortedBy { it.ref }.map { ds ->
            JSONObject()
                .put("ref", ds.ref)
                .put("version", ds.version)
                .put("sha256", ds.sha256)
                .put("mountPath", ds.mountPath)
        }
        return JSONObject()
            .put("specId", plan.specId)
            .put("specVersion", plan.specVersion)
            .put("specHash", plan.specHash)
            .put("planHash", plan.planHash)
            .put("backendId", plan.backendId)
            .put("kernelRef", plan.kernelRef)
            .put("datasets", org.json.JSONArray(datasets))
            .put("seeds", org.json.JSONArray(plan.seeds))
            .put("determinism", plan.determinism.name)
            .put("estimatedCpuMinutes", plan.estimatedCpuMinutes)
            .put("estimatedGpuMinutes", plan.estimatedGpuMinutes)
            .put("requiredArtifacts", org.json.JSONArray(plan.requiredArtifacts))
            .toString()
    }
}

/**
 * The gate that lets [com.example.concordance.research.experiment.RunArtifact] be marked verified.
 * A download whose bytes do not hash to the declared value must never reach the adjudicator.
 */
object ArtifactIntegrity {

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun matches(bytes: ByteArray, expectedSha256: String): Boolean =
        expectedSha256.isNotBlank() &&
            sha256Hex(bytes).equals(expectedSha256.trim().lowercase(), ignoreCase = true)
}

package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.ApiRotationManager
import com.example.concordance.integration.rotation.LicenseClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class HttpJsonResult(
    val status: Int,
    val body: String,
    val elapsedMs: Long,
    val provenance: Provenance
)

/**
 * Shared, rate-limited JSON client. Every call is provenance-logged and reports
 * success/failure to [ApiRotationManager] so the rotation pool stays accurate.
 * The blocking OkHttp call runs on [Dispatchers.IO].
 */
object HttpJsonClient {

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()

    @Volatile
    private var cache: com.example.concordance.data.ResponseCache? = null

    @Volatile
    private var cacheTtlMs: Long = 10 * 60 * 1000L

    fun setCache(c: com.example.concordance.data.ResponseCache?) {
        cache = c
    }

    fun setCacheTtlMs(ms: Long) {
        cacheTtlMs = ms.coerceAtLeast(60_000L)
    }

    fun cacheTtlMs(): Long = cacheTtlMs

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Concordance/0.1 (research tool; mailto:research@concordance.app)")
                .header("Accept", "application/json")
                .header("Accept-Encoding", "identity")
                .build()
            chain.proceed(request)
        }
        .build()

    suspend fun getJson(
        providerId: String,
        url: String,
        queryLabel: String,
        spec: RateLimitSpec,
        licenseClass: LicenseClass,
        authToken: String? = null,
        extraHeaders: Map<String, String>? = null
    ): HttpJsonResult? = request("GET", providerId, url, queryLabel, spec, licenseClass, null, authToken, extraHeaders)

    /** POST a JSON body (e.g. a GraphQL query). Used by the CIViC adapter. */
    suspend fun postJson(
        providerId: String,
        url: String,
        queryLabel: String,
        spec: RateLimitSpec,
        licenseClass: LicenseClass,
        jsonBody: String,
        authToken: String? = null,
        extraHeaders: Map<String, String>? = null
    ): HttpJsonResult? = request("POST", providerId, url, queryLabel, spec, licenseClass, jsonBody, authToken, extraHeaders)

    /** PUT a JSON body. Used by S3/R2-compatible storage uploads. */
    suspend fun putJson(
        providerId: String,
        url: String,
        queryLabel: String,
        spec: RateLimitSpec,
        licenseClass: LicenseClass,
        jsonBody: String,
        authToken: String? = null,
        extraHeaders: Map<String, String>? = null
    ): HttpJsonResult? = request("PUT", providerId, url, queryLabel, spec, licenseClass, jsonBody, authToken, extraHeaders)

    private suspend fun request(
        method: String,
        providerId: String,
        url: String,
        queryLabel: String,
        spec: RateLimitSpec,
        licenseClass: LicenseClass,
        jsonBody: String?,
        authToken: String? = null,
        extraHeaders: Map<String, String>? = null
    ): HttpJsonResult? {
        val cacheKey = "$providerId|$url|${jsonBody ?: ""}"
        // Only keyless GET responses participate in the cache, on both the read
        // and write paths — token-authenticated and POST responses must never be
        // served from, or stored to, a shared keyless cache.
        val cacheable = method == "GET" && authToken == null && extraHeaders == null
        val c = if (cacheable) cache else null

        if (c != null) {
            val cached = c.get(cacheKey)
            if (cached != null) {
                val provenance = Provenance(
                    providerId = providerId,
                    endpoint = url,
                    query = queryLabel,
                    httpStatus = 200,
                    retrievedUtc = ProvenanceRecorder.nowUtc(),
                    responseSha256 = cached.sha256,
                    licenseClass = licenseClass,
                    elapsedMs = 0
                )
                // A cache-served response is still a source response: record it so
                // any capsule built from it keeps a complete provenance digest.
                ProvenanceRecorder.record(provenance)
                ApiRotationManager.recordSuccess(providerId, 0)
                return HttpJsonResult(200, cached.body, 0, provenance)
            }
        }

        ProviderRateLimiter.acquire(providerId, spec)
        val start = System.currentTimeMillis()

        val network = try {
            withContext(Dispatchers.IO) {
                val builder = Request.Builder().url(url)
                if (authToken != null) {
                    builder.header("Authorization", "Bearer $authToken")
                }
                extraHeaders?.forEach { (k, v) -> builder.header(k, v) }
                if (method == "POST" || method == "PUT") {
                    if (method == "POST") {
                        builder.post((jsonBody ?: "").toRequestBody(JSON_MEDIA_TYPE))
                    } else {
                        builder.put((jsonBody ?: "").toRequestBody(JSON_MEDIA_TYPE))
                    }
                } else {
                    builder.get()
                }
                client.newCall(builder.build()).execute().use { response ->
                    response.code to response.body?.string().orEmpty()
                }
            }
        } catch (e: Exception) {
            ApiRotationManager.recordFailure(providerId, false)
            com.example.concordance.telemetry.TelemetryManager.recordApiCall(providerId, false, System.currentTimeMillis() - start)
            return null
        }

        val (status, body) = network
        val elapsed = System.currentTimeMillis() - start

        val provenance = Provenance(
            providerId = providerId,
            endpoint = url,
            query = queryLabel,
            httpStatus = status,
            retrievedUtc = ProvenanceRecorder.nowUtc(),
            responseSha256 = ProvenanceRecorder.sha256(body),
            licenseClass = licenseClass,
            elapsedMs = elapsed
        )
        ProvenanceRecorder.record(provenance)

        return if (status in 200..299) {
            ApiRotationManager.recordSuccess(providerId, elapsed)
            com.example.concordance.telemetry.TelemetryManager.recordApiCall(providerId, true, elapsed)
            c?.put(cacheKey, body, ProvenanceRecorder.sha256(body), cacheTtlMs)
            HttpJsonResult(status, body, elapsed, provenance)
        } else {
            ApiRotationManager.recordFailure(providerId, status == 429)
            com.example.concordance.telemetry.TelemetryManager.recordApiCall(providerId, false, elapsed)
            null
        }
    }
}

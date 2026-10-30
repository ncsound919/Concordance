package com.example

import com.example.concordance.integration.api.HttpJsonClient
import com.example.concordance.integration.api.ProvenanceRecorder
import com.example.concordance.integration.rotation.ApiRotationManager
import com.example.concordance.telemetry.TelemetryCollector
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import java.util.concurrent.TimeUnit

/**
 * A local HTTP server standing in for the public research APIs.
 *
 * The adapters build their own absolute URLs (`https://clinicaltrials.gov/...`,
 * `https://ktphbqvoycurlyxieurv.supabase.co/rest/v1/...`), so the harness
 * rewrites the scheme and host of every request to the local server and keeps
 * the path and query intact. The mock therefore sees exactly the path the
 * production code asked for, which is the thing worth asserting.
 *
 * The rewrite interceptor is appended to the production client, so the
 * production headers (the research User-Agent, `Accept`) are still applied and
 * can be asserted.
 */
class MockHttpHarness {

    val server: MockWebServer = MockWebServer()

    /** The client to hand to the code under test. */
    val client: OkHttpClient

    init {
        server.start()
        val base: HttpUrl = server.url("/")
        client = HttpJsonClient.defaultClient()
            .newBuilder()
            .addInterceptor { chain ->
                val original = chain.request().url
                val rewritten = base.newBuilder()
                    .encodedPath(original.encodedPath)
                    .encodedQuery(original.encodedQuery)
                    .build()
                chain.proceed(chain.request().newBuilder().url(rewritten).build())
            }
            .build()
    }

    /** Install the harness as the shared client for [HttpJsonClient]. */
    fun install() {
        HttpJsonClient.httpClient = client
    }

    fun shutdown() {
        HttpJsonClient.httpClient = HttpJsonClient.defaultClient()
        server.shutdown()
    }

    // -- enqueueing ---------------------------------------------------------

    fun enqueue(code: Int = 200, body: String = "{}") {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    fun enqueueJson(code: Int = 200, body: String) = enqueue(code, body)

    fun enqueueError(code: Int, message: String = "error") {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"$message"}""")
        )
    }

    // -- inspecting ---------------------------------------------------------

    fun takeRequest(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!

    fun requestCount(): Int = server.requestCount

    fun pathOfRequest(index: Int = 0): String {
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        return recorded.path ?: ""
    }
}

/**
 * The integration layer keeps process-wide state: the shared HTTP client, the
 * response cache, the rotation pool's counters, the provenance log and the
 * telemetry buffer. Every test that touches one of them has to put it back.
 */
fun resetIntegrationState() {
    HttpJsonClient.setCache(null)
    HttpJsonClient.setCacheTtlMs(10 * 60 * 1000L)
    ProvenanceRecorder.clear()
    ApiRotationManager.resetStats()
    TelemetryCollector.clear()
}

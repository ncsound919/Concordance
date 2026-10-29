package com.example

import com.example.concordance.integration.cloud.ArtifactIntegrity
import com.example.concordance.integration.cloud.CloudOutcome
import com.example.concordance.integration.cloud.RunClient
import com.example.concordance.research.experiment.Backends
import com.example.concordance.research.experiment.CompileResult
import com.example.concordance.research.experiment.ExperimentCompiler
import com.example.concordance.research.experiment.Preregistration
import com.example.concordance.research.experiment.RunPlan
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RunClientTest {

    private val server = MockWebServer()

    @After
    fun tearDown() {
        RunClient.clear()
        runCatching { server.shutdown() }
    }

    private fun start(): String {
        server.start()
        return server.url("/").toString().trimEnd('/')
    }

    private fun plan(): RunPlan {
        val spec = ExperimentSpecTest.spec()
        return (
            ExperimentCompiler.compile(
                spec,
                Backends.KAGGLE,
                Preregistration(spec.sha256(), "2026-10-28T12:00:00Z", "tap45"),
            ) as CompileResult.Ready
            ).plan
    }

    private fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private fun <T> unwrap(outcome: CloudOutcome<T>): T {
        assertTrue(outcome.toString(), outcome is CloudOutcome.Ok<*>)
        @Suppress("UNCHECKED_CAST")
        return (outcome as CloudOutcome.Ok<T>).value
    }

    private fun <T> refused(outcome: CloudOutcome<T>): CloudOutcome.Refused {
        assertTrue(outcome.toString(), outcome is CloudOutcome.Refused)
        return outcome as CloudOutcome.Refused
    }

    private fun <T> unavailable(outcome: CloudOutcome<T>): CloudOutcome.Unavailable {
        assertTrue(outcome.toString(), outcome is CloudOutcome.Unavailable)
        return outcome as CloudOutcome.Unavailable
    }

    @Test
    fun anUnconfiguredClientRefusesWithoutTouchingTheNetwork() = runBlocking {
        start()
        val failure = unavailable(RunClient.submit(plan()))
        assertFalse(failure.retryable)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun submitPostsThePlanWithAuthAndParsesTheRun() = runBlocking {
        val base = start()
        RunClient.configure(base, "tok-123")
        enqueue(201, """{"runId":"run-9","state":"QUEUED","quota":{"gpuHoursRemaining":27.4}}""")

        val submitted = unwrap(RunClient.submit(plan()))
        assertEquals("run-9", submitted.runId)
        assertEquals("QUEUED", submitted.state)
        assertEquals(27.4, submitted.quota!!.gpuHoursRemaining!!, 0.0)

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/api/runs", request.path)
        assertEquals("Bearer tok-123", request.getHeader("Authorization"))

        // Parse rather than substring-match: org.json escapes '/' as '\/', so a raw string
        // assertion would be testing the serializer instead of the contract.
        val sent = org.json.JSONObject(request.body.readUtf8())
        assertEquals("rp-esr1-cns-001", sent.getString("specId"))
        assertEquals(plan().specHash, sent.getString("specHash"))
        assertEquals(plan().planHash, sent.getString("planHash"))
        assertEquals("kaggle", sent.getString("backendId"))
        val dataset = sent.getJSONArray("datasets").getJSONObject(0)
        assertEquals("a".repeat(64), dataset.getString("sha256"))
        assertTrue(dataset.getString("mountPath").startsWith("/kaggle/input/"))
    }

    @Test
    fun aQuotaRefusalIsAResultNotAnError() = runBlocking {
        RunClient.configure(start(), "tok")
        enqueue(422, """{"code":"GPU_SESSION_LIMIT","detail":"2 concurrent GPU sessions already running"}""")
        val refusal = refused(RunClient.submit(plan()))
        assertEquals("GPU_SESSION_LIMIT", refusal.code)
        assertTrue(refusal.detail.contains("2 concurrent"))
    }

    @Test
    fun aDuplicatePlanReportsTheExistingRun() = runBlocking {
        RunClient.configure(start(), "tok")
        enqueue(409, """{"code":"DUPLICATE_PLAN","runId":"run-existing"}""")
        val refusal = refused(RunClient.submit(plan()))
        assertEquals("DUPLICATE_PLAN", refusal.code)
        assertTrue(refusal.detail.contains("run-existing"))
    }

    @Test
    fun aServerErrorIsRetryable() = runBlocking {
        RunClient.configure(start(), "tok")
        enqueue(503, """{"code":"KAGGLE_UNREACHABLE"}""")
        assertTrue(unavailable(RunClient.submit(plan())).retryable)
    }

    @Test
    fun aRejectedTokenIsNotRetryable() = runBlocking {
        RunClient.configure(start(), "bad")
        enqueue(401, "{}")
        assertFalse(unavailable(RunClient.submit(plan())).retryable)
    }

    @Test
    fun statusParsesTheLedgerAndProgress() = runBlocking {
        RunClient.configure(start(), "tok")
        enqueue(
            200,
            """{"runId":"run-9","state":"RUNNING","kernelVersion":3,"progress":0.4,
               "ledger":["PLANNED","APPROVED","QUEUED","RUNNING"],
               "quota":{"gpuHoursUsed":2.6,"gpuHoursRemaining":27.4}}"""
        )
        val status = unwrap(RunClient.status("run-9"))
        assertEquals("RUNNING", status.state)
        assertEquals(3, status.kernelVersion)
        assertEquals(0.4, status.progress!!, 0.0)
        assertEquals(listOf("PLANNED", "APPROVED", "QUEUED", "RUNNING"), status.ledger)
        assertEquals(2.6, status.quota!!.gpuHoursUsed!!, 0.0)
    }

    @Test
    fun anUnknownRunIsRefusedRatherThanTreatedAsEmpty() = runBlocking {
        RunClient.configure(start(), "tok")
        enqueue(404, """{"code":"RUN_NOT_FOUND"}""")
        assertEquals("RUN_NOT_FOUND", refused(RunClient.status("nope")).code)
    }

    @Test
    fun artifactsAreParsedWithTheirHashes() = runBlocking {
        RunClient.configure(start(), "tok")
        enqueue(
            200,
            """{"artifacts":[{"name":"results.json","sha256":"${"b".repeat(64)}","bytes":1834,
               "url":"https://r2.example/results.json"}]}"""
        )
        val set = unwrap(RunClient.artifacts("run-9"))
        assertEquals(1, set.artifacts.size)
        assertEquals("results.json", set.artifacts[0].name)
        assertEquals(1834L, set.artifacts[0].bytes)
        assertEquals("https://r2.example/results.json", set.artifacts[0].url)
    }

    @Test
    fun artifactsNotReadyIsARefusal() = runBlocking {
        RunClient.configure(start(), "tok")
        enqueue(409, "{}")
        assertEquals("NOT_READY", refused(RunClient.artifacts("run-9")).code)
    }

    @Test
    fun cancellingATerminalRunIsRefused() = runBlocking {
        RunClient.configure(start(), "tok")
        enqueue(409, "{}")
        assertEquals("ALREADY_TERMINAL", refused(RunClient.cancel("run-9")).code)
    }

    @Test
    fun artifactIntegrityAcceptsOnlyTheDeclaredBytes() {
        val bytes = "hello".toByteArray()
        val hash = ArtifactIntegrity.sha256Hex(bytes)
        assertEquals(64, hash.length)
        assertTrue(ArtifactIntegrity.matches(bytes, hash))
        assertTrue(ArtifactIntegrity.matches(bytes, hash.uppercase()))
        assertFalse(ArtifactIntegrity.matches("hell0".toByteArray(), hash))
        // A blank expected hash must never be treated as a match.
        assertFalse(ArtifactIntegrity.matches(bytes, ""))
    }
}

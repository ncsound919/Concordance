package com.example

import com.example.concordance.data.CachedResponseDao
import com.example.concordance.data.CachedResponseEntity
import com.example.concordance.data.ResponseCache
import com.example.concordance.integration.api.HttpJsonClient
import com.example.concordance.integration.api.ProvenanceRecorder
import com.example.concordance.integration.api.RateLimitSpec
import com.example.concordance.integration.rotation.ApiRotationManager
import com.example.concordance.integration.rotation.LicenseClass
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared JSON client that every research-API adapter goes through.
 *
 * It is the one place where the app decides what a response is worth: whether
 * it may be cached, what provenance row it produces, and what the rotation
 * pool is told about the provider. Those three decisions are what these tests
 * pin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HttpJsonClientTest {

    private lateinit var harness: MockHttpHarness

    private val spec = RateLimitSpec(requestsPerSecond = 100.0, burst = 20)

    @Before
    fun setUp() {
        resetIntegrationState()
        harness = MockHttpHarness()
        harness.install()
    }

    @After
    fun tearDown() {
        harness.shutdown()
        resetIntegrationState()
    }

    // -----------------------------------------------------------------------
    // The happy path
    // -----------------------------------------------------------------------

    @Test
    fun `a successful GET returns the body and the status`() = runBlocking {
        harness.enqueue(200, """{"ok":true}""")
        val result = HttpJsonClient.getJson(
            providerId = "test_provider",
            url = "https://example.org/data",
            queryLabel = "data",
            spec = spec,
            licenseClass = LicenseClass.OPEN
        )
        assertNotNull(result)
        assertEquals(200, result!!.status)
        assertEquals("""{"ok":true}""", result.body)
    }

    @Test
    fun `the request carries the research user agent`() = runBlocking {
        harness.enqueue(200, "{}")
        HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN
        )
        val request = harness.takeRequest()
        assertEquals(
            "Concordance/0.1 (research tool; mailto:research@concordance.app)",
            request.getHeader("User-Agent")
        )
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun `the request keeps the path and the query the caller asked for`() = runBlocking {
        harness.enqueue(200, "{}")
        HttpJsonClient.getJson(
            "test_provider",
            "https://example.org/api/v2/studies?query.term=ESR1&pageSize=10",
            "ESR1",
            spec,
            LicenseClass.OPEN
        )
        val request = harness.takeRequest()
        assertEquals("/api/v2/studies?query.term=ESR1&pageSize=10", request.path)
    }

    @Test
    fun `a token is sent as a bearer credential`() = runBlocking {
        harness.enqueue(200, "{}")
        HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.ACADEMIC_ONLY,
            authToken = "secret-token"
        )
        assertEquals("Bearer secret-token", harness.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `extra headers are sent`() = runBlocking {
        harness.enqueue(200, "{}")
        HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN,
            extraHeaders = mapOf("X-Api-Key" to "abc")
        )
        assertEquals("abc", harness.takeRequest().getHeader("X-Api-Key"))
    }

    // -----------------------------------------------------------------------
    // POST and PUT
    // -----------------------------------------------------------------------

    @Test
    fun `a POST sends the body as JSON`() = runBlocking {
        harness.enqueue(200, """{"data":{}}""")
        val result = HttpJsonClient.postJson(
            "test_provider", "https://example.org/graphql", "gql", spec, LicenseClass.OPEN,
            jsonBody = """{"query":"{ gene { name } }"}"""
        )
        assertNotNull(result)
        val request = harness.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("""{"query":"{ gene { name } }"}""", request.body.readUtf8())
        assertTrue(
            "Content-Type was ${request.getHeader("Content-Type")}",
            request.getHeader("Content-Type")?.startsWith("application/json") == true
        )
    }

    @Test
    fun `a PUT sends the body as JSON`() = runBlocking {
        harness.enqueue(200, "{}")
        HttpJsonClient.putJson(
            "test_provider", "https://example.org/object", "put", spec, LicenseClass.OPEN,
            jsonBody = """{"a":1}"""
        )
        val request = harness.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("""{"a":1}""", request.body.readUtf8())
    }

    // -----------------------------------------------------------------------
    // Failure
    // -----------------------------------------------------------------------

    @Test
    fun `a server error yields no result`() = runBlocking {
        harness.enqueue(500, """{"error":"boom"}""")
        val result = HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN
        )
        assertNull(result)
    }

    @Test
    fun `a 404 yields no result`() = runBlocking {
        harness.enqueue(404, """{"error":"not found"}""")
        assertNull(
            HttpJsonClient.getJson(
                "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN
            )
        )
    }

    @Test
    fun `a rate limited response yields no result`() = runBlocking {
        harness.enqueue(429, """{"error":"slow down"}""")
        assertNull(
            HttpJsonClient.getJson(
                "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN
            )
        )
    }

    @Test
    fun `an unreachable server yields no result rather than throwing`() = runBlocking {
        // Nothing is enqueued and the server is shut down first.
        harness.shutdown()
        val result = HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN
        )
        assertNull(result)
    }

    // -----------------------------------------------------------------------
    // Provenance
    // -----------------------------------------------------------------------

    @Test
    fun `a response is recorded in the provenance log`() = runBlocking {
        harness.enqueue(200, """{"ok":true}""")
        HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "my query", spec, LicenseClass.OPEN
        )
        val record = ProvenanceRecorder.records.value.first()
        assertEquals("test_provider", record.providerId)
        assertEquals("https://example.org/data", record.endpoint)
        assertEquals("my query", record.query)
        assertEquals(200, record.httpStatus)
        assertEquals(LicenseClass.OPEN, record.licenseClass)
    }

    @Test
    fun `a secret query parameter is redacted from the persisted provenance endpoint`() = runBlocking {
        harness.enqueue(200, "{}")
        HttpJsonClient.getJson(
            "pubpeer_api",
            "https://example.org/v1/publications?doi=10.1/a&api_key=super-secret",
            "10.1/a",
            spec,
            LicenseClass.OPEN
        )
        val record = ProvenanceRecorder.records.value.first()
        // The wire request keeps the real key...
        assertTrue(harness.takeRequest().path!!.contains("api_key=super-secret"))
        // ...but the persisted provenance never stores it.
        org.junit.Assert.assertFalse(record.endpoint.contains("super-secret"))
        assertTrue(record.endpoint.contains("api_key=[redacted]"))
    }

    @Test
    fun `sanitizeUrlForStorage redacts known secret params and leaves others`() {
        val scrubbed = HttpJsonClient.sanitizeUrlForStorage(
            "https://h.test/p?token=abc&api_key=k1&page=2&access_token=t2&signature=s"
        )
        org.junit.Assert.assertFalse(scrubbed.contains("abc"))
        org.junit.Assert.assertFalse(scrubbed.contains("k1"))
        org.junit.Assert.assertFalse(scrubbed.contains("t2"))
        org.junit.Assert.assertFalse(scrubbed.contains("signature=s"))
        assertTrue(scrubbed.contains("page=2"))
    }

    @Test
    fun `the provenance digest is the digest of the body`() = runBlocking {
        val body = """{"ok":true}"""
        harness.enqueue(200, body)
        HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN
        )
        val record = ProvenanceRecorder.records.value.first()
        assertEquals(ProvenanceRecorder.sha256(body), record.responseSha256)
    }

    @Test
    fun `a failed response is still recorded in the provenance log`() = runBlocking {
        harness.enqueue(500, """{"error":"boom"}""")
        HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN
        )
        val record = ProvenanceRecorder.records.value.first()
        assertEquals(500, record.httpStatus)
    }

    @Test
    fun `the provenance log is newest first`() = runBlocking {
        harness.enqueue(200, """{"a":1}""")
        harness.enqueue(200, """{"b":2}""")
        HttpJsonClient.getJson("p1", "https://example.org/one", "one", spec, LicenseClass.OPEN)
        HttpJsonClient.getJson("p2", "https://example.org/two", "two", spec, LicenseClass.OPEN)
        val records = ProvenanceRecorder.records.value
        assertEquals(2, records.size)
        assertEquals("p2", records.first().providerId)
    }

    // -----------------------------------------------------------------------
    // The rotation pool
    // -----------------------------------------------------------------------

    @Test
    fun `a success is reported to the rotation pool`() = runBlocking {
        harness.enqueue(200, "{}")
        HttpJsonClient.getJson(
            "clinical_trials", "https://example.org/data", "data", spec, LicenseClass.OPEN
        )
        val provider = ApiRotationManager.providers.value.first { it.id == "clinical_trials" }
        assertEquals(1, provider.requestCount)
        assertEquals(1, provider.successCount)
        assertEquals(0, provider.failureCount)
    }

    @Test
    fun `a failure is reported to the rotation pool`() = runBlocking {
        harness.enqueue(500, "{}")
        HttpJsonClient.getJson(
            "clinical_trials", "https://example.org/data", "data", spec, LicenseClass.OPEN
        )
        val provider = ApiRotationManager.providers.value.first { it.id == "clinical_trials" }
        assertEquals(1, provider.failureCount)
    }

    // -----------------------------------------------------------------------
    // Caching
    // -----------------------------------------------------------------------

    /** An in-memory DAO, so the cache can be exercised without Room. */
    private class FakeDao : CachedResponseDao {
        val rows = mutableMapOf<String, CachedResponseEntity>()
        override suspend fun get(key: String): CachedResponseEntity? = rows[key]
        override suspend fun upsert(row: CachedResponseEntity) { rows[row.cacheKey] = row }
        override suspend fun delete(key: String) { rows.remove(key) }
        override suspend fun deleteExpired(now: Long) {}
        override suspend fun clear() { rows.clear() }
    }

    @Test
    fun `a keyless GET is served from the cache the second time`() = runBlocking {
        val dao = FakeDao()
        HttpJsonClient.setCache(ResponseCache(dao))
        harness.enqueue(200, """{"cached":true}""")

        HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN
        )
        assertEquals(1, harness.requestCount())

        val second = HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN
        )
        assertEquals("the second call must not hit the network", 1, harness.requestCount())
        assertEquals("""{"cached":true}""", second!!.body)
    }

    @Test
    fun `a token-authenticated GET is never cached`() = runBlocking {
        val dao = FakeDao()
        HttpJsonClient.setCache(ResponseCache(dao))
        harness.enqueue(200, "{}")
        harness.enqueue(200, "{}")

        HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN,
            authToken = "token"
        )
        HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN,
            authToken = "token"
        )
        assertEquals(2, harness.requestCount())
    }

    @Test
    fun `a GET with extra headers is never cached`() = runBlocking {
        val dao = FakeDao()
        HttpJsonClient.setCache(ResponseCache(dao))
        harness.enqueue(200, "{}")
        harness.enqueue(200, "{}")

        HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN,
            extraHeaders = mapOf("X-Api-Key" to "k")
        )
        HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN,
            extraHeaders = mapOf("X-Api-Key" to "k")
        )
        assertEquals(2, harness.requestCount())
    }

    @Test
    fun `a POST is never cached`() = runBlocking {
        val dao = FakeDao()
        HttpJsonClient.setCache(ResponseCache(dao))
        harness.enqueue(200, "{}")
        harness.enqueue(200, "{}")

        HttpJsonClient.postJson(
            "test_provider", "https://example.org/graphql", "gql", spec, LicenseClass.OPEN,
            jsonBody = "{}"
        )
        HttpJsonClient.postJson(
            "test_provider", "https://example.org/graphql", "gql", spec, LicenseClass.OPEN,
            jsonBody = "{}"
        )
        assertEquals(2, harness.requestCount())
    }

    @Test
    fun `a cache-served response is still recorded in the provenance log`() = runBlocking {
        HttpJsonClient.setCache(ResponseCache(FakeDao()))
        harness.enqueue(200, """{"cached":true}""")
        HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN
        )
        HttpJsonClient.getJson(
            "test_provider", "https://example.org/data", "data", spec, LicenseClass.OPEN
        )
        assertEquals(2, ProvenanceRecorder.records.value.size)
    }

    @Test
    fun `the cache ttl is never below a minute`() {
        HttpJsonClient.setCacheTtlMs(1L)
        assertEquals(60_000L, HttpJsonClient.cacheTtlMs())
        HttpJsonClient.setCacheTtlMs(120_000L)
        assertEquals(120_000L, HttpJsonClient.cacheTtlMs())
    }
}

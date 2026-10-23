package com.example

import com.example.concordance.integration.retraction.RetractionCheck
import com.example.concordance.integration.supabase.InMemorySupabaseSessionStore
import com.example.concordance.integration.supabase.PostgRestClient
import com.example.concordance.integration.supabase.SupabaseAuth
import com.example.concordance.integration.supabase.SupabaseSession
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RetractionCheckTest {

    private lateinit var harness: MockHttpHarness

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

    private fun authWithToken(token: String): SupabaseAuth {
        val store = InMemorySupabaseSessionStore()
        store.save(SupabaseSession(accessToken = token, refreshToken = "r", expiresAt = Long.MAX_VALUE, userId = "u", email = "e"))
        return SupabaseAuth(store, harness.client)
    }

    @Test
    fun `retractedDois returns the subset present in the mirror`() = runBlocking {
        val auth = authWithToken("tok")
        val rest = PostgRestClient(auth, harness.client)
        harness.enqueue(200, """[{"doi":"10.1/a"},{"doi":"10.1/b"}]""")
        val result = RetractionCheck(rest).retractedDois(listOf("10.1/a", "10.1/c", "10.1/b"))
        assertEquals(setOf("10.1/a", "10.1/b"), result)
        val req = harness.takeRequest()
        assertTrue(req.path!!.contains("retractions"))
        assertTrue(req.path!!.contains("doi=in."))
    }

    @Test
    fun `retractedDois returns empty set on empty input`() = runBlocking {
        val auth = authWithToken("tok")
        val rest = PostgRestClient(auth, harness.client)
        assertTrue(RetractionCheck(rest).retractedDois(emptyList()).isEmpty())
        assertEquals(0, harness.server.requestCount)
    }

    @Test
    fun `retractedDois returns empty set when the server fails`() = runBlocking {
        val auth = authWithToken("tok")
        val rest = PostgRestClient(auth, harness.client)
        harness.enqueue(500, """{"error":"boom"}""")
        assertTrue(RetractionCheck(rest).retractedDois(listOf("10.1/a")).isEmpty())
    }

    @Test
    fun `retractedPmids returns the subset present in the mirror`() = runBlocking {
        val auth = authWithToken("tok")
        val rest = PostgRestClient(auth, harness.client)
        harness.enqueue(200, """[{"pmid":"123"}]""")
        val result = RetractionCheck(rest).retractedPmids(listOf("123", "999"))
        assertEquals(setOf("123"), result)
    }

    @Test
    fun `flagBody builds the expected JSON`() {
        val json = RetractionCheck.flagBody("capsule", "cap-1", "retracted source")
        val o = JSONObject(json)
        assertEquals("capsule", o.getString("artifact_type"))
        assertEquals("cap-1", o.getString("artifact_id"))
        assertEquals("retracted source", o.getString("reason"))
    }
}

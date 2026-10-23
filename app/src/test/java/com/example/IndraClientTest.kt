package com.example

import com.example.concordance.integration.knowledge.IndraClient
import com.example.concordance.integration.knowledge.IndraResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * INDRA runs phone-side, so these tests pin the two rules that matter:
 * patient text never leaves the device, and a slow/unavailable database is a
 * result the UI can show - never an exception that breaks the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IndraClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: IndraClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = IndraClient(baseUrl = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(code: Int, body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    private val payload = """
        {
          "results": {
            "h1": {
              "type": "Activation",
              "sub": {"name": "EGFR"},
              "obj": {"name": "AKT1"},
              "belief": 0.91,
              "evidence": [
                {"text": "EGFR activates AKT1.", "source_api": "reach",
                 "pmid": "12345678", "doi": "10.1/x"}
              ]
            },
            "h2": {
              "type": "Inhibition",
              "sub": {"name": "EGFR"},
              "obj": {"name": "AKT1"},
              "belief": 0.72,
              "evidence": [
                {"text": "EGFR inhibits AKT1 in this model.", "source_api": "sparser",
                 "text_refs": {"PMID": "87654321"}}
              ]
            },
            "h3": {
              "type": "Phosphorylation",
              "sub": {"name": "EGFR"},
              "obj": {"name": "AKT1"},
              "belief": 0.55,
              "evidence": []
            }
          }
        }
    """.trimIndent()

    @Test
    fun `prior knowledge parses statements sorted by belief`() = runBlocking {
        enqueue(200, payload)
        val result = client.priorKnowledge("EGFR", "AKT1")
        assertTrue(result is IndraResult.Ok)
        val knowledge = (result as IndraResult.Ok).value
        assertEquals(3, knowledge.statementCount)
        assertEquals("Activation", knowledge.statements.first().statementType)
        assertEquals(0.91, knowledge.statements.first().belief, 1e-9)
        assertEquals("12345678", knowledge.statements.first().evidence.first().pmid)
        assertEquals("EGFR", knowledge.statements.first().subject)
        assertEquals("AKT1", knowledge.statements.first().objectName)
        assertTrue(knowledge.types.containsKey("Activation"))
        assertTrue(knowledge.disclaimer.contains("not a clinical conclusion"))
    }

    @Test
    fun `evidence pmid falls back to text_refs`() = runBlocking {
        enqueue(200, payload)
        val knowledge = (client.priorKnowledge("EGFR", "AKT1") as IndraResult.Ok).value
        val inhibition = knowledge.statements.first { it.statementType == "Inhibition" }
        assertEquals("87654321", inhibition.evidence.first().pmid)
    }

    @Test
    fun `opposing statements are surfaced as contradictions`() = runBlocking {
        enqueue(200, payload)
        enqueue(200, payload)
        val result = client.contradictions("EGFR", "AKT1")
        val contradictions = (result as IndraResult.Ok).value
        assertTrue(contradictions.contradicted)
        assertEquals(listOf("Activation", "Inhibition"), contradictions.pairs.first().types)
        assertTrue(contradictions.disclaimer.contains("review trigger"))
    }

    @Test
    fun `free text is refused before any request is made`() = runBlocking {
        val result = client.priorKnowledge("EGFR mutation in patient John Doe", null)
        assertTrue(result is IndraResult.Refused)
        assertTrue((result as IndraResult.Refused).reason.contains("stay on this device"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `blank subject is refused`() = runBlocking {
        assertTrue(client.priorKnowledge("   ", null) is IndraResult.Refused)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `server errors degrade to unavailable`() = runBlocking {
        enqueue(503, "upstream down")
        val result = client.priorKnowledge("EGFR", "AKT1")
        assertTrue(result is IndraResult.Unavailable)
        assertTrue((result as IndraResult.Unavailable).reason.contains("503"))
    }

    @Test
    fun `unreadable json degrades to unavailable`() = runBlocking {
        enqueue(200, "<html>not json</html>")
        assertTrue(client.priorKnowledge("EGFR", "AKT1") is IndraResult.Unavailable)
    }

    @Test
    fun `repeat lookups are served from the in-memory cache`() = runBlocking {
        enqueue(200, payload)
        val first = client.priorKnowledge("EGFR", "AKT1")
        val second = client.priorKnowledge("EGFR", "AKT1")
        assertTrue(first is IndraResult.Ok)
        assertTrue(second is IndraResult.Ok)
        assertEquals(1, server.requestCount)
        assertFalse((second as IndraResult.Ok).value.statements.isEmpty())
    }

    @Test
    fun `min belief filters the prior list but keeps the count honest`() = runBlocking {
        enqueue(200, payload)
        val knowledge = (client.priorKnowledge("EGFR", "AKT1", minBelief = 0.8) as IndraResult.Ok).value
        assertEquals(1, knowledge.statementCount)
        assertEquals(0.91, knowledge.statements.single().belief, 1e-9)
    }
}

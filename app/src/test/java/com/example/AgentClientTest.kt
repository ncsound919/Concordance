package com.example

import com.example.concordance.integration.agent.AgentClient
import com.example.concordance.integration.agent.AgentConfig
import com.example.concordance.integration.agent.AgentOutcome
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real-HTTP tests for the agent client against a local mock server.
 *
 * These lock in the contract that matters most: a refusal is a result, not an error, and an
 * upload is a multipart POST that carries its data classification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AgentClientTest {

    private lateinit var server: MockWebServer
    private val client = AgentClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config(apiKey: String? = null) =
        AgentConfig(baseUrl = server.url("/").toString().trimEnd('/'), apiKey = apiKey)

    private fun enqueue(code: Int, body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    @Test
    fun `anchors are parsed with their evidence grade`() = runBlocking {
        enqueue(
            200,
            """
            [{"id":"giannoudis2024_pooled","label":"Giannoudis 2024 pooled",
              "evidence_grade":"counts","source":"Foundation Medicine cohort"}]
            """.trimIndent()
        )
        val outcome = client.anchors(config())
        val anchors = (outcome as AgentOutcome.Ok).value
        assertEquals(1, anchors.size)
        assertEquals("giannoudis2024_pooled", anchors[0].id)
        assertEquals("counts", anchors[0].evidenceGrade)
        assertEquals("/api/anchors", server.takeRequest().path)
    }

    @Test
    fun `adjudicate posts the anchor and returns the job handle`() = runBlocking {
        enqueue(200, """{"job_id":"job-1","status":"queued","durable":true}""")
        val outcome = client.adjudicate(config(), "giannoudis2024_pooled", dataClass = "aggregate")
        val job = (outcome as AgentOutcome.Ok).value
        assertEquals("job-1", job.jobId)
        assertEquals("queued", job.status)
        assertFalse(job.isTerminal)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/adjudicate", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"anchor_id\":\"giannoudis2024_pooled\""))
        assertTrue(body.contains("\"data_class\":\"aggregate\""))
    }

    @Test
    fun `completed job parses the capsule thresholds and trace`() = runBlocking {
        enqueue(
            200,
            """
            {"job_id":"job-2","status":"completed","capsule":{
              "job_id":"job-2","anchor_id":"giannoudis2024_pooled","verdict":"not_supported",
              "reason":"Detection artifact explains the depletion.",
              "threshold_values":{"corrected_or":0.42,"corrected_ci":[0.21,0.55],
                                  "significance_threshold_r":0.97},
              "uncertainty":{"sensitivity":0.9,"ci_lower":0.2,"ci_upper":0.5,"p_value":0.01,
                             "effect_size":0.3,"power":0.81},
              "decision_trace":["validated counts","applied correction"],
              "evidence_tier":"derived","rubric_version":"1.1","capsule_hash":"abc"}}
            """.trimIndent()
        )
        val job = (client.job(config(), "job-2") as AgentOutcome.Ok).value
        assertTrue(job.isTerminal)
        assertFalse(job.wasRefused)
        val capsule = requireNotNull(job.capsule)
        assertEquals("not_supported", capsule.verdict)
        assertEquals("derived", capsule.evidenceTier)
        assertEquals("1.1", capsule.rubricVersion)
        assertEquals(0.42, capsule.correctedOddsRatio!!, 1e-9)
        assertEquals(0.21, capsule.correctedCi!!.first, 1e-9)
        assertEquals(0.55, capsule.correctedCi!!.second, 1e-9)
        assertEquals(0.81, capsule.power!!, 1e-9)
        assertEquals(2, capsule.decisionTrace.size)
    }

    @Test
    fun `a withheld verdict is terminal and reads as a refusal`() = runBlocking {
        enqueue(
            200,
            """{"job_id":"job-3","status":"withheld","capsule":{"verdict":"withheld",
                "anchor_id":"x","reason":"evidence grade 'abstract_only' is not adjudicable",
                "evidence_tier":"unmeasured"}}"""
        )
        val job = (client.job(config(), "job-3") as AgentOutcome.Ok).value
        assertTrue(job.isTerminal)
        assertTrue(job.wasRefused)
        assertEquals("withheld", job.capsule!!.verdict)
    }

    @Test
    fun `a 422 refusal is an outcome not an error`() = runBlocking {
        enqueue(422, """{"detail":"unknown anchor_id 'not_a_real_anchor'"}""")
        val outcome = client.adjudicate(config(), "not_a_real_anchor")
        assertTrue(outcome is AgentOutcome.Refused)
        assertEquals("unknown anchor_id 'not_a_real_anchor'", (outcome as AgentOutcome.Refused).reason)
    }

    @Test
    fun `authentication failures are explained without a stack trace`() = runBlocking {
        enqueue(401, """{"detail":"missing or invalid API key"}""")
        val failure = client.health(config()) as AgentOutcome.Failed
        assertEquals(401, failure.status)
        assertTrue(failure.message.contains("credentials"))
        assertFalse(failure.message.contains("\tat "))
    }

    @Test
    fun `an unreachable agent is reported as such`() = runBlocking {
        // Port 1 is never listening; this is the "agent not started" case.
        val outcome = client.health(AgentConfig(baseUrl = "http://127.0.0.1:1", timeoutSeconds = 5))
        val failure = outcome as AgentOutcome.Failed
        assertEquals(0, failure.status)
        assertTrue(failure.message.contains("Cannot reach the agent"))
        // The failure names the transport class for diagnosis, but never a stack trace.
        assertFalse(failure.message.contains("\tat "))
        assertTrue(failure.message.contains("127.0.0.1:1"))
    }

    @Test
    fun `an unconfigured url is refused before any network call`() = runBlocking {
        val outcome = client.health(AgentConfig(baseUrl = ""))
        assertTrue(outcome is AgentOutcome.Failed)
        assertTrue((outcome as AgentOutcome.Failed).message.contains("No agent URL configured"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the api key is sent as a bearer token`() = runBlocking {
        enqueue(200, """{"status":"ok"}""")
        client.health(config(apiKey = "secret-key"))
        assertEquals("Bearer secret-key", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `explain returns the agent's own plain language`() = runBlocking {
        enqueue(200, EXPLAIN_PAYLOAD)
        val view = (client.explainJob(config(), "job-1") as AgentOutcome.Ok).value
        assertEquals("Moderate signal", view.headline)
        assertEquals("moderate", view.signalBand)
        assertEquals("Derived", view.qualityLabel)
        assertEquals("derived", view.evidenceTier)
        assertEquals(listOf("We took the published comparison."), view.whatWeDid)
        assertEquals(listOf("anchor"), view.wordsWeUsed.map { it.term })
        assertEquals("/api/jobs/job-1/explain", server.takeRequest().path)
    }

    @Test
    fun `found values and numbers keep a stable order without recalculating them`() = runBlocking {
        enqueue(200, EXPLAIN_PAYLOAD)
        val view = (client.explainJob(config(), "job-1") as AgentOutcome.Ok).value
        assertEquals(
            listOf("Reads as", "Difference after correction", "Likely range"),
            view.whatWeFound.map { it.first }
        )
        assertEquals("0.31", view.whatWeFound[1].second)
        // A two-element range is rendered as a range, and the digits are untouched.
        assertEquals("0.19 – 0.34", view.whatWeFound[2].second)
        assertTrue(view.numbers.any { it.first == "Leak factor r" && it.second == "0.6" })
        assertFalse(view.isNegativeish)
    }

    @Test
    fun `a cannot-tell band is flagged as not proof of absence`() = runBlocking {
        enqueue(
            200,
            """{"mode":"plain","headline":"Cannot be measured",
                "signal":{"band":"cannot_tell","label":"Cannot be measured","detail":"not enough"},
                "evidence_quality":{"tier":"unmeasured","label":"Unmeasured","detail":"none"}}"""
        )
        val view = (client.explainJob(config(), "job-9") as AgentOutcome.Ok).value
        assertTrue(view.isNegativeish)
        assertTrue(view.restsOnAssumptions)
    }

    @Test
    fun `figure upload is a multipart post carrying its data class`() = runBlocking {
        enqueue(
            200,
            """
            {"upload_sha256":"${"a".repeat(64)}","content_type":"image/png","bytes":1234,
             "data_class":"public","target_backend":"local","engine":"paddleocr",
             "pages":[{"text":"EGFR L858R","confidence":0.99,"source_hash":"abc"}],
             "text":"EGFR L858R","confidence":0.99,"evidence_tier":"derived",
             "human_verified":false,"adjudication_ready":false,"disclaimer":"Research tool."}
            """.trimIndent()
        )
        val outcome = client.uploadFigure(
            config = config(),
            bytes = byteArrayOf(1, 2, 3),
            fileName = "figure.png",
            contentType = "image/png",
            dataClass = "public",
            targetBackend = "local"
        )
        val figure = (outcome as AgentOutcome.Ok).value
        assertEquals("paddleocr", figure.engine)
        assertEquals("EGFR L858R", figure.text)
        assertEquals("derived", figure.evidenceTier)
        assertFalse(figure.humanVerified)
        assertFalse(figure.adjudicationReady)
        assertEquals(1, figure.pageCount)
        assertEquals("public", figure.dataClass)

        val request = server.takeRequest()
        assertEquals("/api/imaging/ocr", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"file\""))
        assertTrue(body.contains("filename=\"figure.png\""))
        assertTrue(body.contains("name=\"data_class\""))
        assertTrue(body.contains("name=\"target_backend\""))
        assertTrue(body.contains("local"))
    }

    @Test
    fun `a phi upload routed off-host is refused by the agent`() = runBlocking {
        enqueue(422, """{"detail":"PHI data class cannot be routed to 'aws_public'"}""")
        val outcome = client.uploadFigure(
            config = config(),
            bytes = byteArrayOf(1),
            fileName = "chart.png",
            contentType = "image/png",
            dataClass = "phi",
            targetBackend = "aws_public"
        )
        assertTrue(outcome is AgentOutcome.Refused)
        assertTrue((outcome as AgentOutcome.Refused).reason.contains("PHI"))
    }

    @Test
    fun `the agent's own explanation is surfaced for size and type errors`() = runBlocking {
        enqueue(413, """{"detail":"upload exceeds the 25 MB limit"}""")
        val tooBig = client.uploadFigure(config(), ByteArray(4), "big.png", "image/png")
        assertTrue(tooBig is AgentOutcome.Failed)
        assertTrue((tooBig as AgentOutcome.Failed).message.contains("25 MB limit"))

        enqueue(415, """{"detail":"unsupported media type 'text/plain'"}""")
        val wrongType = client.uploadFigure(config(), ByteArray(4), "notes.txt", "text/plain")
        assertTrue(wrongType is AgentOutcome.Failed)
        assertTrue((wrongType as AgentOutcome.Failed).message.contains("unsupported media type"))
    }

    @Test
    fun `a bare error body falls back to a readable message`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(413).setBody(""))
        val outcome = client.uploadFigure(config(), ByteArray(4), "big.png", "image/png")
        assertTrue(outcome is AgentOutcome.Failed)
        assertTrue((outcome as AgentOutcome.Failed).message.contains("too large"))

        server.enqueue(MockResponse().setResponseCode(415).setBody(""))
        val wrongType = client.uploadFigure(config(), ByteArray(4), "notes.txt", "text/plain")
        assertTrue((wrongType as AgentOutcome.Failed).message.contains("Unsupported file type"))
    }

    @Test
    fun `correction with explain returns the expert and plain halves`() = runBlocking {
        enqueue(
            200,
            """
            {"mode":"both",
             "result":{"anchor_id":"giannoudis2024_pooled","status":"ok","evidence_tier":"derived",
                       "r":0.6,"corrected_or":0.31,"corrected_ci":[0.19,0.34],"feasible":true,
                       "significance_threshold_r":0.97,"min_feasible_r":0.2,"significant_at_r":true},
             "plain":$EXPLAIN_PAYLOAD}
            """.trimIndent()
        )
        val result = (client.correction(config(), "giannoudis2024_pooled", site = "brain", sn = 0.9, explain = true)
            as AgentOutcome.Ok).value
        assertEquals("ok", result.status)
        assertEquals("derived", result.evidenceTier)
        assertEquals(0.31, result.correctedOddsRatio!!, 1e-9)
        assertEquals(0.97, result.significanceThresholdR!!, 1e-9)
        assertTrue(result.significantAtR)
        assertTrue(result.feasible)
        assertTrue(server.takeRequest().path!!.startsWith("/api/correction?explain=true"))
    }

    @Test
    fun `an inadjudicable anchor is refused with the agent's reason`() = runBlocking {
        enqueue(
            200,
            """{"anchor_id":"abstract_only_1","status":"withheld","evidence_tier":"unmeasured",
                "reason":"evidence grade 'abstract_only' is not adjudicable"}"""
        )
        val result = (client.correction(config(), "abstract_only_1") as AgentOutcome.Ok).value
        assertEquals("withheld", result.status)
        assertTrue(result.reason!!.contains("not adjudicable"))
        assertNull(result.correctedOddsRatio)
    }

    @Test
    fun `the plain correction view comes from the agent's own explain endpoint`() = runBlocking {
        enqueue(200, EXPLAIN_PAYLOAD)
        val view = (client.correctionEasyMode(config(), "giannoudis2024_pooled", site = "brain", sn = 0.9)
            as AgentOutcome.Ok).value
        val request = server.takeRequest()
        assertEquals("/api/explain/correction", request.path)
        assertTrue(request.body.readUtf8().contains("\"s_n\":0.9"))
        // The wording is the agent's, not ours — the client only arranges it.
        assertEquals("Moderate signal", view.headline)
        assertEquals("moderate", view.signalBand)
        assertFalse(view.isNegativeish)
        assertEquals("derived", view.evidenceTier)
        assertTrue(view.wordsWeUsed.isNotEmpty())
        assertTrue(view.nextSteps.isNotEmpty())
    }

    @Test
    fun `imaging status reports capabilities`() = runBlocking {
        enqueue(
            200,
            """{"ocr_available":true,"pdf_available":true,"engine":"paddleocr",
                "accepted_media_types":["application/pdf","image/png"],"max_upload_mb":25,
                "evidence_tier":"derived","human_verified_required":true,"notes":"local OCR"}"""
        )
        val status = (client.imagingStatus(config()) as AgentOutcome.Ok).value
        assertTrue(status.ocrAvailable)
        assertTrue(status.pdfAvailable)
        assertEquals(25, status.maxUploadMb)
        assertEquals(listOf("application/pdf", "image/png"), status.acceptedMediaTypes)
    }

    @Test
    fun `a malformed body is a failure not a crash`() = runBlocking {
        enqueue(200, "not json at all")
        val outcome = client.anchors(config())
        assertTrue(outcome is AgentOutcome.Failed)
        assertTrue((outcome as AgentOutcome.Failed).message.contains("Could not read"))
    }

    @Test
    fun `referee audit posts the claim and table and returns the verdict with its digests`() = runBlocking {
        enqueue(
            200,
            """
            {"verdict":"REPRODUCED",
             "reasons":["Cluster bootstrap reproduces the claimed interval"],
             "dimensions":{"leakage":"none","baseline":"beat"},
             "input_sha256":"${"a".repeat(64)}",
             "decision_sha256":"${"b".repeat(64)}",
             "report_sha256":"${"c".repeat(64)}",
             "provenance":"computed",
             "disposition":"commit"}
            """.trimIndent()
        )

        val claim = org.json.JSONObject("""{"claim_id":"c1","claimed_auc":0.85}""")
        val outcome = client.refereeAudit(config(), claim, "patient_id,label,score,split\n")

        assertTrue("referee audit outcome was: $outcome", outcome is AgentOutcome.Ok)
        val report = (outcome as AgentOutcome.Ok).value
        assertEquals("REPRODUCED", report.verdict)
        assertEquals(1, report.reasons.size)
        assertEquals("none", report.dimensions["leakage"])
        assertEquals("computed", report.provenance)
        assertEquals("c".repeat(64), report.reportSha256)

        val request = server.takeRequest()
        assertEquals("/api/referee/audit", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"claim\""))
        assertTrue(body.contains("\"csv_text\""))
        assertTrue(body.contains("\"data_class\""))
        assertTrue(body.contains("\"target_backend\""))
    }

    @Test
    fun `a referee refusal is a result, not an error to retry`() = runBlocking {
        enqueue(422, """{"detail":"PHI cannot be routed off-host"}""")

        val claim = org.json.JSONObject("""{"claim_id":"c1"}""")
        val outcome = client.refereeAudit(config(), claim, "patient_id,label,score,split\n")

        assertTrue(outcome is AgentOutcome.Refused)
        assertEquals(422, (outcome as AgentOutcome.Refused).status)
    }

    @Test
    fun `every call is recorded in the provenance log`() = runBlocking {
        enqueue(200, """{"status":"ok"}""")
        val before = com.example.concordance.integration.api.ProvenanceRecorder.records.value.size
        client.health(config())
        val after = com.example.concordance.integration.api.ProvenanceRecorder.records.value
        assertTrue(after.size > before)
        val agentRecord = after.firstOrNull { it.providerId == AgentClient.PROVIDER_ID }
        assertNotNull(agentRecord)
        assertEquals(200, agentRecord!!.httpStatus)
    }

    @Test
    fun `provenance stores a digest and never the response body`() = runBlocking {
        enqueue(200, """{"status":"ok","secret":"patient-like-content"}""")
        client.health(config())
        val record = com.example.concordance.integration.api.ProvenanceRecorder.records.value
            .first { it.providerId == AgentClient.PROVIDER_ID }
        assertEquals(64, record.responseSha256.length)
        assertFalse(record.toString().contains("patient-like-content"))
    }

    private companion object {
        val EXPLAIN_PAYLOAD = """
        {"mode":"plain","headline":"Moderate signal",
         "signal":{"band":"moderate","label":"Moderate signal","detail":"The difference appears after correction."},
         "evidence_quality":{"tier":"derived","label":"Derived","detail":"Computed from the published counts."},
         "what_we_did":["We took the published comparison."],
         "what_we_found":{"reads_as":"less common at this site",
                          "difference_after_correction":0.31,
                          "likely_range":[0.19,0.34]},
         "what_it_does_not_mean":["This is not medical advice."],
         "words_we_used":[{"term":"anchor","plain":"A claim we are checking.","why":"Example."}],
         "next_steps":["Read the likely range."],
         "the_numbers":{"verdict":"inconclusive","evidence_tier":"derived","leak_factor_r":0.6}}
        """.trimIndent()
    }
}

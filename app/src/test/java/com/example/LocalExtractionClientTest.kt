package com.example

import com.example.concordance.model.EvidenceGrade
import com.example.concordance.local.LlmExtraction
import com.example.concordance.local.LocalExtractionClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the tolerant JSON parsing inside [LocalExtractionClient].
 * The extraction client must never throw on model output — models wrap JSON in
 * prose and markdown fences, drop commas, and misquote numbers; all of that is
 * allowed to degrade to null fields rather than an exception.
 */
class LocalExtractionClientTest {

    private val cleanJson = """
        {
          "gene": "esr1",
          "variant": "y537s",
          "brain_events": 12,
          "brain_total": 531,
          "other_events": 3,
          "other_total": 498,
          "odds_ratio": 4.1,
          "ci_low": 1.25,
          "ci_high": 13.7,
          "confidence": 0.86,
          "summary": "ESR1 Y537S carriers had more brain metastases on ctDNA follow-up."
        }
    """.trimIndent()

    @Test
    fun `parses a clean flat object`() {
        val parsed = LocalExtractionClient.parseExtractionJson(cleanJson)!!
        assertEquals("ESR1", parsed.gene)
        assertEquals("Y537S", parsed.variant)
        assertEquals(12, parsed.brainEvents)
        assertEquals(531, parsed.brainTotal)
        assertEquals(3, parsed.otherEvents)
        assertEquals(498, parsed.otherTotal)
        assertEquals(4.1, parsed.oddsRatio!!, 1e-9)
        assertEquals(1.25, parsed.ciLow!!, 1e-9)
        assertEquals(13.7, parsed.ciHigh!!, 1e-9)
        assertEquals(0.86, parsed.confidence!!, 1e-9)
        assertTrue(parsed.summary!!.contains("brain metastases"))
    }

    @Test
    fun `tolerates markdown fences and surrounding prose`() {
        val messy = "Here is the result:\n```json\n$cleanJson\n```\nHope that helps!"
        val parsed = LocalExtractionClient.parseExtractionJson(messy)!!
        assertEquals("ESR1", parsed.gene)
        assertEquals(4.1, parsed.oddsRatio!!, 1e-9)
    }

    @Test
    fun `extracts content from a chat completion envelope`() {
        val envelope =
            """{"choices":[{"finish_reason":"stop","index":0,"message":{"role":"assistant","content":"```json\n{\n  \"gene\": \"ESR1\",\n  \"variant\": \"Y537S\"\n}\n```"}}]}"""
        val content = LocalExtractionClient.extractChatContent(envelope)
        assertTrue(content.contains("\"gene\": \"ESR1\""))
        assertEquals("ESR1", LocalExtractionClient.parseExtractionJson(content)?.gene)
    }

    @Test
    fun `empty or missing chat content stays empty`() {
        assertEquals(
            "",
            LocalExtractionClient.extractChatContent(
                """{"choices":[{"message":{"content":"","reasoning_content":"thinking"}}]}"""
            )
        )
        assertEquals("", LocalExtractionClient.extractChatContent("""{"choices":[]}"""))
        assertEquals("", LocalExtractionClient.extractChatContent("not json"))
    }

    @Test
    fun `null for empty, blank, or objectless output`() {
        assertNull(LocalExtractionClient.parseExtractionJson(""))
        assertNull(LocalExtractionClient.parseExtractionJson("   "))
        assertNull(LocalExtractionClient.parseExtractionJson("No JSON here at all."))
        assertNull(LocalExtractionClient.parseExtractionJson("{ unbalanced"))
    }

    @Test
    fun `null when model reports nothing usable`() {
        val empty = """{"gene": null, "variant": null, "summary": null}"""
        assertNull(LocalExtractionClient.parseExtractionJson(empty))
    }

    @Test
    fun `rejects a gene token that cannot be an HGNC symbol`() {
        val longToken = cleanJson.replace("\"esr1\"", "\"SOMEREAALLYLONGTOKEN\"")
        val parsed = LocalExtractionClient.parseExtractionJson(longToken)
        // Numbers remain valid, so extraction still succeeds — but the bogus
        // gene token is dropped rather than trusted.
        assertEquals(null, parsed?.gene)
        assertEquals(4.1, parsed?.oddsRatio!!, 1e-9)
    }

    @Test
    fun `accepts numbers given as strings`() {
        val stringy = """{"gene": "PIK3CA", "odds_ratio": "2.4", "brain_events": "8"}"""
        val parsed = LocalExtractionClient.parseExtractionJson(stringy)!!
        assertEquals("PIK3CA", parsed.gene)
        assertEquals(8, parsed.brainEvents)
        assertEquals(2.4, parsed.oddsRatio!!, 1e-9)
    }

    @Test
    fun `extraction still parses when no numbers exist but gene does`() {
        val geneOnly = """{"gene": "BRCA1"}"""
        val parsed = LocalExtractionClient.parseExtractionJson(geneOnly)!!
        assertEquals("BRCA1", parsed.gene)
        assertNull(parsed.brainEvents)
        assertNull(parsed.oddsRatio)
    }

    @Test
    fun `maps counts to COUNTS grade and high default confidence`() {
        val result = LocalExtractionClient.toResult(
            LlmExtraction(
                gene = "ESR1", brainEvents = 12, brainTotal = 531,
                otherEvents = 3, otherTotal = 498
            ),
            elapsedMs = 1_500
        )
        assertEquals(EvidenceGrade.COUNTS, result.evidenceGrade)
        assertEquals(0.95, result.confidenceScore, 1e-9)
        assertEquals(1_500, result.executionTimeMs)
    }

    @Test
    fun `maps odds ratio to SUMMARY grade`() {
        val result = LocalExtractionClient.toResult(
            LlmExtraction(gene = "AKT1", oddsRatio = 2.0, ciLow = 1.1, ciHigh = 3.9),
            elapsedMs = 100
        )
        assertEquals(EvidenceGrade.SUMMARY, result.evidenceGrade)
        assertEquals(0.70, result.confidenceScore, 1e-9)
    }

    @Test
    fun `maps no-statistics output to ABSTRACT_ONLY`() {
        val result = LocalExtractionClient.toResult(
            LlmExtraction(gene = "TP53"),
            elapsedMs = 100
        )
        assertEquals(EvidenceGrade.ABSTRACT_ONLY, result.evidenceGrade)
    }

    @Test
    fun `extracted confidence is clamped into 0 to 1`() {
        val over = LocalExtractionClient.toResult(LlmExtraction(gene = "PTEN", confidence = 4.0), 0)
        val under = LocalExtractionClient.toResult(LlmExtraction(gene = "PTEN", confidence = -1.0), 0)
        assertTrue(over.confidenceScore in 0.0..1.0)
        assertTrue(under.confidenceScore in 0.0..1.0)
    }

    @Test
    fun `modality reflects ctDNA language in the summary`() {
        val liquid = LocalExtractionClient.toResult(
            LlmExtraction(gene = "CDH1", summary = "Followed with plasma ctDNA sampling."),
            0
        )
        assertTrue(liquid.modalityCompared.contains("ctDNA"))

        val tissue = LocalExtractionClient.toResult(
            LlmExtraction(gene = "CDH1", summary = "All samples from tissue biopsy."),
            0
        )
        assertTrue(tissue.modalityCompared.contains("Tissue"))
    }

    @Test
    fun `prompt lists the schema keys and embeds the abstract`() {
        val prompt = LocalExtractionClient.buildExtractionPrompt("Some abstract text.")
        listOf("gene", "variant", "brain_events", "brain_total", "odds_ratio", "summary")
            .forEach { key -> assertTrue("missing key: $key", prompt.contains(key)) }
        assertTrue(prompt.contains("Some abstract text."))
    }

    @Test
    fun `prompt is capped for long abstracts`() {
        val longAbstract = "word ".repeat(20_000)
        val prompt = LocalExtractionClient.buildExtractionPrompt(longAbstract)
        assertFalse(prompt.length > longAbstract.length)
    }
}

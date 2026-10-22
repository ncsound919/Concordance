package com.example

import com.example.concordance.local.LocalExtractionClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalExtractionClientTest2 {
    @Test
    fun buildExtractionPrompt_truncates() {
        val p = LocalExtractionClient.buildExtractionPrompt("A".repeat(5000))
        assertTrue(p.contains("A".repeat(4000)))
    }

    @Test
    fun extractChatContent() {
        val raw = """{"choices":[{"message":{"content":"{\"gene\":\"EGFR\"}"}}]}"""
        assertEquals("""{"gene":"EGFR"}""", LocalExtractionClient.extractChatContent(raw))
    }

    @Test
    fun extractChatContent_missing() {
        assertEquals("", LocalExtractionClient.extractChatContent("no content here"))
    }

    @Test
    fun parseExtractionJson_counts() {
        val raw = """{"gene":"EGFR","brain_events":5,"brain_total":10,"other_events":2,"other_total":100,"confidence":0.9}"""
        val e = LocalExtractionClient.parseExtractionJson(raw)
        assertNotNull(e)
        assertEquals("EGFR", e!!.gene)
        assertEquals(5, e.brainEvents)
    }

    @Test
    fun parseExtractionJson_nullOnNoData() {
        assertNull(LocalExtractionClient.parseExtractionJson("""{"foo":"bar"}"""))
    }

    @Test
    fun parseExtractionJson_toleratesFences() {
        val raw = "Here is the result:\n```json\n{\"gene\":\"TP53\",\"brain_events\":1,\"brain_total\":2,\"other_events\":3,\"other_total\":4}\n```"
        assertNotNull(LocalExtractionClient.parseExtractionJson(raw))
    }

    @Test
    fun toResult() {
        val e = LocalExtractionClient.parseExtractionJson("""{"gene":"EGFR","brain_events":5,"brain_total":10,"other_events":2,"other_total":100,"summary":"plasma ctDNA"}""")
        val r = LocalExtractionClient.toResult(e!!, 42L)
        assertEquals("EGFR", r.gene)
        assertEquals(42L, r.executionTimeMs)
        assertTrue(r.modalityCompared.contains("Plasma") || r.modalityCompared.contains("plasma", true))
    }
}

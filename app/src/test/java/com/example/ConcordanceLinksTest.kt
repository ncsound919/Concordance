package com.example

import android.net.Uri
import com.example.concordance.links.ConcordanceLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConcordanceLinksTest {

    private fun id(raw: String?): String? = ConcordanceLinks.anchorId(raw?.let { Uri.parse(it) })

    @Test
    fun `accepts a verified anchor app link`() {
        assertEquals("abc-123", id("https://concordance-web.tap4500.workers.dev/concordance/anchor/abc-123"))
    }

    @Test
    fun `accepts the custom scheme`() {
        assertEquals("abc-123", id("concordance://anchor/abc-123"))
    }

    @Test
    fun `refuses a foreign host`() {
        assertNull(id("https://evil.example/concordance/anchor/abc-123"))
    }

    @Test
    fun `refuses another surface path`() {
        assertNull(id("https://concordance-web.tap4500.workers.dev/sieve/recall?q=EGFR"))
    }

    @Test
    fun `refuses other schemes`() {
        assertNull(id("javascript:alert(1)"))
        assertNull(id("file:///etc/passwd"))
        assertNull(id("aetherdesk://anchor/abc-123"))
    }

    @Test
    fun `refuses an unsafe id`() {
        assertNull(id("concordance://anchor/../../etc/passwd"))
        assertNull(id("concordance://anchor/${"x".repeat(200)}"))
        assertNull(id("concordance://anchor/"))
    }

    @Test
    fun `refuses a link with no anchor`() {
        assertNull(id("https://concordance-web.tap4500.workers.dev/concordance"))
        assertNull(id(null))
    }
}

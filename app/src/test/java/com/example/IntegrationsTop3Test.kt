package com.example

import com.example.concordance.integration.orcid.OrcidClient
import com.example.concordance.integration.zenodo.ZenodoClient
import com.example.concordance.integration.zooniverse.ZooniverseClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationsTop3Test {
    @Test
    fun `orcid validates id format`() {
        val c = OrcidClient()
        assertTrue(c.validateId("0000-0001-2345-6789/extra") == null)
        assertTrue(c.validateId("") != null)
        assertTrue(c.validateId("no-slash") != null)
    }

    @Test
    fun `zenodo configured only with token`() {
        val c = ZenodoClient()
        assertFalse(c.isConfigured(null))
        assertFalse(c.isConfigured(""))
        assertTrue(c.isConfigured("secret"))
    }

    @Test
    fun `zenodo payload has required metadata`() {
        val c = ZenodoClient()
        val p = c.buildPayload("title", "desc", listOf("A", "B"))
        val meta = p["metadata"] as Map<*, *>
        assertEquals("title", meta["title"])
        assertEquals("cc-by-4.0", meta["license"])
        assertEquals(2, (meta["creators"] as List<*>).size)
    }

    @Test
    fun `zooniverse builds project url`() {
        val c = ZooniverseClient()
        val url = c.projectsUrl("https://example.org/api", "birds", 5)
        assertTrue(url.contains("search=birds"))
        assertTrue(url.contains("page_size=5"))
    }

    @Test
    fun `zooniverse refuses phi data class`() {
        val c = ZooniverseClient()
        assertFalse(c.isPhiSafe("phi"))
        assertTrue(c.isPhiSafe("aggregate"))
    }
}

package com.example

import com.example.concordance.ecosystem.EcosystemLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EcosystemLinksTest {

    @Test
    fun `builds an allow-listed recall link`() {
        assertEquals(
            "sieve://recall?q=ESR1%20Y537S",
            EcosystemLinks.build("sieve", "recall", mapOf("q" to "ESR1 Y537S"))
        )
    }

    @Test
    fun `refuses an unknown peer or path`() {
        assertNull(EcosystemLinks.build("unknown", "recall"))
        assertNull(EcosystemLinks.build("sieve", "admin"))
    }

    @Test
    fun `parses a link it produced`() {
        val link = EcosystemLinks.parse("recourse://research?q=EGFR")
        assertEquals("recourse", link?.peer?.id)
        assertEquals("research", link?.path)
        assertEquals("EGFR", link?.params?.get("q"))
    }

    @Test
    fun `refuses foreign schemes and plain web urls`() {
        assertNull(EcosystemLinks.parse("https://evil.example/sieve/recall"))
        assertNull(EcosystemLinks.parse("javascript:alert(1)"))
        assertNull(EcosystemLinks.parse("sieve://admin"))
        assertNull(EcosystemLinks.parse(""))
    }

    @Test
    fun `registry and mesh contract paths are recorded`() {
        assertEquals("eco/wiring/registry.json", EcosystemLinks.REGISTRY_PATH)
        assertEquals(
            "phone-apps/shared/llm-mesh/spec/llm-mesh.openapi.json",
            EcosystemLinks.MESH_CONTRACT_PATH
        )
    }
}

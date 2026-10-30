package com.example

import com.example.concordance.integration.agent.FreeTierModels
import com.example.concordance.integration.supabase.SyncConflict
import com.example.concordance.integration.supabase.SyncConflictBus
import com.example.concordance.integration.retraction.RetractionCheck
import org.json.JSONObject
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
 * Small, self-contained pieces of the integration layer: the free-tier model
 * catalogue, the sync conflict bus, and the moderation flag body.
 *
 * The moderation flag body is built with `org.json`, so this needs the
 * Robolectric runtime.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IntegrationSupportTest {

    @Before
    fun setUp() {
        SyncConflictBus.clear()
    }

    // -----------------------------------------------------------------------
    // The free-tier model catalogue
    // -----------------------------------------------------------------------

    @Test
    fun `every provider has a list of models`() {
        for (provider in listOf("openrouter", "gemini", "mistral", "nvidia", "ollama", "opencode")) {
            val models = FreeTierModels.allFor(provider)
            assertTrue("$provider had no models", models.isNotEmpty())
        }
    }

    @Test
    fun `the provider lookup is case insensitive`() {
        assertEquals(FreeTierModels.gemini, FreeTierModels.allFor("GEMINI"))
        assertEquals(FreeTierModels.gemini, FreeTierModels.allFor("Gemini"))
    }

    @Test
    fun `an unknown provider has no models`() {
        assertTrue(FreeTierModels.allFor("not-a-provider").isEmpty())
        assertTrue(FreeTierModels.allFor("").isEmpty())
    }

    @Test
    fun `no model id is blank`() {
        for (provider in listOf("openrouter", "gemini", "mistral", "nvidia", "ollama", "opencode")) {
            for (model in FreeTierModels.allFor(provider)) {
                assertTrue("blank model id in $provider", model.isNotBlank())
            }
        }
    }

    @Test
    fun `no model id is listed twice within a provider`() {
        for (provider in listOf("openrouter", "gemini", "mistral", "nvidia", "ollama", "opencode")) {
            val models = FreeTierModels.allFor(provider)
            assertEquals(
                "duplicate model id in $provider",
                models.size,
                models.toSet().size
            )
        }
    }

    @Test
    fun `the open router free models are marked as free`() {
        // Every OpenRouter entry except the auto-router must carry the :free
        // suffix, otherwise the app would offer a model that costs money.
        val paid = FreeTierModels.openrouter.filter { !it.endsWith(":free") && it != "openrouter/free" }
        assertTrue("paid OpenRouter models offered: $paid", paid.isEmpty())
    }

    // -----------------------------------------------------------------------
    // The sync conflict bus
    // -----------------------------------------------------------------------

    @Test
    fun `a reported conflict appears on the bus`() {
        val conflict = SyncConflict("claim", "c1", "field differs")
        SyncConflictBus.report(conflict)
        assertEquals(listOf(conflict), SyncConflictBus.conflicts.value)
    }

    @Test
    fun `several conflicts accumulate`() {
        val a = SyncConflict("claim", "c1", "field differs")
        val b = SyncConflict("capsule", "k1", "version differs")
        SyncConflictBus.report(a)
        SyncConflictBus.report(b)
        assertEquals(2, SyncConflictBus.conflicts.value.size)
    }

    @Test
    fun `a conflict can be removed`() {
        val conflict = SyncConflict("claim", "c1", "field differs")
        SyncConflictBus.report(conflict)
        SyncConflictBus.remove(conflict)
        assertTrue(SyncConflictBus.conflicts.value.isEmpty())
    }

    @Test
    fun `resolving a conflict records the choice and clears the conflict`() {
        val conflict = SyncConflict("claim", "c1", "field differs")
        SyncConflictBus.report(conflict)
        SyncConflictBus.resolve(conflict, "keep-local")
        assertTrue(SyncConflictBus.conflicts.value.isEmpty())
        assertEquals("keep-local", SyncConflictBus.resolutionFor("claim", "c1"))
    }

    @Test
    fun `a resolution is keyed by entity type and id`() {
        val conflict = SyncConflict("claim", "c1", "field differs")
        SyncConflictBus.report(conflict)
        SyncConflictBus.resolve(conflict, "accept-server")
        assertNull(SyncConflictBus.resolutionFor("capsule", "c1"))
        assertNull(SyncConflictBus.resolutionFor("claim", "other"))
    }

    @Test
    fun `an unresolved conflict has no resolution`() {
        assertNull(SyncConflictBus.resolutionFor("claim", "c1"))
    }

    @Test
    fun `clearing empties the conflict list`() {
        SyncConflictBus.report(SyncConflict("claim", "c1", "field differs"))
        SyncConflictBus.clear()
        assertTrue(SyncConflictBus.conflicts.value.isEmpty())
    }

    // -----------------------------------------------------------------------
    // The moderation flag body
    // -----------------------------------------------------------------------

    @Test
    fun `the moderation flag body carries the three fields`() {
        val body = JSONObject(RetractionCheck.flagBody("paper", "10.1038/x", "retracted"))
        assertEquals("paper", body.getString("artifact_type"))
        assertEquals("10.1038/x", body.getString("artifact_id"))
        assertEquals("retracted", body.getString("reason"))
    }

    @Test
    fun `the moderation flag body is valid json`() {
        val raw = RetractionCheck.flagBody("paper", "id", "reason")
        assertNotNull(JSONObject(raw))
        assertTrue(raw.startsWith("{"))
        assertTrue(raw.endsWith("}"))
    }
}

package com.example

import com.example.concordance.integration.api.IntegrationRegistry
import com.example.concordance.integration.api.KaggleComputeAdapter
import com.example.concordance.integration.supabase.SyncConflictBus
import com.example.concordance.integration.supabase.SyncConflict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResearchExperimentCoverageTest {

    @Test
    fun kaggleComputeAdapterIsRegistered() {
        val desc = IntegrationRegistry.descriptor("kaggle_api")
        assertNotNull(desc)
        assertEquals("kaggle_api", desc?.id)
        assertEquals("Kaggle Compute (bounty execution)", desc?.displayName)
    }

    @Test
    fun kaggleComputeAdapterHasComputeCapability() {
        val desc = IntegrationRegistry.descriptor("kaggle_api")
        assertNotNull(desc)
        assertTrue(desc!!.capabilities.contains(com.example.concordance.integration.api.Capability.COMPUTE))
    }

    @Test
    fun allNewIntegrationAdaptersAreRegistered() {
        val expected = listOf(
            "semantic_scholar", "pubmed_eutils", "biorxiv_api", "chembl_api", "uniprot_api",
            "gdc_api", "pubpeer_api", "osf_api", "figshare_api", "zotero_api", "unpaywall_api", "gnomad_api"
        )
        for (id in expected) {
            assertNotNull("Adapter $id should be registered", IntegrationRegistry.descriptor(id))
        }
    }

    @Test
    fun totalAdapterCountIsAtLeast20() {
        assertTrue(IntegrationRegistry.adapters.size >= 18)
    }

    @Test
    fun syncConflictBusReportsAndClears() {
        SyncConflictBus.clear()
        assertEquals(0, SyncConflictBus.conflicts.value.size)
        val c = SyncConflict("claim", "test-id", "test reason")
        SyncConflictBus.report(c)
        assertEquals(1, SyncConflictBus.conflicts.value.size)
        assertEquals("test-id", SyncConflictBus.conflicts.value[0].entityId)
        SyncConflictBus.clear()
        assertEquals(0, SyncConflictBus.conflicts.value.size)
    }

    @Test
    fun syncConflictBusResolvesAndRemoves() {
        SyncConflictBus.clear()
        val c = SyncConflict("capsule", "cap-1", "divergence")
        SyncConflictBus.report(c)
        assertEquals(1, SyncConflictBus.conflicts.value.size)
        SyncConflictBus.resolve(c, "keep_local")
        assertEquals(0, SyncConflictBus.conflicts.value.size)
        assertEquals("keep_local", SyncConflictBus.resolutionFor("capsule", "cap-1"))
        SyncConflictBus.clear()
    }

    @Test
    fun syncConflictBusRemovesSpecificConflict() {
        SyncConflictBus.clear()
        val c1 = SyncConflict("claim", "a", "reason a")
        val c2 = SyncConflict("claim", "b", "reason b")
        SyncConflictBus.report(c1)
        SyncConflictBus.report(c2)
        assertEquals(2, SyncConflictBus.conflicts.value.size)
        SyncConflictBus.remove(c1)
        assertEquals(1, SyncConflictBus.conflicts.value.size)
        assertEquals("b", SyncConflictBus.conflicts.value[0].entityId)
        SyncConflictBus.clear()
    }

    @Test
    fun kaggleComputeAdapterDescriptorHasCorrectCategory() {
        val desc = KaggleComputeAdapter.descriptor
        assertEquals("kaggle_api", desc.id)
        assertEquals("Kaggle Compute (bounty execution)", desc.displayName)
    }
}

package com.example

import com.example.concordance.engine.CapsuleEngine
import com.example.concordance.integration.cloudflare.CloudflareR2Manager
import com.example.concordance.model.AdjudicationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CloudflareR2ManagerTest {

    private fun fakeCapsule() = com.example.concordance.model.Capsule(
        id = "cap-test-1",
        permalink = "concordance.app/c/cap-test-1",
        createdUtc = "2026-10-19T00:00:00Z",
        sha256 = "abc123",
        question = "q",
        anchorLabel = "a",
        verdict = com.example.concordance.model.Verdict.SUPPORTED,
        citableArgument = "arg",
        plainLanguageVerdict = "plain",
        rNull = 0.4,
        rSignificance = 0.5,
        snAssumed = 0.9,
        rLow = 0.3,
        rHigh = 0.9,
        observedOr = 0.35,
        rawJson = "{\"x\":1}",
        markdown = "md"
    )

    @Test
    fun unconfiguredR2IsReportedAsSimulatedNotSynced() = runBlocking {
        CloudflareR2Manager.updateConfig("acct123", "bucket-r2", null)
        val capsule = fakeCapsule()
        val synced = CloudflareR2Manager.syncCapsuleToR2(capsule)

        assertEquals(capsule.id, synced.capsuleId)
        assertEquals("bucket-r2", synced.bucket)
        assertEquals("acct123", synced.accountId)
        assertEquals(true, synced.simulated)
        assertEquals("", synced.publicUrl)
        assertEquals(1, CloudflareR2Manager.syncedObjects.value.size)
        assertEquals(CloudflareR2Manager.R2SyncStatus.SIMULATED, CloudflareR2Manager.syncStatus.value)
    }

    @Test
    fun removeSyncedObjectClearsIt() = runBlocking {
        CloudflareR2Manager.updateConfig("acct123", "bucket-r2", null)
        val capsule = fakeCapsule()
        CloudflareR2Manager.syncCapsuleToR2(capsule)
        CloudflareR2Manager.removeSyncedObject(capsule.id)
        assertTrue(CloudflareR2Manager.syncedObjects.value.isEmpty())
    }

    @Test
    fun realPathPutsToMockServerWhenConfigured() = runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()
        try {
            val url = server.url("/").toString().trimEnd('/')
            CloudflareR2Manager.updateConfig("acct123", "bucket-r2", "tok-abc", endpoint = url)
            server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200))
            val capsule = fakeCapsule()
            val synced = CloudflareR2Manager.syncCapsuleToR2(capsule)

            // The real path should have issued a PUT with the bearer token.
            val req = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals("PUT", req?.method)
            assertEquals("Bearer tok-abc", req?.getHeader("Authorization"))
            assertTrue(req?.path?.contains(capsule.id) == true)
            assertEquals(capsule.id, synced.capsuleId)
            assertEquals(false, synced.simulated)
        } finally {
            server.shutdown()
        }
    }
    @Test
    fun isConfiguredReflectsTokenAndAccount() {
        CloudflareR2Manager.updateConfig("", "bucket", "tok")
        assertEquals(false, CloudflareR2Manager.isConfigured)
        CloudflareR2Manager.updateConfig("acct", "bucket", "tok")
        assertEquals(true, CloudflareR2Manager.isConfigured)
        CloudflareR2Manager.updateConfig("acct", "bucket", null)
        assertEquals(false, CloudflareR2Manager.isConfigured)
    }
}

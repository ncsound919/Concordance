package com.example

import com.example.concordance.integration.aws.AwsCloudManager
import com.example.concordance.integration.aws.AwsSyncStatus
import com.example.concordance.model.Capsule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AwsCloudManagerTest {

    private fun fakeCapsule() = Capsule(
        id = "cap-aws-1",
        permalink = "concordance.app/c/cap-aws-1",
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
    fun syncCapsuleToS3SimulatesWhenUnconfigured() = runBlocking {
        AwsCloudManager.updateConfig(null, null)
        val capsule = fakeCapsule()
        val synced = AwsCloudManager.syncCapsuleToS3(capsule)
        assertEquals(capsule.id, synced.capsuleId)
        assertEquals(true, synced.simulated)
        assertEquals("", synced.presignedUrl)
        assertEquals(AwsSyncStatus.SIMULATED, AwsCloudManager.syncStatus.value)
        assertEquals(1, AwsCloudManager.syncedObjects.value.size)
    }

    @Test
    fun realPathPutsToMockServerWhenConfigured() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val url = server.url("/").toString().trimEnd('/')
            AwsCloudManager.updateConfig(
                accessKey = "AKIAIOSFODNN7EXAMPLE",
                secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                region = "us-east-2",
                bucket = "test-bucket",
                endpoint = url
            )
            server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200))
            val capsule = fakeCapsule()
            val synced = AwsCloudManager.syncCapsuleToS3(capsule)
            val req = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals("PUT", req?.method)
            assertTrue(req?.getHeader("Authorization")?.startsWith("AWS4-HMAC-SHA256") == true)
            assertEquals(capsule.id, synced.capsuleId)
            assertEquals(false, synced.simulated)
        } finally {
            server.shutdown()
            AwsCloudManager.updateConfig(null, null)
        }
    }

    @Test
    fun isConfiguredReflectsCreds() {
        AwsCloudManager.updateConfig(null, null)
        assertEquals(false, AwsCloudManager.isConfigured)
        AwsCloudManager.updateConfig("AK", "SK")
        assertEquals(true, AwsCloudManager.isConfigured)
        AwsCloudManager.updateConfig(null, null)
    }
}

package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.concordance.integration.queue.JobQueueManager
import com.example.concordance.integration.tasker.TaskerBridge
import com.example.concordance.local.LocalExtractionClient
import com.example.concordance.local.LocalModels
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HardeningTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // --- Tasker bridge: the exported receiver must not be able to grow prefs without bound ---

    @Test
    fun taskerQueueIsBounded() {
        TaskerBridge.drain(context)
        repeat(TaskerBridge.MAX_QUEUE + 20) { i ->
            // Distinct extras so the dedupe window does not collapse them.
            TaskerBridge.enqueue(context, TaskerBridge.ACTION_RE_ADJUDICATE, "rev-$i-${System.nanoTime()}")
        }
        assertEquals(TaskerBridge.MAX_QUEUE, TaskerBridge.pendingCount(context))
        assertTrue(TaskerBridge.drain(context).size <= TaskerBridge.MAX_QUEUE)
        assertEquals(0, TaskerBridge.pendingCount(context))
    }

    @Test
    fun repeatedIdenticalTriggersCollapse() {
        TaskerBridge.drain(context)
        repeat(5) { TaskerBridge.enqueue(context, TaskerBridge.ACTION_CHECK_AUTO_TRIGGER) }
        assertEquals(1, TaskerBridge.pendingCount(context))
        TaskerBridge.drain(context)
    }

    @Test
    fun oversizedExtraIsTruncated() {
        TaskerBridge.drain(context)
        TaskerBridge.enqueue(context, TaskerBridge.ACTION_RE_ADJUDICATE, "x".repeat(5_000))
        val drained = TaskerBridge.drain(context)
        assertEquals(1, drained.size)
        assertTrue(drained.first().length < 300)
    }

    @Test
    fun unknownActionsAreIgnored() {
        TaskerBridge.drain(context)
        TaskerBridge.handleIntent(context, android.content.Intent("com.evil.action.NOT_A_CONCORDANCE_ACTION"))
        assertEquals(0, TaskerBridge.pendingCount(context))
    }

    // --- Job queue: seeded runs must be identifiable as samples ---

    @Test
    fun seededJobsAndWorkersAreFlaggedAsSamples() {
        val jobs = JobQueueManager.allJobs.value
        val workers = JobQueueManager.workerNodes.value
        assertTrue("expected seeded jobs", jobs.isNotEmpty())
        assertTrue("every seeded job must be labelled", jobs.all { it.isSample })
        assertTrue("every seeded worker must be labelled", workers.all { it.isSample })
    }

    @Test
    fun submittedJobsAreLabelledAsSimulated() {
        val job = JobQueueManager.submitHeavyProcess(
            dataClass = com.example.concordance.integration.queue.JobDataClass.SENSITIVITY_PERMUTATION,
            routingPolicy = com.example.concordance.integration.queue.WorkerRoutingPolicy.LOCAL_WORKER,
            inputs = "{}",
            modelRevision = "test-rev",
            params = emptyMap()
        )
        assertTrue("nothing remote is contacted, so the job must be flagged", job.isSample)
        assertNull("a simulated run has no citable artifact", job.citableCapsuleSha256)
    }

    // --- Local model server: the loopback listener must require the session key ---

    @Test
    fun localModelKeyIsRotatedAndCleared() {
        val first = LocalModels.rotateApiKey()
        val second = LocalModels.rotateApiKey()
        assertEquals(32, first.length)
        assertTrue(first != second)
        assertEquals(second, LocalModels.currentApiKey)
        LocalModels.clearApiKey()
        assertNull(LocalModels.currentApiKey)
    }

    @Test
    fun extractionClientSendsTheSessionKey() {
        val server = MockWebServer()
        server.start()
        try {
            LocalModels.rotateApiKey()
            val key = LocalModels.currentApiKey
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[{"message":{"content":"{}"}}]}"""))
            runBlocking { LocalExtractionClient.extract("abstract", port = server.port) }
            val request = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
            assertNotNull(request)
            assertEquals("Bearer $key", request!!.getHeader("Authorization"))
        } finally {
            server.shutdown()
            LocalModels.clearApiKey()
        }
    }

    @Test
    fun extractionClientOmitsTheKeyWhenNoServerIsRunning() {
        LocalModels.clearApiKey()
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[{"message":{"content":"{}"}}]}"""))
            runBlocking { LocalExtractionClient.extract("abstract", port = server.port) }
            val request = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
            assertNotNull(request)
            assertNull(request!!.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }
}

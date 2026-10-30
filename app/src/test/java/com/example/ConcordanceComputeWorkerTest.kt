package com.example

import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.concordance.integration.queue.ConcordanceComputeWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConcordanceComputeWorkerTest {

    @Test
    fun createNotificationChannels_does_not_crash() {
        val ctx = testApp()
        ConcordanceComputeWorker.createNotificationChannels(ctx)
        // No exception = pass. Channels are created best-effort.
    }

    @Test
    fun constants_are_stable() {
        assertEquals("key_task_uuid", ConcordanceComputeWorker.KEY_TASK_UUID)
        assertEquals("key_job_id", ConcordanceComputeWorker.KEY_JOB_ID)
        assertEquals(1001, ConcordanceComputeWorker.NOTIFICATION_ID_PROGRESS)
        assertEquals(1002, ConcordanceComputeWorker.NOTIFICATION_ID_COMPLETION)
    }

}

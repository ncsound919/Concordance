package com.example

import com.example.concordance.integration.queue.JobDataClass
import com.example.concordance.integration.queue.JobExecutionStatus
import com.example.concordance.integration.queue.JobQueueManager
import com.example.concordance.integration.queue.JobSpec
import com.example.concordance.integration.queue.WorkerRoutingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The heavy-compute queue.
 *
 * Two contracts matter:
 *
 *  - the job id is a *content hash*, so the same inputs always produce the
 *    same job id and a different input produces a different one. That is what
 *    makes a submitted job idempotent and auditable;
 *  - a task that stops making progress is recovered automatically rather than
 *    left hanging forever.
 *
 * The queue builds its seeded benchmark jobs with `org.json`, so it needs the
 * Robolectric runtime rather than the plain JVM stubs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JobQueueTest {

    // -----------------------------------------------------------------------
    // The content hash
    // -----------------------------------------------------------------------

    @Test
    fun `the same inputs produce the same job id`() {
        val a = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-1",
            mapOf("b" to "2", "a" to "1")
        )
        val b = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-1",
            mapOf("a" to "1", "b" to "2")
        )
        assertEquals(a.jobId, b.jobId)
    }

    @Test
    fun `the job id is a prefixed sixteen character hex digest`() {
        val spec = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-1",
            emptyMap()
        )
        assertTrue(spec.jobId.startsWith("cjob_"))
        assertEquals(16, spec.jobId.removePrefix("cjob_").length)
        assertTrue(spec.jobId.removePrefix("cjob_").all { it in "0123456789abcdef" })
    }

    @Test
    fun `different inputs produce a different job id`() {
        val base = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs-a",
            "rev-1",
            emptyMap()
        )
        val different = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs-b",
            "rev-1",
            emptyMap()
        )
        assertNotEquals(base.jobId, different.jobId)
    }

    @Test
    fun `a different data class produces a different job id`() {
        val a = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-1",
            emptyMap()
        )
        val b = JobSpec.create(
            JobDataClass.LITERATURE_SYNTHESIS_BATCH,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-1",
            emptyMap()
        )
        assertNotEquals(a.jobId, b.jobId)
    }

    @Test
    fun `a different routing policy produces a different job id`() {
        val a = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-1",
            emptyMap()
        )
        val b = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_FARGATE_GPU,
            "inputs",
            "rev-1",
            emptyMap()
        )
        assertNotEquals(a.jobId, b.jobId)
    }

    @Test
    fun `a different model revision produces a different job id`() {
        val a = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-1",
            emptyMap()
        )
        val b = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-2",
            emptyMap()
        )
        assertNotEquals(a.jobId, b.jobId)
    }

    @Test
    fun `different parameters produce a different job id`() {
        val a = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-1",
            mapOf("iterations" to "10000")
        )
        val b = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-1",
            mapOf("iterations" to "20000")
        )
        assertNotEquals(a.jobId, b.jobId)
    }

    @Test
    fun `surrounding whitespace in the inputs does not change the job id`() {
        val a = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-1",
            emptyMap()
        )
        val b = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "  inputs  ",
            "rev-1",
            emptyMap()
        )
        assertEquals(a.jobId, b.jobId)
    }

    @Test
    fun `each submission gets its own task uuid`() {
        val a = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-1",
            emptyMap()
        )
        val b = JobSpec.create(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-1",
            emptyMap()
        )
        assertNotEquals(a.taskUuid, b.taskUuid)
        assertTrue(a.taskUuid.startsWith("task_"))
    }

    // -----------------------------------------------------------------------
    // The queue
    // -----------------------------------------------------------------------

    @Test
    fun `a submitted job starts pending with no worker and zero progress`() {
        val response = JobQueueManager.submitHeavyProcess(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-1",
            mapOf("iterations" to "10000")
        )
        assertEquals(JobExecutionStatus.PENDING, response.status)
        assertEquals(0, response.progressPercent)
        assertNull(response.workerNode)
        assertNull(response.startedAtTimestamp)
        assertNull(response.completedAtTimestamp)
    }

    @Test
    fun `a submitted job reports the wait implied by its routing policy`() {
        val response = JobQueueManager.submitHeavyProcess(
            JobDataClass.IMAGING_VLM_FIGURE_EXTRACTION,
            WorkerRoutingPolicy.AWS_FARGATE_GPU,
            "inputs",
            "rev-1",
            emptyMap()
        )
        assertEquals(45, response.estimatedWaitSeconds)
    }

    @Test
    fun `a submitted job is retrievable by its task uuid`() {
        val response = JobQueueManager.submitHeavyProcess(
            JobDataClass.CROSS_MODALITY_CTDNA_OFFLOAD,
            WorkerRoutingPolicy.KAGGLE_CLUSTER_GPU,
            "inputs",
            "rev-1",
            emptyMap()
        )
        val found = JobQueueManager.getJobStatus(response.taskUuid)
        assertNotNull(found)
        assertEquals(response.jobId, found!!.jobId)
    }

    @Test
    fun `an unknown task uuid is not found`() {
        assertNull(JobQueueManager.getJobStatus("task_does_not_exist"))
    }

    @Test
    fun `a submitted job appears in the job list`() {
        val before = JobQueueManager.allJobs.value.size
        val response = JobQueueManager.submitHeavyProcess(
            JobDataClass.LITERATURE_SYNTHESIS_BATCH,
            WorkerRoutingPolicy.AWS_FARGATE_GPU,
            "inputs",
            "rev-1",
            emptyMap()
        )
        assertTrue(JobQueueManager.allJobs.value.size > before)
        assertTrue(JobQueueManager.allJobs.value.any { it.taskUuid == response.taskUuid })
    }

    @Test
    fun `a submitted job records the content hash in its log`() {
        val response = JobQueueManager.submitHeavyProcess(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "inputs",
            "rev-1",
            emptyMap()
        )
        assertTrue(response.logs.any { it.contains(response.jobId) })
    }

    @Test
    fun `the worker node list is populated and healthy`() {
        val nodes = JobQueueManager.workerNodes.value
        assertTrue(nodes.isNotEmpty())
        assertTrue(nodes.all { it.isHealthy })
        assertTrue(nodes.any { it.routingPolicy == WorkerRoutingPolicy.AWS_FARGATE_GPU })
        assertTrue(nodes.any { it.routingPolicy == WorkerRoutingPolicy.LOCAL_WORKER })
    }

    // -----------------------------------------------------------------------
    // Stuck-task recovery
    // -----------------------------------------------------------------------

    @Test
    fun `a simulated stuck task is processing with progress part way through`() {
        val stuck = JobQueueManager.simulateStuckTaskForTesting()
        assertEquals(JobExecutionStatus.PROCESSING, stuck.status)
        assertEquals(42, stuck.progressPercent)
        assertNotNull(stuck.startedAtTimestamp)
    }

    @Test
    fun `a task stuck for longer than the threshold is recovered`() {
        val stuck = JobQueueManager.simulateStuckTaskForTesting()
        val recovered = JobQueueManager.checkAndRecoverStuckTasks(300_000)
        assertTrue("expected at least one recovery, got $recovered", recovered >= 1)
        val after = JobQueueManager.getJobStatus(stuck.taskUuid)
        assertNotNull(after)
        assertEquals(JobExecutionStatus.STUCK_RECOVERED, after!!.status)
        assertTrue(after.stuckRecoveryTriggered)
        assertEquals(1, after.retryCount)
        assertNull(after.workerNode)
    }

    @Test
    fun `a recovery is written to the recovery log`() {
        JobQueueManager.simulateStuckTaskForTesting()
        val before = JobQueueManager.stuckRecoveryLog.value.size
        JobQueueManager.checkAndRecoverStuckTasks(300_000)
        val after = JobQueueManager.stuckRecoveryLog.value
        assertTrue(after.size > before)
        assertTrue(after.first().contains("Automatic Stuck-Task Recovery"))
    }

    @Test
    fun `a task that is not stuck is not recovered`() {
        val fresh = JobQueueManager.submitHeavyProcess(
            JobDataClass.SENSITIVITY_PERMUTATION,
            WorkerRoutingPolicy.AWS_BATCH_CPU,
            "fresh-inputs",
            "rev-1",
            emptyMap()
        )
        JobQueueManager.checkAndRecoverStuckTasks(300_000)
        val after = JobQueueManager.getJobStatus(fresh.taskUuid)
        assertNotNull(after)
        assertEquals(JobExecutionStatus.PENDING, after!!.status)
        assertEquals(0, after.retryCount)
    }

    @Test
    fun `a task that has not been running for long enough is not recovered`() {
        val stuck = JobQueueManager.simulateStuckTaskForTesting()
        // The simulated task started six minutes ago, so a ten-minute
        // threshold must not trip it.
        val recovered = JobQueueManager.checkAndRecoverStuckTasks(600_000)
        assertTrue(recovered >= 0)
        val after = JobQueueManager.getJobStatus(stuck.taskUuid)
        assertEquals(JobExecutionStatus.PROCESSING, after!!.status)
    }
}

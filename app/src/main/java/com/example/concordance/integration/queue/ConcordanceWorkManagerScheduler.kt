package com.example.concordance.integration.queue

import android.content.Context
import androidx.work.*
import java.util.UUID
import java.util.concurrent.TimeUnit

object ConcordanceWorkManagerScheduler {

    /**
     * Schedules a compute task on the Android OS WorkManager scheduler.
     * Enforces constraints: Network Connected, Battery Not Low.
     * Survives reboots and memory reclaim.
     */
    fun scheduleComputeTask(
        context: Context,
        jobResponse: JobStatusResponse,
        inputs: String = ""
    ): UUID {
        // Build constraints as prescribed by OS scheduler best practices
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putString(ConcordanceComputeWorker.KEY_TASK_UUID, jobResponse.taskUuid)
            .putString(ConcordanceComputeWorker.KEY_JOB_ID, jobResponse.jobId)
            .putString(ConcordanceComputeWorker.KEY_DATA_CLASS, jobResponse.dataClass.label)
            .putString(ConcordanceComputeWorker.KEY_ROUTING, jobResponse.routingPolicy.title)
            .putString(ConcordanceComputeWorker.KEY_INPUTS, inputs)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ConcordanceComputeWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("concordance_compute_${jobResponse.jobId}")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }
}

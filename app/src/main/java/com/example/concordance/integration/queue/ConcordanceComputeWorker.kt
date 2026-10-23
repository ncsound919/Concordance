package com.example.concordance.integration.queue

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.delay

class ConcordanceComputeWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ONGOING_PROGRESS = "concordance_worker_progress"
        const val CHANNEL_COMPLETION = "concordance_worker_completion"
        const val KEY_TASK_UUID = "key_task_uuid"
        const val KEY_JOB_ID = "key_job_id"
        const val KEY_DATA_CLASS = "key_data_class"
        const val KEY_ROUTING = "key_routing"
        const val KEY_INPUTS = "key_inputs"
        const val NOTIFICATION_ID_PROGRESS = 1001
        const val NOTIFICATION_ID_COMPLETION = 1002

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val ongoingChannel = NotificationChannel(
                    CHANNEL_ONGOING_PROGRESS,
                    "Compute Worker Ongoing Progress",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Displays live progress and ETA for asynchronous compute tasks."
                    setShowBadge(false)
                }

                val completionChannel = NotificationChannel(
                    CHANNEL_COMPLETION,
                    "Compute Task Completion",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifies when remote worker finishes computation and verified capsule is ready."
                    enableVibration(true)
                }

                notificationManager.createNotificationChannel(ongoingChannel)
                notificationManager.createNotificationChannel(completionChannel)
            }
        }
    }

    override suspend fun doWork(): Result {
        val taskUuid = inputData.getString(KEY_TASK_UUID) ?: return Result.failure()
        val jobId = inputData.getString(KEY_JOB_ID) ?: "cjob_unknown"
        val dataClassName = inputData.getString(KEY_DATA_CLASS) ?: "Compute Task"
        val routing = inputData.getString(KEY_ROUTING) ?: "Remote Cluster"

        createNotificationChannels(appContext)
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Setup notification pending intent to bring user back to app
        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("opened_from_notification_task_uuid", taskUuid)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var isComplete = false
        var attempts = 0
        val maxAttempts = 30

        try {
            while (!isComplete && attempts < maxAttempts) {
                val status = JobQueueManager.getJobStatus(taskUuid)
                val progress = status?.progressPercent ?: 10
                val eta = status?.estimatedWaitSeconds ?: 20
                val statusLabel = status?.status?.label ?: "Queued"

                // Update Ongoing Notification with progress bar and ETA
                val progressNotification = NotificationCompat.Builder(appContext, CHANNEL_ONGOING_PROGRESS)
                    .setContentTitle("Concordance: $dataClassName")
                    .setContentText("Worker $routing: $statusLabel ($progress%) · ETA ~${eta}s")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setProgress(100, progress, false)
                    .setOngoing(true)
                    .setContentIntent(pendingIntent)
                    .setOnlyAlertOnce(true)
                    .build()

                notificationManager.notify(NOTIFICATION_ID_PROGRESS, progressNotification)

                if (status?.status == JobExecutionStatus.COMPLETED) {
                    isComplete = true
                    // Remove ongoing notification
                    notificationManager.cancel(NOTIFICATION_ID_PROGRESS)

                    // Post Completion Notification (the trigger, not the polling loop!)
                    val completionNotification = NotificationCompat.Builder(appContext, CHANNEL_COMPLETION)
                        .setContentTitle("Compute Finished: Verified Capsule Ready")
                        .setContentText(status.resultSummary ?: "Task $jobId completed. Tap to view citable results.")
                        .setStyle(
                            NotificationCompat.BigTextStyle()
                                .bigText("${status.resultSummary}\n\nImmutable Hash: $jobId\nCapsule SHA-256: ${status.citableCapsuleSha256}")
                        )
                        .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .build()

                    notificationManager.notify(NOTIFICATION_ID_COMPLETION, completionNotification)

                    val output = workDataOf(
                        "job_id" to jobId,
                        "result_summary" to (status.resultSummary ?: ""),
                        "capsule_sha" to (status.citableCapsuleSha256 ?: "")
                    )
                    return Result.success(output)
                } else if (status?.status == JobExecutionStatus.FAILED) {
                    notificationManager.cancel(NOTIFICATION_ID_PROGRESS)
                    return Result.failure()
                }

                delay(1500)
                attempts++
            }

            notificationManager.cancel(NOTIFICATION_ID_PROGRESS)
            return Result.success()
        } catch (e: Exception) {
            notificationManager.cancel(NOTIFICATION_ID_PROGRESS)
            return Result.retry()
        }
    }
}

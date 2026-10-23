package com.example.concordance.autonomy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.example.MainActivity

class SlowResearchWorker(
    private val appContext: Context,
    workerParams: androidx.work.WorkerParameters
) : androidx.work.CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "autonomy_slow"
        const val NOTIFICATION_ID = 2001

        /** PDFs are expensive; a step indexes a batch so the queue advances over time. */
        private const val BATCH_FILES = 20

        /**
         * Builds the foreground notification for a slow-mode step.
         *
         * The foreground service type is **not** optional here. WorkManager hands the
         * type straight to `Service.startForeground(id, notification, type)`, and the
         * platform throws `InvalidForegroundServiceTypeException: Starting FGS with type
         * none ... has been prohibited` when the type is `FOREGROUND_SERVICE_TYPE_NONE`
         * on Android 14+ (targetSdk >= 34). The type must also match the
         * `android:foregroundServiceType` declared for
         * `androidx.work.impl.foreground.SystemForegroundService` in the manifest.
         */
        fun createForegroundInfo(context: Context, text: String): ForegroundInfo {
            createChannel(context)
            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Concordance")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setContentIntent(pi)
                .setOnlyAlertOnce(true)
                .build()
            return ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "Autonomy slow mode", NotificationManager.IMPORTANCE_LOW).apply {
                            description = "Background research steps"
                        }
                    )
                }
            }
        }
    }

    override suspend fun doWork(): Result {
        AutonomyModeManager.init(appContext)
        AutonomyActivity.init(appContext)
        val state = AutonomyModeManager.state.value

        // Skip if paused or battery low. Every early exit is logged: a worker that
        // silently does nothing is indistinguishable from one that never ran.
        if (state.mode == AutonomyMode.PAUSED) {
            AutonomyActivity.record(
                AutonomyActivity.SOURCE_SLOW,
                "Step skipped: automation is paused",
                ActivityLevel.SKIPPED
            )
            return Result.success()
        }
        if (!state.batteryOk) {
            AutonomyActivity.record(
                AutonomyActivity.SOURCE_SLOW,
                "Step skipped: battery is low",
                ActivityLevel.SKIPPED
            )
            return Result.success()
        }

        // If offline, do local work only
        if (!state.online) {
            val entry = AutonomyActivity.start(
                AutonomyActivity.SOURCE_SLOW,
                "Offline step: indexing the local corpus"
            )
            return try {
                val corpus = LocalCorpus.index(appContext, maxNewFiles = BATCH_FILES)
                AutonomyModeManager.tickStep()
                AutonomyActivity.finish(
                    entry,
                    corpusLevel(corpus),
                    corpusMessage("Offline step", corpus)
                )
                Result.success()
            } catch (e: Exception) {
                AutonomyActivity.finish(
                    entry,
                    ActivityLevel.FAILED,
                    "Offline step failed: ${e.javaClass.simpleName}"
                )
                Result.retry()
            }
        }

        // There is no report endpoint to poll, so a slow step does the work that is actually
        // local and verifiable — index a batch of the user's papers — and says exactly that.
        val entry = AutonomyActivity.start(
            AutonomyActivity.SOURCE_SLOW,
            "Slow step: indexing the local corpus"
        )
        try {
            // The notification is a courtesy, not the work. On Android 12+ the platform can
            // refuse to start a foreground service from the background
            // (ForegroundServiceStartNotAllowedException); one indexing batch fits inside a
            // single worker run, so a refused notification must not fail the step.
            runCatching {
                setForeground(createForegroundInfo(appContext, "Slow mode: indexing local papers"))
            }.onFailure {
                Log.w("SlowResearchWorker", "foreground notification refused", it)
            }
            val corpus = LocalCorpus.index(appContext, maxNewFiles = BATCH_FILES)
            AutonomyModeManager.tickStep()
            AutonomyActivity.finish(
                entry,
                corpusLevel(corpus),
                corpusMessage("Slow step", corpus)
            )
        } catch (e: Exception) {
            AutonomyActivity.finish(
                entry,
                ActivityLevel.FAILED,
                "Slow step failed: ${e.javaClass.simpleName}"
            )
            return Result.retry()
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo(appContext, "Slow mode running")

    /** A step only counts as done when there was something to read. */
    private fun corpusLevel(status: CorpusStatus): ActivityLevel = when {
        !status.hasFolder -> ActivityLevel.SKIPPED
        status.files > 0 -> ActivityLevel.OK
        else -> ActivityLevel.SKIPPED
    }

    /**
     * Says what really happened. "0 files indexed" alone reads as success; the log has to
     * distinguish an empty folder, an unreadable one and one that has not been chosen yet.
     */
    private fun corpusMessage(prefix: String, status: CorpusStatus): String = when {
        !status.hasFolder ->
            "$prefix: no corpus folder chosen — pick one on the Agent tab"
        status.files > 0 ->
            "$prefix: ${status.files} files, ${status.chunks} chunks indexed" +
                (status.note?.let { " · $it" } ?: "")
        else ->
            "$prefix: ${status.note ?: "nothing indexable in the chosen folder"}"
    }

}

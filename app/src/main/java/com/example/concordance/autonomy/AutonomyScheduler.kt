package com.example.concordance.autonomy

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object AutonomyScheduler {
    private const val SLOW_WORK_NAME = "autonomy_slow"

    fun scheduleSlow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<SlowResearchWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SLOW_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelSlow(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SLOW_WORK_NAME)
    }

    fun runOnce(context: Context) {
        val request = OneTimeWorkRequestBuilder<SlowResearchWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}

package com.example.concordance.integration.supabase

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.concordance.data.ConcordanceDatabase

class SupabaseSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = ConcordanceDatabase.getDatabase(applicationContext)
            val store = createSupabaseSessionStore(applicationContext)
            val auth = SupabaseAuth(store)
            if (auth.session == null) return Result.success() // guest mode
            val rest = PostgRestClient(auth)
            val mgr = SupabaseSyncManager(auth, rest, db)
            mgr.onConflict = { c ->
                // Routing handled by caller; here we just log via telemetry-free path.
            }
            val pulled = mgr.pullAll()
            if (pulled.isFailure) return Result.retry()
            val pushed = mgr.pushAll()
            if (pushed.isFailure) return Result.retry()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "concordance_supabase_sync"

        fun schedule(context: Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            val req = androidx.work.PeriodicWorkRequestBuilder<SupabaseSyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 5, java.util.concurrent.TimeUnit.MINUTES)
                .build()
            androidx.work.WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, androidx.work.ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(context: Context) {
            val req = androidx.work.OneTimeWorkRequestBuilder<SupabaseSyncWorker>().build()
            androidx.work.WorkManager.getInstance(context).enqueue(req)
        }
    }
}
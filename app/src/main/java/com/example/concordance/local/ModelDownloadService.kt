package com.example.concordance.local

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.concordance.autonomy.ForegroundServiceTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** What the BYOK screen needs to draw the download row. */
sealed interface ModelDownloadStatus {
    data object Idle : ModelDownloadStatus

    data class Downloading(
        val fileName: String,
        val displayName: String,
        val bytes: Long,
        val total: Long
    ) : ModelDownloadStatus {
        val fraction: Float
            get() = if (total <= 0) 0f else (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f)
    }

    data class Installed(val fileName: String, val displayName: String) : ModelDownloadStatus
    data class Failed(val fileName: String, val displayName: String, val message: String) :
        ModelDownloadStatus
}

/**
 * Downloads one GGUF in a dataSync foreground service.
 *
 * The BYOK screen can be rotated or backgrounded without killing a multi-GB
 * transfer, and the status is observable from the view model. Only one download
 * runs at a time: a second Start is ignored while the first is active.
 */
class ModelDownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 7310
        private const val ACTION_START = "com.example.concordance.action.MODEL_DOWNLOAD_START"
        private const val ACTION_CANCEL = "com.example.concordance.action.MODEL_DOWNLOAD_CANCEL"
        private const val EXTRA_MODEL_ID = "model_id"

        private val _status = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.Idle)
        val status: StateFlow<ModelDownloadStatus> = _status.asStateFlow()

        fun start(context: Context, model: InstallableModel) {
            val intent = Intent(context, ModelDownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODEL_ID, model.id)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, ModelDownloadService::class.java).setAction(ACTION_CANCEL)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var currentModel: InstallableModel? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "concordance:model-download"
        ).apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                downloadJob?.cancel()
                downloadJob = null
                currentModel?.let { model ->
                    ModelDownloader.partFile(LocalModels.modelsDir(this), model).delete()
                }
                currentModel = null
                _status.value = ModelDownloadStatus.Idle
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                if (downloadJob?.isActive == true) return START_NOT_STICKY
                val model = LocalModelCatalog.forId(intent.getStringExtra(EXTRA_MODEL_ID).orEmpty())
                if (model == null) {
                    _status.value = ModelDownloadStatus.Failed("", "Model", "Unknown model")
                    stopSelf()
                    return START_NOT_STICKY
                }
                currentModel = model
                _status.value = ModelDownloadStatus.Downloading(
                    model.fileName, model.displayName, 0L, model.sizeBytes
                )
                ForegroundServiceTypes.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification(model, 0L, model.sizeBytes, done = false),
                    ForegroundServiceTypes.DATA_SYNC
                )
                startDownload(model)
            }

            else -> if (downloadJob == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startDownload(model: InstallableModel) {
        wakeLock?.acquire(2 * 60 * 60 * 1000L)
        downloadJob = scope.launch {
            val directory = LocalModels.modelsDir(this@ModelDownloadService)
            val result = ModelDownloader().download(model, directory) { bytes, total ->
                _status.value = ModelDownloadStatus.Downloading(
                    model.fileName, model.displayName, bytes, total
                )
                updateNotification(model, bytes, total)
            }
            when (result) {
                is ModelDownloadResult.Success -> {
                    _status.value = ModelDownloadStatus.Installed(model.fileName, model.displayName)
                    notifyDone(model, "Installed and verified")
                }

                is ModelDownloadResult.Failure -> {
                    _status.value = ModelDownloadStatus.Failed(
                        model.fileName, model.displayName, result.message
                    )
                    notifyDone(model, "Download failed: ${result.message}")
                }
            }
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    private fun updateNotification(model: InstallableModel, bytes: Long, total: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(model, bytes, total, done = false))
    }

    private fun notifyDone(model: InstallableModel, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID + 1,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(model.displayName)
                .setContentText(text)
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .build()
        )
    }

    private fun notification(
        model: InstallableModel,
        bytes: Long,
        total: Long,
        done: Boolean
    ): Notification {
        val percent = if (total <= 0) 0 else ((bytes * 100) / total).toInt().coerceIn(0, 100)
        val progress = if (total <= 0) 0 else ((bytes * 1000) / total).toInt().coerceIn(0, 1000)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(model.displayName)
            .setContentText(
                if (done) "Installed" else "$percent% — ${describe(bytes)} of ${describe(total)}"
            )
            .setContentIntent(openAppIntent())
            .setOngoing(!done)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(1000, progress, total <= 0)
            .build()
    }

    private fun openAppIntent(): PendingIntent? {
        val open = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun describe(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val gb = bytes / 1_073_741_824.0
        return if (gb >= 1.0) {
            String.format(Locale.US, "%.2f GB", gb)
        } else {
            String.format(Locale.US, "%.0f MB", bytes / 1_048_576.0)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Downloads on-device GGUF models" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

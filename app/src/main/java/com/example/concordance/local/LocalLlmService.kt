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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Lifecycle of the embedded llama.cpp server. */
sealed interface LocalLlmStatus {
    data object Stopped : LocalLlmStatus
    data class Starting(val model: String) : LocalLlmStatus
    data class Ready(val model: String, val port: Int) : LocalLlmStatus
    data class Failed(val message: String) : LocalLlmStatus
}

/**
 * Runs `llama-server` as a foreground service.
 *
 * The executable is shipped as `libllamaserver.so` in `jniLibs`; Android extracts native
 * libraries into `nativeLibraryDir` with execute permission, which is the only supported way
 * for an app to run a bundled binary on Android 10+. Its shared libraries sit in the same
 * directory and are resolved through `LD_LIBRARY_PATH`.
 */
class LocalLlmService : Service() {

    companion object {
        private const val TAG = "LocalLlmService"
        private const val CHANNEL_ID = "local_llm"
        private const val NOTIFICATION_ID = 7301

        const val ACTION_START = "com.example.concordance.action.LOCAL_LLM_START"
        const val ACTION_STOP = "com.example.concordance.action.LOCAL_LLM_STOP"
        const val EXTRA_MODEL = "model_path"

        private val _status = MutableStateFlow<LocalLlmStatus>(LocalLlmStatus.Stopped)
        val status: StateFlow<LocalLlmStatus> = _status.asStateFlow()

        fun start(context: Context, model: File) {
            val intent = Intent(context, LocalLlmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODEL, model.absolutePath)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LocalLlmService::class.java).setAction(ACTION_STOP)
            )
        }

        /** Base URL the rest of the app should use for local inference. */
        fun baseUrl(): String = "http://127.0.0.1:${LocalModels.LLM_PORT}"
    }

    private var process: Process? = null
    private var reader: Thread? = null
    private var health: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val recentLog = ArrayDeque<String>()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "concordance:local-llm").apply {
            setReferenceCounted(false)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val path = intent.getStringExtra(EXTRA_MODEL)
                if (path.isNullOrBlank()) {
                    _status.value = LocalLlmStatus.Failed("No model selected")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val model = File(path)
                if (!model.isFile) {
                    _status.value = LocalLlmStatus.Failed("Model not found: ${model.name}")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val current = _status.value
                if (current is LocalLlmStatus.Ready && processAlive(process) && current.model == model.name) {
                    return START_STICKY
                }
                startForeground(NOTIFICATION_ID, notification("Starting ${model.name}"))
                launchServer(model)
            }
            else -> {
                // Restarted by the system without an intent: nothing sensible to run.
                if (process == null) stopSelf()
            }
        }
        return START_STICKY
    }

    private fun launchServer(model: File) {
        stopServer(keepStatus = true)
        _status.value = LocalLlmStatus.Starting(model.name)
        wakeLock?.acquire(60 * 60 * 1000L)

        val binary = LocalModels.runtimeBinary(this)
        if (!binary.isFile) {
            fail("Runtime libllamaserver.so missing from nativeLibraryDir")
            return
        }

        val command = listOf(
            binary.absolutePath,
            "-m", model.absolutePath,
            // The packaged runtime is the CPU-only llama.cpp build: no OpenCL/HTP backend,
            // so there is nothing device-specific to select and no vendor library to link.
            "-ngl", "0",
            "-t", "6",
            "-c", "4096",
            "-b", "512",
            "-ub", "512",
            "--host", "127.0.0.1",
            "--port", LocalModels.LLM_PORT.toString(),
            "--jinja"
        )

        val builder = ProcessBuilder(command)
            .directory(filesDir)
            .redirectErrorStream(true)
        builder.environment()["LD_LIBRARY_PATH"] = applicationInfo.nativeLibraryDir

        val started = try {
            builder.start()
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to launch llama-server", t)
            fail("Unable to launch runtime: ${t.message}")
            return
        }
        process = started

        reader = Thread {
            try {
                started.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(recentLog) {
                            if (recentLog.size >= 60) recentLog.removeFirst()
                            recentLog.addLast(line)
                        }
                        if (line.contains("listening on", ignoreCase = true)) {
                            markReady(model)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Log reader ended", t)
            }
            if (process === started && !processAlive(started)) {
                val tail = synchronized(recentLog) { recentLog.takeLast(6).joinToString(" | ") }
                if (_status.value !is LocalLlmStatus.Ready) {
                    fail(tail.ifBlank { "Runtime exited" })
                } else if (_status.value is LocalLlmStatus.Ready) {
                    _status.value = LocalLlmStatus.Failed("Runtime stopped unexpectedly")
                }
            }
        }.apply { isDaemon = true; name = "llama-log"; start() }

        health = Thread {
            repeat(120) {
                if (process !== started || !processAlive(started)) return@Thread
                if (_status.value is LocalLlmStatus.Ready) return@Thread
                if (healthy()) {
                    markReady(model)
                    return@Thread
                }
                try {
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
            if (_status.value is LocalLlmStatus.Starting) {
                val tail = synchronized(recentLog) { recentLog.takeLast(6).joinToString(" | ") }
                fail("Timed out waiting for the model. $tail")
            }
        }.apply { isDaemon = true; name = "llama-health"; start() }
    }

    /**
     * Liveness without `Process.isAlive` (API 26; minSdk is 24): a live process
     * throws from `exitValue()`, an exited one returns immediately.
     */
    private fun processAlive(p: Process?): Boolean {
        if (p == null) return false
        return try {
            p.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }
    }

    private fun healthy(): Boolean = try {
        val connection = URL("http://127.0.0.1:${LocalModels.LLM_PORT}/health").openConnection() as HttpURLConnection
        connection.connectTimeout = 800
        connection.readTimeout = 800
        connection.requestMethod = "GET"
        val code = connection.responseCode
        connection.disconnect()
        code == 200
    } catch (_: Throwable) {
        false
    }

    private fun markReady(model: File) {
        if (_status.value is LocalLlmStatus.Ready) return
        _status.value = LocalLlmStatus.Ready(model.name, LocalModels.LLM_PORT)
        notify("On-device model ready", "${model.name} is listening on 127.0.0.1:${LocalModels.LLM_PORT}")
    }

    private fun fail(message: String) {
        Log.e(TAG, message)
        _status.value = LocalLlmStatus.Failed(message)
        notify("On-device model stopped", message.take(160))
        wakeLock?.takeIf { it.isHeld }?.release()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun stopServer(keepStatus: Boolean = false) {
        health?.interrupt()
        health = null
        process?.let { p ->
            try {
                p.destroy()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly()
                }
            } catch (_: Throwable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) p.destroyForcibly()
            }
        }
        process = null
        reader = null
        wakeLock?.takeIf { it.isHeld }?.release()
        if (!keepStatus) _status.value = LocalLlmStatus.Stopped
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "On-device model",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps the local llama.cpp server running" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)
            ?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Concordance local model")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    private fun notify(title: String, text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID + 1, notification("$title — $text"))
    }
}

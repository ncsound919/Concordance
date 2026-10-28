package com.example.concordance.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.concordance.autonomy.ForegroundServiceTypes
import com.example.concordance.local.LocalModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Loopback HTTP service for the operator chat's voice.
 *
 *   GET  /health  -> {ok, engine, ready, voices, sampleRate, reason}
 *   POST /tts     -> audio/wav  {text, voice?}
 *
 * Binds 127.0.0.1 only. The engine is the same on-device Kokoro path the app
 * already uses; no second model, no cloud. If Kokoro is not installed the
 * service still answers /health with ready:false and the exact reason, and /tts
 * returns 503 — it never returns silence as success.
 */
class KokoroTtsServer : Service() {

    companion object {
        private const val TAG = "KokoroTtsServer"
        const val ACTION_START = "com.example.concordance.action.KOKORO_TTS_START"
        const val ACTION_STOP = "com.example.concordance.action.KOKORO_TTS_STOP"
        const val PORT = 8770
        private const val CHANNEL_ID = "kokoro_tts"
        private const val NOTIFICATION_ID = 7302
        private const val MAX_BODY_BYTES = 200_000

        /** Start only when the model files are present; the caller is foreground. */
        fun startIfReady(context: Context) {
            if (!LocalModels.kokoroReady(context)) return
            val intent = Intent(context, KokoroTtsServer::class.java).setAction(ACTION_START)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w(TAG, "could not start Kokoro service", it) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, KokoroTtsServer::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var synthesizer: SherpaKokoroSynthesizer? = null

    @Volatile
    private var ready = false

    @Volatile
    private var unavailableReason: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val synth = SherpaKokoroSynthesizer.fromDirectory(this)
        synthesizer = synth
        ready = synth.isAvailable()
        unavailableReason = if (ready) null else SpeechOutput.kokoroUnavailableReason(this)
        if (!ready) Log.w(TAG, "Kokoro not ready: ${unavailableReason ?: "unknown"}")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServer()
            stopSelf()
            return START_NOT_STICKY
        }

        ForegroundServiceTypes.startForeground(
            this,
            NOTIFICATION_ID,
            notification(if (ready) "Voice ready on 127.0.0.1:$PORT" else "Voice unavailable"),
            ForegroundServiceTypes.SPECIAL_USE,
        )
        startServer()
        return START_STICKY
    }

    override fun onDestroy() {
        stopServer()
        scope.cancel()
        super.onDestroy()
    }

    private fun startServer() {
        if (serverSocket != null) return
        try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT))
            serverSocket = socket
            acceptThread = Thread({ acceptLoop(socket) }, "kokoro-tts").apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "listening on 127.0.0.1:$PORT ready=$ready")
        } catch (t: Throwable) {
            Log.e(TAG, "could not bind 127.0.0.1:$PORT", t)
            unavailableReason = "port $PORT unavailable: ${t.message}"
            stopSelf()
        }
    }

    private fun stopServer() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread = null
        synthesizer?.shutdown()
        synthesizer = null
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            try {
                val client = socket.accept()
                scope.launch { handle(client) }
            } catch (t: Throwable) {
                if (!socket.isClosed) Log.w(TAG, "accept failed", t)
            }
        }
    }

    private suspend fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 15_000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return respondJson(output, 400, mapOf("ok" to false, "error" to "bad request"))
            val method = parts[0]
            val path = parts[1].substringBefore('?')

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }

            when {
                method == "GET" && path == "/health" -> {
                    val synth = synthesizer
                    respondJson(
                        output,
                        200,
                        mapOf(
                            "ok" to true,
                            "engine" to "kokoro",
                            "ready" to ready,
                            "voices" to listOf(LocalModels.DEFAULT_VOICE_NAME),
                            "sampleRate" to (synth?.currentSampleRate() ?: 24_000),
                            "reason" to unavailableReason,
                        ),
                    )
                }

                method == "POST" && path == "/tts" -> {
                    val length = headers["content-length"]?.toIntOrNull() ?: 0
                    if (length <= 0 || length > MAX_BODY_BYTES) {
                        return respondJson(output, 413, mapOf("ok" to false, "error" to "body missing or too large"))
                    }
                    val body = ByteArray(length)
                    if (!readFully(input, body)) {
                        return respondJson(output, 400, mapOf("ok" to false, "error" to "short body"))
                    }
                    val json = runCatching { JSONObject(String(body, Charsets.UTF_8)) }.getOrNull()
                    val text = json?.optString("text")?.trim().orEmpty()
                    if (text.isEmpty()) {
                        return respondJson(output, 400, mapOf("ok" to false, "error" to "text is required"))
                    }
                    if (!ready) {
                        return respondJson(
                            output,
                            503,
                            mapOf("ok" to false, "ready" to false, "reason" to unavailableReason),
                        )
                    }
                    val voice = json?.optString("voice")?.trim().orEmpty()
                    val speaker = if (voice.isEmpty() || voice == LocalModels.DEFAULT_VOICE_NAME) {
                        LocalModels.DEFAULT_VOICE_ID
                    } else {
                        voice.toIntOrNull() ?: LocalModels.DEFAULT_VOICE_ID
                    }
                    val wav = synthesizer?.synthesizeWav(text, speaker)
                    if (wav == null) {
                        return respondJson(
                            output,
                            503,
                            mapOf("ok" to false, "error" to "synthesis failed", "reason" to unavailableReason),
                        )
                    }
                    respondBytes(output, 200, "audio/wav", wav)
                }

                else -> respondJson(output, 404, mapOf("ok" to false, "error" to "not found"))
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var any = false
        while (true) {
            val b = input.read()
            if (b < 0) return if (any) out.toString("UTF-8") else null
            any = true
            if (b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
        }
        return out.toString("UTF-8")
    }

    private fun readFully(input: InputStream, target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun respondJson(output: BufferedOutputStream, status: Int, body: Map<String, Any?>) {
        val bytes = JSONObject(body).toString().toByteArray(Charsets.UTF_8)
        respondBytes(output, status, "application/json; charset=utf-8", bytes)
    }

    private fun respondBytes(output: BufferedOutputStream, status: Int, contentType: String, bytes: ByteArray) {
        val head = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(statusText(status)).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(head.toByteArray(Charsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        404 -> "Not Found"
        413 -> "Payload Too Large"
        503 -> "Service Unavailable"
        else -> "Error"
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Kokoro voice",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Loopback voice for the operator chat"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun notification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kokoro voice")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}

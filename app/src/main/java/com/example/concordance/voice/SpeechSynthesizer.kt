package com.example.concordance.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.concordance.local.LocalModels
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Spoken output. Two implementations ship: an on-device Kokoro model when its assets
 * are installed, and the phone's built-in engine otherwise. No managed platform is
 * involved either way.
 */
interface SpeechSynthesizer {
    /** Shown in the UI so the operator always knows which voice is speaking. */
    val engineName: String

    /** True only when this engine can actually produce audio right now. */
    fun isAvailable(): Boolean

    /** Speak [text]; returns true if it was spoken. Never throws. */
    suspend fun speak(text: String): Boolean

    fun stop()

    fun shutdown()
}

/**
 * Reported when the preferred engine cannot run. Keeping this as a real object (rather
 * than a half-built engine) means the UI can show exactly why, and nothing pretends to
 * work.
 */
class UnavailableSynthesizer(private val reason: String) : SpeechSynthesizer {
    override val engineName: String = "No voice available ($reason)"
    override fun isAvailable(): Boolean = false
    override suspend fun speak(text: String): Boolean = false
    override fun stop() = Unit
    override fun shutdown() = Unit
}

/**
 * The phone's own TextToSpeech engine.
 *
 * This is the default: it is offline, license-free and present on every Android device.
 * It is used whenever Kokoro is not installed.
 */
class SystemTtsSynthesizer(context: Context) : SpeechSynthesizer {

    override val engineName: String = "System voice (on-device)"

    private val ready = CompletableDeferred<Boolean>()
    private val completions = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    @Volatile
    private var failed = false

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) ready.complete(true) else {
            failed = true
            ready.complete(false)
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { completions.remove(it)?.complete(true) }
            }

            @Deprecated("Required by the platform interface")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { completions.remove(it)?.complete(false) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { completions.remove(it)?.complete(false) }
            }
        })
        tts.setLanguage(Locale.ENGLISH)
    }

    override fun isAvailable(): Boolean = !failed

    override suspend fun speak(text: String): Boolean {
        if (text.isBlank() || failed) return false
        val initialised = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() } ?: false
        if (!initialised) return false

        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>()
        completions[id] = done
        val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (queued != TextToSpeech.SUCCESS) {
            completions.remove(id)
            return false
        }
        return withTimeoutOrNull(speakTimeoutMs(text)) { done.await() } ?: false
    }

    /** Long text needs headroom; ~15 characters per second is a safe lower bound. */
    private fun speakTimeoutMs(text: String): Long =
        (text.length / 15L * 1000L + 5_000L).coerceAtMost(120_000L)

    override fun stop() {
        runCatching { tts.stop() }
    }

    override fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }

    companion object {
        private const val INIT_TIMEOUT_MS = 5_000L
    }
}

/**
 * Chooses a voice without ever failing silently: callers can read [SpeechSynthesizer.engineName]
 * and tell the operator which engine is in use, and [kokoroUnavailableReason] explains why
 * Kokoro is not.
 */
object SpeechOutput {

    fun forContext(context: Context, kokoroAssetsDir: File = defaultKokoroDir(context)): SpeechSynthesizer {
        // sherpa-onnx performs G2P in-process, so no executable is needed.
        val sherpa = SherpaKokoroSynthesizer.fromDirectory(context)
        return if (sherpa.isAvailable()) sherpa else SystemTtsSynthesizer(context)
    }

    fun defaultKokoroDir(context: Context): File = LocalModels.kokoroDir(context)

    /** Where the platform keeps libraries extracted from the APK — the only executable place. */
    fun nativeLibDir(context: Context): File = File(context.applicationInfo.nativeLibraryDir)

    /** Human-readable reason Kokoro cannot be used, or null when it can. */
    fun kokoroUnavailableReason(context: Context, kokoroDir: File = defaultKokoroDir(context)): String? =
        if (LocalModels.kokoroReady(context)) {
            null
        } else {
            "Kokoro files are missing from ${LocalModels.kokoroDir(context)}"
        }
}

package com.example.concordance.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.concordance.local.LocalModels
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Kokoro text-to-speech through sherpa-onnx.
 *
 * Unlike [KokoroTtsSynthesizer], this path does not need an `espeak-ng` child process:
 * sherpa-onnx performs grapheme-to-phoneme conversion in-process using the bundled
 * `espeak-ng-data`. That is what makes it work on Android 10+, where executing binaries from
 * app storage is blocked.
 *
 * Expected files (see [LocalModels]):
 *   kokoro/model.onnx, kokoro/voices.bin, kokoro/tokens.txt, kokoro/espeak-ng-data
 */
class SherpaKokoroSynthesizer private constructor(
    private val context: Context,
    private val directory: File,
    private val speakerId: Int,
    private val voiceName: String
) : SpeechSynthesizer {

    private val mutex = Mutex()
    private var engine: OfflineTts? = null

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var cancelled = false

    override val engineName: String = "Kokoro $voiceName (on-device)"

    override fun isAvailable(): Boolean = LocalModels.kokoroReady(context)

    override suspend fun speak(text: String): Boolean = withContext(Dispatchers.Default) {
        if (!isAvailable() || text.isBlank()) return@withContext false
        mutex.withLock {
            cancelled = false
            try {
                val tts = engine ?: createEngine().also {
                    engine = it
                    Log.i(TAG, "engine ready: sampleRate=${it.sampleRate()} speakers=${it.numSpeakers()}")
                }
                val started = System.currentTimeMillis()
                val audio = tts.generate(text, speakerId, 1.0f)
                val samples = audio.samples
                Log.i(
                    TAG,
                    "generated ${samples.size} samples @ ${audio.sampleRate} Hz in " +
                        "${System.currentTimeMillis() - started} ms (speaker=$speakerId)"
                )
                if (samples.isEmpty()) {
                    false
                } else {
                    play(samples, audio.sampleRate)
                    Log.i(TAG, "playback finished")
                    true
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Kokoro synthesis failed", t)
                false
            }
        }
    }

    /**
     * Synthesize [text] to a mono 16-bit WAV without playing it.
     *
     * Used by [KokoroTtsServer] so the operator chat can receive audio over the
     * loopback. Returns null when Kokoro is not installed, the text is blank, or
     * the engine fails; the caller reports the reason rather than an empty file.
     */
    suspend fun synthesizeWav(text: String, speakerOverride: Int? = null): ByteArray? =
        withContext(Dispatchers.Default) {
            if (!isAvailable() || text.isBlank()) return@withContext null
            mutex.withLock {
                try {
                    val tts = engine ?: createEngine().also {
                        engine = it
                        Log.i(TAG, "engine ready: sampleRate=${it.sampleRate()} speakers=${it.numSpeakers()}")
                    }
                    val started = System.currentTimeMillis()
                    val audio = tts.generate(text, speakerOverride ?: speakerId, 1.0f)
                    val samples = audio.samples
                    if (samples.isEmpty()) return@withLock null
                    Log.i(
                        TAG,
                        "wav ${samples.size} samples @ ${audio.sampleRate} Hz in " +
                            "${System.currentTimeMillis() - started} ms (speaker=${speakerOverride ?: speakerId})"
                    )
                    Pcm.toWavBytes(samples, audio.sampleRate)
                } catch (t: Throwable) {
                    Log.e(TAG, "Kokoro WAV synthesis failed", t)
                    null
                }
            }
        }

    /** Advertised sample rate before/after the engine loads. Kokoro is 24 kHz. */
    fun currentSampleRate(): Int = engine?.sampleRate() ?: 24_000

    private fun createEngine(): OfflineTts {
        val kokoro = OfflineTtsKokoroModelConfig(
            model = File(directory, "model.onnx").absolutePath,
            voices = File(directory, "voices.bin").absolutePath,
            tokens = File(directory, "tokens.txt").absolutePath,
            dataDir = File(directory, "espeak-ng-data").absolutePath,
            lexicon = "",
            lang = "en",
            dictDir = "",
            lengthScale = 1.0f
        )
        val model = OfflineTtsModelConfig(
            kokoro = kokoro,
            numThreads = 2,
            debug = false,
            provider = "cpu"
        )
        val config = OfflineTtsConfig(
            model = model,
            ruleFsts = "",
            ruleFars = "",
            maxNumSentences = 1,
            silenceScale = 0.2f
        )
        // The weights live on external storage, not in assets. sherpa-onnx refuses absolute
        // paths when an AssetManager is supplied (and exits the process), so pass null.
        return OfflineTts(null, config)
    }

    private suspend fun play(samples: FloatArray, sampleRate: Int) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferSize = maxOf(minBuffer, samples.size * 4)
        val output = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track = output
        try {
            output.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            output.play()
            while (!cancelled && output.playbackHeadPosition < samples.size) {
                delay(40)
            }
        } finally {
            runCatching { output.stop() }
            runCatching { output.release() }
            if (track === output) track = null
        }
    }

    override fun stop() {
        cancelled = true
        track?.let { t ->
            runCatching { t.pause() }
            runCatching { t.stop() }
        }
    }

    override fun shutdown() {
        stop()
        runCatching { engine?.release() }
        engine = null
    }

    companion object {
        private const val TAG = "SherpaKokoro"

        fun fromDirectory(
            context: Context,
            directory: File = LocalModels.kokoroDir(context),
            speakerId: Int = LocalModels.DEFAULT_VOICE_ID,
            voiceName: String = LocalModels.DEFAULT_VOICE_NAME
        ): SherpaKokoroSynthesizer =
            SherpaKokoroSynthesizer(context.applicationContext, directory, speakerId, voiceName)
    }
}

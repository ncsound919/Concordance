package com.example.concordance.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Events from the on-device recogniser. Failures are events, not exceptions, so the UI can
 * always explain what happened.
 */
sealed interface SpeechEvent {
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data class Failed(val message: String) : SpeechEvent
    data object Unavailable : SpeechEvent
}

/**
 * Dictation that never sends audio off the device.
 *
 * Android only guarantees a truly on-device recogniser from API 31
 * ([SpeechRecognizer.createOnDeviceSpeechRecognizer]). On older versions the platform may
 * silently route audio to a network service, which is unacceptable for this app, so
 * dictation is reported as unavailable and the operator types instead.
 */
class OnDeviceSpeechRecognizer(private val context: Context) {

    fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /** Why dictation is unavailable, or null when it works. Shown in the UI. */
    fun unavailableReason(): String? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ->
            "This device (Android ${Build.VERSION.RELEASE}) has no guaranteed on-device speech " +
                "recognition. Type instead, so no audio is sent anywhere."
        !SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
            "No on-device speech recogniser is installed on this device."
        else -> null
    }

    /**
     * Listen once. Collection must happen on the main thread, which is where Android
     * requires [SpeechRecognizer] to be created and used.
     */
    fun listen(): Flow<SpeechEvent> = callbackFlow {
        // The SDK check is inline (not just inside unavailableReason) so lint can prove the
        // API-31 call below is guarded.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            trySend(SpeechEvent.Unavailable)
            close()
            return@callbackFlow
        }
        val reason = unavailableReason()
        if (reason != null) {
            trySend(SpeechEvent.Unavailable)
            close()
            return@callbackFlow
        }

        val recognizer = createOnDeviceRecognizer()
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                firstResult(partialResults)?.let { trySend(SpeechEvent.Partial(it)) }
            }

            override fun onResults(results: android.os.Bundle?) {
                val text = firstResult(results)
                if (text != null) {
                    trySend(SpeechEvent.Final(text))
                } else {
                    trySend(SpeechEvent.Failed("No speech was recognised."))
                }
                close()
            }

            override fun onError(error: Int) {
                trySend(SpeechEvent.Failed(describeError(error)))
                close()
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }
        recognizer.startListening(intent)

        awaitClose {
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.destroy() }
        }
    }

    /** Only ever called behind an explicit SDK-31 check. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun createOnDeviceRecognizer(): SpeechRecognizer =
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)

    companion object {
        private fun firstResult(bundle: android.os.Bundle?): String? =
            bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        /** Plain-language explanation; never includes a stack trace. */
        fun describeError(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone permission."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for dictation."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "The recogniser tried to use the network and failed. On-device recognition is required."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognised. Try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The recogniser is busy. Try again in a moment."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was heard. Try again."
            else -> "Speech recognition failed (code $error)."
        }
    }
}

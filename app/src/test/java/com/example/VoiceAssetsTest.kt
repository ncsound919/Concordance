package com.example

import com.example.concordance.voice.EspeakPhonemizer
import com.example.concordance.voice.KokoroVocab
import com.example.concordance.voice.SpeechOutput
import com.example.concordance.voice.SpeechSynthesizer
import com.example.concordance.voice.SherpaKokoroSynthesizer
import com.example.concordance.voice.UnavailableSynthesizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The on-device Kokoro (sherpa-onnx) path, minus the 82M model itself.
 *
 * The model cannot be exercised here, but everything around it can: phoneme parsing,
 * vocabulary loading, and — most importantly — the honest "here is what is missing"
 * reporting that stops a broken install from pretending to work.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceAssetsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---- phonemiser ----

    @Test
    fun `espeak output is joined into one phoneme string`() {
        val raw = "hˈɛlˈoʊ\n\nwˈɜːld\n"
        assertEquals("hˈɛlˈoʊ wˈɜːld", EspeakPhonemizer.parsePhonemes(raw))
    }

    @Test
    fun `blank espeak output yields null rather than empty phonemes`() {
        assertNull(EspeakPhonemizer.parsePhonemes(""))
        assertNull(EspeakPhonemizer.parsePhonemes("\n   \n"))
    }

    @Test
    fun `a missing phonemiser binary is reported as unavailable`() {
        val phonemizer = EspeakPhonemizer(File(tmp.root, "does-not-exist"))
        assertFalse(phonemizer.isAvailable)
        assertNull(phonemizer.phonemes("hello"))
    }

    // ---- vocabulary ----

    @Test
    fun `a flat vocabulary is tokenised`() {
        val vocab = KokoroVocab.fromJson("""{"h":1,"ˈ":2,"ɛ":3,"l":4,"oʊ":5}""")
        assertEquals(5, vocab.size)
        assertEquals(listOf<Long>(1, 2, 3, 4), vocab.tokenize("hˈɛl").toList())
    }

    @Test
    fun `a nested vocabulary is accepted`() {
        val vocab = KokoroVocab.fromJson("""{"vocab":{"a":7,"b":8}}""")
        assertEquals(listOf<Long>(7, 8), vocab.tokenize("ab").toList())
    }

    @Test
    fun `symbols outside the vocabulary are dropped rather than guessed`() {
        val vocab = KokoroVocab.fromJson("""{"a":1}""")
        assertEquals(listOf<Long>(1, 1), vocab.tokenize("a?z a").toList())
    }

    @Test
    fun `an empty vocabulary file is rejected loudly`() {
        assertThrows(IllegalArgumentException::class.java) {
            KokoroVocab.fromJson("""{"vocab":{}}""")
        }
    }

    // ---- honest readiness reporting ----

    @Test
    fun `missing kokoro files are reported with the reason rather than thrown`() {
        val reason = SpeechOutput.kokoroUnavailableReason(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )
        assertNotNull(reason)
        assertTrue(reason!!.contains("missing"))
    }

    @Test
    fun `building without assets returns an unavailable voice, never a crash`() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val synth: SpeechSynthesizer = SherpaKokoroSynthesizer.fromDirectory(context, File(tmp.root, "absent"))
        // The model directory does not exist, so Kokoro is not ready.
        assertFalse(synth.isAvailable())
        assertFalse(synth.speak("hello"))
        synth.stop()
        synth.shutdown()
    }

    @Test
    fun `the unavailable voice explains itself rather than failing silently`() = runBlocking {
        val synth = UnavailableSynthesizer("no model installed")
        assertFalse(synth.isAvailable())
        assertTrue(synth.engineName.contains("no model installed"))
        assertFalse(synth.speak("anything"))
    }
}

package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.concordance.voice.SpeechOutput
import com.example.concordance.voice.UnavailableSynthesizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeechOutputTest {
    @Test
    fun unavailableSynthesizer() {
        val s = UnavailableSynthesizer("nope")
        assertFalse(s.isAvailable())
        assertEquals("No voice available (nope)", s.engineName)
        // speak returns false without throwing
        kotlinx.coroutines.runBlocking { assertFalse(s.speak("hi")) }
        s.stop()
        s.shutdown()
    }

    @Test
    fun kokoroUnavailableReason() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val r = SpeechOutput.kokoroUnavailableReason(ctx)
        assertNotNull(r)
    }

    @Test
    fun forContext_returnsSynthesizer() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val s = SpeechOutput.forContext(ctx)
        assertNotNull(s.engineName)
        // isAvailable may be false; just call
        s.isAvailable()
    }
}

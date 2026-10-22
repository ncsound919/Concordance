package com.example

import com.example.concordance.voice.Pcm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PcmTest2 {
    @Test
    fun toWavBytes_header() {
        val bytes = Pcm.toWavBytes(floatArrayOf(0f, 0.5f, -0.5f), 22050)
        assertEquals(Pcm.HEADER_BYTES + 6, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals(22050, Pcm.readIntLe(bytes, 24))
    }

    @Test
    fun clampToPcm16_bounds() {
        assertEquals(32767, Pcm.clampToPcm16(1f))
        assertEquals(-32767, Pcm.clampToPcm16(-1f))
        assertEquals(0, Pcm.clampToPcm16(0f))
        assertEquals(32767, Pcm.clampToPcm16(5f)) // clamped
    }

    @Test
    fun toWavBytes_requiresPositiveSampleRate() {
        var thrown = false
        try { Pcm.toWavBytes(floatArrayOf(), 0) } catch (e: IllegalArgumentException) { thrown = true }
        assertTrue(thrown)
    }
}

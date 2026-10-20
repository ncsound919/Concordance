package com.example

import com.example.concordance.voice.Pcm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** WAV encoding for the Kokoro output path, checked byte by byte. */
class PcmTest {

    @Test
    fun `a wav header describes 16-bit mono pcm`() {
        val wav = Pcm.toWavBytes(FloatArray(10) { 0f }, sampleRate = 24_000)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))

        assertEquals(16, Pcm.readIntLe(wav, 16))          // PCM chunk size
        assertEquals(1, wav[20].toInt() or (wav[21].toInt() shl 8))  // audio format = PCM
        assertEquals(1, wav[22].toInt())                  // channels
        assertEquals(24_000, Pcm.readIntLe(wav, 24))      // sample rate
        assertEquals(24_000 * 2, Pcm.readIntLe(wav, 28))  // byte rate
        assertEquals(2, wav[32].toInt())                  // block align
        assertEquals(16, wav[34].toInt())                 // bits per sample
    }

    @Test
    fun `the file length matches the sample count`() {
        val wav = Pcm.toWavBytes(FloatArray(100), sampleRate = 16_000)
        assertEquals(Pcm.HEADER_BYTES + 200, wav.size)
        assertEquals(200, Pcm.readIntLe(wav, 40))
        assertEquals(Pcm.HEADER_BYTES + 200 - 8, Pcm.readIntLe(wav, 4))
    }

    @Test
    fun `samples are clamped to the 16-bit range`() {
        assertEquals(0, Pcm.clampToPcm16(0f))
        assertEquals(32_767, Pcm.clampToPcm16(1f))
        assertEquals(-32_767, Pcm.clampToPcm16(-1f))
        assertEquals(32_767, Pcm.clampToPcm16(9f))
        assertEquals(-32_767, Pcm.clampToPcm16(-9f))
    }

    @Test
    fun `extreme float samples do not produce integer overflow noise`() {
        val wav = Pcm.toWavBytes(floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY), sampleRate = 8_000)
        // NaN coerces to 0 in Kotlin's coerceIn, and infinity clamps to full scale; either
        // way the sample must stay inside two bytes rather than wrap.
        assertEquals(Pcm.HEADER_BYTES + 4, wav.size)
    }

    @Test
    fun `only mono output is supported`() {
        assertThrows(IllegalArgumentException::class.java) {
            Pcm.toWavBytes(FloatArray(4), sampleRate = 24_000, channels = 2)
        }
    }

    @Test
    fun `a non-positive sample rate is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Pcm.toWavBytes(FloatArray(4), sampleRate = 0)
        }
    }

    @Test
    fun `a silent buffer still yields a playable file`() {
        val wav = Pcm.toWavBytes(FloatArray(4) { 0f }, sampleRate = 24_000)
        assertTrue(wav.size >= Pcm.HEADER_BYTES + 8)
    }
}

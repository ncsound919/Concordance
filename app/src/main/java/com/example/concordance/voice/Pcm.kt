package com.example.concordance.voice

import java.io.ByteArrayOutputStream

/**
 * Minimal PCM helpers.
 *
 * Kokoro returns float samples; Android plays 16-bit PCM. Writing the WAV header by
 * hand keeps this dependency-free and unit-testable without a device.
 */
object Pcm {

    const val HEADER_BYTES = 44

    /** Encode float samples in [-1, 1] as a 16-bit little-endian mono WAV file. */
    fun toWavBytes(samples: FloatArray, sampleRate: Int, channels: Int = 1): ByteArray {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channels == 1) { "only mono output is supported" }

        val dataBytes = samples.size * 2
        val out = ByteArrayOutputStream(HEADER_BYTES + dataBytes)
        writeAscii(out, "RIFF")
        writeIntLe(out, 36 + dataBytes)
        writeAscii(out, "WAVE")
        writeAscii(out, "fmt ")
        writeIntLe(out, 16)                      // PCM header size
        writeShortLe(out, 1)                     // audio format: PCM
        writeShortLe(out, channels)
        writeIntLe(out, sampleRate)
        writeIntLe(out, sampleRate * channels * 2) // byte rate
        writeShortLe(out, channels * 2)            // block align
        writeShortLe(out, 16)                      // bits per sample
        writeAscii(out, "data")
        writeIntLe(out, dataBytes)
        for (s in samples) {
            val sample = clampToPcm16(s)
            // 16-bit little-endian: low byte first, then high byte.
            out.write(sample and 0xFF)
            out.write((sample ushr 8) and 0xFF)
        }
        return out.toByteArray()
    }

    /** Convert a float sample in [-1, 1] to a signed 16-bit value (little-endian pair). */
    fun clampToPcm16(sample: Float): Int {
        val clamped = sample.coerceIn(-1f, 1f)
        // 32767 is the largest positive 16-bit value; asymmetric scaling avoids clipping 1.0.
        return (clamped * 32767f).toInt()
    }

    private fun writeAscii(out: ByteArrayOutputStream, s: String) {
        out.write(s.toByteArray(Charsets.US_ASCII))
    }

    private fun writeIntLe(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private fun writeShortLe(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    /** Read a little-endian int back out (used by tests and callers that verify a header). */
    fun readIntLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}

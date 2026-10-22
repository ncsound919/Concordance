package com.example

import com.example.concordance.voice.EspeakPhonemizer
import com.example.concordance.voice.KokoroVocab
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhonemizerVocabTest {

    @Test
    fun parsePhonemes_dropsBlankLines() {
        val raw = "p ə ɹ\n\n  t ɛ s t  \n"
        assertEquals("p ə ɹ t ɛ s t", EspeakPhonemizer.parsePhonemes(raw))
    }

    @Test
    fun parsePhonemes_nullOnBlank() {
        assertNull(EspeakPhonemizer.parsePhonemes("   \n\n"))
    }

    @Test
    fun vocab_fromJson_flat() {
        val v = KokoroVocab.fromJson("""{"a": 1, "b": 2}""")
        assertEquals(2, v.size)
        assertArrayEquals(longArrayOf(1, 2), v.tokenize("ab"))
    }

    @Test
    fun vocab_fromJson_nested() {
        val v = KokoroVocab.fromJson("""{"vocab": {"x": 5}}""")
        assertEquals(1, v.size)
        assertArrayEquals(longArrayOf(5), v.tokenize("x"))
    }

    @Test
    fun vocab_tokenize_unknownDropped() {
        val v = KokoroVocab.fromJson("""{"a": 1}""")
        assertArrayEquals(longArrayOf(), v.tokenize("zzz"))
    }

    @Test
    fun vocab_requiresNonEmpty() {
        var thrown = false
        try { KokoroVocab.fromJson("""{}""") } catch (e: IllegalArgumentException) { thrown = true }
        assertTrue(thrown)
    }

    @Test
    fun unavailablePhonemizer() {
        val p = com.example.concordance.voice.UnavailablePhonemizer
        assertTrue(!p.isAvailable)
        assertNull(p.phonemes("hi"))
    }
}

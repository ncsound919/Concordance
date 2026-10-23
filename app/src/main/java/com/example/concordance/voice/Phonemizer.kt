package com.example.concordance.voice

import org.json.JSONObject
import java.io.File

/**
 * Grapheme-to-phoneme step for Kokoro.
 *
 * Kokoro's ONNX graph takes phoneme token ids, so text must be phonemised first.
 * There is no reliable pure-Kotlin English G2P, so the app uses `espeak-ng` when it
 * is present in app storage. If it is missing we report that honestly instead of
 * inventing phonemes (which would produce garbage audio).
 */
interface Phonemizer {
    /** IPA phonemes for [text], or null when phonemisation is unavailable. */
    fun phonemes(text: String): String?

    val isAvailable: Boolean
}

/** espeak-ng invoked as a child process (the binary is supplied by the operator). */
class EspeakPhonemizer(private val binary: File) : Phonemizer {

    override val isAvailable: Boolean
        get() = binary.isFile && binary.canExecute()

    override fun phonemes(text: String): String? {
        if (!isAvailable || text.isBlank()) return null
        return try {
            val process = ProcessBuilder(binary.absolutePath, "-q", "--ipa", "-v", "en-us", text)
                .redirectErrorStream(false)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            parsePhonemes(output)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** espeak prints one phoneme line per input line; blank lines are dropped. */
        fun parsePhonemes(raw: String): String? =
            raw.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
                .takeIf { it.isNotEmpty() }
    }
}

/** Used when no phonemizer is configured; keeps the failure explicit and loud. */
object UnavailablePhonemizer : Phonemizer {
    override val isAvailable: Boolean = false
    override fun phonemes(text: String): String? = null
}

/**
 * Phoneme symbol -> id table, read from the model bundle's own vocabulary file so the
 * ids always match the exact model build in use.
 */
class KokoroVocab private constructor(private val symbolToId: Map<String, Long>) {

    fun tokenize(phonemes: String): LongArray =
        phonemes.mapNotNull { symbolToId[it.toString()] }.toLongArray()

    val size: Int get() = symbolToId.size

    companion object {
        /**
         * Accepts either a flat `{"symbol": id}` object or the nested form
         * `{"vocab": {...}}` used by some Kokoro exports.
         */
        fun fromJson(json: String): KokoroVocab {
            val root = JSONObject(json)
            val table = root.optJSONObject("vocab") ?: root
            val map = LinkedHashMap<String, Long>()
            for (key in table.keys()) {
                map[key] = table.optLong(key)
            }
            require(map.isNotEmpty()) { "vocabulary file contains no symbols" }
            return KokoroVocab(map)
        }

        fun fromFile(file: File): KokoroVocab = fromJson(file.readText(Charsets.UTF_8))
    }
}

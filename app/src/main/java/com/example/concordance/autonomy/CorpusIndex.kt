package com.example.concordance.autonomy

/**
 * The parts of corpus indexing that do not touch Android: which file types count, how text
 * is split for keyword search, and when a cached file is stale.
 *
 * Kept separate so they can be unit-tested without a device, a DocumentFile tree or PDFBox.
 */
object CorpusIndex {

    /** Formats the corpus reads. PDFs are the format papers actually arrive in. */
    val SUPPORTED_EXTENSIONS = setOf("pdf", "md", "txt", "csv", "tsv", "json")

    const val CHUNK_SIZE = 1_000
    const val CHUNK_OVERLAP = 100

    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot < 0 || dot == name.length - 1) "" else name.substring(dot + 1).lowercase()
    }

    fun isSupported(name: String): Boolean = extensionOf(name) in SUPPORTED_EXTENSIONS

    /** True when a cached copy can be reused: same identity, same timestamp, same length. */
    fun isUnchanged(cachedMtime: Long, cachedSize: Long, mtime: Long, size: Long): Boolean =
        cachedMtime > 0 && cachedMtime == mtime && cachedSize == size

    /**
     * Splits text into overlapping windows so a query near a boundary still matches.
     * Blank text yields no chunks rather than one empty one.
     */
    fun chunk(
        source: String,
        text: String,
        chunkSize: Int = CHUNK_SIZE,
        overlap: Int = CHUNK_OVERLAP,
    ): List<LocalCorpus.Chunk> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= chunkSize) return listOf(LocalCorpus.Chunk(source, trimmed))

        val step = (chunkSize - overlap).coerceAtLeast(1)
        val out = ArrayList<LocalCorpus.Chunk>()
        var i = 0
        while (i < trimmed.length) {
            val end = minOf(i + chunkSize, trimmed.length)
            out.add(LocalCorpus.Chunk(source, trimmed.substring(i, end)))
            if (end >= trimmed.length) break
            i += step
        }
        return out
    }

    /**
     * Keyword score for a chunk: whole-term hits weigh more than a bare substring. Used by
     * [LocalCorpus.search] and testable on its own.
     */
    fun score(text: String, terms: List<String>): Int {
        if (terms.isEmpty()) return 0
        val lower = text.lowercase()
        return terms.sumOf { term ->
            val hits = lower.split(term).size - 1
            if (hits == 0) 0 else hits
        }
    }
}

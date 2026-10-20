package com.example.concordance.integration.science

import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import java.security.MessageDigest
import java.util.UUID

/**
 * Batch ingestion of science papers for references and comparable research.
 * Deduplicates by DOI, extracts anchors, builds a reference corpus for
 * reproducibility and credibility.
 */
data class PaperRecord(
    val id: String = UUID.randomUUID().toString().take(12),
    val title: String,
    val authors: String,
    val journal: String,
    val year: Int,
    val doi: String,
    val pmid: String? = null,
    val abstractText: String,
    val sourceApi: String,
    val citationCount: Int = 0,
    val tldr: String? = null,
    val verificationHash: String = ""
) {
    fun computeHash(): String {
        val canonical = buildString {
            append(title); append("|"); append(authors); append("|")
            append(journal); append("|"); append(year); append("|")
            append(doi); append("|"); append(abstractText); append("|")
            append(sourceApi)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun isVerified(): Boolean = verificationHash.isNotBlank() && verificationHash == computeHash()

    /** Convert a paper to an Anchor if it has extractable count data. */
    fun toAnchorOrNull(): Anchor? = null // Papers don't carry counts directly; use SciencePaperManager for extraction
}

object BatchPaperIngestion {

    data class IngestResult(
        val accepted: List<PaperRecord>,
        val rejected: List<Pair<PaperRecord, String>>,
        val duplicates: List<String>,
        val corpus: List<PaperRecord>
    )

    /** Validate a paper record. */
    fun validate(paper: PaperRecord): String? {
        if (paper.title.isBlank()) return "Missing title"
        if (paper.doi.isBlank()) return "Missing DOI"
        if (paper.abstractText.isBlank()) return "Missing abstract"
        if (paper.year < 1900 || paper.year > 2100) return "Invalid year"
        if (paper.sourceApi.isBlank()) return "Missing source API"
        return null
    }

    /** Deduplicate papers by DOI (case-insensitive). */
    fun deduplicate(papers: List<PaperRecord>): Pair<List<PaperRecord>, List<String>> {
        val seen = mutableSetOf<String>()
        val unique = mutableListOf<PaperRecord>()
        val duplicates = mutableListOf<String>()
        for (paper in papers) {
            val key = paper.doi.trim().lowercase()
            if (key in seen) {
                duplicates.add(paper.doi)
            } else {
                seen.add(key)
                unique.add(paper)
            }
        }
        return unique to duplicates
    }

    /** Ingest a batch: validate, deduplicate, hash, build corpus. */
    fun ingest(papers: List<PaperRecord>, existingCorpus: List<PaperRecord> = emptyList()): IngestResult {
        val (unique, duplicates) = deduplicate(papers)

        // Also deduplicate against existing corpus
        val existingDois = existingCorpus.map { it.doi.trim().lowercase() }.toSet()
        val newPapers = unique.filter { it.doi.trim().lowercase() !in existingDois }
        val corpusDuplicates = unique.filter { it.doi.trim().lowercase() in existingDois }.map { it.doi }

        val accepted = mutableListOf<PaperRecord>()
        val rejected = mutableListOf<Pair<PaperRecord, String>>()

        for (paper in newPapers) {
            val error = validate(paper)
            if (error != null) {
                rejected.add(paper to error)
                continue
            }
            if (paper.verificationHash.isBlank()) {
                val hashed = paper.copy(verificationHash = paper.computeHash())
                accepted.add(hashed)
            } else {
                if (!paper.isVerified()) {
                    rejected.add(paper to "Hash verification failed")
                    continue
                }
                accepted.add(paper)
            }
        }

        val allDuplicates = duplicates + corpusDuplicates

        return IngestResult(
            accepted = accepted,
            rejected = rejected,
            duplicates = allDuplicates,
            corpus = existingCorpus + accepted
        )
    }

    /** Search the corpus for papers matching a query. */
    fun searchCorpus(corpus: List<PaperRecord>, query: String): List<PaperRecord> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return corpus
        return corpus.filter {
            it.title.lowercase().contains(q) ||
                it.abstractText.lowercase().contains(q) ||
                it.authors.lowercase().contains(q) ||
                it.doi.lowercase().contains(q) ||
                (it.tldr?.lowercase()?.contains(q) ?: false)
        }
    }

    /** Get papers sorted by citation count (most cited first). */
    fun sortByCitations(corpus: List<PaperRecord>): List<PaperRecord> =
        corpus.sortedByDescending { it.citationCount }
}

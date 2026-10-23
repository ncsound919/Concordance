package com.example

import com.example.concordance.integration.science.BatchPaperIngestion
import com.example.concordance.integration.science.PaperRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchPaperIngestionTest {

    private fun paper(id: String, doi: String, title: String = "Title $id", year: Int = 2025) = PaperRecord(
        id = id, title = title, authors = "Author", journal = "Journal",
        year = year, doi = doi, abstractText = "Abstract text here", sourceApi = "PubMed",
        citationCount = 10, tldr = "TLDR"
    )

    @Test
    fun validPaperIsAcceptedAndHashed() {
        val p = paper("p1", "10.1/test")
        val result = BatchPaperIngestion.ingest(listOf(p))
        assertEquals(1, result.accepted.size)
        assertEquals(0, result.rejected.size)
        assertEquals(0, result.duplicates.size)
        assertTrue(result.accepted[0].isVerified())
    }

    @Test
    fun missingDoiIsRejected() {
        val p = paper("p1", "")
        val result = BatchPaperIngestion.ingest(listOf(p))
        assertEquals(0, result.accepted.size)
        assertEquals(1, result.rejected.size)
        assertTrue(result.rejected[0].second.contains("DOI"))
    }

    @Test
    fun duplicateDoiWithinBatchIsDeduplicated() {
        val p1 = paper("p1", "10.1/test")
        val p2 = paper("p2", "10.1/test") // same DOI
        val result = BatchPaperIngestion.ingest(listOf(p1, p2))
        assertEquals(1, result.accepted.size)
        assertEquals(1, result.duplicates.size)
    }

    @Test
    fun duplicateAgainstExistingCorpusIsDeduplicated() {
        val existing = listOf(paper("p1", "10.1/test").let { it.copy(verificationHash = it.computeHash()) })
        val newPaper = paper("p2", "10.1/test")
        val result = BatchPaperIngestion.ingest(listOf(newPaper), existing)
        assertEquals(0, result.accepted.size)
        assertEquals(1, result.duplicates.size)
        assertEquals(1, result.corpus.size) // corpus unchanged
    }

    @Test
    fun searchCorpusFindsMatchingPapers() {
        val corpus = listOf(
            paper("p1", "10.1/a", "ESR1 brain metastasis"),
            paper("p2", "10.1/b", "PIK3CA liver metastasis")
        ).map { it.copy(verificationHash = it.computeHash()) }
        val results = BatchPaperIngestion.searchCorpus(corpus, "ESR1")
        assertEquals(1, results.size)
        assertEquals("ESR1 brain metastasis", results[0].title)
    }

    @Test
    fun sortByCitationsOrdersDescending() {
        val corpus = listOf(
            paper("p1", "10.1/a").copy(citationCount = 5, verificationHash = "x"),
            paper("p2", "10.1/b").copy(citationCount = 50, verificationHash = "x")
        )
        val sorted = BatchPaperIngestion.sortByCitations(corpus)
        assertEquals(50, sorted[0].citationCount)
    }

    @Test
    fun invalidYearIsRejected() {
        val p = paper("p1", "10.1/test", year = 1800)
        val result = BatchPaperIngestion.ingest(listOf(p))
        assertEquals(0, result.accepted.size)
        assertTrue(result.rejected[0].second.contains("year"))
    }
}

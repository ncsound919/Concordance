package com.example

import com.example.concordance.integration.imaging.ExtractionCandidate
import com.example.concordance.integration.imaging.ImagingDataset
import com.example.concordance.integration.imaging.ImagingDatasetManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagingDatasetTest {

    private fun candidate(status: String = "needs_review", counts: List<Int>? = listOf(5, 50, 10, 100)) =
        ExtractionCandidate(status = status, counts = counts, reasons = listOf("ok"), readings = emptyList())

    @Test
    fun validDatasetIsAcceptedAndHashed() {
        val ds = ImagingDataset(label = "Table 1", sourceImage = "paper.pdf", extractionEngine = "pdfplumber", extractionVersion = "1.0", candidate = candidate())
        val result = ImagingDatasetManager.ingest(listOf(ds))
        assertEquals(1, result.accepted.size)
        assertEquals(0, result.rejected.size)
        assertEquals(1, result.anchors.size)
        assertTrue(result.accepted[0].isVerified())
    }

    @Test
    fun blockedCandidateIsRejected() {
        val ds = ImagingDataset(label = "Table 1", sourceImage = "paper.pdf", extractionEngine = "pdfplumber", extractionVersion = "1.0", candidate = candidate(status = "blocked"))
        val result = ImagingDatasetManager.ingest(listOf(ds))
        assertEquals(0, result.accepted.size)
        assertTrue(result.rejected[0].second.contains("status") || result.rejected.isNotEmpty())
    }

    @Test
    fun missingCountsAreRejected() {
        val ds = ImagingDataset(label = "Table 1", sourceImage = "paper.pdf", extractionEngine = "pdfplumber", extractionVersion = "1.0", candidate = candidate(counts = null))
        val result = ImagingDatasetManager.ingest(listOf(ds))
        assertEquals(0, result.accepted.size)
        assertTrue(result.rejected[0].second.contains("counts"))
    }

    @Test
    fun eventsExceedingTotalAreRejected() {
        val ds = ImagingDataset(label = "Table 1", sourceImage = "paper.pdf", extractionEngine = "pdfplumber", extractionVersion = "1.0", candidate = candidate(counts = listOf(100, 50, 10, 100)))
        val result = ImagingDatasetManager.ingest(listOf(ds))
        assertEquals(0, result.accepted.size)
        assertTrue(result.rejected[0].second.contains("exceed"))
    }

    @Test
    fun toAnchorProducesValidAnchor() {
        val ds = ImagingDataset(label = "Table 1", sourceImage = "paper.pdf", extractionEngine = "pdfplumber", extractionVersion = "1.0", candidate = candidate())
        val result = ImagingDatasetManager.ingest(listOf(ds))
        val anchor = result.anchors[0]
        assertEquals(5, anchor.brainEvents)
        assertEquals(50, anchor.brainTotal)
        assertEquals(10, anchor.otherEvents)
        assertEquals(100, anchor.otherTotal)
        assertEquals("Imaging: paper.pdf (engine: pdfplumber v1.0)", anchor.source)
    }

    @Test
    fun tamperedHashIsRejected() {
        val ds = ImagingDataset(label = "Table 1", sourceImage = "paper.pdf", extractionEngine = "pdfplumber", extractionVersion = "1.0", candidate = candidate(), verificationHash = "tampered")
        val result = ImagingDatasetManager.ingest(listOf(ds))
        assertEquals(0, result.accepted.size)
        assertTrue(result.rejected[0].second.contains("Hash verification"))
    }
}

package com.example

import com.example.concordance.integration.wetlab.WetLabDataIngester
import com.example.concordance.integration.wetlab.WetLabRecord
import com.example.concordance.model.EvidenceGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WetLabDataTest {

    @Test
    fun validRecordIsAcceptedAndHashed() {
        val record = WetLabRecord(
            label = "ESR1 brain vs non-CNS",
            source = "Lab A",
            experimentDate = "2026-10-19",
            operator = "dr-who",
            evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = 53, brainTotal = 822,
            otherEvents = 3220, otherTotal = 15516
        )
        val result = WetLabDataIngester.ingest(listOf(record))
        assertEquals(1, result.accepted.size)
        assertEquals(0, result.rejected.size)
        assertEquals(1, result.anchors.size)
        assertTrue(result.accepted[0].isVerified())
    }

    @Test
    fun invalidCountsAreRejected() {
        val record = WetLabRecord(
            label = "bad", source = "Lab A", experimentDate = "2026-10-19",
            operator = "dr-who", evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = 100, brainTotal = 50 // events > total
        )
        val result = WetLabDataIngester.ingest(listOf(record))
        assertEquals(0, result.accepted.size)
        assertEquals(1, result.rejected.size)
        assertTrue(result.rejected[0].second.contains("exceed"))
    }

    @Test
    fun negativeCountsAreRejected() {
        val record = WetLabRecord(
            label = "bad", source = "Lab A", experimentDate = "2026-10-19",
            operator = "dr-who", evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = -1, brainTotal = 10
        )
        val result = WetLabDataIngester.ingest(listOf(record))
        assertEquals(0, result.accepted.size)
        assertTrue(result.rejected[0].second.contains("Negative"))
    }

    @Test
    fun tamperedHashIsRejected() {
        val record = WetLabRecord(
            label = "test", source = "Lab A", experimentDate = "2026-10-19",
            operator = "dr-who", evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = 1, brainTotal = 10, otherEvents = 2, otherTotal = 20,
            verificationHash = "tampered-hash-value"
        )
        val result = WetLabDataIngester.ingest(listOf(record))
        assertEquals(0, result.accepted.size)
        assertTrue(result.rejected[0].second.contains("Hash verification"))
    }

    @Test
    fun validateReturnsNullForValidRecord() {
        val record = WetLabRecord(
            label = "test", source = "Lab A", experimentDate = "2026-10-19",
            operator = "dr-who", evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = 1, brainTotal = 10, otherEvents = 2, otherTotal = 20
        )
        assertEquals(null, WetLabDataIngester.validate(record))
    }

    @Test
    fun verifyAllChecksAllRecords() {
        val r1 = WetLabRecord(
            label = "r1", source = "Lab A", experimentDate = "2026-10-19",
            operator = "dr-who", evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = 1, brainTotal = 10, otherEvents = 2, otherTotal = 20
        ).let { it.copy(verificationHash = it.computeHash()) }
        val r2 = WetLabRecord(
            label = "r2", source = "Lab A", experimentDate = "2026-10-19",
            operator = "dr-who", evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = 100, brainTotal = 50 // invalid
        )
        assertTrue(WetLabDataIngester.verifyAll(listOf(r1)))
        assertTrue(!WetLabDataIngester.verifyAll(listOf(r1, r2)))
    }

    @Test
    fun toAnchorProducesValidAnchor() {
        val record = WetLabRecord(
            label = "test", source = "Lab A", experimentDate = "2026-10-19",
            operator = "dr-who", evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = 5, brainTotal = 50, otherEvents = 10, otherTotal = 100,
            verificationHash = "" // will be set during ingest
        )
        val result = WetLabDataIngester.ingest(listOf(record))
        val anchor = result.anchors[0]
        assertEquals(5, anchor.brainEvents)
        assertEquals(50, anchor.brainTotal)
        assertEquals(10, anchor.otherEvents)
        assertEquals(100, anchor.otherTotal)
    }
}

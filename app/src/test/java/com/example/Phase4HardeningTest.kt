package com.example

import com.example.concordance.learning.ModelParameters
import com.example.concordance.learning.ModelRevision
import com.example.concordance.learning.RevisionArchive
import com.example.concordance.learning.TrainingSetBuilder
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase4HardeningTest {

    private fun rev(id: String, sn: Double = 0.85, ref: Double = 0.18, promoted: String? = "dr-who") =
        ModelRevision(
            id = id,
            parentRevisionId = null,
            createdAtUtc = "2026-10-19T00:00:00Z",
            parameters = ModelParameters(sn = sn, referencePrevalence = ref),
            metricE = 0.123,
            promotedBy = promoted,
            notes = "n-$id"
        )

    // --- Revision archive round-trip ---

    @Test
    fun archiveRoundTripsRevisions() {
        val original = listOf(rev("rev-1"), rev("rev-2", sn = 0.70, ref = 0.30, promoted = null))
        val json = RevisionArchive.export(original)
        val restored = RevisionArchive.import(json)

        assertEquals(2, restored.size)
        assertEquals("rev-1", restored[0].id)
        assertEquals(0.85, restored[0].parameters.sn, 1e-9)
        assertEquals(0.18, restored[0].parameters.referencePrevalence, 1e-9)
        assertEquals("dr-who", restored[0].promotedBy)
        assertEquals("rev-2", restored[1].id)
        assertNull(restored[1].promotedBy)
        assertEquals(0.123, restored[1].metricE, 1e-9)
    }

    @Test
    fun archiveCarriesSchemaVersionAndKind() {
        val json = RevisionArchive.export(listOf(rev("rev-1")))
        assertTrue(json.contains("\"schemaVersion\""))
        assertTrue(json.contains("concordance.model-revisions"))
    }

    @Test
    fun importRejectsUnknownSchemaVersion() {
        val bad = """{"schemaVersion": 99, "kind": "concordance.model-revisions", "revisions": []}"""
        try {
            RevisionArchive.import(bad)
            fail("expected ArchiveException")
        } catch (e: RevisionArchive.ArchiveException) {
            assertTrue(e.message!!.contains("schemaVersion"))
        }
    }

    @Test
    fun importRejectsWrongKind() {
        val bad = """{"schemaVersion": 1, "kind": "something-else", "revisions": []}"""
        try {
            RevisionArchive.import(bad)
            fail("expected ArchiveException")
        } catch (e: RevisionArchive.ArchiveException) {
            assertTrue(e.message!!.contains("kind"))
        }
    }

    @Test
    fun importRejectsOutOfBoundsParameters() {
        val bad = """
        {"schemaVersion":1,"kind":"concordance.model-revisions","revisions":[
          {"id":"rev-x","parentRevisionId":null,"createdAtUtc":"t","sn":5.0,
           "referencePrevalence":0.2,"z95":1.96,"metricE":0.1}
        ]}
        """.trimIndent()
        try {
            RevisionArchive.import(bad)
            fail("expected ArchiveException")
        } catch (e: RevisionArchive.ArchiveException) {
            assertTrue(e.message!!.contains("out-of-bounds"))
        }
    }

    @Test
    fun importRejectsMissingParameterFields() {
        val bad = """
        {"schemaVersion":1,"kind":"concordance.model-revisions","revisions":[
          {"id":"rev-x","createdAtUtc":"t","referencePrevalence":0.2,"z95":1.96}
        ]}
        """.trimIndent()
        try {
            RevisionArchive.import(bad)
            fail("expected ArchiveException")
        } catch (e: RevisionArchive.ArchiveException) {
            assertTrue(e.message!!.contains("missing parameters"))
        }
    }

    @Test
    fun importRejectsMalformedJson() {
        try {
            RevisionArchive.import("{not json")
            fail("expected ArchiveException")
        } catch (e: RevisionArchive.ArchiveException) {
            assertNotNull(e.message)
        }
    }

    // --- Anti-collapse assertion (belt-and-braces) ---

    @Test
    fun assertNoSelfGeneratedPassesForHumanAnchors() {
        val clean = listOf(
            Anchor(
                id = "h1", label = "human", source = "Giannoudis 2024",
                evidenceGrade = EvidenceGrade.COUNTS,
                brainEvents = 1, brainTotal = 2, otherEvents = 3, otherTotal = 4
            )
        )
        TrainingSetBuilder.assertNoSelfGenerated(clean) // must not throw
    }

    @Test
    fun assertNoSelfGeneratedThrowsForImportedAnchor() {
        val bad = listOf(
            Anchor(
                id = "civic-1", label = "imported", source = "CIViC EID243: url",
                evidenceGrade = EvidenceGrade.ABSTRACT_ONLY, oddsRatio = 0.4
            )
        )
        try {
            TrainingSetBuilder.assertNoSelfGenerated(bad)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Anti-collapse"))
        }
    }

    @Test
    fun assertNoSelfGeneratedThrowsForRevisionDerivedAnchor() {
        val bad = listOf(
            Anchor(
                id = "d1", label = "derived", source = "Giannoudis 2024",
                evidenceGrade = EvidenceGrade.COUNTS,
                brainEvents = 1, brainTotal = 2, otherEvents = 3, otherTotal = 4,
                modelRevisionId = "rev-9"
            )
        )
        try {
            TrainingSetBuilder.assertNoSelfGenerated(bad)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Anti-collapse"))
        }
    }
}

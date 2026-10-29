package com.example

import androidx.room.Room
import com.example.concordance.data.ConcordanceDatabase
import com.example.concordance.evidence.ArtifactType
import com.example.concordance.evidence.DerivedKind
import com.example.concordance.evidence.EvidenceKind
import com.example.concordance.evidence.EvidenceRef
import com.example.concordance.evidence.KgEdge
import com.example.concordance.evidence.LineageEdge
import com.example.concordance.evidence.NodeKind
import com.example.concordance.evidence.NodeRef
import com.example.concordance.evidence.Predicate
import com.example.concordance.evidence.PutResult
import com.example.concordance.evidence.ReviewStatus
import com.example.concordance.evidence.RoomEvidenceStore
import com.example.concordance.evidence.SourceArtifact
import com.example.concordance.evidence.Hypothesis
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RoomEvidenceStoreTest {

    private lateinit var db: ConcordanceDatabase
    private lateinit var store: RoomEvidenceStore

    @Before
    fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, ConcordanceDatabase::class.java).allowMainThreadQueries().build()
        store = RoomEvidenceStore(db.evidenceDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun anArtifactSurvivesAProcessBoundary() {
        val artifact = SourceArtifact.create(
            contentHash = "a".repeat(64),
            type = ArtifactType.PAPER_PDF,
            originUri = "pmid:12345678",
            mimeType = "application/pdf",
            sizeBytes = 1024,
            ingestedAtUtc = "2026-10-28T12:00:00Z",
        )
        val first = store.putArtifact(artifact)
        assertTrue(first is PutResult.Stored<*>)
        val reloaded = store.artifact(artifact.artifactId)!!
        assertEquals(artifact.artifactId, reloaded.artifactId)
        assertEquals(artifact.contentHash, reloaded.contentHash)
    }

    @Test
    fun anArtifactIsDeduplicatedAcrossWrites() {
        val artifact = SourceArtifact.create(
            contentHash = "b".repeat(64),
            type = ArtifactType.API_RECORD,
            originUri = "civic:EID42",
            mimeType = "application/json",
            sizeBytes = 42,
            ingestedAtUtc = "2026-10-28T12:00:00Z",
        )
        assertTrue((store.putArtifact(artifact) as PutResult.Stored<*>).created)
        assertFalse((store.putArtifact(artifact) as PutResult.Stored<*>).created)
        assertEquals(1, store.artifacts().size)
    }

    @Test
    fun anEdgeMergesItsEvidenceAcrossWrites() {
        val node = NodeRef(NodeKind.GENE, "EGFR", "EGFR")
        val drug = NodeRef(NodeKind.DRUG, "erlotinib", "Erlotinib")
        val first = KgEdge(
            edgeId = KgEdge.idFor(node, Predicate.SENSITIVE_TO, drug),
            subject = node,
            predicate = Predicate.SENSITIVE_TO,
            objectRef = drug,
            evidence = listOf(EvidenceRef(EvidenceKind.DATASET, "ds_1")),
            score = 0.8,
            createdBy = "test",
            createdAtUtc = "2026-10-28T12:00:00Z",
        )
        val second = first.copy(
            evidence = listOf(EvidenceRef(EvidenceKind.PAPER, "pmid:1")),
            score = 0.9,
        )
        assertTrue((store.putEdge(first) as PutResult.Stored<KgEdge>).created)
        val merged = (store.putEdge(second) as PutResult.Stored<KgEdge>).value
        assertEquals(2, merged.evidenceCount)
        assertEquals(0.9, merged.score, 0.0)
        assertEquals(1, store.edges().size)
        assertEquals(2, store.edge(first.edgeId)!!.evidenceCount)
    }

    @Test
    fun lineageIsQueryableAfterPersistence() {
        val line = LineageEdge(
            derivedKind = DerivedKind.CLAIM,
            derivedId = "claim-1",
            artifactId = "art_aaaaaaaaaaaaaaaa",
            transform = "pdf_to_anchor",
            transformVersion = "1.2.0",
            createdAtUtc = "2026-10-28T12:00:00Z",
        )
        assertTrue((store.addLineage(line) as PutResult.Stored<*>).created)
        assertEquals(1, store.lineageFor("claim-1").size)
        assertEquals("pdf_to_anchor", store.lineageFor("claim-1")[0].transform)
    }

    @Test
    fun aHypothesisRoundTripsItsLists() {
        val edge = KgEdge(
            edgeId = KgEdge.idFor(
                NodeRef(NodeKind.GENE, "EGFR", "EGFR"),
                Predicate.ASSOCIATED_WITH,
                NodeRef(NodeKind.DRUG, "erlotinib", "Erlotinib"),
            ),
            subject = NodeRef(NodeKind.GENE, "EGFR", "EGFR"),
            predicate = Predicate.ASSOCIATED_WITH,
            objectRef = NodeRef(NodeKind.DRUG, "erlotinib", "Erlotinib"),
            evidence = listOf(EvidenceRef(EvidenceKind.ARTIFACT, "art_x")),
            score = 0.5,
            createdBy = "test",
            createdAtUtc = "2026-10-28T12:00:00Z",
        )
        store.putEdge(edge)
        val hypothesis = Hypothesis(
            hypothesisId = "hyp_test",
            cancerType = "brain",
            biomarkerSymbol = "EGFR",
            biomarkerType = "amplification",
            candidateDrug = "erlotinib",
            drugRxnorm = "41493",
            rationale = "round trip",
            supportingEdgeIds = listOf(edge.edgeId),
            pubmedIds = listOf("1", "2"),
            datasetIds = listOf("ds_1"),
            wetlabIds = listOf("wet_1"),
            rankScore = 0.7,
            modelVersion = "v1",
            generatedAtUtc = "2026-10-28T12:00:00Z",
            reviewStatus = ReviewStatus.UNREVIEWED,
        )
        assertTrue((store.putHypothesis(hypothesis) as PutResult.Stored<*>).created)
        val reloaded = store.hypothesis("hyp_test")!!
        assertEquals(listOf("1", "2"), reloaded.pubmedIds)
        assertEquals(listOf("ds_1"), reloaded.datasetIds)
        assertEquals(listOf("wet_1"), reloaded.wetlabIds)
        assertEquals(listOf(edge.edgeId), reloaded.supportingEdgeIds)
        assertEquals(0.7, reloaded.rankScore, 0.0)
    }
}

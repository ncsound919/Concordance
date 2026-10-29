package com.example

import com.example.concordance.evidence.ArtifactType
import com.example.concordance.evidence.DatasetRecord
import com.example.concordance.evidence.DerivedKind
import com.example.concordance.evidence.EvidenceKind
import com.example.concordance.evidence.EvidenceRef
import com.example.concordance.evidence.EvidenceRules
import com.example.concordance.evidence.Hypothesis
import com.example.concordance.evidence.InMemoryEvidenceStore
import com.example.concordance.evidence.KgEdge
import com.example.concordance.evidence.LineageEdge
import com.example.concordance.evidence.NodeKind
import com.example.concordance.evidence.NodeRef
import com.example.concordance.evidence.Predicate
import com.example.concordance.evidence.PutResult
import com.example.concordance.evidence.ReviewResult
import com.example.concordance.evidence.ReviewStatus
import com.example.concordance.evidence.SourceArtifact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceGraphTest {

    private val hash = "a".repeat(64)
    private val otherHash = "b".repeat(64)
    private val at = "2026-10-28T12:00:00Z"

    private fun artifact(h: String = hash) = SourceArtifact.create(
        contentHash = h,
        type = ArtifactType.PAPER_PDF,
        originUri = "pmid:12345678",
        mimeType = "application/pdf",
        sizeBytes = 1024,
        ingestedAtUtc = at,
    )

    private fun dataset(checksum: String = otherHash) = DatasetRecord.create(
        source = "cbioportal",
        studyId = "brca_tcga",
        cancerType = "breast",
        assayType = "rnaseq",
        sampleCount = 1100,
        checksum = checksum,
        license = "CC-BY-4.0",
    )

    private fun edge(
        subjectId: String = "EGFR",
        objectId: String = "erlotinib",
        evidence: List<EvidenceRef> = listOf(EvidenceRef(EvidenceKind.DATASET, "ds_1")),
        score: Double = 0.8,
    ): KgEdge {
        val subject = NodeRef(NodeKind.GENE, subjectId, subjectId)
        val obj = NodeRef(NodeKind.DRUG, objectId, objectId)
        return KgEdge(
            edgeId = KgEdge.idFor(subject, Predicate.SENSITIVE_TO, obj),
            subject = subject,
            predicate = Predicate.SENSITIVE_TO,
            objectRef = obj,
            evidence = evidence,
            score = score,
            createdBy = "bioinformatics-engine",
            createdAtUtc = at,
        )
    }

    private fun hypothesis(
        edgeIds: List<String>,
        status: ReviewStatus = ReviewStatus.UNREVIEWED,
        reviewer: String? = null,
    ) = Hypothesis(
        hypothesisId = Hypothesis.idFor("brain", "EGFR", "erlotinib", "rag-v1.3+ranker-v0.9"),
        cancerType = "brain",
        biomarkerSymbol = "EGFR",
        biomarkerType = "amplification",
        candidateDrug = "erlotinib",
        drugRxnorm = "41493",
        rationale = "EGFR amplification plus matched cell-line sensitivity",
        supportingEdgeIds = edgeIds,
        pubmedIds = listOf("12345678"),
        datasetIds = listOf("ds_1"),
        wetlabIds = listOf("wet_1"),
        rankScore = 0.87,
        modelVersion = "rag-v1.3+ranker-v0.9",
        generatedAtUtc = at,
        reviewStatus = status,
        reviewer = reviewer,
        reviewedAtUtc = if (status == ReviewStatus.UNREVIEWED) null else at,
    )

    private fun <T> stored(result: PutResult<T>): PutResult.Stored<T> {
        assertTrue(result.toString(), result is PutResult.Stored<*>)
        @Suppress("UNCHECKED_CAST")
        return result as PutResult.Stored<T>
    }

    private fun rejected(result: PutResult<*>): PutResult.Rejected {
        assertTrue(result.toString(), result is PutResult.Rejected)
        return result as PutResult.Rejected
    }

    // --- artifacts -------------------------------------------------------------------------

    @Test
    fun anArtifactIdIsItsContentHash() {
        val a = artifact()
        assertEquals("art_" + hash.take(16), a.artifactId)
        assertTrue(EvidenceRules.validateArtifact(a).isEmpty())
    }

    @Test
    fun ingestingTheSameBytesTwiceDeduplicates() {
        val store = InMemoryEvidenceStore()
        val first = stored(store.putArtifact(artifact()))
        val second = stored(store.putArtifact(artifact()))
        assertTrue(first.created)
        assertFalse(second.created)
        assertEquals(first.value.artifactId, second.value.artifactId)
    }

    @Test
    fun aMalformedHashIsRejected() {
        assertTrue(EvidenceRules.validateArtifact(artifact(h = "short")).any { it.code == "ARTIFACT_HASH" })
        assertTrue(rejected(InMemoryEvidenceStore().putArtifact(artifact(h = "short"))).problems.isNotEmpty())
    }

    // --- datasets --------------------------------------------------------------------------

    @Test
    fun aDatasetIdIsDerivedFromSourceStudyAndChecksum() {
        val d = dataset()
        assertEquals(DatasetRecord.idFor("cbioportal", "brca_tcga", otherHash), d.datasetId)
        assertTrue(d.isPinned)
    }

    @Test
    fun anUnpinnedDatasetMayExistButIsNotPinned() {
        val unpinned = dataset(checksum = "")
        assertFalse(unpinned.isPinned)
        assertTrue(EvidenceRules.validateDataset(unpinned).isEmpty())
    }

    @Test
    fun aDatasetWithNoSamplesIsRejected() {
        assertTrue(EvidenceRules.validateDataset(dataset().copy(sampleCount = 0)).any { it.code == "DATASET_SAMPLES" })
    }

    // --- edges -----------------------------------------------------------------------------

    @Test
    fun anEdgeWithoutEvidenceIsNotAnEdge() {
        val bare = edge(evidence = emptyList())
        assertTrue(EvidenceRules.validateEdge(bare).any { it.code == "EDGE_NO_EVIDENCE" })
        assertTrue(rejected(InMemoryEvidenceStore().putEdge(bare)).problems.isNotEmpty())
    }

    @Test
    fun anEdgeScoreOutsideZeroToOneIsRejected() {
        assertTrue(EvidenceRules.validateEdge(edge(score = 1.4)).any { it.code == "EDGE_SCORE" })
        assertTrue(EvidenceRules.validateEdge(edge(score = -0.1)).any { it.code == "EDGE_SCORE" })
        assertTrue(EvidenceRules.validateEdge(edge(score = Double.NaN)).any { it.code == "EDGE_SCORE" })
    }

    @Test
    fun aSelfEdgeIsRejected() {
        // A self-edge needs the same node on both sides: two equal ids of different kinds are a
        // legitimate gene-to-drug edge, not a loop.
        val node = NodeRef(NodeKind.GENE, "EGFR", "EGFR")
        val self = KgEdge(
            edgeId = KgEdge.idFor(node, Predicate.ASSOCIATED_WITH, node),
            subject = node,
            predicate = Predicate.ASSOCIATED_WITH,
            objectRef = node,
            evidence = listOf(EvidenceRef(EvidenceKind.DATASET, "ds_1")),
            score = 0.5,
            createdBy = "test",
            createdAtUtc = at,
        )
        assertTrue(EvidenceRules.validateEdge(self).any { it.code == "EDGE_SELF" })
    }

    @Test
    fun theSameEdgeDiscoveredTwiceMergesItsEvidence() {
        val store = InMemoryEvidenceStore()
        val first = stored(store.putEdge(edge()))
        assertTrue(first.created)
        assertEquals(1, first.value.evidenceCount)

        val second = stored(
            store.putEdge(
                edge(
                    evidence = listOf(
                        EvidenceRef(EvidenceKind.DATASET, "ds_1"),
                        EvidenceRef(EvidenceKind.PAPER, "pmid:12345678"),
                    )
                )
            )
        )
        assertFalse(second.created)
        assertEquals(2, second.value.evidenceCount)
        assertEquals(1, store.edges().size)
    }

    @Test
    fun theEdgeIdIgnoresTheDisplayLabel() {
        val a = edge()
        assertEquals(a.edgeId, a.copy(subject = a.subject.copy(label = "EGFR (HER1)")).edgeId)
        assertEquals(a.edgeId, KgEdge.idFor(a.subject, a.predicate, a.objectRef))
    }

    @Test
    fun anEdgeWhoseIdDoesNotMatchItsNodesIsRejected() {
        assertTrue(EvidenceRules.validateEdge(edge().copy(edgeId = "kg_deadbeefdeadbeef")).any { it.code == "EDGE_ID" })
    }

    // --- hypotheses ------------------------------------------------------------------------

    @Test
    fun aHypothesisWithoutASupportingEdgeIsRefused() {
        val result = rejected(InMemoryEvidenceStore().putHypothesis(hypothesis(emptyList())))
        assertTrue(result.problems.any { it.code == "HYP_NO_EDGES" })
    }

    @Test
    fun aHypothesisCannotCiteAnEdgeThatDoesNotExist() {
        val result = rejected(InMemoryEvidenceStore().putHypothesis(hypothesis(listOf("kg_missing00000000"))))
        assertTrue(result.problems.any { it.code == "HYP_UNKNOWN_EDGE" })
    }

    @Test
    fun aHypothesisWithARealEdgeIsAccepted() {
        val store = InMemoryEvidenceStore()
        val storedEdge = stored(store.putEdge(edge())).value
        val result = stored(store.putHypothesis(hypothesis(listOf(storedEdge.edgeId))))
        assertTrue(result.created)
    }

    @Test
    fun anUnstandardisedDrugIsRefused() {
        val store = InMemoryEvidenceStore()
        val storedEdge = stored(store.putEdge(edge())).value
        val result = rejected(
            store.putHypothesis(hypothesis(listOf(storedEdge.edgeId)).copy(drugRxnorm = ""))
        )
        assertTrue(result.problems.any { it.code == "HYP_RXNORM" })
    }

    @Test
    fun nothingIsPublishableBeforeAHumanReview() {
        val store = InMemoryEvidenceStore()
        val storedEdge = stored(store.putEdge(edge())).value
        val h = hypothesis(listOf(storedEdge.edgeId))
        assertFalse(h.isPublishable)
        assertFalse(EvidenceRules.isPublishable(h, setOf(storedEdge.edgeId)))
    }

    @Test
    fun reviewIsForwardOnlyAndCannotSkipAState() {
        val h = hypothesis(listOf("kg_x"))
        assertFalse(EvidenceRules.canReview(ReviewStatus.UNREVIEWED, ReviewStatus.REVIEWED))
        assertTrue(EvidenceRules.canReview(ReviewStatus.UNREVIEWED, ReviewStatus.IN_REVIEW))
        assertTrue(EvidenceRules.canReview(ReviewStatus.IN_REVIEW, ReviewStatus.REVIEWED))
        assertFalse(EvidenceRules.canReview(ReviewStatus.REVIEWED, ReviewStatus.IN_REVIEW))
        assertFalse(EvidenceRules.canReview(ReviewStatus.REVIEWED, ReviewStatus.UNREVIEWED))
        assertTrue(EvidenceRules.review(h, ReviewStatus.REVIEWED, "dr.smith", at) is ReviewResult.Refused)
    }

    @Test
    fun aFinalReviewNeedsANamedReviewer() {
        val inReview = hypothesis(listOf("kg_x"), status = ReviewStatus.IN_REVIEW)
        val refused = EvidenceRules.review(inReview, ReviewStatus.REVIEWED, null, at) as ReviewResult.Refused
        assertEquals("REVIEW_REVIEWER", refused.code)

        val reviewed = (EvidenceRules.review(inReview, ReviewStatus.REVIEWED, "dr.smith", at) as ReviewResult.Updated)
            .hypothesis
        assertEquals(ReviewStatus.REVIEWED, reviewed.reviewStatus)
        assertEquals("dr.smith", reviewed.reviewer)
        assertTrue(reviewed.isPublishable)
    }

    @Test
    fun aReviewedHypothesisIsOnlyPublishableWhenTheRulesStillPass() {
        val store = InMemoryEvidenceStore()
        val storedEdge = stored(store.putEdge(edge())).value
        val reviewed = hypothesis(listOf(storedEdge.edgeId), status = ReviewStatus.REVIEWED, reviewer = "dr.smith")
        assertTrue(EvidenceRules.isPublishable(reviewed, setOf(storedEdge.edgeId)))
        assertFalse(EvidenceRules.isPublishable(reviewed, emptySet()))
    }

    // --- lineage ---------------------------------------------------------------------------

    @Test
    fun lineageLinksADerivedRecordToItsArtifactOnce() {
        val store = InMemoryEvidenceStore()
        val a = stored(store.putArtifact(artifact())).value
        val line = LineageEdge(DerivedKind.CLAIM, "claim-1", a.artifactId, "pdf_to_anchor", "1.2.0", at)
        assertTrue(stored(store.addLineage(line)).created)
        assertFalse(stored(store.addLineage(line)).created)
        assertEquals(1, store.lineageFor("claim-1").size)
        assertNull(store.lineageFor("claim-2").firstOrNull())
        assertNotNull(store.artifact(a.artifactId))
    }

    @Test
    fun lineageWithNoTransformIsRejected() {
        val bad = LineageEdge(DerivedKind.CAPSULE, "cap-1", "art_1", "", "1.0", at)
        assertTrue(rejected(InMemoryEvidenceStore().addLineage(bad)).problems.isNotEmpty())
    }
}

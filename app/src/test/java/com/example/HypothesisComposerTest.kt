package com.example

import com.example.concordance.evidence.EvidenceKind
import com.example.concordance.evidence.EvidenceRef
import com.example.concordance.evidence.HypothesisComposer
import com.example.concordance.evidence.KgEdge
import com.example.concordance.evidence.NodeKind
import com.example.concordance.evidence.NodeRef
import com.example.concordance.evidence.Predicate
import com.example.concordance.evidence.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HypothesisComposerTest {

    private val at = "2026-10-28T12:00:00Z"

    private fun request() = HypothesisComposer.Request(
        cancerType = "nsclc",
        biomarkerSymbol = "EGFR",
        biomarkerType = "amplification",
        candidateDrug = "Erlotinib",
        drugRxnorm = "41493",
        modelVersion = "rag-v1.3+ranker-v0.9",
        atUtc = at,
    )

    private fun edge(
        predicate: Predicate = Predicate.SENSITIVE_TO,
        gene: String = "EGFR",
        drug: String = "erlotinib",
        score: Double = 0.9,
        evidence: List<EvidenceRef> = listOf(EvidenceRef(EvidenceKind.ARTIFACT, "art_aaaaaaaaaaaaaaaa")),
        objectKind: NodeKind = NodeKind.DRUG,
        objectId: String = "drug:$drug",
    ): KgEdge {
        val subject = NodeRef(NodeKind.GENE, "gene:$gene", gene)
        val obj = NodeRef(objectKind, objectId, drug)
        return KgEdge(
            edgeId = KgEdge.idFor(subject, predicate, obj),
            subject = subject,
            predicate = predicate,
            objectRef = obj,
            evidence = evidence,
            score = score,
            createdBy = "test",
            createdAtUtc = at,
        )
    }

    private fun built(result: HypothesisComposer.Result) = (result as HypothesisComposer.Result.Built).hypothesis

    private fun refused(result: HypothesisComposer.Result) = (result as HypothesisComposer.Result.Refused).code

    @Test
    fun aMatchingSensitivityEdgeProducesAnUnreviewedHypothesis() {
        val h = built(HypothesisComposer.compose(request(), listOf(edge())))
        assertEquals("EGFR", h.biomarkerSymbol)
        assertEquals("Erlotinib", h.candidateDrug)
        assertEquals("41493", h.drugRxnorm)
        assertEquals("rag-v1.3+ranker-v0.9", h.modelVersion)
        assertEquals(ReviewStatus.UNREVIEWED, h.reviewStatus)
        assertFalse(h.isPublishable)
        assertEquals(1, h.supportingEdgeIds.size)
        assertTrue(h.rankScore > 0.0 && h.rankScore < 1.0)
    }

    @Test
    fun anEdgeForAnotherGeneIsIgnored() {
        val result = HypothesisComposer.compose(request(), listOf(edge(gene = "KRAS")))
        assertEquals("NO_SUPPORTING_EDGE", refused(result))
    }

    @Test
    fun anEdgeForAnotherDrugIsIgnored() {
        val result = HypothesisComposer.compose(request(), listOf(edge(drug = "gefitinib")))
        assertEquals("NO_SUPPORTING_EDGE", refused(result))
    }

    @Test
    fun aDiseaseAssociationEdgeCountsForTheCancerType() {
        val diseaseEdge = edge(
            predicate = Predicate.ASSOCIATED_WITH,
            objectKind = NodeKind.CANCER_TYPE,
            objectId = "disease:nsclc",
        )
        val h = built(HypothesisComposer.compose(request(), listOf(diseaseEdge)))
        assertEquals(1, h.supportingEdgeIds.size)
    }

    @Test
    fun noEdgesAtAllIsARefusal() {
        assertEquals("NO_SUPPORTING_EDGE", refused(HypothesisComposer.compose(request(), emptyList())))
    }

    @Test
    fun resistanceEvidenceAsStrongAsSensitivityRefusesTheCandidate() {
        val result = HypothesisComposer.compose(
            request(),
            listOf(
                edge(predicate = Predicate.SENSITIVE_TO, score = 0.7),
                edge(predicate = Predicate.RESISTANT_TO, score = 0.7),
            ),
        )
        assertEquals("RESISTANCE_CONTRADICTS", refused(result))
    }

    @Test
    fun strongerResistanceRefusesTheCandidate() {
        val result = HypothesisComposer.compose(
            request(),
            listOf(
                edge(predicate = Predicate.SENSITIVE_TO, score = 0.4),
                edge(predicate = Predicate.RESISTANT_TO, score = 0.95),
            ),
        )
        assertEquals("RESISTANCE_CONTRADICTS", refused(result))
    }

    @Test
    fun weakerResistanceIsRecordedAsACaveatButDoesNotBlock() {
        val h = built(
            HypothesisComposer.compose(
                request(),
                listOf(
                    edge(predicate = Predicate.SENSITIVE_TO, score = 0.9),
                    edge(predicate = Predicate.RESISTANT_TO, score = 0.3),
                ),
            )
        )
        // The resistance edge is a caveat, never a supporting citation.
        assertEquals(1, h.supportingEdgeIds.size)
        assertTrue(h.rationale, h.rationale.contains("resistance", ignoreCase = true))
    }

    @Test
    fun corroborationRaisesTheRank() {
        val single = built(HypothesisComposer.compose(request(), listOf(edge(score = 0.9))))
        val corroborated = built(
            HypothesisComposer.compose(
                request(),
                listOf(
                    edge(score = 0.9),
                    edge(
                        score = 0.9,
                        evidence = listOf(EvidenceRef(EvidenceKind.DATASET, "ds_1")),
                    ),
                    edge(
                        score = 0.9,
                        evidence = listOf(EvidenceRef(EvidenceKind.PAPER, "pmid:1")),
                    ),
                ),
            )
        )
        assertTrue(
            "corroborated rank ${corroborated.rankScore} should exceed single ${single.rankScore}",
            corroborated.rankScore > single.rankScore,
        )
    }

    @Test
    fun theRankNeverReachesCertaintyFromOneEdge() {
        val h = built(HypothesisComposer.compose(request(), listOf(edge(score = 1.0))))
        assertTrue("rank ${h.rankScore} must stay below 1.0", h.rankScore < 1.0)
    }

    @Test
    fun anIncompleteRequestIsRefusedBeforeAnythingIsBuilt() {
        val result = HypothesisComposer.compose(
            request().copy(drugRxnorm = ""),
            listOf(edge()),
        )
        assertEquals("INCOMPLETE_REQUEST", refused(result))
    }

    @Test
    fun theSameRequestAndEdgesProduceTheSameHypothesisId() {
        val a = built(HypothesisComposer.compose(request(), listOf(edge())))
        val b = built(HypothesisComposer.compose(request(), listOf(edge())))
        assertEquals(a.hypothesisId, b.hypothesisId)
        assertEquals(a.supportingEdgeIds, b.supportingEdgeIds)
        assertEquals(a.rankScore, b.rankScore, 0.0)
    }
}

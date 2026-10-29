package com.example

import com.example.concordance.evidence.EvidenceKind
import com.example.concordance.evidence.InMemoryEvidenceStore
import com.example.concordance.evidence.Predicate
import com.example.concordance.evidence.ReviewStatus
import com.example.concordance.evidence.connectors.CivicConnector
import com.example.concordance.evidence.connectors.CohortConnector
import com.example.concordance.evidence.connectors.ConnectorIngest
import com.example.concordance.evidence.connectors.LiteratureConnector
import com.example.concordance.evidence.connectors.WetLabConnector
import com.example.concordance.integration.api.CohortStudy
import com.example.concordance.integration.api.CuratedVariantEvidence
import com.example.concordance.integration.science.SciencePaper
import com.example.concordance.integration.wetlab.WetLabRecord
import com.example.concordance.model.EvidenceGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorTest {

    private val at = "2026-10-28T12:00:00Z"

    private fun paper(doi: String = "10.1000/xyz", pmid: String? = "12345678") = SciencePaper(
        id = "paper-1",
        title = "EGFR amplification predicts erlotinib response in glioblastoma",
        authors = "Smith J, Doe A",
        journal = "Neuro-Oncology",
        year = 2025,
        doi = doi,
        pmid = pmid,
        abstractText = "EGFR amplification was associated with response.",
        sourceApi = "europepmc",
        citationCount = 12,
        tldr = null,
    )

    private fun cohort(samples: Int? = 1100) = CohortStudy(
        studyId = "brca_tcga",
        name = "Breast TCGA",
        cancerType = "breast",
        description = "Pan-cancer atlas cohort",
        sampleCount = samples,
        pmid = "12345678",
        citation = "TCGA 2018",
        referenceGenome = "hg38",
        url = "https://cbioportal.org/study/brca_tcga",
    )

    private fun civic(
        significance: String = "Sensitivity/Response",
        therapies: List<String> = listOf("Erlotinib"),
        disease: String = "Non-Small Cell Lung Cancer",
        evidenceDirection: String = "Supports",
    ) = CuratedVariantEvidence(
        civicEvidenceId = 42,
        civicUrl = "https://civicdb.org/e/42",
        gene = "EGFR",
        variant = "AMPLIFICATION",
        variantTypes = listOf("amplification"),
        molecularProfile = "EGFR AMPLIFICATION",
        evidenceType = "Predictive",
        evidenceLevel = "A",
        evidenceDirection = evidenceDirection,
        significance = significance,
        evidenceRating = 5,
        disease = disease,
        therapies = therapies,
        description = "EGFR amplification confers sensitivity to erlotinib.",
    )

    private fun wetLab(verified: Boolean = true): WetLabRecord {
        val record = WetLabRecord(
            id = "wet-1",
            label = "ESR1 discordance, site 1",
            source = "lab-notebook-2026-10",
            experimentDate = "2026-10-01",
            operator = "tech-a",
            evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = 12,
            brainTotal = 40,
            otherEvents = 4,
            otherTotal = 40,
            notes = "matched tissue/plasma pairs",
        )
        return record.copy(verificationHash = if (verified) record.computeHash() else "deadbeef")
    }

    // --- literature ------------------------------------------------------------------------

    @Test
    fun aPaperBecomesAContentAddressedArtifact() {
        val result = LiteratureConnector.fromPaper(paper(), at)
        assertEquals(1, result.artifacts.size)
        assertEquals("doi:10.1000/xyz", result.artifacts[0].originUri)
        assertTrue(result.artifacts[0].artifactId.startsWith("art_"))
        assertTrue(result.problems.isEmpty())
    }

    @Test
    fun theSamePaperIsTheSameArtifact() {
        val a = LiteratureConnector.fromPaper(paper(), at).artifacts[0]
        val b = LiteratureConnector.fromPaper(paper(), at).artifacts[0]
        assertEquals(a.artifactId, b.artifactId)
    }

    @Test
    fun aPmidOnlyPaperStillHasAnOrigin() {
        val result = LiteratureConnector.fromPaper(paper(doi = "", pmid = "99887766"), at)
        assertEquals("pmid:99887766", result.artifacts[0].originUri)
        assertEquals("pmid:99887766", LiteratureConnector.evidenceRef(paper(doi = "", pmid = "99887766")).id)
        assertEquals(EvidenceKind.PAPER, LiteratureConnector.evidenceRef(paper()).kind)
    }

    // --- cohorts ---------------------------------------------------------------------------

    @Test
    fun aCohortBecomesAnUnpinnedDataset() {
        val result = CohortConnector.fromCohort(cohort())
        val dataset = result.datasets.single()
        assertEquals("cbioportal", dataset.source)
        assertEquals("brca_tcga", dataset.studyId)
        assertEquals(1100, dataset.sampleCount)
        // cBioPortal gives no content hash; the connector must not invent one.
        assertFalse(dataset.isPinned)
    }

    @Test
    fun aCohortWithNoSampleCountIsRefused() {
        val result = CohortConnector.fromCohort(cohort(samples = null))
        assertTrue(result.datasets.isEmpty())
        assertTrue(result.problems.any { it.code == "COHORT_NO_SAMPLES" })
    }

    // --- CIViC -----------------------------------------------------------------------------

    @Test
    fun aSensitivityItemBecomesASensitiveToEdge() {
        val result = CivicConnector.fromEvidence(civic(), at)
        val edge = result.edges.single()
        assertEquals(Predicate.SENSITIVE_TO, edge.predicate)
        assertEquals("gene:EGFR", edge.subject.id)
        assertEquals("drug:erlotinib", edge.objectRef.id)
        assertEquals(0.95, edge.score, 0.0001) // A + rating 5 nudge, clamped below 1.0
        assertEquals(1, edge.evidenceCount)
        assertEquals(1, result.lineage.size)
        assertEquals(edge.edgeId, result.lineage[0].derivedId)
    }

    @Test
    fun aResistanceItemIsNotASensitivityItem() {
        val result = CivicConnector.fromEvidence(civic(significance = "Resistance"), at)
        assertEquals(Predicate.RESISTANT_TO, result.edges.single().predicate)
    }

    @Test
    fun anAdverseResponseIsNotSensitivity() {
        // The bug that motivated this fix: "Adverse Response" contains "response", so the old
        // string check mapped it to SENSITIVE_TO. It is toxicity, not support.
        val result = CivicConnector.fromEvidence(civic(significance = "Adverse Response"), at)
        assertEquals(Predicate.ADVERSE_EFFECT, result.edges.single().predicate)
    }

    @Test
    fun aReducedSensitivityItemIsStillSensitivity() {
        val result = CivicConnector.fromEvidence(civic(significance = "Reduced Sensitivity"), at)
        assertEquals(Predicate.SENSITIVE_TO, result.edges.single().predicate)
    }

    @Test
    fun anNaItemIsRefused() {
        val result = CivicConnector.fromEvidence(civic(significance = "N/A"), at)
        assertTrue(result.edges.isEmpty())
        assertTrue(result.problems.any { it.code == "CIVIC_NOT_INFORMATIVE" })
    }

    @Test
    fun aDoesNotSupportItemIsDemoted() {
        val supports = CivicConnector.fromEvidence(civic(), at).edges.single()
        val refutes = CivicConnector.fromEvidence(
            civic(evidenceDirection = "Does Not Support"),
            at,
        ).edges.single()
        assertEquals(Predicate.SENSITIVE_TO, refutes.predicate)
        assertTrue("refuted score must be lower", refutes.score < supports.score)
    }

    @Test
    fun aTherapyLessItemFallsBackToTheDiseaseNode() {
        val result = CivicConnector.fromEvidence(
            civic(significance = "Pathogenic", therapies = emptyList(), disease = "Breast Cancer"),
            at,
        )
        val edge = result.edges.single()
        assertEquals(Predicate.ASSOCIATED_WITH, edge.predicate)
        assertEquals("disease:breast-cancer", edge.objectRef.id)
    }

    @Test
    fun anItemWithNoObjectIsRefused() {
        val result = CivicConnector.fromEvidence(
            civic(therapies = emptyList(), disease = ""),
            at,
        )
        assertTrue(result.edges.isEmpty())
        assertTrue(result.problems.any { it.code == "CIVIC_NO_OBJECT" })
    }

    @Test
    fun ingestingTheSameCivicItemTwiceDeduplicatesTheArtifactAndMergesTheEdge() {
        val store = InMemoryEvidenceStore()
        val first = ConnectorIngest.write(store, CivicConnector.fromEvidence(civic(), at))
        assertEquals(1, first.artifactsCreated)
        assertEquals(1, first.edgesCreated)
        assertEquals(1, first.lineageAdded)

        val second = ConnectorIngest.write(store, CivicConnector.fromEvidence(civic(), at))
        assertEquals(0, second.artifactsCreated)
        assertEquals(1, second.artifactsDeduplicated)
        assertEquals(0, second.edgesCreated)
        assertEquals(1, second.edgesMerged)
        assertEquals(0, second.lineageAdded)
        assertEquals(1, store.edges().size)
        assertEquals(1, store.artifacts().size)
    }

    // --- wet lab ---------------------------------------------------------------------------

    @Test
    fun aVerifiedWetLabRecordBecomesAnArtifact() {
        val result = WetLabConnector.fromRecord(wetLab(), at)
        assertEquals(1, result.artifacts.size)
        assertTrue(result.problems.isEmpty())
        assertEquals(EvidenceKind.WETLAB, WetLabConnector.evidenceRef(wetLab()).kind)
    }

    @Test
    fun anUnverifiedWetLabRecordIsRefused() {
        val result = WetLabConnector.fromRecord(wetLab(verified = false), at)
        assertTrue(result.artifacts.isEmpty())
        assertTrue(result.problems.any { it.code == "WETLAB_UNVERIFIED" })
    }

    @Test
    fun aWetLabRefIsStableAcrossReads() {
        assertEquals(WetLabConnector.evidenceRef(wetLab()).id, WetLabConnector.evidenceRef(wetLab()).id)
    }

    @Test
    fun anIngestedPaperIsStoredOnceAndCitable() {
        val store = InMemoryEvidenceStore()
        val summary = ConnectorIngest.write(store, LiteratureConnector.fromPaper(paper(), at))
        assertEquals(1, summary.artifactsCreated)
        assertNotNull(store.artifact(LiteratureConnector.fromPaper(paper(), at).artifacts[0].artifactId))
        assertEquals(1, store.artifacts().size)
    }

    @Test
    fun aReviewedHypothesisCanBeBuiltFromAConnectorEdge() {
        val store = InMemoryEvidenceStore()
        ConnectorIngest.write(store, CivicConnector.fromEvidence(civic(), at))
        val edge = store.edges().single()
        val hypothesis = com.example.concordance.evidence.Hypothesis(
            hypothesisId = com.example.concordance.evidence.Hypothesis.idFor("nsclc", "EGFR", "erlotinib", "v1"),
            cancerType = "nsclc",
            biomarkerSymbol = "EGFR",
            biomarkerType = "amplification",
            candidateDrug = "erlotinib",
            drugRxnorm = "41493",
            rationale = "curated sensitivity evidence",
            supportingEdgeIds = listOf(edge.edgeId),
            pubmedIds = emptyList(),
            datasetIds = emptyList(),
            wetlabIds = emptyList(),
            rankScore = 0.8,
            modelVersion = "v1",
            generatedAtUtc = at,
            reviewStatus = ReviewStatus.UNREVIEWED,
        )
        val storedHypothesis = com.example.concordance.evidence.EvidenceRules
            .validateHypothesis(hypothesis, setOf(edge.edgeId))
        assertTrue(storedHypothesis.toString(), storedHypothesis.isEmpty())
    }
}

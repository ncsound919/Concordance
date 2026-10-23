package com.example

import com.example.concordance.engine.CapsuleEngine
import com.example.concordance.engine.StatisticalEngine
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import com.example.concordance.model.Verdict
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    private val giannoudis = Anchor(
        id = "giannoudis2024_pooled",
        label = "Giannoudis 2024 pooled (Foundation Medicine)",
        source = "Giannoudis 2024",
        evidenceGrade = EvidenceGrade.COUNTS,
        brainEvents = 53,
        brainTotal = 822,
        otherEvents = 3220,
        otherTotal = 15516
    )

    private val annalsMeta = Anchor(
        id = "annals_106P_meta",
        label = "Annals 106P meta",
        source = "Annals of Oncology",
        evidenceGrade = EvidenceGrade.ABSTRACT_ONLY,
        oddsRatio = 0.35,
        orCiLow = 0.24,
        orCiHigh = 0.52,
        referencePrevalence = 3220.0 / 15516.0
    )

    @Test
    fun testObservedMatchesRegister() {
        val (est, lo, hi) = StatisticalEngine.observedOrCi(giannoudis)
        assertEquals(0.263, est, 0.005)
        val p = StatisticalEngine.fisherP(giannoudis)
        assertNotNull(p)
        assertTrue(p!! < 1e-20)
        val (pb, pn) = StatisticalEngine.observedPrevalences(giannoudis)
        assertEquals(0.0645, pb, 0.001)
        assertEquals(0.2075, pn, 0.001)
    }

    @Test
    fun testNullCrossingR() {
        val rStar = StatisticalEngine.nullCrossingR(giannoudis)
        assertEquals(0.3107, rStar, 0.002)

        val correctedAtNull = StatisticalEngine.corrected(giannoudis, rStar, 0.90)
        assertTrue(correctedAtNull.feasible)
        assertEquals(1.0, correctedAtNull.oddsRatio!!, 0.0001)
    }

    @Test
    fun testSignificanceThreshold() {
        val rSig = StatisticalEngine.significanceThresholdR(giannoudis, 0.90)
        assertNotNull(rSig)
        assertTrue(rSig!! in 0.39..0.41)
    }

    @Test
    fun testVerdictWithheldOnAbstractOnly() {
        val adj = StatisticalEngine.adjudicate(annalsMeta, 0.90, 0.70, 0.90)
        assertEquals(Verdict.WITHHELD, adj.verdict)
    }

    @Test
    fun testVerdictOutcomesOnCounts() {
        // Without stated range: threshold_only
        val adj1 = StatisticalEngine.adjudicate(giannoudis, 0.90, null, null)
        assertEquals(Verdict.THRESHOLD_ONLY, adj1.verdict)

        // Across high range (0.80 - 1.00): supported
        val adj2 = StatisticalEngine.adjudicate(giannoudis, 0.90, 0.80, 1.00)
        assertEquals(Verdict.SUPPORTED, adj2.verdict)

        // In discordance range (0.31 - 0.38): not_supported (artifact explains it)
        val adj3 = StatisticalEngine.adjudicate(giannoudis, 0.90, 0.31, 0.38)
        assertEquals(Verdict.NOT_SUPPORTED, adj3.verdict)
    }

    @Test
    fun testCapsuleTamperVerification() {
        val adj = StatisticalEngine.adjudicate(giannoudis, 0.90, 0.80, 1.00)
        val capsule = CapsuleEngine.buildCapsule(adj)

        // Pristine verification
        val (isValid1, msg1, _) = CapsuleEngine.verifyJson(capsule.rawJson)
        assertTrue("Capsule should be valid", isValid1)

        // Modify payload to simulate tampering
        val tamperedJson = capsule.rawJson.replace("\"verdict\": \"supported\"", "\"verdict\": \"not_supported\"")
        val (isValid2, _, _) = CapsuleEngine.verifyJson(tamperedJson)
        assertFalse("Tampered capsule must fail verification", isValid2)
    }

    @Test
    fun testPowerRequirementMatchesTheory() {
        val req = StatisticalEngine.requiredSample(giannoudis, rTrue = 0.60, sn = 0.90, alpha = 0.05, power = 0.80)
        assertTrue(req.truePositivesPerSite >= 10)
        assertTrue(req.patientsBrain > req.truePositivesPerSite)
        assertTrue(req.patientsNonCns >= req.truePositivesPerSite)
    }

    @Test
    fun testLocalModelExtraction() {
        val abstractSample = "We evaluated ESR1 Y537S mutations in brain metastases (53 of 822) compared to non-CNS sites (3220 of 15516). Tissue and plasma assays revealed tissue-plasma discordance with negative plasma."
        val extraction = com.example.concordance.engine.LocalModelEngine.analyzeText(abstractSample)

        assertEquals("ESR1", extraction.gene)
        assertEquals("Y537S", extraction.variant)
        assertEquals(53, extraction.brainEvents)
        assertEquals(822, extraction.brainTotal)
        assertEquals(3220, extraction.otherEvents)
        assertEquals(15516, extraction.otherTotal)
        assertEquals(EvidenceGrade.COUNTS, extraction.evidenceGrade)
        assertTrue(extraction.qualitativeSummary.contains("discordance", ignoreCase = true))
    }

    @Test
    fun testApiRotationStrategy() {
        com.example.concordance.integration.rotation.ApiRotationManager.setStrategy(
            com.example.concordance.integration.rotation.RotationStrategy.ROUND_ROBIN
        )
        val p1 = com.example.concordance.integration.rotation.ApiRotationManager.getNextProvider(
            com.example.concordance.integration.rotation.ProviderCategory.AI_MODEL
        )
        assertNotNull(p1)

        com.example.concordance.integration.rotation.ApiRotationManager.recordSuccess(p1.id, 50)
        assertEquals(com.example.concordance.integration.rotation.ProviderStatus.HEALTHY, p1.status)
    }

    @Test
    fun testKaggleDatasetIngestion() {
        val ds = com.example.concordance.integration.kaggle.KaggleComputeManager.searchDatasets("cBioPortal")
        assertTrue(ds.isNotEmpty())
        assertEquals("cbioportal/breast-cancer-met-tropism", ds.first().ref)

        val (rows, errors) = com.example.concordance.engine.PairedAnalyzer.parseCsv(ds.first().csvData)
        assertTrue(errors.isEmpty())
        assertTrue(rows.isNotEmpty())
    }

    @Test
    fun testPubChemCompounds() {
        val elacestrant = com.example.concordance.integration.pubchem.PubChemManager.searchCompounds("Elacestrant")
        assertTrue(elacestrant.isNotEmpty())
        assertEquals(54726192L, elacestrant.first().cid)
        assertEquals("C30H38N2O2", elacestrant.first().molecularFormula)
        assertTrue(elacestrant.first().associatedMutations.contains("ESR1 Y537S"))
    }

    @Test
    fun testClinicalTrialsStudies() {
        val trials = com.example.concordance.integration.clinicaltrials.ClinicalTrialsManager.searchStudies("EMERALD")
        assertTrue(trials.isNotEmpty())
        assertEquals("NCT03778931", trials.first().nctId)
        assertTrue(trials.first().esr1EndpointTracking.contains("Guardant360"))
    }

    @Test
    fun testNewLiteratureProvidersRegistered() {
        val providers = com.example.concordance.integration.rotation.ApiRotationManager.providers.value
        val providerIds = providers.map { it.id }
        assertTrue(providerIds.contains("arxiv_api"))
        assertTrue(providerIds.contains("pubchem_pug"))
        assertTrue(providerIds.contains("crossref_api"))
        assertTrue(providerIds.contains("openalex_api"))
        assertTrue(providerIds.contains("clinical_trials"))
    }
}


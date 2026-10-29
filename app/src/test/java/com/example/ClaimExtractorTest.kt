package com.example

import com.example.concordance.research.deep.ClaimExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaimExtractorTest {

    private val abstract =
        "In a Phase II study of 42 patients with glioblastoma, EGFR amplification predicted " +
            "response to erlotinib (PFS, p = 0.001). The hazard ratio was 0.42 (95% CI 0.21-0.84). " +
            "ORR was 31%. Patients with EGFR wild-type disease did not benefit."

    @Test
    fun anEmptyAbstractYieldsAnEmptyClaim() {
        val claim = ClaimExtractor.extract("")
        assertTrue(claim.isEmpty)
        assertEquals(0.0, claim.confidence, 0.0)
    }

    @Test
    fun aBlankAbstractYieldsAnEmptyClaim() {
        assertTrue(ClaimExtractor.extract("   \n  ").isEmpty)
    }

    @Test
    fun entitiesAreExtractedFromATypicalAbstract() {
        val claim = ClaimExtractor.extract(abstract)
        assertTrue("EGFR", claim.genes.contains("EGFR"))
        assertTrue("erlotinib", claim.drugs.contains("ERLOTINIB"))
        assertTrue("glioblastoma", claim.diseases.contains("GLIOBLASTOMA"))
        assertTrue("PFS", claim.endpoints.contains("PFS"))
        assertTrue("ORR", claim.endpoints.contains("ORR"))
        assertEquals(0.001, claim.pValues[0], 0.0)
        assertTrue(claim.sampleSizes.contains(42))
        assertTrue(claim.hazardRatios.contains(0.42))
    }

    @Test
    fun aGeneIsNotMatchedAsASubstring() {
        // "MYC" must not be found inside "MYOGENIC" or similar; the regex uses word boundaries.
        val claim = ClaimExtractor.extract("The MYOGENIC pathway was studied in 10 patients.")
        assertFalse(claim.genes.contains("MYC"))
    }

    @Test
    fun confidenceReflectsHowManyEntityKindsWereFound() {
        val full = ClaimExtractor.extract(abstract)
        assertEquals(1.0, full.confidence, 0.0)
        val partial = ClaimExtractor.extract("EGFR was studied in 10 patients.")
        assertTrue(partial.confidence in 0.0..1.0)
        assertTrue(partial.confidence < full.confidence)
    }

    @Test
    fun pValuesAreSortedAndDeduplicated() {
        val claim = ClaimExtractor.extract("p = 0.05 and p = 0.01 and p = 0.05 again")
        assertEquals(listOf(0.01, 0.05), claim.pValues)
    }

    @Test
    fun sampleSizeIsPickedUpFromNPatients() {
        val claim = ClaimExtractor.extract("We enrolled 128 patients with NSCLC.")
        assertTrue(claim.sampleSizes.contains(128))
        assertTrue(claim.diseases.contains("NSCLC"))
    }

    @Test
    fun anOddsRatioIsExtracted() {
        val claim = ClaimExtractor.extract("The odds ratio was 2.43 (95% CI 1.1-5.2).")
        assertEquals(listOf(2.43), claim.oddsRatios)
    }
}

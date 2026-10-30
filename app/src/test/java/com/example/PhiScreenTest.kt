package com.example

import com.example.concordance.data.CapsuleEntity
import com.example.concordance.data.ChallengeEntity
import com.example.concordance.data.ClaimEntity
import com.example.concordance.integration.supabase.PhiScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sync boundary refuses records whose free text looks like it carries an
 * identifier. False positives only cost a sync; false negatives break the
 * PHI-never-leaves-device promise, so the patterns err toward blocking.
 */
class PhiScreenTest {

    private fun claim(label: String, notes: String = "n") = ClaimEntity(
        id = "c1", label = label, source = "source", evidenceGrade = "COUNTS",
        brainEvents = 1, brainTotal = 10, otherEvents = 2, otherTotal = 100,
        oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
        denominator = "samples", notes = notes, isCustom = false, dateAdded = "2026-10-18"
    )

    private fun capsule(citableArgument: String) = CapsuleEntity(
        id = "k1", permalink = "https://example.org/k1", capsuleVersion = "1",
        softwareVersion = "1", createdUtc = "2026-10-19T00:00:00Z",
        sha256 = "a".repeat(64), question = "Q", anchorLabel = "A", verdict = "supported",
        citableArgument = citableArgument, plainLanguageVerdict = "plain",
        rNull = 0.2, rSignificance = 0.3, snAssumed = 0.9, rLow = 0.3, rHigh = 0.9,
        observedOr = 0.68, rawJson = "{}", markdown = "#"
    )

    private fun challenge(notes: String) = ChallengeEntity(
        id = "ch1", claimId = "c1", author = "jane", date = "2026-10-19", notes = notes,
        updatedBrainEvents = 1, updatedBrainTotal = 10, updatedOtherEvents = 2,
        updatedOtherTotal = 100
    )

    // ---------------------------------------------------------------------
    // Text patterns
    // ---------------------------------------------------------------------

    @Test
    fun ssnIsBlocked() {
        assertTrue(PhiScreen.containsLikelyIdentifier("sample from 123-45-6789"))
    }

    @Test
    fun medicalRecordNumberLabelIsBlocked() {
        assertTrue(PhiScreen.containsLikelyIdentifier("MRN 482913"))
        assertTrue(PhiScreen.containsLikelyIdentifier("Medical record number: 99211"))
    }

    @Test
    fun dateOfBirthLabelIsBlocked() {
        assertTrue(PhiScreen.containsLikelyIdentifier("DOB 1980-04-12"))
        assertTrue(PhiScreen.containsLikelyIdentifier("date of birth: 1980-04-12"))
    }

    @Test
    fun emailAddressIsBlocked() {
        assertTrue(PhiScreen.containsLikelyIdentifier("reach jane.doe@hospital.org"))
    }

    @Test
    fun phoneNumberIsBlocked() {
        assertTrue(PhiScreen.containsLikelyIdentifier("call (555) 123-4567"))
        assertTrue(PhiScreen.containsLikelyIdentifier("call 555-123-4567"))
        assertTrue(PhiScreen.containsLikelyIdentifier("call +1 555 123 4567"))
    }

    @Test
    fun patientNameReferenceIsBlocked() {
        assertTrue(PhiScreen.containsLikelyIdentifier("Patient Smith consented"))
    }

    @Test
    fun ordinaryScientificTextIsAllowed() {
        assertFalse(PhiScreen.containsLikelyIdentifier("EGFR L858R in brain metastases"))
        assertFalse(PhiScreen.containsLikelyIdentifier("odds ratio 0.68 (95% CI 0.41-0.99)"))
        assertFalse(PhiScreen.containsLikelyIdentifier("counts per 100 samples, p < 0.05"))
        assertFalse(PhiScreen.containsLikelyIdentifier(null))
        assertFalse(PhiScreen.containsLikelyIdentifier(""))
    }

    // ---------------------------------------------------------------------
    // Entity-level checks
    // ---------------------------------------------------------------------

    @Test
    fun claimLabelIsChecked() {
        assertFalse(PhiScreen.claimBlocked(claim(label = "Brain depletion claim")))
        assertTrue(PhiScreen.claimBlocked(claim(label = "MRN 12345 case")))
    }

    @Test
    fun claimNotesAreNotConsideredBecauseNotesNeverUpload() {
        // Notes are stripped from the payload entirely, so a claim whose only
        // identifier-bearing text is in notes may still sync.
        assertFalse(PhiScreen.claimBlocked(claim(label = "Claim", notes = "SSN 123-45-6789")))
    }

    @Test
    fun capsuleFreeTextIsChecked() {
        assertFalse(PhiScreen.capsuleBlocked(capsule(citableArgument = "Depletion survives correction")))
        assertTrue(PhiScreen.capsuleBlocked(capsule(citableArgument = "as shown for patient Jane Doe")))
    }

    @Test
    fun challengeNotesAreChecked() {
        // A challenge without its argument is empty, so its notes gate the push.
        assertFalse(PhiScreen.challengeBlocked(challenge(notes = "counts look off")))
        assertTrue(PhiScreen.challengeBlocked(challenge(notes = "contact jane@example.com")))
    }
}

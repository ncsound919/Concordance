package com.example.concordance.integration.supabase

import com.example.concordance.data.CapsuleEntity
import com.example.concordance.data.ChallengeEntity
import com.example.concordance.data.ClaimEntity

/**
 * Boundary screen for identifiers in free text before anything is pushed to
 * Supabase.
 *
 * The app's contract is that PHI-class content never leaves the device, and
 * the server enforces data_class='aggregate' on insert, so a record whose
 * remaining free text looks like it carries an identifier is refused at the
 * boundary: it is not uploaded, not marked synced, and is reported through
 * [SupabaseSyncManager.phiBlocked]. A false positive only costs a sync; a
 * false negative would break the never-leaves-device promise, so the patterns
 * err toward blocking.
 */
object PhiScreen {

    // US SSN: 123-45-6789.
    private val ssn = Regex("""\b\d{3}-\d{2}-\d{4}\b""")

    // Medical-record-number labels.
    private val mrn = Regex("""\b(?:MRN|medical record number)\b""", RegexOption.IGNORE_CASE)

    // Date of birth, written out.
    private val dob = Regex("""\b(?:DOB|date of birth)\b\s*:?\s*\S""", RegexOption.IGNORE_CASE)

    // Email addresses.
    private val email = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")

    // US-style phone numbers: (555) 123-4567, 555-123-4567, +1 555 123 4567.
    private val phone = Regex("""\+?\d{0,2}[-. (]*\d{3}[-. )]*\d{3}[-. ]*\d{4}\b""")

    // "patient Smith" style references.
    private val patient = Regex("""\bpatient\s+[A-Za-z]""", RegexOption.IGNORE_CASE)

    private val patterns = listOf(ssn, mrn, dob, email, phone, patient)

    /** True when the text looks like it carries an identifier. */
    fun containsLikelyIdentifier(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return patterns.any { it.containsMatchIn(text) }
    }

    /** The claim fields that still sync (notes never upload) plus the label. */
    fun claimBlocked(c: ClaimEntity): Boolean = containsLikelyIdentifier(c.label)

    /** The capsule fields that upload and carry free text. */
    fun capsuleBlocked(c: CapsuleEntity): Boolean =
        containsLikelyIdentifier(c.question) ||
            containsLikelyIdentifier(c.citableArgument) ||
            containsLikelyIdentifier(c.plainLanguageVerdict) ||
            containsLikelyIdentifier(c.markdown) ||
            containsLikelyIdentifier(c.rawJson)

    /** The challenge's own text: a challenge without its argument is empty. */
    fun challengeBlocked(c: ChallengeEntity): Boolean = containsLikelyIdentifier(c.notes)
}

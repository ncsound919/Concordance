package com.example.concordance.integration.wetlab

import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import java.security.MessageDigest
import java.util.UUID

/**
 * Wet-lab data record: a single experimental observation (2x2 counts or odds ratio)
 * with full provenance for verifiability.
 */
data class WetLabRecord(
    val id: String = UUID.randomUUID().toString().take(12),
    val label: String,
    val source: String,
    val experimentDate: String,
    val operator: String,
    val evidenceGrade: EvidenceGrade,
    val brainEvents: Int? = null,
    val brainTotal: Int? = null,
    val otherEvents: Int? = null,
    val otherTotal: Int? = null,
    val oddsRatio: Double? = null,
    val orCiLow: Double? = null,
    val orCiHigh: Double? = null,
    val referencePrevalence: Double? = null,
    val assayType: String = "unknown",
    val tissueType: String = "unknown",
    val notes: String = "",
    val verificationHash: String = ""
) {
    /** Compute a deterministic SHA-256 over the record's data fields. */
    fun computeHash(): String {
        val canonical = buildString {
            append(label); append("|"); append(source); append("|")
            append(experimentDate); append("|"); append(operator); append("|")
            append(evidenceGrade.name); append("|")
            append(brainEvents); append("|"); append(brainTotal); append("|")
            append(otherEvents); append("|"); append(otherTotal); append("|")
            append(oddsRatio); append("|"); append(orCiLow); append("|"); append(orCiHigh); append("|")
            append(referencePrevalence); append("|"); append(assayType); append("|"); append(tissueType)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Verify the record's hash matches its data. */
    fun isVerified(): Boolean = verificationHash.isNotBlank() && verificationHash == computeHash()

    /** Convert to an Anchor for adjudication. */
    fun toAnchor(): Anchor = Anchor(
        id = id,
        label = label,
        source = "WetLab: $source",
        evidenceGrade = evidenceGrade,
        brainEvents = brainEvents,
        brainTotal = brainTotal,
        otherEvents = otherEvents,
        otherTotal = otherTotal,
        oddsRatio = oddsRatio,
        orCiLow = orCiLow,
        orCiHigh = orCiHigh,
        referencePrevalence = referencePrevalence ?: (if (otherTotal != null && otherTotal!! > 0 && otherEvents != null) otherEvents!!.toDouble() / otherTotal!! else null),
        notes = notes
    )
}

/**
 * Ingester for wet-lab data: validates, hashes, and converts records to anchors.
 * All records are verified (hash check) before conversion.
 */
object WetLabDataIngester {

    data class IngestResult(
        val accepted: List<WetLabRecord>,
        val rejected: List<Pair<WetLabRecord, String>>,
        val anchors: List<Anchor>
    )

    /** Validate a record: counts must be non-negative, totals >= events, etc. */
    fun validate(record: WetLabRecord): String? {
        if (record.label.isBlank()) return "Missing label"
        if (record.source.isBlank()) return "Missing source"
        if (record.experimentDate.isBlank()) return "Missing experiment date"
        if (record.brainEvents != null && record.brainTotal != null) {
            if (record.brainEvents < 0 || record.brainTotal < 0) return "Negative counts"
            if (record.brainEvents > record.brainTotal) return "Brain events exceed total"
        }
        if (record.otherEvents != null && record.otherTotal != null) {
            if (record.otherEvents < 0 || record.otherTotal < 0) return "Negative counts"
            if (record.otherEvents > record.otherTotal) return "Other events exceed total"
        }
        if (record.oddsRatio != null && record.oddsRatio <= 0) return "Odds ratio must be positive"
        if (record.orCiLow != null && record.orCiHigh != null && record.orCiLow!! > record.orCiHigh!!) return "CI low > CI high"
        return null
    }

    /** Ingest a batch of records: validate, hash, convert to anchors. */
    fun ingest(records: List<WetLabRecord>): IngestResult {
        val accepted = mutableListOf<WetLabRecord>()
        val rejected = mutableListOf<Pair<WetLabRecord, String>>()
        val anchors = mutableListOf<Anchor>()

        for (record in records) {
            val error = validate(record)
            if (error != null) {
                rejected.add(record to error)
                continue
            }
            if (record.verificationHash.isBlank()) {
                // New record: compute and set hash
                val hashed = record.copy(verificationHash = record.computeHash())
                accepted.add(hashed)
                anchors.add(hashed.toAnchor())
            } else {
                // Existing record: verify hash matches data
                if (!record.isVerified()) {
                    rejected.add(record to "Hash verification failed")
                    continue
                }
                accepted.add(record)
                anchors.add(record.toAnchor())
            }
        }

        return IngestResult(accepted = accepted, rejected = rejected, anchors = anchors)
    }

    /** Verify a batch of records (all must pass validation + hash check). */
    fun verifyAll(records: List<WetLabRecord>): Boolean =
        records.all { validate(it) == null && it.isVerified() }
}

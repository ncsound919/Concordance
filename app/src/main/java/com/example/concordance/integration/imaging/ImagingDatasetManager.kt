package com.example.concordance.integration.imaging

import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import java.security.MessageDigest
import java.util.UUID

/**
 * Manages imaging-derived datasets: table extraction results converted to
 * verifiable anchor candidates. Each candidate carries a verification hash
 * over its extracted counts and provenance.
 */
data class ImagingDataset(
    val id: String = UUID.randomUUID().toString().take(12),
    val label: String,
    val sourceImage: String,
    val extractionEngine: String,
    val extractionVersion: String,
    val candidate: ExtractionCandidate,
    val verificationHash: String = ""
) {
    fun computeHash(): String {
        val canonical = buildString {
            append(label); append("|"); append(sourceImage); append("|")
            append(extractionEngine); append("|"); append(extractionVersion); append("|")
            append(candidate.status); append("|")
            append(candidate.counts?.joinToString(",") ?: "null")
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun isVerified(): Boolean = verificationHash.isNotBlank() && verificationHash == computeHash()

    /** Convert a verified candidate to an Anchor (only if status allows). */
    fun toAnchor(): Anchor? {
        val counts = candidate.counts ?: return null
        if (counts.size < 4) return null
        if (candidate.status != "needs_review") return null
        return Anchor(
            id = id,
            label = label,
            source = "Imaging: $sourceImage (engine: $extractionEngine v$extractionVersion)",
            evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = counts[0],
            brainTotal = counts[1],
            otherEvents = counts[2],
            otherTotal = counts[3],
            notes = "Extracted from imaging table. Reasons: ${candidate.reasons.joinToString()}"
        )
    }
}

object ImagingDatasetManager {

    data class IngestResult(
        val accepted: List<ImagingDataset>,
        val rejected: List<Pair<ImagingDataset, String>>,
        val anchors: List<Anchor>
    )

    fun validate(dataset: ImagingDataset): String? {
        if (dataset.label.isBlank()) return "Missing label"
        if (dataset.sourceImage.isBlank()) return "Missing source image"
        if (dataset.candidate.status != "needs_review") return "Candidate status is '${dataset.candidate.status}', not 'needs_review'"
        if (dataset.candidate.counts == null) return "No extracted counts"
        if (dataset.candidate.counts.size < 4) return "Incomplete counts (need 4 values)"
        val c = dataset.candidate.counts
        if (c[0] < 0 || c[1] < 0 || c[2] < 0 || c[3] < 0) return "Negative counts"
        if (c[0] > c[1] || c[2] > c[3]) return "Events exceed totals"
        return null
    }

    fun ingest(datasets: List<ImagingDataset>): IngestResult {
        val accepted = mutableListOf<ImagingDataset>()
        val rejected = mutableListOf<Pair<ImagingDataset, String>>()
        val anchors = mutableListOf<Anchor>()

        for (ds in datasets) {
            val error = validate(ds)
            if (error != null) {
                rejected.add(ds to error)
                continue
            }
            if (ds.verificationHash.isBlank()) {
                val hashed = ds.copy(verificationHash = ds.computeHash())
                accepted.add(hashed)
                hashed.toAnchor()?.let { anchors.add(it) }
            } else {
                if (!ds.isVerified()) {
                    rejected.add(ds to "Hash verification failed")
                    continue
                }
                accepted.add(ds)
                ds.toAnchor()?.let { anchors.add(it) }
            }
        }

        return IngestResult(accepted = accepted, rejected = rejected, anchors = anchors)
    }
}

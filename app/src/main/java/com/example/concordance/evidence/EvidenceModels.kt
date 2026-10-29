package com.example.concordance.evidence

import com.example.concordance.research.experiment.DataAccess
import com.example.concordance.research.experiment.DataClass

/**
 * The evidence backbone: immutable source artifacts, lineage, dataset records, cross-layer edges
 * and the hypotheses that are assembled from them.
 *
 * This is the phone-sized subset of the relational schema in the platform design. What changed in
 * translation:
 *
 * - PostgreSQL -> Room/SQLite (structured) + D1 (mirror) + R2 (bytes).
 * - `VECTOR(768)` -> sqlite-vec, added when the corpus is actually queried.
 * - `content_hash UNIQUE` -> the id **is** the hash prefix, so dedup and idempotency are the same
 *   mechanism rather than a constraint someone can forget.
 * - `kg_edge.evidence_count` -> derived from the merged evidence list, never stored separately.
 *
 * The guardrails are the point: a hypothesis with no supporting edge cannot exist, and a
 * hypothesis cannot reach [ReviewStatus.REVIEWED] without a named reviewer.
 */

enum class ArtifactType {
    OCR_SCAN,
    WETLAB_FILE,
    OMICS_DATASET,
    PAPER_PDF,
    PAPER_XML,
    API_RECORD,
}

/**
 * One ingested artifact. Immutable, content-addressed: the id is derived from the hash, so
 * re-ingesting the same bytes is a no-op rather than a duplicate row.
 */
data class SourceArtifact(
    val artifactId: String,
    val type: ArtifactType,
    val originUri: String,
    val contentHash: String,
    val mimeType: String,
    val sizeBytes: Long,
    val ingestedAtUtc: String,
    val ingestionRunId: String? = null,
) {
    companion object {
        fun create(
            contentHash: String,
            type: ArtifactType,
            originUri: String,
            mimeType: String,
            sizeBytes: Long,
            ingestedAtUtc: String,
            ingestionRunId: String? = null,
        ) = SourceArtifact(
            artifactId = idFor(contentHash),
            type = type,
            originUri = originUri,
            contentHash = contentHash.trim().lowercase(),
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            ingestedAtUtc = ingestedAtUtc,
            ingestionRunId = ingestionRunId,
        )

        fun idFor(contentHash: String): String = "art_" + contentHash.trim().lowercase().take(16)
    }
}

/** What kind of record a lineage edge points at. */
enum class DerivedKind {
    CLAIM,
    CAPSULE,
    DATASET,
    OMICS_FEATURE,
    KG_EDGE,
    HYPOTHESIS,
    WETLAB_MEASUREMENT,
    RUN_PLAN,
}

/**
 * "This derived record came from this artifact, via this transform."
 *
 * Every derived table in the platform schema has one of these; here it is a single table rather
 * than a column on every entity, so a new derived type needs no migration.
 */
data class LineageEdge(
    val derivedKind: DerivedKind,
    val derivedId: String,
    val artifactId: String,
    val transform: String,
    val transformVersion: String,
    val createdAtUtc: String,
)

/**
 * A dataset as a first-class, pinnable record rather than a string in a spec.
 *
 * [checksum] is the content hash the spec's `DatasetRef.sha256` must match; an empty checksum is
 * an unpinned dataset, which is allowed to exist but cannot be attached to a confirmatory run.
 */
data class DatasetRecord(
    val datasetId: String,
    val source: String,
    val studyId: String,
    val cancerType: String,
    val assayType: String,
    val sampleCount: Int,
    val checksum: String,
    val license: String,
    val access: DataAccess,
    val dataClass: DataClass,
) {
    val isPinned: Boolean get() = checksum.isNotBlank()

    companion object {
        fun create(
            source: String,
            studyId: String,
            cancerType: String,
            assayType: String,
            sampleCount: Int,
            checksum: String,
            license: String,
            access: DataAccess = DataAccess.PUBLIC,
            dataClass: DataClass = DataClass.AGGREGATE,
        ) = DatasetRecord(
            datasetId = idFor(source, studyId, checksum),
            source = source,
            studyId = studyId,
            cancerType = cancerType,
            assayType = assayType,
            sampleCount = sampleCount,
            checksum = checksum,
            license = license,
            access = access,
            dataClass = dataClass,
        )

        fun idFor(source: String, studyId: String, checksum: String): String =
            "ds_" + EvidenceHash.sha256("$source|$studyId|${checksum.trim().lowercase()}").take(16)
    }
}

/** A node in the cross-layer graph. [label] is for display only. */
data class NodeRef(
    val kind: NodeKind,
    val id: String,
    val label: String,
)

enum class NodeKind {
    GENE,
    DRUG,
    CANCER_TYPE,
    BIOMARKER,
    PATHWAY,
    CELL_LINE,
    DATASET,
    PAPER,
    CLAIM,
}

enum class Predicate(val wire: String) {
    SENSITIVE_TO("sensitive_to"),
    RESISTANT_TO("resistant_to"),
    ADVERSE_EFFECT("adverse_effect"),
    ASSOCIATED_WITH("associated_with"),
    BIOMARKER_FOR("biomarker_for"),
    REPURPOSES("repurposes"),
    EXPRESSED_IN("expressed_in"),
}

/** What kind of record an evidence reference points at. Distinct from [DerivedKind]. */
enum class EvidenceKind {
    ARTIFACT,
    DATASET,
    PAPER,
    WETLAB,
    CLAIM,
    CAPSULE,
    RUN,
}

/** A pointer to the evidence behind an edge. Deliberately closed: no free-text citations. */
data class EvidenceRef(val kind: EvidenceKind, val id: String)

/**
 * A drug-gene-response (or similar) edge with its evidence.
 *
 * The id is derived from subject/predicate/object only. Two teams adding the same edge merge
 * their evidence instead of inflating the count - which is what keeps `evidenceCount` honest.
 */
data class KgEdge(
    val edgeId: String,
    val subject: NodeRef,
    val predicate: Predicate,
    val objectRef: NodeRef,
    val evidence: List<EvidenceRef>,
    val score: Double,
    val createdBy: String,
    val createdAtUtc: String,
) {
    val evidenceCount: Int get() = evidence.size

    companion object {
        fun idFor(subject: NodeRef, predicate: Predicate, objectRef: NodeRef): String =
            "kg_" + EvidenceHash.sha256(
                "${subject.kind}|${subject.id}|${predicate.wire}|${objectRef.kind}|${objectRef.id}"
            ).take(16)
    }
}

enum class ReviewStatus {
    UNREVIEWED,
    IN_REVIEW,
    REVIEWED,
    REJECTED,
}

/**
 * The output of the pipeline: a ranked, auditable candidate.
 *
 * It is never a treatment recommendation. [reviewStatus] gates publication, and [modelVersion]
 * ties the claim to the exact generator so a regression is diagnosable.
 */
data class Hypothesis(
    val hypothesisId: String,
    val cancerType: String,
    val biomarkerSymbol: String,
    val biomarkerType: String,
    val candidateDrug: String,
    val drugRxnorm: String,
    val rationale: String,
    val supportingEdgeIds: List<String>,
    val pubmedIds: List<String>,
    val datasetIds: List<String>,
    val wetlabIds: List<String>,
    val rankScore: Double,
    val modelVersion: String,
    val generatedAtUtc: String,
    val reviewStatus: ReviewStatus = ReviewStatus.UNREVIEWED,
    val reviewer: String? = null,
    val reviewedAtUtc: String? = null,
) {
    /** Only a reviewed hypothesis with a named human may leave the device. */
    val isPublishable: Boolean
        get() = reviewStatus == ReviewStatus.REVIEWED && !reviewer.isNullOrBlank()

    companion object {
        fun idFor(cancerType: String, biomarkerSymbol: String, candidateDrug: String, modelVersion: String): String =
            "hyp_" + EvidenceHash.sha256(
                "${cancerType.trim().lowercase()}|${biomarkerSymbol.trim().uppercase()}|" +
                    "${candidateDrug.trim().lowercase()}|${modelVersion.trim()}"
            ).take(16)
    }
}

object EvidenceHash {
    fun sha256(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

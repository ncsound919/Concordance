package com.example.concordance.evidence

/**
 * The rules that make the evidence graph trustworthy.
 *
 * They are deliberately few and absolute: an edge with no evidence is not an edge, a hypothesis
 * with no edge is not a hypothesis, and nothing reaches [ReviewStatus.REVIEWED] without a named
 * human. A refusal is a result, as everywhere else in this app.
 */
object EvidenceRules {

    fun validateArtifact(artifact: SourceArtifact): List<EvidenceProblem> = buildList {
        if (artifact.type == ArtifactType.API_RECORD && artifact.originUri.isBlank()) {
            add(EvidenceProblem("ARTIFACT_ORIGIN", "an API record needs an origin uri"))
        }
        if (!isSha256(artifact.contentHash)) {
            add(EvidenceProblem("ARTIFACT_HASH", "contentHash must be a 64-character sha256"))
        }
        if (artifact.originUri.isBlank()) add(EvidenceProblem("ARTIFACT_ORIGIN", "originUri is empty"))
        if (artifact.mimeType.isBlank()) add(EvidenceProblem("ARTIFACT_MIME", "mimeType is empty"))
        if (artifact.sizeBytes < 0) add(EvidenceProblem("ARTIFACT_SIZE", "sizeBytes is negative"))
        if (artifact.ingestedAtUtc.isBlank()) {
            add(EvidenceProblem("ARTIFACT_TIME", "ingestedAtUtc is empty"))
        }
        if (artifact.artifactId != SourceArtifact.idFor(artifact.contentHash)) {
            add(EvidenceProblem("ARTIFACT_ID", "artifactId is not derived from contentHash"))
        }
    }

    fun validateDataset(dataset: DatasetRecord): List<EvidenceProblem> = buildList {
        if (dataset.source.isBlank()) add(EvidenceProblem("DATASET_SOURCE", "source is empty"))
        if (dataset.studyId.isBlank()) add(EvidenceProblem("DATASET_STUDY", "studyId is empty"))
        if (dataset.license.isBlank()) add(EvidenceProblem("DATASET_LICENSE", "license is empty"))
        if (dataset.sampleCount <= 0) add(EvidenceProblem("DATASET_SAMPLES", "sampleCount must be > 0"))
        if (dataset.checksum.isNotBlank() && !isSha256(dataset.checksum)) {
            add(EvidenceProblem("DATASET_CHECKSUM", "checksum must be a 64-character sha256"))
        }
        if (dataset.access == com.example.concordance.research.experiment.DataAccess.UNKNOWN) {
            add(EvidenceProblem("DATASET_ACCESS", "access is not a known value"))
        }
        if (dataset.dataClass == com.example.concordance.research.experiment.DataClass.UNKNOWN) {
            add(EvidenceProblem("DATASET_CLASS", "dataClass is not a known value"))
        }
    }

    fun validateEdge(edge: KgEdge): List<EvidenceProblem> = buildList {
        if (edge.subject.id.isBlank()) add(EvidenceProblem("EDGE_SUBJECT", "subject id is empty"))
        if (edge.objectRef.id.isBlank()) add(EvidenceProblem("EDGE_OBJECT", "object id is empty"))
        if (edge.subject.kind == edge.objectRef.kind && edge.subject.id == edge.objectRef.id) {
            add(EvidenceProblem("EDGE_SELF", "subject and object are the same node"))
        }
        if (edge.evidence.isEmpty()) {
            add(EvidenceProblem("EDGE_NO_EVIDENCE", "an edge with no evidence is not an edge"))
        }
        if (edge.evidence.any { it.id.isBlank() }) {
            add(EvidenceProblem("EDGE_EVIDENCE_ID", "an evidence reference has a blank id"))
        }
        if (edge.score < 0.0 || edge.score > 1.0 || !edge.score.isFinite()) {
            add(EvidenceProblem("EDGE_SCORE", "score must be between 0 and 1"))
        }
        if (edge.createdBy.isBlank()) add(EvidenceProblem("EDGE_CREATOR", "createdBy is empty"))
        if (edge.edgeId != KgEdge.idFor(edge.subject, edge.predicate, edge.objectRef)) {
            add(EvidenceProblem("EDGE_ID", "edgeId is not derived from subject/predicate/object"))
        }
    }

    /**
     * @param knownEdgeIds edges that exist in the store. Every supporting id must be one of them;
     *   passing an empty set means "no edge is known" and therefore every hypothesis is refused.
     */
    fun validateHypothesis(hypothesis: Hypothesis, knownEdgeIds: Set<String>): List<EvidenceProblem> = buildList {
        if (hypothesis.cancerType.isBlank()) add(EvidenceProblem("HYP_CANCER", "cancerType is empty"))
        if (hypothesis.biomarkerSymbol.isBlank()) add(EvidenceProblem("HYP_BIOMARKER", "biomarkerSymbol is empty"))
        if (hypothesis.biomarkerType.isBlank()) add(EvidenceProblem("HYP_BIOMARKER_TYPE", "biomarkerType is empty"))
        if (hypothesis.candidateDrug.isBlank()) add(EvidenceProblem("HYP_DRUG", "candidateDrug is empty"))
        if (hypothesis.drugRxnorm.isBlank()) {
            add(EvidenceProblem("HYP_RXNORM", "drugRxnorm is empty; the drug is not standardised"))
        }
        if (hypothesis.rationale.isBlank()) add(EvidenceProblem("HYP_RATIONALE", "rationale is empty"))
        if (hypothesis.modelVersion.isBlank()) add(EvidenceProblem("HYP_MODEL", "modelVersion is empty"))
        if (hypothesis.generatedAtUtc.isBlank()) add(EvidenceProblem("HYP_TIME", "generatedAtUtc is empty"))
        if (hypothesis.rankScore < 0.0 || hypothesis.rankScore > 1.0 || !hypothesis.rankScore.isFinite()) {
            add(EvidenceProblem("HYP_RANK", "rankScore must be between 0 and 1"))
        }
        if (hypothesis.supportingEdgeIds.isEmpty()) {
            add(EvidenceProblem("HYP_NO_EDGES", "a hypothesis needs at least one supporting edge"))
        }
        val unknown = hypothesis.supportingEdgeIds.filter { it !in knownEdgeIds }
        if (unknown.isNotEmpty()) {
            add(EvidenceProblem("HYP_UNKNOWN_EDGE", "unknown supporting edges: ${unknown.joinToString(", ")}"))
        }
        if (hypothesis.pubmedIds.any { it.isBlank() } ||
            hypothesis.datasetIds.any { it.isBlank() } ||
            hypothesis.wetlabIds.any { it.isBlank() }
        ) {
            add(EvidenceProblem("HYP_EVIDENCE_BLANK", "an evidence list contains a blank id"))
        }
        if (hypothesis.reviewStatus == ReviewStatus.REVIEWED || hypothesis.reviewStatus == ReviewStatus.REJECTED) {
            if (hypothesis.reviewer.isNullOrBlank()) {
                add(EvidenceProblem("HYP_REVIEWER", "a reviewed hypothesis needs a named reviewer"))
            }
            if (hypothesis.reviewedAtUtc.isNullOrBlank()) {
                add(EvidenceProblem("HYP_REVIEW_TIME", "a reviewed hypothesis needs a review timestamp"))
            }
        }
    }

    fun isPublishable(hypothesis: Hypothesis, knownEdgeIds: Set<String>): Boolean =
        hypothesis.isPublishable && validateHypothesis(hypothesis, knownEdgeIds).isEmpty()

    /**
     * Review is a forward-only human gate. There is no path from UNREVIEWED straight to REVIEWED,
     * and none back to UNREVIEWED.
     */
    fun canReview(from: ReviewStatus, to: ReviewStatus): Boolean = when (from) {
        ReviewStatus.UNREVIEWED -> to == ReviewStatus.IN_REVIEW
        ReviewStatus.IN_REVIEW -> to == ReviewStatus.REVIEWED || to == ReviewStatus.REJECTED
        ReviewStatus.REVIEWED, ReviewStatus.REJECTED -> false
    }

    fun review(
        hypothesis: Hypothesis,
        to: ReviewStatus,
        reviewer: String?,
        atUtc: String,
    ): ReviewResult {
        if (!canReview(hypothesis.reviewStatus, to)) {
            return ReviewResult.Refused(
                "REVIEW_TRANSITION",
                "cannot move from ${hypothesis.reviewStatus} to $to",
            )
        }
        if (to != ReviewStatus.IN_REVIEW && reviewer.isNullOrBlank()) {
            return ReviewResult.Refused("REVIEW_REVIEWER", "a final review needs a named reviewer")
        }
        if (atUtc.isBlank()) {
            return ReviewResult.Refused("REVIEW_TIME", "review has no timestamp")
        }
        return ReviewResult.Updated(
            hypothesis.copy(
                reviewStatus = to,
                reviewer = reviewer?.trim()?.ifBlank { null } ?: hypothesis.reviewer,
                reviewedAtUtc = atUtc,
            )
        )
    }

    /**
     * Merge a re-discovered edge into the stored one: evidence is unioned, never double-counted,
     * and the score moves only upward.
     */
    fun mergeEdges(existing: KgEdge, incoming: KgEdge): KgEdge {
        require(existing.edgeId == incoming.edgeId) { "cannot merge different edges" }
        val union = (existing.evidence + incoming.evidence)
            .distinctBy { it.kind.name + ":" + it.id }
        return existing.copy(
            evidence = union,
            score = maxOf(existing.score, incoming.score),
        )
    }

    private fun isSha256(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

data class EvidenceProblem(val code: String, val message: String) {
    override fun toString(): String = "$code: $message"
}

sealed interface ReviewResult {
    data class Updated(val hypothesis: Hypothesis) : ReviewResult
    data class Refused(val code: String, val reason: String) : ReviewResult
}

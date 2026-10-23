package com.example.concordance.learning

/**
 * Pure revision lifecycle logic. Nothing here touches the database; the
 * ViewModel persists the returned records via the repository.
 *
 * Two invariants (plan §6):
 *  - A round may only produce a *proposal* (promotedBy = null). A proposal is
 *    never the current revision.
 *  - A revision becomes current only through [approve], which requires an
 *    explicit human approver.
 *
 * Rollback is append-only too: reverting publishes a NEW revision carrying the
 * target's parameters rather than mutating or deleting history.
 */
object RevisionPublisher {

    /** Create an unpromoted proposal from a dry-run/search result. */
    fun propose(
        id: String,
        parentRevisionId: String?,
        parameters: ModelParameters,
        metricE: Double,
        createdAtUtc: String,
        notes: String
    ): ModelRevision = ModelRevision(
        id = id,
        parentRevisionId = parentRevisionId,
        createdAtUtc = createdAtUtc,
        parameters = parameters,
        metricE = metricE,
        promotedBy = null,
        notes = notes
    )

    /**
     * Human approval. Returns an approved copy, or null if the approval is
     * invalid (blank approver, out-of-bounds parameters, or no improvement).
     */
    fun approve(
        revision: ModelRevision,
        approver: String,
        baselineResult: EvaluationResult,
        candidateResult: EvaluationResult,
        epsilon: Double = LearningController.EPSILON
    ): ModelRevision? {
        if (approver.isBlank()) return null
        if (!revision.parameters.inBounds()) return null
        // A revision that does not actually improve the metric cannot be promoted.
        if (candidateResult.total >= baselineResult.total - epsilon) return null
        return revision.copy(promotedBy = approver)
    }

    /** Append-only revert: publish a new revision carrying [target]'s parameters. */
    fun revert(
        id: String,
        target: ModelRevision,
        currentRevisionId: String?,
        createdAtUtc: String,
        approver: String
    ): ModelRevision? {
        if (approver.isBlank()) return null
        if (!target.parameters.inBounds()) return null
        return ModelRevision(
            id = id,
            parentRevisionId = currentRevisionId,
            createdAtUtc = createdAtUtc,
            parameters = target.parameters,
            metricE = target.metricE,
            promotedBy = approver,
            notes = "Revert to '${target.id}' (append-only rollback)"
        )
    }
}
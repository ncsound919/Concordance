package com.example.concordance.learning

import com.example.concordance.model.Anchor
import com.example.concordance.model.ClaimChallenge

/**
 * Snapshot of the self-learning loop's health, used by the Phase 3 dashboard.
 */
data class LearningHealth(
    val revisionCount: Int,
    val promotedCount: Int,
    val metricTrend: List<Pair<String, Double>>,
    val totalChallenges: Int,
    val resolvedChallenges: Int,
    val challengeResolutionRate: Double,
    val latestRevisionId: String?
) {
    companion object {
        fun empty() = LearningHealth(0, 0, emptyList(), 0, 0, 0.0, null)
    }
}

object LearningHealthCalculator {
    /**
     * Compute health metrics for [currentRevision] given the stored revisions,
     * claims (anchors) and human-authored challenges.
     */
    fun compute(
        revisions: List<ModelRevision>,
        anchors: List<Anchor>,
        challenges: List<ClaimChallenge>,
        currentRevision: ModelRevision?
    ): LearningHealth {
        val promoted = revisions.filter { it.promotedBy != null }
        val trend = promoted.map { it.id to it.metricE }

        val params = currentRevision?.parameters ?: ModelParameters()
        val byClaim = challenges.groupBy { it.claimId }
        var total = 0
        var resolved = 0
        for ((claimId, chs) in byClaim) {
            val anchor = anchors.firstOrNull { it.id == claimId } ?: continue
            val candVerdict = LearningEvaluator.adjudicate(anchor, params) ?: continue
            for (ch in chs) {
                val implied = LearningEvaluator.impliedVerdict(
                    com.example.concordance.learning.ChallengeLabel(
                        ch.claimId, ch.updatedBrainEvents, ch.updatedBrainTotal,
                        ch.updatedOtherEvents, ch.updatedOtherTotal
                    ),
                    params
                )
                // A correction with unestimable counts has no verdict to match; counting it
                // as a permanent miss would make the resolution rate measure bad input, not
                // model quality.
                if (implied == null) continue
                total++
                if (implied == candVerdict) resolved++
            }
        }
        val rate = if (total == 0) 1.0 else resolved.toDouble() / total

        return LearningHealth(
            revisionCount = revisions.size,
            promotedCount = promoted.size,
            metricTrend = trend,
            totalChallenges = total,
            resolvedChallenges = resolved,
            challengeResolutionRate = rate,
            latestRevisionId = currentRevision?.id
        )
    }
}

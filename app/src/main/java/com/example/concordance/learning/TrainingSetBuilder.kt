package com.example.concordance.learning

import com.example.concordance.model.Anchor

/**
 * Assembles the evaluation/training set for a learning round from stored anchors
 * and human-authored challenges.
 *
 * Anti-collapse guard (plan §6.1): anchors that Concordance itself generated —
 * either imported from its own output or produced under a previous model
 * revision — are refused, and any violation aborts the round. The engine must
 * never learn from its own output.
 */
object TrainingSetBuilder {

    /**
     * Prefixes written by Concordance itself when it imports external evidence
     * into an anchor (e.g. the CIViC -> draft-claim importer).
     */
    val SELF_GENERATED_PREFIXES = listOf("concordance", "civic eid")

    /** Prefix stamped on engine-imported anchors; see ViewModel.importCuratedEvidenceAsClaim. */
    const val IMPORT_SOURCE_PREFIX = "Concordance import"

    data class Result(
        val cases: List<EvaluationCase>,
        val violations: List<String>,
        val skipped: Int
    ) {
        val isAborted: Boolean get() = violations.isNotEmpty()
        val challengeCount: Int get() = cases.sumOf { it.challenges.size }
    }

    /**
     * True when the anchor looks like Concordance-generated rather than
     * human-sourced. Matching is on the lower-cased, trimmed source prefix so we
     * never accidentally classify a real publication as self-generated.
     */
    fun isSelfGenerated(anchor: Anchor): Boolean {
        // Any anchor produced under a model revision is engine output, not evidence.
        if (anchor.modelRevisionId != null) return true
        val src = anchor.source.trim().lowercase()
        return SELF_GENERATED_PREFIXES.any { src.startsWith(it) }
    }

    /** Anchors with neither 2x2 counts nor a summary odds ratio cannot be adjudicated. */
    fun isUsable(anchor: Anchor): Boolean = anchor.hasCounts || anchor.oddsRatio != null

    /**
     * Defensive assertion (Phase 4 hardening): throws if ANY supplied anchor is
     * self-generated. Used as a belt-and-braces check in addition to the
     * violation-collecting [build]; a training set must never contain engine output.
     */
    fun assertNoSelfGenerated(anchors: List<Anchor>) {
        val bad = anchors.filter { isSelfGenerated(it) }
        check(bad.isEmpty()) {
            "Anti-collapse guard: ${bad.size} self-generated anchor(s) present: " +
                bad.joinToString { it.id }
        }
    }

    fun build(
        anchors: List<Anchor>,
        challengesByAnchorId: Map<String, List<ChallengeLabel>>,
        baseline: ModelParameters
    ): Result {
        val violations = mutableListOf<String>()
        val cases = mutableListOf<EvaluationCase>()
        var skipped = 0

        for (anchor in anchors) {
            if (isSelfGenerated(anchor)) {
                violations.add(
                    "Anchor '${anchor.id}' is Concordance-generated (source='${anchor.source}', " +
                        "revision=${anchor.modelRevisionId ?: "baseline"}). " +
                        "Self-generated evidence is refused to prevent model collapse."
                )
                continue
            }
            if (!isUsable(anchor)) {
                skipped++
                continue
            }
            val baselineVerdict = LearningEvaluator.adjudicate(anchor, baseline) ?: run {
                skipped++
                continue
            }
            cases.add(
                EvaluationCase(
                    anchor = anchor,
                    baselineVerdict = baselineVerdict,
                    challenges = challengesByAnchorId[anchor.id].orEmpty()
                )
            )
        }

        return Result(cases = cases, violations = violations, skipped = skipped)
    }
}
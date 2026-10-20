package com.example.concordance.learning

import com.example.concordance.model.Anchor
import com.example.concordance.model.Verdict

/** How a single parameter moved between two revisions. */
data class ParameterDelta(
    val snFrom: Double,
    val snTo: Double,
    val referencePrevalenceFrom: Double,
    val referencePrevalenceTo: Double,
    val z95From: Double,
    val z95To: Double
) {
    val snChanged: Boolean get() = kotlin.math.abs(snTo - snFrom) > 1e-9
    val referencePrevalenceChanged: Boolean
        get() = kotlin.math.abs(referencePrevalenceTo - referencePrevalenceFrom) > 1e-9
    val z95Changed: Boolean get() = kotlin.math.abs(z95To - z95From) > 1e-9
    val anyChanged: Boolean get() = snChanged || referencePrevalenceChanged || z95Changed
}

/** A verdict that would change for one anchor between two revisions. */
data class VerdictFlip(
    val anchorId: String,
    val anchorLabel: String,
    val fromVerdict: Verdict,
    val toVerdict: Verdict
)

/** The full "what changed" report between two revisions. */
data class RevisionDiff(
    val fromRevisionId: String?,
    val toRevisionId: String?,
    val parameterDelta: ParameterDelta,
    val flips: List<VerdictFlip>,
    val unchanged: Int,
    val totalAnchors: Int
) {
    val flipCount: Int get() = flips.size

    /** Fraction of anchors whose verdict changed. */
    val flipRate: Double
        get() = if (totalAnchors == 0) 0.0 else flips.size.toDouble() / totalAnchors
}

/**
 * Computes the "what changed" diff between two model revisions.
 *
 * This never mutates stored capsules: a capsule stays pinned to the revision
 * that produced it (plan §6.7). The diff is a *preview* of how existing anchors
 * would be re-adjudicated under the target revision.
 */
object RevisionDiffer {

    fun diff(
        anchors: List<Anchor>,
        from: ModelParameters,
        to: ModelParameters,
        fromRevisionId: String?,
        toRevisionId: String?
    ): RevisionDiff {
        val flips = mutableListOf<VerdictFlip>()
        var unchanged = 0

        for (anchor in anchors) {
            val fromVerdict = LearningEvaluator.adjudicate(anchor, from)
            val toVerdict = LearningEvaluator.adjudicate(anchor, to)
            if (fromVerdict == null || toVerdict == null) continue
            if (fromVerdict != toVerdict) {
                flips.add(
                    VerdictFlip(
                        anchorId = anchor.id,
                        anchorLabel = anchor.label,
                        fromVerdict = fromVerdict,
                        toVerdict = toVerdict
                    )
                )
            } else {
                unchanged++
            }
        }

        return RevisionDiff(
            fromRevisionId = fromRevisionId,
            toRevisionId = toRevisionId,
            parameterDelta = ParameterDelta(
                snFrom = from.sn,
                snTo = to.sn,
                referencePrevalenceFrom = from.referencePrevalence,
                referencePrevalenceTo = to.referencePrevalence,
                z95From = from.z95,
                z95To = to.z95
            ),
            flips = flips,
            unchanged = unchanged,
            totalAnchors = flips.size + unchanged
        )
    }
}
package com.example

import com.example.concordance.learning.LearningEvaluator
import com.example.concordance.learning.ModelParameters
import com.example.concordance.learning.ParameterSearch
import com.example.concordance.learning.RevisionDiffer
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevisionDiffTest {

    private val baseline = ModelParameters()

    private val fromCounts = Anchor(
        id = "giannoudis2024_pooled",
        label = "Giannoudis 2024 pooled",
        source = "Giannoudis et al. 2024; Foundation Medicine",
        evidenceGrade = EvidenceGrade.COUNTS,
        brainEvents = 53, brainTotal = 822,
        otherEvents = 3220, otherTotal = 15516
    )

    private val annals = Anchor(
        id = "annals_106P_meta",
        label = "Annals 106P meta",
        source = "Annals of Oncology 106P",
        evidenceGrade = EvidenceGrade.ABSTRACT_ONLY,
        oddsRatio = 0.35, orCiLow = 0.24, orCiHigh = 0.52,
        referencePrevalence = 3220.0 / 15516.0
    )

    private val anchors = listOf(fromCounts, annals)

    @Test
    fun identicalParametersProduceNoFlips() {
        val diff = RevisionDiffer.diff(anchors, baseline, baseline, "rev-a", "rev-b")
        assertEquals(0, diff.flipCount)
        assertEquals(anchors.size, diff.unchanged)
        assertEquals(anchors.size, diff.totalAnchors)
        assertFalse(diff.parameterDelta.anyChanged)
    }

    @Test
    fun flipsMatchAnIndependentAdjudicationComparison() {
        val candidate = ModelParameters(sn = 0.60, referencePrevalence = 0.12)
        val diff = RevisionDiffer.diff(anchors, baseline, candidate, "baseline", "cand")

        // Independently recompute the expected flip set.
        val expected = anchors.count {
            val a = LearningEvaluator.adjudicate(it, baseline)
            val b = LearningEvaluator.adjudicate(it, candidate)
            a != null && b != null && a != b
        }
        assertEquals("differ must agree with direct adjudication", expected, diff.flipCount)
        assertEquals(anchors.size, diff.flipCount + diff.unchanged)
        diff.flips.forEach { flip ->
            assertTrue(flip.fromVerdict != flip.toVerdict)
            assertTrue(anchors.any { it.id == flip.anchorId })
        }
    }

    @Test
    fun differDetectsFlipsAcrossTheSearchSpaceOrNoneExist() {
        // Over the whole declared grid, the differ's flip count must always equal
        // a fresh recomputation, and any nonzero count must be surfaced.
        val cases = ParameterSearch.grid(baseline)
        for (candidate in cases) {
            val diff = RevisionDiffer.diff(anchors, baseline, candidate, "baseline", null)
            val expected = anchors.count {
                val a = LearningEvaluator.adjudicate(it, baseline)
                val b = LearningEvaluator.adjudicate(it, candidate)
                a != null && b != null && a != b
            }
            assertEquals(expected, diff.flipCount)
            assertEquals(diff.totalAnchors, diff.flipCount + diff.unchanged)
        }
    }

    @Test
    fun parameterDeltaReportsMovement() {
        val to = ModelParameters(sn = 0.70, referencePrevalence = 0.25)
        val delta = RevisionDiffer.diff(anchors, baseline, to, null, null).parameterDelta
        assertTrue(delta.snChanged)
        assertTrue(delta.referencePrevalenceChanged)
        assertFalse(delta.z95Changed)
        assertTrue(delta.anyChanged)
        assertEquals(baseline.sn, delta.snFrom, 1e-12)
        assertEquals(0.70, delta.snTo, 1e-12)
    }

    @Test
    fun revisionIdsPropagateIntoTheDiff() {
        val diff = RevisionDiffer.diff(anchors, baseline, baseline, "rev-from", "rev-to")
        assertEquals("rev-from", diff.fromRevisionId)
        assertEquals("rev-to", diff.toRevisionId)
    }

    @Test
    fun flipRateIsNormalizedByTotalAnchors() {
        val diff = RevisionDiffer.diff(anchors, baseline, ModelParameters(sn = 0.55), null, null)
        val expected = diff.flipCount.toDouble() / anchors.size
        assertEquals(expected, diff.flipRate, 1e-12)
    }

    @Test
    fun emptyAnchorListYieldsZeroRateNotDivideByZero() {
        val diff = RevisionDiffer.diff(emptyList(), baseline, ModelParameters(sn = 0.6), null, null)
        assertEquals(0, diff.totalAnchors)
        assertEquals(0.0, diff.flipRate, 1e-12)
    }
}

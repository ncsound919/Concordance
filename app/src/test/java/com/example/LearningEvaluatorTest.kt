package com.example

import com.example.concordance.learning.LearningController
import com.example.concordance.learning.LearningEvaluator
import com.example.concordance.learning.ModelParameters
import com.example.concordance.learning.EvaluationCase
import com.example.concordance.learning.ChallengeLabel
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningEvaluatorTest {

    private val giannoudis = Anchor(
        id = "giannoudis2024_pooled",
        label = "Giannoudis 2024 pooled",
        source = "Giannoudis et al. 2024",
        evidenceGrade = EvidenceGrade.COUNTS,
        brainEvents = 53, brainTotal = 822,
        otherEvents = 3220, otherTotal = 15516
    )

    private val annals = Anchor(
        id = "annals_106P_meta",
        label = "Annals 106P meta",
        source = "Annals 106P",
        evidenceGrade = EvidenceGrade.ABSTRACT_ONLY,
        oddsRatio = 0.35, orCiLow = 0.24, orCiHigh = 0.52,
        referencePrevalence = 3220.0 / 15516.0
    )

    /** The published single-patient case report: every brain sample is an event, so the
     *  correction cannot estimate anything from it. */
    private val caseReport = Anchor(
        id = "current_oncology_2026_case",
        label = "Current Oncology 2026 case",
        source = "Current Oncology",
        evidenceGrade = EvidenceGrade.COUNTS,
        brainEvents = 1, brainTotal = 1,
        otherEvents = 42, otherTotal = 200
    )

    private val mskCohort = Anchor(
        id = "msk_lung_2024",
        label = "MSK lung cohort 2024",
        source = "MSK-IMPACT 2024 cohort",
        evidenceGrade = EvidenceGrade.COUNTS,
        brainEvents = 18, brainTotal = 240,
        otherEvents = 900, otherTotal = 4200
    )

    private val baselineParams = ModelParameters()

    private val cases = listOf(
        EvaluationCase(giannoudis, LearningEvaluator.adjudicate(giannoudis, baselineParams)!!),
        EvaluationCase(annals, LearningEvaluator.adjudicate(annals, baselineParams)!!),
        EvaluationCase(mskCohort, LearningEvaluator.adjudicate(mskCohort, baselineParams)!!)
    )

    @Test
    fun baselineEvaluationIsFiniteAndWellFormed() {
        val r = LearningEvaluator.evaluate(cases, baselineParams, baselineParams)
        // The abstract-only anchor is retained but not scored: its verdict is fixed by protocol.
        assertEquals(2, r.casesEvaluated)
        assertEquals(3, cases.size)
        assertTrue(r.total.isFinite())
        assertTrue(r.verdictStabilityPenalty in 0.0..1.0)
        assertTrue(r.challengeResolutionMissRate in 0.0..1.0)
        assertTrue(r.calibrationError in 0.0..1.0)
        assertTrue(r.parsimony >= 0.0)
    }

    @Test
    fun identicalCandidateHasZeroStabilityPenalty() {
        val r = LearningEvaluator.evaluate(cases, baselineParams, baselineParams)
        assertEquals(0.0, r.verdictStabilityPenalty, 1e-9)
    }

    @Test
    fun dryRunBaselineCandidateDoesNotImprove() {
        val report = LearningController.dryRun(cases, baselineParams, baselineParams)
        assertFalse("equal candidate must not be reported as improved", report.improved)
        assertFalse(report.promoted)
    }

    @Test
    fun outOfBoundsCandidateIsRejected() {
        val bad = ModelParameters(sn = 2.0, referencePrevalence = 0.2)
        assertFalse(bad.inBounds())
        val report = LearningController.dryRun(cases, bad, baselineParams)
        assertFalse(report.improved)
        assertTrue(report.notes.contains("REJECTED"))
    }

    @Test
    fun perturbedCandidateProducesFiniteReport() {
        val cand = ModelParameters(sn = 0.85, referencePrevalence = 0.18)
        val report = LearningController.dryRun(cases, cand, baselineParams)
        assertTrue(report.candidateResult.total.isFinite())
        assertTrue(report.baselineResult.total.isFinite())
    }

    @Test
    fun unestimableAnchorIsRefusedRatherThanScoredAsWithheld() {
        assertNotNull(caseReport.validationError())
        assertNull(
            "an anchor the correction cannot estimate must never produce a verdict",
            LearningEvaluator.adjudicate(caseReport, baselineParams)
        )
        // And it is not silently scored as a stable, unchallenged data point.
        val r = LearningEvaluator.evaluate(
            listOf(EvaluationCase(caseReport, com.example.concordance.model.Verdict.SUPPORTED)),
            baselineParams,
            baselineParams
        )
        assertEquals(0, r.casesEvaluated)
    }

    @Test
    fun challengeWithUnestimableCountsHasNoImpliedVerdict() {
        val ch = ChallengeLabel(
            anchorId = giannoudis.id,
            updatedBrainEvents = 0,
            updatedBrainTotal = 822,
            updatedOtherEvents = 3220,
            updatedOtherTotal = 15516
        )
        assertNotNull(LearningEvaluator.challengeError(ch))
        assertNull(LearningEvaluator.impliedVerdict(ch, baselineParams))
    }

    @Test
    fun candidateGridIsBoundedAndInBounds() {
        val grid = LearningController.candidateGrid(baselineParams, snSteps = 3, refSteps = 3)
        assertTrue(grid.isNotEmpty())
        assertTrue(grid.all { it.inBounds() })
    }

    @Test
    fun challengeResolutionCountsAMatchingChallenge() {
        // A challenge whose corrected counts yield the same verdict as the
        // candidate's verdict on the original anchor counts as resolved.
        val c = cases.first()
        val ch = ChallengeLabel(
            anchorId = c.anchor.id,
            updatedBrainEvents = c.anchor.brainEvents!!,
            updatedBrainTotal = c.anchor.brainTotal!!,
            updatedOtherEvents = c.anchor.otherEvents!!,
            updatedOtherTotal = c.anchor.otherTotal!!
        )
        val caseWithChallenge = c.copy(challenges = listOf(ch))
        val r = LearningEvaluator.evaluate(listOf(caseWithChallenge), baselineParams, baselineParams)
        // identical counts => implied verdict == candidate verdict => 0 miss rate
        assertEquals(0.0, r.challengeResolutionMissRate, 1e-9)
    }
}

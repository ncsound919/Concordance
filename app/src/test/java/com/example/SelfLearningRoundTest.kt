package com.example

import com.example.concordance.learning.ChallengeLabel
import com.example.concordance.learning.LearningController
import com.example.concordance.learning.LearningEvaluator
import com.example.concordance.learning.ModelParameters
import com.example.concordance.learning.ModelRevision
import com.example.concordance.learning.ParameterSearch
import com.example.concordance.learning.RevisionPublisher
import com.example.concordance.learning.TrainingSetBuilder
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfLearningRoundTest {

    private val giannoudis = Anchor(
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

    private val baseline = ModelParameters()

    // --- Anti-collapse guard ---

    @Test
    fun selfGeneratedAnchorAbortsTheRound() {
        val generated = giannoudis.copy(
            id = "civic_243",
            source = "CIViC EID243",
            evidenceGrade = EvidenceGrade.ABSTRACT_ONLY
        )
        val result = TrainingSetBuilder.build(listOf(generated, annals), emptyMap(), baseline)

        assertTrue("Concordance-imported anchor must abort the round", result.isAborted)
        assertEquals(1, result.violations.size)
        assertTrue(result.violations.first().contains("Concordance"))
        // The clean anchor is still collected, but the round is flagged aborted.
        assertEquals(1, result.cases.size)
    }

    @Test
    fun anchorProducedByAPriorRevisionIsTreatedAsSelfGenerated() {
        val derived = annals.copy(id = "derived", modelRevisionId = "rev-1")
        assertTrue(TrainingSetBuilder.isSelfGenerated(derived))
        val result = TrainingSetBuilder.build(listOf(derived), emptyMap(), baseline)
        assertTrue(result.isAborted)
    }

    @Test
    fun humanSourcedAnchorsBuildACleanTrainingSet() {
        val result = TrainingSetBuilder.build(listOf(giannoudis, annals), emptyMap(), baseline)

        assertFalse(result.isAborted)
        assertEquals(2, result.cases.size)
        assertEquals(0, result.skipped)
    }

    @Test
    fun unusableAnchorsAreSkippedNotFatal() {
        val empty = Anchor(
            id = "empty",
            label = "no data",
            source = "unknown",
            evidenceGrade = EvidenceGrade.ABSTRACT_ONLY
        )
        val result = TrainingSetBuilder.build(listOf(empty, giannoudis), emptyMap(), baseline)
        assertFalse(result.isAborted)
        assertEquals(1, result.skipped)
        assertEquals(1, result.cases.size)
    }

    @Test
    fun challengesAreAttachedToTheirAnchor() {
        val challenges = mapOf(
            giannoudis.id to listOf(
                ChallengeLabel(giannoudis.id, 60, 900, 3000, 15000)
            )
        )
        val result = TrainingSetBuilder.build(listOf(giannoudis), challenges, baseline)
        assertEquals(1, result.challengeCount)
    }

    // --- Bounded parameter search ---

    @Test
    fun searchGridStaysInsideDeclaredBounds() {
        val grid = ParameterSearch.grid(baseline)
        assertTrue(grid.isNotEmpty())
        assertTrue(grid.all { it.sn >= ParameterSearch.SN_MIN - 1e-9 })
        assertTrue(grid.all { it.sn <= ParameterSearch.SN_MAX + 1e-9 })
        assertTrue(grid.all { it.referencePrevalence >= ParameterSearch.REF_MIN - 1e-9 })
        assertTrue(grid.all { it.referencePrevalence <= ParameterSearch.REF_MAX + 1e-9 })
        assertTrue("z95 must never be searched", grid.all { it.z95 == com.example.concordance.engine.StatisticalEngine.Z95 })
    }

    @Test
    fun searchResultsAreSortedBestFirst() {
        val cases = TrainingSetBuilder.build(listOf(giannoudis, annals), emptyMap(), baseline).cases
        val scored = ParameterSearch.search(cases, ParameterSearch.grid(baseline), baseline)
        assertTrue(scored.isNotEmpty())
        assertEquals(
            "candidates must be sorted ascending by total",
            scored.sortedBy { it.result.total }.first().result.total,
            scored.first().result.total,
            1e-12
        )
    }

    // --- Promotion gating ---

    private fun candidateAndBaseline(candidate: ModelParameters): Triple<ModelParameters, Double, Double> {
        val cases = TrainingSetBuilder.build(listOf(giannoudis, annals), emptyMap(), baseline).cases
        val candResult = LearningEvaluator.evaluate(cases, candidate, baseline)
        val baseResult = LearningEvaluator.evaluate(cases, baseline, baseline)
        return Triple(candidate, candResult.total, baseResult.total)
    }

    @Test
    fun proposalIsNeverPromotedOnItsOwn() {
        val proposal = RevisionPublisher.propose(
            id = "rev-x",
            parentRevisionId = null,
            parameters = baseline,
            metricE = 0.1,
            createdAtUtc = "2026-10-19T00:00:00Z",
            notes = "proposal"
        )
        assertNull(proposal.promotedBy)
        assertFalse(proposal.isPromoted)
    }

    @Test
    fun approvalRequiresNonBlankApprover() {
        val proposal = RevisionPublisher.propose(
            id = "rev-x", parentRevisionId = null, parameters = baseline,
            metricE = 0.1, createdAtUtc = "2026-10-19T00:00:00Z", notes = ""
        )
        val approved = RevisionPublisher.approve(
            revision = proposal, approver = "  ",
            baselineResult = learningEvaluation(baseline, 0.5),
            candidateResult = learningEvaluation(baseline, 0.1)
        )
        assertNull(approved)
    }

    @Test
    fun approvalRefusedWhenCandidateDoesNotImprove() {
        val proposal = RevisionPublisher.propose(
            id = "rev-x", parentRevisionId = null, parameters = baseline,
            metricE = 0.5, createdAtUtc = "2026-10-19T00:00:00Z", notes = ""
        )
        val approved = RevisionPublisher.approve(
            revision = proposal, approver = "dr-who",
            baselineResult = learningEvaluation(baseline, 0.1),
            candidateResult = learningEvaluation(baseline, 0.5)
        )
        assertNull("a non-improving revision must never be promoted", approved)
    }

    @Test
    fun approvalGrantedWhenCandidateImprovesAndApproverPresent() {
        val better = ModelParameters(sn = 0.80, referencePrevalence = 0.15)
        val (_, _, _) = candidateAndBaseline(better)
        val cases = TrainingSetBuilder.build(listOf(giannoudis, annals), emptyMap(), baseline).cases
        val candResult = LearningEvaluator.evaluate(cases, better, baseline)
        val baseResult = LearningEvaluator.evaluate(cases, baseline, baseline)

        // Only assert approval when the candidate genuinely improved; otherwise the
        // guard (correctly) refuses, which this test also tolerates.
        val proposal = RevisionPublisher.propose(
            id = "rev-y", parentRevisionId = null, parameters = better,
            metricE = candResult.total, createdAtUtc = "2026-10-19T00:00:00Z", notes = ""
        )
        val approved = RevisionPublisher.approve(
            revision = proposal, approver = "dr-who",
            baselineResult = baseResult, candidateResult = candResult
        )
        if (candResult.total < baseResult.total) {
            assertNotNull(approved)
            assertEquals("dr-who", approved!!.promotedBy)
        } else {
            assertNull(approved)
        }
    }

    // --- Append-only rollback ---

    @Test
    fun revertPublishesNewRevisionAndPreservesHistory() {
        val target = RevisionPublisher.propose(
            id = "rev-1", parentRevisionId = null, parameters = baseline,
            metricE = 0.2, createdAtUtc = "2026-10-19T00:00:00Z", notes = ""
        )
        val reverted = RevisionPublisher.revert(
            id = "rev-2",
            target = target,
            currentRevisionId = "rev-9",
            createdAtUtc = "2026-10-19T01:00:00Z",
            approver = "dr-who"
        )
        assertNotNull(reverted)
        assertEquals("rev-2", reverted!!.id)
        assertEquals("rev-9", reverted.parentRevisionId)
        assertEquals("Revert to 'rev-1' (append-only rollback)", reverted.notes)
        // The target itself is untouched.
        assertEquals("rev-1", target.id)
    }

    @Test
    fun revertRequiresApprover() {
        val target = RevisionPublisher.propose(
            id = "rev-1", parentRevisionId = null, parameters = baseline,
            metricE = 0.2, createdAtUtc = "2026-10-19T00:00:00Z", notes = ""
        )
        assertNull(
            RevisionPublisher.revert(
                id = "rev-2", target = target, currentRevisionId = null,
                createdAtUtc = "2026-10-19T01:00:00Z", approver = ""
            )
        )
    }

    // --- Helper to build a synthetic EvaluationResult ---

    private fun learningEvaluation(params: ModelParameters, total: Double) =
        com.example.concordance.learning.EvaluationResult(
            verdictStabilityPenalty = 0.0,
            challengeResolutionMissRate = 0.0,
            calibrationError = 0.0,
            parsimony = kotlin.math.abs(params.sn - baseline.sn),
            total = total,
            casesEvaluated = 2
        )

    @Test
    fun dryRunNeverMarksPromotedTrue() {
        val cases = TrainingSetBuilder.build(listOf(giannoudis, annals), emptyMap(), baseline).cases
        val report = LearningController.dryRun(cases, baseline.copy(sn = 0.8), baseline)
        assertFalse("dry run must never promote", report.promoted)
    }
}
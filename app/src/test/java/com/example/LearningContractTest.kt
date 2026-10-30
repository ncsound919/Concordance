package com.example

import com.example.concordance.engine.StatisticalEngine
import com.example.concordance.learning.ChallengeLabel
import com.example.concordance.learning.EvaluationCase
import com.example.concordance.learning.EvaluationResult
import com.example.concordance.learning.LearningEvaluator
import com.example.concordance.learning.ModelParameters
import com.example.concordance.learning.ModelRevision
import com.example.concordance.learning.ParameterSearch
import com.example.concordance.learning.RevisionPublisher
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import com.example.concordance.model.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The learning contracts: the bounded parameter search, the revision
 * lifecycle, and the evaluator.
 *
 * Two invariants carry the weight here:
 *
 *  - the search space is *declared* and bounded, and `z95` is never searched -
 *    widening a confidence interval is a statistical-integrity decision, not
 *    something an evaluation metric should be allowed to buy;
 *  - a revision only becomes current through an explicit human approval, and a
 *    rollback is an append of a new revision rather than a mutation.
 */
class LearningContractTest {

    private fun countsAnchor(
        id: String = "a1",
        brainEvents: Int = 30,
        brainTotal: Int = 300,
        otherEvents: Int = 100,
        otherTotal: Int = 400
    ) = Anchor(
        id = id,
        label = "Anchor $id",
        source = "source",
        evidenceGrade = EvidenceGrade.COUNTS,
        brainEvents = brainEvents,
        brainTotal = brainTotal,
        otherEvents = otherEvents,
        otherTotal = otherTotal,
        oddsRatio = 0.33,
        orCiLow = 0.22,
        orCiHigh = 0.50
    )

    private fun result(total: Double) = EvaluationResult(
        verdictStabilityPenalty = 0.0,
        challengeResolutionMissRate = 0.0,
        calibrationError = 0.0,
        parsimony = 0.0,
        total = total,
        casesEvaluated = 0
    )

    private fun revision(parameters: ModelParameters = ModelParameters()) = ModelRevision(
        id = "rev-1",
        parentRevisionId = "rev-0",
        createdAtUtc = "2026-10-19T00:00:00Z",
        parameters = parameters,
        metricE = 0.5
    )

    // -----------------------------------------------------------------------
    // The bounded grid
    // -----------------------------------------------------------------------

    @Test
    fun `the grid is the declared size`() {
        val grid = ParameterSearch.grid(ModelParameters())
        // 11 sensitivity values (0.50 to 1.00 step 0.05) by 11 reference
        // prevalences (0.10 to 0.30 step 0.02).
        assertEquals(121, grid.size)
    }

    @Test
    fun `every candidate in the grid is in bounds`() {
        assertTrue(ParameterSearch.grid(ModelParameters()).all { it.inBounds() })
    }

    @Test
    fun `the grid never varies the confidence level`() {
        assertTrue(ParameterSearch.grid(ModelParameters()).all { it.z95 == StatisticalEngine.Z95 })
    }

    @Test
    fun `the grid stays inside the declared sensitivity bounds`() {
        for (candidate in ParameterSearch.grid(ModelParameters())) {
            assertTrue(candidate.sn >= ParameterSearch.SN_MIN - 1e-9)
            assertTrue(candidate.sn <= ParameterSearch.SN_MAX + 1e-9)
            assertTrue(candidate.referencePrevalence >= ParameterSearch.REF_MIN - 1e-9)
            assertTrue(candidate.referencePrevalence <= ParameterSearch.REF_MAX + 1e-9)
        }
    }

    @Test
    fun `the grid contains no duplicate candidates`() {
        val grid = ParameterSearch.grid(ModelParameters())
        assertEquals(grid.size, grid.toSet().size)
    }

    @Test
    fun `the grid ignores the baseline parameters`() {
        val a = ParameterSearch.grid(ModelParameters(sn = 0.9, referencePrevalence = 0.2))
        val b = ParameterSearch.grid(ModelParameters(sn = 0.7, referencePrevalence = 0.3))
        assertEquals(a, b)
    }

    // -----------------------------------------------------------------------
    // The search
    // -----------------------------------------------------------------------

    @Test
    fun `the search returns one score per candidate, best first`() {
        val cases = listOf(
            EvaluationCase(
                anchor = countsAnchor(),
                baselineVerdict = Verdict.SUPPORTED,
                challenges = listOf(
                    ChallengeLabel("a1", 28, 300, 105, 400)
                )
            )
        )
        val candidates = ParameterSearch.grid(ModelParameters())
        val scores = ParameterSearch.search(cases, candidates, ModelParameters())
        assertEquals(candidates.size, scores.size)
        val totals = scores.map { it.result.total }
        assertEquals(totals.sorted(), totals)
    }

    @Test
    fun `the best candidate is the first of the search`() {
        val cases = listOf(EvaluationCase(anchor = countsAnchor(), baselineVerdict = Verdict.SUPPORTED))
        val best = ParameterSearch.best(cases, ModelParameters())
        assertNotNull(best)
        val scores = ParameterSearch.search(cases, ParameterSearch.grid(ModelParameters()), ModelParameters())
        assertEquals(scores.first().parameters, best!!.parameters)
    }

    @Test
    fun `searching an empty case list still returns every candidate`() {
        val scores = ParameterSearch.search(emptyList(), ParameterSearch.grid(ModelParameters()), ModelParameters())
        assertEquals(121, scores.size)
        assertTrue(scores.all { it.result.total == 0.0 })
    }

    // -----------------------------------------------------------------------
    // The evaluator
    // -----------------------------------------------------------------------

    @Test
    fun `evaluating no cases gives a zero result`() {
        val result = LearningEvaluator.evaluate(emptyList(), ModelParameters(), ModelParameters())
        assertEquals(0.0, result.total, 1e-12)
        assertEquals(0, result.casesEvaluated)
    }

    @Test
    fun `out of bounds parameters yield no verdict`() {
        assertNull(LearningEvaluator.adjudicate(countsAnchor(), ModelParameters(sn = 5.0)))
        assertNull(LearningEvaluator.adjudicate(countsAnchor(), ModelParameters(referencePrevalence = 2.0)))
    }

    @Test
    fun `an in bounds anchor yields a verdict`() {
        assertNotNull(LearningEvaluator.adjudicate(countsAnchor(), ModelParameters()))
    }

    @Test
    fun `an implied verdict is computed from the corrected counts`() {
        val challenge = ChallengeLabel(
            anchorId = "a1",
            updatedBrainEvents = 30,
            updatedBrainTotal = 300,
            updatedOtherEvents = 100,
            updatedOtherTotal = 400
        )
        // The corrected counts are the same as the anchor's, so the implied
        // verdict must match the anchor's own verdict.
        val implied = LearningEvaluator.impliedVerdict(challenge, ModelParameters())
        val direct = LearningEvaluator.adjudicate(countsAnchor(), ModelParameters())
        assertEquals(direct, implied)
    }

    @Test
    fun `the parsimony term is the distance from the baseline`() {
        val baseline = ModelParameters(sn = 0.9, referencePrevalence = 0.2)
        val candidate = ModelParameters(sn = 0.8, referencePrevalence = 0.25)
        val result = LearningEvaluator.evaluate(
            listOf(EvaluationCase(anchor = countsAnchor(), baselineVerdict = Verdict.SUPPORTED)),
            candidate,
            baseline
        )
        assertEquals(0.1 + 0.05, result.parsimony, 1e-9)
    }

    @Test
    fun `the evaluation counts the cases it evaluated`() {
        val cases = listOf(
            EvaluationCase(anchor = countsAnchor("a1"), baselineVerdict = Verdict.SUPPORTED),
            EvaluationCase(anchor = countsAnchor("a2"), baselineVerdict = Verdict.SUPPORTED)
        )
        val result = LearningEvaluator.evaluate(cases, ModelParameters(), ModelParameters())
        assertEquals(2, result.casesEvaluated)
    }

    // -----------------------------------------------------------------------
    // The revision lifecycle
    // -----------------------------------------------------------------------

    @Test
    fun `a proposal is not promoted`() {
        val proposal = RevisionPublisher.propose(
            id = "rev-2",
            parentRevisionId = "rev-1",
            parameters = ModelParameters(),
            metricE = 0.5,
            createdAtUtc = "2026-10-19T00:00:00Z",
            notes = "notes"
        )
        assertNull(proposal.promotedBy)
        assertEquals(false, proposal.isPromoted)
        assertEquals("rev-1", proposal.parentRevisionId)
    }

    @Test
    fun `an approval with a blank approver is refused`() {
        assertNull(
            RevisionPublisher.approve(
                revision(),
                approver = "   ",
                baselineResult = result(1.0),
                candidateResult = result(0.1)
            )
        )
    }

    @Test
    fun `an approval of out of bounds parameters is refused`() {
        assertNull(
            RevisionPublisher.approve(
                revision(ModelParameters(sn = 5.0)),
                approver = "alice",
                baselineResult = result(1.0),
                candidateResult = result(0.1)
            )
        )
    }

    @Test
    fun `a revision that does not improve the metric is not approved`() {
        assertNull(
            RevisionPublisher.approve(
                revision(),
                approver = "alice",
                baselineResult = result(0.5),
                candidateResult = result(0.5)
            )
        )
    }

    @Test
    fun `a revision that is worse is not approved`() {
        assertNull(
            RevisionPublisher.approve(
                revision(),
                approver = "alice",
                baselineResult = result(0.5),
                candidateResult = result(0.9)
            )
        )
    }

    @Test
    fun `an improvement within the epsilon is not approved`() {
        assertNull(
            RevisionPublisher.approve(
                revision(),
                approver = "alice",
                baselineResult = result(0.500),
                candidateResult = result(0.4999),
                epsilon = 0.01
            )
        )
    }

    @Test
    fun `a clear improvement is approved and records the approver`() {
        val approved = RevisionPublisher.approve(
            revision(),
            approver = "alice",
            baselineResult = result(1.0),
            candidateResult = result(0.1)
        )
        assertNotNull(approved)
        assertEquals("alice", approved!!.promotedBy)
        assertEquals(true, approved.isPromoted)
    }

    @Test
    fun `an approval does not change the revision id or its parent`() {
        val original = revision()
        val approved = RevisionPublisher.approve(
            original,
            approver = "alice",
            baselineResult = result(1.0),
            candidateResult = result(0.1)
        )!!
        assertEquals(original.id, approved.id)
        assertEquals(original.parentRevisionId, approved.parentRevisionId)
        assertEquals(original.metricE, approved.metricE, 1e-12)
    }

    @Test
    fun `a revert with a blank approver is refused`() {
        assertNull(
            RevisionPublisher.revert(
                id = "rev-3",
                target = revision(),
                currentRevisionId = "rev-2",
                createdAtUtc = "2026-10-19T00:00:00Z",
                approver = ""
            )
        )
    }

    @Test
    fun `a revert of out of bounds parameters is refused`() {
        assertNull(
            RevisionPublisher.revert(
                id = "rev-3",
                target = revision(ModelParameters(sn = 5.0)),
                currentRevisionId = "rev-2",
                createdAtUtc = "2026-10-19T00:00:00Z",
                approver = "alice"
            )
        )
    }

    @Test
    fun `a revert publishes a new revision carrying the target parameters`() {
        val target = revision(ModelParameters(sn = 0.75, referencePrevalence = 0.3))
        val reverted = RevisionPublisher.revert(
            id = "rev-3",
            target = target,
            currentRevisionId = "rev-2",
            createdAtUtc = "2026-10-19T00:00:00Z",
            approver = "alice"
        )
        assertNotNull(reverted)
        assertEquals("rev-3", reverted!!.id)
        assertEquals("rev-2", reverted.parentRevisionId)
        assertEquals(target.parameters, reverted.parameters)
        assertEquals(target.metricE, reverted.metricE, 1e-12)
        assertEquals("alice", reverted.promotedBy)
        assertTrue(reverted.notes!!.contains(target.id))
    }

    @Test
    fun `a revert does not mutate the target`() {
        val target = revision()
        val before = target
        RevisionPublisher.revert(
            id = "rev-3",
            target = target,
            currentRevisionId = "rev-2",
            createdAtUtc = "2026-10-19T00:00:00Z",
            approver = "alice"
        )
        assertEquals(before, target)
    }

    @Test
    fun `model parameters report whether they are in bounds`() {
        assertTrue(ModelParameters().inBounds())
        assertTrue(ModelParameters(sn = 0.5, referencePrevalence = 0.2).inBounds())
        assertTrue(!ModelParameters(sn = 0.0).inBounds())
        assertTrue(!ModelParameters(sn = 1.5).inBounds())
        assertTrue(!ModelParameters(referencePrevalence = 0.0).inBounds())
        assertTrue(!ModelParameters(referencePrevalence = 1.0).inBounds())
        assertTrue(!ModelParameters(z95 = 0.5).inBounds())
        assertTrue(!ModelParameters(z95 = 4.0).inBounds())
    }
}

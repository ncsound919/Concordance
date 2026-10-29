package com.example

import com.example.concordance.research.experiment.Adjudicator
import com.example.concordance.research.experiment.Backends
import com.example.concordance.research.experiment.ClaimStatus
import com.example.concordance.research.experiment.ClaimUpdate
import com.example.concordance.research.experiment.CompileResult
import com.example.concordance.research.experiment.DecisionRule
import com.example.concordance.research.experiment.ExperimentCompiler
import com.example.concordance.research.experiment.ExperimentSpec
import com.example.concordance.research.experiment.Preregistration
import com.example.concordance.research.experiment.RunArtifact
import com.example.concordance.research.experiment.RunOutcome
import com.example.concordance.research.experiment.RunRecord
import com.example.concordance.research.experiment.RunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The only ground truth this project has from the pre-rescope system.
 *
 * On 2026-10-20 the edge pipeline adjudicated `giannoudis2024_pooled` end to end and minted
 * capsule `5e8d66a42322fc40`. The numbers below are copied from that capsule's stored payload.
 *
 * This test locks the **decision layer**: given the reported corrected estimate and interval, the
 * adjudicator must reach the same verdict the old Python path reached. It does not re-derive the
 * odds ratio - that numeric parity belongs to the Chaquopy lane, which will run the same
 * `concordance.correction` code on the device.
 */
class ReferenceCapsuleParityTest {

    private val at = "2026-10-20T00:58:26Z"
    private val lockedBy = "edge-e2e"

    private fun spec(threshold: Double): ExperimentSpec = ExperimentSpecTest.spec(
        id = "rp-giannoudis-2024",
        decisionRule = DecisionRule(
            metric = "corrected_or",
            comparison = DecisionRule.CI_BELOW,
            threshold = threshold,
            alpha = 0.05,
            powerTarget = 1.0,
        ),
    )

    private fun completed(spec: ExperimentSpec): RunRecord {
        val plan = (
            ExperimentCompiler.compile(
                spec, Backends.KAGGLE, Preregistration(spec.sha256(), at, lockedBy),
            ) as CompileResult.Ready
            ).plan
        var record = RunRecord.create("run-giannoudis", plan, at)
            .preregister(Preregistration(spec.sha256(), at, lockedBy), at)!!
        listOf(RunState.PLANNED, RunState.APPROVED, RunState.QUEUED, RunState.RUNNING, RunState.COMPLETED)
            .forEach { record = record.advance(it, at)!! }
        return record
    }

    private fun adjudicate(threshold: Double): ClaimUpdate {
        val spec = spec(threshold)
        val record = completed(spec)
        return Adjudicator.adjudicate(
            spec,
            record.plan,
            record,
            RunOutcome.Completed(
                listOf(
                    RunArtifact("results.json", "a".repeat(64), verified = true),
                    RunArtifact("session_info.json", "b".repeat(64), verified = true),
                )
            ),
            mapOf(
                "corrected_or" to CORRECTED_OR,
                "corrected_or.ci_low" to CORRECTED_CI_LOW,
                "corrected_or.ci_high" to CORRECTED_CI_HIGH,
            ),
        )
    }

    @Test
    fun theReferenceCapsuleIsReproducedAsSupported() {
        val update = adjudicate(SIGNIFICANCE_THRESHOLD_R)
        assertTrue(update.toString(), update is ClaimUpdate.Verdict)
        val verdict = update as ClaimUpdate.Verdict
        assertEquals(ClaimStatus.SUPPORTED, verdict.status)
        assertEquals("measured", verdict.evidenceTier)
        assertEquals(CORRECTED_OR, verdict.value, 0.0)
        assertEquals(CORRECTED_CI_LOW, verdict.ciLow!!, 0.0)
        assertEquals(CORRECTED_CI_HIGH, verdict.ciHigh!!, 0.0)
        // The interval must sit entirely below the significance threshold; that is the decision.
        assertTrue(verdict.ciHigh!! < SIGNIFICANCE_THRESHOLD_R)
    }

    @Test
    fun theSameIntervalAgainstAStricterThresholdIsInconclusive() {
        // A threshold below the upper bound is exactly the case that must not be reported as a
        // clean support - this is the guard against moving the goalposts after seeing the result.
        val update = adjudicate(0.30) as ClaimUpdate.Verdict
        assertEquals(ClaimStatus.INCONCLUSIVE, update.status)
    }

    companion object {
        /** Capsule `5e8d66a42322fc40`, created 2026-10-20T01:01:40Z. */
        const val CAPSULE_HASH = "5e8d66a42322fc40"
        const val CORRECTED_OR = 0.25749682567818344
        const val CORRECTED_CI_LOW = 0.19396588377469212
        const val CORRECTED_CI_HIGH = 0.3418364814678401
        const val SIGNIFICANCE_THRESHOLD_R = 0.4007100071376553
    }
}

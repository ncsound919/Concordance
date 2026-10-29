package com.example

import com.example.concordance.research.experiment.Adjudicator
import com.example.concordance.research.experiment.Backends
import com.example.concordance.research.experiment.ClaimStatus
import com.example.concordance.research.experiment.ClaimUpdate
import com.example.concordance.research.experiment.CompileResult
import com.example.concordance.research.experiment.ExecutedRun
import com.example.concordance.research.experiment.ExperimentClass
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

class AdjudicatorTest {

    private val at = "2026-10-28T12:00:00Z"

    private fun compile(spec: ExperimentSpec) = ExperimentCompiler.compile(
        spec,
        Backends.KAGGLE,
        Preregistration(spec.sha256(), at, "tap45"),
    ) as CompileResult.Ready

    private fun preregistration(spec: ExperimentSpec) = Preregistration(spec.sha256(), at, "tap45")

    /** A run that followed the rules: preregistered if confirmatory, then completed. */
    private fun completed(spec: ExperimentSpec): RunRecord {
        var record = RunRecord.create("run-1", compile(spec).plan, at)
        if (spec.confirmatory) {
            record = record.preregister(preregistration(spec), at)!!
        }
        listOf(RunState.PLANNED, RunState.APPROVED, RunState.QUEUED, RunState.RUNNING, RunState.COMPLETED)
            .forEach { record = record.advance(it, at)!! }
        return record
    }

    /** Same, but the preregistration event is omitted on purpose. */
    private fun completedWithoutPreregistration(spec: ExperimentSpec): RunRecord {
        var record = RunRecord.create("run-1", compile(spec).plan, at)
        listOf(RunState.PLANNED, RunState.APPROVED, RunState.QUEUED, RunState.RUNNING, RunState.COMPLETED)
            .forEach { record = record.advance(it, at)!! }
        return record
    }

    private fun completedOutcome(verified: Boolean = true) = RunOutcome.Completed(
        listOf(
            RunArtifact("results.json", "a".repeat(64), verified),
            RunArtifact("session_info.json", "b".repeat(64), verified),
        )
    )

    private fun metrics(value: Double, low: Double, high: Double) = mapOf(
        "retention_ratio_r" to value,
        "retention_ratio_r.ci_low" to low,
        "retention_ratio_r.ci_high" to high,
    )

    private fun refusalCode(update: ClaimUpdate): String =
        (update as ClaimUpdate.Refused).code

    @Test
    fun anExploratoryRunCannotUpdateAClaim() {
        val spec = ExperimentSpecTest.spec(confirmatory = false, decisionRule = null)
        val update = Adjudicator.adjudicate(spec, compile(spec).plan, completed(spec), completedOutcome())
        assertEquals("EXPLORATORY_RUN", refusalCode(update))
    }

    @Test
    fun aRunThatDidNotCompleteCannotBeAdjudicated() {
        val spec = ExperimentSpecTest.spec()
        val plan = compile(spec).plan
        val record = RunRecord.create("run-1", plan, at)
        val update = Adjudicator.adjudicate(spec, plan, record, completedOutcome())
        assertEquals("RUN_NOT_COMPLETED", refusalCode(update))
    }

    @Test
    fun aConfirmatoryRunWithoutAPreregistrationEventIsRefused() {
        val spec = ExperimentSpecTest.spec()
        val update = Adjudicator.adjudicate(
            spec,
            compile(spec).plan,
            completedWithoutPreregistration(spec),
            completedOutcome(),
            metrics(0.2575, 0.1940, 0.3418),
        )
        assertEquals("PREREGISTRATION_MISSING", refusalCode(update))
    }

    @Test
    fun aPreregistrationLockedAgainstAnotherSpecIsRefused() {
        val spec = ExperimentSpecTest.spec()
        val record = completed(spec).copy(
            preregistration = Preregistration("deadbeef".repeat(8), at, "tap45")
        )
        val update = Adjudicator.adjudicate(
            spec, compile(spec).plan, record, completedOutcome(), metrics(0.2575, 0.1940, 0.3418)
        )
        assertEquals("PREREGISTRATION_STALE", refusalCode(update))
    }

    @Test
    fun aMissingArtifactRefusesTheUpdate() {
        val spec = ExperimentSpecTest.spec()
        val outcome = RunOutcome.Completed(listOf(RunArtifact("results.json", "a".repeat(64), true)))
        assertEquals(
            "ARTIFACT_MISSING",
            refusalCode(Adjudicator.adjudicate(spec, compile(spec).plan, completed(spec), outcome)),
        )
    }

    @Test
    fun anUnverifiedArtifactRefusesTheUpdate() {
        val spec = ExperimentSpecTest.spec()
        assertEquals(
            "ARTIFACT_UNVERIFIED",
            refusalCode(
                Adjudicator.adjudicate(
                    spec, compile(spec).plan, completed(spec), completedOutcome(verified = false)
                )
            ),
        )
    }

    @Test
    fun aMissingMetricRefusesTheUpdate() {
        val spec = ExperimentSpecTest.spec()
        val update = Adjudicator.adjudicate(spec, compile(spec).plan, completed(spec), completedOutcome(), emptyMap())
        assertEquals("METRIC_MISSING", refusalCode(update))
    }

    @Test
    fun anIntervalEntirelyBelowTheThresholdSupportsTheClaim() {
        val spec = ExperimentSpecTest.spec()
        val update = Adjudicator.adjudicate(
            spec, compile(spec).plan, completed(spec), completedOutcome(), metrics(0.2575, 0.1940, 0.3418)
        )
        assertTrue(update.toString(), update is ClaimUpdate.Verdict)
        val verdict = update as ClaimUpdate.Verdict
        assertEquals(ClaimStatus.SUPPORTED, verdict.status)
        assertEquals("measured", verdict.evidenceTier)
        assertTrue(verdict.deviations.isEmpty())
    }

    @Test
    fun anIntervalStraddlingTheThresholdIsInconclusive() {
        val spec = ExperimentSpecTest.spec()
        val update = Adjudicator.adjudicate(
            spec, compile(spec).plan, completed(spec), completedOutcome(), metrics(0.45, 0.30, 0.55)
        ) as ClaimUpdate.Verdict
        assertEquals(ClaimStatus.INCONCLUSIVE, update.status)
    }

    @Test
    fun anIntervalEntirelyAboveTheThresholdContradictsTheClaim() {
        val spec = ExperimentSpecTest.spec()
        val update = Adjudicator.adjudicate(
            spec, compile(spec).plan, completed(spec), completedOutcome(), metrics(0.62, 0.50, 0.74)
        ) as ClaimUpdate.Verdict
        assertEquals(ClaimStatus.CONTRADICTED, update.status)
    }

    @Test
    fun aPointComparisonIsEvaluatedWithoutAnInterval() {
        val spec = ExperimentSpecTest.spec(
            decisionRule = ExperimentSpecTest.RULE.copy(comparison = "lt", metric = "r")
        )
        val update = Adjudicator.adjudicate(
            spec, compile(spec).plan, completed(spec), completedOutcome(), mapOf("r" to 0.25)
        ) as ClaimUpdate.Verdict
        assertEquals(ClaimStatus.SUPPORTED, update.status)
    }

    @Test
    fun aRuleThatNeedsAnIntervalWithoutOneIsRefused() {
        val spec = ExperimentSpecTest.spec()
        val update = Adjudicator.adjudicate(
            spec, compile(spec).plan, completed(spec), completedOutcome(), mapOf("retention_ratio_r" to 0.25)
        )
        assertEquals("CI_MISSING", refusalCode(update))
    }

    @Test
    fun anExecutionThatDriftedFromThePlanIsReportedAsDeviated() {
        val spec = ExperimentSpecTest.spec()
        val update = Adjudicator.adjudicate(
            spec,
            compile(spec).plan,
            completed(spec),
            completedOutcome(),
            metrics(0.2575, 0.1940, 0.3418),
            executed = ExecutedRun("concordance.paired", "0.4.0", 20261028L),
        ) as ClaimUpdate.Verdict
        assertEquals("deviated", update.evidenceTier)
        assertTrue(update.deviations.any { it.contains("0.4.0") })
    }

    @Test
    fun aPlanFromADifferentSpecVersionIsStale() {
        val original = ExperimentSpecTest.spec()
        val bumped = ExperimentSpecTest.spec(version = 2)
        val record = completed(original)
        val plan = compile(original).plan
        assertEquals("PLAN_STALE", refusalCode(Adjudicator.adjudicate(bumped, plan, record, completedOutcome())))
    }

    @Test
    fun aPlanEditedAfterCompilationIsDetected() {
        val spec = ExperimentSpecTest.spec()
        val tampered = compile(spec).plan.copy(kernelRef = "someone-else/kernel")
        val update = Adjudicator.adjudicate(
            spec, tampered, completed(spec), completedOutcome(), metrics(0.2575, 0.1940, 0.3418)
        )
        assertEquals("PLAN_TAMPERED", refusalCode(update))
    }

    @Test
    fun aClassThatDoesNotUpdateClaimsIsRefused() {
        val base = ExperimentSpecTest.spec()
        val spec = base.copy(experimentClass = ExperimentClass.NEGATIVE_CONTROL)
        val update = Adjudicator.adjudicate(
            spec, compile(spec).plan, completed(spec), completedOutcome(), metrics(0.25, 0.19, 0.34)
        )
        assertEquals("CLASS_CANNOT_UPDATE", refusalCode(update))
    }

    @Test
    fun anExecutorRefusalIsPassedThroughUnchanged() {
        val spec = ExperimentSpecTest.spec()
        val update = Adjudicator.adjudicate(
            spec, compile(spec).plan, completed(spec),
            RunOutcome.Refused("QUOTA_EXHAUSTED", "weekly GPU quota exhausted"),
        )
        val refused = update as ClaimUpdate.Refused
        assertEquals("QUOTA_EXHAUSTED", refused.code)
        assertEquals("weekly GPU quota exhausted", refused.reason)
    }
}

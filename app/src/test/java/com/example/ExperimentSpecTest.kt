package com.example

import com.example.concordance.research.experiment.DataAccess
import com.example.concordance.research.experiment.DataClass
import com.example.concordance.research.experiment.DatasetRef
import com.example.concordance.research.experiment.DecisionRule
import com.example.concordance.research.experiment.ExperimentClass
import com.example.concordance.research.experiment.ExperimentSpec
import com.example.concordance.research.experiment.MethodRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentSpecTest {

    @Test
    fun aConfirmatorySpecWithoutADecisionRuleIsRefused() {
        val spec = spec(decisionRule = null, confirmatory = true)
        val codes = spec.problems().map { it.code }
        assertTrue("expected NO_DECISION_RULE, got $codes", codes.contains("NO_DECISION_RULE"))
        assertFalse(spec.isValid())
    }

    @Test
    fun anExploratorySpecMayHaveNoDecisionRule() {
        val spec = spec(decisionRule = null, confirmatory = false)
        assertTrue(spec.problems().toString(), spec.isValid())
    }

    @Test
    fun blankIdsAndTitlesAreRefused() {
        val codes = spec(id = "", title = "").problems().map { it.code }
        assertTrue(codes.contains("ID_BLANK"))
        assertTrue(codes.contains("TITLE"))
    }

    @Test
    fun malformedIdIsRefused() {
        val codes = spec(id = "Has Spaces And Caps").problems().map { it.code }
        assertTrue(codes.contains("ID_FORMAT"))
    }

    @Test
    fun anUnsupportedComparisonIsRefusedRatherThanGuessed() {
        val spec = spec(decisionRule = RULE.copy(comparison = "roughly_around"))
        val codes = spec.problems().map { it.code }
        assertTrue(codes.contains("RULE_COMPARISON"))
    }

    @Test
    fun aNonFiniteThresholdIsRefused() {
        val spec = spec(decisionRule = RULE.copy(threshold = Double.NaN))
        assertTrue(spec.problems().map { it.code }.contains("RULE_THRESHOLD"))
    }

    @Test
    fun hashIsStableAcrossEquivalentConstruction() {
        val a = spec()
        val b = spec(datasets = a.datasets.reversed(), params = mapOf("ci" to "0.95", "correction" to "delta_method"))
        assertEquals(a.sha256(), b.sha256())
    }

    @Test
    fun hashChangesWhenAnyMaterialFieldChanges() {
        val base = spec()
        assertNotEquals(base.sha256(), spec(title = "a different title").sha256())
        assertNotEquals(base.sha256(), spec(version = 2).sha256())
        assertNotEquals(base.sha256(), spec(params = mapOf("ci" to "0.90", "correction" to "delta_method")).sha256())
        assertNotEquals(base.sha256(), spec(decisionRule = RULE.copy(threshold = 0.41)).sha256())
    }

    @Test
    fun hashIsSixtyFourLowercaseHex() {
        val hash = spec().sha256()
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(16, spec().shortHash().length)
    }

    @Test
    fun anUnpinnedDatasetIsReportedButDoesNotInvalidateTheSpec() {
        val spec = spec(datasets = listOf(DATASET.copy(sha256 = "")))
        assertTrue(spec.isValid())
        assertFalse(spec.isPinned())
        assertEquals(1, spec.unpinnedDatasets().size)
    }

    @Test
    fun wetLabAndHypothesisClassesAreNotExecutable() {
        assertFalse(ExperimentClass.WETLAB_DESIGN.isExecutable)
        assertFalse(ExperimentClass.HYPOTHESIS_GENESIS.isExecutable)
        assertFalse(ExperimentClass.UNKNOWN.isExecutable)
        assertTrue(ExperimentClass.REPLICATION.isExecutable)
        assertTrue(ExperimentClass.NEGATIVE_CONTROL.isExecutable)
    }

    @Test
    fun anUnknownClassIsASpecProblemRatherThanAnExecutableDefault() {
        val spec = spec().copy(experimentClass = ExperimentClass.UNKNOWN)
        assertTrue(spec.problems().map { it.code }.contains("CLASS_UNKNOWN"))
    }

    @Test
    fun anUnknownDataClassIsASpecProblem() {
        val spec = spec(datasets = listOf(DATASET.copy(dataClass = DataClass.UNKNOWN)))
        assertTrue(spec.problems().map { it.code }.contains("DATASET_CLASS_UNKNOWN"))
    }

    @Test
    fun anUnknownAccessIsASpecProblem() {
        val spec = spec(datasets = listOf(DATASET.copy(access = DataAccess.UNKNOWN)))
        assertTrue(spec.problems().map { it.code }.contains("DATASET_ACCESS_UNKNOWN"))
    }

    @Test
    fun phiNeverLeavesTheHost() {
        assertFalse(DataClass.PHI.mayLeaveHost)
        // An unparseable class must fail closed, not be treated as aggregate.
        assertFalse(DataClass.UNKNOWN.mayLeaveHost)
        assertTrue(DataClass.AGGREGATE.mayLeaveHost)
        assertTrue(DataClass.DERIVED.mayLeaveHost)
    }

    companion object {
        val RULE = DecisionRule(
            metric = "retention_ratio_r",
            comparison = DecisionRule.CI_BELOW,
            threshold = 0.40,
            alpha = 0.05,
            powerTarget = 0.80,
            multiplicity = "none",
        )

        val DATASET = DatasetRef(
            ref = "cbioportal/esr1-plasma-tissue-paired",
            version = "2026-07-01",
            sha256 = "a".repeat(64),
            license = "CC-BY-4.0",
            access = DataAccess.PUBLIC,
            dataClass = DataClass.AGGREGATE,
        )

        fun spec(
            id: String = "rp-esr1-cns-001",
            version: Int = 1,
            title: String = "Replicate ESR1 CNS discordance",
            datasets: List<DatasetRef> = listOf(DATASET),
            params: Map<String, String> = mapOf("correction" to "delta_method", "ci" to "0.95"),
            decisionRule: DecisionRule? = RULE,
            confirmatory: Boolean = true,
            budgetGpuMinutes: Int = 0,
        ) = ExperimentSpec(
            id = id,
            version = version,
            experimentClass = ExperimentClass.REPLICATION,
            title = title,
            hypothesis = "Plasma ctDNA under-detects ESR1 in CNS metastases",
            direction = "discordance_higher_in_cns",
            design = "paired_diagnostic_accuracy",
            unit = "patient",
            contrast = "CNS vs non-CNS",
            outcome = "tissue positive and plasma negative",
            datasets = datasets,
            method = MethodRef(
                pipeline = "concordance.paired",
                version = "0.3.0",
                imageDigest = "sha256:" + "b".repeat(64),
                params = params,
                seed = 20261028L,
            ),
            decisionRule = decisionRule,
            confirmatory = confirmatory,
            expectedArtifacts = listOf("results.json", "session_info.json"),
            budgetCpuMinutes = 15,
            budgetGpuMinutes = budgetGpuMinutes,
        )
    }
}

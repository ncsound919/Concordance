package com.example

import com.example.concordance.research.experiment.Backends
import com.example.concordance.research.experiment.CompileResult
import com.example.concordance.research.experiment.DataAccess
import com.example.concordance.research.experiment.DataClass
import com.example.concordance.research.experiment.ExperimentClass
import com.example.concordance.research.experiment.ExperimentCompiler
import com.example.concordance.research.experiment.Preregistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentCompilerTest {

    private fun preregFor(specHash: String) =
        Preregistration(specHash = specHash, lockedAtUtc = "2026-10-28T12:00:00Z", lockedBy = "tap45")

    private fun codes(result: CompileResult): List<String> =
        (result as CompileResult.Refused).problems.map { it.code }

    @Test
    fun aPinnedPreregisteredSpecCompilesToAPlan() {
        val spec = ExperimentSpecTest.spec()
        val result = ExperimentCompiler.compile(spec, Backends.KAGGLE, preregFor(spec.sha256()))
        assertTrue(result.toString(), result is CompileResult.Ready)
        val plan = (result as CompileResult.Ready).plan
        assertEquals("kaggle", plan.backendId)
        assertEquals(spec.sha256(), plan.specHash)
        assertEquals(64, plan.planHash.length)
        assertEquals("/kaggle/input/cbioportal_esr1-plasma-tissue-paired/2026-07-01", plan.datasets[0].mountPath)
        // The stock Kaggle image is not pinned, so the plan declares STATISTICAL determinism and
        // carries a seed list rather than a single seed.
        assertEquals(com.example.concordance.research.experiment.Determinism.STATISTICAL, plan.determinism)
        assertEquals(listOf(20261028L, 20261029L, 20261030L), plan.seeds)
        assertEquals(listOf("results.json", "session_info.json"), plan.requiredArtifacts)
    }

    @Test
    fun planHashIsDeterministic() {
        val spec = ExperimentSpecTest.spec()
        val prereg = preregFor(spec.sha256())
        val a = ExperimentCompiler.compile(spec, Backends.KAGGLE, prereg) as CompileResult.Ready
        val b = ExperimentCompiler.compile(spec, Backends.KAGGLE, prereg) as CompileResult.Ready
        assertEquals(a.plan.planHash, b.plan.planHash)
    }

    @Test
    fun aConfirmatoryRunWithoutPreregistrationIsRefused() {
        val result = ExperimentCompiler.compile(ExperimentSpecTest.spec(), Backends.KAGGLE, null)
        assertTrue(codes(result).contains("PREREGISTRATION_REQUIRED"))
    }

    @Test
    fun aStalePreregistrationIsRefused() {
        val result = ExperimentCompiler.compile(
            ExperimentSpecTest.spec(),
            Backends.KAGGLE,
            preregFor("deadbeef".repeat(8)),
        )
        assertTrue(codes(result).contains("PREREGISTRATION_STALE"))
    }

    @Test
    fun anUnpinnedDatasetIsRefused() {
        val spec = ExperimentSpecTest.spec(
            datasets = listOf(ExperimentSpecTest.DATASET.copy(sha256 = ""))
        )
        val result = ExperimentCompiler.compile(spec, Backends.KAGGLE, preregFor(spec.sha256()))
        assertTrue(codes(result).contains("DATASET_UNPINNED"))
    }

    @Test
    fun anUnpinnedImageIsRefusedOnAReproducibleBackend() {
        val base = ExperimentSpecTest.spec()
        val spec = base.copy(method = base.method.copy(imageDigest = ""))
        val result = ExperimentCompiler.compile(spec, Backends.KAGGLE_PINNED, preregFor(spec.sha256()))
        assertTrue(codes(result).contains("IMAGE_UNPINNED"))
    }

    @Test
    fun theStockKaggleImageDoesNotRequireADigestBecauseItIsStatistical() {
        val base = ExperimentSpecTest.spec()
        val spec = base.copy(method = base.method.copy(imageDigest = ""))
        val result = ExperimentCompiler.compile(spec, Backends.KAGGLE, preregFor(spec.sha256()))
        assertTrue(result.toString(), result is CompileResult.Ready)
        assertEquals(
            com.example.concordance.research.experiment.Determinism.STATISTICAL,
            (result as CompileResult.Ready).plan.determinism,
        )
    }

    @Test
    fun phiClassDataCannotGoToKaggle() {
        val spec = ExperimentSpecTest.spec(
            datasets = listOf(
                ExperimentSpecTest.DATASET.copy(
                    ref = "pdxnet/paired-lines",
                    access = DataAccess.CONTROLLED,
                    dataClass = DataClass.PHI,
                )
            )
        )
        val result = ExperimentCompiler.compile(spec, Backends.KAGGLE, preregFor(spec.sha256()))
        val codes = codes(result)
        assertTrue(codes.contains("DATA_CLASS_BLOCKED"))
        assertTrue(codes.contains("CONTROLLED_DATA_REMOTE"))
    }

    @Test
    fun controlledDataIsAllowedOnThePhoneBecauseNothingLeavesIt() {
        val spec = ExperimentSpecTest.spec(
            datasets = listOf(
                ExperimentSpecTest.DATASET.copy(
                    ref = "pdxnet/paired-lines",
                    access = DataAccess.CONTROLLED,
                    dataClass = DataClass.DERIVED,
                )
            )
        )
        val result = ExperimentCompiler.compile(spec, Backends.PHONE, preregFor(spec.sha256()))
        assertTrue(result.toString(), result is CompileResult.Ready)
        assertEquals("file:///data/pdxnet/paired-lines/2026-07-01", (result as CompileResult.Ready).plan.datasets[0].mountPath)
    }

    @Test
    fun phiClassDataCannotGoToKaggleEvenWhenItIsPublic() {
        // Access and residency are different questions: a public dataset can still be PHI-class.
        val spec = ExperimentSpecTest.spec(
            datasets = listOf(
                ExperimentSpecTest.DATASET.copy(dataClass = DataClass.PHI, access = DataAccess.PUBLIC)
            )
        )
        val result = ExperimentCompiler.compile(spec, Backends.KAGGLE, preregFor(spec.sha256()))
        assertTrue(codes(result).contains("DATA_CLASS_BLOCKED"))
    }

    @Test
    fun aPublicDatasetOnThePhoneStillResolvesToALocalPath() {
        val spec = ExperimentSpecTest.spec()
        val result = ExperimentCompiler.compile(spec, Backends.PHONE, preregFor(spec.sha256()))
        assertTrue(result.toString(), result is CompileResult.Ready)
        assertEquals(
            "file:///data/cbioportal/esr1-plasma-tissue-paired/2026-07-01",
            (result as CompileResult.Ready).plan.datasets[0].mountPath,
        )
    }

    @Test
    fun aDesignArtifactIsNeverSubmitted() {
        val base = ExperimentSpecTest.spec()
        val spec = base.copy(experimentClass = ExperimentClass.WETLAB_DESIGN, confirmatory = false)
        val result = ExperimentCompiler.compile(spec, Backends.KAGGLE, preregFor(spec.sha256()))
        assertTrue(codes(result).contains("CLASS_NOT_EXECUTABLE"))
    }

    @Test
    fun anOverBudgetRunIsRefused() {
        val spec = ExperimentSpecTest.spec(budgetGpuMinutes = 1000)
        val result = ExperimentCompiler.compile(spec, Backends.KAGGLE, preregFor(spec.sha256()))
        assertTrue(codes(result).contains("BUDGET_GPU"))
    }

    @Test
    fun aClassTheBackendCannotRunIsRefused() {
        val base = ExperimentSpecTest.spec()
        val spec = base.copy(experimentClass = ExperimentClass.SIMULATION)
        val result = ExperimentCompiler.compile(spec, Backends.PHONE, preregFor(spec.sha256()))
        assertTrue(codes(result).contains("BACKEND_UNSUPPORTED"))
    }

    @Test
    fun refusalsAreDeduplicated() {
        val base = ExperimentSpecTest.spec()
        val spec = base.copy(id = "", title = "")
        val result = ExperimentCompiler.compile(spec, Backends.KAGGLE, null)
        val codes = codes(result)
        assertEquals(codes.distinct(), codes)
    }
}

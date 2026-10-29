package com.example

import android.app.Application
import com.example.concordance.research.experiment.Backends
import com.example.concordance.research.experiment.CatalogEntry
import com.example.concordance.research.experiment.CompileResult
import com.example.concordance.research.experiment.DataAccess
import com.example.concordance.research.experiment.ExperimentCatalog
import com.example.concordance.research.experiment.ExperimentCompiler
import com.example.concordance.research.experiment.Preregistration
import com.example.concordance.research.experiment.SpecStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ExperimentCatalogTest {

    private fun catalog(): List<CatalogEntry> {
        val app: Application = RuntimeEnvironment.getApplication()
        val json = app.assets.open("experiment-specs.json").bufferedReader().use { it.readText() }
        return ExperimentCatalog.parse(json)
    }

    private fun parseOne(json: String): CatalogEntry = ExperimentCatalog.parse(json).single()

    @Test
    fun theBundledCatalogParses() {
        val entries = catalog()
        assertEquals(5, entries.size)
        assertTrue(entries.all { it.id.isNotBlank() && it.title.isNotBlank() })
        assertTrue(entries.all { it.reason.isNotBlank() })
    }

    @Test
    fun everyReadyEntryActuallyCompiles() {
        // READY must mean "would compile", not "the author felt optimistic". This check runs
        // forward: when a dataset and image are pinned, the entry must still pass the compiler.
        val entries = catalog()
        entries.filter { it.status == SpecStatus.READY }.forEach { entry ->
            val result = ExperimentCompiler.compile(
                entry.spec,
                Backends.KAGGLE,
                Preregistration(entry.spec.sha256(), "2026-10-28T12:00:00Z", "catalog-test"),
            )
            assertTrue("${entry.id} is READY but does not compile: $result", result is CompileResult.Ready)
        }
    }

    @Test
    fun theCatalogAccountsForEveryEntry() {
        val entries = catalog()
        val counted = SpecStatus.values().sumOf { status -> entries.count { it.status == status } }
        assertEquals(entries.size, counted)
        assertEquals(1, entries.count { it.status == SpecStatus.DESIGN_ONLY })
        // The three ESR1 specs are pinned to the bundled synthetic fixture and are runnable.
        assertEquals(3, entries.count { it.status == SpecStatus.READY })
        // Unsupported entries must say why, not just fail.
        assertTrue(
            entries.filter { it.status == SpecStatus.UNSUPPORTED }
                .all { it.reason.length > 10 }
        )
    }

    @Test
    fun wetLabWorkIsDesignOnlyAndNeverReady() {
        val wetlab = catalog().first { it.id == "xd-02-design" }
        assertEquals(SpecStatus.DESIGN_ONLY, wetlab.status)
        assertTrue(wetlab.reason.contains("design artifact"))
    }

    @Test
    fun anUnpinnedDatasetIsTheReportedReason() {
        // Everything else is present, so the pin is the only thing missing.
        val unpinned = ExperimentSpecTest.spec(
            datasets = listOf(ExperimentSpecTest.DATASET.copy(version = "", sha256 = ""))
        )
        val (status, reason) = ExperimentCatalog.statusFor(unpinned)
        assertEquals(SpecStatus.UNSUPPORTED, status)
        assertTrue(reason, reason.contains("not pinned"))
    }

    @Test
    fun aSpecWithNoDecisionRuleSaysSoRatherThanBlamingItsData() {
        val program = catalog().first { it.id == "xd-01-program" }
        assertEquals(SpecStatus.UNSUPPORTED, program.status)
        assertTrue(program.reason, program.reason.contains("decision rule"))
    }

    @Test
    fun controlledAccessDataOnKaggleIsNotReady() {
        // The predicate the duplicated rule list used to miss; now it comes from the compiler.
        val spec = ExperimentSpecTest.spec(
            datasets = listOf(ExperimentSpecTest.DATASET.copy(access = DataAccess.CONTROLLED))
        )
        val (status, reason) = ExperimentCatalog.statusFor(spec)
        assertEquals(SpecStatus.UNSUPPORTED, status)
        assertTrue(reason, reason.contains("controlled-access"))
    }

    @Test
    fun anOverBudgetSpecIsNotReady() {
        val (status, reason) = ExperimentCatalog.statusFor(ExperimentSpecTest.spec(budgetGpuMinutes = 1000))
        assertEquals(SpecStatus.UNSUPPORTED, status)
        assertTrue(reason, reason.contains("GPU minutes"))
    }

    @Test
    fun aFullyPinnedSpecWithARuleIsReady() {
        val (status, reason) = ExperimentCatalog.statusFor(ExperimentSpecTest.spec())
        assertEquals(reason, SpecStatus.READY, status)
    }

    @Test
    fun aPinnedSpecWithAnUnpinnedImageIsNotReadyOnAPinnedBackend() {
        val base = ExperimentSpecTest.spec()
        val unpinnedImage = base.copy(method = base.method.copy(imageDigest = ""))
        // Only a backend that claims bitwise reproducibility needs a pinned image.
        val (status, reason) = ExperimentCatalog.statusFor(unpinnedImage, Backends.KAGGLE_PINNED)
        assertEquals(SpecStatus.UNSUPPORTED, status)
        assertTrue(reason, reason.contains("image"))
    }

    @Test
    fun aPinnedSpecWithoutADecisionRuleExplainsItself() {
        val noRule = ExperimentSpecTest.spec(decisionRule = null, confirmatory = false)
        val (status, reason) = ExperimentCatalog.statusFor(noRule)
        assertEquals(SpecStatus.UNSUPPORTED, status)
        assertTrue(reason.contains("decision rule"))
    }

    @Test
    fun anUnknownClassFailsClosed() {
        val entry = parseOne(unpinnedSpecJson(rawClass = "NOT_A_REAL_CLASS"))
        assertEquals(SpecStatus.UNSUPPORTED, entry.status)
        assertTrue(entry.reason, entry.reason.contains("not a known value"))
    }

    @Test
    fun anUnknownDataClassFailsClosed() {
        // The dangerous case: a typo in a data class must never be read as "aggregate".
        val entry = parseOne(unpinnedSpecJson(rawClass = "REPLICATION", rawDataClass = "PHII"))
        assertEquals(SpecStatus.UNSUPPORTED, entry.status)
        assertTrue(entry.reason, entry.reason.contains("dataClass"))
    }

    @Test
    fun anUnknownAccessFailsClosed() {
        val entry = parseOne(unpinnedSpecJson(rawClass = "REPLICATION", rawAccess = "EVERYWHERE"))
        assertEquals(SpecStatus.UNSUPPORTED, entry.status)
        assertTrue(entry.reason, entry.reason.contains("access"))
    }

    @Test
    fun orderingIsReadyFirstThenDesignThenUnsupported() {
        val statuses = catalog().map { it.status }
        assertEquals(statuses.sortedBy { it.ordinal }, statuses)
    }

    private fun unpinnedSpecJson(
        rawClass: String,
        rawDataClass: String = "AGGREGATE",
        rawAccess: String = "PUBLIC",
    ): String = """
        [{
          "id": "weird-001",
          "version": 1,
          "class": "$rawClass",
          "title": "t", "hypothesis": "h", "direction": "d", "design": "x",
          "unit": "u", "contrast": "c", "outcome": "o",
          "datasets": [{"ref":"r","version":"1","sha256":"","license":"MIT",
                        "access":"$rawAccess","dataClass":"$rawDataClass"}],
          "method": {"pipeline":"p","version":"1","imageDigest":"","seed":0},
          "confirmatory": false,
          "expectedArtifacts": ["a.json"]
        }]
    """.trimIndent()
}

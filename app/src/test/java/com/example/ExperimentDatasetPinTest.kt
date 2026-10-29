package com.example

import com.example.concordance.integration.kaggle.KaggleComputeManager
import com.example.concordance.research.experiment.DataClass
import com.example.concordance.research.experiment.ExperimentCatalog
import com.example.concordance.research.experiment.SpecStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.security.MessageDigest

/**
 * The bundled fixture spec must be pinned to the bytes the app actually generates.
 *
 * A pin that does not match the data is worse than no pin: it looks reproducible and is not.
 * This recomputes the SHA-256 of `KaggleComputeManager`'s first preset cohort and compares it
 * with `assets/experiment-specs.json`.
 */
@RunWith(RobolectricTestRunner::class)
class ExperimentDatasetPinTest {

    private fun catalog() = ExperimentCatalog.parse(
        RuntimeEnvironment.getApplication()
            .assets.open("experiment-specs.json").bufferedReader().use { it.readText() }
    )

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test
    fun theFixtureSpecIsPinnedToTheBundledCsv() {
        val spec = catalog().first { it.id == "rp-esr1-cns-001" }.spec
        val dataset = spec.datasets.single()

        val csv = KaggleComputeManager.availableDatasets.value.first().csvData
        // Byte count is asserted so an accidental change to the generated cohort fails loudly
        // even if someone "fixes" the hash to match it.
        assertEquals(22020, csv.toByteArray(Charsets.UTF_8).size)

        assertEquals("fixture/esr1-paired-synthetic", dataset.ref)
        assertEquals("2026-10-29", dataset.version)
        assertEquals(sha256(csv), dataset.sha256)
        assertEquals(DataClass.SYNTHETIC, dataset.dataClass)
    }

    @Test
    fun thePinnedFixtureIsRunnableButCannotBecomeAClaim() {
        val entry = catalog().first { it.id == "rp-esr1-cns-001" }
        assertEquals(SpecStatus.READY, entry.status)
        // Ready to run; the adjudicator still refuses a synthetic dataset.
        assertEquals(DataClass.SYNTHETIC, entry.spec.datasets.single().dataClass)
    }

    @Test
    fun everyPinnedDatasetHasAVersionAndAHash() {
        catalog().forEach { entry ->
            entry.spec.datasets.forEach { dataset ->
                if (dataset.sha256.isNotBlank()) {
                    assertEquals("${entry.id}/${dataset.ref} version", 10, dataset.version.length)
                    assertEquals("${entry.id}/${dataset.ref} hash", 64, dataset.sha256.length)
                }
            }
        }
    }
}

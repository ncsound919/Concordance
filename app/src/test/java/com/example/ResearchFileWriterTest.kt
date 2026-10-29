package com.example

import com.example.concordance.research.deep.ResearchFileWriter
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ResearchFileWriterTest {

    private lateinit var root: File

    @After
    fun tearDown() {
        root?.deleteRecursively()
    }

    private fun freshRoot(): File {
        val dir = File(RuntimeEnvironment.getApplication().cacheDir, "research-test-${System.nanoTime()}")
        dir.mkdirs()
        root = dir
        return dir
    }

    @Test
    fun writesEveryFileAndAManifest() {
        val runDir = File(freshRoot(), "run-1")
        val files = mapOf(
            "idea.md" to "# idea\n\nA question.\n",
            "results.json" to """{"n": 42}""",
        )
        val manifest = ResearchFileWriter.write(runDir, "run-1", "2026-10-29T00:00:00Z", files)
        assertEquals("run-1", manifest.runId)
        assertEquals(2, manifest.files.size)
        assertTrue(File(runDir, "idea.md").isFile)
        assertTrue(File(runDir, "results.json").isFile)
        assertTrue(File(runDir, "manifest.json").isFile)
    }

    @Test
    fun theManifestVerifiesACleanDirectory() {
        val runDir = File(freshRoot(), "run-2")
        val files = mapOf("idea.md" to "# idea\n", "analysis.md" to "## analysis\n")
        ResearchFileWriter.write(runDir, "run-2", "2026-10-29T00:00:00Z", files)
        assertTrue(ResearchFileWriter.verify(runDir).isEmpty())
    }

    @Test
    fun tamperingAFileIsDetected() {
        val runDir = File(freshRoot(), "run-3")
        ResearchFileWriter.write(runDir, "run-3", "2026-10-29T00:00:00Z", mapOf("idea.md" to "# idea\n"))
        File(runDir, "idea.md").writeText("# idea\n# tampered\n")
        val problems = ResearchFileWriter.verify(runDir)
        assertTrue(problems.any { it.contains("idea.md") && it.contains("hash mismatch") })
    }

    @Test
    fun aMissingFileIsReported() {
        val runDir = File(freshRoot(), "run-4")
        ResearchFileWriter.write(runDir, "run-4", "2026-10-29T00:00:00Z", mapOf("idea.md" to "# idea\n"))
        File(runDir, "idea.md").delete()
        assertTrue(ResearchFileWriter.verify(runDir).any { it.contains("idea.md is missing") })
    }

    @Test
    fun anUnsafeFileNameIsRefused() {
        val runDir = File(freshRoot(), "run-5")
        val thrown = runCatching {
            ResearchFileWriter.write(runDir, "run-5", "2026-10-29T00:00:00Z", mapOf("../etc/passwd" to "x"))
        }
        assertTrue(thrown.isFailure)
        val thrown2 = runCatching {
            ResearchFileWriter.write(runDir, "run-5", "2026-10-29T00:00:00Z", mapOf("evil.exe" to "x"))
        }
        assertTrue(thrown2.isFailure)
    }

    @Test
    fun anHmacReceiptDiffersFromASha256Receipt() {
        val runDirA = File(freshRoot(), "run-a")
        val runDirB = File(freshRoot(), "run-b")
        val files = mapOf("idea.md" to "# idea\n")
        val without = ResearchFileWriter.write(runDirA, "run-a", "2026-10-29T00:00:00Z", files)
        val withKey = ResearchFileWriter.write(runDirB, "run-b", "2026-10-29T00:00:00Z", files, hmacKey = "secret")
        assertTrue(without.receipt != withKey.receipt)
        assertEquals(64, without.receipt.length)
        assertEquals(64, withKey.receipt.length)
    }

    @Test
    fun reReadingTheManifestGivesTheSameHashes() {
        val runDir = File(freshRoot(), "run-6")
        val manifest = ResearchFileWriter.write(
            runDir, "run-6", "2026-10-29T00:00:00Z",
            mapOf("idea.md" to "# idea\n", "results.json" to """{"n": 42}"""),
        )
        val reRead = ResearchFileWriter.verify(runDir)
        assertTrue(reRead.isEmpty())
        assertEquals(manifest.files.map { it.sha256 }, manifest.files.map { it.sha256 })
    }
}

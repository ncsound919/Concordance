package com.example

import com.example.concordance.autonomy.CorpusDatabase
import com.example.concordance.autonomy.CorpusStore
import com.example.concordance.autonomy.LocalCorpus
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The corpus outgrew memory: a folder of thousands of papers cannot be held in a list, so the
 * text now lives in SQLite with an FTS4 index, and a pass records where it stopped. These tests
 * cover the parts that used to be pure data structures — what is stored, what a re-index
 * replaces, what a search returns — against a real database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CorpusDatabaseTest {

    private lateinit var db: CorpusDatabase

    @Before
    fun setUp() {
        db = CorpusDatabase(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        db.close()
        LocalCorpus.resetForTests()
    }

    @Test
    fun writingAFileStoresItsTextAndCounts() {
        db.writeFile(
            uri = "content://tree/paper.pdf",
            name = "Giannoudis 2024.pdf",
            mtime = 1_000L,
            size = 4_096L,
            isPdf = true,
            pieces = listOf("EGFR L858R appears in brain metastasis", "and in the leptomeninges"),
            textless = false,
        )

        val counts = db.counts()
        assertEquals(1, counts.files)
        assertEquals(2, counts.chunks)
        assertEquals(1, counts.pdfs)
        assertEquals(0, counts.textless)
    }

    @Test
    fun aTextlessPdfIsRecordedSoItIsNotRetriedEveryPass() {
        db.writeFile(
            uri = "content://tree/scan.pdf",
            name = "scan.pdf",
            mtime = 5L,
            size = 90L,
            isPdf = true,
            pieces = emptyList(),
            textless = true,
        )

        val meta = db.fileMeta()["content://tree/scan.pdf"]
        assertNotNull(meta)
        assertTrue(meta!!.textless)
        assertEquals(0, meta.chunks)
        assertEquals(1, db.counts().textless)
    }

    @Test
    fun reIndexingAChangedFileReplacesItsOldChunks() {
        val uri = "content://tree/notes.md"
        db.writeFile(uri, "notes.md", 1L, 10L, false, listOf("bevacizumab response"), false)
        db.writeFile(uri, "notes.md", 2L, 11L, false, listOf("temozolomide response"), false)

        assertEquals(1, db.counts().chunks)
        assertTrue("the replaced text must leave the index", db.search("\"bevacizumab\"", 10).isEmpty())
        assertEquals(1, db.search("\"temozolomide\"", 10).size)
    }

    @Test
    fun searchMatchesTermsRegardlessOfCaseAndDoesNotMatchOtherText() {
        db.writeFile(
            uri = "content://tree/a.pdf",
            name = "a.pdf",
            mtime = 1L,
            size = 1L,
            isPdf = true,
            pieces = listOf("EGFR amplification in glioblastoma"),
            textless = false,
        )
        db.writeFile(
            uri = "content://tree/b.pdf",
            name = "b.pdf",
            mtime = 1L,
            size = 1L,
            isPdf = true,
            pieces = listOf("BRCA1 loss in ovarian carcinoma"),
            textless = false,
        )

        val hits = db.search("\"egfr\"", 10)
        assertEquals(1, hits.size)
        assertEquals("a.pdf", hits.single().source)
        // A space between terms is implicit AND in FTS4's standard query syntax.
        assertTrue(db.search("\"egfr\" \"brca1\"", 10).isEmpty())
        assertTrue(db.search("\"pancreas\"", 10).isEmpty())
    }

    @Test
    fun theResumeCursorAndSweepTotalSurviveInTheDatabase() {
        assertNull(db.cursor())
        db.setCursor("Breast\u0001Chen 2023.pdf")
        assertEquals("Breast\u0001Chen 2023.pdf", db.cursor())
        db.setCursor(null)
        assertNull(db.cursor())

        assertEquals(0, db.sweptSupported())
        db.recordSweep(7_650)
        assertEquals(7_650, db.sweptSupported())
    }

    @Test
    fun clearingEmptiesTheCorpusAndLeavesTheScanRowUsable() {
        db.writeFile("content://tree/a.pdf", "a.pdf", 1L, 1L, true, listOf("melanoma"), false)
        db.setCursor("a.pdf")
        db.recordSweep(1)

        db.clearAll()

        assertEquals(0, db.counts().files)
        assertEquals(0, db.counts().chunks)
        assertNull(db.cursor())
        assertEquals(0, db.sweptSupported())
        assertTrue(db.search("\"melanoma\"", 10).isEmpty())
        // Still writable: the scan row must not have been deleted with the rest.
        db.setCursor("b.pdf")
        assertEquals("b.pdf", db.cursor())
    }

    /**
     * The previous version cached its text in a JSON file. That corpus is imported once, so the
     * papers already read are not extracted a second time, and searching works from the start.
     */
    @Test
    fun thePreviousJsonIndexIsImportedOnceAndSearchable() {
        val app = RuntimeEnvironment.getApplication()
        val legacy = File(app.filesDir, "corpus_index.json")
        val entry = JSONObject().apply {
            put("uri", "content://tree/legacy.pdf")
            put("name", "legacy.pdf")
            put("mtime", 42L)
            put("size", 900L)
            put("isPdf", true)
            put("text", "EGFR L858R retention in brain metastasis after osimertinib")
        }
        legacy.writeText(JSONArray().put(entry).toString())

        assertEquals(1, CorpusStore.readLegacyIndex(app).size)

        LocalCorpus.init(app)

        val status = LocalCorpus.status.value
        assertEquals("note=${status.note}", 1, status.files)
        assertEquals(1, status.pdfs)
        assertEquals(1, status.chunks)
        assertFalse("the imported file should not be read again", legacy.exists())

        val probe = CorpusDatabase(app)
        val indexed = probe.search("\"egfr\"", 10).size
        probe.close()

        val hits = runBlocking { LocalCorpus.search("egfr brain metastasis") }
        assertEquals("fts rows=$indexed", 1, hits.size)
        assertEquals("legacy.pdf", hits.single().source)

        // A second start must not import it again (the file is gone, the rows remain).
        LocalCorpus.resetForTests()
        LocalCorpus.init(app)
        assertEquals(1, LocalCorpus.status.value.files)
        assertEquals(1, LocalCorpus.status.value.chunks)
    }
}

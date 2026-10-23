package com.example

import com.example.concordance.autonomy.CorpusIndex
import com.example.concordance.autonomy.LocalCorpus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CorpusIndexTest {

    @Test
    fun readsPapersAndNotesButNotImages() {
        assertTrue(CorpusIndex.isSupported("Giannoudis 2024.pdf"))
        assertTrue(CorpusIndex.isSupported("notes.MD"))
        assertTrue(CorpusIndex.isSupported("table.csv"))
        assertTrue(CorpusIndex.isSupported("cohort.tsv"))
        assertTrue(CorpusIndex.isSupported("results.json"))
        assertTrue(CorpusIndex.isSupported("readme.txt"))
        assertFalse(CorpusIndex.isSupported("figure.png"))
        assertFalse(CorpusIndex.isSupported("scan.jpg"))
        assertFalse(CorpusIndex.isSupported("no-extension"))
    }

    @Test
    fun extensionParsingIgnoresCaseAndDotsInTheName() {
        assertEquals("pdf", CorpusIndex.extensionOf("v1.2 paper.PDF"))
        assertEquals("", CorpusIndex.extensionOf("trailingdot."))
        assertEquals("", CorpusIndex.extensionOf("plain"))
    }

    @Test
    fun shortTextBecomesOneChunk() {
        val chunks = CorpusIndex.chunk("paper.md", "EGFR L858R appears in the brain.")
        assertEquals(1, chunks.size)
        assertEquals("paper.md", chunks.single().source)
        assertEquals("EGFR L858R appears in the brain.", chunks.single().text)
    }

    @Test
    fun longTextIsSplitWithOverlapSoBoundariesStillMatch() {
        val text = (1..250).joinToString(" ") { "word$it" }
        val chunks = CorpusIndex.chunk("paper.md", text, chunkSize = 200, overlap = 50)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.text.length <= 200 })
        // Consecutive chunks share 50 characters, so no term disappears at a seam.
        assertTrue(chunks[0].text.takeLast(50) == chunks[1].text.take(50))
        assertEquals("paper.md", chunks.first().source)
    }

    @Test
    fun blankTextProducesNoChunks() {
        assertTrue(CorpusIndex.chunk("empty.txt", "   \n ").isEmpty())
    }

    @Test
    fun cachedCopyIsReusedOnlyWhenNothingChanged() {
        assertTrue(CorpusIndex.isUnchanged(1000L, 2048L, 1000L, 2048L))
        assertFalse(CorpusIndex.isUnchanged(1000L, 2048L, 1001L, 2048L))
        assertFalse(CorpusIndex.isUnchanged(1000L, 2048L, 1000L, 4096L))
        assertFalse(CorpusIndex.isUnchanged(0L, 2048L, 0L, 2048L))
    }

    @Test
    fun scoringRanksChunksThatActuallyContainTheTerms() {
        val terms = listOf("egfr", "brain")
        assertTrue(
            CorpusIndex.score("EGFR mutation in brain metastasis", terms) >
                CorpusIndex.score("unrelated tissue cohort", terms)
        )
        assertEquals(0, CorpusIndex.score("unrelated tissue cohort", terms))
    }

    /**
     * The whole point of the PDF dependency: papers are PDFs, and the platform has no text
     * reader. This builds a one-page PDF in memory and reads it back through the same code
     * path the corpus uses.
     */
    @Test
    fun extractsTextFromAPdf() {
        // Even building a PDF with a standard-14 font reads PDFBox's AFM metrics, which
        // live in the library's assets and are only reachable after this init.
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        val pdf = PDDocument()
        try {
            val page = PDPage()
            pdf.addPage(page)
            PDPageContentStream(pdf, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font.HELVETICA, 12f)
                content.newLineAtOffset(72f, 700f)
                content.showText("EGFR L858R retention in brain metastasis")
                content.endText()
            }
            val bytes = ByteArrayOutputStream().also { pdf.save(it) }.toByteArray()

            val text = LocalCorpus.extractPdfText(
                bytes.inputStream(),
                RuntimeEnvironment.getApplication()
            )
            assertTrue("expected extracted text, got: $text", text?.contains("EGFR L858R") == true)
            assertTrue(text!!.contains("brain metastasis"))
        } finally {
            pdf.close()
        }
    }

    /** A page with no text layer (scans) must come back empty rather than throw. */
    @Test
    fun scannedPdfWithoutTextLayerReturnsNull() {
        val pdf = PDDocument()
        try {
            pdf.addPage(PDPage())
            val bytes = ByteArrayOutputStream().also { pdf.save(it) }.toByteArray()
            assertNull(
                LocalCorpus.extractPdfText(bytes.inputStream(), RuntimeEnvironment.getApplication())
            )
        } finally {
            pdf.close()
        }
    }
}

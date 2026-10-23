package com.example

import com.example.concordance.engine.PairedAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the paired-analysis CSV reader.
 *
 * Before this, rows were split on every comma, so a quoted assay name shifted
 * every column after it and the row silently became a different record.
 */
class PairedCsvTest {

    private val header = "patient_id,site,tissue_assay,plasma_assay,tissue_result,plasma_result"

    @Test
    fun quotedAssayNamesDoNotShiftColumns() {
        val csv = "$header\nP1,brain,\"Guardant, tissue\",\"Plasma, v2\",1,0\n"
        val (rows, errors) = PairedAnalyzer.parseCsv(csv)
        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(1, rows.size)
        assertEquals("Guardant, tissue", rows[0].tissueAssay)
        assertEquals("Plasma, v2", rows[0].plasmaAssay)
        assertTrue(rows[0].tissueResult)
        assertFalse(rows[0].plasmaResult)
    }

    @Test
    fun escapedQuotesInsideAFieldAreUnescaped() {
        val csv = "$header\nP1,brain,\"assay \"\"A\"\"\",p,1,0\n"
        val (rows, errors) = PairedAnalyzer.parseCsv(csv)
        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals("assay \"A\"", rows[0].tissueAssay)
    }

    @Test
    fun byteOrderMarkOnTheHeaderIsIgnored() {
        val (rows, errors) = PairedAnalyzer.parseCsv("\uFEFF$header\nP1,brain,t,p,1,1\n")
        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(1, rows.size)
    }

    @Test
    fun genuinelyShortRowIsStillRejected() {
        val (rows, errors) = PairedAnalyzer.parseCsv("$header\nP1,brain,t,p\n")
        assertTrue(rows.isEmpty())
        assertTrue(errors.any { it.contains("insufficient columns") })
    }
}

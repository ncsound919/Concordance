package com.example

import com.example.concordance.engine.StatisticalEngine
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import com.example.concordance.model.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The Kotlin engine is a port of `src/concordance/correction.py`, whose `Anchor` validator
 * refuses anchors the delta-method correction cannot handle. These tests pin that refusal:
 * no silent defaults, no NaN in a citable string, no capsule that cannot be serialised.
 */
class StatisticalEngineValidationTest {

    private val validCounts = Anchor(
        id = "giannoudis2024_pooled",
        label = "Giannoudis 2024 pooled (Foundation Medicine)",
        source = "Giannoudis 2024",
        evidenceGrade = EvidenceGrade.COUNTS,
        brainEvents = 53,
        brainTotal = 822,
        otherEvents = 3220,
        otherTotal = 15516
    )

    private fun anchor(
        brainEvents: Int? = null,
        brainTotal: Int? = null,
        otherEvents: Int? = null,
        otherTotal: Int? = null,
        oddsRatio: Double? = null,
        ciLow: Double? = null,
        ciHigh: Double? = null,
        refPrev: Double? = null,
        grade: EvidenceGrade = EvidenceGrade.COUNTS
    ) = Anchor(
        id = "probe",
        label = "probe",
        source = "test",
        evidenceGrade = grade,
        brainEvents = brainEvents,
        brainTotal = brainTotal,
        otherEvents = otherEvents,
        otherTotal = otherTotal,
        oddsRatio = oddsRatio,
        orCiLow = ciLow,
        orCiHigh = ciHigh,
        referencePrevalence = refPrev
    )

    @Test
    fun validCountsAnchorIsUsable() {
        assertNull(validCounts.validationError())
        assertTrue(validCounts.isUsable)
    }

    @Test
    fun zeroEventGroupIsRefusedNotEstimated() {
        val zero = anchor(brainEvents = 0, brainTotal = 40, otherEvents = 120, otherTotal = 1000)
        assertNotNull(zero.validationError())
        assertTrue(zero.validationError()!!.contains("zero-event"))

        // The low-level estimator must refuse rather than return a NaN interval.
        var thrown = false
        try {
            StatisticalEngine.observedOrCi(zero)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("observedOrCi must refuse a zero cell", thrown)
    }

    @Test
    fun zeroEventAnchorIsWithheldWithNoNumberInTheCitableText() {
        val zero = anchor(brainEvents = 0, brainTotal = 40, otherEvents = 120, otherTotal = 1000)
        val adj = StatisticalEngine.adjudicate(zero, 0.90, 0.31, 0.38)

        assertEquals(Verdict.WITHHELD, adj.verdict)
        assertNull(adj.rSignificance)
        assertTrue(adj.curve.isEmpty())
        assertFalse("no NaN may reach the citable text", adj.citableArgument.contains("NaN", true))
        assertFalse(adj.plainLanguageVerdict.contains("NaN", true))
        assertFalse(adj.rationale.contains("NaN", true))
        assertTrue(adj.whatWouldChange.isNotEmpty())
    }

    @Test
    fun fullEventGroupIsRefused() {
        val full = anchor(brainEvents = 40, brainTotal = 40, otherEvents = 120, otherTotal = 1000)
        assertNotNull(full.validationError())

        val fullOther = anchor(brainEvents = 10, brainTotal = 40, otherEvents = 1000, otherTotal = 1000)
        assertNotNull(fullOther.validationError())
    }

    @Test
    fun partialCountsAreRefused() {
        val partial = anchor(brainEvents = 10, brainTotal = 40, otherEvents = null, otherTotal = null)
        assertNotNull(partial.validationError())
        val adj = StatisticalEngine.adjudicate(partial)
        assertEquals(Verdict.WITHHELD, adj.verdict)
    }

    @Test
    fun eventsCannotExceedTotals() {
        val impossible = anchor(brainEvents = 50, brainTotal = 40, otherEvents = 10, otherTotal = 100)
        assertNotNull(impossible.validationError())
    }

    @Test
    fun summaryAnchorWithoutItsNumbersIsRefused() {
        val bare = anchor(grade = EvidenceGrade.SUMMARY)
        assertNotNull(bare.validationError())
        assertTrue(bare.validationError()!!.contains("odds ratio"))

        val adj = StatisticalEngine.adjudicate(bare, 0.90, 0.50, 0.70)
        assertEquals(Verdict.WITHHELD, adj.verdict)
        assertFalse(adj.citableArgument.contains("0.35"))
        assertFalse(adj.citableArgument.contains("1.000"))
    }

    @Test
    fun summaryAnchorWithNumbersIsUsable() {
        val annals = anchor(
            oddsRatio = 0.35,
            ciLow = 0.24,
            ciHigh = 0.52,
            refPrev = 3220.0 / 15516.0,
            grade = EvidenceGrade.SUMMARY
        )
        assertNull(annals.validationError())
        assertEquals(0.35, StatisticalEngine.observedOrCi(annals).first, 1e-12)
        assertEquals(0.2075, StatisticalEngine.observedPrevalences(annals).second, 1e-4)
    }

    @Test
    fun oddsRatioOutsideItsIntervalIsRefused() {
        val inconsistent = anchor(
            oddsRatio = 1.2,
            ciLow = 0.24,
            ciHigh = 0.52,
            refPrev = 0.2,
            grade = EvidenceGrade.SUMMARY
        )
        assertNotNull(inconsistent.validationError())
    }

    @Test
    fun referencePrevalenceMustBeAProportion() {
        val bad = anchor(oddsRatio = 0.35, ciLow = 0.24, ciHigh = 0.52, refPrev = 1.4, grade = EvidenceGrade.SUMMARY)
        assertNotNull(bad.validationError())
    }

    @Test
    fun countsGradeRequiresCounts() {
        val mislabelled = anchor(
            oddsRatio = 0.35, ciLow = 0.24, ciHigh = 0.52, refPrev = 0.2,
            grade = EvidenceGrade.COUNTS
        )
        assertNotNull(mislabelled.validationError())
    }

    @Test
    fun validAnchorKeepsReferenceParity() {
        val (est, _, _) = StatisticalEngine.observedOrCi(validCounts)
        assertEquals(0.263, est, 0.005)
        assertEquals(0.3107, StatisticalEngine.nullCrossingR(validCounts), 0.002)

        // The identity the whole correction rests on: at r* the corrected OR is 1.
        val atNull = StatisticalEngine.corrected(validCounts, StatisticalEngine.nullCrossingR(validCounts), 0.90)
        assertTrue(atNull.feasible)
        assertEquals(1.0, atNull.oddsRatio!!, 1e-9)
    }

    @Test
    fun curveRefusesADegeneratePointCount() {
        var thrown = false
        try {
            StatisticalEngine.curve(validCounts, 0.90, points = 1)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun powerCalculationRefusesAnUnusableAnchor() {
        var thrown = false
        try {
            StatisticalEngine.requiredSample(anchor(), rTrue = 0.6)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
        assertTrue(StatisticalEngine.powerSweep(anchor()).isEmpty())
    }

    @Test
    fun estimatorNeverReturnsNaNForAnEstimableAnchor() {
        val (est, lo, hi) = StatisticalEngine.observedOrCi(validCounts)
        assertFalse(est.isNaN() || lo.isNaN() || hi.isNaN())
        assertTrue(abs(hi - lo) < 1.0)
    }
}

package com.example

import com.example.concordance.engine.StatisticalEngine
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import com.example.concordance.model.Verdict
import org.junit.Assert.assertEquals
import kotlin.math.abs
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The statistical engine.
 *
 * This is the arithmetic the whole product rests on: it decides whether an
 * observed organotropism depletion survives correction for the detection
 * artifact. The tests below pin the primitives (Wilson interval, probit,
 * Fisher), the feasibility rules, and the four possible verdicts.
 */
class StatisticalEngineTest {

    /** A strong effect: brain prevalence 0.10, other 0.25, with large counts. */
    private fun strongAnchor(
        grade: EvidenceGrade = EvidenceGrade.COUNTS,
        brainEvents: Int? = 30,
        brainTotal: Int? = 300,
        otherEvents: Int? = 100,
        otherTotal: Int? = 400
    ) = Anchor(
        id = "a-strong",
        label = "Strong anchor",
        source = "source",
        evidenceGrade = grade,
        brainEvents = brainEvents,
        brainTotal = brainTotal,
        otherEvents = otherEvents,
        otherTotal = otherTotal,
        oddsRatio = 0.33,
        orCiLow = 0.22,
        orCiHigh = 0.50
    )

    /** A weak effect: brain prevalence 0.06, other 0.10. */
    private fun weakAnchor() = Anchor(
        id = "a-weak",
        label = "Weak anchor",
        source = "source",
        evidenceGrade = EvidenceGrade.COUNTS,
        brainEvents = 12,
        brainTotal = 200,
        otherEvents = 40,
        otherTotal = 400,
        oddsRatio = 0.57,
        orCiLow = 0.30,
        orCiHigh = 0.95
    )

    /** An anchor with no counts: only a summary odds ratio. */
    private fun summaryAnchor() = Anchor(
        id = "a-summary",
        label = "Summary anchor",
        source = "source",
        evidenceGrade = EvidenceGrade.SUMMARY,
        referencePrevalence = 0.2075,
        oddsRatio = 0.35,
        orCiLow = 0.25,
        orCiHigh = 0.50
    )

    // -----------------------------------------------------------------------
    // Wilson interval
    // -----------------------------------------------------------------------

    @Test
    fun `a Wilson interval of no observations is the whole unit interval`() {
        val (lo, hi) = StatisticalEngine.wilson(0, 0)
        assertEquals(0.0, lo, 1e-12)
        assertEquals(1.0, hi, 1e-12)
    }

    @Test
    fun `a Wilson interval contains the observed proportion`() {
        val (lo, hi) = StatisticalEngine.wilson(30, 300)
        assertTrue(lo < 0.1)
        assertTrue(hi > 0.1)
        assertTrue(lo >= 0.0 && hi <= 1.0)
    }

    @Test
    fun `a Wilson interval of a zero count starts at zero`() {
        val (lo, _) = StatisticalEngine.wilson(0, 100)
        assertEquals(0.0, lo, 1e-12)
    }

    @Test
    fun `a Wilson interval of every event reaches one`() {
        val (_, hi) = StatisticalEngine.wilson(100, 100)
        assertEquals(1.0, hi, 1e-12)
    }

    @Test
    fun `a wider sample gives a narrower Wilson interval`() {
        val narrow = StatisticalEngine.wilson(10, 100)
        val wide = StatisticalEngine.wilson(1000, 10000)
        assertTrue(
            (wide.second - wide.first) < (narrow.second - narrow.first)
        )
    }

    // -----------------------------------------------------------------------
    // Probit
    // -----------------------------------------------------------------------

    @Test
    fun `the probit of one half is zero`() {
        assertEquals(0.0, StatisticalEngine.normPpf(0.5), 1e-9)
    }

    @Test
    fun `the probit of ninety seven and a half percent is the z95 constant`() {
        assertEquals(StatisticalEngine.Z95, StatisticalEngine.normPpf(0.975), 1e-6)
    }

    @Test
    fun `the probit matches the published values across the whole range`() {
        // Regression guard. The tail branch used to return the wrong sign and
        // the wrong magnitude, which made the power calculation's z -0.16
        // instead of +2.80 and understated the required sample size by about
        // 300x.
        val expected = mapOf(
            0.001 to -3.090232306167813,
            0.01 to -2.3263478740408408,
            0.025 to -1.959963984540054,
            0.05 to -1.6448536269514722,
            0.1 to -1.2815515655446004,
            0.2 to -0.8416212335729143,
            0.5 to 0.0,
            0.8 to 0.8416212335729143,
            0.9 to 1.2815515655446004,
            0.95 to 1.6448536269514722,
            0.975 to 1.959963984540054,
            0.99 to 2.3263478740408408,
            0.999 to 3.090232306167813
        )
        for ((p, value) in expected) {
            assertEquals("probit at p=$p", value, StatisticalEngine.normPpf(p), 1e-5)
        }
    }

    @Test
    fun `the probit is continuous across the central and tail branches`() {
        // The central branch covers |p - 0.5| <= 0.42; the tail branch takes
        // over at 0.08 and 0.92. There must be no jump there.
        val below = StatisticalEngine.normPpf(0.0799)
        val at = StatisticalEngine.normPpf(0.08)
        val above = StatisticalEngine.normPpf(0.0801)
        assertTrue("jump at 0.08: $below -> $at -> $above", abs(at - below) < 1e-3)
        assertTrue(abs(above - at) < 1e-3)

        val belowHigh = StatisticalEngine.normPpf(0.9199)
        val atHigh = StatisticalEngine.normPpf(0.92)
        val aboveHigh = StatisticalEngine.normPpf(0.9201)
        assertTrue(abs(atHigh - belowHigh) < 1e-3)
        assertTrue(abs(aboveHigh - atHigh) < 1e-3)
    }

    @Test
    fun `the probit is negative below one half and positive above it`() {
        assertTrue(StatisticalEngine.normPpf(0.01) < 0.0)
        assertTrue(StatisticalEngine.normPpf(0.1) < 0.0)
        assertTrue(StatisticalEngine.normPpf(0.99) > 0.0)
        assertTrue(StatisticalEngine.normPpf(0.999) > 0.0)
    }

    @Test
    fun `the probit is symmetric about one half`() {
        assertEquals(StatisticalEngine.normPpf(0.9), -StatisticalEngine.normPpf(0.1), 1e-9)
    }

    @Test
    fun `the probit is increasing`() {
        assertTrue(StatisticalEngine.normPpf(0.6) > StatisticalEngine.normPpf(0.5))
        assertTrue(StatisticalEngine.normPpf(0.9) > StatisticalEngine.normPpf(0.8))
    }

    @Test
    fun `the probit refuses a probability outside its range`() {
        try {
            StatisticalEngine.normPpf(0.0)
            throw AssertionError("expected an IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("out of bounds"))
        }
        try {
            StatisticalEngine.normPpf(1.0)
            throw AssertionError("expected an IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("out of bounds"))
        }
    }

    // -----------------------------------------------------------------------
    // Prevalences and the observed odds ratio
    // -----------------------------------------------------------------------

    @Test
    fun `the observed prevalences come from the counts`() {
        val (pb, pn) = StatisticalEngine.observedPrevalences(strongAnchor())
        assertEquals(0.1, pb, 1e-9)
        assertEquals(0.25, pn, 1e-9)
    }

    @Test
    fun `an anchor with no counts borrows the reference prevalence`() {
        val (pb, pn) = StatisticalEngine.observedPrevalences(summaryAnchor())
        assertEquals(0.2075, pn, 1e-9)
        assertTrue(pb > 0.0 && pb < 1.0)
    }

    @Test
    fun `the observed odds ratio is computed from the counts`() {
        val (est, lo, hi) = StatisticalEngine.observedOrCi(strongAnchor())
        assertEquals(1.0 / 3.0, est, 1e-9)
        assertTrue(lo < est)
        assertTrue(hi > est)
    }

    @Test
    fun `an anchor with no counts returns its summary interval`() {
        val (est, lo, hi) = StatisticalEngine.observedOrCi(summaryAnchor())
        assertEquals(0.35, est, 1e-9)
        assertEquals(0.25, lo, 1e-9)
        assertEquals(0.50, hi, 1e-9)
    }

    @Test
    fun `the null crossing r is the ratio of the prevalences`() {
        assertEquals(0.4, StatisticalEngine.nullCrossingR(strongAnchor()), 1e-9)
    }

    // -----------------------------------------------------------------------
    // Correction
    // -----------------------------------------------------------------------

    @Test
    fun `the correction refuses an r outside its range`() {
        try {
            StatisticalEngine.corrected(strongAnchor(), 0.0, 0.9)
            throw AssertionError("expected an IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("r must be"))
        }
    }

    @Test
    fun `the correction refuses an r above one and a half`() {
        try {
            StatisticalEngine.corrected(strongAnchor(), 1.6, 0.9)
            throw AssertionError("expected an IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("r must be"))
        }
    }

    @Test
    fun `the correction refuses an sn outside its range`() {
        try {
            StatisticalEngine.corrected(strongAnchor(), 0.5, 0.0)
            throw AssertionError("expected an IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("sn must be"))
        }
    }

    @Test
    fun `an infeasible correction is reported as infeasible rather than as a number`() {
        // A very small sn drives the implied prevalence above one.
        val point = StatisticalEngine.corrected(strongAnchor(), 0.05, 0.01)
        assertEquals(false, point.feasible)
        assertNull(point.oddsRatio)
        assertNull(point.ciLow)
    }

    @Test
    fun `a feasible correction reports an odds ratio and an interval around it`() {
        val point = StatisticalEngine.corrected(strongAnchor(), 0.6, 0.9)
        assertEquals(true, point.feasible)
        assertNotNull(point.oddsRatio)
        assertTrue(point.ciLow!! < point.oddsRatio!!)
        assertTrue(point.ciHigh!! > point.oddsRatio!!)
    }

    @Test
    fun `the minimum feasible r is the prevalence over the sensitivity`() {
        assertEquals(0.1 / 0.9, StatisticalEngine.minFeasibleR(strongAnchor(), 0.9), 1e-9)
    }

    // -----------------------------------------------------------------------
    // The significance threshold and the curve
    // -----------------------------------------------------------------------

    @Test
    fun `a table that is never significant has no threshold`() {
        assertNull(StatisticalEngine.significanceThresholdR(weakAnchor(), 0.9))
    }

    @Test
    fun `a significant table has a threshold below one`() {
        val threshold = StatisticalEngine.significanceThresholdR(strongAnchor(), 0.9)
        assertNotNull(threshold)
        assertTrue(threshold!! in 0.0..1.0)
    }

    @Test
    fun `the curve of a significant table is not empty`() {
        val curve = StatisticalEngine.curve(strongAnchor(), 0.9)
        assertTrue(curve.isNotEmpty())
        assertTrue(curve.all { it.feasible })
    }

    @Test
    fun `the curve starts just above the feasible floor`() {
        val floor = StatisticalEngine.minFeasibleR(strongAnchor(), 0.9)
        val curve = StatisticalEngine.curve(strongAnchor(), 0.9)
        assertTrue(curve.first().r > floor)
    }

    @Test
    fun `a curve with no feasible points is empty`() {
        // A floor at or above one leaves no room for a curve.
        assertTrue(StatisticalEngine.curve(strongAnchor(), 0.01).isEmpty())
    }

    @Test
    fun `the corrected odds ratio falls as the sensitivity ratio rises`() {
        val low = StatisticalEngine.corrected(strongAnchor(), 0.3, 0.9)
        val high = StatisticalEngine.corrected(strongAnchor(), 0.9, 0.9)
        assertTrue(low.oddsRatio!! > high.oddsRatio!!)
    }

    // -----------------------------------------------------------------------
    // Fisher
    // -----------------------------------------------------------------------

    @Test
    fun `the Fisher p-value is null without counts`() {
        assertNull(StatisticalEngine.fisherP(summaryAnchor()))
    }

    @Test
    fun `the Fisher p-value is between zero and one`() {
        val p = StatisticalEngine.fisherP(strongAnchor())
        assertNotNull(p)
        assertTrue(p!! in 0.0..1.0)
    }

    @Test
    fun `a strong effect has a small Fisher p-value`() {
        val p = StatisticalEngine.fisherP(strongAnchor())!!
        assertTrue("p was $p", p < 0.05)
    }

    @Test
    fun `no effect has a large Fisher p-value`() {
        val noEffect = Anchor(
            id = "a-null",
            label = "Null",
            source = "s",
            evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = 30,
            brainTotal = 300,
            otherEvents = 30,
            otherTotal = 300
        )
        assertTrue(StatisticalEngine.fisherP(noEffect)!! > 0.05)
    }

    // -----------------------------------------------------------------------
    // The verdicts
    // -----------------------------------------------------------------------

    @Test
    fun `an abstract-only anchor has its verdict withheld`() {
        val result = StatisticalEngine.adjudicate(
            strongAnchor(grade = EvidenceGrade.ABSTRACT_ONLY),
            sn = 0.9,
            rLow = 0.6,
            rHigh = 0.9
        )
        assertEquals(Verdict.WITHHELD, result.verdict)
        assertTrue(result.rationale.contains("abstract-only"))
    }

    @Test
    fun `a synthetic anchor has its verdict withheld`() {
        val result = StatisticalEngine.adjudicate(
            strongAnchor(grade = EvidenceGrade.SYNTHETIC),
            sn = 0.9,
            rLow = 0.6,
            rHigh = 0.9
        )
        assertEquals(Verdict.WITHHELD, result.verdict)
        assertTrue(result.rationale.contains("synthetic"))
    }

    @Test
    fun `synthetic inputs withhold the verdict even for a counts anchor`() {
        val result = StatisticalEngine.adjudicate(
            strongAnchor(),
            sn = 0.9,
            rLow = 0.6,
            rHigh = 0.9,
            syntheticInputs = true
        )
        assertEquals(Verdict.WITHHELD, result.verdict)
    }

    @Test
    fun `no sensitivity range gives a threshold-only verdict`() {
        val result = StatisticalEngine.adjudicate(strongAnchor(), sn = 0.9)
        assertEquals(Verdict.THRESHOLD_ONLY, result.verdict)
        assertNull(result.rLow)
        assertNull(result.rHigh)
    }

    @Test
    fun `a strong effect across the whole range is supported`() {
        val result = StatisticalEngine.adjudicate(strongAnchor(), sn = 0.9, rLow = 0.6, rHigh = 0.9)
        assertEquals(Verdict.SUPPORTED, result.verdict)
    }

    @Test
    fun `a range that straddles the threshold is inconclusive`() {
        val result = StatisticalEngine.adjudicate(strongAnchor(), sn = 0.9, rLow = 0.3, rHigh = 0.9)
        assertEquals(Verdict.INCONCLUSIVE, result.verdict)
    }

    @Test
    fun `a weak effect across the whole range is not supported`() {
        val result = StatisticalEngine.adjudicate(weakAnchor(), sn = 0.9, rLow = 0.3, rHigh = 0.9)
        assertEquals(Verdict.NOT_SUPPORTED, result.verdict)
    }

    @Test
    fun `every verdict carries a rationale, a plain-language line and a citable argument`() {
        for (anchor in listOf(strongAnchor(), weakAnchor(), summaryAnchor())) {
            val result = StatisticalEngine.adjudicate(anchor, sn = 0.9, rLow = 0.3, rHigh = 0.9)
            assertTrue(result.rationale.isNotEmpty())
            assertTrue(result.plainLanguageVerdict.isNotEmpty())
            assertTrue(result.citableArgument.isNotEmpty())
            assertTrue(result.whatWouldChange.isNotEmpty())
        }
    }

    @Test
    fun `the citable argument names the anchor and the digests of the numbers`() {
        val result = StatisticalEngine.adjudicate(strongAnchor(), sn = 0.9, rLow = 0.6, rHigh = 0.9)
        assertTrue(result.citableArgument.contains("Strong anchor"))
        assertTrue(result.citableArgument.contains("95% CI"))
        assertTrue(result.citableArgument.contains("r*"))
    }

    @Test
    fun `the reading states the artifact threshold`() {
        val result = StatisticalEngine.adjudicate(strongAnchor(), sn = 0.9, rLow = 0.6, rHigh = 0.9)
        assertTrue(result.reading.contains("detects brain-lesion positives"))
    }

    @Test
    fun `the endpoint corrections are reported when a range is supplied`() {
        val result = StatisticalEngine.adjudicate(strongAnchor(), sn = 0.9, rLow = 0.6, rHigh = 0.9)
        assertNotNull(result.atRangeLow)
        assertNotNull(result.atRangeHigh)
    }

    @Test
    fun `the endpoint corrections are absent when no range is supplied`() {
        val result = StatisticalEngine.adjudicate(strongAnchor(), sn = 0.9)
        assertNull(result.atRangeLow)
        assertNull(result.atRangeHigh)
    }

    // -----------------------------------------------------------------------
    // Power
    // -----------------------------------------------------------------------

    @Test
    fun `the required sample is at least ten true positives per site`() {
        val requirement = StatisticalEngine.requiredSample(strongAnchor(), rTrue = 0.9)
        assertTrue(requirement.truePositivesPerSite >= 10)
        assertTrue(requirement.patientsBrain > 0)
        assertTrue(requirement.patientsNonCns > 0)
    }

    @Test
    fun `a true effect close to the artifact null cannot be separated`() {
        // The artifact null for this anchor is r* = 0.4. Anything within 2% of
        // it on the log scale cannot be separated with a realistic sample.
        try {
            StatisticalEngine.requiredSample(strongAnchor(), rTrue = 0.405)
            throw AssertionError("expected an IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("within 2% of artifact null"))
        }
    }

    @Test
    fun `the required sample is larger than the ten true positive floor`() {
        // With the corrected probit the power term is z^2 = 7.85, so a
        // realistic but modest effect needs a sample well above the floor.
        // Before the fix the same request returned the floor of ten.
        val requirement = StatisticalEngine.requiredSample(strongAnchor(), rTrue = 0.45)
        assertTrue(
            "required sample was ${requirement.truePositivesPerSite}",
            requirement.truePositivesPerSite > 10
        )
    }

    @Test
    fun `an implied prevalence above one is refused`() {
        try {
            StatisticalEngine.requiredSample(strongAnchor(), rTrue = 0.9, sn = 0.05)
            throw AssertionError("expected an IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Implied true prevalence"))
        }
    }

    @Test
    fun `the power sweep skips the effects too close to the null`() {
        val rows = StatisticalEngine.powerSweep(strongAnchor(), points = 20)
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.rTrue >= StatisticalEngine.nullCrossingR(strongAnchor()) * 1.05 - 1e-9 })
    }

    @Test
    fun `the weakest effect in the sweep needs the most patients`() {
        val rows = StatisticalEngine.powerSweep(strongAnchor(), points = 20)
        assertTrue(rows.size >= 2)
        // The sweep runs from the effect just above the artifact null up to
        // r = 1. The weakest effect is the hardest to separate from the null,
        // so it is the one that needs the largest sample.
        val weakest = rows.first()
        val strongest = rows.last()
        assertTrue(weakest.rTrue < strongest.rTrue)
        assertTrue(
            "weakest effect needed ${weakest.patientsBrain}, strongest needed ${strongest.patientsBrain}",
            weakest.patientsBrain >= strongest.patientsBrain
        )
    }
}

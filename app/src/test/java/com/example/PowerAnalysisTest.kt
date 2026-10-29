package com.example

import com.example.concordance.research.power.PowerAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerAnalysisTest {

    @Test
    fun aLargeEffectNeedsFewSamples() {
        // 30% vs 10% discordance, alpha 0.05, power 0.80.
        val result = PowerAnalysis.requiredSampleSize(0.30, 0.10, 0.05, 0.80)!!
        assertTrue("per-group ${result.perGroup}", result.perGroup in 40..70)
        assertEquals(result.perGroup * 2, result.total)
        assertEquals(0.20, result.effectSize, 1e-9)
    }

    @Test
    fun aTinyEffectNeedsManySamples() {
        val small = PowerAnalysis.requiredSampleSize(0.11, 0.10, 0.05, 0.80)!!
        val large = PowerAnalysis.requiredSampleSize(0.50, 0.10, 0.05, 0.80)!!
        assertTrue("smaller effect must need more samples", small.perGroup > large.perGroup)
    }

    @Test
    fun equalProportionsAreUndefined() {
        assertNull(PowerAnalysis.requiredSampleSize(0.30, 0.30, 0.05, 0.80))
    }

    @Test
    fun proportionsMustBeStrictlyBetweenZeroAndOne() {
        assertNull(PowerAnalysis.requiredSampleSize(0.0, 0.10, 0.05, 0.80))
        assertNull(PowerAnalysis.requiredSampleSize(1.0, 0.10, 0.05, 0.80))
        assertNull(PowerAnalysis.requiredSampleSize(0.10, 0.0, 0.05, 0.80))
        assertNull(PowerAnalysis.requiredSampleSize(0.10, 1.0, 0.05, 0.80))
    }

    @Test
    fun alphaAndPowerMustBeInTheOpenUnitInterval() {
        assertNull(PowerAnalysis.requiredSampleSize(0.30, 0.10, 0.0, 0.80))
        assertNull(PowerAnalysis.requiredSampleSize(0.30, 0.10, 1.0, 0.80))
        assertNull(PowerAnalysis.requiredSampleSize(0.30, 0.10, 0.05, 0.0))
        assertNull(PowerAnalysis.requiredSampleSize(0.30, 0.10, 0.05, 1.0))
    }

    @Test
    fun theResultIsSymmetricInTheTwoProportions() {
        val a = PowerAnalysis.requiredSampleSize(0.30, 0.10, 0.05, 0.80)!!
        val b = PowerAnalysis.requiredSampleSize(0.10, 0.30, 0.05, 0.80)!!
        assertEquals(a.perGroup, b.perGroup)
    }

    @Test
    fun achievedPowerRisesWithSampleSize() {
        val low = PowerAnalysis.achievedPower(0.30, 0.10, 20, 0.05)!!
        val high = PowerAnalysis.achievedPower(0.30, 0.10, 200, 0.05)!!
        assertTrue("power must rise with n: $low -> $high", high > low)
        assertTrue("power must be in (0,1)", high in 0.0..1.0)
    }

    @Test
    fun achievedPowerIsUndefinedForBadInputs() {
        assertNull(PowerAnalysis.achievedPower(0.30, 0.10, 1, 0.05))
        assertNull(PowerAnalysis.achievedPower(0.0, 0.10, 20, 0.05))
        assertNull(PowerAnalysis.achievedPower(0.30, 0.30, 20, 0.05))
    }

    @Test
    fun theRequiredSampleSizeAchievesAtLeastTheTargetPower() {
        val required = PowerAnalysis.requiredSampleSize(0.30, 0.10, 0.05, 0.80)!!
        val achieved = PowerAnalysis.achievedPower(0.30, 0.10, required.perGroup, 0.05)!!
        assertTrue("achieved $achieved < target 0.80", achieved >= 0.79)
    }

    @Test
    fun aHigherAlphaNeedsFewerSamples() {
        val strict = PowerAnalysis.requiredSampleSize(0.30, 0.10, 0.01, 0.80)!!
        val loose = PowerAnalysis.requiredSampleSize(0.30, 0.10, 0.05, 0.80)!!
        assertTrue("stricter alpha needs more samples", strict.perGroup > loose.perGroup)
    }
}

package com.example

import com.example.concordance.agent.EasyMode
import com.example.concordance.integration.agent.EasyModeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Client-side easy-mode policy: what must never be left out, and what must never be
 * reworded. These are pure functions, so they are checked without a device.
 */
class EasyModeTest {

    private fun view(
        band: String = "moderate",
        tier: String = "derived",
        detail: String = "The difference appears after correction.",
        doesNotMean: List<String> = listOf("This is not medical advice.")
    ) = EasyModeView(
        headline = "Moderate signal",
        signalBand = band,
        signalLabel = "Moderate signal",
        signalDetail = detail,
        evidenceTier = tier,
        qualityLabel = "Derived",
        qualityDetail = "Computed from published counts.",
        whatWeDid = listOf("We took the published comparison."),
        whatWeFound = listOf("Difference after correction" to "0.31"),
        doesNotMean = doesNotMean,
        nextSteps = listOf("Read the likely range."),
        wordsWeUsed = emptyList(),
        numbers = listOf("Leak factor r" to "0.6")
    )

    @Test
    fun `the disclaimer is added when the payload omits it`() {
        val withDisclaimer = EasyMode.ensureDisclaimer(emptyList())
        assertEquals(listOf(EasyMode.DISCLAIMER), withDisclaimer)
    }

    @Test
    fun `the disclaimer is not duplicated when it is already present`() {
        val lines = listOf(EasyMode.DISCLAIMER, "Something else.")
        assertEquals(lines, EasyMode.ensureDisclaimer(lines))
    }

    @Test
    fun `the disclaimer is detected case-insensitively`() {
        val lines = listOf("This is NOT medical advice, it is research.")
        assertEquals(lines, EasyMode.ensureDisclaimer(lines))
    }

    @Test
    fun `assumption-based numbers carry a warning`() {
        assertTrue(EasyMode.qualityWarning("configured")!!.contains("someone set"))
        assertTrue(EasyMode.qualityWarning("unmeasured")!!.contains("no accuracy"))
        assertNull(EasyMode.qualityWarning("measured"))
        assertNull(EasyMode.qualityWarning("derived"))
    }

    @Test
    fun `a negative band is never presented as proof of absence`() {
        assertTrue(EasyMode.absenceWarning("none")!!.contains("does not prove"))
        assertTrue(EasyMode.absenceWarning("cannot_tell")!!.contains("Refusing to guess"))
        assertTrue(EasyMode.absenceWarning("not_measurable")!!.contains("not in a form"))
        assertNull(EasyMode.absenceWarning("strong"))
        assertNull(EasyMode.absenceWarning("moderate"))
    }

    @Test
    fun `mandatory caveats trigger for negative bands and assumed numbers`() {
        assertTrue(EasyMode.hasMandatoryCaveats(view(band = "none")))
        assertTrue(EasyMode.hasMandatoryCaveats(view(tier = "configured")))
        assertFalse(EasyMode.hasMandatoryCaveats(view()))
    }

    @Test
    fun `the spoken summary uses the agent's words and always ends with the disclaimer`() {
        val spoken = EasyMode.speakableSummary(view())
        assertTrue(spoken.contains("Moderate signal."))
        assertTrue(spoken.contains("The difference appears after correction."))
        assertTrue(spoken.endsWith(EasyMode.DISCLAIMER))
    }

    @Test
    fun `the spoken summary adds the absence caveat for a negative result`() {
        val spoken = EasyMode.speakableSummary(view(band = "none", detail = "No clear difference."))
        assertTrue(spoken.contains("does not prove"))
        assertTrue(spoken.endsWith(EasyMode.DISCLAIMER))
    }

    @Test
    fun `the spoken summary never invents a number`() {
        val spoken = EasyMode.speakableSummary(view())
        assertFalse(spoken.contains("0.31"))
        assertFalse(spoken.contains("0.6"))
    }
}

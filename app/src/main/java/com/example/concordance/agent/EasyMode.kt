package com.example.concordance.agent

import com.example.concordance.integration.agent.EasyModeView

/**
 * Client-side half of easy mode.
 *
 * The *translation* happens on the agent (`app/explain.py`), so this object only
 * decides what must never be left out and how to read it aloud. It performs no
 * arithmetic and never rewrites a number: easy mode must stay a translation layer,
 * not a simplification.
 */
object EasyMode {

    const val DISCLAIMER =
        "This is a research tool, not medical advice. It studies groups of samples, not individual people."

    /** Guarantee the disclaimer is present even if a server payload omitted it. */
    fun ensureDisclaimer(lines: List<String>): List<String> =
        if (lines.any { it.contains("not medical advice", ignoreCase = true) }) {
            lines
        } else {
            listOf(DISCLAIMER) + lines
        }

    /** A warning when the number rests on assumptions or does not exist. */
    fun qualityWarning(tier: String): String? = when (tier) {
        "configured" ->
            "These numbers rest on values someone set, not measurements taken from this data. " +
                "Fine for exploring, not for concluding."
        "unmeasured" ->
            "There is no trustworthy number here, so no accuracy is reported at all."
        else -> null
    }

    /**
     * A "no signal" band must never be read as proof of absence.
     */
    fun absenceWarning(band: String): String? = when (band) {
        "none" ->
            "A 'no signal' result does not prove there is no effect — only that this comparison " +
                "did not show one clearly."
        "cannot_tell" ->
            "There is not enough usable signal to give an honest number. Refusing to guess is " +
                "the correct answer here."
        "not_measurable" ->
            "This claim's evidence is not in a form the agent is allowed to judge (for example, " +
                "an abstract with no methods)."
        else -> null
    }

    /** True when the view carries a caveat the UI is obliged to show. */
    fun hasMandatoryCaveats(view: EasyModeView): Boolean =
        view.isNegativeish || view.restsOnAssumptions

    /**
     * Text for on-device speech. It reuses the agent's own words and always ends
     * with the disclaimer, so spoken output cannot overstate the result.
     */
    fun speakableSummary(view: EasyModeView): String {
        val parts = mutableListOf<String>()
        if (view.headline.isNotBlank()) parts.add(view.headline + ".")
        if (view.signalDetail.isNotBlank()) parts.add(view.signalDetail)
        if (view.qualityLabel.isNotBlank()) parts.add("Evidence quality: ${view.qualityLabel}.")
        absenceWarning(view.signalBand)?.let { parts.add(it) }
        parts.add(DISCLAIMER)
        return parts.joinToString(" ")
    }
}

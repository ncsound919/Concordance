package com.example.concordance.engine

import com.example.concordance.model.*
import java.util.Locale
import kotlin.math.*

object StatisticalEngine {
    const val Z95 = 1.959963984540054
    private const val MIN_TRUE_POSITIVES = 10

    private fun odds(p: Double): Double = p / (1.0 - p)

    /**
     * Every public entry point refuses an anchor the correction cannot handle rather than
     * substituting a default. [Anchor.validationError] is the single source of truth, mirroring
     * `schema.py` in the reference engine: zero-event groups, 100%-event groups, partial counts,
     * and summary anchors without an interval or prevalence are not estimable.
     */
    private fun requireUsable(anchor: Anchor) {
        anchor.validationError()?.let { reason ->
            throw IllegalArgumentException("Anchor '${anchor.id}' cannot be adjudicated: $reason")
        }
    }

    fun observedPrevalences(anchor: Anchor): Pair<Double, Double> {
        requireUsable(anchor)
        return if (anchor.hasCounts) {
            val pb = anchor.brainEvents!!.toDouble() / anchor.brainTotal!!
            val pn = anchor.otherEvents!!.toDouble() / anchor.otherTotal!!
            Pair(pb, pn)
        } else {
            val pn = anchor.referencePrevalence!!
            val oddsB = anchor.oddsRatio!! * odds(pn)
            val pb = oddsB / (1.0 + oddsB)
            Pair(pb, pn)
        }
    }

    fun observedOrCi(anchor: Anchor): Triple<Double, Double, Double> {
        requireUsable(anchor)
        if (!anchor.hasCounts) {
            return Triple(anchor.oddsRatio!!, anchor.orCiLow!!, anchor.orCiHigh!!)
        }
        val b = anchor.brainEvents!!.toDouble()
        val nb = anchor.brainTotal!!.toDouble()
        val o = anchor.otherEvents!!.toDouble()
        val no = anchor.otherTotal!!.toDouble()

        val est = (b / (nb - b)) / (o / (no - o))
        val se = sqrt(1.0 / b + 1.0 / (nb - b) + 1.0 / o + 1.0 / (no - o))
        val lo = exp(ln(est) - Z95 * se)
        val hi = exp(ln(est) + Z95 * se)
        return Triple(est, lo, hi)
    }

    fun nullCrossingR(anchor: Anchor): Double {
        val (pb, pn) = observedPrevalences(anchor)
        return pb / pn
    }

    fun minFeasibleR(anchor: Anchor, sn: Double): Double {
        val (pb, _) = observedPrevalences(anchor)
        return if (anchor.hasCounts) {
            pb / sn
        } else {
            val refPrev = anchor.referencePrevalence!!
            val oddsHi = anchor.orCiHigh!! * odds(refPrev)
            val pHi = oddsHi / (1.0 + oddsHi)
            pHi / sn
        }
    }

    private fun summaryCorrectedOr(orVal: Double, pRef: Double, r: Double, sn: Double): Double? {
        val oddsB = orVal * odds(pRef)
        val pb = oddsB / (1.0 + oddsB)
        val piB = pb / (r * sn)
        val piN = pRef / sn
        if (piB >= 1.0 || piN >= 1.0) return null
        return odds(piB) / odds(piN)
    }

    fun corrected(anchor: Anchor, r: Double, sn: Double): CorrectedPoint {
        requireUsable(anchor)
        require(r in 0.0001..1.5) { "r must be in (0, 1.5]" }
        require(sn in 0.0001..1.0) { "sn must be in (0, 1]" }

        val (pb, pn) = observedPrevalences(anchor)
        val piB = pb / (r * sn)
        val piN = pn / sn

        if (piB >= 1.0 || piN >= 1.0) {
            return CorrectedPoint(r = r, feasible = false)
        }

        val est = odds(piB) / odds(piN)

        return if (anchor.hasCounts) {
            val varLogitB = (pb * (1.0 - pb) / anchor.brainTotal!!) / (r * sn).pow(2) / (piB * (1.0 - piB)).pow(2)
            val varLogitN = (pn * (1.0 - pn) / anchor.otherTotal!!) / sn.pow(2) / (piN * (1.0 - piN)).pow(2)
            val se = sqrt(varLogitB + varLogitN)
            val lo = exp(ln(est) - Z95 * se)
            val hi = exp(ln(est) + Z95 * se)
            CorrectedPoint(r, true, est, lo, hi, piB, piN)
        } else {
            val refPrev = anchor.referencePrevalence!!
            val lo = summaryCorrectedOr(anchor.orCiLow!!, refPrev, r, sn)
            val hi = summaryCorrectedOr(anchor.orCiHigh!!, refPrev, r, sn)
            if (lo == null || hi == null) {
                CorrectedPoint(r = r, feasible = false)
            } else {
                CorrectedPoint(r, true, est, lo, hi, piB, piN)
            }
        }
    }

    fun significanceThresholdR(anchor: Anchor, sn: Double, steps: Int = 2000): Double? {
        requireUsable(anchor)
        fun isSignificant(rVal: Double): Boolean {
            val c = corrected(anchor, rVal, sn)
            return c.feasible && (c.ciHigh ?: 2.0) < 1.0
        }

        if (!isSignificant(1.0)) return null

        val loBound = minFeasibleR(anchor, sn) * 1.0001
        if (loBound >= 1.0) return 1.0

        var prev = 1.0
        for (i in 1..steps) {
            val rVal = 1.0 - (1.0 - loBound) * i / steps
            if (!isSignificant(rVal)) {
                var a = rVal
                var b = prev
                for (iter in 0..60) {
                    val mid = (a + b) / 2.0
                    if (isSignificant(mid)) {
                        b = mid
                    } else {
                        a = mid
                    }
                }
                return b
            }
            prev = rVal
        }
        return loBound
    }

    fun curve(anchor: Anchor, sn: Double, points: Int = 60): List<CorrectedPoint> {
        requireUsable(anchor)
        require(points >= 2) { "a curve needs at least two points" }
        val lo = max(minFeasibleR(anchor, sn) * 1.001, 0.02)
        if (lo >= 1.0) return emptyList()
        val out = mutableListOf<CorrectedPoint>()
        for (i in 0 until points) {
            val rVal = lo + (1.0 - lo) * i / (points - 1)
            val pt = corrected(anchor, rVal, sn)
            if (pt.feasible) {
                out.add(pt)
            }
        }
        return out
    }

    // Wilson score interval for binomial proportion
    fun wilson(k: Int, n: Int, z: Double = Z95): Pair<Double, Double> {
        if (n == 0) return Pair(0.0, 1.0)
        val p = k.toDouble() / n
        val denom = 1.0 + z * z / n
        val centre = (p + z * z / (2.0 * n)) / denom
        val half = z * sqrt(p * (1.0 - p) / n + z * z / (4.0 * n * n)) / denom
        return Pair(max(0.0, centre - half), min(1.0, centre + half))
    }

    // Inverse Normal CDF (probit approximation - Beasley-Springer-Moro)
    fun normPpf(p: Double): Double {
        require(p in 0.0000001..0.9999999) { "p out of bounds for normPpf" }
        // Rational approximation for central and tail regions
        val a = doubleArrayOf(
            -3.969683028665376e+01, 2.209460984245205e+02,
            -2.759285104469687e+02, 1.383577518672690e+02,
            -3.066479806614716e+01, 2.506628277459239e+00
        )
        val b = doubleArrayOf(
            -5.447609879822406e+01, 1.615858368580409e+02,
            -1.556989798598866e+02, 6.680131188771972e+01,
            -1.328068155288572e+01
        )
        val c = doubleArrayOf(
            -7.784894002430293e-03, -3.223964580411365e-01,
            -2.400758277161838e+00, -2.549732539343734e+00,
            4.374664141464968e+00, 2.938163982698783e+00
        )
        val d = doubleArrayOf(
            7.784695709041462e-03, 3.224671290700398e-01,
            2.445134137142996e+00, 3.754408661907416e+00
        )
        val q = p - 0.5
        return if (abs(q) <= 0.42) {
            val r = q * q
            q * (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) /
                    (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0)
        } else {
            val r = if (q < 0.0) p else 1.0 - p
            var s = sqrt(-ln(r))
            var res = (((((c[0] * s + c[1]) * s + c[2]) * s + c[3]) * s + c[4]) * s + c[5]) /
                    ((((d[0] * s + d[1]) * s + d[2]) * s + d[3]) * s + 1.0)
            if (q < 0.0) -res else res
        }
    }

    // Two-tailed Fisher Exact Test
    fun fisherP(anchor: Anchor): Double? {
        if (!anchor.hasCounts) return null
        val a = anchor.brainEvents!!
        val b = anchor.brainTotal!! - a
        val c = anchor.otherEvents!!
        val d = anchor.otherTotal!! - c

        val n = a + b + c + d
        val row1 = a + b
        val row2 = c + d
        val col1 = a + c

        // Log factorial array cache
        val logFact = DoubleArray(n + 1)
        var acc = 0.0
        for (i in 1..n) {
            acc += ln(i.toDouble())
            logFact[i] = acc
        }

        fun logHyper(k: Int): Double {
            return logFact[row1] + logFact[row2] + logFact[col1] + logFact[n - col1] -
                    (logFact[k] + logFact[row1 - k] + logFact[col1 - k] + logFact[n - row1 - col1 + k] + logFact[n])
        }

        val minK = max(0, col1 - row2)
        val maxK = min(row1, col1)
        val observedLogP = logHyper(a)

        var pSum = 0.0
        for (k in minK..maxK) {
            val lp = logHyper(k)
            if (lp <= observedLogP + 1e-9) {
                pSum += exp(lp)
            }
        }
        return min(1.0, max(0.0, pSum))
    }

    /**
     * A refused adjudication. Nothing is estimated and no number is printed: an anchor the
     * correction cannot handle is withheld with its reason, exactly like abstract-only or
     * synthetic evidence. Prefer this over letting a NaN reach a citable string or a capsule.
     */
    private fun withheldForInputs(
        anchor: Anchor,
        sn: Double,
        rLow: Double?,
        rHigh: Double?,
        reason: String
    ): AdjudicationResult = AdjudicationResult(
        anchor = anchor,
        sn = sn,
        rLow = rLow,
        rHigh = rHigh,
        observedOr = 0.0,
        observedCiLow = 0.0,
        observedCiHigh = 0.0,
        fisherP = null,
        prevalenceBrain = 0.0,
        prevalenceOther = 0.0,
        rNull = 0.0,
        rSignificance = null,
        rFloor = 0.0,
        reading = "No correction is computed: $reason.",
        verdict = Verdict.WITHHELD,
        rationale = "No verdict is issued. The anchor's inputs are unusable: $reason.",
        plainLanguageVerdict = "No number is reported for this claim. Its evidence does not supply " +
            "what the correction needs, and the tool does not fill the gap with a guess.",
        citableArgument = "Concordance adjudication for ${anchor.label}: withheld. " +
            "Inputs unusable ($reason); no odds ratio, threshold or verdict is reported.",
        whatWouldChange = listOf(
            "Complete 2x2 counts (at least one event and one non-event in each group) would make " +
                "the correction estimable.",
            "Alternatively, an odds ratio with its 95% interval and the non-CNS prevalence (0 < p < 1)."
        ),
        curve = emptyList()
    )

    // Adjudicate claim anchor
    fun adjudicate(
        anchor: Anchor,
        sn: Double = 0.90,
        rLow: Double? = null,
        rHigh: Double? = null,
        syntheticInputs: Boolean = false
    ): AdjudicationResult {
        anchor.validationError()?.let { reason ->
            return withheldForInputs(anchor, sn, rLow, rHigh, reason)
        }
        val (est, lo, hi) = observedOrCi(anchor)
        val (pb, pn) = observedPrevalences(anchor)
        val rNull = nullCrossingR(anchor)
        val rSig = significanceThresholdR(anchor, sn)
        val rFloor = minFeasibleR(anchor, sn)
        val fP = fisherP(anchor)
        val curvePoints = curve(anchor, sn)

        val reading = buildString {
            append("Artifact alone reproduces the observed depletion if the assay detects brain-lesion positives at ")
            append(String.format(Locale.US, "%.2f×", rNull))
            append(" the rate it detects them elsewhere or lower. ")
            if (rSig != null) {
                append("The depletion stays statistically distinguishable from zero only above ")
                append(String.format(Locale.US, "%.2f×.", rSig))
            } else {
                append("The depletion is not statistically distinguishable from zero even uncorrected.")
            }
        }

        var atRangeLow: CorrectedPoint? = null
        var atRangeHigh: CorrectedPoint? = null

        if (rLow != null && rHigh != null) {
            atRangeLow = corrected(anchor, max(rLow, rFloor * 1.0001), sn)
            atRangeHigh = corrected(anchor, rHigh, sn)
        }

        val verdict: Verdict
        val rationale: String

        if (anchor.evidenceGrade == EvidenceGrade.ABSTRACT_ONLY ||
            anchor.evidenceGrade == EvidenceGrade.SYNTHETIC ||
            syntheticInputs
        ) {
            verdict = Verdict.WITHHELD
            val reason = if (anchor.evidenceGrade == EvidenceGrade.SYNTHETIC || syntheticInputs) {
                "synthetic data"
            } else {
                "an abstract-only source with methods unseen"
            }
            rationale = "No verdict is issued on $reason. The threshold curve is shown for planning only."
        } else if (rLow == null || rHigh == null) {
            verdict = Verdict.THRESHOLD_ONLY
            rationale = "No sensitivity range was supplied. Threshold r* = ${String.format(Locale.US, "%.2f", rNull)} and r_sig = ${String.format(Locale.US, "%.2f", rSig ?: rNull)} are what a paired study has to beat."
        } else {
            val loC = atRangeLow!!
            val hiC = atRangeHigh!!
            if (hiC.feasible && (hiC.ciHigh ?: 2.0) >= 1.0) {
                verdict = Verdict.NOT_SUPPORTED
                rationale = "Even at the most favourable sensitivity in the stated range, the corrected interval includes OR = 1: the depletion is indistinguishable from detection artifact."
            } else if (loC.feasible && (loC.ciHigh ?: 2.0) < 1.0) {
                verdict = Verdict.SUPPORTED
                rationale = "Across the whole stated range, the corrected interval stays below OR = 1. Depletion survives detection correction (biology L1 vs confounding/dormancy L2-L4 unseparated)."
            } else {
                verdict = Verdict.INCONCLUSIVE
                rationale = "The stated range straddles the significance threshold: some values keep the depletion, others erase it."
            }
        }

        // Generate the powerhouse decision-relevant 1-sentence verdict ready to cite:
        val rSigStr = String.format(Locale.US, "%.2f", rSig ?: rNull)
        val rNullStr = String.format(Locale.US, "%.2f", rNull)

        val plainLanguage = when (verdict) {
            Verdict.WITHHELD ->
                "Verdict withheld by protocol: Abstract-only/synthetic evidence cannot substantiate an organotropism claim without full-text 2×2 counts or verified assays."
            Verdict.THRESHOLD_ONLY ->
                "The observed depletion is indistinguishable from detection artifact unless brain metastases are sampled at least ${rSigStr}× as sensitively as non-CNS sites; the published 2026 case report suggests this threshold may not be met."
            Verdict.NOT_SUPPORTED ->
                "Under realistic CNS-to-plasma sensitivity ($rLow to $rHigh), the observed depletion is indistinguishable from detection artifact ($rNullStr× artifact null)."
            Verdict.SUPPORTED ->
                "The reported brain metastasis depletion survives detection correction across the tested range ($rLow to $rHigh), requiring biological or clinical investigation beyond assay sensitivity."
            Verdict.INCONCLUSIVE ->
                "Current assay sensitivity assumptions ($rLow to $rHigh) straddle the critical threshold (${rSigStr}×); paired tissue-plasma concordance is mandatory to resolve."
        }

        val citable = "Concordance adjudication for ${anchor.label}: observed OR = ${String.format(Locale.US, "%.3f", est)} (95% CI ${String.format(Locale.US, "%.3f", lo)}–${String.format(Locale.US, "%.3f", hi)}). Artifact-only threshold r* = $rNullStr; significance threshold r_sig = $rSigStr (at sn=${String.format(Locale.US, "%.2f", sn)}). $plainLanguage"

        val whatWouldChange = mutableListOf(
            "A paired study estimating relative sensitivity with an interval wholly above ${rSigStr} would support depletion.",
            "An interval wholly at or below ${rNullStr} would show artifact alone explains the depletion."
        )
        if (verdict == Verdict.WITHHELD) {
            whatWouldChange.add("Full-text counts or verified patient-level data would lift the withheld status.")
        }

        return AdjudicationResult(
            anchor = anchor,
            sn = sn,
            rLow = rLow,
            rHigh = rHigh,
            observedOr = est,
            observedCiLow = lo,
            observedCiHigh = hi,
            fisherP = fP,
            prevalenceBrain = pb,
            prevalenceOther = pn,
            rNull = rNull,
            rSignificance = rSig,
            rFloor = rFloor,
            reading = reading,
            verdict = verdict,
            rationale = rationale,
            plainLanguageVerdict = plainLanguage,
            citableArgument = citable,
            whatWouldChange = whatWouldChange,
            curve = curvePoints,
            atRangeLow = atRangeLow,
            atRangeHigh = atRangeHigh
        )
    }

    // Power and sample size calculation
    fun requiredSample(
        anchor: Anchor,
        rTrue: Double,
        sn: Double = 0.90,
        alpha: Double = 0.05,
        power: Double = 0.80
    ): PowerRequirement {
        requireUsable(anchor)
        require(alpha in 0.001..0.5)
        require(power in 0.5..0.999)
        require(sn in 0.01..1.0)
        val sb = rTrue * sn
        require(sb in 0.001..1.0) { "r_true * sn must be <= 1.0" }

        val (pb, pn) = observedPrevalences(anchor)
        val rStar = nullCrossingR(anchor)
        val gap = abs(ln(rTrue / rStar))
        if (gap < 0.02) {
            throw IllegalArgumentException("r_true is within 2% of artifact null r*; cannot separate with realistic sample.")
        }

        val piB = pb / sb
        val piN = pn / sn
        if (piB >= 1.0 || piN >= 1.0) {
            throw IllegalArgumentException("Implied true prevalence >= 1. Choose larger r_true or sn.")
        }

        val z = normPpf(1.0 - alpha / 2.0) + normPpf(power)
        val perM = (1.0 - sb) / sb + (1.0 - sn) / sn
        val rawM = if (perM > 0.0) ceil((z * z * perM) / (gap * gap)).toInt() else 2
        val m = max(rawM, MIN_TRUE_POSITIVES)

        return PowerRequirement(
            rTrue = rTrue,
            rStar = rStar,
            sn = sn,
            sb = sb,
            alpha = alpha,
            power = power,
            truePositivesPerSite = m,
            patientsBrain = ceil(m / piB).toInt(),
            patientsNonCns = ceil(m / piN).toInt(),
            piBrain = piB,
            piOther = piN
        )
    }

    fun powerSweep(
        anchor: Anchor,
        sn: Double = 0.90,
        alpha: Double = 0.05,
        power: Double = 0.80,
        points: Int = 20
    ): List<PowerSweepRow> {
        if (!anchor.isUsable) return emptyList()
        require(points >= 2) { "a sweep needs at least two points" }
        val rStar = nullCrossingR(anchor)
        val out = mutableListOf<PowerSweepRow>()
        for (i in 0 until points) {
            val rVal = 0.10 + (1.0 - 0.10) * i / (points - 1)
            if (rVal < rStar * 1.05) continue
            try {
                val req = requiredSample(anchor, rVal, sn, alpha, power)
                out.add(
                    PowerSweepRow(
                        rTrue = rVal,
                        truePositivesPerSite = req.truePositivesPerSite,
                        patientsBrain = req.patientsBrain,
                        patientsNonCns = req.patientsNonCns
                    )
                )
            } catch (_: Exception) {}
        }
        return out
    }
}

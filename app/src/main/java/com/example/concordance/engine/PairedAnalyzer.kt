package com.example.concordance.engine

import com.example.concordance.model.DataSource
import com.example.concordance.model.ModalityPair
import com.example.concordance.model.PairedAnalysisResult
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

object PairedAnalyzer {

    private fun parseTruthy(value: String): Boolean {
        val s = value.trim().lowercase()
        return when (s) {
            "1", "true", "t", "yes", "y", "pos", "positive" -> true
            "0", "false", "f", "no", "n", "neg", "negative" -> false
            else -> throw IllegalArgumentException("Cannot parse '$value' as a positive/negative result.")
        }
    }

    fun parseCsv(csvText: String): Pair<List<ModalityPair>, List<String>> {
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return Pair(emptyList(), listOf("CSV file is empty."))
        }
        val header = lines.first().split(",").map { it.trim().lowercase() }
        val required = listOf("patient_id", "site", "tissue_assay", "plasma_assay", "tissue_result", "plasma_result")
        val missing = required.filter { !header.contains(it) }
        if (missing.isNotEmpty()) {
            return Pair(emptyList(), listOf("Missing required columns: ${missing.joinToString(", ")}"))
        }

        val patientIdx = header.indexOf("patient_id")
        val siteIdx = header.indexOf("site")
        val tAssayIdx = header.indexOf("tissue_assay")
        val pAssayIdx = header.indexOf("plasma_assay")
        val tResIdx = header.indexOf("tissue_result")
        val pResIdx = header.indexOf("plasma_result")
        val ctdnaIdx = header.indexOf("ctdna_fraction")
        val lodIdx = header.indexOf("limit_of_detection")
        val sourceIdx = header.indexOf("data_source")

        val rows = mutableListOf<ModalityPair>()
        val errors = mutableListOf<String>()

        for (i in 1 until lines.size) {
            val lineNum = i + 1
            val cols = lines[i].split(",").map { it.trim() }
            if (cols.size < required.size) {
                errors.add("Line $lineNum: insufficient columns (${cols.size} found, at least ${required.size} required).")
                continue
            }
            try {
                val patientId = cols.getOrNull(patientIdx) ?: ""
                val site = (cols.getOrNull(siteIdx) ?: "").lowercase()
                if (site != "brain" && site != "non_cns") {
                    errors.add("Line $lineNum: invalid site '$site' (must be 'brain' or 'non_cns').")
                    continue
                }
                val tAssay = cols.getOrNull(tAssayIdx) ?: "tissue_panel"
                val pAssay = cols.getOrNull(pAssayIdx) ?: "plasma_panel"
                val tRes = parseTruthy(cols.getOrNull(tResIdx) ?: "")
                val pRes = parseTruthy(cols.getOrNull(pResIdx) ?: "")

                val ctdna = if (ctdnaIdx >= 0 && ctdnaIdx < cols.size && cols[ctdnaIdx].isNotEmpty()) cols[ctdnaIdx].toDoubleOrNull() else null
                val lod = if (lodIdx >= 0 && lodIdx < cols.size && cols[lodIdx].isNotEmpty()) cols[lodIdx].toDoubleOrNull() else null
                val dataSource = if (sourceIdx >= 0 && sourceIdx < cols.size && cols[sourceIdx].lowercase() == "synthetic") DataSource.SYNTHETIC else DataSource.REAL

                if (ctdna != null && lod != null) {
                    if (pRes && ctdna < lod) {
                        errors.add("Line $lineNum: plasma-positive call with ctDNA fraction ($ctdna) < limit of detection ($lod).")
                        continue
                    }
                    if (!pRes && ctdna >= lod) {
                        errors.add("Line $lineNum: plasma-negative call with ctDNA fraction ($ctdna) >= limit of detection ($lod).")
                        continue
                    }
                }

                rows.add(
                    ModalityPair(
                        patientId = patientId,
                        site = site,
                        tissueAssay = tAssay,
                        plasmaAssay = pAssay,
                        tissueResult = tRes,
                        plasmaResult = pRes,
                        ctdnaFraction = ctdna,
                        limitOfDetection = lod,
                        dataSource = dataSource
                    )
                )
            } catch (e: Exception) {
                errors.add("Line $lineNum: ${e.message}")
            }
        }
        return Pair(rows, errors)
    }

    fun analyze(rows: List<ModalityPair>, claimModality: String = "plasma"): PairedAnalysisResult {
        val synthetic = rows.any { it.dataSource == DataSource.SYNTHETIC }
        val warnings = mutableListOf<String>()

        val brainRows = rows.filter { it.site == "brain" }
        val nonCnsRows = rows.filter { it.site == "non_cns" }

        val bBothPos = brainRows.count { it.tissueResult && it.plasmaResult }
        val bTOnly = brainRows.count { it.tissueResult && !it.plasmaResult }
        val bPOnly = brainRows.count { !it.tissueResult && it.plasmaResult }
        val bBothNeg = brainRows.size - bBothPos - bTOnly - bPOnly

        val nBothPos = nonCnsRows.count { it.tissueResult && it.plasmaResult }
        val nTOnly = nonCnsRows.count { it.tissueResult && !it.plasmaResult }
        val nPOnly = nonCnsRows.count { !it.tissueResult && it.plasmaResult }
        val nBothNeg = nonCnsRows.size - nBothPos - nTOnly - nPOnly

        // Composite positive = positive on either assay
        val bPos = brainRows.filter { it.tissueResult || it.plasmaResult }
        val nPos = nonCnsRows.filter { it.tissueResult || it.plasmaResult }

        val bHit = bPos.count { if (claimModality == "tissue") it.tissueResult else it.plasmaResult }
        val nHit = nPos.count { if (claimModality == "tissue") it.tissueResult else it.plasmaResult }

        var sb: Double? = null
        var sbCi: Pair<Double, Double>? = null
        var sn: Double? = null
        var snCi: Pair<Double, Double>? = null
        var rEst: Double? = null
        var rCi: Pair<Double, Double>? = null

        if (bPos.isEmpty()) {
            warnings.add("No composite-positive brain samples; relative sensitivity cannot be estimated.")
        } else {
            sb = bHit.toDouble() / bPos.size
            sbCi = StatisticalEngine.wilson(bHit, bPos.size)
            if (bPos.size < 10) {
                warnings.add("Only ${bPos.size} composite-positive brain samples; confidence interval will be wide.")
            }
        }

        if (nPos.isEmpty()) {
            warnings.add("No composite-positive non-CNS samples; relative sensitivity cannot be estimated.")
        } else {
            sn = nHit.toDouble() / nPos.size
            snCi = StatisticalEngine.wilson(nHit, nPos.size)
            if (nPos.size < 10) {
                warnings.add("Only ${nPos.size} composite-positive non-CNS samples; confidence interval will be wide.")
            }
        }

        if (sb != null && sn != null && bHit > 0 && nHit > 0 && sb > 0.0 && sn > 0.0) {
            val rVal = sb / sn
            val se = sqrt((1.0 - sb) / (sb * bPos.size) + (1.0 - sn) / (sn * nPos.size))
            val ciLo = rVal * exp(-StatisticalEngine.Z95 * se)
            val ciHi = min(rVal * exp(StatisticalEngine.Z95 * se), 1.5)
            rEst = rVal
            rCi = Pair(ciLo, ciHi)
        } else if (bHit == 0 || nHit == 0) {
            warnings.add("Zero detections in one stratum; relative sensitivity is not estimable.")
        }

        if (synthetic) {
            warnings.add("Dataset contains rows tagged synthetic; final adjudication verdict will be withheld.")
        }

        val limitation = "Composite reference (positive on either assay) overestimates sensitivity because double-negative true positives are unobservable."

        return PairedAnalysisResult(
            claimModality = claimModality,
            totalRows = rows.size,
            synthetic = synthetic,
            brainTotal = brainRows.size,
            brainBothPos = bBothPos,
            brainTissueOnly = bTOnly,
            brainPlasmaOnly = bPOnly,
            brainBothNeg = bBothNeg,
            nonCnsTotal = nonCnsRows.size,
            nonCnsBothPos = nBothPos,
            nonCnsTissueOnly = nTOnly,
            nonCnsPlasmaOnly = nPOnly,
            nonCnsBothNeg = nBothNeg,
            brainSensitivity = sb,
            brainSensitivityCi = sbCi,
            nonCnsSensitivity = sn,
            nonCnsSensitivityCi = snCi,
            relativeSensitivity = rEst,
            relativeSensitivityCi = rCi,
            warnings = warnings,
            limitation = limitation
        )
    }

    /**
     * A clearly-labelled SYNTHETIC demonstration CSV. Every row is tagged
     * `synthetic`, so the analyzer withholds any verdict built from it.
     */
    fun sampleBenchmarkDataset(): String {
        return buildString {
            append("patient_id,site,tissue_assay,plasma_assay,tissue_result,plasma_result,ctdna_fraction,limit_of_detection,data_source\n")
            // Illustrative demonstration row (not a patient record):
            append("CO-2026-01,brain,BrainMetTissue-NGS,Plasma-cfDNA-NGS,yes,no,0.000,0.005,synthetic\n")
            // Illustrative cohort pairs representing reduced CNS shedding (r ~ 0.35):
            for (i in 1..25) {
                append("P-BRN-$i,brain,FM-Tissue,FM-Liquid,yes,no,0.001,0.005,synthetic\n")
            }
            for (i in 26..38) {
                append("P-BRN-$i,brain,FM-Tissue,FM-Liquid,yes,yes,0.018,0.005,synthetic\n")
            }
            for (i in 1..8) {
                append("P-OTH-$i,non_cns,FM-Tissue,FM-Liquid,yes,no,0.002,0.005,synthetic\n")
            }
            for (i in 9..85) {
                append("P-OTH-$i,non_cns,FM-Tissue,FM-Liquid,yes,yes,0.024,0.005,synthetic\n")
            }
        }
    }
}

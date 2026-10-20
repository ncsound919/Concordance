package com.example.concordance.model

enum class EvidenceGrade(val label: String, val description: String) {
    COUNTS("Counts", "2x2 counts available; full delta-method uncertainty propagation"),
    SUMMARY("Summary", "OR + CI from full-text source; non-CNS prevalence borrowed"),
    ABSTRACT_ONLY("Abstract Only", "Methods unseen; verdict strictly withheld by protocol"),
    SYNTHETIC("Synthetic", "Guardrail check only; verdict strictly withheld")
}

enum class DataSource {
    REAL,
    SYNTHETIC
}

enum class Verdict(val label: String, val badgeColorHex: Long) {
    SUPPORTED("Supported", 0xFF00E676),          // Green: survives detection correction
    NOT_SUPPORTED("Not Supported", 0xFFFF5252),  // Red: artifact explains depletion
    INCONCLUSIVE("Inconclusive", 0xFFFFD740),    // Amber: range straddles significance
    WITHHELD("Withheld", 0xFF9E9E9E),            // Grey: abstract-only or synthetic
    THRESHOLD_ONLY("Threshold Only", 0xFF40C4FF) // Blue: no sensitivity range supplied
}

data class Anchor(
    val id: String,
    val label: String,
    val source: String,
    val evidenceGrade: EvidenceGrade,
    val brainEvents: Int? = null,
    val brainTotal: Int? = null,
    val otherEvents: Int? = null,
    val otherTotal: Int? = null,
    val oddsRatio: Double? = null,
    val orCiLow: Double? = null,
    val orCiHigh: Double? = null,
    val referencePrevalence: Double? = null,
    val denominator: String = "samples",
    val notes: String = "",
    val isCustom: Boolean = false,
    val dateAdded: String = "2026-10-18",
    val modelRevisionId: String? = null
) {
    val hasCounts: Boolean
        get() = brainEvents != null && brainTotal != null && otherEvents != null && otherTotal != null
}

data class ModalityPair(
    val patientId: String,
    val site: String, // "brain" or "non_cns"
    val tissueAssay: String,
    val plasmaAssay: String,
    val tissueResult: Boolean,
    val plasmaResult: Boolean,
    val ctdnaFraction: Double? = null,
    val limitOfDetection: Double? = null,
    val dataSource: DataSource = DataSource.REAL
)

data class CorrectedPoint(
    val r: Double,
    val feasible: Boolean,
    val oddsRatio: Double? = null,
    val ciLow: Double? = null,
    val ciHigh: Double? = null,
    val piBrain: Double? = null,
    val piOther: Double? = null
)

data class AdjudicationResult(
    val anchor: Anchor,
    val sn: Double,
    val rLow: Double?,
    val rHigh: Double?,
    val observedOr: Double,
    val observedCiLow: Double,
    val observedCiHigh: Double,
    val fisherP: Double?,
    val prevalenceBrain: Double,
    val prevalenceOther: Double,
    val rNull: Double,
    val rSignificance: Double?,
    val rFloor: Double,
    val reading: String,
    val verdict: Verdict,
    val rationale: String,
    val plainLanguageVerdict: String,
    val citableArgument: String,
    val whatWouldChange: List<String>,
    val curve: List<CorrectedPoint>,
    val atRangeLow: CorrectedPoint? = null,
    val atRangeHigh: CorrectedPoint? = null
)

data class Capsule(
    val id: String,
    val permalink: String,
    val capsuleVersion: String = "1",
    val softwareVersion: String = "0.1.0",
    val createdUtc: String,
    val sha256: String,
    val question: String,
    val anchorLabel: String,
    val verdict: Verdict,
    val citableArgument: String,
    val plainLanguageVerdict: String,
    val rNull: Double,
    val rSignificance: Double?,
    val snAssumed: Double,
    val rLow: Double?,
    val rHigh: Double?,
    val observedOr: Double,
    val rawJson: String,
    val markdown: String,
    val provenanceDigest: String? = null,
    val provenanceRecordIds: List<Long> = emptyList(),
    val modelRevisionId: String? = null,
    val supersedesCapsuleId: String? = null
)

data class PairedAnalysisResult(
    val claimModality: String,
    val totalRows: Int,
    val synthetic: Boolean,
    val brainTotal: Int,
    val brainBothPos: Int,
    val brainTissueOnly: Int,
    val brainPlasmaOnly: Int,
    val brainBothNeg: Int,
    val nonCnsTotal: Int,
    val nonCnsBothPos: Int,
    val nonCnsTissueOnly: Int,
    val nonCnsPlasmaOnly: Int,
    val nonCnsBothNeg: Int,
    val brainSensitivity: Double?,
    val brainSensitivityCi: Pair<Double, Double>?,
    val nonCnsSensitivity: Double?,
    val nonCnsSensitivityCi: Pair<Double, Double>?,
    val relativeSensitivity: Double?,
    val relativeSensitivityCi: Pair<Double, Double>?,
    val warnings: List<String>,
    val limitation: String
)

data class PowerRequirement(
    val rTrue: Double,
    val rStar: Double,
    val sn: Double,
    val sb: Double,
    val alpha: Double,
    val power: Double,
    val truePositivesPerSite: Int,
    val patientsBrain: Int,
    val patientsNonCns: Int,
    val piBrain: Double,
    val piOther: Double
)

data class PowerSweepRow(
    val rTrue: Double,
    val truePositivesPerSite: Int,
    val patientsBrain: Int,
    val patientsNonCns: Int
)

data class ClaimChallenge(
    val id: String,
    val claimId: String,
    val author: String,
    val date: String,
    val notes: String,
    val updatedBrainEvents: Int,
    val updatedBrainTotal: Int,
    val updatedOtherEvents: Int,
    val updatedOtherTotal: Int
)

package com.example.concordance.research.experiment

import java.security.MessageDigest
import java.util.Locale

/**
 * The typed description of an experiment.
 *
 * An experiment is not a title and a status flag. It is a falsifiable question, a dataset that
 * has been pinned by hash, a method that has been pinned by version, and a decision rule that was
 * written down **before** the result was seen. [problems] refuses a spec that cannot be one of
 * those things, and [sha256] makes any later edit a new version rather than a silent change.
 *
 * This type is deliberately free of Android and network dependencies so it can be unit-tested on
 * the JVM and reused by a Kaggle kernel's parameter block.
 */
data class ExperimentSpec(
    val id: String,
    val version: Int,
    val experimentClass: ExperimentClass,
    val title: String,
    val hypothesis: String,
    val direction: String,
    val design: String,
    val unit: String,
    val contrast: String,
    val outcome: String,
    val datasets: List<DatasetRef>,
    val method: MethodRef,
    val decisionRule: DecisionRule?,
    val confirmatory: Boolean,
    val expectedArtifacts: List<String>,
    val budgetUsd: Double = 0.0,
    val budgetCpuMinutes: Int = 0,
    val budgetGpuMinutes: Int = 0,
    val ethics: String = "",
) {

    /**
     * Spec-level problems. A spec may legitimately be incomplete - the catalog shows it as
     * `UNSUPPORTED` with the reason rather than hiding it. What it may not do is claim to be
     * confirmatory without a decision rule.
     */
    fun problems(): List<SpecProblem> = buildList {
        if (id.isBlank()) add(SpecProblem("ID_BLANK", "spec id is empty"))
        if (!ID_PATTERN.matches(id)) {
            add(SpecProblem("ID_FORMAT", "spec id must match ${ID_PATTERN.pattern}"))
        }
        if (version < 1) add(SpecProblem("VERSION", "version must be >= 1"))
        if (title.isBlank()) add(SpecProblem("TITLE", "title is empty"))
        if (hypothesis.isBlank()) add(SpecProblem("HYPOTHESIS", "hypothesis is empty"))
        if (direction.isBlank()) add(SpecProblem("DIRECTION", "direction is empty"))
        if (design.isBlank()) add(SpecProblem("DESIGN", "design is empty"))
        if (unit.isBlank()) add(SpecProblem("UNIT", "unit is empty"))
        if (contrast.isBlank()) add(SpecProblem("CONTRAST", "contrast is empty"))
        if (outcome.isBlank()) add(SpecProblem("OUTCOME", "outcome is empty"))

        if (datasets.isEmpty()) add(SpecProblem("NO_DATASET", "no dataset declared"))
        datasets.forEachIndexed { index, ds ->
            if (ds.ref.isBlank()) add(SpecProblem("DATASET_REF", "dataset[$index].ref is empty"))
            // A blank version is not a malformed spec: it is an unpinned dataset, reported by
            // `isPinned()` and refused by the compiler. Keeping it out of `problems()` is what
            // lets the catalog say "dataset not pinned" instead of "version is empty".
            if (ds.license.isBlank()) add(SpecProblem("DATASET_LICENSE", "dataset[$index].license is empty"))
            if (ds.access == DataAccess.UNKNOWN) {
                add(SpecProblem("DATASET_ACCESS_UNKNOWN", "dataset[$index].access is not a known value"))
            }
            if (ds.dataClass == DataClass.UNKNOWN) {
                add(
                    SpecProblem(
                        "DATASET_CLASS_UNKNOWN",
                        "dataset[$index].dataClass is not a known value; refusing to guess its residency",
                    )
                )
            }
        }

        if (experimentClass == ExperimentClass.UNKNOWN) {
            add(SpecProblem("CLASS_UNKNOWN", "experiment class is not a known value"))
        }

        if (method.pipeline.isBlank()) add(SpecProblem("PIPELINE", "method.pipeline is empty"))
        if (method.version.isBlank()) add(SpecProblem("METHOD_VERSION", "method.version is empty"))
        if (method.seed < 0) add(SpecProblem("SEED", "method.seed must be >= 0"))

        if (expectedArtifacts.isEmpty()) add(SpecProblem("NO_ARTIFACTS", "expectedArtifacts is empty"))
        if (expectedArtifacts.any { it.isBlank() }) {
            add(SpecProblem("ARTIFACT_BLANK", "expectedArtifacts contains a blank name"))
        }

        if (budgetUsd < 0.0) add(SpecProblem("BUDGET_USD", "budgetUsd must be >= 0"))
        if (budgetCpuMinutes < 0) add(SpecProblem("BUDGET_CPU", "budgetCpuMinutes must be >= 0"))
        if (budgetGpuMinutes < 0) add(SpecProblem("BUDGET_GPU", "budgetGpuMinutes must be >= 0"))

        if (confirmatory && decisionRule == null) {
            add(SpecProblem("NO_DECISION_RULE", "a confirmatory spec needs a decision rule"))
        }

        decisionRule?.let { rule ->
            if (rule.metric.isBlank()) add(SpecProblem("RULE_METRIC", "decisionRule.metric is empty"))
            if (rule.comparison !in DecisionRule.SUPPORTED_COMPARISONS) {
                add(
                    SpecProblem(
                        "RULE_COMPARISON",
                        "decisionRule.comparison '${rule.comparison}' is not one of " +
                            DecisionRule.SUPPORTED_COMPARISONS.joinToString(", ")
                    )
                )
            }
            if (!rule.threshold.isFinite()) {
                add(SpecProblem("RULE_THRESHOLD", "decisionRule.threshold is not finite"))
            }
            if (rule.alpha <= 0.0 || rule.alpha >= 1.0) {
                add(SpecProblem("RULE_ALPHA", "decisionRule.alpha must be between 0 and 1"))
            }
            if (rule.powerTarget <= 0.0 || rule.powerTarget > 1.0) {
                add(SpecProblem("RULE_POWER", "decisionRule.powerTarget must be in (0, 1]"))
            }
        }
    }

    fun isValid(): Boolean = problems().isEmpty()

    /** True when a dataset has not been pinned by both content hash and version. */
    fun unpinnedDatasets(): List<DatasetRef> =
        datasets.filter { it.sha256.isBlank() || it.version.isBlank() }

    fun isPinned(): Boolean = unpinnedDatasets().isEmpty()

    /**
     * Canonical, order-independent encoding. Datasets and parameters are sorted so that two
     * callers building the same spec in a different order hash identically.
     */
    fun canonical(): String {
        val separator = '\u001F'
        val lineBreak = '\u001E'
        fun field(value: String) = value.replace(separator, ' ').replace(lineBreak, ' ')

        val datasetLines = datasets
            .sortedWith(compareBy({ it.ref }, { it.version }))
            .joinToString(lineBreak.toString()) { ds ->
                listOf(
                    field(ds.ref),
                    field(ds.version),
                    field(ds.sha256),
                    field(ds.license),
                    ds.access.name,
                    ds.dataClass.name,
                    field(ds.datasetId ?: ""),
                ).joinToString(separator.toString())
            }

        val paramLines = method.params.entries
            .sortedBy { it.key }
            .joinToString(lineBreak.toString()) { (k, v) -> field(k) + "=" + field(v) }

        val rule = decisionRule
        val ruleLine = if (rule == null) {
            "none"
        } else {
            listOf(
                field(rule.metric),
                rule.comparison,
                num(rule.threshold),
                num(rule.alpha),
                num(rule.powerTarget),
                field(rule.multiplicity),
                rule.equivalenceMargin?.let { num(it) } ?: "none",
            ).joinToString(separator.toString())
        }

        return buildString {
            append("experimentspec/v1").append(lineBreak)
            append(
                listOf(
                    field(id),
                    version.toString(),
                    experimentClass.name,
                    field(title),
                    field(hypothesis),
                    field(direction),
                    field(design),
                    field(unit),
                    field(contrast),
                    field(outcome),
                    confirmatory.toString(),
                    field(ethics),
                    num(budgetUsd),
                    budgetCpuMinutes.toString(),
                    budgetGpuMinutes.toString(),
                ).joinToString(separator.toString())
            ).append(lineBreak)
            append(datasetLines).append(lineBreak)
            append(
                listOf(
                    field(method.pipeline),
                    field(method.version),
                    field(method.imageDigest),
                    method.seed.toString(),
                ).joinToString(separator.toString())
            ).append(lineBreak)
            append(paramLines).append(lineBreak)
            append(ruleLine).append(lineBreak)
            append(expectedArtifacts.sorted().joinToString(separator.toString()))
        }
    }

    /** Full 64-character spec hash. Any field change produces a different value. */
    fun sha256(): String = sha256Hex(canonical())

    /** Short form for UI and log lines. */
    fun shortHash(): String = sha256().take(16)

    companion object {
        private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]{2,62}$")

        fun sha256Hex(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }

        private fun num(value: Double): String =
            String.format(Locale.US, "%.6f", value)
    }
}

data class SpecProblem(val code: String, val message: String) {
    override fun toString(): String = "$code: $message"
}

/**
 * Experiment classes, each with its own required fields and default autonomy. Only classes that
 * can update a claim are allowed to produce a [ClaimUpdate.Verdict].
 */
enum class ExperimentClass(val label: String, val canUpdateClaim: Boolean) {
    REPLICATION("Replication", true),
    ROBUSTNESS("Robustness / multiverse", true),
    DATASET("Dataset construction", false),
    NEGATIVE_CONTROL("Negative control", false),
    BENCHMARK("Method benchmark", false),
    SIMULATION("Simulation", true),
    HYPOTHESIS_GENESIS("Hypothesis genesis", false),
    WETLAB_DESIGN("Wet-lab design", false),

    /**
     * An unparseable class. Deliberately not [REPLICATION]: defaulting a typo to the most
     * executable class would turn a document into a runnable experiment.
     */
    UNKNOWN("Unknown", false),
    ;

    /** Wet-lab, hypothesis-generation and unknown specs are documents. They are never submitted. */
    val isExecutable: Boolean
        get() = this != WETLAB_DESIGN && this != HYPOTHESIS_GENESIS && this != UNKNOWN
}

enum class DataAccess(val label: String) {
    PUBLIC("Public"),
    CONTROLLED("Controlled (bring your own file)"),
    LOCAL_ONLY("Local only"),

    /** Unparseable access. Treated as non-public everywhere, so it fails closed. */
    UNKNOWN("Unknown"),
}

enum class DataClass(val wireValue: String) {
    AGGREGATE("aggregate"),
    DERIVED("derived"),
    PHI("phi"),

    /**
     * Demo data that exercises the pipeline. It may leave the host (it is public and contains no
     * real patients) but it can never support a scientific claim - see the adjudicator's
     * `SYNTHETIC_DATA` refusal.
     */
    SYNTHETIC("synthetic"),

    /**
     * Unparseable data class. Must never be treated as aggregate: a typo in a source file
     * would otherwise let patient-level data leave the host.
     */
    UNKNOWN("unknown"),
    ;

    /** Only aggregate, derived and synthetic artifacts may leave the host. */
    val mayLeaveHost: Boolean
        get() = this == AGGREGATE || this == DERIVED || this == SYNTHETIC
}

data class DatasetRef(
    val ref: String,
    val version: String,
    val sha256: String,
    val license: String,
    val access: DataAccess,
    val dataClass: DataClass,
    val datasetId: String? = null,
)

data class MethodRef(
    val pipeline: String,
    val version: String,
    val imageDigest: String,
    val params: Map<String, String> = emptyMap(),
    val seed: Long = 0L,
)

/**
 * Written before the result is seen. [comparison] is deliberately a small closed vocabulary:
 * an unsupported comparison is refused, never guessed at.
 */
data class DecisionRule(
    val metric: String,
    val comparison: String,
    val threshold: Double,
    val alpha: Double = 0.05,
    val powerTarget: Double = 0.80,
    val multiplicity: String = "none",
    val equivalenceMargin: Double? = null,
) {
    companion object {
        const val LT = "lt"
        const val LEQ = "leq"
        const val GT = "gt"
        const val GEQ = "geq"
        const val CI_BELOW = "ci_below"
        const val CI_ABOVE = "ci_above"

        val SUPPORTED_COMPARISONS = listOf(LT, LEQ, GT, GEQ, CI_BELOW, CI_ABOVE)
    }
}

/** A locked analysis plan. [specHash] must equal the spec at adjudication time. */
data class Preregistration(
    val specHash: String,
    val lockedAtUtc: String,
    val lockedBy: String,
)

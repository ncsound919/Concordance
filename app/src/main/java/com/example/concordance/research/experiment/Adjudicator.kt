package com.example.concordance.research.experiment

/**
 * The only path from a finished run to knowledge.
 *
 * [adjudicate] recomputes the planned decision rule from reported metrics and refuses anything it
 * cannot stand behind: an exploratory run, a plan that drifted from its spec, an unverified
 * artifact, a missing metric, a rule it does not implement. A refusal is a result, not an error.
 */
object Adjudicator {

    fun adjudicate(
        spec: ExperimentSpec,
        plan: RunPlan,
        record: RunRecord,
        outcome: RunOutcome,
        metrics: Map<String, Double> = emptyMap(),
        executed: ExecutedRun? = null,
    ): ClaimUpdate {
        if (!spec.experimentClass.canUpdateClaim) {
            return ClaimUpdate.Refused(
                "CLASS_CANNOT_UPDATE",
                "${spec.experimentClass.label} runs do not update claims"
            )
        }
        // A fixture proves the plumbing works. It must never become a scientific claim.
        if (spec.datasets.any { it.dataClass == DataClass.SYNTHETIC }) {
            return ClaimUpdate.Refused(
                "SYNTHETIC_DATA",
                "synthetic fixtures exercise the pipeline; they cannot produce a scientific claim"
            )
        }
        if (!spec.confirmatory) {
            return ClaimUpdate.Refused(
                "EXPLORATORY_RUN",
                "exploratory runs cannot update a claim; write a decision rule and preregister"
            )
        }
        if (record.state != RunState.COMPLETED) {
            return ClaimUpdate.Refused(
                "RUN_NOT_COMPLETED",
                "run is ${record.state.name}, not COMPLETED"
            )
        }
        // The plan must still belong to this exact spec version.
        if (plan.specHash != spec.sha256() || plan.specHash != record.plan.specHash) {
            return ClaimUpdate.Refused(
                "PLAN_STALE",
                "the plan was compiled from a different spec version"
            )
        }
        // The plan must hash to its own identity, so an edited kernel or mount path is detected.
        if (plan.planHash != plan.computeHash()) {
            return ClaimUpdate.Refused(
                "PLAN_TAMPERED",
                "the plan does not hash to its recorded planHash"
            )
        }
        // Confirmatory work needs the lock to be an event on the chain, not a claim in a document.
        if (spec.confirmatory) {
            val preregistration = record.preregistration
                ?: return ClaimUpdate.Refused(
                    "PREREGISTRATION_MISSING",
                    "the run record has no preregistration event"
                )
            if (preregistration.specHash != spec.sha256()) {
                return ClaimUpdate.Refused(
                    "PREREGISTRATION_STALE",
                    "the preregistration was locked against a different spec version"
                )
            }
        }

        when (outcome) {
            is RunOutcome.Refused -> return ClaimUpdate.Refused(outcome.code, outcome.reason)
            is RunOutcome.Failed -> return ClaimUpdate.Refused("RUN_FAILED", outcome.reason)
            is RunOutcome.Completed -> Unit
        }
        val artifacts = (outcome as RunOutcome.Completed).artifacts
        val byName = artifacts.associateBy { it.name }

        val missing = plan.requiredArtifacts.filter { it !in byName }
        if (missing.isNotEmpty()) {
            return ClaimUpdate.Refused(
                "ARTIFACT_MISSING",
                "missing required artifacts: ${missing.joinToString(", ")}"
            )
        }
        val unverified = plan.requiredArtifacts.mapNotNull { byName[it] }.filter { !it.verified }
        if (unverified.isNotEmpty()) {
            return ClaimUpdate.Refused(
                "ARTIFACT_UNVERIFIED",
                "artifact hashes not verified: ${unverified.joinToString(", ") { it.name }}"
            )
        }

        val rule = spec.decisionRule
            ?: return ClaimUpdate.Refused("NO_DECISION_RULE", "spec has no decision rule")
        val value = metrics[rule.metric]
            ?: return ClaimUpdate.Refused(
                "METRIC_MISSING",
                "artifact set does not report '${rule.metric}'"
            )
        if (!value.isFinite()) {
            return ClaimUpdate.Refused("METRIC_NOT_FINITE", "'${rule.metric}' is not a finite number")
        }

        val ciLow = metrics["${rule.metric}.ci_low"]
        val ciHigh = metrics["${rule.metric}.ci_high"]

        val status = when (rule.comparison) {
            DecisionRule.LT -> if (value < rule.threshold) ClaimStatus.SUPPORTED else ClaimStatus.CONTRADICTED
            DecisionRule.LEQ -> if (value <= rule.threshold) ClaimStatus.SUPPORTED else ClaimStatus.CONTRADICTED
            DecisionRule.GT -> if (value > rule.threshold) ClaimStatus.SUPPORTED else ClaimStatus.CONTRADICTED
            DecisionRule.GEQ -> if (value >= rule.threshold) ClaimStatus.SUPPORTED else ClaimStatus.CONTRADICTED
            DecisionRule.CI_BELOW, DecisionRule.CI_ABOVE -> {
                if (ciLow == null || ciHigh == null) {
                    return ClaimUpdate.Refused(
                        "CI_MISSING",
                        "rule '${rule.comparison}' needs '${rule.metric}.ci_low' and '${rule.metric}.ci_high'"
                    )
                }
                if (ciLow > ciHigh) {
                    return ClaimUpdate.Refused("CI_INVERTED", "metric interval is inverted")
                }
                if (rule.comparison == DecisionRule.CI_BELOW) {
                    when {
                        ciHigh < rule.threshold -> ClaimStatus.SUPPORTED
                        ciLow >= rule.threshold -> ClaimStatus.CONTRADICTED
                        else -> ClaimStatus.INCONCLUSIVE
                    }
                } else {
                    when {
                        ciLow > rule.threshold -> ClaimStatus.SUPPORTED
                        ciHigh <= rule.threshold -> ClaimStatus.CONTRADICTED
                        else -> ClaimStatus.INCONCLUSIVE
                    }
                }
            }
            else -> return ClaimUpdate.Refused(
                "RULE_UNSUPPORTED",
                "comparison '${rule.comparison}' is not implemented"
            )
        }

        // A run that was executed differently from the locked plan is reported, not hidden and
        // not promoted to a research-grade measurement.
        val deviations = mutableListOf<String>()
        if (executed != null) {
            if (executed.pipeline != spec.method.pipeline) {
                deviations += "pipeline ${executed.pipeline} != planned ${spec.method.pipeline}"
            }
            if (executed.version != spec.method.version) {
                deviations += "version ${executed.version} != planned ${spec.method.version}"
            }
            if (executed.seed !in plan.seeds) {
                deviations += "seed ${executed.seed} not in planned seeds ${plan.seeds}"
            }
        }

        return ClaimUpdate.Verdict(
            status = status,
            metric = rule.metric,
            value = value,
            ciLow = ciLow,
            ciHigh = ciHigh,
            thresholds = Thresholds(
                rule = rule.comparison,
                threshold = rule.threshold,
                alpha = rule.alpha,
                multiplicity = rule.multiplicity,
            ),
            deviations = deviations,
            evidenceTier = if (deviations.isEmpty()) "measured" else "deviated",
            runHash = record.ledgerHash(),
            specHash = spec.sha256(),
            planHash = plan.planHash,
        )
    }
}

sealed interface RunOutcome {
    data class Completed(val artifacts: List<RunArtifact>) : RunOutcome
    data class Failed(val reason: String) : RunOutcome
    data class Refused(val code: String, val reason: String) : RunOutcome
}

data class RunArtifact(
    val name: String,
    val sha256: String,
    val verified: Boolean,
)

data class ExecutedRun(
    val pipeline: String,
    val version: String,
    val seed: Long,
)

sealed interface ClaimUpdate {

    data class Verdict(
        val status: ClaimStatus,
        val metric: String,
        val value: Double,
        val ciLow: Double?,
        val ciHigh: Double?,
        val thresholds: Thresholds,
        val deviations: List<String>,
        val evidenceTier: String,
        val runHash: String,
        val specHash: String,
        val planHash: String,
    ) : ClaimUpdate

    data class Refused(val code: String, val reason: String) : ClaimUpdate
}

data class Thresholds(
    val rule: String,
    val threshold: Double,
    val alpha: Double,
    val multiplicity: String,
)

enum class ClaimStatus { SUPPORTED, CONTRADICTED, INCONCLUSIVE }

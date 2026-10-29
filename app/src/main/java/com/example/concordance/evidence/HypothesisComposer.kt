package com.example.concordance.evidence

import java.util.Locale
import kotlin.math.round

/**
 * Assembles a [Hypothesis] from edges that already exist.
 *
 * Three things this deliberately does **not** do:
 *
 * 1. It does not invent evidence. If no edge involves the candidate, it refuses.
 * 2. It does not let a sensitivity claim outvote a resistance claim. If resistance evidence is at
 *    least as strong, the candidate is refused rather than ranked lower.
 * 3. It does not approve anything. Every composed hypothesis is [ReviewStatus.UNREVIEWED].
 *
 * The rank is a documented blend, not a magic constant:
 *
 * ```
 * rank = 0.60 * mean(edge score)
 *      + 0.25 * min(1, evidence count / 4)
 *      + 0.15 * min(1, distinct evidence kinds / 4)
 * ```
 *
 * Weak on purpose: half the weight is evidence strength, the rest rewards corroboration from
 * independent sources. It cannot reach 1.0 from a single strong edge.
 */
object HypothesisComposer {

    data class Request(
        val cancerType: String,
        val biomarkerSymbol: String,
        val biomarkerType: String,
        val candidateDrug: String,
        val drugRxnorm: String,
        val modelVersion: String,
        val atUtc: String,
    )

    sealed interface Result {
        data class Built(val hypothesis: Hypothesis) : Result
        data class Refused(val code: String, val reason: String) : Result
    }

    fun compose(
        request: Request,
        edges: List<KgEdge>,
        pubmedIds: List<String> = emptyList(),
        datasetIds: List<String> = emptyList(),
        wetlabIds: List<String> = emptyList(),
    ): Result {
        val gene = request.biomarkerSymbol.trim().uppercase(Locale.US)
        val drug = slug(request.candidateDrug)

        val incomplete = listOf(
            "cancerType" to request.cancerType,
            "biomarkerSymbol" to gene,
            "biomarkerType" to request.biomarkerType,
            "candidateDrug" to request.candidateDrug,
            "drugRxnorm" to request.drugRxnorm,
            "modelVersion" to request.modelVersion,
            "atUtc" to request.atUtc,
        ).firstOrNull { it.second.isBlank() }
        if (incomplete != null) {
            return Result.Refused("INCOMPLETE_REQUEST", "${incomplete.first} is empty")
        }

        val geneNode = "gene:$gene"
        val drugNode = "drug:$drug"
        val diseaseNode = "disease:" + slug(request.cancerType)

        // An edge belongs to this candidate if it starts at the gene and ends at the drug or the
        // cancer type. Anything else is a different claim.
        val relevant = edges.filter { edge ->
            edge.subject.kind == NodeKind.GENE &&
                edge.subject.id.equals(geneNode, ignoreCase = true) &&
                (
                    (edge.objectRef.kind == NodeKind.DRUG && edge.objectRef.id.equals(drugNode, true)) ||
                        (edge.objectRef.kind == NodeKind.CANCER_TYPE && edge.objectRef.id.equals(diseaseNode, true))
                    )
        }

        val supporting = relevant.filter { it.predicate != Predicate.RESISTANT_TO }
        val contradicting = relevant.filter { it.predicate == Predicate.RESISTANT_TO }

        if (supporting.isEmpty()) {
            return Result.Refused(
                "NO_SUPPORTING_EDGE",
                "no edge links $gene to ${request.candidateDrug} or ${request.cancerType}",
            )
        }

        val bestSupport = supporting.maxOf { it.score }
        val bestResistance = contradicting.maxOfOrNull { it.score } ?: 0.0
        if (contradicting.isNotEmpty() && bestResistance >= bestSupport) {
            return Result.Refused(
                "RESISTANCE_CONTRADICTS",
                "resistance evidence ($bestResistance) is at least as strong as sensitivity " +
                    "evidence ($bestSupport) for $gene / ${request.candidateDrug}",
            )
        }

        val evidence = supporting.flatMap { it.evidence }.distinctBy { it.kind.name + ":" + it.id }
        val meanScore = supporting.sumOf { it.score } / supporting.size
        val countFactor = (supporting.size / 4.0).coerceAtMost(1.0)
        val diversity = (evidence.map { it.kind }.distinct().size / 4.0).coerceAtMost(1.0)
        val rank = (0.60 * meanScore + 0.25 * countFactor + 0.15 * diversity).coerceIn(0.0, 1.0)

        val rationale = buildString {
            append("$gene ${request.biomarkerType} in ${request.cancerType}: ")
            append("${supporting.size} supporting edge(s) for ${request.candidateDrug} ")
            append("(best score ${format(bestSupport)}).")
            if (contradicting.isNotEmpty()) {
                append(" Weaker resistance evidence was also found ")
                append("(best score ${format(bestResistance)}) and is listed as a caveat.")
            }
        }

        val hypothesis = Hypothesis(
            hypothesisId = Hypothesis.idFor(
                request.cancerType,
                gene,
                request.candidateDrug,
                request.modelVersion,
            ),
            cancerType = request.cancerType,
            biomarkerSymbol = gene,
            biomarkerType = request.biomarkerType,
            candidateDrug = request.candidateDrug,
            drugRxnorm = request.drugRxnorm,
            rationale = rationale,
            supportingEdgeIds = supporting.map { it.edgeId }.distinct().sorted(),
            pubmedIds = pubmedIds.distinct().sorted(),
            datasetIds = datasetIds.distinct().sorted(),
            wetlabIds = wetlabIds.distinct().sorted(),
            rankScore = round(rank * 10_000) / 10_000,
            modelVersion = request.modelVersion,
            generatedAtUtc = request.atUtc,
            reviewStatus = ReviewStatus.UNREVIEWED,
        )

        val problems = EvidenceRules.validateHypothesis(hypothesis, supporting.map { it.edgeId }.toSet())
        if (problems.isNotEmpty()) {
            return Result.Refused("HYPOTHESIS_INVALID", problems.first().message)
        }
        return Result.Built(hypothesis)
    }

    private fun slug(value: String): String = value.trim().lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)
}

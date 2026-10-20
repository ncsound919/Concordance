package com.example.concordance.integration.knowledge

/**
 * Public prior knowledge about one entity pair, assembled by INDRA
 * (https://db.indra.bio) from text mining and curated databases.
 *
 * This is background for a human reviewer: belief scores are model estimates,
 * and opposing statements are a review trigger - never a resolved verdict.
 */
data class IndraEvidence(
    val text: String,
    val source: String,
    val pmid: String,
    val doi: String
)

data class IndraStatement(
    val statementType: String,
    val subject: String,
    val objectName: String,
    val belief: Double,
    val sources: List<String>,
    val evidence: List<IndraEvidence>,
    val url: String
)

data class IndraKnowledge(
    val subject: String,
    val objectName: String?,
    val statementCount: Int,
    val types: Map<String, Int>,
    val statements: List<IndraStatement>,
    val disclaimer: String
)

data class IndraContradictionPair(
    val types: List<String>,
    val positive: IndraStatement,
    val negative: IndraStatement,
    val beliefGap: Double
)

data class IndraContradictions(
    val subject: String,
    val objectName: String,
    val contradicted: Boolean,
    val pairs: List<IndraContradictionPair>,
    val disclaimer: String
)

/** Refusals and transport failures are results, not exceptions. */
sealed interface IndraResult<out T> {
    data class Ok<T>(val value: T) : IndraResult<T>

    /** The public database could not be reached or returned something unusable. */
    data class Unavailable(val reason: String) : IndraResult<Nothing>

    /** The query was not a public entity name, so it was never sent. */
    data class Refused(val reason: String) : IndraResult<Nothing>
}

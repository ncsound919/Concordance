package com.example.concordance.integration.context

/**
 * trim-1 (Caveman) context pruner client surface.
 *
 * trim-1 is a 1.2 MB model that decides which lines of a context an agent needs
 * for the current ask and drops the rest. It is a *pruner*, not a decision-maker,
 * so it never touches adjudication inputs consumed by the deterministic engine.
 * It only prunes model-facing context (OCR text, paper text, INDRA statements).
 *
 * This module provides the deterministic contract + a rule-based fallback pruner
 * so wiring and tests work without the model weights.
 */
data class TrimRequest(
    val question: String,
    val lines: List<String>,
    val maxLines: Int = 40
)

data class TrimResult(
    val kept: List<String>,
    val dropped: Int,
    val disclaimer: String
)

sealed interface TrimOutcome<out T> {
    data class Ok<T>(val value: T) : TrimOutcome<T>
    data class Unavailable(val reason: String) : TrimOutcome<Nothing>
}

class TrimClient(
    private val modelAvailable: () -> Boolean = { false }
) {
    fun isAvailable(): Boolean = modelAvailable()

    fun prune(request: TrimRequest): TrimOutcome<TrimResult> {
        if (request.question.isBlank()) return TrimOutcome.Unavailable("Question is blank.")
        if (request.lines.isEmpty()) return TrimOutcome.Unavailable("No context to prune.")
        val max = request.maxLines.coerceIn(1, request.lines.size)
        if (request.lines.size <= max) {
            return TrimOutcome.Ok(TrimResult(request.lines, 0, NO_OP_DISCLAIMER))
        }
        val qTokens = tokenize(request.question)
        val scored = request.lines.mapIndexed { idx, line ->
            val score = scoreLine(line, qTokens, idx, request.lines.size)
            Triple(idx, line, score)
        }
        val keptIdx = scored.sortedByDescending { it.third }.take(max).map { it.first }.toSortedSet()
        val kept = request.lines.filterIndexed { i, _ -> i in keptIdx }
        val dropped = request.lines.size - kept.size
        return TrimOutcome.Ok(TrimResult(kept, dropped, RULE_DISCLAIMER))
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }.toSet()

    private fun scoreLine(line: String, qTokens: Set<String>, idx: Int, total: Int): Double {
        val lTokens = tokenize(line)
        val overlap = if (qTokens.isEmpty()) 0.0 else (lTokens intersect qTokens).size.toDouble() / qTokens.size
        // Slight recency bias: later lines slightly preferred on ties.
        val recency = idx.toDouble() / total.coerceAtLeast(1)
        return overlap + recency * 0.05
    }

    companion object {
        const val DEFAULT_MAX = 40
        const val NO_OP_DISCLAIMER = "Context already within budget; nothing pruned."
        const val RULE_DISCLAIMER =
            "Rule-based context prune (trim-1 weights not installed). " +
                "Model-facing context only; adjudication inputs untouched."
    }
}

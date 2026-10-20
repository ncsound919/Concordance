package com.example.concordance.integration.agent

/**
 * Connection settings for the self-hosted adjudication agent.
 *
 * The agent is expected to run on your own machine or LAN (an emulator reaches the
 * host loopback at 10.0.2.2). Nothing in this client talks to a managed platform.
 */
data class AgentConfig(
    val baseUrl: String = "",
    val apiKey: String? = null,
    val timeoutSeconds: Long = 90,
    val modelName: String? = null
) {
    val normalizedBaseUrl: String
        get() = baseUrl.trim().trimEnd('/')

    val isConfigured: Boolean
        get() = normalizedBaseUrl.startsWith("http://") || normalizedBaseUrl.startsWith("https://")
}

/**
 * Outcome of an agent call.
 *
 * [Refused] is deliberately separate from [Failed]: the agent refuses when the
 * evidence cannot support an answer (unknown anchor, abstract-only claim, PHI routed
 * off-host). A refusal is a *result* the UI must present honestly, not an error.
 */
sealed interface AgentOutcome<out T> {
    data class Ok<T>(val value: T, val status: Int = 200) : AgentOutcome<T>
    data class Refused(val reason: String, val status: Int) : AgentOutcome<Nothing>
    data class Failed(val message: String, val status: Int = 0) : AgentOutcome<Nothing>
}

data class AgentAnchor(
    val id: String,
    val label: String,
    val evidenceGrade: String,
    val source: String
)

data class AgentCapsule(
    val anchorId: String,
    val verdict: String,
    val reason: String,
    val evidenceTier: String,
    val rubricVersion: String,
    val correctedOddsRatio: Double?,
    val correctedCi: Pair<Double, Double>?,
    val significanceThresholdR: Double?,
    val power: Double?,
    val decisionTrace: List<String>
)

data class AgentJob(
    val jobId: String,
    val status: String,
    val capsule: AgentCapsule?,
    val error: String?,
    val rawJson: String
) {
    val isTerminal: Boolean
        get() = status.lowercase() in setOf("completed", "failed", "withheld", "stalled")

    /**
     * The agent declined to give a verdict. `withheld` is a real answer â€” it means the
     * evidence did not support a call â€” so the UI must present it as a result.
     */
    val wasRefused: Boolean
        get() = capsule?.verdict == "withheld" || status.lowercase() == "withheld"
}

data class CorrectionResult(
    val anchorId: String,
    val status: String,
    val evidenceTier: String,
    val r: Double?,
    val correctedOddsRatio: Double?,
    val correctedCi: Pair<Double, Double>?,
    val significanceThresholdR: Double?,
    val minFeasibleR: Double?,
    val significantAtR: Boolean,
    val feasible: Boolean,
    val interpretation: String?,
    val reason: String?,
    val rawJson: String
)

data class UploadedFigure(
    val uploadSha256: String,
    val contentType: String,
    val bytes: Int,
    val dataClass: String,
    val engine: String,
    val text: String,
    val confidence: Double,
    val pageCount: Int,
    val evidenceTier: String,
    val humanVerified: Boolean,
    val adjudicationReady: Boolean,
    val disclaimer: String
)

data class ImagingStatus(
    val ocrAvailable: Boolean,
    val pdfAvailable: Boolean,
    val maxUploadMb: Int,
    val acceptedMediaTypes: List<String>,
    val notes: String
)

data class GlossaryTerm(val term: String, val plain: String, val why: String)

/**
 * The agent's own plain-language translation of a result (`app/explain.py`).
 *
 * The client never recomputes or simplifies these numbers: it only arranges text the
 * server produced, so the easy view and the expert view can never disagree.
 */
data class EasyModeView(
    val headline: String,
    val signalBand: String,
    val signalLabel: String,
    val signalDetail: String,
    val evidenceTier: String,
    val qualityLabel: String,
    val qualityDetail: String,
    val whatWeDid: List<String>,
    val whatWeFound: List<Pair<String, String>>,
    val doesNotMean: List<String>,
    val nextSteps: List<String>,
    val wordsWeUsed: List<GlossaryTerm>,
    val numbers: List<Pair<String, String>>
) {
    /** Bands that must never be read as "there is definitely no effect". */
    val isNegativeish: Boolean
        get() = signalBand in setOf("none", "cannot_tell", "not_measurable")

    /** An assumption-based or missing number is explicitly not a measured fact. */
    val restsOnAssumptions: Boolean
        get() = evidenceTier == "configured" || evidenceTier == "unmeasured"
}


data class AgentResearchResult(
    val query: String,
    val summary: String,
    val literatureQueries: List<String>,
    val hypotheses: List<String>,
    val experiments: List<AgentExperiment>,
    val nextSteps: List<String>,
    val rawJson: String
)

data class AgentExperiment(
    val title: String,
    val design: String,
    val controls: String,
    val endpoints: String,
    val estimatedDuration: String,
    val risk: String
)

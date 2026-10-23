package com.example.concordance.integration.science

import com.example.concordance.engine.LocalModelEngine
import com.example.concordance.engine.LocalModelExtractionResult
import com.example.concordance.integration.api.IntegrationQuery
import com.example.concordance.integration.api.IntegrationRegistry
import com.example.concordance.integration.api.NormalizedRecord
import com.example.concordance.local.DecisionModel
import com.example.concordance.local.ExtractionReview
import com.example.concordance.local.ExtractionState
import com.example.concordance.local.LocalExtractionClient
import com.example.concordance.local.LocalLlmService
import com.example.concordance.local.LocalLlmStatus
import com.example.concordance.model.Anchor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class SciencePaper(
    val id: String,
    val title: String,
    val authors: String,
    val journal: String,
    val year: Int,
    val doi: String,
    val pmid: String?,
    val abstractText: String,
    val sourceApi: String,
    val citationCount: Int,
    val tldr: String?
)

enum class AnalysisEngine(val label: String, val badge: String) {
    LOCAL_RULE_EXTRACTOR("On-device rule extractor", "Offline"),
    LOCAL_LLM("On-device LLM (llama.cpp)", "Offline"),
    CLOUD_MODEL("Cloud model (not configured)", "Unavailable")
}

data class PaperAnalysisOutput(
    val paper: SciencePaper,
    val engineUsed: AnalysisEngine,
    val extraction: LocalModelExtractionResult,
    val convertedAnchor: Anchor,
    val executionTimeMs: Long,
    /** Typed review gate: rules by default, on-device model when it is running. */
    val review: ExtractionReview
)

/** Result of a live literature fan-out across Europe PMC and OpenAlex. */
data class LiveLiteratureSearch(
    val papers: List<SciencePaper>,
    val liveRecords: Int,
    val providers: List<String>,
    val offlineFallback: Boolean
)

object SciencePaperManager {

    /** No fabricated citations: papers come only from live sources. */
    private val BENCHMARK_PAPERS: List<SciencePaper> = emptyList()

    private val _papers = MutableStateFlow(BENCHMARK_PAPERS)
    val papers: StateFlow<List<SciencePaper>> = _papers.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _selectedEngine = MutableStateFlow(AnalysisEngine.LOCAL_RULE_EXTRACTOR)
    val selectedEngine: StateFlow<AnalysisEngine> = _selectedEngine.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _latestAnalysis = MutableStateFlow<PaperAnalysisOutput?>(null)
    val latestAnalysis: StateFlow<PaperAnalysisOutput?> = _latestAnalysis.asStateFlow()

    fun setEngine(engine: AnalysisEngine) {
        _selectedEngine.value = engine
    }

    fun searchPapers(query: String): List<SciencePaper> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) return _papers.value
        return _papers.value.filter {
            it.title.lowercase(Locale.US).contains(q) ||
                    it.authors.lowercase(Locale.US).contains(q) ||
                    it.journal.lowercase(Locale.US).contains(q) ||
                    it.abstractText.lowercase(Locale.US).contains(q) ||
                    (it.pmid?.contains(q) == true)
        }
    }

    /**
     * Live search against the registered literature adapters (Europe PMC, OpenAlex).
     * Merges results into the in-memory corpus so they survive recomposition.
     */
    suspend fun searchLivePapers(query: String, pageSize: Int = 25): LiveLiteratureSearch {
        if (query.isBlank()) {
            return LiveLiteratureSearch(emptyList(), 0, emptyList(), offlineFallback = false)
        }
        val records = IntegrationRegistry.searchLiterature(
            IntegrationQuery(text = query, pageSize = pageSize)
        )
        val mapped = records.map { it.toSciencePaper() }
        if (mapped.isNotEmpty()) {
            _papers.value = (mapped + BENCHMARK_PAPERS).distinctBy { it.id }
            _currentPage.value = 0
        }
        return LiveLiteratureSearch(
            papers = if (mapped.isNotEmpty()) mapped else searchPapers(query),
            liveRecords = records.size,
            providers = records.map { it.source.substringBefore(" (") }.distinct(),
            offlineFallback = records.isEmpty()
        )
    }

    private fun NormalizedRecord.toSciencePaper(): SciencePaper {
        val stableId = when {
            pmid != null -> "PMID:$pmid"
            !doi.isNullOrBlank() -> "DOI:$doi"
            else -> externalId
        }
        return SciencePaper(
            id = stableId,
            title = title.ifBlank { "(untitled record)" },
            authors = authors.ifBlank { "Unknown authors" },
            journal = journal.ifBlank { "Unindexed venue" },
            year = year ?: 0,
            doi = doi.orEmpty(),
            pmid = pmid,
            abstractText = abstractText,
            sourceApi = source,
            citationCount = citationCount ?: 0,
            tldr = null
        )
    }

    /**
     * Fetches the next page of live literature and appends it to the corpus.
     * Returns the number of new records added.
     */
    suspend fun loadMorePapers(query: String, pageSize: Int = 25): Int {
        if (_isLoadingMore.value || query.isBlank()) return 0
        _isLoadingMore.value = true
        return try {
            val nextPage = _currentPage.value + 1
            val records = IntegrationRegistry.searchLiterature(
                IntegrationQuery(text = query, pageSize = pageSize, page = nextPage)
            )
            val mapped = records.map { it.toSciencePaper() }
            if (mapped.isNotEmpty()) {
                _papers.value = (_papers.value + mapped).distinctBy { it.id }
                _currentPage.value = nextPage
            }
            mapped.size
        } finally {
            _isLoadingMore.value = false
        }
    }

    suspend fun analyzePaper(paper: SciencePaper): PaperAnalysisOutput {
        _isAnalyzing.value = true
        val start = System.currentTimeMillis()

        val llmRequested = _selectedEngine.value == AnalysisEngine.LOCAL_LLM
        val llmReady = LocalLlmService.status.value is LocalLlmStatus.Ready

        // The local LLM is an assist: when the operator selected it and the
        // embedded server is running, try structured extraction; the moment
        // it fails or returns nothing, fall back to the rule extractor.
        if (llmRequested && llmReady) {
            val ready = LocalLlmService.status.value as LocalLlmStatus.Ready
            val llmExtraction = LocalExtractionClient.extract(paper.abstractText, port = ready.port)
            if (llmExtraction != null) {
                val anchor = LocalModelEngine.toAnchor(
                    llmExtraction, paper.title.take(30), "${paper.journal} (${paper.year})"
                )
                val output = PaperAnalysisOutput(
                    paper = paper,
                    engineUsed = AnalysisEngine.LOCAL_LLM,
                    extraction = llmExtraction,
                    convertedAnchor = anchor,
                    executionTimeMs = System.currentTimeMillis() - start,
                    review = DecisionModel.review(
                        llmExtraction.toDecisionState(paper.abstractText),
                        llmPort = ready.port
                    )
                )
                _latestAnalysis.value = output
                _isAnalyzing.value = false
                return output
            }
        }

        val extraction = LocalModelEngine.analyzeText(paper.abstractText)
        val anchor = LocalModelEngine.toAnchor(extraction, paper.title.take(30), "${paper.journal} (${paper.year})")
        val note = when {
            llmRequested && !llmReady ->
                " [On-device LLM not running; start it in Settings > BYOK and re-analyze. Rule extractor used.]"
            llmRequested ->
                " [Local LLM returned no structured data; the on-device rule extractor was used.]"
            _selectedEngine.value == AnalysisEngine.CLOUD_MODEL ->
                " [Cloud model is not configured; the on-device rule extractor was used.]"
            else -> ""
        }
        val output = PaperAnalysisOutput(
            paper = paper,
            engineUsed = AnalysisEngine.LOCAL_RULE_EXTRACTOR,
            extraction = extraction.copy(qualitativeSummary = extraction.qualitativeSummary + note),
            convertedAnchor = anchor,
            executionTimeMs = System.currentTimeMillis() - start,
            // The rule path never calls the model for a second opinion: it already
            // promised a 0 ms offline answer, and a failed LLM call is not retried
            // just to label the result.
            review = DecisionModel.review(extraction.toDecisionState(paper.abstractText), llmPort = null)
        )

        _latestAnalysis.value = output
        _isAnalyzing.value = false
        return output
    }

    /** Only the fields the review gate needs; nothing patient-identifying. */
    private fun LocalModelExtractionResult.toDecisionState(abstractText: String) = ExtractionState(
        abstractText = abstractText,
        gene = gene,
        variant = variant,
        brainEvents = brainEvents,
        brainTotal = brainTotal,
        otherEvents = otherEvents,
        otherTotal = otherTotal,
        oddsRatio = oddsRatio,
        reportedConfidence = confidenceScore
    )
}

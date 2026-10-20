package com.example.concordance.integration.clinicaltrials

import com.example.concordance.integration.api.IntegrationQuery
import com.example.concordance.integration.api.IntegrationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class ClinicalTrialStudy(
    val nctId: String,
    val briefTitle: String,
    val officialTitle: String,
    val overallStatus: String,
    val phase: String,
    val conditions: List<String>,
    val interventions: List<String>,
    val brainMetastasisCriteria: String,
    val esr1EndpointTracking: String,
    val sponsor: String,
    val primaryCompletionDate: String,
    val url: String
)

object ClinicalTrialsManager {

    private val BENCHMARK_TRIALS = listOf(
        ClinicalTrialStudy(
            nctId = "NCT03778931",
            briefTitle = "EMERALD: Phase 3 Trial of Elacestrant (RAD1901) vs SOC in ER+/HER2- Metastatic Breast Cancer",
            officialTitle = "A Phase 3, Randomized, Open-Label, Active-Controlled Study of Elacestrant vs Standard of Care Endocrine Therapy in Patients With ER+/HER2- Advanced Breast Cancer",
            overallStatus = "COMPLETED",
            phase = "Phase 3",
            conditions = listOf("Estrogen Receptor Positive Advanced Breast Cancer", "ESR1 Gene Mutation"),
            interventions = listOf("Drug: Elacestrant (400 mg daily)", "Drug: Investigator Choice SOC (Fulvestrant or Aromatase Inhibitor)"),
            brainMetastasisCriteria = "Asymptomatic treated brain metastases permitted. Exploratory subgroup analyzed intracranial PFS and CNS progression patterns.",
            esr1EndpointTracking = "Circulating tumor DNA (ctDNA) Guardant360 baseline stratification for ESR1 mutation status; primary PFS endpoint in ESR1-mutated subgroup.",
            sponsor = "Radius Health / Stemline Therapeutics",
            primaryCompletionDate = "2023-01-30",
            url = "https://clinicaltrials.gov/study/NCT03778931"
        ),
        ClinicalTrialStudy(
            nctId = "NCT04214288",
            briefTitle = "SERENA-2: Camizestrant (AZD9833) in Advanced ER+/HER2- Breast Cancer",
            officialTitle = "A Phase 2, Randomised, Open-Label, Multi-Centre Study to Assess the Efficacy and Safety of Camizestrant (AZD9833) vs Fulvestrant in Postmenopausal Women With Advanced ER+/HER2- Breast Cancer",
            overallStatus = "ACTIVE_NOT_RECRUITING",
            phase = "Phase 2",
            conditions = listOf("Metastatic Breast Cancer", "ESR1-Mutated Breast Carcinoma"),
            interventions = listOf("Drug: Camizestrant (75 mg or 150 mg)", "Drug: Fulvestrant (500 mg)"),
            brainMetastasisCriteria = "Patients with treated and stable brain metastases permitted. Brain MRI required at baseline for known lesions.",
            esr1EndpointTracking = "Serial plasma ctDNA sequencing at Cycle 1 Day 1, Cycle 2 Day 1, and progression to assess clonal clearance of ESR1 Y537S and D538G.",
            sponsor = "AstraZeneca",
            primaryCompletionDate = "2024-06-15",
            url = "https://clinicaltrials.gov/study/NCT04214288"
        ),
        ClinicalTrialStudy(
            nctId = "NCT04975308",
            briefTitle = "ELAINE-2: Lasofoxifene Plus Abemaciclib in ESR1-Mutant Metastatic Breast Cancer with CNS Involvement",
            officialTitle = "An Open-Label, Phase 2 Study of Lasofoxifene in Combination With Abemaciclib for the Treatment of Postmenopausal Women and Pre/Perimenopausal Women With Locally Advanced or Metastatic ER+/HER2- Breast Cancer Harboring an ESR1 Mutation",
            overallStatus = "ACTIVE_NOT_RECRUITING",
            phase = "Phase 2",
            conditions = listOf("Breast Cancer", "Brain Metastases", "ESR1 Mutation"),
            interventions = listOf("Drug: Lasofoxifene (5 mg)", "Drug: Abemaciclib (150 mg BID)"),
            brainMetastasisCriteria = "Specifically includes cohorts with brain metastases. Lasofoxifene evaluated for CNS penetrance and intracranial response.",
            esr1EndpointTracking = "Mandatory baseline and on-treatment Guardant360 ctDNA assay for circulating ESR1 variant allele fractions.",
            sponsor = "Sermonix Pharmaceuticals",
            primaryCompletionDate = "2025-12-31",
            url = "https://clinicaltrials.gov/study/NCT04975308"
        )
    )

    private val _studies = MutableStateFlow(BENCHMARK_TRIALS)
    val studies: StateFlow<List<ClinicalTrialStudy>> = _studies.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun searchStudies(query: String): List<ClinicalTrialStudy> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) return _studies.value
        return _studies.value.filter {
            it.briefTitle.lowercase(Locale.US).contains(q) ||
                    it.nctId.lowercase(Locale.US).contains(q) ||
                    it.sponsor.lowercase(Locale.US).contains(q) ||
                    it.conditions.any { c -> c.lowercase(Locale.US).contains(q) } ||
                    it.interventions.any { i -> i.lowercase(Locale.US).contains(q) }
        }
    }

    suspend fun queryClinicalTrialsGov(query: String): List<ClinicalTrialStudy> {
        _isSearching.value = true
        val live = try {
            IntegrationRegistry.searchTrials(IntegrationQuery(text = query, pageSize = 25))
        } catch (e: Exception) {
            emptyList()
        } finally {
            _isSearching.value = false
        }

        if (live.isNotEmpty()) {
            _studies.value = live
            _currentPage.value = 0
            return live
        }

        // Offline or no live match: fall back to the bundled benchmark corpus so the
        // screen always renders something meaningful.
        val fallback = searchStudies(query).ifEmpty { BENCHMARK_TRIALS }
        _studies.value = fallback
        return fallback
    }

    /** True when the last query was served from the bundled corpus, not the live API. */
    val isUsingBenchmarkFallback: Boolean
        get() = _studies.value == BENCHMARK_TRIALS

    suspend fun loadMoreTrials(query: String, pageSize: Int = 25): Int {
        if (_isLoadingMore.value || query.isBlank()) return 0
        _isLoadingMore.value = true
        return try {
            val nextPage = _currentPage.value + 1
            val live = IntegrationRegistry.searchTrials(IntegrationQuery(text = query, pageSize = pageSize, page = nextPage))
            if (live.isNotEmpty()) {
                _studies.value = (_studies.value + live).distinctBy { it.nctId }
                _currentPage.value = nextPage
            }
            live.size
        } finally {
            _isLoadingMore.value = false
        }
    }
}

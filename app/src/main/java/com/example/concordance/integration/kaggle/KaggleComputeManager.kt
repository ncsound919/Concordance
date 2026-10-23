package com.example.concordance.integration.kaggle

import com.example.concordance.integration.api.HttpJsonClient
import com.example.concordance.integration.api.RateLimitSpec
import com.example.concordance.integration.rotation.ApiRotationManager
import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import com.example.concordance.model.Anchor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class KaggleDataset(
    val ref: String,
    val title: String,
    val description: String,
    val totalSamples: Int,
    val pairedSamples: Int,
    val sizeMb: Double,
    val lastUpdated: String,
    val csvData: String
)

enum class KaggleJobStatus(val label: String, val colorHex: Long) {
    QUEUED("Queued in Kaggle Cluster", 0xFFFFD740),
    RUNNING("Running GPU/CPU Compute", 0xFF00E5FF),
    COMPLETED("Completed (Artifact Ready)", 0xFF00E676),
    FAILED("Failed", 0xFFFF5252)
}

data class KaggleComputeJob(
    val id: String,
    val title: String,
    val kernelSlug: String,
    val status: KaggleJobStatus,
    val progressPercent: Int,
    val executionTimeSec: Int,
    val resultSummary: String?,
    val timestamp: String = "2026-10-18"
)

object KaggleComputeManager {

    private val PRESET_DATASETS = listOf(
        KaggleDataset(
            ref = "cbioportal/breast-cancer-met-tropism",
            title = "cBioPortal Breast Cancer Organotropism Cohort",
            description = "Synthetic demonstration cohort (not patient data) for exercising the paired analyzer. Denominator: rows.",
            totalSamples = 16338,
            pairedSamples = 412,
            sizeMb = 4.2,
            lastUpdated = "2026-08-14",
            csvData = buildString {
                append("patient_id,site,tissue_assay,plasma_assay,tissue_result,plasma_result,data_source\n")
                for (i in 1..40) append("CBP-BRN-$i,brain,FoundationOne-CDx,FoundationOne-Liquid,yes,no,synthetic\n")
                for (i in 41..70) append("CBP-BRN-$i,brain,FoundationOne-CDx,FoundationOne-Liquid,yes,yes,synthetic\n")
                for (i in 1..25) append("CBP-OTH-$i,non_cns,FoundationOne-CDx,FoundationOne-Liquid,yes,no,synthetic\n")
                for (i in 26..220) append("CBP-OTH-$i,non_cns,FoundationOne-CDx,FoundationOne-Liquid,yes,yes,synthetic\n")
            }
        ),
        KaggleDataset(
            ref = "genomics/esr1-plasma-tissue-paired",
            title = "ESR1 Y537S/D538G Paired Plasma vs Tissue Matrix",
            description = "Synthetic ESR1 demonstration rows (not patient data) for exercising discordance analysis.",
            totalSamples = 840,
            pairedSamples = 320,
            sizeMb = 1.8,
            lastUpdated = "2026-09-02",
            csvData = buildString {
                append("patient_id,site,tissue_assay,plasma_assay,tissue_result,plasma_result,ctdna_fraction,limit_of_detection,data_source\n")
                // Brain rows with discordance (r < 0.40)
                for (i in 1..35) append("ESR-BRN-$i,brain,Tissue-NGS,Plasma-cfDNA,yes,no,0.001,0.005,synthetic\n")
                for (i in 36..55) append("ESR-BRN-$i,brain,Tissue-NGS,Plasma-cfDNA,yes,yes,0.022,0.005,synthetic\n")
                // Extracranial rows with high concordance
                for (i in 1..15) append("ESR-OTH-$i,non_cns,Tissue-NGS,Plasma-cfDNA,yes,no,0.002,0.005,synthetic\n")
                for (i in 16..160) append("ESR-OTH-$i,non_cns,Tissue-NGS,Plasma-cfDNA,yes,yes,0.035,0.005,synthetic\n")
            }
        ),
        KaggleDataset(
            ref = "oncology/brain-metastasis-cfdna-benchmarks",
            title = "July 2026 Current Oncology CNS Discordance Benchmark",
            description = "Synthetic CNS-discordance demonstration rows (not a clinical dataset).",
            totalSamples = 201,
            pairedSamples = 88,
            sizeMb = 0.6,
            lastUpdated = "2026-07-28",
            csvData = buildString {
                append("patient_id,site,tissue_assay,plasma_assay,tissue_result,plasma_result,ctdna_fraction,limit_of_detection,data_source\n")
                append("CO-2026-CASE,brain,Tissue-Exome,Liquid-Panel,yes,no,0.000,0.005,synthetic\n")
                for (i in 1..20) append("CO-BRN-$i,brain,Tissue-Exome,Liquid-Panel,yes,no,0.002,0.005,synthetic\n")
                for (i in 21..32) append("CO-BRN-$i,brain,Tissue-Exome,Liquid-Panel,yes,yes,0.015,0.005,synthetic\n")
                for (i in 1..6) append("CO-OTH-$i,non_cns,Tissue-Exome,Liquid-Panel,yes,no,0.001,0.005,synthetic\n")
                for (i in 7..55) append("CO-OTH-$i,non_cns,Tissue-Exome,Liquid-Panel,yes,yes,0.028,0.005,synthetic\n")
            }
        )
    )

    private val _availableDatasets = MutableStateFlow(PRESET_DATASETS)
    val availableDatasets: StateFlow<List<KaggleDataset>> = _availableDatasets.asStateFlow()

    private val _baseUrl = MutableStateFlow("https://www.kaggle.com/api/v1")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _apiTokens = MutableStateFlow<List<String>>(emptyList())
    val apiTokens: StateFlow<List<String>> = _apiTokens.asStateFlow()

    fun updateConfig(apiToken: String?, baseUrl: String? = null) {
        updateConfig(listOfNotNull(apiToken?.takeIf { it.isNotBlank() }), baseUrl)
    }

    fun updateConfig(apiTokens: List<String>, baseUrl: String? = null) {
        _apiTokens.value = apiTokens.map { it.trim() }.filter { it.isNotBlank() }
        if (!baseUrl.isNullOrBlank()) _baseUrl.value = baseUrl.trimEnd('/')
    }

    val isConfigured: Boolean get() = _apiTokens.value.isNotEmpty()

    private fun nextToken(): String? = _apiTokens.value.firstOrNull()

    private val _computeJobs = MutableStateFlow<List<KaggleComputeJob>>(emptyList())
    val computeJobs: StateFlow<List<KaggleComputeJob>> = _computeJobs.asStateFlow()

    /** Real (remote) dataset search via the Kaggle API when a token is configured.
     *  Falls back to the local preset filter otherwise. */
    suspend fun fetchRemoteDatasets(query: String): List<KaggleDataset> {
        val token = nextToken() ?: return searchDatasets(query)
        val provider = ApiRotationManager.getNextProvider(ProviderCategory.DATASET_COMPUTE)
        val url = "${_baseUrl.value}/datasets/list?search=${java.net.URLEncoder.encode(query, "UTF-8")}&page=1"
        val result = HttpJsonClient.getJson(
            providerId = provider.id,
            url = url,
            queryLabel = "kaggle-datasets-search",
            spec = RateLimitSpec(requestsPerSecond = 5.0, burst = 5),
            licenseClass = LicenseClass.OPEN,
            authToken = token
        )
        if (result == null) return searchDatasets(query)
        // Parse the JSON array into KaggleDataset entries (best-effort; keep csvData empty).
        return try {
            val arr = org.json.JSONArray(result.body)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                KaggleDataset(
                    ref = o.optString("ref", o.optString("id", "unknown")),
                    title = o.optString("title", "Untitled"),
                    description = o.optString("subtitle", ""),
                    totalSamples = o.optInt("totalBytes", 0) / 1024,
                    pairedSamples = 0,
                    sizeMb = o.optDouble("totalBytes", 0.0) / (1024.0 * 1024.0),
                    lastUpdated = o.optString("lastUpdated", ""),
                    csvData = ""
                )
            }
        } catch (e: Exception) {
            searchDatasets(query)
        }
    }

    fun searchDatasets(query: String): List<KaggleDataset> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) return _availableDatasets.value
        return _availableDatasets.value.filter {
            it.title.lowercase(Locale.US).contains(q) ||
                    it.ref.lowercase(Locale.US).contains(q) ||
                    it.description.lowercase(Locale.US).contains(q)
        }
    }

    suspend fun triggerPermutationTestKernel(
        anchor: Anchor,
        iterations: Int = 10000
    ): KaggleComputeJob {
        val job = KaggleComputeJob(
            id = "kjob_${UUID.randomUUID().toString().take(8)}",
            title = "Permutation test for ${anchor.label.take(24)}",
            kernelSlug = "concordance/permutation-test-r-star",
            status = KaggleJobStatus.FAILED,
            progressPercent = 0,
            executionTimeSec = 0,
            resultSummary = "No kernel was submitted: this build does not implement Kaggle job submission."
        )
        _computeJobs.value = listOf(job) + _computeJobs.value
        return job
    }

    suspend fun triggerBootstrapConfidenceIntervalKernel(
        anchor: Anchor,
        resamples: Int = 1000
    ): KaggleComputeJob {
        val job = KaggleComputeJob(
            id = "kjob_${UUID.randomUUID().toString().take(8)}",
            title = "Bootstrap CI for ${anchor.label.take(24)}",
            kernelSlug = "concordance/bootstrap-concordance-ci",
            status = KaggleJobStatus.FAILED,
            progressPercent = 0,
            executionTimeSec = 0,
            resultSummary = "No kernel was submitted: this build does not implement Kaggle job submission."
        )
        _computeJobs.value = listOf(job) + _computeJobs.value
        return job
    }
}

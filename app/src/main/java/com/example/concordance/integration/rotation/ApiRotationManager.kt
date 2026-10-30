package com.example.concordance.integration.rotation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

enum class ProviderCategory(val label: String) {
    AI_MODEL("Small Local & Cloud AI"),
    LITERATURE("Science Paper & Preprint APIs"),
    BIOCHEM_COMPOUNDS("PubChem & Drug Target APIs"),
    CLINICAL_TRIALS("ClinicalTrials.gov API"),
    VARIANT_EVIDENCE("Curated Variant & Cohort APIs"),
    DATASET_COMPUTE("Kaggle Compute & Datasets"),
    CLOUD_STORAGE("AWS S3 & Cloud Vault")
}

enum class ProviderStatus(val label: String, val colorHex: Long) {
    HEALTHY("Healthy", 0xFF00E676),
    RATE_LIMITED("Rate Limited (429)", 0xFFFFD740),
    DEGRADED("Degraded", 0xFFFF9100),
    OFFLINE("Offline", 0xFFFF5252)
}

/**
 * Licensing tier for a provider. Drives license-aware gating so the app never
 * silently uses a resource outside its permitted scope.
 */
enum class LicenseClass(val label: String) {
    OPEN("Open"),
    ACADEMIC_ONLY("Academic / Research only"),
    PAID("Paid license required")
}

enum class RotationStrategy(val label: String, val description: String) {
    PRIORITY_FAILOVER("Priority Failover", "Primary endpoint with instant failover on 429 or timeout"),
    ROUND_ROBIN("Round-Robin", "Balances requests evenly across registered endpoints"),
    LATENCY_OPTIMIZED("Latency-Optimized", "Routes traffic to the fastest responsive provider")
}

data class ApiProvider(
    val id: String,
    val name: String,
    val category: ProviderCategory,
    val baseUrl: String,
    val status: ProviderStatus = ProviderStatus.HEALTHY,
    val requestCount: Long = 0,
    val successCount: Long = 0,
    val failureCount: Long = 0,
    val avgLatencyMs: Long = 45,
    val lastUsedTimestamp: String = "Just now",
    val isActive: Boolean = true,
    val licenseClass: LicenseClass = LicenseClass.OPEN
)

object ApiRotationManager {

    private val _strategy = MutableStateFlow(RotationStrategy.PRIORITY_FAILOVER)
    val strategy: StateFlow<RotationStrategy> = _strategy.asStateFlow()

    private val initialProviders = listOf(
        ApiProvider(
            id = "gemini_flash",
            name = "Gemini 3.5 Flash",
            category = ProviderCategory.AI_MODEL,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            avgLatencyMs = 320
        ),
        ApiProvider(
            id = "local_micro_model",
            name = "Small Local Micro-Model",
            category = ProviderCategory.AI_MODEL,
            baseUrl = "on-device://statistical-nlp-engine",
            avgLatencyMs = 8
        ),
        ApiProvider(
            id = "pubmed_eutils",
            name = "PubMed / NCBI E-Utilities",
            category = ProviderCategory.LITERATURE,
            baseUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils",
            avgLatencyMs = 210
        ),
        ApiProvider(
            id = "arxiv_api",
            name = "arXiv Preprints (Cornell University)",
            category = ProviderCategory.LITERATURE,
            baseUrl = "https://export.arxiv.org/api/query",
            avgLatencyMs = 290
        ),
        ApiProvider(
            id = "europe_pmc",
            name = "Europe PMC REST API",
            category = ProviderCategory.LITERATURE,
            baseUrl = "https://www.ebi.ac.uk/europepmc/webservices/rest",
            avgLatencyMs = 180
        ),
        ApiProvider(
            id = "semantic_scholar",
            name = "Semantic Scholar Graph API",
            category = ProviderCategory.LITERATURE,
            baseUrl = "https://api.semanticscholar.org/graph/v1",
            avgLatencyMs = 240
        ),
        ApiProvider(
            id = "crossref_api",
            name = "Crossref Metadata API",
            category = ProviderCategory.LITERATURE,
            baseUrl = "https://api.crossref.org/works",
            avgLatencyMs = 195
        ),
        ApiProvider(
            id = "unpaywall_api",
            name = "Unpaywall (OA locations)",
            category = ProviderCategory.LITERATURE,
            baseUrl = "https://api.unpaywall.org/v2",
            avgLatencyMs = 170
        ),
        ApiProvider(
            id = "biorxiv_api",
            name = "bioRxiv / medRxiv Preprints",
            category = ProviderCategory.LITERATURE,
            baseUrl = "https://api.biorxiv.org",
            avgLatencyMs = 230
        ),
        ApiProvider(
            id = "openalex_api",
            name = "OpenAlex Global Research Index",
            category = ProviderCategory.LITERATURE,
            baseUrl = "https://api.openalex.org/works",
            avgLatencyMs = 220
        ),
        ApiProvider(
            id = "pubpeer_api",
            name = "PubPeer Post-Publication Review",
            category = ProviderCategory.LITERATURE,
            baseUrl = "https://pubpeer.com/api/v1",
            avgLatencyMs = 210
        ),
        ApiProvider(
            id = "osf_api",
            name = "Open Science Framework (OSF)",
            category = ProviderCategory.LITERATURE,
            baseUrl = "https://api.osf.io/v2",
            avgLatencyMs = 230
        ),
        ApiProvider(
            id = "figshare_api",
            name = "Figshare",
            category = ProviderCategory.LITERATURE,
            baseUrl = "https://api.figshare.com/v2",
            avgLatencyMs = 235
        ),
        ApiProvider(
            id = "zotero_api",
            name = "Zotero Web API",
            category = ProviderCategory.LITERATURE,
            baseUrl = "https://api.zotero.org",
            avgLatencyMs = 225
        ),
        ApiProvider(
            id = "pubchem_pug",
            name = "NCBI PubChem PUG-REST API",
            category = ProviderCategory.BIOCHEM_COMPOUNDS,
            baseUrl = "https://pubchem.ncbi.nlm.nih.gov/rest/pug",
            avgLatencyMs = 260
        ),
        ApiProvider(
            id = "chembl_api",
            name = "ChEMBL Drug Targets (EBI)",
            category = ProviderCategory.BIOCHEM_COMPOUNDS,
            baseUrl = "https://www.ebi.ac.uk/chembl/api/data",
            avgLatencyMs = 240
        ),
        ApiProvider(
            id = "uniprot_api",
            name = "UniProt Knowledge Base",
            category = ProviderCategory.BIOCHEM_COMPOUNDS,
            baseUrl = "https://rest.uniprot.org",
            avgLatencyMs = 245
        ),
        ApiProvider(
            id = "clinical_trials",
            name = "ClinicalTrials.gov v2 REST API",
            category = ProviderCategory.CLINICAL_TRIALS,
            baseUrl = "https://clinicaltrials.gov/api/v2/studies",
            avgLatencyMs = 275
        ),
        ApiProvider(
            id = "civic_api",
            name = "CIViC Curated Variant Evidence (GraphQL)",
            category = ProviderCategory.VARIANT_EVIDENCE,
            baseUrl = "https://civicdb.org/api/graphql",
            avgLatencyMs = 250
        ),
        ApiProvider(
            id = "cbioportal_api",
            name = "cBioPortal Cancer Genomics REST API",
            category = ProviderCategory.VARIANT_EVIDENCE,
            baseUrl = "https://www.cbioportal.org/api",
            avgLatencyMs = 265
        ),
        ApiProvider(
            id = "gnomad_api",
            name = "gnomAD Variant Frequencies (GraphQL)",
            category = ProviderCategory.VARIANT_EVIDENCE,
            baseUrl = "https://gnomad.broadinstitute.org/api",
            avgLatencyMs = 310
        ),
        ApiProvider(
            id = "gdc_api",
            name = "NCI Genomic Data Commons REST API",
            category = ProviderCategory.VARIANT_EVIDENCE,
            baseUrl = "https://api.gdc.cancer.gov",
            avgLatencyMs = 280
        ),
        ApiProvider(
            id = "oncokb_api",
            name = "OncoKB Oncology Knowledge Base (Token-gated)",
            category = ProviderCategory.VARIANT_EVIDENCE,
            baseUrl = "https://www.oncokb.org/api/v1",
            avgLatencyMs = 260,
            licenseClass = LicenseClass.ACADEMIC_ONLY
        ),
        ApiProvider(
            id = "kaggle_api",
            name = "Kaggle Dataset & Kernel REST API",
            category = ProviderCategory.DATASET_COMPUTE,
            baseUrl = "https://www.kaggle.com/api/v1",
            avgLatencyMs = 410
        ),
        ApiProvider(
            id = "aws_s3_rest",
            name = "AWS S3 & Cloud Compute REST",
            category = ProviderCategory.CLOUD_STORAGE,
            baseUrl = "https://s3.amazonaws.com",
            avgLatencyMs = 150
        ),
        // Free-tier LLM providers (models rotate frequently; name is configurable)
        ApiProvider(
            id = "opencode_llm",
            name = "OpenCode (free/stealth models)",
            category = ProviderCategory.AI_MODEL,
            baseUrl = "https://opencode.ai/api",
            avgLatencyMs = 300,
            licenseClass = LicenseClass.OPEN
        ),
        ApiProvider(
            id = "openrouter_llm",
            name = "OpenRouter (free tier)",
            category = ProviderCategory.AI_MODEL,
            baseUrl = "https://openrouter.ai/api/v1",
            avgLatencyMs = 350,
            licenseClass = LicenseClass.OPEN
        ),
        ApiProvider(
            id = "ollama_local",
            name = "Ollama (local)",
            category = ProviderCategory.AI_MODEL,
            baseUrl = "http://localhost:11434",
            avgLatencyMs = 50,
            licenseClass = LicenseClass.OPEN
        ),
        ApiProvider(
            id = "gemini_free",
            name = "Gemini (free tier)",
            category = ProviderCategory.AI_MODEL,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            avgLatencyMs = 320,
            licenseClass = LicenseClass.OPEN
        ),
        ApiProvider(
            id = "mistral_free",
            name = "Mistral (free tier)",
            category = ProviderCategory.AI_MODEL,
            baseUrl = "https://api.mistral.ai/v1",
            avgLatencyMs = 340,
            licenseClass = LicenseClass.OPEN
        ),
        ApiProvider(
            id = "nvidia_nim",
            name = "NVIDIA NIM (free tier)",
            category = ProviderCategory.AI_MODEL,
            baseUrl = "https://integrate.api.nvidia.com/v1",
            avgLatencyMs = 400,
            licenseClass = LicenseClass.OPEN
        ),
        ApiProvider(
            id = "aws_api_gateway",
            name = "AWS API Gateway (LLM proxy)",
            category = ProviderCategory.AI_MODEL,
            baseUrl = "https://execute-api.us-east-2.amazonaws.com",
            avgLatencyMs = 200,
            licenseClass = LicenseClass.PAID
        )
    )

    private val _providers = MutableStateFlow(initialProviders)
    val providers: StateFlow<List<ApiProvider>> = _providers.asStateFlow()

    private val roundRobinIndices = mutableMapOf<ProviderCategory, AtomicInteger>()

    // Circuit breaker state: consecutive failures and cooldown deadlines.
    // Concurrent maps: these are read and written from multiple coroutines.
    private val consecutiveFailures = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val cooldownUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @Volatile
    private var failureThreshold: Int = 3

    @Volatile
    private var cooldownMs: Long = 60_000L

    fun setFailureThreshold(n: Int) {
        failureThreshold = n.coerceAtLeast(1)
    }

    fun setCooldownMs(ms: Long) {
        cooldownMs = ms.coerceAtLeast(5_000L)
    }

    fun failureThreshold(): Int = failureThreshold
    fun cooldownMs(): Long = cooldownMs

    fun setStrategy(newStrategy: RotationStrategy) {
        _strategy.value = newStrategy
    }

    fun getNextProvider(category: ProviderCategory): ApiProvider {
        val now = System.currentTimeMillis()
        // Recover any providers whose cooldown has expired.
        _providers.value = _providers.value.map { p ->
            val until = cooldownUntil[p.id] ?: 0L
            if (until > 0 && now >= until) {
                cooldownUntil.remove(p.id)
                consecutiveFailures[p.id] = 0
                if (p.status == ProviderStatus.OFFLINE) p.copy(status = ProviderStatus.HEALTHY) else p
            } else p
        }

        val eligible = _providers.value.filter {
            it.category == category && it.isActive && it.status != ProviderStatus.OFFLINE &&
                (cooldownUntil[it.id] ?: 0L) <= now
        }
        if (eligible.isEmpty()) {
            return _providers.value.first { it.category == category }
        }

        return when (_strategy.value) {
            RotationStrategy.ROUND_ROBIN -> {
                val counter = roundRobinIndices.getOrPut(category) { AtomicInteger(0) }
                val idx = (counter.getAndIncrement() % eligible.size).let { if (it < 0) -it else it }
                eligible[idx]
            }
            RotationStrategy.LATENCY_OPTIMIZED -> {
                eligible.minByOrNull { it.avgLatencyMs } ?: eligible.first()
            }
            RotationStrategy.PRIORITY_FAILOVER -> {
                // Return first healthy, else first non-offline
                eligible.firstOrNull { it.status == ProviderStatus.HEALTHY } ?: eligible.first()
            }
        }
    }

    fun recordSuccess(providerId: String, latencyMs: Long) {
        consecutiveFailures[providerId] = 0
        cooldownUntil.remove(providerId)
        _providers.value = _providers.value.map { p ->
            if (p.id == providerId) {
                val newCount = p.requestCount + 1
                val newSuccess = p.successCount + 1
                val newAvg = ((p.avgLatencyMs * p.requestCount) + latencyMs) / newCount
                p.copy(
                    requestCount = newCount,
                    successCount = newSuccess,
                    avgLatencyMs = newAvg,
                    status = ProviderStatus.HEALTHY,
                    lastUsedTimestamp = "Just now"
                )
            } else p
        }
    }

    fun recordFailure(providerId: String, isRateLimit: Boolean) {
        val failures = (consecutiveFailures[providerId] ?: 0) + 1
        consecutiveFailures[providerId] = failures
        val tripped = failures >= failureThreshold
        if (tripped) {
            cooldownUntil[providerId] = System.currentTimeMillis() + cooldownMs
        }
        _providers.value = _providers.value.map { p ->
            if (p.id == providerId) {
                val newCount = p.requestCount + 1
                val newFailure = p.failureCount + 1
                val newStatus = if (tripped) ProviderStatus.OFFLINE
                else if (isRateLimit) ProviderStatus.RATE_LIMITED else ProviderStatus.DEGRADED
                p.copy(
                    requestCount = newCount,
                    failureCount = newFailure,
                    status = newStatus,
                    lastUsedTimestamp = "Just now (Failed)"
                )
            } else p
        }
    }

    fun toggleProviderActive(providerId: String) {
        _providers.value = _providers.value.map { p ->
            if (p.id == providerId) p.copy(isActive = !p.isActive) else p
        }
    }

    fun cooldownRemainingMs(providerId: String): Long {
        val until = cooldownUntil[providerId] ?: 0L
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun resetStats() {
        consecutiveFailures.clear()
        cooldownUntil.clear()
        _providers.value = _providers.value.map {
            it.copy(
                requestCount = 0,
                successCount = 0,
                failureCount = 0,
                status = ProviderStatus.HEALTHY
            )
        }
    }
}

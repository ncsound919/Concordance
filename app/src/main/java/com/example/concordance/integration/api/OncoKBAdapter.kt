package com.example.concordance.integration.api

import com.example.concordance.integration.byok.GateResult
import com.example.concordance.integration.byok.IntegrationGate
import com.example.concordance.integration.byok.KeyStore
import com.example.concordance.integration.byok.LicenseStore
import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONObject

/**
 * OncoKB (Memorial Sloan Kettering) oncology knowledge base.
 *
 * The evidence-levels endpoint is public; gene-level annotations require a
 * free academic API token. License is [LicenseClass.ACADEMIC_ONLY], so the
 * adapter is gated behind both a license acknowledgment and (for gene
 * annotations) a user-provided token. This is the first BYOK-gated provider.
 *
 * Docs: https://www.oncokb.org/apiAccess
 */
object OncoKBAdapter : IntegrationAdapter {

    private const val BASE = "https://www.oncokb.org/api/v1"

    override val descriptor = AdapterDescriptor(
        id = "oncokb_api",
        displayName = "OncoKB Oncology Knowledge Base",
        category = ProviderCategory.VARIANT_EVIDENCE,
        authType = AuthType.TOKEN,
        licenseClass = LicenseClass.ACADEMIC_ONLY,
        rateLimit = RateLimitSpec(requestsPerSecond = 2.0, burst = 2),
        capabilities = setOf(Capability.SEARCH, Capability.FETCH_METADATA),
        docsUrl = "https://www.oncokb.org/apiAccess"
    )

    /** Gate status for the UI: allowed / needs license / needs key. */
    fun gateStatus(keys: KeyStore, licenses: LicenseStore): GateResult =
        IntegrationGate.evaluate(descriptor, keys, licenses)

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        // Gate: license must be acknowledged first.
        // (Key is only required for gene-level annotations; levels are public.)
        return emptyList()
    }

    /**
     * Fetches the public evidence-levels catalog. Always available once the
     * license is acknowledged, regardless of token.
     */
    suspend fun fetchLevels(keys: KeyStore, licenses: LicenseStore): List<NormalizedRecord> {
        if (IntegrationGate.evaluate(descriptor, keys, licenses) !is GateResult.Allowed) return emptyList()
        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = "$BASE/levels",
            queryLabel = "levels",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return emptyList()
        return parseLevels(resp.body, resp.provenance)
    }

    /**
     * Fetches gene-level annotations for [geneSymbol]. Requires a user-provided
     * OncoKB token; returns empty (and a gate result) without one.
     */
    suspend fun fetchGene(
        geneSymbol: String,
        keys: KeyStore,
        licenses: LicenseStore
    ): List<NormalizedRecord> {
        val gate = IntegrationGate.evaluate(descriptor, keys, licenses)
        if (gate !is GateResult.Allowed) return emptyList()
        val token = keys.getKey(descriptor.id) ?: return emptyList()
        if (geneSymbol.isBlank()) return emptyList()

        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = "$BASE/genes/$geneSymbol",
            queryLabel = "gene:$geneSymbol",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass,
            authToken = token
        ) ?: return emptyList()
        return parseGene(resp.body, geneSymbol, resp.provenance)
    }

    internal fun parseLevels(json: String, provenance: Provenance): List<NormalizedRecord> {
        return try {
            val obj = JSONObject(json)
            listOf(
                NormalizedRecord(
                    externalId = "OncoKB:levels",
                    source = "OncoKB",
                    title = "OncoKB evidence levels (${obj.length()} levels)",
                    authors = "OncoKB / MSK",
                    journal = "OncoKB (ACADEMIC_ONLY)",
                    year = null,
                    doi = null,
                    pmid = null,
                    abstractText = obj.keys().asSequence().joinToString("; ") { "$it: ${obj.optString(it)}" },
                    url = "https://www.oncokb.org/levels",
                    citationCount = null,
                    isOpenAccess = false,
                    provenance = provenance
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }

    internal fun parseGene(json: String, geneSymbol: String, provenance: Provenance): List<NormalizedRecord> {
        return try {
            val obj = JSONObject(json)
            listOf(
                NormalizedRecord(
                    externalId = "OncoKB:gene:$geneSymbol",
                    source = "OncoKB",
                    title = "OncoKB gene annotation: $geneSymbol",
                    authors = "OncoKB / MSK",
                    journal = "OncoKB (ACADEMIC_ONLY)",
                    year = null,
                    doi = null,
                    pmid = null,
                    abstractText = obj.optString("oncokbAnnotatedVariants", obj.toString()).take(500),
                    url = "https://www.oncokb.org/gene/$geneSymbol",
                    citationCount = null,
                    isOpenAccess = false,
                    provenance = provenance
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}

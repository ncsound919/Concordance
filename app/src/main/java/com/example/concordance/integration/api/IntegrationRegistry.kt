package com.example.concordance.integration.api

import com.example.concordance.integration.clinicaltrials.ClinicalTrialStudy
import com.example.concordance.integration.byok.KeyStore
import com.example.concordance.integration.byok.LicenseStore
import com.example.concordance.integration.rotation.ProviderCategory

/**
 * Central registration point for live integrations. Managers call into this
 * instead of holding provider-specific clients, so adding a provider means
 * adding one adapter and one line here.
 */
object IntegrationRegistry {

    val adapters: List<IntegrationAdapter> = listOf(
        EuropePmcAdapter,
        OpenAlexAdapter,
        ClinicalTrialsGovAdapter,
        CivicAdapter,
        CBioPortalAdapter,
        OncoKBAdapter
    )

    fun descriptor(id: String): AdapterDescriptor? =
        adapters.firstOrNull { it.descriptor.id == id }?.descriptor

    /** Fans a query out across every literature adapter and de-duplicates by DOI/PMID/title. */
    suspend fun searchLiterature(query: IntegrationQuery): List<NormalizedRecord> {
        val out = mutableListOf<NormalizedRecord>()
        for (adapter in adapters) {
            val d = adapter.descriptor
            if (d.category != ProviderCategory.LITERATURE) continue
            if (Capability.SEARCH !in d.capabilities) continue
            out += runCatching { adapter.search(query) }.getOrDefault(emptyList())
        }
        return out.distinctBy { (it.doi ?: it.pmid ?: it.title).lowercase() }
    }

    /** Live ClinicalTrials.gov registry search. */
    suspend fun searchTrials(query: IntegrationQuery): List<ClinicalTrialStudy> =
        runCatching { ClinicalTrialsGovAdapter.searchStudies(query) }.getOrDefault(emptyList())

    /** Curated variant evidence from CIViC for a gene symbol. */
    suspend fun searchCuratedEvidence(geneSymbol: String, limit: Int = 25): List<CuratedVariantEvidence> =
        runCatching { CivicAdapter.searchEvidence(geneSymbol, limit) }.getOrDefault(emptyList())

    /** Public cancer-genomics cohorts from cBioPortal matching a keyword. */
    suspend fun searchCohorts(keyword: String, limit: Int = 25): List<CohortStudy> =
        runCatching { CBioPortalAdapter.searchStudies(keyword, limit) }.getOrDefault(emptyList())

    /** Somatic mutations for a gene within a cBioPortal study's mutation profile. */
    suspend fun searchMutations(studyId: String, geneSymbol: String, limit: Int = 50): List<SomaticMutation> =
        runCatching { CBioPortalAdapter.searchMutations(studyId, geneSymbol, limit) }.getOrDefault(emptyList())

    /** OncoKB evidence levels (public). Requires license acknowledgment. */
    suspend fun searchOncoKBLevels(keys: KeyStore, licenses: LicenseStore): List<NormalizedRecord> =
        runCatching { OncoKBAdapter.fetchLevels(keys, licenses) }.getOrDefault(emptyList())

    /** OncoKB gene annotation (requires a user-provided token). */
    suspend fun searchOncoKBGene(geneSymbol: String, keys: KeyStore, licenses: LicenseStore): List<NormalizedRecord> =
        runCatching { OncoKBAdapter.fetchGene(geneSymbol, keys, licenses) }.getOrDefault(emptyList())
}

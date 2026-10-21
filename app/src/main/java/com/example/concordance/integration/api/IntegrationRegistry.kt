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
        OncoKBAdapter,
        SemanticScholarAdapter,
        EUtilsAdapter,
        BiorxivAdapter,
        ChemblAdapter,
        UniProtAdapter,
        GdcAdapter,
        PubPeerAdapter,
        OsfAdapter,
        FigshareAdapter,
        ZoteroAdapter
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

    /** Wave-1 (2026-10) additions — see INTEGRATIONS_PLAN.md. */

    /** Papers citing a source paper on Semantic Scholar (supporting/contradicting context). */
    suspend fun fetchCitingPapers(paperIdOrDoi: String, limit: Int = 25): List<NormalizedRecord> =
        runCatching { SemanticScholarAdapter.fetchCitations(paperIdOrDoi, limit) }.getOrDefault(emptyList())

    /** Best lawful open-access location for a source DOI (Unpaywall). */
    suspend fun lookupOpenAccess(doi: String, contactEmail: String? = null): UnpaywallRecord? =
        runCatching { UnpaywallAdapter.fetchOa(doi, contactEmail) }.getOrNull()

    /** Raw JATS XML of an open-access PMC article (Vectorize chunk input). */
    suspend fun fetchPmcFullText(pmcId: String, contactEmail: String? = null): String? =
        runCatching { EUtilsAdapter.fetchPmcXml(pmcId, contactEmail) }.getOrNull()

    /** Version history for a preprint DOI, newest first (re-adjudication trigger). */
    suspend fun fetchPreprintVersions(doi: String): List<PreprintVersion> =
        runCatching { BiorxivAdapter.fetchVersionHistory(doi) }.getOrDefault(emptyList())

    /** ChEMBL molecules matching a drug name. */
    suspend fun searchMolecules(drugTerm: String, limit: Int = 25): List<MoleculeRecord> =
        runCatching { ChemblAdapter.fetchMolecules(drugTerm, limit)?.first ?: emptyList() }
            .getOrDefault(emptyList())

    /** Mechanisms of action for a drug (mechanism-claim evidence). */
    suspend fun drugMechanisms(chemblId: String): List<DrugMechanism> =
        runCatching { ChemblAdapter.fetchMechanisms(chemblId) }.getOrDefault(emptyList())

    /** UniProt protein entries matching a gene/protein query. */
    suspend fun searchProteins(term: String, limit: Int = 25): List<ProteinRecord> =
        runCatching { UniProtAdapter.fetchProteins(term, limit)?.first ?: emptyList() }
            .getOrDefault(emptyList())

    /** Population allele frequencies for a variant (common-variant guard). */
    suspend fun variantFrequency(variantId: String): GnomadVariant? =
        runCatching { GnomadAdapter.fetchVariant(variantId) }.getOrNull()

    /** GDC projects (cohorts) matching a keyword. */
    suspend fun searchGdcProjects(keyword: String, limit: Int = 25): List<GdcProject> =
        runCatching { GdcAdapter.searchProjects(keyword, limit) }.getOrDefault(emptyList())

    /** Wave-2 (2026-10) additions — see INTEGRATIONS_PLAN.md. */

    /** PubPeer comments for a paper identified by DOI. */
    suspend fun fetchPubPeerByDoi(doi: String, apiKey: String? = null): List<PubPeerPublication> =
        runCatching { PubPeerAdapter.fetchByDoi(doi, apiKey) }.getOrDefault(emptyList())

    /** PubPeer comments for a paper identified by PMID. */
    suspend fun fetchPubPeerByPmid(pmid: String, apiKey: String? = null): List<PubPeerPublication> =
        runCatching { PubPeerAdapter.fetchByPmid(pmid, apiKey) }.getOrDefault(emptyList())

    /** OSF projects matching a keyword. */
    suspend fun searchOsfNodes(term: String, limit: Int = 25, authToken: String? = null): List<OsfNode> =
        runCatching { OsfAdapter.searchNodes(term, limit, authToken)?.first }.getOrDefault(emptyList())

    /** Files attached to an OSF node (artifact provenance). */
    suspend fun osfFiles(nodeId: String, authToken: String? = null): List<OsfFile> =
        runCatching { OsfAdapter.listFiles(nodeId, authToken) }.getOrDefault(emptyList())

    /** Create an OSF project (BYOK token). */
    suspend fun createOsfProject(title: String, description: String?, authToken: String): String? =
        runCatching { OsfAdapter.createProject(title, description, authToken) }.getOrNull()

    /** Figshare articles matching a keyword. */
    suspend fun searchFigshare(term: String, limit: Int = 25, authToken: String? = null): List<FigshareArticle> =
        runCatching { FigshareAdapter.searchArticles(term, limit, authToken)?.first }.getOrDefault(emptyList())

    /** Single Figshare article by id. */
    suspend fun fetchFigshareArticle(id: Long, authToken: String? = null): FigshareArticle? =
        runCatching { FigshareAdapter.fetchArticle(id, authToken) }.getOrNull()

    /** Create a Figshare article (BYOK token). */
    suspend fun createFigshareArticle(title: String, description: String?, tags: List<String>, authToken: String): Long? =
        runCatching { FigshareAdapter.createArticle(title, description, tags, authToken) }.getOrNull()

    /** Zotero library search by keyword (BYOK key + user id). */
    suspend fun searchZoteroLibrary(userId: String, query: String, apiKey: String, limit: Int = 25): List<ZoteroItem> =
        runCatching { ZoteroAdapter.searchLibrary(userId, query, apiKey, limit) }.getOrDefault(emptyList())

    /** Zotero group library items (optionally scoped to a collection). */
    suspend fun importZoteroGroup(groupId: String, collectionKey: String?, apiKey: String, limit: Int = 50): List<ZoteroItem> =
        runCatching { ZoteroAdapter.fetchGroupItems(groupId, collectionKey, apiKey, limit) }.getOrDefault(emptyList())

    /** Export items to a Zotero library (BYOK key). */
    suspend fun exportToZotero(userId: String, items: List<JSONObject>, apiKey: String): Boolean =
        runCatching { ZoteroAdapter.createItems(userId, items, apiKey) }.getOrDefault(false)
}

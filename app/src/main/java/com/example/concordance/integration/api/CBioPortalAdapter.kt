package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONArray
import java.net.URLEncoder

/**
 * cBioPortal REST API. Keyless and CORS-enabled for public studies.
 *
 * The adapter performs a study (cohort) search by keyword, which is the entry
 * point for locating breast / CNS-metastasis cohorts to run paired analyses on.
 * Docs: https://www.cbioportal.org/web/api
 */
object CBioPortalAdapter : IntegrationAdapter {

    private const val ENDPOINT = "https://www.cbioportal.org/api/studies"

    override val descriptor = AdapterDescriptor(
        id = "cbioportal_api",
        displayName = "cBioPortal Cancer Genomics",
        category = ProviderCategory.VARIANT_EVIDENCE,
        authType = AuthType.NONE,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 2.0, burst = 2),
        capabilities = setOf(
            Capability.SEARCH,
            Capability.FETCH_METADATA,
            Capability.FETCH_COUNTS
        ),
        docsUrl = "https://www.cbioportal.org/web/api"
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        val (studies, provenance) = fetch(query.text, query.pageSize)
        if (provenance == null) return emptyList()
        return studies.map { it.toNormalizedRecord(provenance) }
    }

    /** Public cohort studies matching a keyword (e.g. "breast", "brain metastasis"). */
    suspend fun searchStudies(keyword: String, limit: Int = 25): List<CohortStudy> =
        fetch(keyword, limit).first

    private suspend fun fetch(keyword: String, limit: Int): Pair<List<CohortStudy>, Provenance?> {
        val term = keyword.trim()
        if (term.isBlank()) return emptyList<CohortStudy>() to null

        val encoded = URLEncoder.encode(term, "UTF-8")
        val pageSize = limit.coerceIn(1, 100)
        val url = "$ENDPOINT?keyword=$encoded&projection=SUMMARY&pageSize=$pageSize"

        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = term,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return emptyList<CohortStudy>() to null

        return parseStudies(resp.body) to resp.provenance
    }

    /** Somatic mutation calls for a gene within a cBioPortal study's mutation profile. */
    suspend fun searchMutations(studyId: String, geneSymbol: String, limit: Int = 50): List<SomaticMutation> =
        fetchMutations(studyId, geneSymbol, limit).first

    private suspend fun fetchMutations(
        studyId: String,
        geneSymbol: String,
        limit: Int
    ): Pair<List<SomaticMutation>, Provenance?> {
        val study = studyId.trim()
        val gene = geneSymbol.trim()
        if (study.isBlank() || gene.isBlank()) return emptyList<SomaticMutation>() to null

        // 1. Resolve the gene symbol to an Entrez id.
        val geneEncoded = URLEncoder.encode(gene, "UTF-8")
        val geneUrl = "https://www.cbioportal.org/api/genes?keyword=$geneEncoded&projection=SUMMARY"
        val geneResp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = geneUrl,
            queryLabel = "gene:$gene",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return emptyList<SomaticMutation>() to null
        val entrezId = try {
            org.json.JSONArray(geneResp.body).optJSONObject(0)?.optInt("entrezGeneId", -1) ?: -1
        } catch (e: Exception) { -1 }
        if (entrezId <= 0) return emptyList<SomaticMutation>() to null

        // 2. Find the study's mutation molecular profile.
        val profilesUrl = "https://www.cbioportal.org/api/studies/$study/molecular-profiles"
        val profilesResp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = profilesUrl,
            queryLabel = "profiles:$study",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return emptyList<SomaticMutation>() to null
        val profileId = try {
            val arr = org.json.JSONArray(profilesResp.body)
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .firstOrNull { it.optString("molecularProfileId").contains("mutations", ignoreCase = true) }
                ?.optString("molecularProfileId")
        } catch (e: Exception) { null }
        if (profileId.isNullOrBlank()) return emptyList<SomaticMutation>() to null

        // 3. Fetch mutations for that gene across the profile.
        val pageSize = limit.coerceIn(1, 500)
        val url = "https://www.cbioportal.org/api/mutations/fetch?pageSize=$pageSize"
        val body = org.json.JSONObject()
            .put("molecularProfileIds", org.json.JSONArray().put(profileId))
            .put("entrezGeneIds", org.json.JSONArray().put(entrezId))
            .toString()
        val resp = HttpJsonClient.postJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = "$study:$gene",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass,
            jsonBody = body
        ) ?: return emptyList<SomaticMutation>() to null

        return parseMutations(resp.body, study, gene, profileId) to resp.provenance
    }

    /**
     * Parses a cBioPortal `/mutations/fetch` response.
     * Kept internal so it can be unit-tested without a network call.
     */
    internal fun parseMutations(json: String, studyId: String, geneSymbol: String, profileId: String): List<SomaticMutation> {
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                SomaticMutation(
                    studyId = o.optString("studyId").ifBlank { studyId },
                    sampleId = o.optString("sampleId"),
                    patientId = o.optString("patientId"),
                    entrezGeneId = o.optInt("entrezGeneId", -1),
                    geneSymbol = geneSymbol,
                    proteinChange = o.optString("proteinChange"),
                    mutationType = o.optString("mutationType"),
                    chr = o.optString("chr").ifBlank { null },
                    startPosition = o.optInt("startPosition", -1).takeIf { it >= 0 },
                    endPosition = o.optInt("endPosition", -1).takeIf { it >= 0 },
                    referenceAllele = o.optString("referenceAllele").ifBlank { null },
                    variantAllele = o.optString("variantAllele").ifBlank { null },
                    tumorAltCount = o.optInt("tumorAltCount", -1).takeIf { it >= 0 },
                    tumorRefCount = o.optInt("tumorRefCount", -1).takeIf { it >= 0 },
                    mutationStatus = o.optString("mutationStatus").ifBlank { null },
                    validationStatus = o.optString("validationStatus").ifBlank { null },
                    molecularProfileId = o.optString("molecularProfileId").ifBlank { profileId }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    internal fun parseStudies(json: String): List<CohortStudy> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val studyId = o.optString("studyId")
                if (studyId.isBlank()) return@mapNotNull null
                CohortStudy(
                    studyId = studyId,
                    name = o.optString("name"),
                    cancerType = o.optString("cancerTypeId").ifBlank { null },
                    description = o.optString("description"),
                    sampleCount = o.optInt("allSampleCount", -1).takeIf { it >= 0 },
                    pmid = o.optString("pmid").ifBlank { null },
                    citation = o.optString("citation").ifBlank { null },
                    referenceGenome = o.optString("referenceGenome").ifBlank { null },
                    url = "https://www.cbioportal.org/study/summary?id=$studyId"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONObject
import java.net.URLEncoder

/**
 * NCI Genomic Data Commons REST API — project (cohort) search and case counts.
 * Keyless. Complements cBioPortal with primary GDC data for cohort-level
 * claims. Docs: https://docs.gdc.cancer.gov/API/Viewer
 */
data class GdcProject(
    val projectId: String,
    val name: String,
    val diseaseType: String?,
    val primarySites: List<String>,
    val caseCount: Int?
)

object GdcAdapter : IntegrationAdapter {

    private const val ENDPOINT = "https://api.gdc.cancer.gov/v0/projects"

    override val descriptor = AdapterDescriptor(
        id = "gdc_api",
        displayName = "NCI Genomic Data Commons",
        category = ProviderCategory.VARIANT_EVIDENCE,
        authType = AuthType.NONE,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 2.0, burst = 4),
        capabilities = setOf(Capability.SEARCH, Capability.FETCH_METADATA, Capability.FETCH_COUNTS),
        docsUrl = "https://docs.gdc.cancer.gov/API/Viewer"
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        val (projects, prov) = fetch(query.text, query.pageSize) ?: return emptyList()
        return projects.map { p ->
            NormalizedRecord(
                externalId = "GDC:${p.projectId}",
                source = "NCI GDC",
                title = p.name.ifBlank { p.projectId },
                authors = "NCI Genomic Data Commons",
                journal = "GDC project (${p.diseaseType ?: "unspecified"})",
                year = null,
                doi = null,
                pmid = null,
                abstractText = p.primarySites.joinToString(", ").ifBlank { "" },
                url = "https://portal.gdc.cancer.gov/projects/${p.projectId}",
                citationCount = p.caseCount,
                isOpenAccess = true,
                provenance = prov
            )
        }
    }

    /**
     * GDC projects matching [keyword] against name and disease_type
     * (e.g. "breast", "brain"). Uses a server-side `contains` filter.
     */
    suspend fun searchProjects(keyword: String, limit: Int = 25): List<GdcProject> =
        fetch(keyword, limit)?.first ?: emptyList()

    private suspend fun fetch(keyword: String, limit: Int): Pair<List<GdcProject>, Provenance>? {
        val term = keyword.trim()
        if (term.isBlank()) return null
        val pageSize = limit.coerceIn(1, 100)
        val filters = JSONObject()
            .put(
                "op", "and"
            )
            .put(
                "content", org.json.JSONArray()
                    .put(containsFilter("name", term))
                    .put(containsFilter("disease_type", term))
            )
        val encodedFilters = URLEncoder.encode(filters.toString(), "UTF-8")
        val url = "$ENDPOINT?size=$pageSize&fields=project_id,name,disease_type,primary_site,summary.case_count&filters=$encodedFilters"

        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = term,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return null
        return parseProjects(resp.body) to resp.provenance
    }

    private fun containsFilter(field: String, value: String): JSONObject = JSONObject()
        .put("op", "contains")
        .put("content", JSONObject().put("field", field).put("value", listOf(value)))

    /** GDC REST shape: {"data": {"hits": [ {project_id, name, ...} ]}}. */
    internal fun parseProjects(json: String): List<GdcProject> = try {
        val hits = JSONObject(json).optJSONObject("data")?.optJSONArray("hits")
            ?: return emptyList()
        (0 until hits.length()).mapNotNull { i ->
            val o = hits.optJSONObject(i) ?: return@mapNotNull null
            val projectId = o.optString("project_id")
            if (projectId.isBlank()) return@mapNotNull null
            val sites = o.optJSONArray("primary_site")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).ifBlank { null } }
            }.orEmpty()
            GdcProject(
                projectId = projectId,
                name = o.optString("name"),
                diseaseType = firstOrNullString(o, "disease_type"),
                primarySites = sites,
                caseCount = o.optJSONObject("summary")?.optInt("case_count", -1)?.takeIf { it >= 0 }
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** GDC returns multi-valued fields as arrays; single-valued as strings. */
    private fun firstOrNullString(o: JSONObject, field: String): String? {
        val arr = o.optJSONArray(field)
        if (arr != null) return arr.optString(0).ifBlank { null }
        return o.optString(field).ifBlank { null }
    }
}

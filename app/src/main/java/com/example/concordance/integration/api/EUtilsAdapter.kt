package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONObject
import java.net.URLEncoder

/**
 * NCBI E-utilities — PubMed metadata search plus PMC open-access full text.
 * Shares the existing `pubmed_eutils` rotation entry and its rate bucket,
 * which is correct: NCBI's limits apply per client across all E-utilities
 * (3 rps keyless; a BYOK API key would lift this to 10 rps later).
 * Docs: https://www.ncbi.nlm.nih.gov/books/NBK25497/
 *
 * Two flows:
 *  1. [search] — esearch + esummary on PubMed/PMC for literature records.
 *  2. [fetchPmcXml] — efetch of a PMC article's full JATS XML, the input for
 *     section-level text chunks sent to the edge Vectorize pipeline.
 */
object EUtilsAdapter : IntegrationAdapter {

    private const val BASE = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"

    override val descriptor = AdapterDescriptor(
        id = "pubmed_eutils",
        displayName = "PubMed / PMC E-Utilities",
        category = ProviderCategory.LITERATURE,
        authType = AuthType.NONE,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 3.0, burst = 3),
        capabilities = setOf(
            Capability.SEARCH,
            Capability.FETCH_METADATA,
            Capability.FULLTEXT_LINK
        ),
        docsUrl = "https://www.ncbi.nlm.nih.gov/books/NBK25497/",
        requiresEmail = true
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        val term = query.text.trim()
        if (term.isBlank()) return emptyList()
        val email = URLEncoder.encode(query.contactEmail ?: "research@concordance.app", "UTF-8")
        val encoded = URLEncoder.encode(term, "UTF-8")
        val pageSize = query.pageSize.coerceIn(1, 100)

        val esearchUrl = "$BASE/esearch.fcgi?db=pubmed&term=$encoded&retmode=json&retmax=$pageSize&tool=concordance&email=$email"
        val esearchResp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = esearchUrl,
            queryLabel = term,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return emptyList()
        val ids = parseSearchIds(esearchResp.body)
        if (ids.isEmpty()) return emptyList()

        val idList = ids.joinToString(",")
        val esummaryUrl = "$BASE/esummary.fcgi?db=pubmed&id=$idList&retmode=json&tool=concordance&email=$email"
        val esummaryResp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = esummaryUrl,
            queryLabel = "esummary:$idList",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return emptyList()
        return parseSummaries(esummaryResp.body, esummaryResp.provenance)
    }

    /** Raw JATS XML of an open-access PMC article (pmcId like "PMC1234567" or bare digits). */
    suspend fun fetchPmcXml(pmcId: String, contactEmail: String?): String? {
        val id = pmcId.trim().removePrefix("PMC").ifBlank { return null }
        val email = URLEncoder.encode(contactEmail ?: "research@concordance.app", "UTF-8")
        val url = "$BASE/efetch.fcgi?db=pmc&id=$id&retmode=xml&tool=concordance&email=$email"
        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = "efetch:PMC$id",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return null
        return if (resp.status in 200..299 && resp.body.contains('<')) resp.body else null
    }

    internal fun parseSearchIds(json: String): List<String> = try {
        val result = JSONObject(json).optJSONObject("esearchresult") ?: return emptyList()
        val list = result.optJSONArray("idlist") ?: return emptyList()
        (0 until list.length()).mapNotNull { list.optString(it).ifBlank { null } }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * PubMed esummary: {"result": {"uids": [...], "<pmid>": {title, authors, pubdate, ...}}}
     * Article IDs (pmcid/doi) live in the `articleids` array.
     */
    internal fun parseSummaries(json: String, prov: Provenance): List<NormalizedRecord> = try {
        val result = JSONObject(json).optJSONObject("result") ?: return emptyList()
        val uids = result.optJSONArray("uids") ?: return emptyList()
        (0 until uids.length()).mapNotNull { i ->
            val pmid = uids.optString(i).ifBlank { return@mapNotNull null }
            val o = result.optJSONObject(pmid) ?: return@mapNotNull null
            val ids = o.optJSONArray("articleids")
            var doi: String? = null
            var pmcid: String? = null
            if (ids != null) {
                for (j in 0 until ids.length()) {
                    val entry = ids.optJSONObject(j) ?: continue
                    when (entry.optString("idtype")) {
                        "doi" -> doi = entry.optString("value").ifBlank { null }
                        "pmcid" -> pmcid = entry.optString("value").ifBlank { null }
                    }
                }
            }
            val authors = o.optJSONArray("authors")?.let { arr ->
                val names = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
                when {
                    names.isEmpty() -> ""
                    names.size <= 4 -> names.joinToString(", ")
                    else -> names.take(3).joinToString(", ") + " et al."
                }
            }.orEmpty()
            NormalizedRecord(
                externalId = "PubMed:$pmid",
                source = "PubMed",
                title = o.optString("title"),
                authors = authors,
                journal = o.optString("fulljournalname").ifBlank { o.optString("source") },
                year = o.optString("pubdate").takeIf { it.isNotBlank() }?.take(4)?.toIntOrNull(),
                doi = doi,
                pmid = pmid,
                abstractText = "",
                url = pmcid?.let { "https://pmc.ncbi.nlm.nih.gov/articles/$it/" } ?: "https://pubmed.ncbi.nlm.nih.gov/$pmid/",
                citationCount = null,
                isOpenAccess = pmcid != null,
                provenance = prov
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

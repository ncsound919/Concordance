package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Semantic Scholar Graph API. Keyless against the shared anonymous pool
 * (documented as ~1 request/second, globally shared — hence a tight bucket).
 * A user-provided key can lift this later without changing the descriptor.
 *
 * Beyond plain literature search this adapter exposes the citation graph
 * (`citations` endpoint), which adjudication uses to surface supporting and
 * contradicting follow-up literature for a claim's source paper.
 * Docs: https://api.semanticscholar.org/api-docs/graph
 */
object SemanticScholarAdapter : IntegrationAdapter {

    private const val BASE = "https://api.semanticscholar.org/graph/v1"

    private val SEARCH_FIELDS = listOf(
        "title", "authors", "year", "abstract", "externalIds", "venue",
        "citationCount", "isOpenAccess", "openAccessPdf"
    ).joinToString(",")

    override val descriptor = AdapterDescriptor(
        id = "semantic_scholar",
        displayName = "Semantic Scholar (S2ORC)",
        category = ProviderCategory.LITERATURE,
        authType = AuthType.NONE,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 1.0, burst = 1),
        capabilities = setOf(
            Capability.SEARCH,
            Capability.FETCH_METADATA,
            Capability.FETCH_COUNTS
        ),
        docsUrl = "https://api.semanticscholar.org/api-docs/graph"
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        val term = query.text.trim()
        if (term.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(term, "UTF-8")
        val pageSize = query.pageSize.coerceIn(1, 100)
        val offset = query.page * pageSize
        val url = "$BASE/paper/search?query=$encoded&fields=$SEARCH_FIELDS&limit=$pageSize&offset=$offset"

        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = term,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return emptyList()
        return parseSearch(resp.body, resp.provenance)
    }

    /**
     * Follow-up literature that cites the paper identified by [paperIdOrDoi]
     * (a bare S2 paper id, or a "DOI:10.xxxx/..." form). Powers the
     * supporting/contradicting context shown during adjudication.
     */
    suspend fun fetchCitations(paperIdOrDoi: String, limit: Int = 25): List<NormalizedRecord> {
        val id = paperIdOrDoi.trim()
        if (id.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(id, "UTF-8")
        val pageSize = limit.coerceIn(1, 100)
        val url = "$BASE/paper/$encoded/citations?fields=$SEARCH_FIELDS&limit=$pageSize"

        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = "citations:$id",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return emptyList()
        return parseCitations(resp.body, resp.provenance)
    }

    /** Resolves a DOI into the S2 canonical paper id ("DOI:..." accepted natively). */
    internal fun doiToPaperId(doi: String): String =
        doi.removePrefix("doi:").removePrefix("DOI:").let { "DOI:$it" }

    internal fun parseSearch(json: String, prov: Provenance): List<NormalizedRecord> = try {
        val arr = JSONObject(json).optJSONArray("data") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            paperToRecord(arr.optJSONObject(i) ?: return@mapNotNull null, prov)
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** S2 nests each citation as {"citingPaper": {...}} inside `data`. */
    internal fun parseCitations(json: String, prov: Provenance): List<NormalizedRecord> = try {
        val arr = JSONObject(json).optJSONArray("data") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val paper = arr.optJSONObject(i)?.optJSONObject("citingPaper")
                ?: return@mapNotNull null
            paperToRecord(paper, prov)
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun paperToRecord(o: JSONObject, prov: Provenance): NormalizedRecord? {
        val paperId = o.optString("paperId")
        if (paperId.isBlank()) return null
        val ids = o.optJSONObject("externalIds")
        val doi = ids?.optString("DOI")?.ifBlank { null }
        val pmid = ids?.optString("PubMed")?.ifBlank { null }
        val authors = o.optJSONArray("authors")?.let { arr ->
            val names = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
            when {
                names.isEmpty() -> ""
                names.size <= 4 -> names.joinToString(", ")
                else -> names.take(3).joinToString(", ") + " et al."
            }
        }.orEmpty()
        return NormalizedRecord(
            externalId = "S2:$paperId",
            source = "Semantic Scholar",
            title = o.optString("title"),
            authors = authors,
            journal = o.optString("venue"),
            year = o.optInt("year", -1).takeIf { it > 0 },
            doi = doi,
            pmid = pmid,
            abstractText = o.optString("abstract"),
            url = if (doi != null) "https://doi.org/$doi" else "https://www.semanticscholar.org/paper/$paperId",
            citationCount = o.optInt("citationCount", -1).takeIf { it >= 0 },
            isOpenAccess = o.optBoolean("isOpenAccess", false),
            provenance = prov
        )
    }
}

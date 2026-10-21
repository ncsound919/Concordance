package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * PubPeer — post-publication peer review. Lookup is by DOI or PMID (the API
 * has no free-text search), so [search] only fires when the query looks like
 * one of those. Existing comments are auto-attached as challenge seed material
 * at claim creation time. Docs: https://pubpeer.com/api
 */
data class PubPeerComment(
    val id: String,
    val author: String,
    val text: String,
    val createdAt: String,
    val type: String?
)

data class PubPeerPublication(
    val id: String,
    val title: String,
    val doi: String?,
    val pmid: String?,
    val comments: List<PubPeerComment>
)

object PubPeerAdapter : IntegrationAdapter {

    private const val BASE = "https://pubpeer.com/api/v1"

    override val descriptor = AdapterDescriptor(
        id = "pubpeer_api",
        displayName = "PubPeer (post-pub review)",
        category = ProviderCategory.LITERATURE,
        authType = AuthType.API_KEY,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 1.0, burst = 2),
        capabilities = setOf(Capability.SEARCH, Capability.FETCH_METADATA),
        docsUrl = "https://pubpeer.com/api"
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        val term = query.text.trim()
        if (term.isBlank()) return emptyList()
        val publications = if (UnpaywallAdapter.looksLikeDoi(term)) {
            fetchByDoi(term, null)
        } else if (term.all { it.isDigit() }) {
            fetchByPmid(term, null)
        } else emptyList()
        return publications.map { it.toNormalizedRecord() }
    }

    /** Comments on a paper identified by DOI (BYOK key lifts the rate limit). */
    suspend fun fetchByDoi(doi: String, apiKey: String?): List<PubPeerPublication> {
        val d = UnpaywallAdapter.normalizeDoi(doi)
        if (!UnpaywallAdapter.looksLikeDoi(d)) return emptyList()
        val encoded = URLEncoder.encode(d, "UTF-8")
        val key = apiKey?.trim()?.takeIf { it.isNotBlank() }?.let { "&api_key=$it" }.orEmpty()
        val url = "$BASE/publications?doi=$encoded$key"
        return fetchPublications(url, d)
    }

    /** Comments on a paper identified by PMID. */
    suspend fun fetchByPmid(pmid: String, apiKey: String?): List<PubPeerPublication> {
        val p = pmid.trim()
        if (p.isBlank() || !p.all { it.isDigit() }) return emptyList()
        val key = apiKey?.trim()?.takeIf { it.isNotBlank() }?.let { "&api_key=$it" }.orEmpty()
        val url = "$BASE/publications?pmids=$p$key"
        return fetchPublications(url, p)
    }

    private suspend fun fetchPublications(url: String, queryLabel: String): List<PubPeerPublication> {
        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = queryLabel,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return emptyList()
        return parsePublications(resp.body)
    }

    internal fun parsePublications(json: String): List<PubPeerPublication> = try {
        val arr = JSONObject(json).optJSONArray("publications") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            PubPeerPublication(
                id = o.optString("id"),
                title = o.optString("title"),
                doi = o.optString("doi").ifBlank { null },
                pmid = o.optString("pmid").ifBlank { null },
                comments = parseComments(o.optJSONArray("comments"))
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseComments(arr: JSONArray?): List<PubPeerComment> = try {
        if (arr == null) emptyList()
        else (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONObject(i) ?: return@mapNotNull null
            PubPeerComment(
                id = c.optString("id"),
                author = c.optString("author_name").ifBlank { c.optString("author") },
                text = c.optString("text"),
                createdAt = c.optString("created_at"),
                type = c.optString("comment_type").ifBlank { null }
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun PubPeerPublication.toNormalizedRecord(): NormalizedRecord = NormalizedRecord(
        externalId = "PubPeer:${id.ifBlank { doi ?: pmid ?: title }}",
        source = "PubPeer",
        title = title.ifBlank { "PubPeer publication" },
        authors = "",
        journal = "PubPeer",
        year = null,
        doi = doi,
        pmid = pmid,
        abstractText = comments.joinToString("\n\n") { "[${it.author}] ${it.text}" },
        url = if (doi != null) "https://pubpeer.com/publications/$doi" else null,
        citationCount = null,
        isOpenAccess = true,
        provenance = Provenance(
            providerId = descriptor.id,
            endpoint = "$BASE/publications",
            query = doi ?: pmid ?: title,
            httpStatus = 200,
            retrievedUtc = ProvenanceRecorder.nowUtc(),
            responseSha256 = ProvenanceRecorder.sha256(title + comments.size),
            licenseClass = descriptor.licenseClass,
            elapsedMs = 0
        )
    )
}

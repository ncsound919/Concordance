package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONObject
import java.net.URLEncoder

/**
 * bioRxiv / medRxiv detail + version-history API. Keyless.
 *
 * The deep feature is version tracking: a preprint DOI resolves to its full
 * `collection` (every published version), so a claim can be pinned to the exact
 * version it adjudicates — and a newer version appearing is the trigger for
 * re-adjudication. Docs: https://api.biorxiv.org/
 *
 * Text search is not supported by this API, so no SEARCH capability; the
 * literature fan-out never sends keyword queries here.
 */
data class PreprintVersion(
    val server: String,
    val doi: String,
    val version: Int,
    val title: String,
    val authors: String,
    val date: String,
    val category: String?,
    val publishedJournal: String?,
    val status: String
)

object BiorxivAdapter : IntegrationAdapter {

    private const val BASE = "https://api.biorxiv.org"

    override val descriptor = AdapterDescriptor(
        id = "biorxiv_api",
        displayName = "bioRxiv / medRxiv",
        category = ProviderCategory.LITERATURE,
        authType = AuthType.NONE,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 1.0, burst = 2),
        capabilities = setOf(Capability.FETCH_METADATA),
        docsUrl = "https://api.biorxiv.org/"
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        val doi = query.text.trim()
        if (!UnpaywallAdapter.looksLikeDoi(doi)) return emptyList()
        val rec = fetchLatest(doi, query.contactEmail) ?: return emptyList()
        return listOf(rec)
    }

    /**
     * Latest version of a preprint DOI as a literature record.
     * Tries bioRxiv first, then medRxiv.
     */
    suspend fun fetchLatest(doiOrUrl: String, contactEmail: String?): NormalizedRecord? {
        val versions = fetchVersionHistory(doiOrUrl)
        val latest = versions.firstOrNull() ?: return null
        // A dedicated provenance for the record: the version fetch it came from.
        val prov = Provenance(
            providerId = descriptor.id,
            endpoint = "$BASE/details/${latest.server}/${latest.doi}",
            query = latest.doi,
            httpStatus = 200,
            retrievedUtc = ProvenanceRecorder.nowUtc(),
            responseSha256 = ProvenanceRecorder.sha256(latest.title + latest.date),
            licenseClass = descriptor.licenseClass,
            elapsedMs = 0
        )
        return NormalizedRecord(
            externalId = "${latest.server}:${latest.doi}:v${latest.version}",
            source = if (latest.server == "medrxiv") "medRxiv" else "bioRxiv",
            title = latest.title,
            authors = latest.authors,
            journal = latest.publishedJournal ?: "${if (latest.server == "medrxiv") "medRxiv" else "bioRxiv"} preprint (${latest.date})",
            year = latest.date.take(4).toIntOrNull(),
            doi = latest.doi,
            pmid = null,
            abstractText = "",
            url = "https://doi.org/${latest.doi}",
            citationCount = null,
            isOpenAccess = true,
            provenance = prov
        )
    }

    /**
     * Full version history for a preprint DOI, newest first. Cross-server:
     * bioRxiv is tried first, then medRxiv. Empty when the DOI is not a
     * preprint on either server.
     */
    suspend fun fetchVersionHistory(doiOrUrl: String): List<PreprintVersion> {
        val doi = UnpaywallAdapter.normalizeDoi(doiOrUrl)
        if (!UnpaywallAdapter.looksLikeDoi(doi)) return emptyList()
        val encoded = URLEncoder.encode(doi, "UTF-8")
        for (server in listOf("biorxiv", "medrxiv")) {
            val url = "$BASE/details/$server/$encoded"
            val resp = HttpJsonClient.getJson(
                providerId = descriptor.id,
                url = url,
                queryLabel = "details:$server:$doi",
                spec = descriptor.rateLimit,
                licenseClass = descriptor.licenseClass
            ) ?: continue
            val parsed = parseVersions(resp.body, server)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    /**
     * Response shape: {"collection": [ {...version 1...}, {...version 2...} ]}
     * The API lists oldest→newest; we reverse to newest-first.
     */
    internal fun parseVersions(json: String, server: String): List<PreprintVersion> = try {
        val collection = JSONObject(json).optJSONArray("collection") ?: return emptyList()
        (0 until collection.length()).mapNotNull { i ->
            val o = collection.optJSONObject(i) ?: return@mapNotNull null
            val doi = o.optString("doi").ifBlank { return@mapNotNull null }
            PreprintVersion(
                server = server,
                doi = doi,
                version = o.optInt("version", -1).takeIf { it >= 0 } ?: return@mapNotNull null,
                title = o.optString("title"),
                authors = o.optString("authors"),
                date = o.optString("date"),
                category = o.optString("category").ifBlank { null },
                publishedJournal = o.optString("published").ifBlank { null },
                status = o.optString("status")
            )
        }.sortedByDescending { it.version }
    } catch (e: Exception) {
        emptyList()
    }
}

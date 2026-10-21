package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Unpaywall — legal open-access status and locations for a DOI.
 * Lookup-by-DOI only (no keyword search), which is exactly the intake flow:
 * a claim's source DOI resolves to the best lawful full-text location, which
 * then feeds the agent-side OCR / evidence pipeline.
 * Docs: https://unpaywall.org/products/api
 */
data class UnpaywallRecord(
    val doi: String,
    val title: String,
    val journal: String,
    val year: Int?,
    val authors: String,
    val isOa: Boolean,
    /** best_oa_location: "green"/"gold"/"hybrid"/"bronze" */
    val oaStatus: String?,
    val bestOaUrl: String?,
    val bestOaPdfUrl: String?,
    val provenance: Provenance
) {
    fun toNormalizedRecord(): NormalizedRecord = NormalizedRecord(
        externalId = "Unpaywall:$doi",
        source = "Unpaywall",
        title = title,
        authors = authors,
        journal = journal,
        year = year,
        doi = doi,
        pmid = null,
        abstractText = "",
        url = bestOaUrl ?: "https://doi.org/$doi",
        citationCount = null,
        isOpenAccess = isOa,
        provenance = provenance
    )
}

object UnpaywallAdapter : IntegrationAdapter {

    private const val BASE = "https://api.unpaywall.org/v2"

    override val descriptor = AdapterDescriptor(
        id = "unpaywall_api",
        displayName = "Unpaywall (OA locations)",
        category = ProviderCategory.LITERATURE,
        authType = AuthType.NONE,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 2.0, burst = 2),
        // Deliberately no SEARCH: Unpaywall is DOI-lookup only, and the
        // literature fan-out must not send keyword queries to it.
        capabilities = setOf(Capability.FULLTEXT_LINK, Capability.FETCH_METADATA),
        docsUrl = "https://unpaywall.org/products/api",
        requiresEmail = true
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        val doi = query.text.trim()
        if (!looksLikeDoi(doi)) return emptyList()
        val rec = fetchOa(doi, query.contactEmail) ?: return emptyList()
        return listOf(rec.toNormalizedRecord())
    }

    /** Open-access metadata for a DOI (also accepts a bare https://doi.org/ URL). */
    suspend fun fetchOa(doiOrUrl: String, contactEmail: String?): UnpaywallRecord? {
        val doi = normalizeDoi(doiOrUrl)
        if (!looksLikeDoi(doi)) return null
        val email = URLEncoder.encode(contactEmail ?: "research@concordance.app", "UTF-8")
        val encoded = URLEncoder.encode(doi, "UTF-8")
        val url = "$BASE/$encoded?email=$email"

        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = doi,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return null
        return parseRecord(resp.body, resp.provenance)
    }

    internal fun normalizeDoi(input: String): String = input.trim()
        .removePrefix("https://doi.org/")
        .removePrefix("http://doi.org/")
        .removePrefix("doi:")
        .removePrefix("DOI:")
        .trim()

    internal fun looksLikeDoi(input: String): Boolean {
        val doi = normalizeDoi(input)
        return doi.isNotBlank() && doi.contains('/') && !doi.contains(' ')
    }

    internal fun parseRecord(json: String, prov: Provenance): UnpaywallRecord? = try {
        val o = JSONObject(json)
        val doi = o.optString("doi").ifBlank { return null }
        val best = o.optJSONObject("best_oa_location")
        val authors = o.optJSONArray("z_authors")?.let { arr ->
            val names = (0 until arr.length()).mapNotNull { i ->
                val a = arr.optJSONObject(i) ?: return@mapNotNull null
                (a.optString("given") + " " + a.optString("family")).trim().ifBlank { null }
            }
            when {
                names.isEmpty() -> ""
                names.size <= 4 -> names.joinToString(", ")
                else -> names.take(3).joinToString(", ") + " et al."
            }
        }.orEmpty()
        UnpaywallRecord(
            doi = doi,
            title = o.optString("title"),
            journal = o.optString("journal_name"),
            year = o.optInt("year", -1).takeIf { it > 0 },
            authors = authors.trim(),
            isOa = o.optBoolean("is_oa", false),
            oaStatus = o.optString("oa_status").ifBlank { null },
            bestOaUrl = best?.optString("url")?.ifBlank { null },
            bestOaPdfUrl = best?.optString("url_for_pdf")?.ifBlank { null },
            provenance = prov
        )
    } catch (e: Exception) {
        null
    }
}

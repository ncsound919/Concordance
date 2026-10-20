package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Europe PMC REST API. No key required, fully open metadata (incl. abstracts).
 * Docs: https://europepmc.org/RestfulWebService
 */
object EuropePmcAdapter : IntegrationAdapter {

    override val descriptor = AdapterDescriptor(
        id = "europe_pmc",
        displayName = "Europe PMC",
        category = ProviderCategory.LITERATURE,
        authType = AuthType.NONE,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 3.0, burst = 3),
        capabilities = setOf(
            Capability.SEARCH,
            Capability.FETCH_METADATA,
            Capability.FULLTEXT_LINK
        ),
        docsUrl = "https://europepmc.org/RestfulWebService"
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        if (query.text.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query.text, "UTF-8")
        val page = (query.page + 1).coerceAtLeast(1)
        val url = "https://www.ebi.ac.uk/europepmc/webservices/rest/search" +
                "?query=$encoded&format=json&resultType=core" +
                "&pageSize=${query.pageSize}&page=$page"

        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = query.text,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return emptyList()

        return try {
            val root = JSONObject(resp.body)
            val arr = root.optJSONObject("resultList")?.optJSONArray("result") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val src = o.optString("source")
                NormalizedRecord(
                    externalId = "EPMC:$id",
                    source = "Europe PMC ($src)",
                    title = o.optString("title"),
                    authors = o.optString("authorString"),
                    journal = o.optString("journalTitle"),
                    year = o.optString("pubYear").toIntOrNull(),
                    doi = o.optString("doi").ifBlank { null },
                    pmid = o.optString("pmid").ifBlank { null },
                    abstractText = o.optString("abstractText"),
                    url = "https://europepmc.org/article/$src/$id",
                    citationCount = o.optInt("citedByCount", -1).takeIf { it >= 0 },
                    isOpenAccess = o.optString("isOpenAccess").equals("Y", ignoreCase = true),
                    provenance = resp.provenance
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

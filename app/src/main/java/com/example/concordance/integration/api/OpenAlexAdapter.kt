package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONObject
import java.net.URLEncoder
import java.util.TreeMap

/**
 * OpenAlex works index. Free and keyless when a contact email is supplied
 * (the "polite pool"). Docs: https://docs.openalex.org/
 */
object OpenAlexAdapter : IntegrationAdapter {

    override val descriptor = AdapterDescriptor(
        id = "openalex_api",
        displayName = "OpenAlex",
        category = ProviderCategory.LITERATURE,
        authType = AuthType.NONE,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 5.0, burst = 5),
        capabilities = setOf(
            Capability.SEARCH,
            Capability.FETCH_METADATA,
            Capability.FETCH_COUNTS
        ),
        docsUrl = "https://docs.openalex.org/",
        requiresEmail = true
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        if (query.text.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query.text, "UTF-8")
        val mail = URLEncoder.encode(query.contactEmail ?: "research@concordance.app", "UTF-8")
        val page = (query.page + 1).coerceAtLeast(1)
        val url = "https://api.openalex.org/works" +
                "?search=$encoded&per-page=${query.pageSize}&page=$page&mailto=$mail"

        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = query.text,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return emptyList()

        return try {
            val root = JSONObject(resp.body)
            val arr = root.optJSONArray("results") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val openAlexId = o.optString("id").substringAfterLast('/')
                val doi = o.optString("doi").removePrefix("https://doi.org/").ifBlank { null }
                NormalizedRecord(
                    externalId = "OpenAlex:$openAlexId",
                    source = "OpenAlex",
                    title = o.optString("display_name").ifBlank { o.optString("title") },
                    authors = reconstructAuthors(o),
                    journal = o.optJSONObject("primary_location")
                        ?.optJSONObject("source")
                        ?.optString("display_name")
                        .orEmpty(),
                    year = o.optInt("publication_year", -1).takeIf { it > 0 },
                    doi = doi,
                    pmid = null,
                    abstractText = reconstructAbstract(o.optJSONObject("abstract_inverted_index")),
                    url = if (doi != null) "https://doi.org/$doi" else o.optString("id").ifBlank { null },
                    citationCount = o.optInt("cited_by_count", -1).takeIf { it >= 0 },
                    isOpenAccess = o.optJSONObject("open_access")?.optBoolean("is_oa"),
                    provenance = resp.provenance
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** OpenAlex stores authors as an array of authorships with nested author objects. */
    private fun reconstructAuthors(work: JSONObject): String {
        val authorships = work.optJSONArray("authorships") ?: return ""
        val names = mutableListOf<String>()
        for (i in 0 until authorships.length()) {
            val name = authorships.optJSONObject(i)
                ?.optJSONObject("author")
                ?.optString("display_name")
                .orEmpty()
            if (name.isNotBlank()) names.add(name)
        }
        return when {
            names.isEmpty() -> ""
            names.size <= 4 -> names.joinToString(", ")
            else -> names.take(3).joinToString(", ") + " et al."
        }
    }

    /**
     * OpenAlex returns abstracts as an inverted index (word -> [positions]).
     * Rebuild the plain text by sorting the positions.
     */
    private fun reconstructAbstract(inverted: JSONObject?): String {
        if (inverted == null) return ""
        val positions = TreeMap<Int, String>()
        val keys = inverted.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val arr = inverted.optJSONArray(word) ?: continue
            for (i in 0 until arr.length()) {
                val pos = arr.optInt(i, -1)
                if (pos >= 0) positions[pos] = word
            }
        }
        return positions.values.joinToString(" ")
    }
}

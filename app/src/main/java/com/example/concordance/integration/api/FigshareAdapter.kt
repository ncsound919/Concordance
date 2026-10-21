package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONArray
import org.json.JSONObject

/**
 * Figshare REST API v2. Public read is keyless; writes require a personal
 * access token (BYOK).
 *
 * Deep flow: attach Figshare articles to capsules as artifact provenance, and
 * publish a sprint's aggregate dataset to Figshare with the capsule DOI as a
 * related identifier.
 * Docs: https://docs.figshare.com/
 */
data class FigshareArticle(
    val id: Long,
    val title: String,
    val description: String?,
    val doi: String?,
    val publishedDate: String?,
    val url: String?,
    val tags: List<String>,
    val categories: List<String>
)

object FigshareAdapter : IntegrationAdapter {

    private const val BASE = "https://api.figshare.com/v2"

    override val descriptor = AdapterDescriptor(
        id = "figshare_api",
        displayName = "Figshare",
        category = ProviderCategory.LITERATURE,
        authType = AuthType.ACCESS_TOKEN,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 2.0, burst = 4),
        capabilities = setOf(Capability.SEARCH, Capability.FETCH_METADATA),
        docsUrl = "https://docs.figshare.com/"
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        val (articles, prov) = searchArticles(query.text, query.pageSize, null) ?: return emptyList()
        return articles.map { a ->
            NormalizedRecord(
                externalId = "Figshare:${a.id}",
                source = "Figshare",
                title = a.title,
                authors = "Figshare contributors",
                journal = "Figshare article",
                year = a.publishedDate?.take(4)?.toIntOrNull(),
                doi = a.doi,
                pmid = null,
                abstractText = a.description.orEmpty(),
                url = a.url ?: (a.doi?.let { "https://doi.org/$it" }),
                citationCount = null,
                isOpenAccess = true,
                provenance = prov
            )
        }
    }

    /** Article search by keyword. */
    suspend fun searchArticles(term: String, limit: Int = 25, authToken: String? = null): Pair<List<FigshareArticle>, Provenance?>? {
        val t = term.trim()
        if (t.isBlank()) return null
        val body = JSONObject()
            .put("search_for", t)
            .put("page", 1)
            .put("page_size", limit.coerceIn(1, 100))
            .toString()
        val resp = HttpJsonClient.postJson(
            providerId = descriptor.id,
            url = "$BASE/articles/search",
            queryLabel = t,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass,
            jsonBody = body,
            authToken = authToken
        ) ?: return null
        return parseArticles(resp.body) to resp.provenance
    }

    /** Single article by id. */
    suspend fun fetchArticle(id: Long, authToken: String? = null): FigshareArticle? {
        val url = "$BASE/articles/$id"
        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = "article:$id",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass,
            authToken = authToken
        ) ?: return null
        return parseArticle(resp.body)
    }

    /** Create a new article (requires a BYOK token). */
    suspend fun createArticle(title: String, description: String?, tags: List<String>, authToken: String): Long? {
        val token = authToken.trim()
        if (token.isBlank() || title.isBlank()) return null
        val body = JSONObject()
            .put("title", title)
            .put("description", description ?: "")
            .put("tags", JSONArray(tags))
            .put("defined_type", "dataset")
            .toString()
        val resp = HttpJsonClient.postJson(
            providerId = descriptor.id,
            url = "$BASE/account/articles",
            queryLabel = "create:$title",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass,
            jsonBody = body,
            authToken = token
        ) ?: return null
        return try {
            JSONObject(resp.body).optLong("id", -1L).takeIf { it > 0 }
        } catch (e: Exception) {
            null
        }
    }

    internal fun parseArticles(json: String): List<FigshareArticle> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            parseArticleObject(arr.optJSONObject(i) ?: return@mapNotNull null)
        }
    } catch (e: Exception) {
        emptyList()
    }

    internal fun parseArticle(json: String): FigshareArticle? = try {
        parseArticleObject(JSONObject(json))
    } catch (e: Exception) {
        null
    }

    private fun parseArticleObject(o: JSONObject): FigshareArticle? {
        val id = o.optLong("id", -1L)
        if (id <= 0) return null
        val tags = o.optJSONArray("tags")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).ifBlank { null } }
        }.orEmpty()
        val cats = o.optJSONArray("categories")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name")?.ifBlank { null } }
        }.orEmpty()
        return FigshareArticle(
            id = id,
            title = o.optString("title"),
            description = o.optString("description").ifBlank { null },
            doi = o.optString("doi").ifBlank { null },
            publishedDate = o.optString("published_date").ifBlank { null },
            url = o.optString("url").ifBlank { null },
            tags = tags,
            categories = cats
        )
    }
}

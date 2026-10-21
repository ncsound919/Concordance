package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONArray
import org.json.JSONObject

/**
 * Zotero Web API v3. Requires a BYOK API key plus a user (or group) id.
 *
 * Deep flow: import a team's group collection into an adjudication sprint's
 * evidence pool, and export a sprint's claim source set back as a Zotero
 * collection tagged `concordance:claim:<id>`. Local caching/sync is handled by
 * the WorkManager worker in `integration/zotero/`.
 * Docs: https://www.zotero.org/support/dev/web_api/v3/start
 */
data class ZoteroItem(
    val key: String,
    val version: Int,
    val itemType: String,
    val title: String,
    val creators: String,
    val publicationTitle: String?,
    val date: String?,
    val doi: String?,
    val url: String?,
    val abstractNote: String?,
    val tags: List<String>
)

object ZoteroAdapter : IntegrationAdapter {

    private const val BASE = "https://api.zotero.org"

    override val descriptor = AdapterDescriptor(
        id = "zotero_api",
        displayName = "Zotero (reference library)",
        category = ProviderCategory.LITERATURE,
        authType = AuthType.API_KEY,
        licenseClass = LicenseClass.ACADEMIC_ONLY,
        rateLimit = RateLimitSpec(requestsPerSecond = 1.0, burst = 2),
        capabilities = setOf(Capability.SEARCH, Capability.FETCH_METADATA),
        docsUrl = "https://www.zotero.org/support/dev/web_api/v3/start"
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        // search() cannot resolve a user id on its own; callers use the
        // explicit fetch* methods with a BYOK key + user id. Return empty here
        // so the literature fan-out never hits Zotero.
        return emptyList()
    }

    /** Search a user's library by keyword. */
    suspend fun searchLibrary(userId: String, query: String, apiKey: String, limit: Int = 25): List<ZoteroItem> {
        val uid = userId.trim()
        val key = apiKey.trim()
        if (uid.isBlank() || key.isBlank()) return emptyList()
        val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$BASE/users/$uid/items?limit=${limit.coerceIn(1, 100)}&q=$encoded&format=json"
        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = query,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass,
            extraHeaders = mapOf("Zotero-API-Key" to key)
        ) ?: return emptyList()
        return parseItems(resp.body)
    }

    /** Items in a group library (optionally scoped to a collection). */
    suspend fun fetchGroupItems(groupId: String, collectionKey: String?, apiKey: String, limit: Int = 50): List<ZoteroItem> {
        val gid = groupId.trim()
        val key = apiKey.trim()
        if (gid.isBlank() || key.isBlank()) return emptyList()
        val path = if (collectionKey.isNullOrBlank()) {
            "$BASE/groups/$gid/items"
        } else {
            "$BASE/groups/$gid/collections/$collectionKey/items"
        }
        val url = "$path?limit=${limit.coerceIn(1, 100)}&format=json"
        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = "group:$gid",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass,
            extraHeaders = mapOf("Zotero-API-Key" to key)
        ) ?: return emptyList()
        return parseItems(resp.body)
    }

    /** Create items in a user's library (BYOK key required). */
    suspend fun createItems(userId: String, items: List<JSONObject>, apiKey: String): Boolean {
        val uid = userId.trim()
        val key = apiKey.trim()
        if (uid.isBlank() || key.isBlank() || items.isEmpty()) return false
        val body = JSONArray(items).toString()
        val resp = HttpJsonClient.postJson(
            providerId = descriptor.id,
            url = "$BASE/users/$uid/items",
            queryLabel = "create:${items.size}",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass,
            jsonBody = body,
            extraHeaders = mapOf("Zotero-API-Key" to key)
        )
        return resp != null
    }

    internal fun parseItems(json: String): List<ZoteroItem> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val d = o.optJSONObject("data") ?: return@mapNotNull null
            val title = d.optString("title")
            if (title.isBlank()) return@mapNotNull null
            val creators = d.optJSONArray("creators")?.let { c ->
                (0 until c.length()).mapNotNull { j ->
                    val cr = c.optJSONObject(j) ?: return@mapNotNull null
                    val last = cr.optString("lastName")
                    val first = cr.optString("firstName")
                    when {
                        last.isNotBlank() && first.isNotBlank() -> "$first $last"
                        last.isNotBlank() -> last
                        else -> null
                    }
                }.joinToString(", ")
            }.orEmpty()
            val tags = d.optJSONArray("tags")?.let { t ->
                (0 until t.length()).mapNotNull { j -> t.optJSONObject(j)?.optString("tag")?.ifBlank { null } }
            }.orEmpty()
            ZoteroItem(
                key = o.optString("key"),
                version = o.optInt("version", 0),
                itemType = if (d.isNull("itemType")) "" else d.optString("itemType"),
                title = title,
                creators = creators,
                publicationTitle = if (d.isNull("publicationTitle")) null else d.optString("publicationTitle").ifBlank { null },
                date = if (d.isNull("date")) null else d.optString("date").ifBlank { null },
                doi = if (d.isNull("DOI")) null else d.optString("DOI").ifBlank { null },
                url = if (d.isNull("url")) null else d.optString("url").ifBlank { null },
                abstractNote = if (d.isNull("abstractNote")) null else d.optString("abstractNote").ifBlank { null },
                tags = tags
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun ZoteroItem.toNormalizedRecord(prov: Provenance): NormalizedRecord = NormalizedRecord(
        externalId = "Zotero:$key",
        source = "Zotero",
        title = title,
        authors = creators,
        journal = publicationTitle ?: "Zotero item",
        year = date?.take(4)?.toIntOrNull(),
        doi = doi,
        pmid = null,
        abstractText = abstractNote.orEmpty(),
        url = url ?: doi?.let { "https://doi.org/$it" },
        citationCount = null,
        isOpenAccess = true,
        provenance = prov
    )
}

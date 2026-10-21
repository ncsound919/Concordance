package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Open Science Framework (OSF) REST API v2. Public read is keyless; private
 * nodes and writes require a personal access token (BYOK).
 *
 * Deep flow: attach OSF projects/data sets to capsules as artifact provenance
 * (checksummed links), and publish a sprint's aggregate dataset to OSF with
 * the capsule DOI as a related identifier.
 * Docs: https://developer.osf.io/
 */
data class OsfNode(
    val id: String,
    val title: String,
    val description: String?,
    val category: String?,
    val dateCreated: String?,
    val dateModified: String?,
    val public: Boolean
)

data class OsfFile(
    val id: String,
    val name: String,
    val kind: String,
    val size: Long?,
    val downloadUrl: String?
)

object OsfAdapter : IntegrationAdapter {

    private const val BASE = "https://api.osf.io/v2"

    override val descriptor = AdapterDescriptor(
        id = "osf_api",
        displayName = "Open Science Framework",
        category = ProviderCategory.LITERATURE,
        authType = AuthType.ACCESS_TOKEN,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 2.0, burst = 4),
        capabilities = setOf(Capability.SEARCH, Capability.FETCH_METADATA),
        docsUrl = "https://developer.osf.io/"
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        val (nodes, prov) = searchNodes(query.text, query.pageSize, null) ?: return emptyList()
        return nodes.map { n ->
            NormalizedRecord(
                externalId = "OSF:${n.id}",
                source = "OSF",
                title = n.title,
                authors = "OSF contributors",
                journal = "OSF ${n.category ?: "project"}",
                year = n.dateCreated?.take(4)?.toIntOrNull(),
                doi = null,
                pmid = null,
                abstractText = n.description.orEmpty(),
                url = "https://osf.io/${n.id}/",
                citationCount = null,
                isOpenAccess = n.public,
                provenance = prov
            )
        }
    }

    /** Public project search by title keyword. */
    suspend fun searchNodes(term: String, limit: Int = 25, authToken: String? = null): Pair<List<OsfNode>, Provenance?>? {
        val t = term.trim()
        if (t.isBlank()) return null
        val encoded = URLEncoder.encode(t, "UTF-8")
        val pageSize = limit.coerceIn(1, 100)
        val url = "$BASE/nodes/?filter[title]=$encoded&page[size]=$pageSize"

        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = t,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass,
            authToken = authToken
        ) ?: return null
        return parseNodes(resp.body) to resp.provenance
    }

    /** Files attached to a node (for artifact provenance). */
    suspend fun listFiles(nodeId: String, authToken: String? = null): List<OsfFile> {
        val id = nodeId.trim()
        if (id.isBlank()) return emptyList()
        val url = "$BASE/nodes/$id/files/osfstorage/?page[size]=100"
        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = "files:$id",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass,
            authToken = authToken
        ) ?: return emptyList()
        return parseFiles(resp.body)
    }

    /** Create a new OSF project (requires a BYOK token). */
    suspend fun createProject(title: String, description: String?, authToken: String): String? {
        val token = authToken.trim()
        if (token.isBlank() || title.isBlank()) return null
        val body = JSONObject()
            .put("data", JSONObject()
                .put("type", "nodes")
                .put("attributes", JSONObject()
                    .put("title", title)
                    .put("description", description ?: "")
                    .put("category", "project")))
            .toString()
        val resp = HttpJsonClient.postJson(
            providerId = descriptor.id,
            url = "$BASE/nodes/",
            queryLabel = "create:$title",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass,
            jsonBody = body,
            authToken = token
        ) ?: return null
        return try {
            JSONObject(resp.body).optJSONObject("data")?.optString("id")?.ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    internal fun parseNodes(json: String): List<OsfNode> = try {
        val arr = JSONObject(json).optJSONArray("data") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isBlank()) return@mapNotNull null
            val a = o.optJSONObject("attributes") ?: JSONObject()
            OsfNode(
                id = id,
                title = a.optString("title"),
                description = a.optString("description").ifBlank { null },
                category = a.optString("category").ifBlank { null },
                dateCreated = a.optString("date_created").ifBlank { null },
                dateModified = a.optString("date_modified").ifBlank { null },
                public = a.optBoolean("public", false)
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    internal fun parseFiles(json: String): List<OsfFile> = try {
        val arr = JSONObject(json).optJSONArray("data") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val a = o.optJSONObject("attributes") ?: JSONObject()
            OsfFile(
                id = o.optString("id"),
                name = a.optString("name"),
                kind = a.optString("kind"),
                size = a.optLong("size", -1L).takeIf { it >= 0 },
                downloadUrl = a.optString("download").ifBlank { null }
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

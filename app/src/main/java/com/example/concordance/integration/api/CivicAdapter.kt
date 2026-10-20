package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONObject

/**
 * CIViC (Clinical Interpretation of Variants in Cancer) v2 GraphQL API.
 *
 * Keyless and free (anonymous rate limit 3 req/sec over a 5-minute window). The
 * adapter queries curated evidence items for a single gene symbol (e.g. "ESR1").
 * Docs: https://civic.readthedocs.io/en/latest/api.html
 */
object CivicAdapter : IntegrationAdapter {

    private const val ENDPOINT = "https://civicdb.org/api/graphql"
    private const val MAX_MOLECULAR_PROFILES = 3

    override val descriptor = AdapterDescriptor(
        id = "civic_api",
        displayName = "CIViC Curated Variant Evidence",
        category = ProviderCategory.VARIANT_EVIDENCE,
        authType = AuthType.NONE,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 3.0, burst = 3),
        capabilities = setOf(Capability.SEARCH, Capability.FETCH_METADATA),
        docsUrl = "https://civic.readthedocs.io/en/latest/api.html"
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        val (evidence, provenance) = fetch(query.text, query.pageSize)
        if (provenance == null) return emptyList()
        return evidence.map { it.toNormalizedRecord(provenance) }
    }

    /** Curated variant evidence for a gene symbol (case-insensitive). */
    suspend fun searchEvidence(geneSymbol: String, limit: Int = 25): List<CuratedVariantEvidence> =
        fetch(geneSymbol, limit).first

    private suspend fun fetch(geneSymbol: String, limit: Int): Pair<List<CuratedVariantEvidence>, Provenance?> {
        val gene = geneSymbol.trim()
        if (gene.isBlank()) return emptyList<CuratedVariantEvidence>() to null

        val resp = HttpJsonClient.postJson(
            providerId = descriptor.id,
            url = ENDPOINT,
            queryLabel = "gene:$gene",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass,
            jsonBody = buildRequestBody(gene, limit)
        ) ?: return emptyList<CuratedVariantEvidence>() to null

        return parseEvidence(resp.body) to resp.provenance
    }

    private fun buildRequestBody(gene: String, limit: Int): String {
        val n = limit.coerceIn(1, 50)
        // Raw string keeps the GraphQL query readable; ${'$'} emits a literal '$'
        // so it is not treated as a Kotlin string template.
        val query = """
            query(${'$'}sym: String!, ${'$'}n: Int!) {
              gene(entrezSymbol: ${'$'}sym) {
                name
                variants(first: ${'$'}n) {
                  nodes {
                    name
                    variantTypes { name }
                    molecularProfiles(first: $MAX_MOLECULAR_PROFILES) {
                      nodes {
                        name
                        evidenceItems(first: ${'$'}n) {
                          nodes {
                            id
                            link
                            evidenceType
                            evidenceLevel
                            evidenceDirection
                            significance
                            evidenceRating
                            disease { name }
                            therapies { name }
                            description
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        return JSONObject()
            .put("query", query)
            .put("variables", JSONObject().put("sym", gene.uppercase()).put("n", n))
            .toString()
    }

    /**
     * Parses a CIViC GraphQL response into de-duplicated evidence items.
     * Kept internal so it can be unit-tested without a network call.
     */
    internal fun parseEvidence(json: String): List<CuratedVariantEvidence> {
        return try {
            val root = JSONObject(json)
            val geneObj = root.optJSONObject("data")?.optJSONObject("gene") ?: return emptyList()
            val geneName = geneObj.optString("name")
            val variants = geneObj.optJSONObject("variants")?.optJSONArray("nodes") ?: return emptyList()

            val byId = LinkedHashMap<Int, CuratedVariantEvidence>()
            for (i in 0 until variants.length()) {
                val variant = variants.optJSONObject(i) ?: continue
                val variantName = variant.optString("name")
                val variantTypes = variant.optJSONArray("variantTypes")
                    ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name")?.ifBlank { null } } }
                    .orEmpty()

                val profiles = variant.optJSONObject("molecularProfiles")?.optJSONArray("nodes") ?: continue
                for (j in 0 until profiles.length()) {
                    val profile = profiles.optJSONObject(j) ?: continue
                    val profileName = profile.optString("name")
                    val evidence = profile.optJSONObject("evidenceItems")?.optJSONArray("nodes") ?: continue

                    for (k in 0 until evidence.length()) {
                        val e = evidence.optJSONObject(k) ?: continue
                        val id = e.optInt("id", -1)
                        if (id < 0 || byId.containsKey(id)) continue

                        val link = e.optString("link")
                        byId[id] = CuratedVariantEvidence(
                            civicEvidenceId = id,
                            civicUrl = if (link.isBlank()) "https://civicdb.org/evidence/$id" else "https://civicdb.org$link",
                            gene = geneName,
                            variant = variantName,
                            variantTypes = variantTypes,
                            molecularProfile = profileName,
                            evidenceType = e.optString("evidenceType"),
                            evidenceLevel = e.optString("evidenceLevel"),
                            evidenceDirection = e.optString("evidenceDirection"),
                            significance = e.optString("significance"),
                            evidenceRating = e.optInt("evidenceRating", -1).takeIf { it >= 0 },
                            disease = e.optJSONObject("disease")?.optString("name").orEmpty(),
                            therapies = e.optJSONArray("therapies")
                                ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name")?.ifBlank { null } } }
                                .orEmpty(),
                            description = e.optString("description")
                        )
                    }
                }
            }
            byId.values.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

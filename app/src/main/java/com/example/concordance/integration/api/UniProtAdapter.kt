package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * UniProt REST — protein/gene records, the normalization spine that ChEMBL
 * targets and GDC mutations resolve against. Keyless (polite pool).
 * Docs: https://www.ebi.ac.uk/proteins/api/doc/ and https://rest.uniprot.org
 */
data class ProteinRecord(
    val accession: String,
    val entryId: String,
    val proteinName: String,
    val geneSymbol: String?,
    val organism: String?,
    val functionSummary: String?,
    /** Databases the entry cross-references, e.g. ["ChEMBL", "Reactome", "PDB"]. */
    val crossReferencedDatabases: List<String>
)

object UniProtAdapter : IntegrationAdapter {

    private const val BASE = "https://rest.uniprot.org/uniprotkb/search"

    override val descriptor = AdapterDescriptor(
        id = "uniprot_api",
        displayName = "UniProt Knowledge Base",
        category = ProviderCategory.BIOCHEM_COMPOUNDS,
        authType = AuthType.NONE,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 1.0, burst = 1),
        capabilities = setOf(Capability.SEARCH, Capability.FETCH_METADATA),
        docsUrl = "https://rest.uniprot.org/"
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        val term = query.text.trim()
        if (term.isBlank()) return emptyList()
        val (proteins, prov) = fetchProteins(term, query.pageSize) ?: return emptyList()
        return proteins.map { p ->
            NormalizedRecord(
                externalId = "UniProt:${p.accession}",
                source = "UniProt",
                title = "${p.proteinName} (${p.entryId})",
                authors = p.organism ?: "UniProt",
                journal = "UniProtKB entry ${p.accession}",
                year = null,
                doi = null,
                pmid = null,
                abstractText = p.functionSummary.orEmpty(),
                url = "https://www.uniprot.org/uniprotkb/${p.accession}",
                citationCount = null,
                isOpenAccess = true,
                provenance = prov
            )
        }
    }

    /** Free-text/gene query against UniProtKB, e.g. "gene:BRCA1", "EGFR human". */
    suspend fun fetchProteins(term: String, limit: Int = 25): Pair<List<ProteinRecord>, Provenance>? {
        val t = term.trim()
        if (t.isBlank()) return null
        val encoded = URLEncoder.encode(t, "UTF-8")
        val pageSize = limit.coerceIn(1, 100)
        val url = "$BASE?query=$encoded&format=json&size=$pageSize"

        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = t,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return null
        return parseProteins(resp.body) to resp.provenance
    }

    /** Convenience: resolve a gene symbol to the reviewed human entry. */
    suspend fun resolveHumanGene(geneSymbol: String): ProteinRecord? =
        fetchProteins("gene:$geneSymbol AND organism_id:9606 AND reviewed:true", 5)
            ?.first?.firstOrNull { it.accession.isNotBlank() }

    internal fun parseProteins(json: String): List<ProteinRecord> = try {
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val accession = o.optString("primaryAccession")
            if (accession.isBlank()) return@mapNotNull null
            ProteinRecord(
                accession = accession,
                entryId = o.optString("uniProtkbId"),
                proteinName = recommendedProteinName(o.optJSONObject("proteinDescription")),
                geneSymbol = o.optJSONArray("genes")?.optJSONObject(0)
                    ?.optJSONObject("geneName")?.optString("value")?.ifBlank { null },
                organism = o.optJSONObject("organism")?.optString("scientificName")?.ifBlank { null },
                functionSummary = firstFunctionText(o.optJSONArray("comments")),
                crossReferencedDatabases = crossRefDatabases(o.optJSONArray("uniProtKBCrossReferences"))
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun recommendedProteinName(desc: JSONObject?): String {
        return try {
            // New REST API uses "recommendedName" (singular object); older dumps
            // used "recommendedProteinNames" (array). Handle both.
            val fromSingular = desc?.optJSONObject("recommendedName")
                ?.optJSONObject("fullName")?.optString("value").orEmpty()
            if (fromSingular.isNotBlank()) return fromSingular
            desc?.optJSONArray("recommendedProteinNames")?.optJSONObject(0)
                ?.optJSONObject("fullName")?.optString("value").orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    private fun firstFunctionText(comments: JSONArray?): String? {
        return try {
            if (comments != null) {
                for (i in 0 until comments.length()) {
                    val c = comments.optJSONObject(i) ?: continue
                    if (c.optString("commentType") == "FUNCTION") {
                        val text = c.optJSONArray("texts")?.optJSONObject(0)?.optString("value")
                        if (!text.isNullOrBlank()) return text
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun crossRefDatabases(refs: JSONArray?): List<String> = try {
        if (refs == null) emptyList()
        else (0 until refs.length())
            .mapNotNull { refs.optJSONObject(it)?.optString("database")?.ifBlank { null } }
            .distinct()
    } catch (e: Exception) {
        emptyList()
    }
}

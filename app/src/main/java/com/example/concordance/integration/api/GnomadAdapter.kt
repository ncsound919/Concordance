package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONObject

/**
 * gnomAD browser GraphQL API — population allele frequencies for a variant.
 * Keyless. Every variant-scoped claim gets this as structured context so the
 * engine can flag common-variant confounds (high AF → not a rare-driver story).
 * Docs: https://gnomad.broadinstitute.org/about
 *
 * Variant ids use the gnomAD convention: "1-55516888-G-A" (GRCh38, gnomad_r4).
 */
data class GnomadPopulation(val id: String, val ac: Long?, val an: Long?, val af: Double?)

data class GnomadConsequence(
    val geneSymbol: String,
    val consequenceTerms: List<String>
)

data class GnomadVariant(
    val variantId: String,
    val referenceGenome: String?,
    val exomeAc: Long?,
    val exomeAn: Long?,
    val exomeAf: Double?,
    val genomeAc: Long?,
    val genomeAn: Long?,
    val genomeAf: Double?,
    val populations: List<GnomadPopulation>,
    val consequences: List<GnomadConsequence>
) {
    /** Combined exome+genome AF, or null when neither cohort carries the allele. */
    fun combinedAf(): Double? {
        val exomeAf = exomeAf
        val genomeAf = genomeAf
        return when {
            exomeAf != null && genomeAf != null -> {
                val ac = (exomeAc ?: 0L) + (genomeAc ?: 0L)
                val an = (exomeAn ?: 0L) + (genomeAn ?: 0L)
                if (an > 0) ac.toDouble() / an else null
            }
            exomeAf != null -> exomeAf
            else -> genomeAf
        }
    }

    /** Compact one-line summary used as evidence context text. */
    fun summaryLine(): String {
        val af = combinedAf()
        val genes = consequences.map { it.geneSymbol }.filter { it.isNotBlank() }.distinct()
        val geneText = if (genes.isEmpty()) "" else " in ${genes.joinToString("/")}"
        return "gnomAD allele frequency ${af ?: "not observed"} for $variantId$geneText"
    }
}

object GnomadAdapter : IntegrationAdapter {

    private const val ENDPOINT = "https://gnomad.broadinstitute.org/api"

    override val descriptor = AdapterDescriptor(
        id = "gnomad_api",
        displayName = "gnomAD Variant Frequencies",
        category = ProviderCategory.VARIANT_EVIDENCE,
        authType = AuthType.NONE,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 1.0, burst = 2),
        // Lookup-only (variant id), so it stays out of the literature fan-out.
        capabilities = setOf(Capability.FETCH_METADATA),
        docsUrl = "https://gnomad.broadinstitute.org/about"
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        // Variant data is not a literature record, so the interface
        // implementation yields nothing; callers use fetchVariant via the
        // registry and consume the typed GnomadVariant model directly.
        fetchVariant(query.text.trim())
        return emptyList()
    }

    /** Population allele-frequency context for a gnomAD-style variant id. */
    suspend fun fetchVariant(variantId: String): GnomadVariant? {
        val id = variantId.trim()
        if (id.isBlank() || id.contains(' ')) return null
        val body = JSONObject()
            .put(
                "query",
                "query(\$id: String!) { variant(dataset: gnomad_r4, variantId: \$id) {" +
                    " id reference_genome" +
                    " exome { ac an af populations { id ac an af } }" +
                    " genome { ac an af populations { id ac an af } }" +
                    " transcript_consequences { gene_symbol consequence_terms }" +
                    "} }"
            )
            .put("variables", JSONObject().put("id", id))
            .toString()

        val resp = HttpJsonClient.postJson(
            providerId = descriptor.id,
            url = ENDPOINT,
            queryLabel = id,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass,
            jsonBody = body
        ) ?: return null
        return parseVariant(resp.body)
    }

    internal fun parseVariant(json: String): GnomadVariant? {
        return try {
            val v = JSONObject(json).optJSONObject("data")?.optJSONObject("variant")
                ?: return null
            val variantId = v.optString("id")
            if (variantId.isBlank()) return null
        val pops = mutableListOf<GnomadPopulation>()
        for (cohortKey in listOf("exome", "genome")) {
            val cohort = v.optJSONObject(cohortKey) ?: continue
            val arr = cohort.optJSONArray("populations") ?: continue
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val popId = p.optString("id")
                if (popId.isBlank() || pops.any { it.id == popId }) continue
                pops += GnomadPopulation(
                    id = popId,
                    ac = p.optLong("ac", -1L).takeIf { it >= 0 },
                    an = p.optLong("an", -1L).takeIf { it >= 0 },
                    af = p.optDouble("af", -1.0).takeIf { it >= 0.0 }
                )
            }
        }
        val consequences = v.optJSONArray("transcript_consequences")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val c = arr.optJSONObject(i) ?: return@mapNotNull null
                val gene = c.optString("gene_symbol")
                if (gene.isBlank()) return@mapNotNull null
                val terms = c.optJSONArray("consequence_terms")?.let { t ->
                    (0 until t.length()).mapNotNull { t.optString(it).ifBlank { null } }
                }.orEmpty()
                GnomadConsequence(geneSymbol = gene, consequenceTerms = terms)
            }
        }.orEmpty()
        fun cohortStats(key: String): Triple<Long?, Long?, Double?> {
            val c = v.optJSONObject(key) ?: return Triple(null, null, null)
            return Triple(
                c.optLong("ac", -1L).takeIf { it >= 0 },
                c.optLong("an", -1L).takeIf { it >= 0 },
                c.optDouble("af", -1.0).takeIf { it >= 0.0 }
            )
        }
        val (exAc, exAn, exAf) = cohortStats("exome")
        val (geAc, geAn, geAf) = cohortStats("genome")
        GnomadVariant(
            variantId = variantId,
            referenceGenome = v.optString("reference_genome").ifBlank { null },
            exomeAc = exAc, exomeAn = exAn, exomeAf = exAf,
            genomeAc = geAc, genomeAn = geAn, genomeAf = geAf,
            populations = pops,
            consequences = consequences
        )
    } catch (e: Exception) {
            null
        }
    }
}

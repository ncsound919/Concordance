package com.example.concordance.integration.api

import com.example.concordance.integration.clinicaltrials.ClinicalTrialStudy
import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONObject
import java.net.URLEncoder

/**
 * ClinicalTrials.gov v2 REST API. Keyless and public domain.
 * Docs: https://clinicaltrials.gov/data-api/api
 */
object ClinicalTrialsGovAdapter : IntegrationAdapter {

    override val descriptor = AdapterDescriptor(
        id = "clinical_trials",
        displayName = "ClinicalTrials.gov",
        category = ProviderCategory.CLINICAL_TRIALS,
        authType = AuthType.NONE,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 5.0, burst = 5),
        capabilities = setOf(Capability.SEARCH, Capability.TRIALS),
        docsUrl = "https://clinicaltrials.gov/data-api/api"
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        val fetched = fetch(query)
        return fetched.studies.map { study ->
            NormalizedRecord(
                externalId = "NCT:${study.nctId}",
                source = "ClinicalTrials.gov",
                title = study.briefTitle,
                authors = study.sponsor,
                journal = "ClinicalTrials.gov Registry",
                year = study.primaryCompletionDate.take(4).toIntOrNull(),
                doi = null,
                pmid = null,
                abstractText = "${study.brainMetastasisCriteria} ${study.esr1EndpointTracking}".trim(),
                url = study.url,
                citationCount = null,
                isOpenAccess = true,
                provenance = fetched.provenance
                    ?: Provenance("clinical_trials", "", query.text, 0, "", "", descriptor.licenseClass, 0)
            )
        }
    }

    /** Registry-specific entry point that keeps trial-only fields. */
    suspend fun searchStudies(query: IntegrationQuery): List<ClinicalTrialStudy> = fetch(query).studies

    private data class TrialFetch(val studies: List<ClinicalTrialStudy>, val provenance: Provenance?)

    private suspend fun fetch(query: IntegrationQuery): TrialFetch {
        if (query.text.isBlank()) return TrialFetch(emptyList(), null)
        val encoded = URLEncoder.encode(query.text, "UTF-8")
        val page = (query.page + 1).coerceAtLeast(1)
        val url = "https://clinicaltrials.gov/api/v2/studies" +
                "?query.term=$encoded&pageSize=${query.pageSize}&page=$page&format=json"

        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = query.text,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return TrialFetch(emptyList(), null)

        return try {
            val root = JSONObject(resp.body)
            val arr = root.optJSONArray("studies") ?: return TrialFetch(emptyList(), resp.provenance)
            val studies = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { mapStudy(it) }
            }
            TrialFetch(studies, resp.provenance)
        } catch (e: Exception) {
            TrialFetch(emptyList(), resp.provenance)
        }
    }

    private fun mapStudy(study: JSONObject): ClinicalTrialStudy? {
        val protocol = study.optJSONObject("protocolSection") ?: return null
        val idModule = protocol.optJSONObject("identificationModule")
        val nctId = idModule?.optString("nctId").orEmpty()
        if (nctId.isBlank()) return null

        val statusModule = protocol.optJSONObject("statusModule")
        val designModule = protocol.optJSONObject("designModule")
        val conditionsModule = protocol.optJSONObject("conditionsModule")
        val interventionsModule = protocol.optJSONObject("armsInterventionsModule")
        val sponsor = protocol.optJSONObject("sponsorCollaboratorsModule")
            ?.optJSONObject("leadSponsor")
            ?.optString("name")
            .orEmpty()
        val eligibility = protocol.optJSONObject("eligibilityModule")
            ?.optString("eligibilityCriteria")
            .orEmpty()
        val briefSummary = protocol.optJSONObject("descriptionModule")
            ?.optString("briefSummary")
            .orEmpty()

        val phases = mutableListOf<String>()
        designModule?.optJSONArray("phases")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = prettyPhase(arr.optString(i))
                if (p.isNotBlank()) phases.add(p)
            }
        }

        val conditions = mutableListOf<String>()
        conditionsModule?.optJSONArray("conditions")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { conditions.add(it) }
        }

        val interventions = mutableListOf<String>()
        interventionsModule?.optJSONArray("interventions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val entry = arr.optJSONObject(i) ?: continue
                val name = entry.optString("name")
                val type = entry.optString("type").lowercase()
                if (name.isNotBlank()) interventions.add(if (type.isNotBlank()) "${type.replaceFirstChar { it.uppercase() }}: $name" else name)
            }
        }

        val hac = combinedText(eligibility, briefSummary)
        val brainCriteria = if (containsAny(hac, "brain metast", "cns metast", "intracranial", "central nervous system")) {
            "Registry eligibility/summary references CNS involvement. Concordance flags this study for paired tissue-vs-plasma adjudication of any intracranial depletion claim."
        } else {
            "No explicit CNS/brain-metastasis inclusion language detected in the registry record."
        }
        val esr1Tracking = if (containsAny(hac, "ctdna", "circulating tumor dna", "liquid biopsy", "cell-free dna", "esr1")) {
            "Registry record mentions ctDNA/liquid-biopsy or ESR1-based assessment. Verify whether intracranial and extracranial fractions are reported separately."
        } else {
            "No ctDNA/liquid-biopsy or ESR1 tracking language detected in the registry record."
        }

        val completion = statusModule?.optJSONObject("primaryCompletionDateStruct")?.optString("date")
            ?.takeIf { it.isNotBlank() }
            ?: statusModule?.optJSONObject("completionDateStruct")?.optString("date").orEmpty()

        return ClinicalTrialStudy(
            nctId = nctId,
            briefTitle = idModule?.optString("briefTitle").orEmpty(),
            officialTitle = idModule?.optString("officialTitle").orEmpty(),
            overallStatus = statusModule?.optString("overallStatus").orEmpty(),
            phase = phases.joinToString(" / ").ifBlank { "Not specified" },
            conditions = conditions,
            interventions = interventions,
            brainMetastasisCriteria = brainCriteria,
            esr1EndpointTracking = esr1Tracking,
            sponsor = sponsor.ifBlank { "Not specified" },
            primaryCompletionDate = completion.ifBlank { "Unknown" },
            url = "https://clinicaltrials.gov/study/$nctId"
        )
    }

    private fun combinedText(vararg parts: String): String = parts.joinToString(" ").lowercase()

    private fun containsAny(haystack: String, vararg needles: String): Boolean =
        needles.any { haystack.contains(it) }

    private fun prettyPhase(raw: String): String = when (raw.uppercase()) {
        "EARLY_PHASE1" -> "Early Phase 1"
        "PHASE1" -> "Phase 1"
        "PHASE2" -> "Phase 2"
        "PHASE3" -> "Phase 3"
        "PHASE4" -> "Phase 4"
        "NA" -> "Not Applicable"
        else -> raw.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }
}

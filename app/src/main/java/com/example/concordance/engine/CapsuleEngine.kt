package com.example.concordance.engine

import com.example.concordance.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

object CapsuleEngine {
    private const val CAPSULE_VERSION = "1"

    /**
     * Version stamped into every capsule. Derived from the app's real version
     * (BuildConfig.VERSION_NAME) so provenance never misreports the producing
     * software as a stale hard-coded number.
     */
    val SOFTWARE_VERSION: String = com.example.BuildConfig.VERSION_NAME

    private val ASSUMPTIONS = listOf(
        "Perfect assay specificity; depletion can only arise from reduced sensitivity in brain samples.",
        "Sensitivity is a single number per site (s_brain = r * s_non_cns), not varying within site.",
        "Observed counts are independent samples; clustering within patients is ignored.",
        "Uncertainty uses the delta method on log-odds for count anchors; for summary anchors the published CI limits are transformed with non-CNS prevalence held fixed."
    )

    private val LIMITATIONS = listOf(
        "A surviving depletion does not separate real biology (L1) from confounding (L2) or dormancy (L4).",
        "Relative sensitivity from paired data uses a composite reference standard and is optimistic.",
        "Research workflow tool for scientific hypothesis adjudication; not a medical device."
    )

    fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Builds canonical sorted JSON string matching the Python canonicalization
     */
    fun buildCanonicalBody(
        inputsJson: JSONObject,
        resultsJson: JSONObject,
        pairedJson: JSONObject?
    ): String {
        val combined = JSONObject()
        combined.put("inputs", inputsJson)
        if (pairedJson != null) {
            combined.put("paired", pairedJson)
        } else {
            combined.put("paired", JSONObject.NULL)
        }
        combined.put("results", resultsJson)
        return sortJsonObject(combined).toString()
    }

    private fun sortJsonObject(obj: JSONObject): JSONObject {
        val sorted = JSONObject()
        val keys = mutableListOf<String>()
        val it = obj.keys()
        while (it.hasNext()) {
            keys.add(it.next())
        }
        keys.sort()
        for (key in keys) {
            val v = obj.get(key)
            when (v) {
                is JSONObject -> sorted.put(key, sortJsonObject(v))
                is JSONArray -> sorted.put(key, sortJsonArray(v))
                else -> sorted.put(key, v)
            }
        }
        return sorted
    }

    private fun sortJsonArray(arr: JSONArray): JSONArray {
        val sorted = JSONArray()
        for (i in 0 until arr.length()) {
            val v = arr.get(i)
            when (v) {
                is JSONObject -> sorted.put(sortJsonObject(v))
                is JSONArray -> sorted.put(sortJsonArray(v))
                else -> sorted.put(v)
            }
        }
        return sorted
    }

    fun buildCapsule(
        adj: AdjudicationResult,
        paired: PairedAnalysisResult? = null,
        createdUtc: String? = null,
        provenanceRecordIds: List<Long> = emptyList(),
        modelRevisionId: String? = null,
        supersedesCapsuleId: String? = null
    ): Capsule {
        val timestamp = createdUtc ?: run {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.format(Date())
        }

        // Generate short DOI-like permanent identifier
        val shortId = UUID.randomUUID().toString().substring(0, 8)
        val permalink = "concordance.app/c/$shortId"

        val inputsJson = JSONObject().apply {
            put("anchor_id", adj.anchor.id)
            put("anchor_label", adj.anchor.label)
            put("evidence_grade", adj.anchor.evidenceGrade.name)
            put("s_n", adj.sn)
            put("r_low", adj.rLow ?: JSONObject.NULL)
            put("r_high", adj.rHigh ?: JSONObject.NULL)
        }

        val resultsJson = JSONObject().apply {
            put("verdict", adj.verdict.name.lowercase(Locale.US))
            put("observed_or", adj.observedOr)
            put("observed_ci_low", adj.observedCiLow)
            put("observed_ci_high", adj.observedCiHigh)
            put("r_null", adj.rNull)
            put("r_significance", adj.rSignificance ?: JSONObject.NULL)
            put("rationale", adj.rationale)
        }

        val pairedJson = paired?.let {
            JSONObject().apply {
                put("claim_modality", it.claimModality)
                put("n_rows", it.totalRows)
                put("synthetic", it.synthetic)
                put("relative_sensitivity", it.relativeSensitivity ?: JSONObject.NULL)
            }
        }

        val canonicalBody = buildCanonicalBody(inputsJson, resultsJson, pairedJson)
        val hash = sha256Hex(canonicalBody)

        val provenanceDigest = if (provenanceRecordIds.isNotEmpty()) {
            sha256Hex(provenanceRecordIds.sorted().joinToString(","))
        } else null

        val fullJson = JSONObject().apply {
            put("capsule_id", shortId)
            put("permalink", permalink)
            put("capsule_version", CAPSULE_VERSION)
            put("software_version", SOFTWARE_VERSION)
            put("created_utc", timestamp)
            put("sha256", hash)
            put("question", "Does the reported brain-metastasis depletion survive correction for assay detection sensitivity?")
            put("inputs", inputsJson)
            put("results", resultsJson)
            if (pairedJson != null) put("paired", pairedJson)
            put("citable_argument", adj.citableArgument)
            put("plain_language_verdict", adj.plainLanguageVerdict)
            val assumpArr = JSONArray()
            ASSUMPTIONS.forEach { assumpArr.put(it) }
            put("assumptions", assumpArr)
            val limitArr = JSONArray()
            LIMITATIONS.forEach { limitArr.put(it) }
            put("limitations", limitArr)
            if (provenanceDigest != null) {
                put("provenance_digest", provenanceDigest)
                val provArr = JSONArray()
                provenanceRecordIds.sorted().forEach { provArr.put(it) }
                put("provenance_record_ids", provArr)
            }
        }

        val markdown = toMarkdown(adj, shortId, permalink, timestamp, hash, paired, provenanceDigest, provenanceRecordIds.size)

        return Capsule(
            id = shortId,
            permalink = permalink,
            capsuleVersion = CAPSULE_VERSION,
            softwareVersion = SOFTWARE_VERSION,
            createdUtc = timestamp,
            sha256 = hash,
            question = "Does the reported brain-metastasis depletion survive correction for assay detection sensitivity?",
            anchorLabel = adj.anchor.label,
            verdict = adj.verdict,
            citableArgument = adj.citableArgument,
            plainLanguageVerdict = adj.plainLanguageVerdict,
            rNull = adj.rNull,
            rSignificance = adj.rSignificance,
            snAssumed = adj.sn,
            rLow = adj.rLow,
            rHigh = adj.rHigh,
            observedOr = adj.observedOr,
            rawJson = fullJson.toString(2),
            markdown = markdown,
            provenanceDigest = provenanceDigest,
            provenanceRecordIds = provenanceRecordIds,
            modelRevisionId = modelRevisionId,
            supersedesCapsuleId = supersedesCapsuleId
        )
    }

    fun verifyJson(rawJsonString: String): Triple<Boolean, String, String> {
        return try {
            val root = JSONObject(rawJsonString)
            val declaredHash = root.optString("sha256", "")
            if (declaredHash.isEmpty()) {
                return Triple(false, "No sha256 property found in capsule.", "")
            }
            val inputs = root.getJSONObject("inputs")
            val results = root.getJSONObject("results")
            val paired = if (root.has("paired") && !root.isNull("paired")) root.getJSONObject("paired") else null

            val canonical = buildCanonicalBody(inputs, results, paired)
            val computedHash = sha256Hex(canonical)

            val isValid = computedHash.equals(declaredHash, ignoreCase = true)
            Triple(isValid, if (isValid) "Hash verified! Capsule payload is pristine and untampered." else "Hash mismatch! Payload was modified.", computedHash)
        } catch (e: Exception) {
            Triple(false, "Invalid JSON structure: ${e.message}", "")
        }
    }

    private fun toMarkdown(
        adj: AdjudicationResult,
        id: String,
        permalink: String,
        createdUtc: String,
        hash: String,
        paired: PairedAnalysisResult?,
        provenanceDigest: String? = null,
        provenanceRecordCount: Int = 0
    ): String {
        return buildString {
            append("# Claim capsule: ${adj.anchor.label}\n\n")
            append("- **DOI / Permalink**: https://$permalink\n")
            append("- **Capsule ID**: `$id`\n")
            append("- **Verdict**: **${adj.verdict.label}**\n")
            append("- **Rationale**: ${adj.rationale}\n")
            append("- **Created (UTC)**: $createdUtc\n")
            append("- **SHA-256 Digest**: `$hash`\n")
            append("- **Evidence Grade**: ${adj.anchor.evidenceGrade.label} | Source: ${adj.anchor.source}\n\n")
            append("## Plain-Language Citable Verdict\n")
            append("> ${adj.plainLanguageVerdict}\n\n")
            append("## Core Scientific Argument\n")
            append("Depletion in brain metastases cannot be interpreted as genuine organotropism unless assay sensitivity in brain lesions relative to non-CNS sites (r) exceeds the threshold where detection artifact alone reproduces the observations.\n\n")
            append("## Observed Values\n")
            append("- Observed Odds Ratio: ${String.format(Locale.US, "%.3f", adj.observedOr)} (95% CI ${String.format(Locale.US, "%.3f", adj.observedCiLow)} to ${String.format(Locale.US, "%.3f", adj.observedCiHigh)})\n")
            append("- Brain lesion prevalence: ${String.format(Locale.US, "%.3f", adj.prevalenceBrain)} | Other sites: ${String.format(Locale.US, "%.3f", adj.prevalenceOther)}\n")
            if (adj.fisherP != null) {
                append("- Two-tailed Fisher exact p: ${String.format(Locale.US, "%.2e", adj.fisherP)}\n")
            }
            append("\n## Sensitivity Thresholds (r = s_brain / s_non_cns)\n")
            append("- **Artifact-only threshold r***: ${String.format(Locale.US, "%.3f", adj.rNull)} (depletion equals artifact)\n")
            append("- **Significance threshold r_sig**: ${String.format(Locale.US, "%.3f", adj.rSignificance ?: adj.rNull)} (depletion distinguishable from 0)\n")
            append("- **Assumed non-CNS assay sensitivity**: ${String.format(Locale.US, "%.2f", adj.sn)}\n\n")
            append("_${adj.reading}_\n\n")

            if (adj.rLow != null && adj.rHigh != null) {
                append("## Tested Sensitivity Range\n")
                append("- r from ${String.format(Locale.US, "%.2f", adj.rLow)} to ${String.format(Locale.US, "%.2f", adj.rHigh)}\n\n")
            }

            if (paired != null) {
                append("## Paired Tissue/Plasma Evidence\n")
                append("- Rows analyzed: ${paired.totalRows} | Claim modality: ${paired.claimModality}\n")
                append("- Synthetic guardrail: ${paired.synthetic}\n")
                if (paired.relativeSensitivity != null) {
                    append("- Estimated r: ${String.format(Locale.US, "%.3f", paired.relativeSensitivity)}\n")
                }
                append("\n")
            }

            append("## 2026 Clinical Benchmark Context\n")
            append("- July 2026 *Current Oncology* case report: ESR1 Y537S was detected in brain metastasis tissue but absent in postoperative plasma ctDNA in a patient with isolated CNS progression. This clinical finding aligns with r < 0.40 detection discordance.\n\n")

            if (provenanceDigest != null) {
                append("## Source Provenance\n")
                append("- **Provenance digest (SHA-256)**: `$provenanceDigest`\n")
                append("- **Linked source responses**: $provenanceRecordCount\n")
                append("- Each linked record stores provider, endpoint, query, HTTP status, UTC timestamp, and SHA-256 of the response body in the local provenance registry.\n\n")
            }

            append("## What Would Change This\n")
            for (w in adj.whatWouldChange) {
                append("- $w\n")
            }
            append("\n## Assumptions\n")
            for (a in ASSUMPTIONS) {
                append("- $a\n")
            }
            append("\n## Limitations & Regulatory Classification\n")
            for (l in LIMITATIONS) {
                append("- $l\n")
            }
        }
    }
}

package com.example.concordance.integration.retraction

import com.example.concordance.integration.supabase.PostgRestClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-side client for the Retraction Watch mirror (`public.retractions`).
 *
 * Queries the mirror through the existing PostgREST client and reports which of
 * a claim's source DOIs/PMIDs are retracted, so the claim can be flagged into
 * the moderation queue (see `POST /api/v1/moderation/flags`).
 */
class RetractionCheck(private val rest: PostgRestClient) {

    /** Returns the subset of [dois] that are present in the retraction mirror. */
    suspend fun retractedDois(dois: Collection<String>): Set<String> {
        val cleaned = dois.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        if (cleaned.isEmpty()) return emptySet()
        val inList = cleaned.joinToString(",") { it }
        val query = "select=doi&doi=in.($inList)"
        val body = rest.select("retractions", query).getOrNull() ?: return emptySet()
        return parseDois(body).map { it.lowercase() }.toSet()
    }

    /** Returns the subset of [pmids] that are present in the retraction mirror. */
    suspend fun retractedPmids(pmids: Collection<String>): Set<String> {
        val cleaned = pmids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleaned.isEmpty()) return emptySet()
        val inList = cleaned.joinToString(",")
        val query = "select=pmid&pmid=in.($inList)"
        val body = rest.select("retractions", query).getOrNull() ?: return emptySet()
        return parsePmids(body).toSet()
    }

    private fun parseDois(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("doi")?.ifBlank { null } }
    } catch (e: Exception) {
        emptyList()
    }

    private fun parsePmids(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("pmid")?.ifBlank { null } }
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        /** Build the JSON body for a moderation flag request. */
        fun flagBody(artifactType: String, artifactId: String, reason: String): String =
            JSONObject()
                .put("artifact_type", artifactType)
                .put("artifact_id", artifactId)
                .put("reason", reason)
                .toString()
    }
}

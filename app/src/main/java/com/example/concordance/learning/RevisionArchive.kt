package com.example.concordance.learning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned JSON export/import of model revisions for reproducibility (Phase 4).
 *
 * The archive is a plain, human-readable JSON document with an explicit
 * schemaVersion so future format changes can be migrated rather than guessed.
 * Import is strict: an unknown schema version or a missing required field is an
 * error (fail-closed), never a silent partial import.
 */
object RevisionArchive {

    const val SCHEMA_VERSION = 1

    class ArchiveException(message: String) : Exception(message)

    fun export(revisions: List<ModelRevision>): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("kind", "concordance.model-revisions")
        val arr = JSONArray()
        for (r in revisions) {
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("parentRevisionId", r.parentRevisionId ?: JSONObject.NULL)
                    put("createdAtUtc", r.createdAtUtc)
                    put("sn", r.parameters.sn)
                    put("referencePrevalence", r.parameters.referencePrevalence)
                    put("z95", r.parameters.z95)
                    put("metricE", r.metricE)
                    put("promotedBy", r.promotedBy ?: JSONObject.NULL)
                    put("notes", r.notes ?: JSONObject.NULL)
                }
            )
        }
        root.put("revisions", arr)
        return root.toString(2)
    }

    /** Throws [ArchiveException] on any structural problem. Never returns partial data. */
    fun import(json: String): List<ModelRevision> {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw ArchiveException("Not valid JSON: ${e.message}")
        }

        val version = root.optInt("schemaVersion", -1)
        if (version != SCHEMA_VERSION) {
            throw ArchiveException("Unsupported schemaVersion=$version (expected $SCHEMA_VERSION)")
        }
        val kind = root.optString("kind", "")
        if (kind != "concordance.model-revisions") {
            throw ArchiveException("Unexpected archive kind='$kind'")
        }
        val arr = root.optJSONArray("revisions")
            ?: throw ArchiveException("Missing 'revisions' array")

        val out = mutableListOf<ModelRevision>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: throw ArchiveException("revisions[$i] is not an object")
            val id = o.optString("id", "")
            if (id.isBlank()) throw ArchiveException("revisions[$i] has no id")
            val sn = o.optDouble("sn", Double.NaN)
            val ref = o.optDouble("referencePrevalence", Double.NaN)
            val z = o.optDouble("z95", Double.NaN)
            if (sn.isNaN() || ref.isNaN() || z.isNaN()) {
                throw ArchiveException("revisions[$i] ($id) is missing parameters")
            }
            val params = ModelParameters(sn = sn, referencePrevalence = ref, z95 = z)
            if (!params.inBounds()) {
                throw ArchiveException("revisions[$i] ($id) has out-of-bounds parameters")
            }
            out.add(
                ModelRevision(
                    id = id,
                    parentRevisionId = if (o.isNull("parentRevisionId")) null else o.optString("parentRevisionId"),
                    createdAtUtc = o.optString("createdAtUtc", ""),
                    parameters = params,
                    metricE = o.optDouble("metricE", 0.0),
                    promotedBy = if (o.isNull("promotedBy")) null else o.optString("promotedBy"),
                    notes = if (o.isNull("notes")) null else o.optString("notes")
                )
            )
        }
        return out
    }
}

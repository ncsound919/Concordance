package com.example.concordance.integration.supabase

import com.example.concordance.data.ClaimEntity
import com.example.concordance.data.CapsuleEntity
import com.example.concordance.data.ChallengeEntity
import org.json.JSONArray
import org.json.JSONObject

object SyncMappers {

    fun claimToSupabase(c: ClaimEntity): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("label", c.label)
        put("source", c.source)
        put("evidence_grade", c.evidenceGrade)
        put("brain_events", c.brainEvents)
        put("brain_total", c.brainTotal)
        put("other_events", c.otherEvents)
        put("other_total", c.otherTotal)
        put("odds_ratio", c.oddsRatio)
        put("or_ci_low", c.orCiLow)
        put("or_ci_high", c.orCiHigh)
        put("reference_prevalence", c.referencePrevalence)
        put("denominator", c.denominator)
        put("notes", c.notes)
        put("is_custom", c.isCustom)
        put("date_added", c.dateAdded)
        put("challenge_count", c.challengeCount)
        put("model_revision_id", c.modelRevisionId)
        put("owner_type", "user")
        put("visibility", if (c.isCustom) "private" else "public")
        // The record's own classification travels with it; the boundary refuses
        // anything that is not aggregate rather than relabelling it here.
        put("data_class", c.dataClass)
    }

    fun claimFromSupabase(o: JSONObject): ClaimEntity = ClaimEntity(
        id = o.optString("id"),
        label = o.optString("label"),
        source = o.optString("source"),
        evidenceGrade = o.optString("evidence_grade", "ABSTRACT_ONLY"),
        brainEvents = if (o.isNull("brain_events")) null else o.optInt("brain_events"),
        brainTotal = if (o.isNull("brain_total")) null else o.optInt("brain_total"),
        otherEvents = if (o.isNull("other_events")) null else o.optInt("other_events"),
        otherTotal = if (o.isNull("other_total")) null else o.optInt("other_total"),
        oddsRatio = if (o.isNull("odds_ratio")) null else o.optDouble("odds_ratio"),
        orCiLow = if (o.isNull("or_ci_low")) null else o.optDouble("or_ci_low"),
        orCiHigh = if (o.isNull("or_ci_high")) null else o.optDouble("or_ci_high"),
        referencePrevalence = if (o.isNull("reference_prevalence")) null else o.optDouble("reference_prevalence"),
        denominator = o.optString("denominator"),
        notes = o.optString("notes"),
        isCustom = o.optBoolean("is_custom", false),
        dateAdded = o.optString("date_added"),
        challengeCount = o.optInt("challenge_count", 0),
        modelRevisionId = o.optString("model_revision_id").ifBlank { null },
        syncStatus = "synced",
        serverUpdatedAt = parseTs(o.optString("updated_at")),
        version = o.optInt("version", 0),
        deletedAt = null,
        dataClass = o.optString("data_class", ClaimEntity.DATA_CLASS_AGGREGATE)
    )

    fun capsuleToSupabase(c: CapsuleEntity): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("permalink", c.permalink)
        put("capsule_version", c.capsuleVersion)
        put("software_version", c.softwareVersion)
        put("created_utc", c.createdUtc)
        put("sha256", c.sha256)
        put("question", c.question)
        put("anchor_label", c.anchorLabel)
        put("verdict", c.verdict)
        put("citable_argument", c.citableArgument)
        put("plain_language_verdict", c.plainLanguageVerdict)
        put("r_null", c.rNull)
        put("r_significance", c.rSignificance)
        put("sn_assumed", c.snAssumed)
        put("r_low", c.rLow)
        put("r_high", c.rHigh)
        put("observed_or", c.observedOr)
        put("raw_json", c.rawJson)
        put("markdown", c.markdown)
        put("provenance_digest", c.provenanceDigest)
        put("provenance_record_ids", c.provenanceRecordIds)
        put("model_revision_id", c.modelRevisionId)
        put("supersedes_capsule_id", c.supersedesCapsuleId)
        put("owner_type", "user")
        put("visibility", "private")
    }

    fun capsuleFromSupabase(o: JSONObject): CapsuleEntity = CapsuleEntity(
        id = o.optString("id"),
        permalink = o.optString("permalink"),
        capsuleVersion = o.optString("capsule_version"),
        softwareVersion = o.optString("software_version"),
        createdUtc = o.optString("created_utc"),
        sha256 = o.optString("sha256"),
        question = o.optString("question"),
        anchorLabel = o.optString("anchor_label"),
        verdict = o.optString("verdict"),
        citableArgument = o.optString("citable_argument"),
        plainLanguageVerdict = o.optString("plain_language_verdict"),
        rNull = o.optDouble("r_null"),
        rSignificance = if (o.isNull("r_significance")) null else o.optDouble("r_significance"),
        snAssumed = o.optDouble("sn_assumed"),
        rLow = if (o.isNull("r_low")) null else o.optDouble("r_low"),
        rHigh = if (o.isNull("r_high")) null else o.optDouble("r_high"),
        observedOr = o.optDouble("observed_or"),
        rawJson = o.optString("raw_json"),
        markdown = o.optString("markdown"),
        provenanceDigest = o.optString("provenance_digest").ifBlank { null },
        provenanceRecordIds = o.optString("provenance_record_ids").ifBlank { null },
        modelRevisionId = o.optString("model_revision_id").ifBlank { null },
        supersedesCapsuleId = o.optString("supersedes_capsule_id").ifBlank { null },
        syncStatus = "synced",
        serverUpdatedAt = parseTs(o.optString("updated_at")),
        version = o.optInt("version", 0),
        deletedAt = null
    )

    fun challengeToSupabase(c: ChallengeEntity): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("claim_id", c.claimId)
        put("author_handle", c.author)
        put("date", c.date)
        put("notes", c.notes)
        put("updated_brain_events", c.updatedBrainEvents)
        put("updated_brain_total", c.updatedBrainTotal)
        put("updated_other_events", c.updatedOtherEvents)
        put("updated_other_total", c.updatedOtherTotal)
    }

    fun challengeFromSupabase(o: JSONObject): ChallengeEntity = ChallengeEntity(
        id = o.optString("id"),
        claimId = o.optString("claim_id"),
        author = o.optString("author_handle"),
        date = o.optString("date"),
        notes = o.optString("notes"),
        updatedBrainEvents = o.optInt("updated_brain_events"),
        updatedBrainTotal = o.optInt("updated_brain_total"),
        updatedOtherEvents = o.optInt("updated_other_events"),
        updatedOtherTotal = o.optInt("updated_other_total"),
        syncStatus = "synced",
        serverUpdatedAt = parseTs(o.optString("created_at")),
        version = o.optInt("version", 0),
        deletedAt = null
    )

    fun parseJsonArray(text: String): List<JSONObject> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun parseTs(s: String): Long? {
        if (s.isBlank()) return null
        return try {
            java.time.Instant.parse(s).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}
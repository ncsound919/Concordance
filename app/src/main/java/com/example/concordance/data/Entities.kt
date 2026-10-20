package com.example.concordance.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade

@Entity(tableName = "claims")
data class ClaimEntity(
    @PrimaryKey val id: String,
    val label: String,
    val source: String,
    val evidenceGrade: String,
    val brainEvents: Int?,
    val brainTotal: Int?,
    val otherEvents: Int?,
    val otherTotal: Int?,
    val oddsRatio: Double?,
    val orCiLow: Double?,
    val orCiHigh: Double?,
    val referencePrevalence: Double?,
    val denominator: String,
    val notes: String,
    val isCustom: Boolean,
    val dateAdded: String,
    val challengeCount: Int = 0,
    val modelRevisionId: String? = null,
    val syncStatus: String = "local",
    val serverUpdatedAt: Long? = null,
    val version: Int = 0,
    val deletedAt: Long? = null
) {
    fun toAnchor(): Anchor {
        return Anchor(
            id = id,
            label = label,
            source = source,
            evidenceGrade = EvidenceGrade.valueOf(evidenceGrade),
            brainEvents = brainEvents,
            brainTotal = brainTotal,
            otherEvents = otherEvents,
            otherTotal = otherTotal,
            oddsRatio = oddsRatio,
            orCiLow = orCiLow,
            orCiHigh = orCiHigh,
            referencePrevalence = referencePrevalence,
            denominator = denominator,
            notes = notes,
            isCustom = isCustom,
            dateAdded = dateAdded,
            modelRevisionId = modelRevisionId
        )
    }

    companion object {
        fun fromAnchor(a: Anchor, challengeCount: Int = 0): ClaimEntity {
            return ClaimEntity(
                id = a.id,
                label = a.label,
                source = a.source,
                evidenceGrade = a.evidenceGrade.name,
                brainEvents = a.brainEvents,
                brainTotal = a.brainTotal,
                otherEvents = a.otherEvents,
                otherTotal = a.otherTotal,
                oddsRatio = a.oddsRatio,
                orCiLow = a.orCiLow,
                orCiHigh = a.orCiHigh,
                referencePrevalence = a.referencePrevalence,
                denominator = a.denominator,
                notes = a.notes,
                isCustom = a.isCustom,
                dateAdded = a.dateAdded,
                challengeCount = challengeCount,
                modelRevisionId = a.modelRevisionId
            )
        }
    }
}

@Entity(tableName = "capsules")
data class CapsuleEntity(
    @PrimaryKey val id: String,
    val permalink: String,
    val capsuleVersion: String,
    val softwareVersion: String,
    val createdUtc: String,
    val sha256: String,
    val question: String,
    val anchorLabel: String,
    val verdict: String,
    val citableArgument: String,
    val plainLanguageVerdict: String,
    val rNull: Double,
    val rSignificance: Double?,
    val snAssumed: Double,
    val rLow: Double?,
    val rHigh: Double?,
    val observedOr: Double,
    val rawJson: String,
    val markdown: String,
    val provenanceDigest: String? = null,
    val provenanceRecordIds: String? = null,
    val modelRevisionId: String? = null,
    val supersedesCapsuleId: String? = null,
    val syncStatus: String = "local",
    val serverUpdatedAt: Long? = null,
    val version: Int = 0,
    val deletedAt: Long? = null
)

@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey val id: String,
    val claimId: String,
    val author: String,
    val date: String,
    val notes: String,
    val updatedBrainEvents: Int,
    val updatedBrainTotal: Int,
    val updatedOtherEvents: Int,
    val updatedOtherTotal: Int,
    val syncStatus: String = "local",
    val serverUpdatedAt: Long? = null,
    val version: Int = 0,
    val deletedAt: Long? = null
)

@Entity(tableName = "provenance_records")
data class ProvenanceEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val providerId: String,
    val endpoint: String,
    val query: String,
    val httpStatus: Int,
    val retrievedUtc: String,
    val responseSha256: String,
    val licenseClass: String,
    val elapsedMs: Long,
    val capsuleId: String? = null
)

@Entity(tableName = "response_cache")
data class CachedResponseEntity(
    @PrimaryKey val cacheKey: String,
    val body: String,
    val sha256: String,
    val createdAt: Long,
    val ttlMs: Long
)

/**
 * Append-only record of a detection-correction model revision. Never overwritten;
 * a rollback simply points the "current revision" back to a parent. See
 * concordance_self_learning_plan.md.
 */
@Entity(tableName = "model_revisions")
data class ModelRevisionEntity(
    @PrimaryKey val id: String,
    val parentRevisionId: String?,
    val createdAtUtc: String,
    val parametersJson: String,
    val metricE: Double,
    val promotedBy: String? = null,
    val notes: String? = null
)

/**
 * Append-only audit of every learning decision — promoted OR rejected (plan §6.5).
 * A rejected candidate is as important to record as an accepted one.
 */
@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey val entity: String,
    val lastPulledAt: String? = null
)

@Entity(tableName = "learning_audit")
data class LearningAuditEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val candidateParamsJson: String,
    val baselineParamsJson: String,
    val metricTotal: Double,
    val stabilityPenalty: Double,
    val resolutionMiss: Double,
    val calibrationError: Double,
    val parsimony: Double,
    /** PROPOSED | PROMOTED | REJECTED | REVERTED */
    val decision: String,
    val approver: String?,
    val revisionId: String?,
    val createdAtUtc: String,
    val notes: String
)

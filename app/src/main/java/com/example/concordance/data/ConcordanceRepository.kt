package com.example.concordance.data

import com.example.concordance.integration.api.Provenance
import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.learning.EvaluationResult
import com.example.concordance.learning.LearningAudit
import com.example.concordance.learning.LearningDecision
import com.example.concordance.learning.ModelParameters
import com.example.concordance.learning.ModelRevision
import com.example.concordance.model.*
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ConcordanceRepository(private val db: ConcordanceDatabase) {

    private val defaultAnchors = listOf(
        ClaimEntity(
            id = "giannoudis2024_pooled",
            label = "Giannoudis 2024 pooled (Foundation Medicine)",
            source = "Giannoudis et al. 2024; Foundation Medicine real-world clinical cohort",
            evidenceGrade = EvidenceGrade.COUNTS.name,
            brainEvents = 53,
            brainTotal = 822,
            otherEvents = 3220,
            otherTotal = 15516,
            oddsRatio = null,
            orCiLow = null,
            orCiHigh = null,
            referencePrevalence = 3220.0 / 15516.0,
            denominator = "samples",
            notes = "Pooled contrast mixes assays and sample types. Core test anchor for detection-correction model.",
            isCustom = false,
            dateAdded = "2024-06-01",
            challengeCount = 0
        ),
        ClaimEntity(
            id = "annals_106P_meta",
            label = "Annals of Oncology abstract 106P meta-analysis",
            source = "Annals of Oncology abstract 106P (10 pooled studies)",
            evidenceGrade = EvidenceGrade.ABSTRACT_ONLY.name,
            brainEvents = null,
            brainTotal = null,
            otherEvents = null,
            otherTotal = null,
            oddsRatio = 0.35,
            orCiLow = 0.24,
            orCiHigh = 0.52,
            referencePrevalence = 3220.0 / 15516.0,
            denominator = "unknown",
            notes = "Abstract only; methods unseen. Protocol strictly withholds verdict; sensitivity curve shown for planning only.",
            isCustom = false,
            dateAdded = "2025-10-15",
            challengeCount = 0
        ),
    )

    suspend fun seedDefaultsIfNeeded() {
        val existing = db.claimDao().getAllClaims()
        if (existing.isEmpty()) {
            db.claimDao().insertAll(defaultAnchors)
        }
    }

    fun getAllClaimsFlow(): Flow<List<Anchor>> {
        return db.claimDao().getAllClaimsFlow().map { list ->
            list.map { it.toAnchor() }
        }
    }

    suspend fun getClaimById(id: String): Anchor? {
        return db.claimDao().getClaimById(id)?.toAnchor()
    }

    suspend fun addClaim(anchor: Anchor) {
        db.claimDao().insertClaim(ClaimEntity.fromAnchor(anchor))
    }

    suspend fun challengeClaim(
        claimId: String,
        author: String,
        notes: String,
        updatedBrainEvents: Int,
        updatedBrainTotal: Int,
        updatedOtherEvents: Int,
        updatedOtherTotal: Int
    ) {
        val challenge = ChallengeEntity(
            id = UUID.randomUUID().toString(),
            claimId = claimId,
            author = author,
            date = "2026-10-18",
            notes = notes,
            updatedBrainEvents = updatedBrainEvents,
            updatedBrainTotal = updatedBrainTotal,
            updatedOtherEvents = updatedOtherEvents,
            updatedOtherTotal = updatedOtherTotal
        )
        db.challengeDao().insertChallenge(challenge)

        val existing = db.claimDao().getClaimById(claimId)
        if (existing != null) {
            val updated = existing.copy(
                brainEvents = updatedBrainEvents,
                brainTotal = updatedBrainTotal,
                otherEvents = updatedOtherEvents,
                otherTotal = updatedOtherTotal,
                challengeCount = existing.challengeCount + 1,
                notes = "${existing.notes} [Challenged by $author: $notes]",
                syncStatus = "local"
            )
            db.claimDao().updateClaim(updated)
        }
    }

    suspend fun getAllChallenges(): List<ClaimChallenge> {
        return db.challengeDao().getAllChallenges().map { it.toChallenge() }
    }

    fun getAllChallengesFlow(): Flow<List<ClaimChallenge>> {
        return db.challengeDao().getAllChallengesFlow().map { list -> list.map { it.toChallenge() } }
    }

    /** One-shot snapshot of every anchor, used to build a learning training set. */
    suspend fun getAllClaims(): List<Anchor> {
        return db.claimDao().getAllClaims().map { it.toAnchor() }
    }

    fun getChallengesForClaimFlow(claimId: String): Flow<List<ClaimChallenge>> {
        return db.challengeDao().getChallengesForClaimFlow(claimId).map { list ->
            list.map {
                ClaimChallenge(
                    id = it.id,
                    claimId = it.claimId,
                    author = it.author,
                    date = it.date,
                    notes = it.notes,
                    updatedBrainEvents = it.updatedBrainEvents,
                    updatedBrainTotal = it.updatedBrainTotal,
                    updatedOtherEvents = it.updatedOtherEvents,
                    updatedOtherTotal = it.updatedOtherTotal
                )
            }
        }
    }

    fun getAllCapsulesFlow(): Flow<List<Capsule>> {
        return db.capsuleDao().getAllCapsulesFlow().map { list ->
            list.map {
                Capsule(
                    id = it.id,
                    permalink = it.permalink,
                    capsuleVersion = it.capsuleVersion,
                    softwareVersion = it.softwareVersion,
                    createdUtc = it.createdUtc,
                    sha256 = it.sha256,
                    question = it.question,
                    anchorLabel = it.anchorLabel,
                    verdict = Verdict.valueOf(it.verdict),
                    citableArgument = it.citableArgument,
                    plainLanguageVerdict = it.plainLanguageVerdict,
                    rNull = it.rNull,
                    rSignificance = it.rSignificance,
                    snAssumed = it.snAssumed,
                    rLow = it.rLow,
                    rHigh = it.rHigh,
                    observedOr = it.observedOr,
                    rawJson = it.rawJson,
                    markdown = it.markdown,
                    provenanceDigest = it.provenanceDigest,
                    provenanceRecordIds = it.provenanceRecordIds
                        ?.split(",")
                        ?.mapNotNull { s -> s.trim().toLongOrNull() }
                        .orEmpty(),
                    modelRevisionId = it.modelRevisionId,
                    supersedesCapsuleId = it.supersedesCapsuleId
                )
            }
        }
    }

    suspend fun saveCapsule(capsule: Capsule, provenanceRecordIds: List<Long> = emptyList()) {
        val entity = CapsuleEntity(
            id = capsule.id,
            permalink = capsule.permalink,
            capsuleVersion = capsule.capsuleVersion,
            softwareVersion = capsule.softwareVersion,
            createdUtc = capsule.createdUtc,
            sha256 = capsule.sha256,
            question = capsule.question,
            anchorLabel = capsule.anchorLabel,
            verdict = capsule.verdict.name,
            citableArgument = capsule.citableArgument,
            plainLanguageVerdict = capsule.plainLanguageVerdict,
            rNull = capsule.rNull,
            rSignificance = capsule.rSignificance,
            snAssumed = capsule.snAssumed,
            rLow = capsule.rLow,
            rHigh = capsule.rHigh,
            observedOr = capsule.observedOr,
            rawJson = capsule.rawJson,
            markdown = capsule.markdown,
            provenanceDigest = capsule.provenanceDigest,
            provenanceRecordIds = provenanceRecordIds.joinToString(",").ifBlank { null },
            modelRevisionId = capsule.modelRevisionId,
            supersedesCapsuleId = capsule.supersedesCapsuleId
        )
        db.capsuleDao().insertCapsule(entity)
        if (provenanceRecordIds.isNotEmpty()) {
            db.provenanceDao().linkToCapsule(provenanceRecordIds, capsule.id)
        }
    }

    suspend fun deleteCapsule(capsuleId: String) {
        val existing = db.capsuleDao().getCapsuleById(capsuleId)
        if (existing != null) {
            db.capsuleDao().deleteCapsule(existing)
        }
    }

    fun responseCache(): ResponseCache = ResponseCache(db.cachedResponseDao())

    // --- Self-learning: append-only model revisions ---

    fun getAllModelRevisionsFlow(): Flow<List<ModelRevision>> {
        return db.modelRevisionDao().getAllFlow().map { list -> list.map { it.toModelRevision() } }
    }

    /**
     * Fail-closed write boundary (Phase 4 hardening): an out-of-bounds parameter
     * set can never reach the database, no matter how buggy a caller is. This is
     * the DB-access-layer enforcement of the declared search bounds.
     */
    suspend fun saveModelRevision(revision: ModelRevision): Boolean {
        if (!revision.parameters.inBounds()) return false
        db.modelRevisionDao().insert(revision.toEntity())
        return true
    }

    suspend fun getLatestPromotedRevision(): ModelRevision? {
        return db.modelRevisionDao().getLatestPromoted()?.toModelRevision()
    }

    // --- Self-learning: append-only decision audit ---

    fun getAllLearningAuditFlow(): Flow<List<LearningAudit>> {
        return db.learningAuditDao().getAllFlow().map { list -> list.map { it.toLearningAudit() } }
    }

    suspend fun saveLearningAudit(entry: LearningAudit) {
        db.learningAuditDao().insert(entry.toEntity())
    }

    fun getAllProvenanceFlow(): Flow<List<Provenance>> {
        return db.provenanceDao().getAllProvenanceFlow().map { list ->
            list.map { it.toProvenance() }
        }
    }

    suspend fun getProvenanceForCapsule(capsuleId: String): List<Provenance> {
        return db.provenanceDao().getForCapsule(capsuleId).map { it.toProvenance() }
    }

    suspend fun saveProvenance(record: Provenance): Long {
        return db.provenanceDao().insertProvenance(
            ProvenanceEntity(
                providerId = record.providerId,
                endpoint = record.endpoint,
                query = record.query,
                httpStatus = record.httpStatus,
                retrievedUtc = record.retrievedUtc,
                responseSha256 = record.responseSha256,
                licenseClass = record.licenseClass.name,
                elapsedMs = record.elapsedMs
            )
        )
    }
}

private fun ProvenanceEntity.toProvenance(): Provenance = Provenance(
    providerId = providerId,
    endpoint = endpoint,
    query = query,
    httpStatus = httpStatus,
    retrievedUtc = retrievedUtc,
    responseSha256 = responseSha256,
    licenseClass = LicenseClass.valueOf(licenseClass),
    elapsedMs = elapsedMs
)

private fun ChallengeEntity.toChallenge(): ClaimChallenge = ClaimChallenge(
    id = id,
    claimId = claimId,
    author = author,
    date = date,
    notes = notes,
    updatedBrainEvents = updatedBrainEvents,
    updatedBrainTotal = updatedBrainTotal,
    updatedOtherEvents = updatedOtherEvents,
    updatedOtherTotal = updatedOtherTotal
)

// --- Model revision <-> entity mapping (parameters serialized as JSON) ---

private fun ModelParameters.toJsonString(): String = JSONObject()
    .put("sn", sn)
    .put("referencePrevalence", referencePrevalence)
    .put("z95", z95)
    .toString()

private fun jsonToParameters(json: String): ModelParameters {
    val o = JSONObject(json)
    return ModelParameters(
        sn = o.optDouble("sn", 0.90),
        referencePrevalence = o.optDouble("referencePrevalence", 0.2075),
        z95 = o.optDouble("z95", 1.959963984540054)
    )
}

private fun ModelRevision.toEntity(): ModelRevisionEntity = ModelRevisionEntity(
    id = id,
    parentRevisionId = parentRevisionId,
    createdAtUtc = createdAtUtc,
    parametersJson = parameters.toJsonString(),
    metricE = metricE,
    promotedBy = promotedBy,
    notes = notes
)

private fun ModelRevisionEntity.toModelRevision(): ModelRevision = ModelRevision(
    id = id,
    parentRevisionId = parentRevisionId,
    createdAtUtc = createdAtUtc,
    parameters = jsonToParameters(parametersJson),
    metricE = metricE,
    promotedBy = promotedBy,
    notes = notes
)

private fun LearningAudit.toEntity(): LearningAuditEntity = LearningAuditEntity(
    pk = pk,
    candidateParamsJson = candidate.toJsonString(),
    baselineParamsJson = baseline.toJsonString(),
    metricTotal = result.total,
    stabilityPenalty = result.verdictStabilityPenalty,
    resolutionMiss = result.challengeResolutionMissRate,
    calibrationError = result.calibrationError,
    parsimony = result.parsimony,
    decision = decision.name,
    approver = approver,
    revisionId = revisionId,
    createdAtUtc = createdAtUtc,
    notes = notes
)

private fun LearningAuditEntity.toLearningAudit(): LearningAudit = LearningAudit(
    pk = pk,
    candidate = jsonToParameters(candidateParamsJson),
    baseline = jsonToParameters(baselineParamsJson),
    result = EvaluationResult(
        verdictStabilityPenalty = stabilityPenalty,
        challengeResolutionMissRate = resolutionMiss,
        calibrationError = calibrationError,
        parsimony = parsimony,
        total = metricTotal,
        casesEvaluated = 0
    ),
    decision = runCatching { LearningDecision.valueOf(decision) }.getOrDefault(LearningDecision.PROPOSED),
    approver = approver,
    revisionId = revisionId,
    createdAtUtc = createdAtUtc,
    notes = notes
)

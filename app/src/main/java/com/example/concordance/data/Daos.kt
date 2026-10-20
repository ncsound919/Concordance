package com.example.concordance.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClaimDao {
    @Query("SELECT * FROM claims ORDER BY isCustom ASC, id ASC")
    fun getAllClaimsFlow(): Flow<List<ClaimEntity>>

    @Query("SELECT * FROM claims ORDER BY isCustom ASC, id ASC")
    suspend fun getAllClaims(): List<ClaimEntity>

    @Query("SELECT * FROM claims WHERE id = :id LIMIT 1")
    suspend fun getClaimById(id: String): ClaimEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClaim(claim: ClaimEntity)

    @Upsert
    suspend fun upsert(claim: ClaimEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(claims: List<ClaimEntity>)

    @Update
    suspend fun updateClaim(claim: ClaimEntity)

    @Delete
    suspend fun deleteClaim(claim: ClaimEntity)
}

@Dao
interface CapsuleDao {
    @Query("SELECT * FROM capsules ORDER BY createdUtc DESC")
    fun getAllCapsulesFlow(): Flow<List<CapsuleEntity>>

    @Query("SELECT * FROM capsules WHERE id = :id LIMIT 1")
    suspend fun getCapsuleById(id: String): CapsuleEntity?

    @Query("SELECT * FROM capsules ORDER BY createdUtc DESC")
    suspend fun getAllCapsules(): List<CapsuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCapsule(capsule: CapsuleEntity)

    @Upsert
    suspend fun upsert(capsule: CapsuleEntity)

    @Delete
    suspend fun deleteCapsule(capsule: CapsuleEntity)
}

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenges WHERE claimId = :claimId ORDER BY date DESC")
    fun getChallengesForClaimFlow(claimId: String): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges ORDER BY date DESC")
    suspend fun getAllChallenges(): List<ChallengeEntity>

    @Query("SELECT * FROM challenges ORDER BY date DESC")
    fun getAllChallengesFlow(): Flow<List<ChallengeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallenge(challenge: ChallengeEntity)

    @Upsert
    suspend fun upsert(challenge: ChallengeEntity)
}

@Dao
interface ProvenanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvenance(record: ProvenanceEntity): Long

    @Query("SELECT * FROM provenance_records ORDER BY pk DESC")
    fun getAllProvenanceFlow(): Flow<List<ProvenanceEntity>>

    @Query("SELECT * FROM provenance_records WHERE capsuleId = :capsuleId ORDER BY pk ASC")
    suspend fun getForCapsule(capsuleId: String): List<ProvenanceEntity>

    @Query("UPDATE provenance_records SET capsuleId = :capsuleId WHERE pk IN (:recordIds)")
    suspend fun linkToCapsule(recordIds: List<Long>, capsuleId: String)

    @Query("DELETE FROM provenance_records")
    suspend fun deleteAll()
}

@Dao
interface CachedResponseDao {
    @Query("SELECT * FROM response_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): CachedResponseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: CachedResponseEntity)

    @Query("DELETE FROM response_cache WHERE cacheKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM response_cache WHERE (:now - createdAt) > ttlMs")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM response_cache")
    suspend fun clear()
}

@Dao
interface ModelRevisionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(revision: ModelRevisionEntity)

    @Query("SELECT * FROM model_revisions ORDER BY createdAtUtc DESC")
    fun getAllFlow(): Flow<List<ModelRevisionEntity>>

    @Query("SELECT * FROM model_revisions ORDER BY createdAtUtc DESC")
    suspend fun getAll(): List<ModelRevisionEntity>

    @Query("SELECT * FROM model_revisions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ModelRevisionEntity?

    @Query("SELECT * FROM model_revisions WHERE promotedBy IS NOT NULL ORDER BY createdAtUtc DESC LIMIT 1")
    suspend fun getLatestPromoted(): ModelRevisionEntity?
}

@Dao
interface LearningAuditDao {
    @Insert
    suspend fun insert(entry: LearningAuditEntity): Long

    @Query("SELECT * FROM learning_audit ORDER BY pk DESC")
    fun getAllFlow(): Flow<List<LearningAuditEntity>>

    @Query("SELECT * FROM learning_audit ORDER BY pk DESC")
    suspend fun getAll(): List<LearningAuditEntity>
}



@Dao
interface SyncCursorDao {
    @Query("SELECT * FROM sync_cursor WHERE entity = :entity")
    suspend fun get(entity: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: SyncCursorEntity)

    @Query("DELETE FROM sync_cursor WHERE entity = :entity")
    suspend fun clear(entity: String)
}

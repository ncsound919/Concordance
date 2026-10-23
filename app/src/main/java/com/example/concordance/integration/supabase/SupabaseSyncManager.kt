package com.example.concordance.integration.supabase

import com.example.concordance.data.ClaimEntity
import com.example.concordance.data.CapsuleEntity
import com.example.concordance.data.ChallengeEntity
import com.example.concordance.data.ConcordanceDatabase
import com.example.concordance.data.SyncCursorEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Orchestrates read sync: pulls claims/capsules/challenges from Supabase into Room.
 * Offline-first: failures are swallowed and retried by the caller (WorkManager).
 */
data class SyncConflict(val entityType: String, val entityId: String, val reason: String)

class SupabaseSyncManager(
    private val auth: SupabaseAuth,
    private val rest: PostgRestClient,
    private val db: ConcordanceDatabase
) {
    val conflicts: MutableList<SyncConflict> = mutableListOf()
    var onConflict: ((SyncConflict) -> Unit)? = null
    suspend fun pullClaims(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val cursor = db.syncCursorDao().get("claims")?.lastPulledAt
            val query = if (cursor.isNullOrBlank()) "select=*&order=updated_at.asc"
            else "select=*&updated_at=gte.$cursor&order=updated_at.asc"
            val text = rest.select("claims", query).getOrThrow()
            val rows = SyncMappers.parseJsonArray(text)
            var n = 0
            for (o in rows) {
                val remote = SyncMappers.claimFromSupabase(o)
                val local = db.claimDao().getClaimById(remote.id)
                if (local != null && local.syncStatus != "synced" && local.serverUpdatedAt != null
                    && remote.serverUpdatedAt != null && remote.serverUpdatedAt != local.serverUpdatedAt) {
                    // Conflict: local has unsynced changes AND server moved. Don't overwrite.
                    db.claimDao().upsert(local.copy(syncStatus = "conflict"))
                    conflicts.add(SyncConflict("claim", remote.id, "local vs server divergence"))
                    // Route into self-learning/trends/agent repair team (Phase 3.3)
                    onConflict?.invoke(SyncConflict("claim", remote.id, "local vs server divergence"))
                } else {
                    db.claimDao().upsert(remote)
                }
                n++
            }
            val last = rows.lastOrNull()?.optString("updated_at")
            if (!last.isNullOrBlank()) db.syncCursorDao().upsert(SyncCursorEntity("claims", last))
            n
        }
    }

    suspend fun pullCapsules(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val cursor = db.syncCursorDao().get("capsules")?.lastPulledAt
            val query = if (cursor.isNullOrBlank()) "select=*&order=updated_at.asc"
            else "select=*&updated_at=gte.$cursor&order=updated_at.asc"
            val text = rest.select("capsules", query).getOrThrow()
            val rows = SyncMappers.parseJsonArray(text)
            var n = 0
            for (o in rows) {
                val remote = SyncMappers.capsuleFromSupabase(o)
                val local = db.capsuleDao().getCapsuleById(remote.id)
                if (local != null && local.syncStatus != "synced" && local.serverUpdatedAt != null
                    && remote.serverUpdatedAt != null && remote.serverUpdatedAt != local.serverUpdatedAt) {
                    db.capsuleDao().upsert(local.copy(syncStatus = "conflict"))
                    val c = SyncConflict("capsule", remote.id, "local vs server divergence")
                    conflicts.add(c); onConflict?.invoke(c)
                } else {
                    db.capsuleDao().upsert(remote)
                }
                n++
            }
            val last = rows.lastOrNull()?.optString("updated_at")
            if (!last.isNullOrBlank()) db.syncCursorDao().upsert(SyncCursorEntity("capsules", last))
            n
        }
    }

    suspend fun pullChallenges(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val cursor = db.syncCursorDao().get("challenges")?.lastPulledAt
            val query = if (cursor.isNullOrBlank()) "select=*&order=created_at.asc"
            else "select=*&created_at=gte.$cursor&order=created_at.asc"
            val text = rest.select("challenges", query).getOrThrow()
            val rows = SyncMappers.parseJsonArray(text)
            var n = 0
            for (o in rows) {
                db.challengeDao().upsert(SyncMappers.challengeFromSupabase(o))
                n++
            }
            val last = rows.lastOrNull()?.optString("created_at")
            if (!last.isNullOrBlank()) db.syncCursorDao().upsert(SyncCursorEntity("challenges", last))
            n
        }
    }

    /** Pull everything. Returns total rows pulled. */
    suspend fun pullAll(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var total = 0
            total += pullClaims().getOrThrow()
            total += pullCapsules().getOrThrow()
            total += pullChallenges().getOrThrow()
            total
        }
    }

    /** Push dirty claims. Records that are not `aggregate` never leave the device. */
    suspend fun pushClaims(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val session = auth.session ?: error("Not signed in")
            val dirty = db.claimDao().getAllClaims().filter {
                (it.syncStatus == "local" || it.syncStatus == "pending") && it.deletedAt == null
            }
            var n = 0
            for (c in dirty) {
                if (!mayLeaveDevice(c)) {
                    // Hold it locally and say why. One classified record must not
                    // block the rest of the queue.
                    db.claimDao().upsert(c.copy(syncStatus = "blocked"))
                    val conflict = SyncConflict(
                        "claim",
                        c.id,
                        "data_class='${c.dataClass}' is not publishable from this device"
                    )
                    conflicts.add(conflict)
                    onConflict?.invoke(conflict)
                    continue
                }
                val body = SyncMappers.claimToSupabase(c).apply {
                    put("owner_id", session.userId)
                }.toString()
                rest.upsert("claims", body).getOrThrow()
                db.claimDao().upsert(c.copy(syncStatus = "synced", version = c.version + 1))
                n++
            }
            // handle deletes only after the server confirms
            val deleted = db.claimDao().getAllClaims().filter { it.deletedAt != null }
            for (c in deleted) {
                rest.delete("claims", "id", c.id).getOrThrow()
                db.claimDao().deleteClaim(c)
            }
            n
        }
    }

    suspend fun pushCapsules(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val session = auth.session ?: error("Not signed in")
            val dirty = db.capsuleDao().getAllCapsules().filter {
                (it.syncStatus == "local" || it.syncStatus == "pending") && it.deletedAt == null
            }
            var n = 0
            for (c in dirty) {
                val body = SyncMappers.capsuleToSupabase(c).apply {
                    put("owner_id", session.userId)
                    put("data_class", "aggregate")
                }.toString()
                rest.upsert("capsules", body).getOrThrow()
                db.capsuleDao().upsert(c.copy(syncStatus = "synced", version = c.version + 1))
                n++
            }
            val deleted = db.capsuleDao().getAllCapsules().filter { it.deletedAt != null }
            for (c in deleted) {
                rest.delete("capsules", "id", c.id).getOrThrow()
                db.capsuleDao().deleteCapsule(c)
            }
            n
        }
    }

    suspend fun pushChallenges(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val session = auth.session ?: error("Not signed in")
            val all = db.challengeDao().getAllChallenges()
            val dirty = all.filter {
                (it.syncStatus == "local" || it.syncStatus == "pending") && it.deletedAt == null
            }
            var n = 0
            for (c in dirty) {
                val body = SyncMappers.challengeToSupabase(c).apply {
                    put("author_id", session.userId)
                }.toString()
                rest.upsert("challenges", body).getOrThrow()
                db.challengeDao().upsert(c.copy(syncStatus = "synced", version = c.version + 1))
                n++
            }
            n
        }
    }

    suspend fun pushAll(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var total = 0
            total += pushClaims().getOrThrow()
            total += pushCapsules().getOrThrow()
            total += pushChallenges().getOrThrow()
            total
        }
    }

    /** Publish a claim to the public registry (visibility=public + publication row). */
    suspend fun publishClaim(claimId: String, license: String = "CC-BY-4.0"): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val session = auth.session ?: error("Not signed in")
                val claim = db.claimDao().getClaimById(claimId) ?: error("Claim not found")
                require(mayLeaveDevice(claim)) {
                    "data_class='${claim.dataClass}' cannot be published from this device"
                }
                rest.patch("claims", "id", claimId, """{"visibility":"public","published_at":"${java.time.Instant.now()}"}""").getOrThrow()
                val pub = org.json.JSONObject().apply {
                    put("artifact_type", "claim")
                    put("artifact_id", claimId)
                    put("publisher_id", session.userId)
                    put("visibility", "public")
                    put("license", license)
                }.toString()
                rest.insert("publications", pub).getOrThrow()
                Unit
            }
        }

    /** Publish a capsule to the public registry. */
    suspend fun publishCapsule(capsuleId: String, license: String = "CC-BY-4.0"): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val session = auth.session ?: error("Not signed in")
                rest.patch("capsules", "id", capsuleId, """{"visibility":"public","published_at":"${java.time.Instant.now()}"}""").getOrThrow()
                val pub = org.json.JSONObject().apply {
                    put("artifact_type", "capsule")
                    put("artifact_id", capsuleId)
                    put("publisher_id", session.userId)
                    put("visibility", "public")
                    put("license", license)
                }.toString()
                rest.insert("publications", pub).getOrThrow()
                Unit
            }
        }

    /** Teams visible to the signed-in user. */
    suspend fun myTeams(): Result<String> = withContext(Dispatchers.IO) {
        rest.select("teams", "select=*,team_memberships!inner(user_id,role)&team_memberships.user_id=eq.${auth.session?.userId}")
    }

    companion object {
        /**
         * The sync boundary. A record may leave the device only when it is
         * explicitly aggregate-class; the classification is stored on the record
         * and never assumed at push time.
         */
        fun mayLeaveDevice(claim: ClaimEntity): Boolean =
            claim.dataClass == ClaimEntity.DATA_CLASS_AGGREGATE
    }
}
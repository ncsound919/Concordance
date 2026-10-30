package com.example

import androidx.room.Room
import com.example.concordance.data.*
import com.example.concordance.model.EvidenceGrade
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDaoTest {

    private lateinit var db: ConcordanceDatabase

    @Before
    fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, ConcordanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() = db.close()

    // ---- ClaimDao ----
    @Test
    fun claimDao_insertAndGetById() = runBlocking {
        val claim = ClaimEntity(
            id = "c1", label = "L", source = "S", evidenceGrade = EvidenceGrade.COUNTS.name,
            brainEvents = 1, brainTotal = 10, otherEvents = 2, otherTotal = 100,
            oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
            denominator = "samples", notes = "n", isCustom = false, dateAdded = "d"
        )
        db.claimDao().insertClaim(claim)
        assertEquals(claim, db.claimDao().getClaimById("c1"))
    }

    @Test
    fun claimDao_getClaimById_missing_returnsNull() = runBlocking {
        assertNull(db.claimDao().getClaimById("nope"))
    }

    @Test
    fun claimDao_upsert_replaces() = runBlocking {
        val base = ClaimEntity(
            id = "c1", label = "L", source = "S", evidenceGrade = EvidenceGrade.COUNTS.name,
            brainEvents = 1, brainTotal = 10, otherEvents = 2, otherTotal = 100,
            oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
            denominator = "samples", notes = "n", isCustom = false, dateAdded = "d"
        )
        db.claimDao().upsert(base)
        db.claimDao().upsert(base.copy(label = "L2"))
        assertEquals("L2", db.claimDao().getClaimById("c1")!!.label)
    }

    @Test
    fun claimDao_insertAll_ignoresConflicts() = runBlocking {
        val c = ClaimEntity(
            id = "c1", label = "L", source = "S", evidenceGrade = EvidenceGrade.COUNTS.name,
            brainEvents = null, brainTotal = null, otherEvents = null, otherTotal = null,
            oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
            denominator = "samples", notes = "n", isCustom = false, dateAdded = "d"
        )
        db.claimDao().insertClaim(c)
        db.claimDao().insertAll(listOf(c.copy(label = "DIFF")))
        assertEquals("L", db.claimDao().getClaimById("c1")!!.label)
    }

    @Test
    fun claimDao_updateClaim() = runBlocking {
        val c = ClaimEntity(
            id = "c1", label = "L", source = "S", evidenceGrade = EvidenceGrade.COUNTS.name,
            brainEvents = null, brainTotal = null, otherEvents = null, otherTotal = null,
            oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
            denominator = "samples", notes = "n", isCustom = false, dateAdded = "d"
        )
        db.claimDao().insertClaim(c)
        db.claimDao().updateClaim(c.copy(label = "updated"))
        assertEquals("updated", db.claimDao().getClaimById("c1")!!.label)
    }

    @Test
    fun claimDao_deleteClaim() = runBlocking {
        val c = ClaimEntity(
            id = "c1", label = "L", source = "S", evidenceGrade = EvidenceGrade.COUNTS.name,
            brainEvents = null, brainTotal = null, otherEvents = null, otherTotal = null,
            oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
            denominator = "samples", notes = "n", isCustom = false, dateAdded = "d"
        )
        db.claimDao().insertClaim(c)
        db.claimDao().deleteClaim(c)
        assertNull(db.claimDao().getClaimById("c1"))
    }

    @Test
    fun claimDao_getAllClaimsFlow_ordered() = runBlocking {
        val custom = ClaimEntity(
            id = "z", label = "Z", source = "S", evidenceGrade = EvidenceGrade.COUNTS.name,
            brainEvents = null, brainTotal = null, otherEvents = null, otherTotal = null,
            oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
            denominator = "samples", notes = "n", isCustom = true, dateAdded = "d"
        )
        val normal = ClaimEntity(
            id = "a", label = "A", source = "S", evidenceGrade = EvidenceGrade.COUNTS.name,
            brainEvents = null, brainTotal = null, otherEvents = null, otherTotal = null,
            oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
            denominator = "samples", notes = "n", isCustom = false, dateAdded = "d"
        )
        db.claimDao().insertClaim(custom)
        db.claimDao().insertClaim(normal)
        val list = db.claimDao().getAllClaimsFlow().first()
        assertEquals("a", list[0].id)
        assertEquals("z", list[1].id)
    }

    @Test
    fun claimDao_getAllClaims() = runBlocking {
        db.claimDao().insertClaim(ClaimEntity(
            id = "c1", label = "L", source = "S", evidenceGrade = EvidenceGrade.COUNTS.name,
            brainEvents = null, brainTotal = null, otherEvents = null, otherTotal = null,
            oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
            denominator = "samples", notes = "n", isCustom = false, dateAdded = "d"
        ))
        assertEquals(1, db.claimDao().getAllClaims().size)
    }

    // ---- CapsuleDao ----
    private fun capsule(id: String) = CapsuleEntity(
        id = id, permalink = "p", capsuleVersion = "1", softwareVersion = "1",
        createdUtc = "2026-01-01", sha256 = "h", question = "q", anchorLabel = "a",
        verdict = "SUPPORTS", citableArgument = "arg", plainLanguageVerdict = "pv",
        rNull = 0.5, rSignificance = 0.1, snAssumed = 0.9, rLow = 0.0, rHigh = 1.0,
        observedOr = 2.0, rawJson = "{}", markdown = "md"
    )

    @Test
    fun capsuleDao_crud() = runBlocking {
        db.capsuleDao().insertCapsule(capsule("k1"))
        assertNotNull(db.capsuleDao().getCapsuleById("k1"))
        db.capsuleDao().upsert(capsule("k1").copy(question = "q2"))
        assertEquals("q2", db.capsuleDao().getCapsuleById("k1")!!.question)
        assertEquals(1, db.capsuleDao().getAllCapsules().size)
        db.capsuleDao().deleteCapsule(capsule("k1"))
        assertNull(db.capsuleDao().getCapsuleById("k1"))
    }

    @Test
    fun capsuleDao_getAllCapsulesFlow_orderedDesc() = runBlocking {
        db.capsuleDao().insertCapsule(capsule("k1").copy(createdUtc = "2026-01-01"))
        db.capsuleDao().insertCapsule(capsule("k2").copy(createdUtc = "2026-02-02"))
        val list = db.capsuleDao().getAllCapsulesFlow().first()
        assertEquals("k2", list[0].id)
    }

    // ---- ChallengeDao ----
    private fun challenge(id: String, claimId: String) = ChallengeEntity(
        id = id, claimId = claimId, author = "a", date = "d", notes = "n",
        updatedBrainEvents = 1, updatedBrainTotal = 10, updatedOtherEvents = 2, updatedOtherTotal = 100
    )

    @Test
    fun challengeDao_crud_andFlow() = runBlocking {
        db.challengeDao().insertChallenge(challenge("ch1", "c1"))
        db.challengeDao().upsert(challenge("ch1", "c1").copy(notes = "n2"))
        assertEquals("n2", db.challengeDao().getAllChallenges().first().notes)
        val flow = db.challengeDao().getChallengesForClaimFlow("c1").first()
        assertEquals(1, flow.size)
        val allFlow = db.challengeDao().getAllChallengesFlow().first()
        assertEquals(1, allFlow.size)
    }

    // ---- ProvenanceDao ----
    private fun prov(provider: String) = ProvenanceEntity(
        providerId = provider, endpoint = "e", query = "q", httpStatus = 200,
        retrievedUtc = "t", responseSha256 = "h", licenseClass = "OPEN", elapsedMs = 10
    )

    @Test
    fun provenanceDao_insertLinkDeleteAll() = runBlocking {
        val pk = db.provenanceDao().insertProvenance(prov("p1"))
        assertTrue(pk > 0)
        db.provenanceDao().linkToCapsule(listOf(pk), "cap1")
        val forCapsule = db.provenanceDao().getForCapsule("cap1")
        assertEquals(1, forCapsule.size)
        assertEquals("cap1", forCapsule[0].capsuleId)
        val flow = db.provenanceDao().getAllProvenanceFlow().first()
        assertEquals(1, flow.size)
        db.provenanceDao().deleteAll()
        assertEquals(0, db.provenanceDao().getAllProvenanceFlow().first().size)
    }

    // ---- CachedResponseDao ----
    @Test
    fun cachedResponseDao_crudAndExpiry() = runBlocking {
        val dao = db.cachedResponseDao()
        dao.upsert(CachedResponseEntity("k", "body", "h", System.currentTimeMillis(), 1000L))
        assertNotNull(dao.get("k"))
        dao.delete("k")
        assertNull(dao.get("k"))
        dao.upsert(CachedResponseEntity("k2", "body", "h", System.currentTimeMillis() - 10000, 1000L))
        dao.deleteExpired(System.currentTimeMillis())
        assertNull(dao.get("k2"))
        dao.upsert(CachedResponseEntity("k3", "body", "h", System.currentTimeMillis(), 1000L))
        dao.clear()
        assertNull(dao.get("k3"))
    }

    // ---- ModelRevisionDao ----
    @Test
    fun modelRevisionDao_crud() = runBlocking {
        val dao = db.modelRevisionDao()
        val rev = ModelRevisionEntity(
            id = "r1", parentRevisionId = null, createdAtUtc = "t", parametersJson = "{}",
            metricE = 0.5, promotedBy = "tester", notes = "n"
        )
        dao.insert(rev)
        assertNotNull(dao.getById("r1"))
        assertEquals(1, dao.getAll().size)
        assertNotNull(dao.getAllFlow().first())
        val promoted = dao.getLatestPromoted()
        assertNotNull(promoted)
    }

    @Test
    fun modelRevisionDao_getLatestPromoted_nullWhenUnpromoted() = runBlocking {
        val dao = db.modelRevisionDao()
        dao.insert(ModelRevisionEntity(
            id = "r2", parentRevisionId = null, createdAtUtc = "t", parametersJson = "{}",
            metricE = 0.5, promotedBy = null, notes = null
        ))
        assertNull(dao.getLatestPromoted())
    }

    // ---- LearningAuditDao ----
    @Test
    fun learningAuditDao_crud() = runBlocking {
        val dao = db.learningAuditDao()
        val pk = dao.insert(LearningAuditEntity(
            candidateParamsJson = "{}", baselineParamsJson = "{}", metricTotal = 0.1,
            stabilityPenalty = 0.2, resolutionMiss = 0.3, calibrationError = 0.4,
            parsimony = 0.5, decision = "PROPOSED", approver = null, revisionId = null,
            createdAtUtc = "t", notes = "n"
        ))
        assertTrue(pk > 0)
        assertEquals(1, dao.getAll().size)
        assertNotNull(dao.getAllFlow().first())
    }

    // ---- SyncCursorDao ----
    @Test
    fun syncCursorDao_crud() = runBlocking {
        val dao = db.syncCursorDao()
        assertNull(dao.get("claims"))
        dao.upsert(SyncCursorEntity("claims", "2026-01-01"))
        assertEquals("2026-01-01", dao.get("claims")!!.lastPulledAt)
        dao.clear("claims")
        assertNull(dao.get("claims"))
    }
}

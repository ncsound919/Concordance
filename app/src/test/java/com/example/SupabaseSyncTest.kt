package com.example

import com.example.concordance.data.ClaimEntity
import com.example.concordance.data.ConcordanceDatabase
import com.example.concordance.integration.supabase.InMemorySupabaseSessionStore
import com.example.concordance.integration.supabase.PostgRestClient
import com.example.concordance.integration.supabase.SupabaseAuth
import com.example.concordance.integration.supabase.SupabaseSession
import com.example.concordance.integration.supabase.SupabaseSessionStore
import com.example.concordance.integration.supabase.SupabaseSyncManager
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
import org.robolectric.annotation.Config

/**
 * The Supabase boundary: the PostgREST client, the GoTrue auth client, and
 * the sync manager that moves rows between the server and the local database.
 *
 * The base URL is a compile-time constant, so the harness rewrites the host of
 * every request to the local server and keeps the path - which means the test
 * also pins the path and the headers the production code sends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseSyncTest {

    private lateinit var harness: MockHttpHarness
    private lateinit var store: SupabaseSessionStore
    private lateinit var auth: SupabaseAuth
    private lateinit var rest: PostgRestClient
    private lateinit var db: ConcordanceDatabase

    @Before
    fun setUp() {
        resetIntegrationState()
        harness = MockHttpHarness()
        harness.install()
        store = InMemorySupabaseSessionStore()
        auth = SupabaseAuth(store, harness.client)
        rest = PostgRestClient(auth, harness.client)
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.close()
        harness.shutdown()
        resetIntegrationState()
    }

    private fun signedIn() {
        store.save(
            SupabaseSession(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                expiresAt = System.currentTimeMillis() / 1000 + 3600,
                userId = "user-1",
                email = "jane@example.com"
            )
        )
    }

    private fun manager() = SupabaseSyncManager(auth, rest, db)

    // =======================================================================
    // PostgRestClient
    // =======================================================================

    @Test
    fun `a select asks for the table under rest and sends the session token`() = runBlocking {
        signedIn()
        harness.enqueue(200, """[{"id":"c1"}]""")
        val result = rest.select("claims", "select=*&order=updated_at.asc")
        assertTrue(result.isSuccess)
        val request = harness.takeRequest()
        assertEquals("/rest/v1/claims?select=*&order=updated_at.asc", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
        assertNotNull(request.getHeader("apikey"))
    }

    @Test
    fun `a select without a query has no query string`() = runBlocking {
        signedIn()
        harness.enqueue(200, "[]")
        rest.select("claims")
        assertEquals("/rest/v1/claims", harness.takeRequest().path)
    }

    @Test
    fun `a select without a session fails rather than sending an anonymous request`() = runBlocking {
        val result = rest.select("claims")
        assertTrue(result.isFailure)
        assertEquals(0, harness.requestCount())
    }

    @Test
    fun `a server error is a failed result, not a crash`() = runBlocking {
        signedIn()
        harness.enqueue(500, """{"message":"boom"}""")
        val result = rest.select("claims")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("failed"))
    }

    @Test
    fun `an insert posts the body and asks for the representation back`() = runBlocking {
        signedIn()
        harness.enqueue(201, """[{"id":"c1"}]""")
        val result = rest.insert("claims", """{"label":"L"}""")
        assertTrue(result.isSuccess)
        val request = harness.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/rest/v1/claims", request.path)
        assertEquals("""{"label":"L"}""", request.body.readUtf8())
        assertEquals("return=representation", request.getHeader("Prefer"))
    }

    @Test
    fun `a patch filters on the row and sends the body`() = runBlocking {
        signedIn()
        harness.enqueue(200, """[{"id":"c1"}]""")
        rest.patch("claims", "id", "c1", """{"visibility":"public"}""")
        val request = harness.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/rest/v1/claims?id=eq.c1", request.path)
        assertEquals("""{"visibility":"public"}""", request.body.readUtf8())
    }

    @Test
    fun `an upsert asks the server to merge on a duplicate`() = runBlocking {
        signedIn()
        harness.enqueue(201, """[{"id":"c1"}]""")
        rest.upsert("claims", """{"id":"c1"}""")
        val request = harness.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("resolution=merge-duplicates,return=representation", request.getHeader("Prefer"))
    }

    @Test
    fun `a delete filters on the row`() = runBlocking {
        signedIn()
        harness.enqueue(204, "")
        val result = rest.delete("claims", "id", "c1")
        assertTrue(result.isSuccess)
        val request = harness.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/rest/v1/claims?id=eq.c1", request.path)
    }

    // =======================================================================
    // SupabaseAuth
    // =======================================================================

    @Test
    fun `a sign-in exchanges the password for a session and stores it`() = runBlocking {
        harness.enqueue(200, SESSION_JSON)
        val result = auth.signIn("jane@example.com", "hunter2")
        assertTrue(result.isSuccess)
        val session = result.getOrNull()!!
        assertEquals("access-token", session.accessToken)
        assertEquals("user-1", session.userId)
        assertEquals("jane@example.com", session.email)
        // The session is stored, so the next request can present it.
        assertEquals("access-token", store.load()!!.accessToken)

        val request = harness.takeRequest()
        assertEquals("POST", request.method)
        assertTrue("path was ${request.path}", request.path!!.startsWith("/auth/v1/token"))
        val body = org.json.JSONObject(request.body.readUtf8())
        assertEquals("jane@example.com", body.getString("email"))
    }

    @Test
    fun `a rejected sign-in reports the reason and stores nothing`() = runBlocking {
        harness.enqueue(400, """{"msg":"Invalid login credentials"}""")
        val result = auth.signIn("jane@example.com", "wrong")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Invalid login credentials"))
        assertNull(store.load())
    }

    @Test
    fun `a sign-up that needs email verification reports that rather than a session`() = runBlocking {
        harness.enqueue(200, """{"id":"user-1","email":"jane@example.com"}""")
        val result = auth.signUp("jane@example.com", "hunter2", "Jane")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("verify your email"))
        assertNull(store.load())
    }

    @Test
    fun `a sign-up that returns a session stores it`() = runBlocking {
        harness.enqueue(200, SESSION_JSON)
        val result = auth.signUp("jane@example.com", "hunter2", "Jane")
        assertTrue(result.isSuccess)
        assertNotNull(store.load())
    }

    @Test
    fun `a refresh exchanges the refresh token and keeps the email`() = runBlocking {
        signedIn()
        // The refresh response has no user block, so the email must be carried over.
        harness.enqueue(
            200,
            """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600}"""
        )
        val result = auth.refresh()
        assertTrue(result.isSuccess)
        val session = result.getOrNull()!!
        assertEquals("new-access", session.accessToken)
        assertEquals("jane@example.com", session.email)
        assertTrue(harness.takeRequest().path!!.contains("grant_type=refresh_token"))
    }

    @Test
    fun `a refresh without a session fails`() = runBlocking {
        val result = auth.refresh()
        assertTrue(result.isFailure)
        assertEquals(0, harness.requestCount())
    }

    @Test
    fun `the current user is fetched with the access token`() = runBlocking {
        signedIn()
        harness.enqueue(200, """{"id":"user-1","email":"jane@example.com"}""")
        val result = auth.currentUser()
        assertTrue(result.isSuccess)
        assertEquals("user-1", result.getOrNull()!!.getString("id"))
        val request = harness.takeRequest()
        assertTrue("path was ${request.path}", request.path!!.startsWith("/auth/v1/user"))
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
    }

    @Test
    fun `signing out clears the stored session`() {
        signedIn()
        auth.signOut()
        assertNull(store.load())
    }

    @Test
    fun `an expired session is refreshed before it is used`() = runBlocking {
        store.save(
            SupabaseSession(
                accessToken = "stale",
                refreshToken = "refresh-token",
                expiresAt = System.currentTimeMillis() / 1000 - 10,
                userId = "user-1",
                email = "jane@example.com"
            )
        )
        harness.enqueue(200, SESSION_JSON)
        val token = auth.accessToken()
        assertEquals("access-token", token)
    }

    @Test
    fun `a valid session is used without a refresh`() = runBlocking {
        signedIn()
        val token = auth.accessToken()
        assertEquals("access-token", token)
        assertEquals(0, harness.requestCount())
    }

    // =======================================================================
    // SupabaseSyncManager - pulling
    // =======================================================================

    @Test
    fun `pulling claims stores them locally and records the cursor`() = runBlocking {
        signedIn()
        harness.enqueue(200, CLAIMS_JSON)
        val result = manager().pullClaims()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        val stored = db.claimDao().getClaimById("c1")
        assertNotNull(stored)
        assertEquals("A claim", stored!!.label)
        assertEquals("synced", stored.syncStatus)

        val cursor = db.syncCursorDao().get("claims")
        assertNotNull(cursor)
        assertEquals("2026-10-19T00:00:00Z", cursor!!.lastPulledAt)
    }

    @Test
    fun `the second pull asks only for what changed`() = runBlocking {
        signedIn()
        harness.enqueue(200, CLAIMS_JSON)
        manager().pullClaims()
        harness.takeRequest()

        harness.enqueue(200, "[]")
        manager().pullClaims()
        val second = harness.takeRequest()
        assertTrue(
            "path was ${second.path}",
            second.path!!.contains("updated_at=gte.2026-10-19T00:00:00Z")
        )
    }

    @Test
    fun `pulling capsules stores them locally`() = runBlocking {
        signedIn()
        harness.enqueue(200, CAPSULES_JSON)
        val result = manager().pullCapsules()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        assertEquals("k1", db.capsuleDao().getCapsuleById("k1")!!.id)
    }

    @Test
    fun `pulling challenges stores them locally`() = runBlocking {
        signedIn()
        harness.enqueue(200, CHALLENGES_JSON)
        val result = manager().pullChallenges()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        assertEquals("ch1", db.challengeDao().getAllChallenges().first().id)
    }

    @Test
    fun `a pull that fails is reported and stores nothing`() = runBlocking {
        signedIn()
        harness.enqueue(500, "{}")
        val result = manager().pullClaims()
        assertTrue(result.isFailure)
        assertTrue(db.claimDao().getAllClaims().isEmpty())
    }

    @Test
    fun `pulling everything sums the three tables`() = runBlocking {
        signedIn()
        harness.enqueue(200, CLAIMS_JSON)
        harness.enqueue(200, CAPSULES_JSON)
        harness.enqueue(200, CHALLENGES_JSON)
        val result = manager().pullAll()
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull())
    }

    // =======================================================================
    // SupabaseSyncManager - conflicts
    // =======================================================================

    @Test
    fun `a local unsynced row that the server moved becomes a conflict`() = runBlocking {
        signedIn()
        db.claimDao().upsert(
            localClaim(id = "c1", syncStatus = "local", serverUpdatedAt = 1_000L)
        )
        harness.enqueue(200, CLAIMS_JSON)
        val sync = manager()
        val result = sync.pullClaims()
        assertTrue(result.isSuccess)
        assertEquals(1, sync.conflicts.size)
        assertEquals("claim", sync.conflicts.first().entityType)
        assertEquals("c1", sync.conflicts.first().entityId)
        assertEquals("conflict", db.claimDao().getClaimById("c1")!!.syncStatus)
    }

    @Test
    fun `the conflict callback is told about a conflict`() = runBlocking {
        signedIn()
        db.claimDao().upsert(
            localClaim(id = "c1", syncStatus = "local", serverUpdatedAt = 1_000L)
        )
        harness.enqueue(200, CLAIMS_JSON)
        val sync = manager()
        var reported: String? = null
        sync.onConflict = { reported = it.entityId }
        sync.pullClaims()
        assertEquals("c1", reported)
    }

    @Test
    fun `a synced local row is overwritten by the server without a conflict`() = runBlocking {
        signedIn()
        db.claimDao().upsert(
            localClaim(id = "c1", syncStatus = "synced", serverUpdatedAt = 1_000L)
        )
        harness.enqueue(200, CLAIMS_JSON)
        val sync = manager()
        sync.pullClaims()
        assertTrue(sync.conflicts.isEmpty())
        assertEquals("A claim", db.claimDao().getClaimById("c1")!!.label)
    }

    // =======================================================================
    // SupabaseSyncManager - resolving
    // =======================================================================

    @Test
    fun `keeping the local copy marks the row pending`() = runBlocking {
        signedIn()
        db.claimDao().upsert(localClaim(id = "c1", syncStatus = "conflict"))
        val result = manager().resolveConflict("claim", "c1", "keep_local")
        assertTrue(result.isSuccess)
        assertEquals("pending", db.claimDao().getClaimById("c1")!!.syncStatus)
    }

    @Test
    fun `keeping the server copy re-pulls the row and marks it synced`() = runBlocking {
        signedIn()
        db.claimDao().upsert(localClaim(id = "c1", syncStatus = "conflict", label = "Local"))
        harness.enqueue(200, CLAIMS_JSON)
        val result = manager().resolveConflict("claim", "c1", "keep_server")
        assertTrue(result.isSuccess)
        val stored = db.claimDao().getClaimById("c1")!!
        assertEquals("A claim", stored.label)
        assertEquals("synced", stored.syncStatus)
    }

    @Test
    fun `accepting the agent proposal keeps the server version`() = runBlocking {
        signedIn()
        db.claimDao().upsert(localClaim(id = "c1", syncStatus = "conflict", label = "Local"))
        harness.enqueue(200, CLAIMS_JSON)
        manager().resolveConflict("claim", "c1", "accept_agent")
        assertEquals("A claim", db.claimDao().getClaimById("c1")!!.label)
    }

    @Test
    fun `opening a challenge changes nothing`() = runBlocking {
        signedIn()
        db.claimDao().upsert(localClaim(id = "c1", syncStatus = "conflict", label = "Local"))
        manager().resolveConflict("claim", "c1", "open_challenge")
        assertEquals("Local", db.claimDao().getClaimById("c1")!!.label)
    }

    @Test
    fun `resolving a row that does not exist is a no-op`() = runBlocking {
        signedIn()
        val result = manager().resolveConflict("claim", "absent", "keep_local")
        assertTrue(result.isSuccess)
    }

    // =======================================================================
    // SupabaseSyncManager - pushing
    // =======================================================================

    @Test
    fun `pushing sends the dirty claims and marks them synced`() = runBlocking {
        signedIn()
        db.claimDao().upsert(localClaim(id = "c1", syncStatus = "local"))
        harness.enqueue(201, """[{"id":"c1"}]""")
        val result = manager().pushClaims()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        val stored = db.claimDao().getClaimById("c1")!!
        assertEquals("synced", stored.syncStatus)
        assertEquals(1, stored.version)

        val request = harness.takeRequest()
        assertEquals("POST", request.method)
        val body = org.json.JSONObject(request.body.readUtf8())
        assertEquals("c1", body.getString("id"))
        assertEquals("user-1", body.getString("owner_id"))
        assertEquals("aggregate", body.getString("data_class"))
    }

    @Test
    fun `a synced claim is not pushed again`() = runBlocking {
        signedIn()
        db.claimDao().upsert(localClaim(id = "c1", syncStatus = "synced"))
        val result = manager().pushClaims()
        assertEquals(0, result.getOrNull())
        assertEquals(0, harness.requestCount())
    }

    @Test
    fun `pushing without a session fails`() = runBlocking {
        db.claimDao().upsert(localClaim(id = "c1", syncStatus = "local"))
        val result = manager().pushClaims()
        assertTrue(result.isFailure)
        assertEquals(0, harness.requestCount())
    }

    @Test
    fun `a deleted claim is deleted on the server and removed locally`() = runBlocking {
        signedIn()
        db.claimDao().upsert(localClaim(id = "c1", syncStatus = "synced", deletedAt = 42L))
        harness.enqueue(204, "")
        val result = manager().pushClaims()
        assertTrue(result.isSuccess)
        assertNull(db.claimDao().getClaimById("c1"))
        val request = harness.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/rest/v1/claims?id=eq.c1", request.path)
    }

    @Test
    fun `pushing capsules sends them and marks them synced`() = runBlocking {
        signedIn()
        db.capsuleDao().upsert(localCapsule(id = "k1", syncStatus = "local"))
        harness.enqueue(201, """[{"id":"k1"}]""")
        val result = manager().pushCapsules()
        assertEquals(1, result.getOrNull())
        assertEquals("synced", db.capsuleDao().getCapsuleById("k1")!!.syncStatus)
    }

    @Test
    fun `pushing challenges sends them and marks them synced`() = runBlocking {
        signedIn()
        db.challengeDao().upsert(localChallenge(id = "ch1", syncStatus = "local"))
        harness.enqueue(201, """[{"id":"ch1"}]""")
        val result = manager().pushChallenges()
        assertEquals(1, result.getOrNull())
        assertEquals("synced", db.challengeDao().getAllChallenges().first().syncStatus)
    }

    @Test
    fun `publishing a claim makes it public and records a publication`() = runBlocking {
        signedIn()
        harness.enqueue(200, "[]")
        harness.enqueue(201, """[{"id":"pub1"}]""")
        val result = manager().publishClaim("c1")
        assertTrue(result.isSuccess)

        val patch = harness.takeRequest()
        assertEquals("PATCH", patch.method)
        assertTrue(patch.body.readUtf8().contains("\"visibility\":\"public\""))

        val insert = harness.takeRequest()
        assertEquals("POST", insert.method)
        assertEquals("/rest/v1/publications", insert.path)
        val body = org.json.JSONObject(insert.body.readUtf8())
        assertEquals("claim", body.getString("artifact_type"))
        assertEquals("c1", body.getString("artifact_id"))
        assertEquals("user-1", body.getString("publisher_id"))
    }

    @Test
    fun `the teams query filters by the signed-in user`() = runBlocking {
        signedIn()
        harness.enqueue(200, "[]")
        manager().myTeams()
        assertTrue(harness.takeRequest().path!!.contains("team_memberships.user_id=eq.user-1"))
    }

    // =======================================================================
    // Fixtures
    // =======================================================================

    private fun localClaim(
        id: String,
        syncStatus: String,
        serverUpdatedAt: Long? = null,
        label: String = "Local",
        deletedAt: Long? = null
    ) = ClaimEntity(
        id = id,
        label = label,
        source = "source",
        evidenceGrade = "COUNTS",
        brainEvents = 1,
        brainTotal = 10,
        otherEvents = 2,
        otherTotal = 100,
        oddsRatio = null,
        orCiLow = null,
        orCiHigh = null,
        referencePrevalence = 0.5,
        denominator = "samples",
        notes = "n",
        isCustom = false,
        dateAdded = "2026-10-18",
        syncStatus = syncStatus,
        serverUpdatedAt = serverUpdatedAt,
        deletedAt = deletedAt
    )

    private fun localCapsule(id: String, syncStatus: String) = com.example.concordance.data.CapsuleEntity(
        id = id,
        permalink = "https://example.org/$id",
        capsuleVersion = "1",
        softwareVersion = "0.1.0",
        createdUtc = "2026-10-19T00:00:00Z",
        sha256 = "a".repeat(64),
        question = "Q",
        anchorLabel = "A",
        verdict = "supported",
        citableArgument = "arg",
        plainLanguageVerdict = "plain",
        rNull = 0.2,
        rSignificance = 0.3,
        snAssumed = 0.9,
        rLow = 0.3,
        rHigh = 0.9,
        observedOr = 0.68,
        rawJson = "{}",
        markdown = "#",
        syncStatus = syncStatus
    )

    private fun localChallenge(id: String, syncStatus: String) = com.example.concordance.data.ChallengeEntity(
        id = id,
        claimId = "c1",
        author = "jane",
        date = "2026-10-19",
        notes = "n",
        updatedBrainEvents = 1,
        updatedBrainTotal = 10,
        updatedOtherEvents = 2,
        updatedOtherTotal = 100,
        syncStatus = syncStatus
    )

    private val SESSION_JSON = """
        {
          "access_token": "access-token",
          "refresh_token": "refresh-token",
          "expires_in": 3600,
          "user": { "id": "user-1", "email": "jane@example.com" }
        }
    """.trimIndent()

    private val CLAIMS_JSON = """
        [
          {
            "id": "c1",
            "label": "A claim",
            "source": "source",
            "evidence_grade": "COUNTS",
            "brain_events": 1,
            "brain_total": 10,
            "other_events": 2,
            "other_total": 100,
            "denominator": "samples",
            "notes": "n",
            "is_custom": false,
            "date_added": "2026-10-18",
            "updated_at": "2026-10-19T00:00:00Z",
            "version": 3
          }
        ]
    """.trimIndent()

    private val CAPSULES_JSON = """
        [
          {
            "id": "k1",
            "permalink": "https://example.org/k1",
            "capsule_version": "1",
            "software_version": "0.1.0",
            "created_utc": "2026-10-19T00:00:00Z",
            "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "question": "Q",
            "anchor_label": "A",
            "verdict": "supported",
            "citable_argument": "arg",
            "plain_language_verdict": "plain",
            "r_null": 0.2,
            "r_significance": 0.3,
            "sn_assumed": 0.9,
            "r_low": 0.3,
            "r_high": 0.9,
            "observed_or": 0.68,
            "raw_json": "{}",
            "markdown": "#",
            "updated_at": "2026-10-19T00:00:00Z"
          }
        ]
    """.trimIndent()

    private val CHALLENGES_JSON = """
        [
          {
            "id": "ch1",
            "claim_id": "c1",
            "author_handle": "jane",
            "date": "2026-10-19",
            "notes": "n",
            "updated_brain_events": 1,
            "updated_brain_total": 10,
            "updated_other_events": 2,
            "updated_other_total": 100,
            "created_at": "2026-10-19T00:00:00Z"
          }
        ]
    """.trimIndent()
}

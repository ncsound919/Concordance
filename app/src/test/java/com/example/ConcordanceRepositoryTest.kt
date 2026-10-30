package com.example

import com.example.concordance.data.ConcordanceRepository
import com.example.concordance.model.EvidenceGrade
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConcordanceRepositoryTest {

    private lateinit var db: com.example.concordance.data.ConcordanceDatabase
    private lateinit var repo: ConcordanceRepository

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repo = ConcordanceRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun makeAnchor(id: String, label: String = "Test claim"): com.example.concordance.model.Anchor =
        com.example.concordance.model.Anchor(
            id = id,
            label = label,
            source = "unit test",
            evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = 1, brainTotal = 10,
            otherEvents = 2, otherTotal = 100,
            oddsRatio = null, orCiLow = null, orCiHigh = null,
            referencePrevalence = 0.5,
            denominator = "samples",
            notes = "n",
            isCustom = true,
            dateAdded = "2026-01-01"
        )

    @Test
    fun seedDefaultsIfNeeded_populates_the_default_anchors() = runBlocking {
        repo.seedDefaultsIfNeeded()
        val claims = repo.getAllClaims()
        assertTrue(claims.isNotEmpty())
        assertTrue(claims.any { it.id == "giannoudis2024_pooled" })
    }

    @Test
    fun seedDefaultsIfNeeded_is_idempotent() = runBlocking {
        repo.seedDefaultsIfNeeded()
        val first = repo.getAllClaims().size
        repo.seedDefaultsIfNeeded()
        val second = repo.getAllClaims().size
        assertEquals(first, second)
    }

    @Test
    fun addClaim_round_trips_through_getAllClaims() = runBlocking {
        repo.addClaim(makeAnchor("test-claim-1"))
        val found = repo.getAllClaims().firstOrNull { it.id == "test-claim-1" }
        assertNotNull(found)
        assertEquals("Test claim", found!!.label)
    }

    @Test
    fun getClaimById_returns_null_for_missing() = runBlocking {
        assertEquals(null, repo.getClaimById("does-not-exist"))
    }

    @Test
    fun getClaimById_finds_added_claim() = runBlocking {
        repo.addClaim(makeAnchor("find-me", "find me"))
        val found = repo.getClaimById("find-me")
        assertNotNull(found)
        assertEquals("find me", found!!.label)
    }

}

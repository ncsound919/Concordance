package com.example

import androidx.room.Room
import com.example.concordance.data.ConcordanceDatabase
import com.example.concordance.data.ConcordanceRepository
import com.example.concordance.learning.ModelParameters
import com.example.concordance.learning.ModelRevision
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ModelRevisionPersistenceTest {

    @Test
    fun revisionRoundTripsThroughRoom() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, ConcordanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = ConcordanceRepository(db)

            // Nothing promoted yet.
            assertNull(repo.getLatestPromotedRevision())

            val rev = ModelRevision(
                id = "rev-1",
                parentRevisionId = null,
                createdAtUtc = "2026-10-18T00:00:00Z",
                parameters = ModelParameters(sn = 0.85, referencePrevalence = 0.18),
                metricE = 0.123,
                promotedBy = "tester",
                notes = "dry-run round 1"
            )
            repo.saveModelRevision(rev)

            val latest = repo.getLatestPromotedRevision()
            assertNotNull(latest)
            assertEquals("rev-1", latest!!.id)
            assertEquals("tester", latest.promotedBy)
            assertEquals(0.85, latest.parameters.sn, 1e-9)
            assertEquals(0.18, latest.parameters.referencePrevalence, 1e-9)
            assertEquals(0.123, latest.metricE, 1e-9)
        } finally {
            db.close()
        }
    }

    @Test
    fun unpromotedRevisionIsNotReturnedAsLatestPromoted() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, ConcordanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = ConcordanceRepository(db)
            repo.saveModelRevision(
                ModelRevision(
                    id = "rev-proposal",
                    parentRevisionId = null,
                    createdAtUtc = "2026-10-18T00:00:00Z",
                    parameters = ModelParameters(),
                    metricE = 0.5,
                    promotedBy = null
                )
            )
            assertNull("unpromoted proposals must never be treated as current", repo.getLatestPromotedRevision())
        } finally {
            db.close()
        }
    }
}

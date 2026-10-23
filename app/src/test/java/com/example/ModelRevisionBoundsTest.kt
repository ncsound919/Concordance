package com.example

import androidx.room.Room
import com.example.concordance.data.ConcordanceDatabase
import com.example.concordance.data.ConcordanceRepository
import com.example.concordance.learning.ModelParameters
import com.example.concordance.learning.ModelRevision
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ModelRevisionBoundsTest {

    @Test
    fun repositoryRefusesOutOfBoundsRevisionAtTheWriteBoundary() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, ConcordanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = ConcordanceRepository(db)

            // sn = 5.0 is far outside the declared bounds.
            val bad = ModelRevision(
                id = "rev-bad",
                parentRevisionId = null,
                createdAtUtc = "2026-10-19T00:00:00Z",
                parameters = ModelParameters(sn = 5.0),
                metricE = 0.01,
                promotedBy = "buggy-caller"
            )
            assertFalse("write boundary must refuse out-of-bounds params", repo.saveModelRevision(bad))
            assertNull(repo.getLatestPromotedRevision())
            assertEquals(0, repo.getAllModelRevisionsFlow().first().size)

            // A valid revision is accepted.
            val good = ModelRevision(
                id = "rev-good",
                parentRevisionId = null,
                createdAtUtc = "2026-10-19T00:01:00Z",
                parameters = ModelParameters(sn = 0.85),
                metricE = 0.01,
                promotedBy = "dr-who"
            )
            assertTrue(repo.saveModelRevision(good))
            assertEquals("rev-good", repo.getLatestPromotedRevision()?.id)
        } finally {
            db.close()
        }
    }

    @Test
    fun repositoryRefusesOutOfBoundsReferencePrevalence() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, ConcordanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = ConcordanceRepository(db)
            val bad = ModelRevision(
                id = "rev-bad2",
                parentRevisionId = null,
                createdAtUtc = "2026-10-19T00:00:00Z",
                parameters = ModelParameters(referencePrevalence = 1.5),
                metricE = 0.01,
                promotedBy = "dr-who"
            )
            assertFalse(repo.saveModelRevision(bad))
        } finally {
            db.close()
        }
    }
}

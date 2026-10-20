package com.example

import androidx.room.Room
import com.example.concordance.data.ConcordanceDatabase
import com.example.concordance.data.ConcordanceRepository
import com.example.concordance.learning.EvaluationResult
import com.example.concordance.learning.LearningAudit
import com.example.concordance.learning.LearningDecision
import com.example.concordance.learning.ModelParameters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LearningAuditPersistenceTest {

    private fun result(total: Double) = EvaluationResult(
        verdictStabilityPenalty = 0.1,
        challengeResolutionMissRate = 0.2,
        calibrationError = 0.05,
        parsimony = 0.03,
        total = total,
        casesEvaluated = 0
    )

    @Test
    fun everyDecisionRoundTripsAndRejectionsAreKept() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, ConcordanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = ConcordanceRepository(db)

            repo.saveLearningAudit(
                LearningAudit(
                    candidate = ModelParameters(sn = 0.80, referencePrevalence = 0.18),
                    baseline = ModelParameters(),
                    result = result(0.12),
                    decision = LearningDecision.REJECTED,
                    approver = null,
                    revisionId = null,
                    createdAtUtc = "2026-10-19T00:00:00Z",
                    notes = "Refused: no metric improvement."
                )
            )
            repo.saveLearningAudit(
                LearningAudit(
                    candidate = ModelParameters(sn = 0.85, referencePrevalence = 0.20),
                    baseline = ModelParameters(),
                    result = result(0.08),
                    decision = LearningDecision.PROMOTED,
                    approver = "dr-who",
                    revisionId = "rev-7",
                    createdAtUtc = "2026-10-19T01:00:00Z",
                    notes = "Promoted."
                )
            )

            val all = repo.getAllLearningAuditFlow().first()

            assertEquals("both accepted and rejected decisions must persist", 2, all.size)

            // Newest first (pk DESC).
            val promoted = all[0]
            assertEquals(LearningDecision.PROMOTED, promoted.decision)
            assertEquals("dr-who", promoted.approver)
            assertEquals("rev-7", promoted.revisionId)
            assertEquals(0.85, promoted.candidate.sn, 1e-9)
            assertEquals(0.08, promoted.result.total, 1e-9)

            val rejected = all[1]
            assertEquals(LearningDecision.REJECTED, rejected.decision)
            assertEquals(null, rejected.approver)
            assertEquals(0.80, rejected.candidate.sn, 1e-9)
        } finally {
            db.close()
        }
    }
}

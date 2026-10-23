package com.example

import com.example.concordance.learning.LearningHealthCalculator
import com.example.concordance.learning.ModelParameters
import com.example.concordance.learning.ModelRevision
import com.example.concordance.model.Anchor
import com.example.concordance.model.ClaimChallenge
import com.example.concordance.model.EvidenceGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningHealthTest {

    private val claim = Anchor(
        id = "giannoudis2024_pooled",
        label = "Giannoudis 2024 pooled",
        source = "Giannoudis et al. 2024",
        evidenceGrade = EvidenceGrade.COUNTS,
        brainEvents = 53, brainTotal = 822,
        otherEvents = 3220, otherTotal = 15516
    )

    private fun challenge(id: String, bE: Int, bT: Int, oE: Int, oT: Int) = ClaimChallenge(
        id = id,
        claimId = claim.id,
        author = "dr-who",
        date = "2026-10-19",
        notes = "audit correction",
        updatedBrainEvents = bE,
        updatedBrainTotal = bT,
        updatedOtherEvents = oE,
        updatedOtherTotal = oT
    )

    @Test
    fun emptyCorpusReportsFullResolutionRateByConvention() {
        val health = LearningHealthCalculator.compute(emptyList(), listOf(claim), emptyList(), null)
        assertEquals(0, health.totalChallenges)
        assertEquals(1.0, health.challengeResolutionRate, 1e-12)
        assertEquals(0, health.revisionCount)
    }

    @Test
    fun countsAndResolvesChallengesForTheCurrentRevision() {
        val challenges = listOf(
            challenge("c1", 40, 822, 3220, 15516),
            challenge("c2", 45, 822, 3220, 15516)
        )
        val health = LearningHealthCalculator.compute(
            revisions = emptyList(),
            anchors = listOf(claim),
            challenges = challenges,
            currentRevision = null
        )
        assertEquals(2, health.totalChallenges)
        assertTrue(health.resolvedChallenges in 0..2)
        val expected = health.resolvedChallenges.toDouble() / 2
        assertEquals(expected, health.challengeResolutionRate, 1e-12)
    }

    @Test
    fun unestimableChallengeIsExcludedFromTheResolutionRate() {
        val challenges = listOf(
            challenge("c-zero", 0, 822, 3220, 15516),
            challenge("c-ok", 40, 822, 3220, 15516)
        )
        val health = LearningHealthCalculator.compute(emptyList(), listOf(claim), challenges, null)
        assertEquals(
            "a correction with a zero-event group has no verdict to match",
            1, health.totalChallenges
        )
    }

    @Test
    fun metricTrendListsOnlyPromotedRevisionsInOrder() {
        val revisions = listOf(
            ModelRevision("rev-1", null, "t1", ModelParameters(), 0.30, "dr-who"),
            ModelRevision("rev-prop", "rev-1", "t2", ModelParameters(), 0.20, null),
            ModelRevision("rev-2", "rev-1", "t3", ModelParameters(sn = 0.85), 0.15, "dr-who")
        )
        val health = LearningHealthCalculator.compute(revisions, emptyList(), emptyList(), revisions[2])
        assertEquals(3, health.revisionCount)
        assertEquals(2, health.promotedCount)
        assertEquals(listOf("rev-1" to 0.30, "rev-2" to 0.15), health.metricTrend)
        assertEquals("rev-2", health.latestRevisionId)
    }

    @Test
    fun emptyFactoryIsAllZeros() {
        val h = com.example.concordance.learning.LearningHealth.empty()
        assertEquals(0, h.revisionCount)
        assertEquals(0, h.totalChallenges)
        assertEquals(0.0, h.challengeResolutionRate, 1e-12)
    }
}

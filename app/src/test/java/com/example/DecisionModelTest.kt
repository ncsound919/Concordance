package com.example

import com.example.concordance.local.DecisionAnswer
import com.example.concordance.local.DecisionBackend
import com.example.concordance.local.DecisionModel
import com.example.concordance.local.DecisionType
import com.example.concordance.local.ExtractionState
import com.example.concordance.local.ROUTE_ACCEPT
import com.example.concordance.local.ROUTE_REVIEW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The review gate must be boring: rules always answer, the on-device model can
 * only replace them when its reply is complete, in-range, and confident. These
 * tests cover the rules, the flat-JSON parser, and the confidence gate; no
 * server or Android framework is involved.
 */
class DecisionModelTest {

    private val abstractText =
        "ESR1 Y537S was associated with more brain metastases than other variants " +
            "(12/531 vs 3/498; OR 4.1, 95% CI 1.25-13.7)."

    private fun state(
        abstract: String = abstractText,
        gene: String? = "ESR1",
        brainEvents: Int? = 12,
        brainTotal: Int? = 531,
        otherEvents: Int? = 3,
        otherTotal: Int? = 498,
        oddsRatio: Double? = 4.1,
        confidence: Double = 0.86
    ) = ExtractionState(
        abstractText = abstract,
        gene = gene,
        variant = "Y537S",
        brainEvents = brainEvents,
        brainTotal = brainTotal,
        otherEvents = otherEvents,
        otherTotal = otherTotal,
        oddsRatio = oddsRatio,
        reportedConfidence = confidence
    )

    @Test
    fun `consistent counts with a plausible OR are auto-accepted`() {
        val answers = DecisionModel.ruleAnswers(state())
        assertEquals("counts", answers["tier"]?.choice)
        assertEquals(ROUTE_ACCEPT, answers["route"]?.choice)
        assertTrue((answers["support"]?.noul ?: 0.0) >= 0.8)
    }

    @Test
    fun `events above the total are downgraded and sent to review`() {
        val answers = DecisionModel.ruleAnswers(state(brainEvents = 600))
        assertEquals("summary", answers["tier"]?.choice)
        assertEquals(ROUTE_REVIEW, answers["route"]?.choice)
    }

    @Test
    fun `a gene absent from the abstract lowers support and requires review`() {
        val answers = DecisionModel.ruleAnswers(state(gene = "AKT1"))
        assertTrue((answers["support"]?.noul ?: 1.0) < 0.65)
        assertEquals(ROUTE_REVIEW, answers["route"]?.choice)
    }

    @Test
    fun `no counts and no effect size is abstract_only`() {
        val answers = DecisionModel.ruleAnswers(
            state(brainEvents = null, brainTotal = null, otherEvents = null, otherTotal = null, oddsRatio = null)
        )
        assertEquals("abstract_only", answers["tier"]?.choice)
        assertEquals(ROUTE_REVIEW, answers["route"]?.choice)
    }

    @Test
    fun `parses a clean flat decision`() {
        val parsed = DecisionModel.parseFlatDecision(
            """{"support": 0.82, "tier": "counts", "route": "accept", "confidence": 0.77}"""
        )!!
        assertEquals("counts", parsed["tier"]?.choice)
        assertEquals(ROUTE_ACCEPT, parsed["route"]?.choice)
        assertEquals(0.82, parsed["support"]?.noul!!, 1e-9)
        assertEquals(0.77, parsed["route"]?.confidence!!, 1e-9)
    }

    @Test
    fun `tolerates fences and prose around the decision`() {
        val raw = "Sure:\n```json\n{\"support\": 0.4, \"tier\": \"summary\", " +
            "\"route\": \"review\", \"confidence\": 0.66}\n```"
        val parsed = DecisionModel.parseFlatDecision(raw)!!
        assertEquals(ROUTE_REVIEW, parsed["route"]?.choice)
    }

    @Test
    fun `rejects unknown keys and out-of-range numbers`() {
        assertTrue(
            DecisionModel.parseFlatDecision(
                """{"support": 0.8, "tier": "guess", "route": "accept", "confidence": 0.9}"""
            ) == null
        )
        assertTrue(
            DecisionModel.parseFlatDecision(
                """{"support": 1.8, "tier": "counts", "route": "accept", "confidence": 0.9}"""
            ) == null
        )
        assertTrue(
            DecisionModel.parseFlatDecision(
                """{"support": 0.8, "tier": "counts", "route": "accept"}"""
            ) == null
        )
        assertTrue(DecisionModel.parseFlatDecision("no json here") == null)
    }

    @Test
    fun `a confident valid remote answer wins`() {
        val rules = DecisionModel.ruleAnswers(state(gene = "AKT1"))
        val remote = remoteAnswer(route = ROUTE_ACCEPT, confidence = 0.8)
        val review = DecisionModel.mergeWithRemote(rules, remote)
        assertEquals(DecisionBackend.ON_DEVICE_LLM, review.backend)
        assertEquals(ROUTE_ACCEPT, review.route)
        assertNull(review.fallbackReason)
    }

    @Test
    fun `a low-confidence remote answer falls back with a reason`() {
        val rules = DecisionModel.ruleAnswers(state())
        val remote = remoteAnswer(route = ROUTE_REVIEW, confidence = 0.2)
        val review = DecisionModel.mergeWithRemote(rules, remote)
        assertEquals(DecisionBackend.RULES, review.backend)
        assertEquals(ROUTE_ACCEPT, review.route)
        assertEquals("low_confidence", review.fallbackReason)
    }

    @Test
    fun `the reason sentence is written by the app not the model`() {
        val accept = DecisionModel.reasonFor(ROUTE_ACCEPT, "counts", 0.85)
        assertTrue(accept.contains("support 85%"))
        assertTrue(accept.contains("counts"))
        val review = DecisionModel.reasonFor(ROUTE_REVIEW, "abstract_only", 0.5)
        assertTrue(review.contains("Check the abstract"))
    }

    private fun remoteAnswer(route: String, confidence: Double): Map<String, DecisionAnswer> = mapOf(
        "tier" to DecisionAnswer("tier", DecisionType.CHOICE, choice = "counts", confidence = confidence),
        "route" to DecisionAnswer("route", DecisionType.CHOICE, choice = route, confidence = confidence),
        "support" to DecisionAnswer("support", DecisionType.NOUL, noul = 0.8)
    )
}

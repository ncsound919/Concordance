package com.example

import com.example.concordance.engine.CapsuleEngine
import com.example.concordance.engine.StatisticalEngine
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The capsule digest is the artifact's only integrity guarantee, so it must cover every field
 * a reader can see. v2 seals the whole document; v1 sealed only inputs + results.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CapsuleIntegrityTest {

    private val anchor = Anchor(
        id = "giannoudis2024_pooled",
        label = "Giannoudis 2024 pooled (Foundation Medicine)",
        source = "Giannoudis 2024",
        evidenceGrade = EvidenceGrade.COUNTS,
        brainEvents = 53,
        brainTotal = 822,
        otherEvents = 3220,
        otherTotal = 15516
    )

    private fun capsuleJson(): String =
        CapsuleEngine.buildCapsule(StatisticalEngine.adjudicate(anchor, 0.90, 0.80, 1.00)).rawJson

    @Test
    fun capsuleIsVersionTwo() {
        val root = JSONObject(capsuleJson())
        assertEquals("2", root.getString("capsule_version"))
    }

    @Test
    fun pristineCapsuleVerifies() {
        val raw = capsuleJson()
        val declared = JSONObject(raw).getString("sha256")
        val (valid, message, computed) = CapsuleEngine.verifyJson(raw)
        assertTrue(message, valid)
        assertEquals(declared, computed)
    }

    @Test
    fun refusedAnchorStillProducesAVerifiableCapsule() {
        val zeroEvents = Anchor(
            id = "zero_event",
            label = "zero event group",
            source = "test",
            evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = 0,
            brainTotal = 40,
            otherEvents = 120,
            otherTotal = 1000
        )
        val capsule = CapsuleEngine.buildCapsule(StatisticalEngine.adjudicate(zeroEvents, 0.90, 0.31, 0.38))
        assertFalse("no NaN may be serialised into a capsule", capsule.rawJson.contains("NaN", true))
        assertTrue(capsule.rawJson.contains("withheld"))
        val (valid, message, _) = CapsuleEngine.verifyJson(capsule.rawJson)
        assertTrue(message, valid)
    }

    @Test
    fun tamperingTheVerdictProseIsDetected() {
        val raw = capsuleJson()
        val tampered = raw.replace(
            "The reported brain metastasis depletion survives detection correction across the tested range",
            "The reported brain metastasis depletion is proven biology"
        )
        assertNotEquals(raw, tampered)
        val (valid, _, _) = CapsuleEngine.verifyJson(tampered)
        assertFalse("verdict prose must be sealed", valid)
    }

    @Test
    fun tamperingTheTimestampIsDetected() {
        val root = JSONObject(capsuleJson())
        val tampered = root.apply { put("created_utc", "1999-01-01T00:00:00Z") }.toString()
        val (valid, _, _) = CapsuleEngine.verifyJson(tampered)
        assertFalse("created_utc must be sealed", valid)
    }

    @Test
    fun tamperingAnAssumptionIsDetected() {
        val root = JSONObject(capsuleJson())
        val assumptions = root.getJSONArray("assumptions")
        assumptions.put(0, "Perfect assay specificity is guaranteed.")
        val (valid, _, _) = CapsuleEngine.verifyJson(root.toString())
        assertFalse("assumptions must be sealed", valid)
    }

    @Test
    fun tamperingThePermalinkIsDetected() {
        val root = JSONObject(capsuleJson())
        root.put("permalink", "concordance.app/c/00000000")
        val (valid, _, _) = CapsuleEngine.verifyJson(root.toString())
        assertFalse("identity fields must be sealed", valid)
    }

    @Test
    fun tamperingTheObservedCountsIsStillDetected() {
        val root = JSONObject(capsuleJson())
        root.getJSONObject("inputs").put("anchor_id", "rewritten")
        val (valid, _, _) = CapsuleEngine.verifyJson(root.toString())
        assertFalse("inputs must be sealed", valid)
    }

    @Test
    fun legacyVersionOneCapsulesStillVerify() {
        val inputs = JSONObject().apply {
            put("anchor_id", anchor.id)
            put("anchor_label", anchor.label)
            put("evidence_grade", anchor.evidenceGrade.name)
            put("s_n", 0.90)
            put("r_low", 0.80)
            put("r_high", 1.00)
        }
        val results = JSONObject().apply {
            put("verdict", "supported")
            put("observed_or", 0.263)
            put("observed_ci_low", 0.19)
            put("observed_ci_high", 0.36)
            put("r_null", 0.3107)
            put("r_significance", 0.40)
            put("rationale", "legacy capsule")
        }
        val hash = CapsuleEngine.sha256Hex(CapsuleEngine.buildCanonicalBody(inputs, results, null))
        val legacy = JSONObject().apply {
            put("capsule_id", "deadbeef")
            put("capsule_version", "1")
            put("created_utc", "2026-01-01T00:00:00Z")
            put("sha256", hash)
            put("inputs", inputs)
            put("results", results)
            put("plain_language_verdict", "legacy narrative")
        }
        val (valid, message, _) = CapsuleEngine.verifyJson(legacy.toString())
        assertTrue(message, valid)
        assertTrue(message.contains("legacy"))
    }

    @Test
    fun legacyCapsuleTamperedCountsAreDetected() {
        val inputs = JSONObject().apply { put("anchor_id", "x"); put("s_n", 0.9) }
        val results = JSONObject().apply { put("verdict", "supported") }
        val hash = CapsuleEngine.sha256Hex(CapsuleEngine.buildCanonicalBody(inputs, results, null))
        val legacy = JSONObject().apply {
            put("capsule_version", "1")
            put("sha256", hash)
            put("inputs", inputs)
            put("results", results)
        }
        inputs.put("s_n", 0.5)
        val (valid, _, _) = CapsuleEngine.verifyJson(legacy.toString())
        assertFalse(valid)
    }
}

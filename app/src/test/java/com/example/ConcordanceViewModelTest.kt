package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import com.example.concordance.ui.ConcordanceViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConcordanceViewModelTest {

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val vm = ConcordanceViewModel(app)

    @Test
    fun selectAnchorAndSn() {
        val anchor = Anchor(
            id = "a", label = "L", source = "S", evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = 1, brainTotal = 10, otherEvents = 2, otherTotal = 100,
            oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
            denominator = "samples", notes = "n", isCustom = false, dateAdded = "d"
        )
        vm.selectAnchor(anchor)
        assertEquals(anchor, vm.selectedAnchor.value)
        vm.updateSn(0.5)
        assertEquals(0.5, vm.sn.value, 1e-9)
        vm.updateSn(0.0) // coerced to 0.1
        assertEquals(0.1, vm.sn.value, 1e-9)
        vm.updateSn(2.0) // coerced to 1.0
        assertEquals(1.0, vm.sn.value, 1e-9)
    }

    @Test
    fun rangeSetters() {
        vm.setRange(0.1, 0.9)
        assertEquals(0.1, vm.rLow.value!!, 1e-9)
        assertEquals(0.9, vm.rHigh.value!!, 1e-9)
        vm.clearRange()
        assertNull(vm.rLow.value)
        assertNull(vm.rHigh.value)
    }

    @Test
    fun powerSetters() {
        vm.updatePowerTargetR(0.7)
        assertEquals(0.7, vm.powerTargetR.value, 1e-9)
        vm.updatePowerTargetR(0.0) // coerced
        assertEquals(0.1, vm.powerTargetR.value, 1e-9)
        vm.updatePowerDesired(0.9)
        assertEquals(0.9, vm.powerDesired.value, 1e-9)
    }

    @Test
    fun pairedCsvAnalysis() {
        vm.updatePairedCsv("a,b\n1,2")
        assertTrue(vm.pairedCsvText.value.contains("a,b"))
        vm.analyzePairedCsv(vm.pairedCsvText.value)
        // errors or result - just ensure no crash
    }

    @Test
    fun tuningSetters() {
        vm.setFailureThreshold(0)
        assertEquals(1, vm.cbThreshold.value)
        vm.setCooldownSeconds(2)
        assertEquals(5, vm.cbCooldownSeconds.value)
        vm.setCacheTtlMinutes(0)
        assertEquals(1, vm.cacheTtlMinutes.value)
        vm.setAutoRoundThreshold(0)
        assertEquals(1, vm.autoRoundThreshold.value)
        vm.setStabilityThreshold(4.0)
        assertEquals(1.0, vm.stabilityThreshold.value, 1e-9)
        vm.setStabilityThreshold(-1.0)
        assertEquals(0.0, vm.stabilityThreshold.value, 1e-9)
    }

    @Test
    fun easyModeAndTranscript() {
        vm.setEasyMode(true)
        assertTrue(vm.easyMode.value)
        vm.setEasyMode(false)
        assertTrue(!vm.easyMode.value)
        vm.setAgentTranscript("hello")
        assertEquals("hello", vm.agentTranscript.value)
        vm.clearAgentResult()
        assertNull(vm.agentCorrection.value)
        vm.clearVerification()
        assertNull(vm.verificationResult.value)
    }

    @Test
    fun clearIndraAndSyncConflicts() {
        vm.clearIndra()
        assertNull(vm.indraKnowledge.value)
        assertNull(vm.indraError.value)
        vm.clearSyncConflicts()
    }

    @Test
    fun configRevisionAndImport() {
        val before = vm.configRevision.value
        val json = org.json.JSONObject()
        json.put("agent_url", "http://example.com")
        vm.importConfigFromJson(json)
        assertTrue(vm.configRevision.value >= before)
    }

    @Test
    fun communityBaseUrl() {
        vm.setCommunityBaseUrl("http://example.com/")
        assertEquals("http://example.com", vm.communityBaseUrl.value)
    }

    @Test
    fun rotationAndProviders() {
        vm.setRotationStrategy(com.example.concordance.integration.rotation.RotationStrategy.ROUND_ROBIN)
        assertEquals(com.example.concordance.integration.rotation.RotationStrategy.ROUND_ROBIN, vm.rotationStrategy.value)
        vm.resetApiRotationStats()
    }

    @Test
    fun paperAnalysisEngine() {
        vm.setPaperAnalysisEngine(com.example.concordance.integration.science.AnalysisEngine.LOCAL_LLM)
        assertEquals(com.example.concordance.integration.science.AnalysisEngine.LOCAL_LLM, vm.paperAnalysisEngine.value)
    }

    @Test
    fun notifyAndTelemetry() {
        vm.notify("hi")
        // uiNotice is SharedFlow; just ensure no crash
        val s = vm.telemetrySummary()
        assertTrue(s is String)
    }

    @Test
    fun challengeImpliedVerdict_noRevision() {
        val anchor = Anchor(
            id = "a", label = "L", source = "S", evidenceGrade = EvidenceGrade.COUNTS,
            brainEvents = 1, brainTotal = 10, otherEvents = 2, otherTotal = 100,
            oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
            denominator = "samples", notes = "n", isCustom = false, dateAdded = "d"
        )
        val ch = com.example.concordance.model.ClaimChallenge(
            id = "c", claimId = "a", author = "x", date = "d", notes = "n",
            updatedBrainEvents = 2, updatedBrainTotal = 10, updatedOtherEvents = 3, updatedOtherTotal = 100
        )
        // just call; result may be null
        vm.challengeImpliedVerdict(ch)
    }

    @Test
    fun installedLocalModelsAndKokoro() {
        // just ensure no crash
        vm.installedLocalModels()
        vm.localKokoroReady()
    }
}

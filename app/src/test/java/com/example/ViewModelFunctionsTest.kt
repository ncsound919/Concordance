package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import com.example.concordance.ui.ConcordanceViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewModelFunctionsTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val vm = ConcordanceViewModel(app)

    @Test
    fun runLearningRound() {
        runBlocking {
            val anchor = Anchor(id="a", label="L", source="S", evidenceGrade=EvidenceGrade.COUNTS,
                brainEvents=1, brainTotal=10, otherEvents=2, otherTotal=100,
                oddsRatio=null, orCiLow=null, orCiHigh=null, referencePrevalence=0.5,
                denominator="samples", notes="n", isCustom=false, dateAdded="d")
            vm.addClaim(anchor)
            kotlinx.coroutines.delay(100)
            vm.selectAnchor(anchor)
            vm.runLearningRound()
            kotlinx.coroutines.delay(200)
        }
    }

    @Test
    fun runDeepResearch() {
        runBlocking {
            val anchor = Anchor(id="a2", label="L2", source="S", evidenceGrade=EvidenceGrade.COUNTS,
                brainEvents=1, brainTotal=10, otherEvents=2, otherTotal=100,
                oddsRatio=null, orCiLow=null, orCiHigh=null, referencePrevalence=0.5,
                denominator="samples", notes="n", isCustom=false, dateAdded="d")
            vm.addClaim(anchor)
            kotlinx.coroutines.delay(100)
            vm.runDeepResearch()
            kotlinx.coroutines.delay(200)
        }
    }

    @Test
    fun checkAutoTrigger() {
        vm.checkAutoTrigger()
    }

    @Test
    fun variousSettersAndClears() {
        vm.clearRevisionDiff()
        vm.clearRevisionArchiveExport()
        vm.clearIndra()
        vm.clearSyncConflicts()
        vm.clearVerification()
        vm.clearAgentResult()
        vm.exportRevisions()
        vm.clearRevisionArchiveExport()
    }

    @Test
    fun challengeImpliedVerdict() {
        val ch = com.example.concordance.model.ClaimChallenge(
            id="c", claimId="a", author="x", date="d", notes="n",
            updatedBrainEvents=2, updatedBrainTotal=10, updatedOtherEvents=3, updatedOtherTotal=100
        )
        vm.challengeImpliedVerdict(ch)
    }

    @Test
    fun reAdjudicateUnder() {
        vm.reAdjudicateUnder(null)
    }

    @Test
    fun previewCandidateDiff() {
        // needs a candidate; skip if none. Just call with null-ish via reflection avoided.
    }
}

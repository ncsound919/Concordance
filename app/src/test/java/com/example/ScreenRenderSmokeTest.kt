package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.concordance.model.CorrectedPoint
import com.example.concordance.ui.ConcordanceViewModel
import com.example.concordance.ui.components.AsyncJobQueueTab
import com.example.concordance.ui.components.ByokSettingsTab
import com.example.concordance.ui.components.CuratedEvidenceTab
import com.example.concordance.ui.components.ModelLearningTab
import com.example.concordance.ui.components.SensitivityCurveChart
import com.example.concordance.ui.components.SkinPickerDialog
import com.example.ui.theme.Skin
import com.example.concordance.ui.screens.AdjudicationScreen
import com.example.concordance.ui.screens.AgentScreen
import com.example.concordance.ui.screens.AuthScreen
import com.example.concordance.ui.screens.CapsulesScreen
import com.example.concordance.ui.screens.ClaimsScreen
import com.example.concordance.ui.screens.ConflictResolutionScreen
import com.example.concordance.ui.screens.IntegrationsScreen
import com.example.concordance.ui.screens.PairedDataScreen
import com.example.concordance.ui.screens.RegistryScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenRenderSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val vm by lazy {
        val ctx = app
        val db = com.example.concordance.data.ConcordanceDatabase.getDatabase(ctx)
        kotlinx.coroutines.runBlocking {
            db.claimDao().insertClaim(com.example.concordance.data.ClaimEntity(
                id = "a", label = "L", source = "S", evidenceGrade = com.example.concordance.model.EvidenceGrade.COUNTS.name,
                brainEvents = 1, brainTotal = 10, otherEvents = 2, otherTotal = 100,
                oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
                denominator = "samples", notes = "n", isCustom = false, dateAdded = "d"
            ))
            db.capsuleDao().insertCapsule(com.example.concordance.data.CapsuleEntity(
                id = "k1", permalink = "p", capsuleVersion = "1", softwareVersion = "1",
                createdUtc = "2026-01-01", sha256 = "h", question = "q", anchorLabel = "L",
                verdict = "SUPPORTS", citableArgument = "arg", plainLanguageVerdict = "pv",
                rNull = 0.5, rSignificance = 0.1, snAssumed = 0.9, rLow = 0.0, rHigh = 1.0,
                observedOr = 2.0, rawJson = "{}", markdown = "md"
            ))
        }
        val v = com.example.concordance.ui.ConcordanceViewModel(ctx)
        v.selectAnchor(com.example.concordance.model.Anchor(
            id = "a", label = "L", source = "S", evidenceGrade = com.example.concordance.model.EvidenceGrade.COUNTS,
            brainEvents = 1, brainTotal = 10, otherEvents = 2, otherTotal = 100,
            oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
            denominator = "samples", notes = "n", isCustom = false, dateAdded = "d"
        ))
        v
    }

    @Test
    fun adjudication() {
        composeRule.setContent { MyApplicationTheme { AdjudicationScreen(viewModel = vm, onNavigateToRegistry = {}) } }
        composeRule.waitForIdle()
    }

    @Test
    fun agent() {
        composeRule.setContent { MyApplicationTheme { AgentScreen(viewModel = vm) } }
        composeRule.waitForIdle()
    }

    @Test
    fun auth() {
        composeRule.setContent { MyApplicationTheme { AuthScreen(onSignedIn = { _ -> }, onGuest = {}) } }
        composeRule.waitForIdle()
    }

    @Test
    fun capsules() {
        composeRule.setContent { MyApplicationTheme { CapsulesScreen(viewModel = vm) } }
        composeRule.waitForIdle()
    }

    @Test
    fun claims() {
        composeRule.setContent { MyApplicationTheme { ClaimsScreen(viewModel = vm, onOpenClaim = {}) } }
        composeRule.waitForIdle()
    }

    @Test
    fun conflictResolution() {
        composeRule.setContent { MyApplicationTheme { ConflictResolutionScreen(conflicts = emptyList(), onResolve = { _, _ -> }) } }
        composeRule.waitForIdle()
    }

    @Test
    fun integrations() {
        composeRule.setContent { MyApplicationTheme { IntegrationsScreen(viewModel = vm, onNavigateToAdjudication = {}) } }
        composeRule.waitForIdle()
    }

    @Test
    fun pairedData() {
        composeRule.setContent { MyApplicationTheme { PairedDataScreen(viewModel = vm, onNavigateToAdjudication = {}) } }
        composeRule.waitForIdle()
    }

    @Test
    fun registry() {
        composeRule.setContent { MyApplicationTheme { RegistryScreen(viewModel = vm, onSelectAndAdjudicate = {}) } }
        composeRule.waitForIdle()
    }

    @Test
    fun asyncJobQueueTab() {
        composeRule.setContent { MyApplicationTheme { AsyncJobQueueTab(viewModel = vm, onCopyText = { _, _ -> }) } }
        composeRule.waitForIdle()
    }

    @Test
    fun byokSettingsTab() {
        composeRule.setContent { MyApplicationTheme { ByokSettingsTab(viewModel = vm) } }
        composeRule.waitForIdle()
    }

    @Test
    fun curatedEvidenceTab() {
        composeRule.setContent { MyApplicationTheme { CuratedEvidenceTab(viewModel = vm, onCopyText = { _, _ -> }) } }
        composeRule.waitForIdle()
    }

    @Test
    fun modelLearningTab() {
        composeRule.setContent { MyApplicationTheme { ModelLearningTab(viewModel = vm) } }
        composeRule.waitForIdle()
    }

    @Test
    fun sensitivityCurveChart() {
        composeRule.setContent { MyApplicationTheme { SensitivityCurveChart(curve = emptyList(), rNull = 0.5, rSig = null, rLow = null, rHigh = null) } }
        composeRule.waitForIdle()
    }

    @Test
    fun skinPickerDialog() {
        composeRule.setContent { MyApplicationTheme { SkinPickerDialog(current = Skin.CLINICAL, onSelect = {}, onDismiss = {}) } }
        composeRule.waitForIdle()
    }
}

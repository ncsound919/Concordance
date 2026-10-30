package com.example

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.concordance.ui.ConcordanceViewModel
import com.example.concordance.ui.screens.AdjudicationScreen
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdjudicationInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun interact() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.concordance.data.ConcordanceDatabase.getDatabase(app)
        kotlinx.coroutines.runBlocking {
            db.claimDao().insertClaim(com.example.concordance.data.ClaimEntity(
                id = "a", label = "L", source = "S", evidenceGrade = com.example.concordance.model.EvidenceGrade.COUNTS.name,
                brainEvents = 1, brainTotal = 10, otherEvents = 2, otherTotal = 100,
                oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
                denominator = "samples", notes = "n", isCustom = false, dateAdded = "d"
            ))
        }
        val vm = ConcordanceViewModel(app)
        kotlinx.coroutines.runBlocking { kotlinx.coroutines.delay(100) }
        vm.selectAnchor(com.example.concordance.model.Anchor(
            id = "a", label = "L", source = "S", evidenceGrade = com.example.concordance.model.EvidenceGrade.COUNTS,
            brainEvents = 1, brainTotal = 10, otherEvents = 2, otherTotal = 100,
            oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = 0.5,
            denominator = "samples", notes = "n", isCustom = false, dateAdded = "d"
        ))
        composeRule.setContent { MyApplicationTheme { AdjudicationScreen(viewModel = vm, onNavigateToRegistry = {}) } }
        composeRule.waitForIdle()
        // click range preset buttons if present
        runCatching { composeRule.onNodeWithText("0.25", substring = true).performClick(); composeRule.waitForIdle() }
        runCatching { composeRule.onNodeWithText("0.80", substring = true).performClick(); composeRule.waitForIdle() }
        runCatching { composeRule.onNodeWithText("Clear", substring = true).performClick(); composeRule.waitForIdle() }
    }
}

package com.example

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.concordance.ui.ConcordanceViewModel
import com.example.concordance.ui.screens.CapsulesScreen
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CapsulesInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabs() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.concordance.data.ConcordanceDatabase.getDatabase(app)
        kotlinx.coroutines.runBlocking {
            db.capsuleDao().insertCapsule(com.example.concordance.data.CapsuleEntity(
                id = "k1", permalink = "p", capsuleVersion = "1", softwareVersion = "1",
                createdUtc = "2026-01-01", sha256 = "h", question = "q", anchorLabel = "L",
                verdict = "SUPPORTS", citableArgument = "arg", plainLanguageVerdict = "pv",
                rNull = 0.5, rSignificance = 0.1, snAssumed = 0.9, rLow = 0.0, rHigh = 1.0,
                observedOr = 2.0, rawJson = "{}", markdown = "md"
            ))
        }
        val vm = ConcordanceViewModel(app)
        kotlinx.coroutines.runBlocking { kotlinx.coroutines.delay(100) }
        composeRule.setContent { MyApplicationTheme { CapsulesScreen(viewModel = vm) } }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cryptographic Verifier").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Artifacts", substring = true).performClick()
        composeRule.waitForIdle()
    }
}

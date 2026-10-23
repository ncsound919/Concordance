package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import com.example.concordance.ui.components.AppSectionCard
import com.example.concordance.ui.components.AppStatTile
import com.example.concordance.ui.components.EmptyState
import com.example.concordance.ui.screens.ClaimRegistryCard
import com.example.concordance.ui.screens.DataMetricPill
import com.example.concordance.ui.screens.ThresholdCard
import com.example.ui.theme.MidnightDeep
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextPrimary
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotGalleryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val anchor = Anchor(
        id = "a1",
        label = "Giannoudis 2026 — ESR1 Y537S CNS metastasis",
        source = "Current Oncology, case series (n=1)",
        evidenceGrade = EvidenceGrade.COUNTS,
        brainEvents = 45,
        brainTotal = 700,
        otherEvents = 2800,
        otherTotal = 14000,
        notes = "CNS-restricted progression; plasma ctDNA negative.",
    )

    private fun capture(name: String, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            MyApplicationTheme(darkTheme = dark) {
                Surface(color = MidnightDeep, modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        content()
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun registryCard_dark() = capture("registry_card_dark", dark = true) {
        ClaimRegistryCard(
            claim = anchor,
            observedOr = 0.321,
            ciLow = 0.24,
            ciHigh = 0.43,
            rNull = 0.40,
            prevBrain = 0.064,
            prevOther = 0.200,
            onAdjudicate = {},
            onChallenge = {},
        )
    }

    @Test
    fun registryCard_light() = capture("registry_card_light", dark = false) {
        ClaimRegistryCard(
            claim = anchor,
            observedOr = 0.321,
            ciLow = 0.24,
            ciHigh = 0.43,
            rNull = 0.40,
            prevBrain = 0.064,
            prevOther = 0.200,
            onAdjudicate = {},
            onChallenge = {},
        )
    }

    @Test
    fun thresholdCards_dark() = capture("threshold_cards_dark", dark = true) {
        ThresholdCard(
            title = "2026 DISCORDANCE",
            value = "0.25–0.38",
            subtitle = "CNS-restricted progression",
            color = TextPrimary,
        )
        ThresholdCard(
            title = "HIGH CONCORDANCE",
            value = "0.80–1.00",
            subtitle = "Systemic progression",
            color = TextPrimary,
        )
        DataMetricPill(label = "r (null)", value = "0.40")
    }

    @Test
    fun sectionCard_light() = capture("section_card_light", dark = false) {
        AppSectionCard(title = "Sensitivity assumptions", subtitle = "Assumed non-CNS sensitivity") {
            DataMetricPill(label = "s_n", value = "0.90")
        }
        AppStatTile(value = "0.263", label = "Corrected OR", hint = "measured")
    }

    @Test
    fun emptyState_dark() = capture("empty_state_dark", dark = true) {
        EmptyState(
            title = "No capsules minted yet",
            message = "Run an adjudication and tap Mint Capsule to create a citable artifact.",
        )
    }
}

package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.concordance.ui.components.AppChip
import com.example.concordance.ui.components.AppSectionCard
import com.example.concordance.ui.components.AppStatTile
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightDeep
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.Skin
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.blueprintGrid
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each skin so the look can be reviewed without a device.
 *
 * These are not golden comparisons - there are no committed baselines - they
 * write images to `build/screenshots/` so a change to the design system can be
 * seen in a diff.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SkinGalleryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, dark: Boolean, skin: Skin) {
        composeRule.setContent {
            MyApplicationTheme(darkTheme = dark, skin = skin) {
                Surface(color = MidnightDeep, modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (skin.blueprintBackdrop) {
                                    Modifier.blueprintGrid(ElectricCyan.copy(alpha = 0.12f))
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AppSectionCard(
                                title = "Sensitivity assumptions",
                                subtitle = "Assumed non-CNS sensitivity",
                            ) {
                                AppChip(text = "s_n  0.90", color = ElectricCyan)
                            }
                            Row2()
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @androidx.compose.runtime.Composable
    private fun Row2() {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppStatTile(
                value = "0.263",
                label = "Corrected OR",
                hint = "measured",
                modifier = Modifier.weight(1f),
                accent = ElectricCyan,
            )
            AppStatTile(
                value = "0.401",
                label = "Threshold r*",
                hint = "discordant",
                modifier = Modifier.weight(1f),
                accent = NeonEmerald,
            )
            AppStatTile(
                value = "REPRODUCED",
                label = "Verdict",
                hint = "computed",
                modifier = Modifier.weight(1f),
                accent = TextPrimary,
            )
        }
    }

    @Test
    fun clinicalDark() = capture("skin_clinical_dark", dark = true, skin = Skin.CLINICAL)

    @Test
    fun clinicalLight() = capture("skin_clinical_light", dark = false, skin = Skin.CLINICAL)

    @Test
    fun siteDark() = capture("skin_site_dark", dark = true, skin = Skin.SITE)

    @Test
    fun siteLight() = capture("skin_site_light", dark = false, skin = Skin.SITE)
}

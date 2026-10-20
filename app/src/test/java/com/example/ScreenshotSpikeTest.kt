package com.example

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.concordance.ui.components.AppSectionCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Spike: proves Roborazzi can render and record a Compose screen on the JVM
 * (no emulator), so we can add visual regression goldens for light/dark themes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotSpikeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sectionCard_dark_records() {
        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                AppSectionCard(title = "Screenshot spike", subtitle = "Roborazzi on the JVM") {
                    Text("This image proves the golden pipeline works.")
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/screenshots/spike_dark.png")
    }
}

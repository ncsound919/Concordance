package com.example

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.concordance.ui.navigation.ConcordanceScaffold
import com.example.concordance.ui.navigation.NavigationState
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the real app shell with a fake content slot, so the navigation bar,
 * rail, More sheet and back handling are covered without a ViewModel or camera.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class NavigationScaffoldTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun render(nav: NavigationState) {
        composeRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                ConcordanceScaffold(
                    navState = nav,
                    themeMode = ThemeMode.DARK,
                ) {
                    Text("HOME CONTENT")
                }
            }
        }
    }

    @Test
    fun compactWidth_shows_five_primary_destinations() {
        render(NavigationState())
        NavigationDestination.primary.forEach { dest ->
            composeRule.onAllNodesWithText(dest.title).onFirst().assertIsDisplayed()
        }
        composeRule.onNodeWithText("HOME CONTENT").assertIsDisplayed()
        // Secondary destinations stay behind "More" on compact widths.
        composeRule.onNodeWithContentDescription("More sections").assertIsDisplayed()
    }

    @Test
    fun tapping_destination_updates_state() {
        val nav = NavigationState()
        render(nav)
        composeRule.onAllNodesWithText("Registry").onFirst().performClick()
        composeRule.runOnIdle { assertEquals(NavigationDestination.REGISTRY, nav.current) }
    }

    @Test
    fun more_sheet_lists_secondary_destinations() {
        val nav = NavigationState()
        render(nav)
        composeRule.onNodeWithContentDescription("More sections").performClick()
        composeRule.waitForIdle()
        assertTrue(nav.showMore)
        composeRule.onNodeWithText("Integrations").assertIsDisplayed()
        composeRule.onNodeWithText("Account").assertIsDisplayed()
    }

    @Test
    fun back_from_secondary_returns_home() {
        val nav = NavigationState()
        nav.select(NavigationDestination.INTEGRATIONS)
        render(nav)
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        assertEquals(NavigationDestination.ADJUDICATE, nav.current)
    }

    @Test
    fun back_closes_more_sheet_first() {
        val nav = NavigationState()
        nav.select(NavigationDestination.CAPSULES)
        nav.openMore()
        render(nav)
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        assertFalse(nav.showMore)
        assertEquals(NavigationDestination.CAPSULES, nav.current)
    }

    @Test
    @Config(qualifiers = "w700dp-h900dp-xhdpi")
    fun expandedWidth_shows_rail_with_secondary_items() {
        render(NavigationState())
        // The rail renders every destination; the compact "More" action is gone.
        composeRule.onNodeWithText("Integrations").assertIsDisplayed()
        composeRule.onNodeWithText("Account").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("More sections").assertCountEquals(0)
    }
}

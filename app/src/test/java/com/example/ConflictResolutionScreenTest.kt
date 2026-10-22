package com.example

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.concordance.integration.supabase.SyncConflict
import com.example.concordance.ui.screens.ConflictResolutionScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sync-conflict screen.
 *
 * This is the screen a human uses to resolve a conflict between the device and
 * the server, so the thing that matters is that every conflict offers the
 * same four resolutions, that the chosen one is reported with the conflict it
 * belongs to, and that an empty queue says so rather than rendering a blank
 * panel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConflictResolutionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun conflict(
        entityType: String = "claim",
        entityId: String = "c1",
        reason: String = "The device and the server disagree."
    ) = SyncConflict(entityType, entityId, reason)

    private fun setContent(
        conflicts: List<SyncConflict>,
        onResolve: (SyncConflict, String) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            MyApplicationTheme {
                Surface { ConflictResolutionScreen(conflicts = conflicts, onResolve = onResolve) }
            }
        }
    }

    @Test
    fun `the screen names itself`() {
        setContent(emptyList())
        composeRule.onNodeWithText("Sync conflicts").assertIsDisplayed()
    }

    @Test
    fun `an empty queue says everything is in sync`() {
        setContent(emptyList())
        composeRule.onNodeWithText("No conflicts. Everything is in sync.").assertIsDisplayed()
    }

    @Test
    fun `a conflict shows its entity and its reason`() {
        setContent(listOf(conflict(entityType = "capsule", entityId = "k9", reason = "Version differs.")))
        composeRule.onNodeWithText("capsule: k9").assertIsDisplayed()
        composeRule.onNodeWithText("Version differs.").assertIsDisplayed()
    }

    @Test
    fun `every conflict offers the same four resolutions`() {
        setContent(listOf(conflict()))
        for (label in listOf(
            "Accept agent proposal",
            "Keep local",
            "Keep server",
            "Open challenge"
        )) {
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun `accepting the agent proposal reports it with the conflict`() {
        var reported: Pair<SyncConflict, String>? = null
        val c = conflict()
        setContent(listOf(c)) { conflict, choice -> reported = conflict to choice }
        composeRule.onNodeWithText("Accept agent proposal").performClick()
        assertEquals(c to "accept_agent", reported)
    }

    @Test
    fun `keeping the local copy reports it`() {
        var choice: String? = null
        setContent(listOf(conflict())) { _, c -> choice = c }
        composeRule.onNodeWithText("Keep local").performClick()
        assertEquals("keep_local", choice)
    }

    @Test
    fun `keeping the server copy reports it`() {
        var choice: String? = null
        setContent(listOf(conflict())) { _, c -> choice = c }
        composeRule.onNodeWithText("Keep server").performClick()
        assertEquals("keep_server", choice)
    }

    @Test
    fun `opening a challenge reports it`() {
        var choice: String? = null
        setContent(listOf(conflict())) { _, c -> choice = c }
        composeRule.onNodeWithText("Open challenge").performClick()
        assertEquals("open_challenge", choice)
    }

    @Test
    fun `several conflicts each get their own resolutions`() {
        val choices = mutableListOf<Pair<String, String>>()
        val first = conflict(entityId = "c1")
        val second = conflict(entityId = "c2")
        setContent(listOf(first, second)) { conflict, choice ->
            choices.add(conflict.entityId to choice)
        }
        // Each conflict renders its own "Keep local" button, so the matcher has
        // to be scoped to one of them rather than expecting a unique node.
        composeRule.onAllNodesWithText("Keep local")[0].performClick()
        assertTrue(choices.contains("c1" to "keep_local"))

        composeRule.onAllNodesWithText("Keep local")[1].performClick()
        assertTrue(choices.contains("c2" to "keep_local"))
        assertEquals(2, choices.size)
    }

    @Test
    fun `the resolution buttons are enabled`() {
        setContent(listOf(conflict()))
        composeRule.onNodeWithText("Keep local").assertIsEnabled()
    }
}

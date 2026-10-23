package com.example

import android.content.Intent
import com.example.concordance.integration.tasker.TaskerBridge
import com.example.concordance.integration.tasker.TaskerTriggerReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TaskerBridgeTest {

    @Test
    fun enqueueAndDrainRoundTripsActions() {
        val ctx = RuntimeEnvironment.getApplication()
        // Ensure clean state
        TaskerBridge.drain(ctx)

        TaskerBridge.enqueue(ctx, TaskerBridge.ACTION_RUN_LEARNING_ROUND)
        TaskerBridge.enqueue(ctx, TaskerBridge.ACTION_RE_ADJUDICATE, "rev-42")

        assertEquals(2, TaskerBridge.pendingCount(ctx))

        val drained = TaskerBridge.drain(ctx)
        assertEquals(2, drained.size)
        assertTrue(drained[0].contains("RUN_LEARNING_ROUND"))
        assertEquals("concordance.action.RE_ADJUDICATE=rev-42", drained[1])

        // Queue cleared
        assertEquals(0, TaskerBridge.pendingCount(ctx))
        assertTrue(TaskerBridge.drain(ctx).isEmpty())
    }

    @Test
    fun handleIntentRecordsKnownActionAndIgnoresUnknown() {
        val ctx = RuntimeEnvironment.getApplication()
        TaskerBridge.drain(ctx)

        val intent = Intent(TaskerBridge.ACTION_CHECK_AUTO_TRIGGER)
        TaskerBridge.handleIntent(ctx, intent)
        assertEquals(1, TaskerBridge.pendingCount(ctx))

        // Unknown action is ignored
        TaskerBridge.handleIntent(ctx, Intent("com.example.NOPE"))
        assertEquals(1, TaskerBridge.pendingCount(ctx))

        TaskerBridge.drain(ctx)
    }

    @Test
    fun receiverDelegatesToBridge() {
        val ctx = RuntimeEnvironment.getApplication()
        TaskerBridge.drain(ctx)

        val receiver = TaskerTriggerReceiver()
        val intent = Intent(TaskerBridge.ACTION_SYNC_CAPSULES)
        receiver.onReceive(ctx, intent)

        assertEquals(1, TaskerBridge.pendingCount(ctx))
        val drained = TaskerBridge.drain(ctx)
        assertTrue(drained[0].contains("SYNC_CAPSULES"))
    }

    @Test
    fun nullIntentIsNoOp() {
        val ctx = RuntimeEnvironment.getApplication()
        TaskerBridge.drain(ctx)
        TaskerBridge.handleIntent(ctx, null)
        assertEquals(0, TaskerBridge.pendingCount(ctx))
    }
}

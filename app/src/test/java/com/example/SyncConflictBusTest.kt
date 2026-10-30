package com.example

import com.example.concordance.integration.supabase.SyncConflict
import com.example.concordance.integration.supabase.SyncConflictBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncConflictBusTest {
    @Test
    fun reportAndClear() {
        SyncConflictBus.clear()
        val c = SyncConflict("claim", "c1", "r")
        SyncConflictBus.report(c)
        assertEquals(1, SyncConflictBus.conflicts.value.size)
        SyncConflictBus.remove(c)
        assertTrue(SyncConflictBus.conflicts.value.isEmpty())
    }

    @Test
    fun resolve() {
        SyncConflictBus.clear()
        val c = SyncConflict("claim", "c1", "r")
        SyncConflictBus.report(c)
        SyncConflictBus.resolve(c, "keep_local")
        assertEquals("keep_local", SyncConflictBus.resolutionFor("claim", "c1"))
        assertTrue(SyncConflictBus.conflicts.value.isEmpty())
    }
}

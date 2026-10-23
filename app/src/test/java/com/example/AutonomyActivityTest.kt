package com.example

import com.example.concordance.autonomy.ActivityLevel
import com.example.concordance.autonomy.AutonomyActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutonomyActivityTest {

    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        AutonomyActivity.init(RuntimeEnvironment.getApplication())
        AutonomyActivity.clock = { now }
        AutonomyActivity.clear()
    }

    @After
    fun tearDown() {
        AutonomyActivity.clear()
        AutonomyActivity.clock = { System.currentTimeMillis() }
    }

    @Test
    fun startThenFinishRecordsOutcomeAndDuration() {
        val id = AutonomyActivity.start(AutonomyActivity.SOURCE_SLOW, "Slow step: polling")
        assertTrue(AutonomyActivity.activeEntry() != null)

        now += 4_500
        AutonomyActivity.finish(id, ActivityLevel.OK, "Slow step: nothing new")

        val entry = AutonomyActivity.entries.value.single()
        assertEquals(ActivityLevel.OK, entry.level)
        assertEquals("Slow step: nothing new", entry.message)
        assertEquals(4_500L, entry.durationMs)
        assertNull(AutonomyActivity.activeEntry())
    }

    @Test
    fun finishWithoutMessageKeepsTheOriginalText() {
        val id = AutonomyActivity.start(AutonomyActivity.SOURCE_HIGH_POWER, "High power research: brain")
        AutonomyActivity.finish(id, ActivityLevel.FAILED)
        assertEquals("High power research: brain", AutonomyActivity.entries.value.single().message)
    }

    @Test
    fun unknownIdIsIgnoredInsteadOfThrowing() {
        AutonomyActivity.record(AutonomyActivity.SOURCE_SYNC, "Synced 2 records", ActivityLevel.OK)
        AutonomyActivity.finish(123_456L, ActivityLevel.FAILED, "should not apply")
        val entry = AutonomyActivity.entries.value.single()
        assertEquals(ActivityLevel.OK, entry.level)
        assertEquals("Synced 2 records", entry.message)
    }

    /**
     * A run that never finished (crash, force-stop, process death) must not keep saying
     * "running" forever: the next launch reports it as interrupted.
     */
    @Test
    fun unfinishedWorkIsMarkedInterruptedAfterReload() {
        AutonomyActivity.start(AutonomyActivity.SOURCE_HIGH_POWER, "High power research: brain")
        now += 60_000
        AutonomyActivity.reload()

        val entry = AutonomyActivity.entries.value.single()
        assertEquals(ActivityLevel.FAILED, entry.level)
        assertTrue(entry.message.contains("interrupted"))
        assertEquals(60_000L, entry.durationMs)
    }

    @Test
    fun newestEntryComesFirstAndTheBufferIsCapped() {
        repeat(45) { i ->
            now += 1_000
            AutonomyActivity.record(AutonomyActivity.SOURCE_SLOW, "step $i", ActivityLevel.OK)
        }
        val entries = AutonomyActivity.entries.value
        assertEquals(40, entries.size)
        assertEquals("step 44", entries.first().message)
        assertEquals("step 5", entries.last().message)
    }

    @Test
    fun clearEmptiesTheLog() {
        AutonomyActivity.record(AutonomyActivity.SOURCE_SLOW, "step", ActivityLevel.OK)
        AutonomyActivity.clear()
        assertTrue(AutonomyActivity.entries.value.isEmpty())
    }

    @Test
    fun durationFormattingStaysCompact() {
        assertEquals("0s", AutonomyActivity.formatDuration(0))
        assertEquals("45s", AutonomyActivity.formatDuration(45_000))
        assertEquals("2m 05s", AutonomyActivity.formatDuration(125_000))
        assertEquals("1h 02m", AutonomyActivity.formatDuration(3_725_000))
    }

    @Test
    fun ageFormattingReadsNaturally() {
        val base = 1_700_000_000_000L
        assertEquals("just now", AutonomyActivity.formatAge(base, base))
        assertEquals("30s ago", AutonomyActivity.formatAge(base + 30_000, base))
        assertEquals("3m ago", AutonomyActivity.formatAge(base + 180_000, base))
        assertEquals("2h ago", AutonomyActivity.formatAge(base + 7_200_000, base))
        assertEquals("1d ago", AutonomyActivity.formatAge(base + 90_000_000, base))
    }
}

package com.example

import com.example.concordance.autonomy.AutonomyMode
import com.example.concordance.autonomy.AutonomyModeManager
import com.example.concordance.autonomy.AutonomyState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutonomyModeManagerTest {

    @Test
    fun testInitialStateIsPaused() {
        val s = AutonomyModeManager.state.value
        // Default state should be PAUSED (safe default)
        assertTrue(s.mode == AutonomyMode.PAUSED || s.mode == AutonomyMode.OFFLINE)
    }

    @Test
    fun testGoSetsHighPowerWhenOnline() {
        AutonomyModeManager.setOffline(false)
        AutonomyModeManager.go()
        assertEquals(AutonomyMode.HIGH_POWER, AutonomyModeManager.state.value.mode)
    }

    @Test
    fun testGoSetsSlowWhenOffline() {
        AutonomyModeManager.setOffline(true)
        AutonomyModeManager.go()
        assertEquals(AutonomyMode.SLOW, AutonomyModeManager.state.value.mode)
        AutonomyModeManager.setOffline(false)
    }

    @Test
    fun testPause() {
        AutonomyModeManager.go()
        AutonomyModeManager.pause()
        assertEquals(AutonomyMode.PAUSED, AutonomyModeManager.state.value.mode)
    }

    @Test
    fun testStopClearsGoal() {
        AutonomyModeManager.setCurrentGoal("test goal")
        AutonomyModeManager.stop()
        assertNull(AutonomyModeManager.state.value.currentGoal)
        assertEquals(0, AutonomyModeManager.state.value.currentStep)
        assertEquals(0, AutonomyModeManager.state.value.queueSize)
    }

    @Test
    fun testSetCurrentGoal() {
        AutonomyModeManager.setCurrentGoal("brain metastasis research")
        assertEquals("brain metastasis research", AutonomyModeManager.state.value.currentGoal)
        assertEquals(0, AutonomyModeManager.state.value.currentStep)
    }

    @Test
    fun testTickStep() {
        AutonomyModeManager.setCurrentGoal("test")
        val before = AutonomyModeManager.state.value.currentStep
        AutonomyModeManager.tickStep()
        assertEquals(before + 1, AutonomyModeManager.state.value.currentStep)
        assertTrue(AutonomyModeManager.state.value.lastRunAt > 0)
    }

    @Test
    fun testSetBatteryOkPausesWhenLow() {
        AutonomyModeManager.setBatteryOk(false)
        assertEquals(AutonomyMode.PAUSED, AutonomyModeManager.state.value.mode)
        assertFalse(AutonomyModeManager.state.value.batteryOk)
        AutonomyModeManager.setBatteryOk(true)
    }

    @Test
    fun testSetOutboxCount() {
        AutonomyModeManager.setOutboxCount(5)
        assertEquals(5, AutonomyModeManager.state.value.outboxCount)
    }

    @Test
    fun testSetLastReportSeen() {
        AutonomyModeManager.setLastReportSeen("2026-10-21")
        assertEquals("2026-10-21", AutonomyModeManager.state.value.lastReportSeen)
    }
}

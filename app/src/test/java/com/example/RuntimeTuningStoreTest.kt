package com.example

import com.example.concordance.data.RuntimeTuning
import com.example.concordance.data.RuntimeTuningStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The runtime tuning store.
 *
 * These values are read from disk and come from a previous version of the app
 * or from a corrupted preferences file, so every one of them is clamped on
 * load. A negative circuit-breaker threshold or a stability threshold outside
 * zero to one would silently break the integration layer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RuntimeTuningStoreTest {

    private lateinit var store: RuntimeTuningStore

    @Before
    fun setUp() {
        store = RuntimeTuningStore(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `the defaults are what the app expects`() {
        val tuning = store.load()
        assertEquals(3, tuning.failureThreshold)
        assertEquals(60, tuning.cooldownSeconds)
        assertEquals(10, tuning.cacheTtlMinutes)
        assertEquals(5, tuning.autoRoundThreshold)
        assertEquals(0, tuning.lastAutoRoundChallengeCount)
        assertEquals(0.25, tuning.stabilityThreshold, 1e-9)
    }

    @Test
    fun `a saved tuning round trips`() {
        val tuning = RuntimeTuning(
            failureThreshold = 7,
            cooldownSeconds = 120,
            cacheTtlMinutes = 45,
            autoRoundThreshold = 9,
            lastAutoRoundChallengeCount = 4,
            stabilityThreshold = 0.6
        )
        store.save(tuning)
        val loaded = store.load()
        assertEquals(7, loaded.failureThreshold)
        assertEquals(120, loaded.cooldownSeconds)
        assertEquals(45, loaded.cacheTtlMinutes)
        assertEquals(9, loaded.autoRoundThreshold)
        assertEquals(4, loaded.lastAutoRoundChallengeCount)
        assertEquals(0.6, loaded.stabilityThreshold, 1e-6)
    }

    @Test
    fun `a failure threshold below one is clamped up`() {
        store.save(RuntimeTuning(failureThreshold = 0))
        assertEquals(1, store.load().failureThreshold)
    }

    @Test
    fun `a negative failure threshold is clamped up`() {
        store.save(RuntimeTuning(failureThreshold = -5))
        assertEquals(1, store.load().failureThreshold)
    }

    @Test
    fun `a cooldown below five seconds is clamped up`() {
        store.save(RuntimeTuning(cooldownSeconds = 0))
        assertEquals(5, store.load().cooldownSeconds)
        store.save(RuntimeTuning(cooldownSeconds = 2))
        assertEquals(5, store.load().cooldownSeconds)
    }

    @Test
    fun `a cache ttl below one minute is clamped up`() {
        store.save(RuntimeTuning(cacheTtlMinutes = 0))
        assertEquals(1, store.load().cacheTtlMinutes)
    }

    @Test
    fun `an auto round threshold below one is clamped up`() {
        store.save(RuntimeTuning(autoRoundThreshold = 0))
        assertEquals(1, store.load().autoRoundThreshold)
    }

    @Test
    fun `a negative challenge count is clamped up to zero`() {
        store.save(RuntimeTuning(lastAutoRoundChallengeCount = -3))
        assertEquals(0, store.load().lastAutoRoundChallengeCount)
    }

    @Test
    fun `a stability threshold above one is clamped down`() {
        store.save(RuntimeTuning(stabilityThreshold = 4.0))
        assertEquals(1.0, store.load().stabilityThreshold, 1e-6)
    }

    @Test
    fun `a negative stability threshold is clamped up to zero`() {
        store.save(RuntimeTuning(stabilityThreshold = -1.0))
        assertEquals(0.0, store.load().stabilityThreshold, 1e-6)
    }

    @Test
    fun `the clamped value is what gets written back on the next save`() {
        store.save(RuntimeTuning(failureThreshold = 0, stabilityThreshold = 9.0))
        val clamped = store.load()
        store.save(clamped)
        assertEquals(1, store.load().failureThreshold)
        assertEquals(1.0, store.load().stabilityThreshold, 1e-6)
    }

    @Test
    fun `saving does not disturb the untouched fields`() {
        store.save(RuntimeTuning(failureThreshold = 11))
        val loaded = store.load()
        assertEquals(11, loaded.failureThreshold)
        assertEquals(60, loaded.cooldownSeconds)
        assertTrue(loaded.cacheTtlMinutes >= 1)
    }
}

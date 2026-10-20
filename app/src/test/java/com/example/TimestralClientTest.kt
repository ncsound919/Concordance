package com.example

import com.example.concordance.integration.forecast.TimestralClient
import com.example.concordance.integration.forecast.TimestralResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimestralClientTest {
    @Test
    fun `baseline forecast needs at least 4 points`() {
        val c = TimestralClient()
        val r = c.baselineForecast(listOf(1.0, 2.0, 3.0), 3)
        assertTrue(r is TimestralResult.Unavailable)
    }

    @Test
    fun `baseline forecast returns horizon points`() {
        val c = TimestralClient()
        val r = c.baselineForecast(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        assertTrue(r is TimestralResult.Ok)
        val f = (r as TimestralResult.Ok).value
        assertEquals(3, f.mean.size)
        assertEquals(3, f.lower.size)
        assertEquals(3, f.upper.size)
        assertTrue(f.lower[0] < f.mean[0])
        assertTrue(f.upper[0] > f.mean[0])
    }

    @Test
    fun `horizon is clamped`() {
        val c = TimestralClient()
        assertEquals(1, c.clampHorizon(0))
        assertEquals(30, c.clampHorizon(100))
        assertEquals(5, c.clampHorizon(5))
    }

    @Test
    fun `non-finite series refused`() {
        val c = TimestralClient()
        val r = c.baselineForecast(listOf(1.0, Double.NaN, 3.0, 4.0), 2)
        assertTrue(r is TimestralResult.Unavailable)
    }

    @Test
    fun `model availability defaults false`() {
        assertTrue(!TimestralClient().isAvailable())
    }
}

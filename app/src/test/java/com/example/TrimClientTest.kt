package com.example

import com.example.concordance.integration.context.TrimClient
import com.example.concordance.integration.context.TrimOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimClientTest {
    @Test
    fun `blank question unavailable`() {
        val c = TrimClient()
        val r = c.prune(com.example.concordance.integration.context.TrimRequest("", listOf("a", "b"), 1))
        assertTrue(r is TrimOutcome.Unavailable)
    }

    @Test
    fun `no pruning when within budget`() {
        val c = TrimClient()
        val lines = listOf("alpha", "beta")
        val r = c.prune(com.example.concordance.integration.context.TrimRequest("alpha", lines, 5))
        assertTrue(r is TrimOutcome.Ok)
        val v = (r as TrimOutcome.Ok).value
        assertEquals(lines, v.kept)
        assertEquals(0, v.dropped)
    }

    @Test
    fun `prunes to max lines keeping relevant`() {
        val c = TrimClient()
        val lines = listOf("unrelated fluff", "ESR1 mutation data", "more fluff", "variant Y537S", "tail")
        val r = c.prune(com.example.concordance.integration.context.TrimRequest("ESR1 variant", lines, 2))
        assertTrue(r is TrimOutcome.Ok)
        val v = (r as TrimOutcome.Ok).value
        assertEquals(2, v.kept.size)
        assertTrue(v.kept.any { it.contains("ESR1") })
        assertEquals(3, v.dropped)
    }

    @Test
    fun `model availability defaults false`() {
        assertTrue(!TrimClient().isAvailable())
    }
}

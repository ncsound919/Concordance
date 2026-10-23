package com.example

import com.example.concordance.autonomy.ResearchRouting
import com.example.concordance.integration.agent.AgentExperiment
import com.example.concordance.integration.queue.WorkerRoutingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchRoutingTest {

    private fun experiment(routing: String) = AgentExperiment(
        title = "Experiment ($routing)",
        design = "design",
        controls = "controls",
        endpoints = "endpoints",
        estimatedDuration = "2 weeks",
        risk = "low",
        routing = routing,
    )

    @Test
    fun mapsHeavyBackendsToPolicies() {
        assertEquals(WorkerRoutingPolicy.AWS_BATCH_CPU, ResearchRouting.policyFor("aws_batch"))
        assertEquals(WorkerRoutingPolicy.AWS_FARGATE_GPU, ResearchRouting.policyFor("aws_fargate"))
        assertEquals(WorkerRoutingPolicy.KAGGLE_CLUSTER_GPU, ResearchRouting.policyFor("kaggle"))
        // combo needs more than one backend; the queue takes the heavier half.
        assertEquals(WorkerRoutingPolicy.AWS_FARGATE_GPU, ResearchRouting.policyFor("combo"))
    }

    @Test
    fun localAndCloudflareAreNeverQueued() {
        assertNull(ResearchRouting.policyFor("local"))
        assertNull(ResearchRouting.policyFor("cloudflare"))
        assertEquals(ResearchRouting.Kind.LOCAL, ResearchRouting.kindFor("local"))
        assertEquals(ResearchRouting.Kind.CLOUDFLARE, ResearchRouting.kindFor("cloudflare"))
    }

    @Test
    fun routingIsMatchedCaseInsensitivelyAndTrimmed() {
        assertEquals(WorkerRoutingPolicy.AWS_BATCH_CPU, ResearchRouting.policyFor(" AWS_Batch "))
        assertEquals(ResearchRouting.Kind.CLOUDFLARE, ResearchRouting.kindFor("Cloudflare"))
    }

    @Test
    fun unknownRoutingIsReportedNotQueued() {
        assertNull(ResearchRouting.policyFor("quantum"))
        assertEquals(ResearchRouting.Kind.OTHER, ResearchRouting.kindFor("quantum"))
    }

    @Test
    fun blankRoutingCountsAsLocal() {
        val plan = ResearchRouting.plan(listOf(experiment("")))
        assertEquals(1, plan.local.size)
        assertEquals(0, plan.routed.size)
    }

    @Test
    fun planSeparatesQueuedFromKeptLocal() {
        val plan = ResearchRouting.plan(
            listOf(
                experiment("aws_batch"),
                experiment("kaggle"),
                experiment("local"),
                experiment("cloudflare"),
            )
        )
        assertEquals(2, plan.routed.size)
        assertEquals(1, plan.local.size)
        assertEquals(1, plan.cloudflare.size)
        assertTrue(plan.other.isEmpty())
    }

    @Test
    fun summarySaysWhatWasNotRouted() {
        val plan = ResearchRouting.plan(
            listOf(experiment("aws_batch"), experiment("local"), experiment("local"))
        )
        val summary = plan.summary()
        assertTrue(summary.contains("1 routed (aws_batch)"))
        assertTrue(summary.contains("2 local"))
    }

    @Test
    fun emptyPlanSaysSo() {
        assertEquals("no experiments", ResearchRouting.plan(emptyList()).summary())
    }
}

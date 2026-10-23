package com.example.concordance.autonomy

import com.example.concordance.integration.agent.AgentExperiment
import com.example.concordance.integration.queue.WorkerRoutingPolicy

/**
 * Where each agent-designed experiment should run.
 *
 * The agent answers with `routing` on every experiment and the vocabulary is fixed by
 * its prompt: `local`, `aws_batch`, `aws_fargate`, `kaggle`, `cloudflare`, `combo`.
 * The phone can only enqueue work on a heavy backend, so this maps that vocabulary
 * onto [WorkerRoutingPolicy] and keeps an explicit bucket for the cases that are *not*
 * the phone's to schedule. That distinction is the whole point: a run where every
 * experiment is `local` must say "0 routed, 3 local" instead of silently routing
 * nothing and looking broken.
 */
object ResearchRouting {

    enum class Kind { QUEUE, LOCAL, CLOUDFLARE, OTHER }

    data class Target(
        val experiment: AgentExperiment,
        val routing: String,
        val kind: Kind,
        val policy: WorkerRoutingPolicy?,
    )

    data class Plan(val targets: List<Target>) {
        val routed: List<Target> get() = targets.filter { it.kind == Kind.QUEUE }
        val local: List<Target> get() = targets.filter { it.kind == Kind.LOCAL }
        val cloudflare: List<Target> get() = targets.filter { it.kind == Kind.CLOUDFLARE }
        val other: List<Target> get() = targets.filter { it.kind == Kind.OTHER }

        /** One line the UI and the notification can show verbatim. */
        fun summary(): String {
            if (targets.isEmpty()) return "no experiments"
            val parts = mutableListOf<String>()
            if (routed.isNotEmpty()) {
                val backends = routed.map { it.routing }.distinct().joinToString(", ")
                parts += "${routed.size} routed ($backends)"
            }
            if (local.isNotEmpty()) parts += "${local.size} local"
            if (cloudflare.isNotEmpty()) parts += "${cloudflare.size} cloudflare"
            if (other.isNotEmpty()) parts += "${other.size} with unknown routing"
            return parts.joinToString(" · ")
        }
    }

    /**
     * `combo` means the experiment needs more than one backend; the queue here submits
     * the heavier half to Fargate, which is what the original mapping did.
     */
    fun policyFor(routing: String): WorkerRoutingPolicy? =
        when (routing.trim().lowercase()) {
            "aws_batch" -> WorkerRoutingPolicy.AWS_BATCH_CPU
            "aws_fargate", "combo" -> WorkerRoutingPolicy.AWS_FARGATE_GPU
            "kaggle" -> WorkerRoutingPolicy.KAGGLE_CLUSTER_GPU
            else -> null
        }

    fun kindFor(routing: String): Kind = when (routing.trim().lowercase()) {
        "local" -> Kind.LOCAL
        "cloudflare" -> Kind.CLOUDFLARE
        else -> if (policyFor(routing) != null) Kind.QUEUE else Kind.OTHER
    }

    fun plan(experiments: List<AgentExperiment>): Plan = Plan(
        experiments.map { exp ->
            val routing = exp.routing.ifBlank { "local" }
            Target(
                experiment = exp,
                routing = routing,
                kind = kindFor(routing),
                policy = policyFor(routing),
            )
        }
    )
}

package com.example.concordance.autonomy

import com.example.concordance.integration.agent.AgentResearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the last high-power run produced.
 *
 * The foreground service owns the network call, so without a shared store its result
 * only ever reached a notification and a one-line activity entry — the research screen
 * stayed empty and the run looked like it produced nothing. The service publishes here
 * and the view model renders it through the same card the manual research path uses.
 */
data class ResearchRun(
    val goal: String,
    val result: AgentResearchResult? = null,
    val routing: ResearchRouting.Plan? = null,
    val error: String? = null,
    val finishedAt: Long = System.currentTimeMillis(),
)

object ResearchRunStore {
    private val _last = MutableStateFlow<ResearchRun?>(null)
    val last: StateFlow<ResearchRun?> = _last.asStateFlow()

    fun publish(run: ResearchRun) {
        _last.value = run
    }

    fun clear() {
        _last.value = null
    }
}

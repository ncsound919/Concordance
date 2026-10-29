package com.example.concordance.research.experiment

/**
 * The append-only record of what actually happened to a planned run.
 *
 * Every transition is hashed into the previous one, so a record cannot be edited after the fact
 * without breaking the chain. Illegal transitions are refused rather than coerced.
 *
 * A run starts at [RunState.DRAFT]. A confirmatory run must then be **preregistered** before it
 * can be planned - the lock is a first-class event on the chain, not a field on a spec that
 * nobody can later prove was written before the result.
 */
data class RunRecord(
    val runId: String,
    val plan: RunPlan,
    val state: RunState,
    val transitions: List<RunTransition>,
    val preregistration: Preregistration? = null,
) {

    /** Records the lock. Only legal before the run is planned. */
    fun preregister(preregistration: Preregistration, atUtc: String): RunRecord? {
        if (state != RunState.DRAFT) return null
        if (preregistration.specHash != plan.specHash) return null
        val next = advance(
            RunState.PREREGISTERED,
            atUtc,
            "preregistered spec ${preregistration.specHash} by ${preregistration.lockedBy}",
        ) ?: return null
        return next.copy(preregistration = preregistration)
    }

    /** Returns the advanced record, or null when the transition is not legal. */
    fun advance(to: RunState, atUtc: String, note: String = ""): RunRecord? {
        if (!RunStateMachine.canTransition(state, to)) return null
        val previous = transitions.lastOrNull()?.hash ?: GENESIS
        val transition = RunTransition(
            atUtc = atUtc,
            from = state,
            to = to,
            note = note,
            previousHash = previous,
            hash = "",
        ).let { it.copy(hash = it.computeHash()) }
        return copy(state = to, transitions = transitions + transition)
    }

    /** Hash of the whole chain; equal to the last transition hash. */
    fun ledgerHash(): String = transitions.lastOrNull()?.hash ?: GENESIS

    fun isTerminal(): Boolean = state in RunStateMachine.TERMINAL

    fun isPreregistered(): Boolean = preregistration != null

    companion object {
        const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000"

        fun create(runId: String, plan: RunPlan, atUtc: String): RunRecord {
            val first = RunTransition(
                atUtc = atUtc,
                from = null,
                to = RunState.DRAFT,
                note = "run created",
                previousHash = GENESIS,
                hash = "",
            ).let { it.copy(hash = it.computeHash()) }
            return RunRecord(
                runId = runId,
                plan = plan,
                state = RunState.DRAFT,
                transitions = listOf(first),
            )
        }
    }
}

data class RunTransition(
    val atUtc: String,
    val from: RunState?,
    val to: RunState,
    val note: String,
    val previousHash: String,
    val hash: String,
) {
    fun computeHash(): String = ExperimentSpec.sha256Hex(
        listOf(previousHash, atUtc, from?.name ?: "genesis", to.name, note)
            .joinToString("\u001F")
    )
}

enum class RunState {
    DRAFT,
    PREREGISTERED,
    PLANNED,
    APPROVED,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    REFUSED,
    CANCELLED,
    ADJUDICATED,
}

object RunStateMachine {

    val TERMINAL = setOf(RunState.FAILED, RunState.REFUSED, RunState.CANCELLED, RunState.ADJUDICATED)

    private val ALLOWED: Map<RunState, Set<RunState>> = mapOf(
        RunState.DRAFT to setOf(RunState.PREREGISTERED, RunState.PLANNED, RunState.CANCELLED),
        RunState.PREREGISTERED to setOf(RunState.PLANNED, RunState.CANCELLED),
        RunState.PLANNED to setOf(RunState.APPROVED, RunState.REFUSED, RunState.CANCELLED),
        RunState.APPROVED to setOf(RunState.QUEUED, RunState.REFUSED, RunState.CANCELLED),
        RunState.QUEUED to setOf(RunState.RUNNING, RunState.FAILED, RunState.REFUSED, RunState.CANCELLED),
        RunState.RUNNING to setOf(RunState.COMPLETED, RunState.FAILED, RunState.REFUSED, RunState.CANCELLED),
        RunState.COMPLETED to setOf(RunState.ADJUDICATED),
        RunState.FAILED to emptySet(),
        RunState.REFUSED to emptySet(),
        RunState.CANCELLED to emptySet(),
        RunState.ADJUDICATED to emptySet(),
    )

    fun canTransition(from: RunState, to: RunState): Boolean = ALLOWED[from]?.contains(to) == true

    /** The states a UI may offer for a run in [from]. */
    fun nextStates(from: RunState): Set<RunState> = ALLOWED[from] ?: emptySet()
}

package com.example

import com.example.concordance.research.experiment.Backends
import com.example.concordance.research.experiment.CompileResult
import com.example.concordance.research.experiment.ExperimentCompiler
import com.example.concordance.research.experiment.Preregistration
import com.example.concordance.research.experiment.RunPlan
import com.example.concordance.research.experiment.RunRecord
import com.example.concordance.research.experiment.RunState
import com.example.concordance.research.experiment.RunStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunRecordTest {

    private val at = "2026-10-28T12:00:00Z"

    private fun plan(): RunPlan = (
        ExperimentCompiler.compile(
            ExperimentSpecTest.spec(),
            Backends.KAGGLE,
            preregistration = Preregistration(ExperimentSpecTest.spec().sha256(), at, "tap45"),
        ) as CompileResult.Ready
        ).plan

    private fun preregistration(): Preregistration = Preregistration(plan().specHash, at, "tap45")

    private fun newRecord(): RunRecord = RunRecord.create("run-1", plan(), at)

    /** DRAFT -> PREREGISTERED -> PLANNED, the path a confirmatory run must take. */
    private fun planned(): RunRecord =
        newRecord().preregister(preregistration(), at)!!.advance(RunState.PLANNED, at)!!

    @Test
    fun aNewRecordStartsAtDraftWithOneTransition() {
        val record = newRecord()
        assertEquals(RunState.DRAFT, record.state)
        assertEquals(1, record.transitions.size)
        assertEquals(null, record.transitions[0].from)
        assertEquals(RunRecord.GENESIS, record.transitions[0].previousHash)
        assertEquals(64, record.ledgerHash().length)
        assertEquals(false, record.isPreregistered())
    }

    @Test
    fun preregisteringIsAnEventOnTheChain() {
        val draft = newRecord()
        val locked = draft.preregister(preregistration(), at)!!
        assertEquals(RunState.PREREGISTERED, locked.state)
        assertEquals(true, locked.isPreregistered())
        assertEquals(preregistration().specHash, locked.preregistration!!.specHash)
        assertNotEquals(draft.ledgerHash(), locked.ledgerHash())
        assertTrue(locked.transitions.last().note.contains(preregistration().specHash))
    }

    @Test
    fun aPreregistrationForADifferentSpecIsRefused() {
        val wrong = Preregistration("deadbeef".repeat(8), at, "tap45")
        val draft = newRecord()
        assertNull(draft.preregister(wrong, at))
        assertEquals(RunState.DRAFT, draft.state)
        assertEquals(false, draft.isPreregistered())
    }

    @Test
    fun preregistrationIsOnlyLegalBeforePlanning() {
        val planned = planned()
        assertNull(planned.preregister(preregistration(), at))
    }

    @Test
    fun theHappyPathReachesAdjudicated() {
        var record = planned()
        listOf(
            RunState.APPROVED,
            RunState.QUEUED,
            RunState.RUNNING,
            RunState.COMPLETED,
            RunState.ADJUDICATED,
        ).forEach { next ->
            record = record.advance(next, at) ?: error("transition to $next refused")
        }
        assertEquals(RunState.ADJUDICATED, record.state)
        // DRAFT, PREREGISTERED, PLANNED, APPROVED, QUEUED, RUNNING, COMPLETED, ADJUDICATED
        assertEquals(8, record.transitions.size)
        assertTrue(record.isTerminal())
        assertEquals(true, record.isPreregistered())
    }

    @Test
    fun planningCannotBeSkipped() {
        val draft = newRecord()
        assertNull(draft.advance(RunState.APPROVED, at))
        assertNull(draft.advance(RunState.COMPLETED, at))
        assertEquals(RunState.DRAFT, draft.state)
    }

    @Test
    fun terminalRunsHaveNoNextState() {
        val refused = planned().advance(RunState.REFUSED, at)!!
        assertTrue(RunStateMachine.nextStates(refused.state).isEmpty())
        assertNull(refused.advance(RunState.COMPLETED, at))
    }

    @Test
    fun eachTransitionChainsToThePreviousHash() {
        val record = planned()
            .advance(RunState.APPROVED, at)!!
            .advance(RunState.QUEUED, at)!!
        record.transitions.zipWithNext().forEach { (a, b) ->
            assertEquals(a.hash, b.previousHash)
        }
        assertEquals(record.transitions.last().hash, record.ledgerHash())
    }

    @Test
    fun changingTheNoteChangesTheChain() {
        val a = planned().advance(RunState.APPROVED, at, "approved by tap45")!!
        val b = planned().advance(RunState.APPROVED, at, "approved silently")!!
        assertNotEquals(a.ledgerHash(), b.ledgerHash())
    }

    @Test
    fun aCancelledRunCannotBecomeCompleted() {
        val cancelled = planned().advance(RunState.CANCELLED, at)!!
        assertNull(cancelled.advance(RunState.COMPLETED, at))
        assertNotNull(cancelled.ledgerHash())
    }

    @Test
    fun everyNonTerminalStateHasAtLeastOneExit() {
        RunState.values().filter { it !in RunStateMachine.TERMINAL }.forEach { state ->
            assertTrue("$state has no exit", RunStateMachine.nextStates(state).isNotEmpty())
        }
    }
}

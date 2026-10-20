package com.example

import com.example.concordance.ui.navigation.NavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationStateTest {

    @Test
    fun startsOnAdjudicateWithMoreClosed() {
        val state = NavigationState()
        assertEquals(NavigationDestination.ADJUDICATE, state.current)
        assertFalse(state.showMore)
    }

    @Test
    fun selectMovesAndClosesMore() {
        val state = NavigationState()
        state.openMore()
        state.select(NavigationDestination.REGISTRY)
        assertEquals(NavigationDestination.REGISTRY, state.current)
        assertFalse(state.showMore)
    }

    @Test
    fun backFromSecondaryReturnsHomeAndIsConsumed() {
        val state = NavigationState()
        state.select(NavigationDestination.INTEGRATIONS)
        assertTrue(state.back())
        assertEquals(NavigationDestination.ADJUDICATE, state.current)
    }

    @Test
    fun backFromHomeIsNotConsumed() {
        val state = NavigationState()
        assertFalse(state.back())
    }

    @Test
    fun backClosesMoreBeforeNavigating() {
        val state = NavigationState()
        state.select(NavigationDestination.CAPSULES)
        state.openMore()
        assertTrue(state.back())
        assertFalse(state.showMore)
        // The first back press only closes the sheet.
        assertEquals(NavigationDestination.CAPSULES, state.current)
        assertTrue(state.back())
        assertEquals(NavigationDestination.ADJUDICATE, state.current)
    }

    @Test
    fun primaryNavigationIsCappedAtFive() {
        assertEquals(5, NavigationDestination.primary.size)
        assertEquals(2, NavigationDestination.secondary.size)
    }

    @Test
    fun destinationsHaveUniqueRoutes() {
        val routes = NavigationDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }
}

package com.example.concordance.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.example.NavigationDestination

/**
 * Navigation state for the app shell, extracted from the composable so the
 * back-press precedence can be unit-tested without a ViewModel or Activity.
 *
 * Precedence on back:
 *   1. close the "More" sheet
 *   2. return to the home destination (Adjudicate)
 *   3. otherwise not consumed (the system exits the app)
 */
class NavigationState(initial: NavigationDestination = NavigationDestination.ADJUDICATE) {
    var current by mutableStateOf(initial)
        private set

    var showMore by mutableStateOf(false)
        private set

    fun select(destination: NavigationDestination) {
        current = destination
        showMore = false
    }

    fun openMore() {
        showMore = true
    }

    fun closeMore() {
        showMore = false
    }

    /** Returns true when this state consumed the back press. */
    fun back(): Boolean {
        if (showMore) {
            showMore = false
            return true
        }
        if (current != NavigationDestination.ADJUDICATE) {
            current = NavigationDestination.ADJUDICATE
            return true
        }
        return false
    }

    companion object {
        val Saver: Saver<NavigationState, Any> = listSaver(
            save = { listOf(it.current.route, it.showMore.toString()) },
            restore = { saved ->
                val route = saved[0] as String
                val more = (saved[1] as String).toBoolean()
                NavigationState(
                    NavigationDestination.entries.firstOrNull { it.route == route }
                        ?: NavigationDestination.ADJUDICATE
                ).apply { showMore = more }
            },
        )
    }
}

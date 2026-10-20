package com.example.concordance.integration.supabase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide bus for sync conflicts.
 *
 * The sync worker runs in WorkManager (a separate process-ish context) and the
 * ViewModel lives in the activity. This bus lets the worker publish conflicts
 * and the UI observe them without a direct reference between the two.
 */
object SyncConflictBus {
    private val _conflicts = MutableStateFlow<List<SyncConflict>>(emptyList())
    val conflicts: StateFlow<List<SyncConflict>> = _conflicts.asStateFlow()

    fun report(conflict: SyncConflict) {
        _conflicts.value = _conflicts.value + conflict
    }

    fun clear() {
        _conflicts.value = emptyList()
    }

    fun remove(conflict: SyncConflict) {
        _conflicts.value = _conflicts.value - conflict
    }
}

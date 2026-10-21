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

    /** Resolutions recorded by the UI, keyed by "entityType:entityId". */
    private val _resolutions = MutableStateFlow<Map<String, String>>(emptyMap())
    val resolutions: StateFlow<Map<String, String>> = _resolutions.asStateFlow()

    fun report(conflict: SyncConflict) {
        _conflicts.value = _conflicts.value + conflict
    }

    fun clear() {
        _conflicts.value = emptyList()
    }

    fun remove(conflict: SyncConflict) {
        _conflicts.value = _conflicts.value - conflict
    }

    /**
     * Record a resolution choice for a conflict and remove it from the active list.
     * The actual data mutation (keep-local push / accept-server pull / open challenge)
     * is performed by [SupabaseSyncManager.resolveConflict] on the next sync cycle,
     * which reads [resolutions] to determine what to do.
     */
    fun resolve(conflict: SyncConflict, resolution: String) {
        val key = "${conflict.entityType}:${conflict.entityId}"
        _resolutions.value = _resolutions.value + (key to resolution)
        remove(conflict)
    }

    fun resolutionFor(entityType: String, entityId: String): String? =
        _resolutions.value["${entityType}:${entityId}"]
}

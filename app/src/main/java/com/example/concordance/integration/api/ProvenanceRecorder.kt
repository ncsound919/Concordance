package com.example.concordance.integration.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * In-memory log of the most recent external requests, newest first. Displayed in
 * the Integrations screen and embedded into claim capsules for auditability.
 *
 * When a [persist] callback is registered (see [setPersist]), every recorded
 * provenance row is also written to the local database so the audit trail
 * survives process restarts and can be linked to a capsule.
 */
object ProvenanceRecorder {

    private const val MAX_RECORDS = 200

    private val _records = MutableStateFlow<List<Provenance>>(emptyList())
    val records: StateFlow<List<Provenance>> = _records.asStateFlow()

    private val _recordIds = MutableStateFlow<List<Long>>(emptyList())
    val recordIds: StateFlow<List<Long>> = _recordIds.asStateFlow()

    @Volatile
    private var persist: (suspend (Provenance) -> Long)? = null

    fun setPersist(callback: (suspend (Provenance) -> Long)?) {
        persist = callback
    }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun nowUtc(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    suspend fun record(provenance: Provenance) {
        val pk = persist?.invoke(provenance) ?: 0L
        _records.value = (listOf(provenance) + _records.value).take(MAX_RECORDS)
        _recordIds.value = (listOf(pk) + _recordIds.value).take(MAX_RECORDS)
    }

    fun clear() {
        _records.value = emptyList()
        _recordIds.value = emptyList()
    }
}

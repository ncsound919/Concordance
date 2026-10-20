package com.example.concordance.integration.byok

import com.example.concordance.integration.rotation.LicenseClass
import androidx.core.content.edit

/**
 * Tracks which license classes the user has acknowledged. ACADEMIC_ONLY and
 * PAID providers require an explicit acknowledgment before they can run.
 */
interface LicenseStore {
    fun isAcknowledged(license: LicenseClass): Boolean
    fun acknowledge(license: LicenseClass)
    fun revoke(license: LicenseClass)
}

class InMemoryLicenseStore : LicenseStore {
    private val acknowledged = mutableSetOf<LicenseClass>()
    override fun isAcknowledged(license: LicenseClass): Boolean = license in acknowledged
    override fun acknowledge(license: LicenseClass) { acknowledged.add(license) }
    override fun revoke(license: LicenseClass) { acknowledged.remove(license) }
}

fun createLicenseStore(context: android.content.Context): LicenseStore {
    return try {
        SharedPreferencesLicenseStore(
            context.getSharedPreferences("concordance_licenses", android.content.Context.MODE_PRIVATE)
        )
    } catch (e: Exception) {
        InMemoryLicenseStore()
    }
}

private class SharedPreferencesLicenseStore(private val prefs: android.content.SharedPreferences) : LicenseStore {
    private fun key(license: LicenseClass) = "ack_${license.name}"
    override fun isAcknowledged(license: LicenseClass): Boolean = prefs.getBoolean(key(license), false)
    override fun acknowledge(license: LicenseClass) { prefs.edit { putBoolean(key(license), true) } }
    override fun revoke(license: LicenseClass) { prefs.edit { putBoolean(key(license), false) } }
}

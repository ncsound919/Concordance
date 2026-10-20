package com.example.concordance.integration.byok

import androidx.core.content.edit
/**
 * Storage for bring-your-own API keys. Implementations are responsible for
 * keeping secrets out of plaintext storage (EncryptedSharedPreferences in
 * production, an in-memory map in tests).
 */
interface KeyStore {
    fun getKey(providerId: String): String?
    fun setKey(providerId: String, key: String?)
    fun hasKey(providerId: String): Boolean = !getKey(providerId).isNullOrBlank()
}

/**
 * In-memory implementation used by unit tests and previews.
 */
class InMemoryKeyStore : KeyStore {
    private val map = mutableMapOf<String, String?>()
    override fun getKey(providerId: String): String? = map[providerId]
    override fun setKey(providerId: String, key: String?) {
        if (key.isNullOrBlank()) map.remove(providerId) else map[providerId] = key
    }
}

/**
 * Production factory: EncryptedSharedPreferences-backed store, falling back to
 * an in-memory store if encryption is unavailable (e.g. on a test device).
 */
fun createKeyStore(context: android.content.Context): KeyStore {
    return try {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "concordance_byok_keys",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        EncryptedSharedPreferencesKeyStore(prefs)
    } catch (e: Exception) {
        InMemoryKeyStore()
    }
}

private class EncryptedSharedPreferencesKeyStore(
    private val prefs: android.content.SharedPreferences
) : KeyStore {
    override fun getKey(providerId: String): String? = prefs.getString(providerId, null)
    override fun setKey(providerId: String, key: String?) {
        prefs.edit {
            if (key.isNullOrBlank()) remove(providerId) else putString(providerId, key)
        }
    }
}

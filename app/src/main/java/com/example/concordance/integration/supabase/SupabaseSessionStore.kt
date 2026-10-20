package com.example.concordance.integration.supabase

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface SupabaseSessionStore {
    fun load(): SupabaseSession?
    fun save(session: SupabaseSession?)
    fun clear() = save(null)
}

class InMemorySupabaseSessionStore : SupabaseSessionStore {
    private var session: SupabaseSession? = null
    override fun load(): SupabaseSession? = session
    override fun save(session: SupabaseSession?) { this.session = session }
}

fun createSupabaseSessionStore(context: Context): SupabaseSessionStore {
    return try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            "concordance_supabase_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        EncryptedSupabaseSessionStore(prefs)
    } catch (e: Exception) {
        InMemorySupabaseSessionStore()
    }
}

private class EncryptedSupabaseSessionStore(private val prefs: SharedPreferences) : SupabaseSessionStore {
    override fun load(): SupabaseSession? {
        val json = prefs.getString("session", null) ?: return null
        return try { SupabaseSession.fromJson(org.json.JSONObject(json)) } catch (e: Exception) { null }
    }
    override fun save(session: SupabaseSession?) {
        prefs.edit {
            if (session == null) remove("session")
            else putString("session", session.toJson().toString())
        }
    }
}
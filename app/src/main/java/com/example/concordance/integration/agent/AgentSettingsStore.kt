package com.example.concordance.integration.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * On-device settings for the agent connection.
 *
 * Default points at the host loopback as seen from an Android emulator
 * (`10.0.2.2`). On a physical device, set the LAN address of the machine running
 * the agent — the agent is self-hosted, so nothing here leaves your network.
 *
 * The API key and endpoint are stored in EncryptedSharedPreferences. If the
 * keystore is unavailable (rare, e.g. a broken test device) the store falls back
 * to the legacy plain file so the app still works; [save] clears the legacy copy.
 */
class AgentSettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val legacy: SharedPreferences =
        appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        legacy
    }

    private fun readString(key: String, default: String?): String? =
        prefs.getString(key, null) ?: legacy.getString(key, default)

    fun load(): AgentConfig = AgentConfig(
        baseUrl = readString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL,
        apiKey = readString(KEY_API_KEY, null),
        timeoutSeconds = prefs.getLong(KEY_TIMEOUT_SECONDS, legacy.getLong(KEY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)),
        modelName = readString(KEY_MODEL_NAME, null)
    )

    fun save(config: AgentConfig) {
        prefs.edit {
            putString(KEY_URL, config.normalizedBaseUrl)
            putString(KEY_API_KEY, config.apiKey)
            putLong(KEY_TIMEOUT_SECONDS, config.timeoutSeconds)
            putString(KEY_MODEL_NAME, config.modelName)
        }
        if (prefs !== legacy) {
            legacy.edit { clear() }
        }
    }

    /** Base URL of the Concordance website used for community publication. */
    fun communityBaseUrl(): String =
        readString(KEY_COMMUNITY_URL, DEFAULT_COMMUNITY_URL)?.takeIf { it.isNotBlank() } ?: DEFAULT_COMMUNITY_URL

    fun setCommunityBaseUrl(url: String) {
        prefs.edit { putString(KEY_COMMUNITY_URL, url.trimEnd('/')) }
        if (prefs !== legacy) legacy.edit { remove(KEY_COMMUNITY_URL) }
    }

    /** Whether new results open in the plain-language view first. */
    fun easyModeByDefault(): Boolean =
        prefs.getBoolean(KEY_EASY_DEFAULT, legacy.getBoolean(KEY_EASY_DEFAULT, true))

    fun setEasyModeByDefault(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_EASY_DEFAULT, enabled) }
        if (prefs !== legacy) legacy.edit { remove(KEY_EASY_DEFAULT) }
    }

    /** Remember the operator's acknowledgement that uploads may leave the phone. */
    fun hasAcknowledgedUpload(): Boolean =
        prefs.getBoolean(KEY_UPLOAD_ACK, legacy.getBoolean(KEY_UPLOAD_ACK, false))

    fun setAcknowledgedUpload(ack: Boolean) {
        prefs.edit { putBoolean(KEY_UPLOAD_ACK, ack) }
        if (prefs !== legacy) legacy.edit { remove(KEY_UPLOAD_ACK) }
    }

    companion object {
        /** The deployed agent. New installs start here (no workstation in the path). */
        const val CLOUD_AGENT_URL = "https://tap4500--concordance-agent-fastapi-app.us-east.modal.run"
        /** The deployed website, used for community publication. */
        const val CLOUD_COMMUNITY_URL = "https://concordance-web.tap4500.workers.dev"

        /** Emulator alias for a locally run agent, kept for development. */
        const val LOCAL_AGENT_URL = "http://10.0.2.2:8000"
        const val LOCAL_COMMUNITY_URL = "http://10.0.2.2:3001"

        const val DEFAULT_URL = CLOUD_AGENT_URL
        const val DEFAULT_COMMUNITY_URL = CLOUD_COMMUNITY_URL
        const val DEFAULT_TIMEOUT_SECONDS = 90L
        private const val SECURE_PREFS = "agent_settings_secure"
        private const val LEGACY_PREFS = "agent_settings"
        private const val KEY_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_TIMEOUT_SECONDS = "timeout_seconds"
        private const val KEY_EASY_DEFAULT = "easy_mode_default"
        private const val KEY_UPLOAD_ACK = "upload_ack"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_COMMUNITY_URL = "community_base_url"
    }
}

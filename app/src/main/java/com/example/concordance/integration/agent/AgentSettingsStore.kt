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
 * keystore is unavailable, settings are kept in memory only; no plaintext
 * fallback is used, so secrets are never written to unencrypted storage.
 */
class AgentSettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val legacyPrefs: SharedPreferences =
        appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
    private val memory = mutableMapOf<String, Any?>()

    private val prefs: SharedPreferences? = try {
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
        null
    }

    init {
        // Migrate any legacy plaintext settings into encrypted storage, then wipe
        // the plaintext copy so secrets do not linger on disk.
        val encrypted = prefs
        if (encrypted != null && legacyPrefs.all.isNotEmpty()) {
            encrypted.edit {
                legacyPrefs.all.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Long -> putLong(key, value)
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Float -> putFloat(key, value)
                    }
                }
            }
        }
        legacyPrefs.edit { clear() }
    }

    private fun getString(key: String, default: String?): String? {
        val p = prefs
        if (p != null && p.contains(key)) return p.getString(key, default)
        return (memory[key] as? String) ?: default
    }

    private fun getLong(key: String, default: Long): Long {
        val p = prefs
        if (p != null && p.contains(key)) return p.getLong(key, default)
        return (memory[key] as? Long) ?: default
    }

    private fun getBoolean(key: String, default: Boolean): Boolean {
        val p = prefs
        if (p != null && p.contains(key)) return p.getBoolean(key, default)
        return (memory[key] as? Boolean) ?: default
    }

    fun load(): AgentConfig = AgentConfig(
        baseUrl = getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL,
        apiKey = getString(KEY_API_KEY, null),
        timeoutSeconds = getLong(KEY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS),
        modelName = getString(KEY_MODEL_NAME, null)
    )

    fun save(config: AgentConfig) {
        val p = prefs
        if (p != null) {
            p.edit {
                putString(KEY_URL, config.normalizedBaseUrl)
                putString(KEY_API_KEY, config.apiKey)
                putLong(KEY_TIMEOUT_SECONDS, config.timeoutSeconds)
                putString(KEY_MODEL_NAME, config.modelName)
            }
        } else {
            memory[KEY_URL] = config.normalizedBaseUrl
            memory[KEY_API_KEY] = config.apiKey
            memory[KEY_TIMEOUT_SECONDS] = config.timeoutSeconds
            memory[KEY_MODEL_NAME] = config.modelName
        }
    }

    /** Base URL of the Concordance website used for community publication. */
    fun communityBaseUrl(): String =
        (getString(KEY_COMMUNITY_URL, DEFAULT_COMMUNITY_URL)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_COMMUNITY_URL)

    fun setCommunityBaseUrl(url: String) {
        val clean = url.trimEnd('/')
        val p = prefs
        if (p != null) {
            p.edit { putString(KEY_COMMUNITY_URL, clean) }
        } else {
            memory[KEY_COMMUNITY_URL] = clean
        }
    }

    /** Whether new results open in the plain-language view first. */
    fun easyModeByDefault(): Boolean = getBoolean(KEY_EASY_DEFAULT, true)

    fun setEasyModeByDefault(enabled: Boolean) {
        val p = prefs
        if (p != null) {
            p.edit { putBoolean(KEY_EASY_DEFAULT, enabled) }
        } else {
            memory[KEY_EASY_DEFAULT] = enabled
        }
    }

    /** Remember the operator's acknowledgement that uploads may leave the phone. */
    fun hasAcknowledgedUpload(): Boolean = getBoolean(KEY_UPLOAD_ACK, false)

    fun setAcknowledgedUpload(ack: Boolean) {
        val p = prefs
        if (p != null) {
            p.edit { putBoolean(KEY_UPLOAD_ACK, ack) }
        } else {
            memory[KEY_UPLOAD_ACK] = ack
        }
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
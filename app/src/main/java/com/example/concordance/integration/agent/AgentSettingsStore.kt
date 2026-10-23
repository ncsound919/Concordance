package com.example.concordance.integration.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * On-device settings for the agent connection.
 *
 * The release default is the operator's cloud deployment, because the supported
 * runtime is a phone with no laptop in the loop. A self-hosted agent is still
 * supported: change the base URL to the LAN address (or a TLS host), and the
 * cleartext policy in `network_security_config.xml` keeps that exception narrow.
 *
 * The community/website URL has no default: no website host is deployed yet, and
 * a loopback default would look configured on a phone while failing every
 * publish. Publishing asks for the URL instead.
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

    /**
     * Older builds kept the endpoint and API key in a plaintext prefs file. Move anything left
     * there into the encrypted store once and delete the plaintext copy, so an upgraded device
     * does not keep a readable key around.
     */
    init {
        if (prefs !== legacy) {
            try {
                val legacyValues = legacy.all
                if (legacyValues.isNotEmpty()) {
                    prefs.edit {
                        legacyValues.forEach { (key, value) ->
                            when (value) {
                                is String -> putString(key, value)
                                is Long -> putLong(key, value)
                                is Int -> putInt(key, value)
                                is Boolean -> putBoolean(key, value)
                                is Float -> putFloat(key, value)
                                else -> Unit
                            }
                        }
                    }
                    legacy.edit { clear() }
                }
            } catch (e: Exception) {
                // If the secure store cannot be written, leave the old file alone; the app still
                // reads through the fallback below.
            }
        }
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
        /**
         * The operator's deployed adjudication agent. Verified reachable at
         * `GET /health` (200, `{"status":"ok"}`) on 2026-10-22.
         */
        const val CLOUD_AGENT_URL =
            "https://tap4500--concordance-agent-fastapi-app.us-east.modal.run"

        /** New installs point at the cloud agent; the field stays editable. */
        const val DEFAULT_URL = CLOUD_AGENT_URL

        /** Set deliberately: publishing has no working default until the site is hosted. */
        const val DEFAULT_COMMUNITY_URL = ""
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

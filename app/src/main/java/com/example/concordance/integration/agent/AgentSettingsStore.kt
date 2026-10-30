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
 * keystore is unavailable the store fails closed to an in-memory copy: the
 * settings work until the process dies and then require re-entry, and secrets
 * are never written to disk in plaintext. Any legacy plaintext values are
 * migrated into the encrypted store (when available) or wiped (when not).
 */
class AgentSettingsStore(context: Context) {

    private val appContext = context.applicationContext

    private val secure: SharedPreferences? = try {
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

    private val prefs: SharedPreferences = secure ?: InMemorySharedPreferences()

    init {
        // Migrate or destroy any plaintext copy left by older builds.
        migrateOrDestroyLegacy()
    }

    private fun migrateOrDestroyLegacy() {
        val legacy = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return
        if (secure != null) {
            val existing = secure.all.keys
            secure.edit {
                legacy.all.forEach { (key, value) ->
                    if (key in existing) return@forEach
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Float -> putFloat(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
                    }
                }
            }
        }
        // Wipe the plaintext copy in both cases: it is now redundant (secure
        // store) or it is the very thing we refuse to keep (keystore failed).
        legacy.edit { clear() }
    }

    private fun readString(key: String, default: String?): String? =
        prefs.getString(key, null) ?: default

    fun load(): AgentConfig = AgentConfig(
        baseUrl = readString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL,
        apiKey = readString(KEY_API_KEY, null),
        timeoutSeconds = prefs.getLong(KEY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS),
        modelName = readString(KEY_MODEL_NAME, null)
    )

    fun save(config: AgentConfig) {
        prefs.edit {
            putString(KEY_URL, config.normalizedBaseUrl)
            putString(KEY_API_KEY, config.apiKey)
            putLong(KEY_TIMEOUT_SECONDS, config.timeoutSeconds)
            putString(KEY_MODEL_NAME, config.modelName)
        }
    }

    /** Base URL of the Concordance website used for community publication. */
    fun communityBaseUrl(): String =
        readString(KEY_COMMUNITY_URL, DEFAULT_COMMUNITY_URL)?.takeIf { it.isNotBlank() } ?: DEFAULT_COMMUNITY_URL

    fun setCommunityBaseUrl(url: String) {
        prefs.edit { putString(KEY_COMMUNITY_URL, url.trimEnd('/')) }
    }

    /** Whether new results open in the plain-language view first. */
    fun easyModeByDefault(): Boolean =
        prefs.getBoolean(KEY_EASY_DEFAULT, true)

    fun setEasyModeByDefault(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_EASY_DEFAULT, enabled) }
    }

    /** Remember the operator's acknowledgement that uploads may leave the phone. */
    fun hasAcknowledgedUpload(): Boolean =
        prefs.getBoolean(KEY_UPLOAD_ACK, false)

    fun setAcknowledgedUpload(ack: Boolean) {
        prefs.edit { putBoolean(KEY_UPLOAD_ACK, ack) }
    }

    companion object {
        /**
         * A self-hosted agent reachable from an Android emulator. New installs
         * default here: nothing is sent anywhere until the operator points the
         * app at their own machine (or explicitly opts into another endpoint).
         */
        const val LOCAL_AGENT_URL = "http://10.0.2.2:8000"
        /** The deployed agent. Kept only as an explicit opt-in destination. */
        const val CLOUD_AGENT_URL = "https://tap4500--concordance-agent-fastapi-app.us-east.modal.run"
        /** The deployed website, used for community publication. */
        const val CLOUD_COMMUNITY_URL = "https://concordance-web.tap4500.workers.dev"

        const val LOCAL_COMMUNITY_URL = "http://10.0.2.2:3001"

        const val DEFAULT_URL = LOCAL_AGENT_URL
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

/**
 * Process-lifetime [SharedPreferences] used when the keystore-backed store
 * cannot be created. Values are never persisted; a restart requires re-entry.
 */
internal class InMemorySharedPreferences : SharedPreferences {

    private val values = mutableMapOf<String, Any?>()
    private val lock = Any()

    override fun getAll(): Map<String, *> = synchronized(lock) { HashMap(values) }

    override fun getString(key: String?, defValue: String?): String? =
        synchronized(lock) { values[key] as? String ?: defValue }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        synchronized(lock) { values[key] as? MutableSet<String> ?: defValues }

    override fun getInt(key: String?, defValue: Int): Int =
        synchronized(lock) { values[key] as? Int ?: defValue }

    override fun getLong(key: String?, defValue: Long): Long =
        synchronized(lock) { values[key] as? Long ?: defValue }

    override fun getFloat(key: String?, defValue: Float): Float =
        synchronized(lock) { values[key] as? Float ?: defValue }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        synchronized(lock) { values[key] as? Boolean ?: defValue }

    override fun contains(key: String?): Boolean = synchronized(lock) { values.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            pending[requireNotNull(key)] = value; return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            pending[requireNotNull(key)] = values; return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { pending[requireNotNull(key)] = value; return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { pending[requireNotNull(key)] = value; return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { pending[requireNotNull(key)] = value; return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { pending[requireNotNull(key)] = value; return this }
        override fun remove(key: String?): SharedPreferences.Editor { pending[requireNotNull(key)] = REMOVE; return this }

        override fun clear(): SharedPreferences.Editor { clearRequested = true; return this }

        override fun commit(): Boolean { apply(); return true }

        override fun apply() {
            synchronized(lock) {
                if (clearRequested) values.clear()
                pending.forEach { (key, value) ->
                    if (value === REMOVE) values.remove(key) else values[key] = value
                }
            }
        }
    }

    private companion object {
        val REMOVE = Any()
    }
}

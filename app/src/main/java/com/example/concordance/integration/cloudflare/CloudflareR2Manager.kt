package com.example.concordance.integration.cloudflare

import com.example.concordance.integration.api.HttpJsonClient
import com.example.concordance.integration.api.ProvenanceRecorder
import com.example.concordance.integration.api.RateLimitSpec
import com.example.concordance.integration.rotation.ApiRotationManager
import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import com.example.concordance.model.Capsule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * Cloudflare R2 storage manager (S3-compatible). Mirrors AwsCloudManager but
 * targets R2 for cheaper egress and 24/7 durability. Real calls go through
 * HttpJsonClient when a bearer token / API token is configured; otherwise the
 * manager runs in simulation mode (mirroring the AWS manager's behaviour) so
 * the app stays usable without credentials.
 */
object CloudflareR2Manager {

    enum class R2SyncStatus(val label: String, val colorHex: Long) {
        CONNECTED("Connected to Cloudflare R2", 0xFF00E676),
        SYNCING("Syncing Capsule to R2", 0xFF00E5FF),
        SYNCED("Synced & Verified (R2)", 0xFF00E676),
        SIMULATED("Simulated - nothing uploaded", 0xFFFFD740),
        DISCONNECTED("R2 Offline / Unconfigured", 0xFFFFD740)
    }

    data class R2SyncedObject(
        val r2Key: String,
        val bucket: String,
        val accountId: String,
        val sizeBytes: Long,
        val sha256Etag: String,
        val publicUrl: String,
        val lastModifiedUtc: String,
        val capsuleId: String,
        /** True when this object describes a dry run, not a completed upload. */
        val simulated: Boolean = false
    )

    private val _accountId = MutableStateFlow("")
    val accountId: StateFlow<String> = _accountId.asStateFlow()

    private val _bucketName = MutableStateFlow("concordance-capsules-r2")
    val bucketName: StateFlow<String> = _bucketName.asStateFlow()

    private val _apiToken = MutableStateFlow<String?>(null)
    val apiToken: StateFlow<String?> = _apiToken.asStateFlow()

    /** Optional override endpoint (defaults to the R2 S3-compatible endpoint). */
    private val _endpoint = MutableStateFlow<String?>(null)
    val endpoint: StateFlow<String?> = _endpoint.asStateFlow()

    private val _syncStatus = MutableStateFlow(R2SyncStatus.DISCONNECTED)
    val syncStatus: StateFlow<R2SyncStatus> = _syncStatus.asStateFlow()

    private val _syncedObjects = MutableStateFlow<List<R2SyncedObject>>(emptyList())
    val syncedObjects: StateFlow<List<R2SyncedObject>> = _syncedObjects.asStateFlow()

    /** True when a real R2 API token is configured. */
    val isConfigured: Boolean get() = !_apiToken.value.isNullOrBlank() && _accountId.value.isNotBlank()

    fun updateConfig(accountId: String, bucket: String, apiToken: String?, endpoint: String? = null) {
        _accountId.value = accountId
        _bucketName.value = bucket
        _apiToken.value = apiToken?.takeIf { it.isNotBlank() }
        _endpoint.value = endpoint?.takeIf { it.isNotBlank() }
        _syncStatus.value = if (isConfigured) R2SyncStatus.CONNECTED else R2SyncStatus.DISCONNECTED
    }

    private fun sha256Hex(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun syncCapsuleToR2(capsule: Capsule): R2SyncedObject {
        val provider = ApiRotationManager.getNextProvider(ProviderCategory.CLOUD_STORAGE)
        _syncStatus.value = R2SyncStatus.SYNCING

        val r2Key = "vault/2026/${capsule.id}/capsule.json"
        val etag = sha256Hex(capsule.rawJson)
        val start = System.currentTimeMillis()

        // Real path: when an endpoint + token are configured, perform an actual PUT.
        val ep = _endpoint.value
        val token = _apiToken.value
        if (isConfigured && ep != null) {
            val url = "$ep/${_bucketName.value}/$r2Key"
            val result = HttpJsonClient.putJson(
                providerId = provider.id,
                url = url,
                queryLabel = "r2-put:${capsule.id}",
                spec = RateLimitSpec(requestsPerSecond = 10.0, burst = 10),
                licenseClass = LicenseClass.PAID,
                jsonBody = capsule.rawJson,
                authToken = token
            )
            if (result != null) {
                val synced = R2SyncedObject(
                    r2Key = r2Key,
                    bucket = _bucketName.value,
                    accountId = _accountId.value,
                    sizeBytes = capsule.rawJson.length.toLong(),
                    sha256Etag = etag,
                    publicUrl = "$ep/${_bucketName.value}/$r2Key",
                    lastModifiedUtc = ProvenanceRecorder.nowUtc(),
                    capsuleId = capsule.id
                )
                _syncedObjects.value = listOf(synced) + _syncedObjects.value.filter { it.capsuleId != capsule.id }
                _syncStatus.value = R2SyncStatus.SYNCED
                ApiRotationManager.recordSuccess(provider.id, System.currentTimeMillis() - start)
                return synced
            }
            // Real call failed; fall through to simulation so the UI stays usable.
        }

        // Dry-run fallback: the UI must show this as simulated, never as synced.
        delay(150)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = sdf.format(Date())
        val dryRun = R2SyncedObject(
            r2Key = r2Key,
            bucket = _bucketName.value,
            accountId = _accountId.value.ifBlank { "unconfigured" },
            sizeBytes = capsule.rawJson.length.toLong(),
            sha256Etag = etag,
            publicUrl = "",
            lastModifiedUtc = timestamp,
            capsuleId = capsule.id,
            simulated = true
        )
        _syncedObjects.value = listOf(dryRun) + _syncedObjects.value.filter { it.capsuleId != capsule.id }
        _syncStatus.value = R2SyncStatus.SIMULATED
        return dryRun
    }

    fun removeSyncedObject(capsuleId: String) {
        _syncedObjects.value = _syncedObjects.value.filter { it.capsuleId != capsuleId }
    }
}

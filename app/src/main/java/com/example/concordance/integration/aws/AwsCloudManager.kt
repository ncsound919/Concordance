package com.example.concordance.integration.aws

import com.example.concordance.integration.rotation.ApiRotationManager
import com.example.concordance.integration.rotation.ProviderCategory
import com.example.concordance.model.Capsule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

enum class AwsSyncStatus(val label: String, val colorHex: Long) {
    CONNECTED("Connected to AWS S3", 0xFF00E676),
    SYNCING("Syncing Capsule to S3 Vault", 0xFF00E5FF),
    SYNCED("Synced & Verified", 0xFF00E676),
    SIMULATED("Simulated - nothing uploaded", 0xFFFFD740),
    DISCONNECTED("AWS Offline / Unconfigured", 0xFFFFD740)
}

data class S3SyncedObject(
    val s3Key: String,
    val bucket: String,
    val region: String,
    val sizeBytes: Long,
    val sha256Etag: String,
    /** Real SigV4 presigned GET URL, empty when the object was not uploaded. */
    val presignedUrl: String,
    val lastModifiedUtc: String,
    val capsuleId: String,
    /** True when this object describes a dry run, not a completed upload. */
    val simulated: Boolean = false,
    /** HTTP status and body from a failed upload, so a misconfiguration is diagnosable. */
    val errorDetail: String? = null
)

object AwsCloudManager {

    private val _accessKey = MutableStateFlow<String?>(null)
    val accessKey: StateFlow<String?> = _accessKey.asStateFlow()

    private val _secretKey = MutableStateFlow<String?>(null)
    val secretKey: StateFlow<String?> = _secretKey.asStateFlow()

    /**
     * Session token for temporary credentials (STS / Cognito Identity). Long-lived IAM user keys
     * are still accepted, but short-lived credentials are the supported way to ship a client.
     */
    private val _sessionToken = MutableStateFlow<String?>(null)
    val sessionToken: StateFlow<String?> = _sessionToken.asStateFlow()

    private val _regionCfg = MutableStateFlow("us-east-1")
    val region: StateFlow<String> = _regionCfg.asStateFlow()
    val regionCfg: StateFlow<String> = _regionCfg.asStateFlow()

    private val _bucketCfg = MutableStateFlow("concordance-capsules")
    val bucketName: StateFlow<String> = _bucketCfg.asStateFlow()
    val bucketCfg: StateFlow<String> = _bucketCfg.asStateFlow()

    private val _endpoint = MutableStateFlow<String?>(null)
    val endpoint: StateFlow<String?> = _endpoint.asStateFlow()

    private val _syncStatus = MutableStateFlow(AwsSyncStatus.DISCONNECTED)
    val syncStatus: StateFlow<AwsSyncStatus> = _syncStatus.asStateFlow()

    private val _syncedObjects = MutableStateFlow<List<S3SyncedObject>>(emptyList())
    val syncedObjects: StateFlow<List<S3SyncedObject>> = _syncedObjects.asStateFlow()

    val isConfigured: Boolean get() = !_accessKey.value.isNullOrBlank() && !_secretKey.value.isNullOrBlank()

    fun updateConfig(
        accessKey: String?,
        secretKey: String?,
        region: String? = null,
        bucket: String? = null,
        endpoint: String? = null,
        sessionToken: String? = null
    ) {
        _accessKey.value = accessKey?.takeIf { it.isNotBlank() }
        _secretKey.value = secretKey?.takeIf { it.isNotBlank() }
        _sessionToken.value = sessionToken?.takeIf { it.isNotBlank() }
        if (!region.isNullOrBlank()) _regionCfg.value = region
        if (!bucket.isNullOrBlank()) _bucketCfg.value = bucket
        _endpoint.value = endpoint?.takeIf { it.isNotBlank() }
        _syncStatus.value = if (isConfigured) AwsSyncStatus.CONNECTED else AwsSyncStatus.DISCONNECTED
    }

    /** Drop credentials from memory. Called on sign-out so a shared device keeps nothing. */
    fun clearCredentials() {
        _accessKey.value = null
        _secretKey.value = null
        _sessionToken.value = null
        _syncStatus.value = AwsSyncStatus.DISCONNECTED
    }

    /** Custom endpoints (R2, MinIO, a mock server) use path-style addressing. */
    private fun pathStyle(): Boolean = _endpoint.value != null

    /**
     * Uploads and syncs an adjudicated claim capsule to the AWS S3 Cloud Vault.
     * Generates a presigned URL valid for independent scientific review.
     */
    suspend fun syncCapsuleToS3(capsule: Capsule): S3SyncedObject {
        val provider = ApiRotationManager.getNextProvider(ProviderCategory.CLOUD_STORAGE)
        _syncStatus.value = AwsSyncStatus.SYNCING

        val region = _regionCfg.value
        val bucket = _bucketCfg.value
        val s3Key = "vault/2026/${capsule.id}/capsule.json"
        val start = System.currentTimeMillis()

        // Real path: SigV4-signed PUT when credentials are configured.
        val ak = _accessKey.value
        val sk = _secretKey.value
        var failure: String? = null
        if (isConfigured && ak != null && sk != null) {
            val (url, headers) = S3SigV4.signPut(
                accessKey = ak,
                secretKey = sk,
                region = region,
                bucket = bucket,
                key = s3Key,
                body = capsule.rawJson,
                endpoint = _endpoint.value,
                sessionToken = _sessionToken.value,
                pathStyle = pathStyle()
            )
            val outcome = try {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val client = OkHttpClient()
                    val builder = Request.Builder().url(url)
                    headers.forEach { (k, v) -> builder.header(k, v) }
                    builder.put(capsule.rawJson.toRequestBody("application/json".toMediaType()))
                    client.newCall(builder.build()).execute().use { resp ->
                        if (resp.isSuccessful) null
                        else "HTTP ${resp.code}: ${resp.body?.string()?.take(300).orEmpty()}"
                    }
                }
            } catch (e: Exception) {
                "${e.javaClass.simpleName}: ${e.message}"
            }
            if (outcome == null) {
                val presigned = presignUrl(bucket, region, s3Key)
                val synced = buildSynced(bucket, region, s3Key, capsule, start, presignedUrl = presigned)
                _syncedObjects.value = listOf(synced) + _syncedObjects.value.filter { it.capsuleId != capsule.id }
                _syncStatus.value = AwsSyncStatus.SYNCED
                ApiRotationManager.recordSuccess(provider.id, System.currentTimeMillis() - start)
                return synced
            }
            failure = outcome
        }

        // Dry-run fallback: the UI must show this as simulated, never as synced — and it must
        // say why the real path did not happen.
        delay(150)
        val dryRun = buildSynced(bucket, region, s3Key, capsule, start, simulated = true, errorDetail = failure)
        _syncedObjects.value = listOf(dryRun) + _syncedObjects.value.filter { it.capsuleId != capsule.id }
        _syncStatus.value = AwsSyncStatus.SIMULATED
        return dryRun
    }

    /** A real SigV4 presigned GET URL, or "" when credentials are absent. */
    private fun presignUrl(bucket: String, region: String, s3Key: String): String {
        val ak = _accessKey.value ?: return ""
        val sk = _secretKey.value ?: return ""
        return S3SigV4.presignGet(
            accessKey = ak,
            secretKey = sk,
            region = region,
            bucket = bucket,
            key = s3Key,
            endpoint = _endpoint.value,
            sessionToken = _sessionToken.value,
            pathStyle = pathStyle()
        )
    }

    /** Download a capsule's raw JSON from S3 (real path when configured). */
    suspend fun downloadCapsuleFromS3(capsuleId: String): String? {
        val ak = _accessKey.value ?: return null
        val sk = _secretKey.value ?: return null
        val region = _regionCfg.value
        val bucket = _bucketCfg.value
        val s3Key = "vault/2026/$capsuleId/capsule.json"
        val (url, headers) = S3SigV4.signGet(
            accessKey = ak, secretKey = sk, region = region, bucket = bucket, key = s3Key,
            endpoint = _endpoint.value, sessionToken = _sessionToken.value, pathStyle = pathStyle()
        )
        return try {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                val client = OkHttpClient()
                val builder = Request.Builder().url(url)
                headers.forEach { (k, v) -> builder.header(k, v) }
                client.newCall(builder.get().build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildSynced(
        bucket: String,
        region: String,
        s3Key: String,
        capsule: Capsule,
        start: Long,
        simulated: Boolean = false,
        presignedUrl: String = "",
        errorDetail: String? = null
    ): S3SyncedObject {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = sdf.format(Date())
        return S3SyncedObject(
            s3Key = s3Key,
            bucket = bucket,
            region = region,
            sizeBytes = capsule.rawJson.length.toLong(),
            sha256Etag = capsule.sha256,
            presignedUrl = if (simulated) "" else presignedUrl,
            lastModifiedUtc = timestamp,
            capsuleId = capsule.id,
            simulated = simulated,
            errorDetail = errorDetail
        )
    }

    fun removeSyncedObject(capsuleId: String) {
        _syncedObjects.value = _syncedObjects.value.filter { it.capsuleId != capsuleId }
    }
}

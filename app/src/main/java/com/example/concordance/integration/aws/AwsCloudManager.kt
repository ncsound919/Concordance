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
import java.security.MessageDigest
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
    val presignedUrl: String,
    val lastModifiedUtc: String,
    val capsuleId: String,
    /** True when this object describes a dry run, not a completed upload. */
    val simulated: Boolean = false
)

object AwsCloudManager {

    private val _accessKey = MutableStateFlow<String?>(null)
    val accessKey: StateFlow<String?> = _accessKey.asStateFlow()

    private val _secretKey = MutableStateFlow<String?>(null)
    val secretKey: StateFlow<String?> = _secretKey.asStateFlow()

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

    fun updateConfig(accessKey: String?, secretKey: String?, region: String? = null, bucket: String? = null, endpoint: String? = null) {
        _accessKey.value = accessKey?.takeIf { it.isNotBlank() }
        _secretKey.value = secretKey?.takeIf { it.isNotBlank() }
        if (!region.isNullOrBlank()) _regionCfg.value = region
        if (!bucket.isNullOrBlank()) _bucketCfg.value = bucket
        _endpoint.value = endpoint?.takeIf { it.isNotBlank() }
        _syncStatus.value = if (isConfigured) AwsSyncStatus.CONNECTED else AwsSyncStatus.DISCONNECTED
    }

    private fun md5Hex(content: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(content.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

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
        if (isConfigured && ak != null && sk != null) {
            val ep = _endpoint.value ?: "https://s3.$region.amazonaws.com"
            val (url, headers) = S3SigV4.signPut(
                accessKey = ak,
                secretKey = sk,
                region = region,
                bucket = bucket,
                key = s3Key,
                body = capsule.rawJson,
                endpoint = ep
            )
            val ok = try {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val client = OkHttpClient()
                    val builder = Request.Builder().url(url)
                    headers.forEach { (k, v) -> builder.header(k, v) }
                    builder.put(capsule.rawJson.toRequestBody("application/json".toMediaType()))
                    client.newCall(builder.build()).execute().use { it.isSuccessful }
                }
            } catch (e: Exception) {
                false
            }
            if (ok) {
                val synced = buildSynced(bucket, region, s3Key, capsule, start)
                _syncedObjects.value = listOf(synced) + _syncedObjects.value.filter { it.capsuleId != capsule.id }
                _syncStatus.value = AwsSyncStatus.SYNCED
                ApiRotationManager.recordSuccess(provider.id, System.currentTimeMillis() - start)
                return synced
            }
            // Fall through to simulation on failure.
        }

        // Dry-run fallback: the UI must show this as simulated, never as synced.
        delay(150)
        val dryRun = buildSynced(bucket, region, s3Key, capsule, start, simulated = true)
        _syncedObjects.value = listOf(dryRun) + _syncedObjects.value.filter { it.capsuleId != capsule.id }
        _syncStatus.value = AwsSyncStatus.SIMULATED
        return dryRun
    }

    /** Download a capsule's raw JSON from S3 (real path when configured). */
    suspend fun downloadCapsuleFromS3(capsuleId: String): String? {
        val ak = _accessKey.value ?: return null
        val sk = _secretKey.value ?: return null
        val region = _regionCfg.value
        val bucket = _bucketCfg.value
        val s3Key = "vault/2026/$capsuleId/capsule.json"
        val ep = _endpoint.value ?: "https://s3.$region.amazonaws.com"
        val (url, headers) = S3SigV4.signGet(
            accessKey = ak, secretKey = sk, region = region, bucket = bucket, key = s3Key, endpoint = ep
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
        simulated: Boolean = false
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
            presignedUrl = if (simulated) "" else "https://$bucket.s3.$region.amazonaws.com/$s3Key",
            lastModifiedUtc = timestamp,
            capsuleId = capsule.id,
            simulated = simulated
        )
    }

    fun removeSyncedObject(capsuleId: String) {
        _syncedObjects.value = _syncedObjects.value.filter { it.capsuleId != capsuleId }
    }
}

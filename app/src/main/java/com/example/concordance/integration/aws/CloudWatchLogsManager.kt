package com.example.concordance.integration.aws

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * AWS CloudWatch Logs client for telemetry upload. Uses the CloudWatch Logs
 * PutLogEvents API with SigV4 signing. Falls back to simulation when
 * unconfigured.
 */
object CloudWatchLogsManager {

    private val _logGroup = MutableStateFlow("concordance-telemetry")
    val logGroup: kotlinx.coroutines.flow.StateFlow<String> = _logGroup

    private val _logStream = MutableStateFlow("app-events")
    val logStream: kotlinx.coroutines.flow.StateFlow<String> = _logStream

    fun updateConfig(logGroup: String?, logStream: String?) {
        if (!logGroup.isNullOrBlank()) _logGroup.value = logGroup
        if (!logStream.isNullOrBlank()) _logStream.value = logStream
    }

    /** Upload a batch of log events. Returns true on success. */
    suspend fun putLogEvents(
        accessKey: String,
        secretKey: String,
        region: String,
        events: List<String>
    ): Boolean {
        if (events.isEmpty()) return true
        val endpoint = "https://logs.$region.amazonaws.com"
        val body = JSONObject().apply {
            put("logGroupName", _logGroup.value)
            put("logStreamName", _logStream.value)
            val arr = JSONArray()
            for (e in events) {
                arr.put(JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("message", e)
                })
            }
            put("logEvents", arr)
        }.toString()

        val extraHeaders = mapOf(
            "X-Amz-Target" to "Logs_20140328.PutLogEvents",
            "Content-Type" to "application/x-amz-json-1.1"
        )
        val (url, headers) = S3SigV4.sign(
            method = "POST",
            service = "logs",
            region = region,
            endpoint = endpoint,
            uri = "/",
            payload = body,
            accessKey = accessKey,
            secretKey = secretKey,
            extraHeaders = extraHeaders
        )

        return try {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                val client = OkHttpClient()
                val builder = Request.Builder().url(url)
                headers.forEach { (k, v) -> builder.header(k, v) }
                builder.post(body.toRequestBody("application/x-amz-json-1.1".toMediaType()))
                client.newCall(builder.build()).execute().use { it.isSuccessful }
            }
        } catch (e: Exception) {
            false
        }
    }
}

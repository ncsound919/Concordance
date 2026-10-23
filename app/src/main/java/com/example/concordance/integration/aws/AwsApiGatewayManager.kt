package com.example.concordance.integration.aws

import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AWS API Gateway client for invoking a deployed LLM proxy / compute endpoint.
 * The endpoint URL and API key are configurable (BYOK). Real calls go through
 * OkHttp with an x-api-key header (API Gateway convention); simulation fallback
 * when unconfigured.
 */
object AwsApiGatewayManager {

    private val _endpoint = MutableStateFlow<String?>(null)
    val endpoint: StateFlow<String?> = _endpoint.asStateFlow()

    private val _apiKey = MutableStateFlow<String?>(null)
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    val isConfigured: Boolean get() = !_endpoint.value.isNullOrBlank()

    fun updateConfig(endpoint: String?, apiKey: String?) {
        _endpoint.value = endpoint?.takeIf { it.isNotBlank() }
        _apiKey.value = apiKey?.takeIf { it.isNotBlank() }
    }

    /** Invoke the configured API Gateway endpoint with a JSON body. */
    suspend fun invoke(body: String): String? {
        val ep = _endpoint.value ?: return null
        return try {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                val client = OkHttpClient()
                val builder = Request.Builder().url(ep)
                _apiKey.value?.let { builder.header("x-api-key", it) }
                builder.post(body.toRequestBody("application/json".toMediaType()))
                client.newCall(builder.build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

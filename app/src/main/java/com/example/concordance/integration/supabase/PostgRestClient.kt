package com.example.concordance.integration.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Thin PostgREST client. Uses the anon key + the user's JWT so RLS applies.
 * Hand-rolled (no supabase-kt) per Phase 3 decision.
 */
class PostgRestClient(
    private val auth: SupabaseAuth,
    private val http: OkHttpClient = defaultClient()
) {
    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        private val JSON = "application/json".toMediaType()
    }

    suspend fun select(table: String, query: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val token = auth.accessToken() ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
        val url = SupabaseConfig.restUrl(table) + if (query.isBlank()) "" else "?$query"
        val req = Request.Builder()
            .url(url)
            .header("apikey", SupabaseConfig.ANON_KEY)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("select $table failed (${resp.code}): $text")
                text
            }
        }
    }

    suspend fun insert(table: String, jsonBody: String): Result<String> = withContext(Dispatchers.IO) {
        val token = auth.accessToken() ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
        val req = Request.Builder()
            .url(SupabaseConfig.restUrl(table))
            .header("apikey", SupabaseConfig.ANON_KEY)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=representation")
            .post(jsonBody.toRequestBody(JSON))
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("insert $table failed (${resp.code}): $text")
                text
            }
        }
    }

    suspend fun patch(table: String, idColumn: String, idValue: String, jsonBody: String): Result<String> =
        withContext(Dispatchers.IO) {
            val token = auth.accessToken() ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
            val req = Request.Builder()
                .url("${SupabaseConfig.restUrl(table)}?$idColumn=eq.$idValue")
                .header("apikey", SupabaseConfig.ANON_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .patch(jsonBody.toRequestBody(JSON))
                .build()
            runCatching {
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("patch $table failed (${resp.code}): $text")
                    text
                }
            }
        }

    suspend fun upsert(table: String, jsonBody: String): Result<String> = withContext(Dispatchers.IO) {
        val token = auth.accessToken() ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
        val req = Request.Builder()
            .url(SupabaseConfig.restUrl(table))
            .header("apikey", SupabaseConfig.ANON_KEY)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=representation")
            .post(jsonBody.toRequestBody(JSON))
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("upsert $table failed (${resp.code}): $text")
                text
            }
        }
    }

    suspend fun delete(table: String, idColumn: String, idValue: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val token = auth.accessToken() ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
            val req = Request.Builder()
                .url("${SupabaseConfig.restUrl(table)}?$idColumn=eq.$idValue")
                .header("apikey", SupabaseConfig.ANON_KEY)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            runCatching {
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("delete $table failed (${resp.code})")
                }
            }
        }
}
package com.example.concordance.integration.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Supabase Auth (GoTrue) client. Hand-rolled REST to match the app's existing
 * OkHttp patterns. Tokens are stored via [SupabaseSessionStore].
 */
class SupabaseAuth(
    private val store: SupabaseSessionStore,
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

    val session: SupabaseSession? get() = store.load()

    suspend fun signIn(email: String, password: String): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("email", email)
            put("password", password)
        }.toString()
        val req = Request.Builder()
            .url(SupabaseConfig.authUrl("token?grant_type=password"))
            .header("apikey", SupabaseConfig.ANON_KEY)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Sign-in failed (${resp.code}): ${detail(text)}")
                val s = SupabaseSession.fromJson(JSONObject(text))
                store.save(s)
                s
            }
        }
    }

    suspend fun signUp(email: String, password: String, displayName: String? = null): Result<SupabaseSession> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
                if (!displayName.isNullOrBlank()) {
                    put("data", JSONObject().apply { put("name", displayName) })
                }
            }.toString()
            val req = Request.Builder()
                .url(SupabaseConfig.authUrl("signup"))
                .header("apikey", SupabaseConfig.ANON_KEY)
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(JSON))
                .build()
            runCatching {
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Sign-up failed (${resp.code}): ${detail(text)}")
                    val o = JSONObject(text)
                    // signup may or may not return a session (email confirmation)
                    val s = if (o.has("access_token")) SupabaseSession.fromJson(o) else null
                    if (s != null) store.save(s)
                    s ?: error("Account created. Please verify your email, then sign in.")
                }
            }
        }

    suspend fun refresh(): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        val current = store.load() ?: return@withContext Result.failure(IllegalStateException("No session"))
        val body = JSONObject().apply { put("refresh_token", current.refreshToken) }.toString()
        val req = Request.Builder()
            .url(SupabaseConfig.authUrl("token?grant_type=refresh_token"))
            .header("apikey", SupabaseConfig.ANON_KEY)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Refresh failed (${resp.code}): ${detail(text)}")
                val s = SupabaseSession.fromJson(JSONObject(text))
                // preserve email if refresh response omits user
                val merged = if (s.email.isNullOrBlank()) s.copy(email = current.email) else s
                store.save(merged)
                merged
            }
        }
    }

    suspend fun currentUser(): Result<org.json.JSONObject> = withContext(Dispatchers.IO) {
        val s = store.load() ?: return@withContext Result.failure(IllegalStateException("No session"))
        val req = Request.Builder()
            .url(SupabaseConfig.authUrl("user"))
            .header("apikey", SupabaseConfig.ANON_KEY)
            .header("Authorization", "Bearer ${s.accessToken}")
            .get()
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("getUser failed (${resp.code}): ${detail(text)}")
                org.json.JSONObject(text)
            }
        }
    }

    fun signOut() { store.clear() }

    /** Returns a valid access token, refreshing if needed. */
    suspend fun accessToken(): String? {
        val s = store.load() ?: return null
        if (!s.isExpired) return s.accessToken
        return refresh().getOrNull()?.accessToken ?: s.accessToken
    }

    private fun detail(body: String): String =
        try { JSONObject(body).optString("msg").ifBlank { JSONObject(body).optString("message") } } catch (e: Exception) { body.take(200) }
}
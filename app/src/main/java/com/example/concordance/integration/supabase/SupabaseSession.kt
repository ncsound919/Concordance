package com.example.concordance.integration.supabase

import org.json.JSONObject

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long, // epoch seconds
    val userId: String,
    val email: String?
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() / 1000 >= expiresAt - 60

    companion object {
        fun fromJson(o: JSONObject): SupabaseSession {
            return SupabaseSession(
                accessToken = o.optString("access_token"),
                refreshToken = o.optString("refresh_token"),
                expiresAt = o.optLong("expires_at", (System.currentTimeMillis() / 1000) + o.optLong("expires_in", 3600)),
                userId = (if (o.has("user")) o.optJSONObject("user")?.optString("id") else null)?.takeIf { !it.isNullOrBlank() }
                    ?: o.optString("user_id").takeIf { it.isNotBlank() }
                    ?: "",
                email = (if (o.has("user")) o.optJSONObject("user")?.optString("email") else null)?.takeIf { !it.isNullOrBlank() }
                    ?: runCatching { o.getString("email") }.getOrNull()?.takeIf { it.isNotBlank() }
            )
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("access_token", accessToken)
        put("refresh_token", refreshToken)
        put("expires_at", expiresAt)
        put("user_id", userId)
        put("email", email)
    }
}
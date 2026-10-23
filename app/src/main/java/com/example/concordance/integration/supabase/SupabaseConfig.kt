package com.example.concordance.integration.supabase

/**
 * Supabase project configuration for the Concordance ecosystem.
 * The anon key is safe to ship in the client; RLS enforces access.
 */
object SupabaseConfig {
    const val PROJECT_REF = "ktphbqvoycurlyxieurv"
    const val BASE_URL = "https://$PROJECT_REF.supabase.co"
    const val ANON_KEY = "sb_publishable_M4-TLN2bUWdGlTmCE_E8aQ_8L8zqmKZ"

    fun authUrl(path: String) = "$BASE_URL/auth/v1/$path"
    fun restUrl(table: String) = "$BASE_URL/rest/v1/$table"
}
package com.example.concordance.integration.zotero

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Stores the Zotero user (or group) id that pairs with a BYOK API key.
 * Kept separate from [KeyStore] because the id is not a secret.
 */
object ZoteroSettings {
    private const val PREFS = "concordance_zotero"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_GROUP_ID = "group_id"

    fun userId(ctx: Context): String? =
        prefs(ctx).getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }

    fun setUserId(ctx: Context, id: String?) {
        prefs(ctx).edit { if (id.isNullOrBlank()) remove(KEY_USER_ID) else putString(KEY_USER_ID, id.trim()) }
    }

    fun groupId(ctx: Context): String? =
        prefs(ctx).getString(KEY_GROUP_ID, null)?.takeIf { it.isNotBlank() }

    fun setGroupId(ctx: Context, id: String?) {
        prefs(ctx).edit { if (id.isNullOrBlank()) remove(KEY_GROUP_ID) else putString(KEY_GROUP_ID, id.trim()) }
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

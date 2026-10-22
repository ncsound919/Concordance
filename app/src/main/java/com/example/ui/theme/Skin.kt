package com.example.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

/**
 * Visual skins.
 *
 * A skin is a palette *and* a surface treatment. Two skins can share a hue and
 * still look completely different: [CLINICAL] is the app's instrument-panel
 * look (flat navy, neon accents, tight radii, glow), while [SITE] reproduces
 * the Concordance website - the same CSS custom properties the site defines in
 * `app/globals.css`, softer surfaces, the blueprint-grid backdrop, the site's
 * fade-up entrance and its larger corner radii.
 *
 * The two are independent of the light/dark choice: a skin is picked first,
 * then the system/dark/light toggle applies within it.
 */
enum class Skin(
    /** Short name shown in the picker. */
    val label: String,
    /** One-line description shown under the name. */
    val blurb: String,
    /** Draw the blueprint grid behind the content, as the website does. */
    val blueprintBackdrop: Boolean,
    /** Let accent surfaces cast a soft glow, as the app's clinical look does. */
    val glow: Boolean,
    /** Corner radius for section cards. */
    val cardRadius: Dp,
    /** Animate content in with the site's fade-up. */
    val entranceAnimation: Boolean,
) {
    CLINICAL(
        label = "Clinical",
        blurb = "The original instrument-panel look: deep navy, electric cyan, tight radii.",
        blueprintBackdrop = false,
        glow = true,
        cardRadius = 12.dp,
        entranceAnimation = false,
    ),
    SITE(
        label = "Website",
        blurb = "The website's palette and surface treatment, with the blueprint grid.",
        blueprintBackdrop = true,
        glow = false,
        cardRadius = 16.dp,
        entranceAnimation = true,
    ),
}

/** The skin in force, readable from any composable. */
val LocalSkin: Skin
    @Composable @ReadOnlyComposable get() = LocalSkinState.current

internal val LocalSkinState = staticCompositionLocalOf { Skin.CLINICAL }

// ---------------------------------------------------------------------------
// Persistence
// ---------------------------------------------------------------------------

interface SkinPreferenceStore {
    fun load(): Skin
    fun save(skin: Skin)
}

private class SharedPrefsSkinPreferenceStore(context: Context) : SkinPreferenceStore {
    private val prefs = context.getSharedPreferences("concordance_theme", Context.MODE_PRIVATE)

    override fun load(): Skin = runCatching {
        Skin.valueOf(prefs.getString("skin", Skin.CLINICAL.name) ?: Skin.CLINICAL.name)
    }.getOrDefault(Skin.CLINICAL)

    override fun save(skin: Skin) {
        prefs.edit { putString("skin", skin.name) }
    }
}

fun createSkinPreferenceStore(context: Context): SkinPreferenceStore =
    SharedPrefsSkinPreferenceStore(context)

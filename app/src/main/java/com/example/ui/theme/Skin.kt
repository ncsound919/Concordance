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
 * still look completely different.
 *
 * [CLINICAL] is the app's own identity - the original "instrument panel"
 * look, now layered: a brand-edge gradient along the top of each card, a
 * surface gradient, a tinted navigation indicator and a fade-up entrance.
 *
 * [SITE] reproduces the Concordance website. Its palette is the CSS custom
 * properties the site defines in `app/globals.css`, and its treatment is the
 * site's: flat, opaque cards with a hairline border and generous padding
 * (`rounded-xl border border-slate-border bg-slate-card p-5`), no gradients
 * and no shadows, over the blueprint-dot texture the site uses in its hero.
 */
enum class Skin(
    /** Short name shown in the picker. */
    val label: String,
    /** One-line description shown under the name. */
    val blurb: String,
    /** Draw the blueprint grid behind the content, as the website's hero does. */
    val blueprintBackdrop: Boolean,
    /** Layered treatment: brand edge, surface gradient, tinted indicators, fade-up. */
    val layered: Boolean,
    /** Corner radius for section cards. */
    val cardRadius: Dp,
    /** Padding inside a section card. */
    val cardPadding: Dp,
) {
    CLINICAL(
        label = "Clinical",
        blurb = "The app's own look: layered cards, a brand edge and a tinted accent.",
        blueprintBackdrop = false,
        layered = true,
        cardRadius = 12.dp,
        cardPadding = 16.dp,
    ),
    SITE(
        label = "Website",
        blurb = "The website's palette and flat, hairline-bordered card treatment.",
        blueprintBackdrop = true,
        layered = false,
        cardRadius = 12.dp,
        cardPadding = 20.dp,
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

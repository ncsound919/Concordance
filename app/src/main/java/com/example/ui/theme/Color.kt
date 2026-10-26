package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Raw brand palettes (immutable). Theme-aware semantic tokens are defined
// below as @Composable getters, so every existing `color = TextPrimary` call
// site automatically follows the active skin and light/dark scheme.
// ---------------------------------------------------------------------------

// --- Clinical, dark identity ----------------------------------------------
internal val RawMidnightDeep = Color(0xFF080E17)
internal val RawSlateCard = Color(0xFF101B2B)
internal val RawSlateCardBorder = Color(0xFF1F2E45)
internal val RawSlateCardElevated = Color(0xFF162438)
internal val RawTextPrimary = Color(0xFFF1F5F9)
internal val RawTextSecondary = Color(0xFF94A3B8)
// #808FA8 meets WCAG 2.2 AA (>=4.5:1) on background, surface and elevated surface.
internal val RawTextMuted = Color(0xFF808FA8)
// Stronger outline for interactive controls (>=3:1 non-text contrast against
// every surface it can sit on: background, card and elevated card).
internal val RawBorderStrongDark = Color(0xFF64748B)

// --- Clinical accents (dark) ----------------------------------------------
internal val RawElectricCyan = Color(0xFF00E5FF)
internal val RawClinicalTeal = Color(0xFF00B4D8)
internal val RawNeonEmerald = Color(0xFF00E676)
internal val RawVividCoral = Color(0xFFFF5252)
internal val RawWarningGold = Color(0xFFFFD740)
internal val RawWarningAmber = Color(0xFFFF9100)
internal val RawIcyBlue = Color(0xFF80D8FF)

// Accessible light-theme accent values (>=4.5:1 on white/light surfaces).
internal val RawDayElectricCyan = Color(0xFF00778F)
internal val RawDayClinicalTeal = Color(0xFF00677A)
internal val RawDayNeonEmerald = Color(0xFF007A3D)
internal val RawDayVividCoral = Color(0xFFB3261E)
internal val RawDayWarningGold = Color(0xFF7A5A00)
internal val RawDayWarningAmber = Color(0xFF8A4B00)
internal val RawDayIcyBlue = Color(0xFF0069A8)

// --- Clinical, light palette ----------------------------------------------
internal val RawDayBackground = Color(0xFFF7F9FC)
internal val RawDaySurface = Color(0xFFFFFFFF)
internal val RawDaySurfaceVariant = Color(0xFFEEF2F7)
internal val RawDayBorder = Color(0xFFD8E0EA)
internal val RawDayTextPrimary = Color(0xFF0F172A)
internal val RawDayTextSecondary = Color(0xFF475569)
internal val RawDayTextMuted = Color(0xFF5A6B85)
internal val RawDayPrimary = Color(0xFF00778F)
// Stronger outline for interactive controls (>=3:1 against white).
internal val RawBorderStrongLight = Color(0xFF7C8CA3)

// ---------------------------------------------------------------------------
// Website palette.
//
// These are the CSS custom properties the Concordance website defines in
// `app/globals.css`, transcribed verbatim:
//
//   --color-bg #080e17   --color-bg-card #101b2b   --color-bg-hover #162438
//   --color-border #1f2e45   --color-border-hover #2b3d5a
//   --color-text #f1f5f9   --color-text-muted #94a3b8   --color-text-subtle #64748b
//   --color-primary #00e5ff   --color-primary-hover #5cf2ff   --color-primary-active #00b8cc
//   --color-success #00e676   --color-warning #ff9100   --color-danger #ff5252
//   --color-info #00b4d8
//
// Two tokens are deliberately NOT copied verbatim, because the website uses
// them for decoration and borders while the app uses them as small text and
// as the outline of an interactive control, where this app's own contract is
// stricter:
//
//   --color-text-subtle #64748b  -> 4.07:1 on the website's background.
//                                     The app needs 4.5:1, so both skins use
//                                     #808FA8 in dark (5.92:1) and #5A6B85 in
//                                     light (5.14:1).
//   --color-border-hover #2b3d5a -> 1.58:1 against a card. The app needs 3:1
//                                     for a control boundary, so both skins
//                                     use #64748B in dark (3.28:1) and
//                                     #7C8CA3 in light (3.04:1).
//
// Likewise the website's light-theme accents (#0891b2 primary, #16a34a
// success, #d97706 warning, #dc2626 danger, #0284c7 info) are web fills: as
// small text they sit between 3.1:1 and 3.5:1 on white. The site skin uses
// the site's own darker steps so the same hues stay legible as text.
// ---------------------------------------------------------------------------
internal val SiteBackground = Color(0xFF080E17)
internal val SiteSurface = Color(0xFF101B2B)
internal val SiteSurfaceElevated = Color(0xFF162438)
internal val SiteBorder = Color(0xFF1F2E45)
internal val SiteTextPrimary = Color(0xFFF1F5F9)
internal val SiteTextSecondary = Color(0xFF94A3B8)
internal val SiteTextMuted = Color(0xFF808FA8)
internal val SiteBorderStrong = Color(0xFF64748B)
internal val SitePrimary = Color(0xFF00E5FF)
internal val SitePrimaryActive = Color(0xFF00B8CC)
internal val SiteSuccess = Color(0xFF00E676)
internal val SiteWarning = Color(0xFFFF9100)
internal val SiteDanger = Color(0xFFFF5252)
internal val SiteInfo = Color(0xFF00B4D8)

internal val SiteDayBackground = Color(0xFFF8FAFC)
internal val SiteDaySurface = Color(0xFFFFFFFF)
internal val SiteDaySurfaceElevated = Color(0xFFF1F5F9)
internal val SiteDayBorder = Color(0xFFE2E8F0)
internal val SiteDayTextPrimary = Color(0xFF0F172A)
internal val SiteDayTextSecondary = Color(0xFF475569)
internal val SiteDayTextMuted = Color(0xFF5A6B85)
internal val SiteDayBorderStrong = Color(0xFF7C8CA3)
internal val SiteDayPrimary = Color(0xFF155E75)
internal val SiteDayPrimaryActive = Color(0xFF0E7490)
internal val SiteDaySuccess = Color(0xFF15803D)
internal val SiteDayWarning = Color(0xFF92400E)
internal val SiteDayWarningAmber = Color(0xFF9A3412)
internal val SiteDayDanger = Color(0xFFB91C1C)
internal val SiteDayInfo = Color(0xFF0369A1)

/** Semantic palette consumed by screens. */
data class ConcordanceColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSecondary: Color,
    val accentSuccess: Color,
    val accentDanger: Color,
    val accentWarning: Color,
    val accentWarningAmber: Color,
    val accentInfo: Color,
    val isDark: Boolean,
)

internal fun darkPalette(skin: Skin = Skin.CLINICAL): ConcordanceColors = when (skin) {
    Skin.CLINICAL -> ConcordanceColors(
        background = RawMidnightDeep,
        surface = RawSlateCard,
        surfaceElevated = RawSlateCardElevated,
        border = RawSlateCardBorder,
        borderStrong = RawBorderStrongDark,
        textPrimary = RawTextPrimary,
        textSecondary = RawTextSecondary,
        textMuted = RawTextMuted,
        accent = RawElectricCyan,
        accentSecondary = RawClinicalTeal,
        accentSuccess = RawNeonEmerald,
        accentDanger = RawVividCoral,
        accentWarning = RawWarningGold,
        accentWarningAmber = RawWarningAmber,
        accentInfo = RawIcyBlue,
        isDark = true,
    )

    Skin.SITE -> ConcordanceColors(
        background = SiteBackground,
        surface = SiteSurface,
        surfaceElevated = SiteSurfaceElevated,
        border = SiteBorder,
        borderStrong = SiteBorderStrong,
        textPrimary = SiteTextPrimary,
        textSecondary = SiteTextSecondary,
        textMuted = SiteTextMuted,
        accent = SitePrimary,
        accentSecondary = SitePrimaryActive,
        accentSuccess = SiteSuccess,
        accentDanger = SiteDanger,
        accentWarning = SiteWarning,
        accentWarningAmber = SiteWarning,
        accentInfo = SiteInfo,
        isDark = true,
    )
}

internal fun lightPalette(skin: Skin = Skin.CLINICAL): ConcordanceColors = when (skin) {
    Skin.CLINICAL -> ConcordanceColors(
        background = RawDayBackground,
        surface = RawDaySurface,
        surfaceElevated = RawDaySurfaceVariant,
        border = RawDayBorder,
        borderStrong = RawBorderStrongLight,
        textPrimary = RawDayTextPrimary,
        textSecondary = RawDayTextSecondary,
        textMuted = RawDayTextMuted,
        accent = RawDayPrimary,
        accentSecondary = RawDayClinicalTeal,
        accentSuccess = RawDayNeonEmerald,
        accentDanger = RawDayVividCoral,
        accentWarning = RawDayWarningGold,
        accentWarningAmber = RawDayWarningAmber,
        accentInfo = RawDayIcyBlue,
        isDark = false,
    )

    Skin.SITE -> ConcordanceColors(
        background = SiteDayBackground,
        surface = SiteDaySurface,
        surfaceElevated = SiteDaySurfaceElevated,
        border = SiteDayBorder,
        borderStrong = SiteDayBorderStrong,
        textPrimary = SiteDayTextPrimary,
        textSecondary = SiteDayTextSecondary,
        textMuted = SiteDayTextMuted,
        accent = SiteDayPrimary,
        accentSecondary = SiteDayPrimaryActive,
        accentSuccess = SiteDaySuccess,
        accentDanger = SiteDayDanger,
        accentWarning = SiteDayWarning,
        accentWarningAmber = SiteDayWarningAmber,
        accentInfo = SiteDayInfo,
        isDark = false,
    )
}

// ---------------------------------------------------------------------------
// Theme-aware tokens. These used to be plain `val Color`s; they are now
// composable getters so a single palette switch re-colours the whole app.
// ---------------------------------------------------------------------------

val MidnightDeep: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.background

val SlateCard: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.surface

val SlateCardElevated: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.surfaceElevated

val SlateCardBorder: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.border

/** Interactive-control outline; use for text fields, chips and toggles. */
val BorderStrong: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.borderStrong

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.textPrimary

val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.textSecondary

val TextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.textMuted

// Accent roles: brand colours in dark mode, accessible darker variants in light.
val ElectricCyan: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.accent

val ClinicalTeal: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.accentSecondary

val NeonEmerald: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.accentSuccess

val VividCoral: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.accentDanger

val WarningGold: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.accentWarning

val WarningAmber: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.accentWarningAmber

val IcyBlue: Color
    @Composable @ReadOnlyComposable get() = LocalConcordanceColors.current.accentInfo

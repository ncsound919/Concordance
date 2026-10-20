package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Raw brand palette (immutable). Theme-aware semantic tokens are defined below
// as @Composable getters, so every existing `color = TextPrimary` call site
// automatically follows the active light/dark scheme.
// ---------------------------------------------------------------------------

// Dark identity
internal val RawMidnightDeep = Color(0xFF080E17)
internal val RawSlateCard = Color(0xFF101B2B)
internal val RawSlateCardBorder = Color(0xFF1F2E45)
internal val RawSlateCardElevated = Color(0xFF162438)
internal val RawTextPrimary = Color(0xFFF1F5F9)
internal val RawTextSecondary = Color(0xFF94A3B8)
// #808FA8 meets WCAG 2.2 AA (>=4.5:1) on background, surface and elevated surface.
internal val RawTextMuted = Color(0xFF808FA8)
// Stronger outline for interactive controls (>=3:1 non-text contrast).
internal val RawBorderStrongDark = Color(0xFF5A6B85)

// Accents (dark brand values). Theme-aware accessors are defined below.
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

// Light palette
internal val RawDayBackground = Color(0xFFF7F9FC)
internal val RawDaySurface = Color(0xFFFFFFFF)
internal val RawDaySurfaceVariant = Color(0xFFEEF2F7)
internal val RawDayBorder = Color(0xFFD8E0EA)
internal val RawDayTextPrimary = Color(0xFF0F172A)
internal val RawDayTextSecondary = Color(0xFF475569)
internal val RawDayTextMuted = Color(0xFF64748B)
internal val RawDayPrimary = Color(0xFF00778F)
// Stronger outline for interactive controls (>=3:1 against white).
internal val RawBorderStrongLight = Color(0xFF7C8CA3)

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

internal fun darkPalette(): ConcordanceColors = ConcordanceColors(
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

internal fun lightPalette(): ConcordanceColors = ConcordanceColors(
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

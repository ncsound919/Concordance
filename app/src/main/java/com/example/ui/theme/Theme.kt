package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** User-selectable theme mode, persisted across launches. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

// ---- CompositionLocals -------------------------------------------------

val LocalConcordanceColors = staticCompositionLocalOf { darkPalette() }

// ---- Material colour schemes ------------------------------------------

private val DarkColorScheme = darkColorScheme(
    primary = RawElectricCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = RawClinicalTeal,
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF004E5B),
    onSecondaryContainer = Color(0xFFA6EEFF),
    tertiary = RawNeonEmerald,
    onTertiary = Color(0xFF003915),
    background = RawMidnightDeep,
    onBackground = RawTextPrimary,
    surface = RawSlateCard,
    onSurface = RawTextPrimary,
    surfaceVariant = RawSlateCardElevated,
    onSurfaceVariant = RawTextSecondary,
    outline = RawBorderStrongDark,
    error = RawVividCoral,
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = RawDayPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6ECF7),
    onPrimaryContainer = Color(0xFF00363D),
    secondary = Color(0xFF00677A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB6ECF7),
    onSecondaryContainer = Color(0xFF00363D),
    tertiary = Color(0xFF006E3A),
    onTertiary = Color.White,
    background = RawDayBackground,
    onBackground = RawDayTextPrimary,
    surface = RawDaySurface,
    onSurface = RawDayTextPrimary,
    surfaceVariant = RawDaySurfaceVariant,
    onSurfaceVariant = RawDayTextSecondary,
    outline = RawBorderStrongLight,
    error = Color(0xFFB3261E),
    onError = Color.White,
)

// --- Website skin ---------------------------------------------------------
// The site's own hues, with the `on*` colours chosen so that Material's
// button and container roles stay legible against them.

private val SiteDarkColorScheme = darkColorScheme(
    primary = SitePrimary,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = SitePrimaryActive,
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004E5B),
    onSecondaryContainer = Color(0xFFA6EEFF),
    tertiary = SiteSuccess,
    onTertiary = Color(0xFF003915),
    background = SiteBackground,
    onBackground = SiteTextPrimary,
    surface = SiteSurface,
    onSurface = SiteTextPrimary,
    surfaceVariant = SiteSurfaceElevated,
    onSurfaceVariant = SiteTextSecondary,
    outline = SiteBorderStrong,
    error = SiteDanger,
    onError = Color.White,
)

private val SiteLightColorScheme = lightColorScheme(
    primary = SiteDayPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6ECF7),
    onPrimaryContainer = Color(0xFF00363D),
    secondary = SiteDayPrimaryActive,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB6ECF7),
    onSecondaryContainer = Color(0xFF00363D),
    tertiary = SiteDaySuccess,
    onTertiary = Color.White,
    background = SiteDayBackground,
    onBackground = SiteDayTextPrimary,
    surface = SiteDaySurface,
    onSurface = SiteDayTextPrimary,
    surfaceVariant = SiteDaySurfaceElevated,
    onSurfaceVariant = SiteDayTextSecondary,
    outline = SiteDayBorderStrong,
    error = SiteDayDanger,
    onError = Color.White,
)

// ---- Theme -------------------------------------------------------------

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    skin: Skin = Skin.CLINICAL,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        skin == Skin.SITE && darkTheme -> SiteDarkColorScheme
        skin == Skin.SITE -> SiteLightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val palette = if (darkTheme) darkPalette(skin) else lightPalette(skin)

    // The app can be dark while the system is light (and vice versa), so drive
    // the status/navigation bar icon colour from the resolved app theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalConcordanceColors provides palette,
        LocalSkinState provides skin,
        LocalConcordanceTextStyles provides concordanceTextStyles(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

// ---- Persisted theme mode ---------------------------------------------

interface ThemePreferenceStore {
    fun load(): ThemeMode
    fun save(mode: ThemeMode)
}

private class SharedPrefsThemePreferenceStore(context: Context) : ThemePreferenceStore {
    private val prefs = context.getSharedPreferences("concordance_theme", Context.MODE_PRIVATE)

    override fun load(): ThemeMode = runCatching {
        ThemeMode.valueOf(prefs.getString("mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    }.getOrDefault(ThemeMode.SYSTEM)

    override fun save(mode: ThemeMode) {
        prefs.edit { putString("mode", mode.name) }
    }
}

fun createThemePreferenceStore(context: Context): ThemePreferenceStore =
    SharedPrefsThemePreferenceStore(context)

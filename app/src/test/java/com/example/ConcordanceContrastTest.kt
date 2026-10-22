package com.example

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.Skin
import com.example.ui.theme.darkPalette
import com.example.ui.theme.lightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.2 AA contrast guard, for every skin.
 *
 * Normal text must be >= 4.5:1 and UI-component boundaries >= 3:1.
 * If a palette token changes, this test fails before the regression ships.
 */
class ConcordanceContrastTest {

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertText(label: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue("%s ratio %.2f < 4.5".format(label, r), r >= 4.5)
    }

    private fun assertUi(label: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue("%s ratio %.2f < 3.0".format(label, r), r >= 3.0)
    }

    private fun assertPaletteMeetsContrast(prefix: String, p: com.example.ui.theme.ConcordanceColors) {
        assertText("$prefix textPrimary/background", p.textPrimary, p.background)
        assertText("$prefix textSecondary/background", p.textSecondary, p.background)
        assertText("$prefix textMuted/background", p.textMuted, p.background)
        assertText("$prefix textMuted/surface", p.textMuted, p.surface)
        assertText("$prefix textMuted/surfaceElevated", p.textMuted, p.surfaceElevated)
        assertText("$prefix accent/background", p.accent, p.background)
        assertText("$prefix accent/surface", p.accent, p.surface)
        assertText("$prefix accentSecondary/background", p.accentSecondary, p.background)
        assertText("$prefix accentSuccess/background", p.accentSuccess, p.background)
        assertText("$prefix accentDanger/background", p.accentDanger, p.background)
        assertText("$prefix accentWarning/background", p.accentWarning, p.background)
        assertText("$prefix accentWarningAmber/background", p.accentWarningAmber, p.background)
        assertText("$prefix accentInfo/background", p.accentInfo, p.background)
        assertUi("$prefix borderStrong/surface", p.borderStrong, p.surface)
        assertUi("$prefix borderStrong/background", p.borderStrong, p.background)
    }

    @Test
    fun darkPalette_meets_contrast() {
        assertPaletteMeetsContrast("dark", darkPalette())
    }

    @Test
    fun lightPalette_meets_contrast() {
        assertPaletteMeetsContrast("light", lightPalette())
    }

    @Test
    fun everySkinMeetsContrast_inBothModes() {
        for (skin in Skin.entries) {
            assertPaletteMeetsContrast("${skin.name} dark", darkPalette(skin))
            assertPaletteMeetsContrast("${skin.name} light", lightPalette(skin))
        }
    }

    @Test
    fun palettes_are_distinct() {
        val d = darkPalette()
        val l = lightPalette()
        assertTrue("dark palette should be dark", d.isDark)
        assertTrue("light palette should not be dark", !l.isDark)
        assertTrue("background must differ", d.background != l.background)
    }

    @Test
    fun skins_are_distinct() {
        for (skin in Skin.entries) {
            val d = darkPalette(skin)
            val l = lightPalette(skin)
            assertTrue("${skin.name} dark background must differ from light", d.background != l.background)
        }
        // The site skin must not be a copy of the clinical one.
        assertTrue(
            "site and clinical dark palettes must differ",
            darkPalette(Skin.SITE) != darkPalette(Skin.CLINICAL),
        )
        assertTrue(
            "site and clinical light palettes must differ",
            lightPalette(Skin.SITE) != lightPalette(Skin.CLINICAL),
        )
    }

    /**
     * The site skin must stay in step with the website.
     *
     * These are the CSS custom properties in `concordance-website/app/globals.css`.
     * If the site's palette is changed there, this test fails here, so the two
     * products cannot drift apart silently.
     */
    @Test
    fun siteSkin_matchesTheWebsitePalette() {
        val dark = darkPalette(Skin.SITE)
        assertEquals(Color(0xFF080E17), dark.background)     // --color-bg
        assertEquals(Color(0xFF101B2B), dark.surface)         // --color-bg-card
        assertEquals(Color(0xFF162438), dark.surfaceElevated) // --color-bg-hover
        assertEquals(Color(0xFF1F2E45), dark.border)         // --color-border
        assertEquals(Color(0xFFF1F5F9), dark.textPrimary)    // --color-text
        assertEquals(Color(0xFF94A3B8), dark.textSecondary)  // --color-text-muted
        assertEquals(Color(0xFF00E5FF), dark.accent)         // --color-primary
        assertEquals(Color(0xFF00B8CC), dark.accentSecondary) // --color-primary-active
        assertEquals(Color(0xFF00E676), dark.accentSuccess)  // --color-success
        assertEquals(Color(0xFFFF9100), dark.accentWarning)  // --color-warning
        assertEquals(Color(0xFFFF5252), dark.accentDanger)   // --color-danger
        assertEquals(Color(0xFF00B4D8), dark.accentInfo)     // --color-info

        val light = lightPalette(Skin.SITE)
        assertEquals(Color(0xFFF8FAFC), light.background)      // html.light --color-bg
        assertEquals(Color(0xFFFFFFFF), light.surface)         // html.light --color-bg-card
        assertEquals(Color(0xFFF1F5F9), light.surfaceElevated) // html.light --color-bg-hover
        assertEquals(Color(0xFFE2E8F0), light.border)         // html.light --color-border
        assertEquals(Color(0xFF0F172A), light.textPrimary)    // html.light --color-text
        assertEquals(Color(0xFF475569), light.textSecondary)  // html.light --color-text-muted
    }
}

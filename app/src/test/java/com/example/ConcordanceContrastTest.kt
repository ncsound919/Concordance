package com.example

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.darkPalette
import com.example.ui.theme.lightPalette
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.2 AA contrast guard.
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

    @Test
    fun darkPalette_meets_contrast() {
        val p = darkPalette()
        assertText("dark textPrimary/background", p.textPrimary, p.background)
        assertText("dark textSecondary/background", p.textSecondary, p.background)
        assertText("dark textMuted/background", p.textMuted, p.background)
        assertText("dark textMuted/surface", p.textMuted, p.surface)
        assertText("dark textMuted/surfaceElevated", p.textMuted, p.surfaceElevated)
        assertText("dark accent/background", p.accent, p.background)
        assertText("dark accent/surface", p.accent, p.surface)
        assertText("dark accentSecondary/background", p.accentSecondary, p.background)
        assertText("dark accentSuccess/background", p.accentSuccess, p.background)
        assertText("dark accentDanger/background", p.accentDanger, p.background)
        assertText("dark accentWarning/background", p.accentWarning, p.background)
        assertText("dark accentWarningAmber/background", p.accentWarningAmber, p.background)
        assertText("dark accentInfo/background", p.accentInfo, p.background)
        assertUi("dark borderStrong/surface", p.borderStrong, p.surface)
        assertUi("dark borderStrong/background", p.borderStrong, p.background)
    }

    @Test
    fun lightPalette_meets_contrast() {
        val p = lightPalette()
        assertText("light textPrimary/background", p.textPrimary, p.background)
        assertText("light textSecondary/background", p.textSecondary, p.background)
        assertText("light textMuted/background", p.textMuted, p.background)
        assertText("light textMuted/surface", p.textMuted, p.surface)
        assertText("light accent/background", p.accent, p.background)
        assertText("light accent/surface", p.accent, p.surface)
        assertText("light accentSecondary/background", p.accentSecondary, p.background)
        assertText("light accentSuccess/background", p.accentSuccess, p.background)
        assertText("light accentDanger/background", p.accentDanger, p.background)
        assertText("light accentWarning/background", p.accentWarning, p.background)
        assertText("light accentWarningAmber/background", p.accentWarningAmber, p.background)
        assertText("light accentInfo/background", p.accentInfo, p.background)
        assertUi("light borderStrong/surface", p.borderStrong, p.surface)
        assertUi("light borderStrong/background", p.borderStrong, p.background)
    }

    @Test
    fun palettes_are_distinct() {
        val d = darkPalette()
        val l = lightPalette()
        assertTrue("dark palette should be dark", d.isDark)
        assertTrue("light palette should not be dark", !l.isDark)
        assertTrue("background must differ", d.background != l.background)
    }
}

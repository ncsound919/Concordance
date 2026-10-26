package com.example.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Brand treatments that sit on top of the palette.
 *
 * These are the parts of the look that a colour swap cannot produce: the
 * gradient that runs through primary actions, the blueprint grid the website
 * draws behind its content, and the fade-up entrance it uses when a section
 * arrives. Keeping them here means a skin switch swaps the whole treatment in
 * one place.
 */

/** The horizontal accent gradient used on primary actions and brand marks. */
@Composable
fun rememberBrandBrush(): Brush {
    val accent = ElectricCyan
    val secondary = ClinicalTeal
    return remember(accent, secondary) {
        Brush.horizontalGradient(listOf(accent, secondary))
    }
}

/**
 * The vertical gradient on a card surface.
 *
 * A hair lighter at the top than the bottom, which reads as a light source
 * above the card and gives flat surfaces some depth without a shadow.
 */
@Composable
fun rememberCardBrush(): Brush {
    val surface = SlateCard
    val elevated = SlateCardElevated
    return remember(surface, elevated) {
        Brush.verticalGradient(listOf(surface, elevated))
    }
}

/**
 * Draws the website's blueprint dot grid behind the content.
 *
 * The website paints it with a repeating radial gradient; Compose has no
 * repeating brush, so the dots are drawn directly. The grid is only attached
 * when the active skin asks for it.
 */
fun Modifier.blueprintGrid(color: Color, spacing: Dp = 24.dp, dotRadius: Dp = 1.dp): Modifier =
    drawBehind {
        val step = spacing.toPx()
        val radius = dotRadius.toPx()
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawCircle(color = color, radius = radius, center = Offset(x, y))
                x += step
            }
            y += step
        }
    }

/**
 * Whether the user has asked the system to remove animations.
 *
 * Compose's own animation APIs do not consult the "Remove animations"
 * accessibility setting, so this reads the animator duration scale directly.
 * When it is zero, entrance animations are skipped rather than merely made
 * faster.
 */
@Composable
fun prefersReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { readReducedMotion(context) }
}

private fun readReducedMotion(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}.getOrDefault(false)

/**
 * The website's fade-up entrance: content rises into place and fades in.
 *
 * Respects the reduced-motion preference by jumping straight to the final
 * state when animations are switched off.
 */
@Composable
fun Modifier.fadeUp(visible: Boolean = true): Modifier {
    val reduced = prefersReducedMotion()
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "fadeUp",
    )
    val p = if (reduced) if (visible) 1f else 0f else progress
    return this
        .alpha(p)
        .offset(y = ((1f - p) * 8).dp)
}

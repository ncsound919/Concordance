package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Semantic text roles for the Concordance UI.
 *
 * Screens should never hardcode `fontSize`. They pick a role from here, which
 * in turn derives from [MaterialTheme.typography]. That keeps one type scale
 * behind the whole product, makes the font-size floor (no text below 11sp)
 * enforceable, and lets a future accessibility "large text" setting scale the
 * app by changing this one object.
 *
 * Role map (M3 base -> role):
 *  - headlineMedium -> screenTitle
 *  - titleLarge     -> sectionTitle
 *  - titleMedium    -> cardTitle / dialogTitle
 *  - bodyLarge      -> body
 *  - bodyMedium     -> bodyDense / dialogBody
 *  - bodySmall      -> caption
 *  - labelSmall     -> overline / chartAxis / tableHeader
 *  - labelMedium    -> chip / metricLabel / buttonSmall
 *  - labelLarge     -> buttonLabel
 *  - headlineSmall  -> metricValue
 */
@Immutable
data class ConcordanceTextStyles(
    val screenTitle: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val dialogTitle: TextStyle,
    val body: TextStyle,
    val bodyDense: TextStyle,
    val dialogBody: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    val metricValue: TextStyle,
    val metricLabel: TextStyle,
    val buttonLabel: TextStyle,
    val buttonSmall: TextStyle,
    val chip: TextStyle,
    val tableHeader: TextStyle,
    val chartAxis: TextStyle,
    val chartLabel: TextStyle,
    val monoId: TextStyle,
    val monoValue: TextStyle,
)

val LocalConcordanceTextStyles = staticCompositionLocalOf<ConcordanceTextStyles> {
    error("ConcordanceTextStyles not provided; wrap content in MyApplicationTheme")
}

@Composable
internal fun concordanceTextStyles(): ConcordanceTextStyles {
    val t = MaterialTheme.typography
    return ConcordanceTextStyles(
        screenTitle = t.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
        ),
        sectionTitle = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        cardTitle = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        dialogTitle = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        body = t.bodyLarge,
        bodyDense = t.bodyMedium,
        dialogBody = t.bodyMedium.copy(lineHeight = 20.sp),
        caption = t.bodySmall,
        overline = t.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        ),
        metricValue = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        metricLabel = t.labelMedium,
        buttonLabel = t.labelLarge.copy(fontWeight = FontWeight.Medium),
        buttonSmall = t.labelMedium.copy(fontWeight = FontWeight.Medium),
        chip = t.labelMedium,
        tableHeader = t.labelSmall.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
        ),
        chartAxis = t.labelSmall.copy(
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        ),
        chartLabel = t.labelSmall,
        monoId = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = t.bodySmall.fontSize,
            lineHeight = t.bodySmall.lineHeight,
        ),
        monoValue = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = t.bodyMedium.fontSize,
            lineHeight = t.bodyMedium.lineHeight,
        ),
    )
}

/** `MaterialTheme.concordance.body`, `MaterialTheme.concordance.caption`, ... */
val MaterialTheme.concordance: ConcordanceTextStyles
    @Composable
    @ReadOnlyComposable
    get() = LocalConcordanceTextStyles.current

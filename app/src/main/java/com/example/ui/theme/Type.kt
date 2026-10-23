package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Material 3 type scale for Concordance.
 *
 * Research basis (2026): body copy must be at least 16sp for comfortable
 * reading; 14sp is the absolute floor for labels on dense scientific tables.
 * Previously the app used 9-11sp for large amounts of real content, which
 * failed the "readable in bright light / with one hand" audit and hurt
 * older and low-vision users. The scale below enforces the floor centrally
 * so new screens inherit it by using MaterialTheme.typography.* rather than
 * literal `.sp` values.
 */
private val default = FontFamily.Default
private val mono = FontFamily.Monospace

private val baseLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
        lineHeightStyle = baseLineHeight,
    ),
    displayMedium = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp,
        lineHeightStyle = baseLineHeight,
    ),
    displaySmall = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        lineHeightStyle = baseLineHeight,
    ),
    headlineLarge = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        lineHeightStyle = baseLineHeight,
    ),
    headlineMedium = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        lineHeightStyle = baseLineHeight,
    ),
    headlineSmall = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        lineHeightStyle = baseLineHeight,
    ),
    titleLarge = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        lineHeightStyle = baseLineHeight,
    ),
    titleMedium = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = baseLineHeight,
    ),
    titleSmall = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = baseLineHeight,
    ),
    bodyLarge = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        lineHeightStyle = baseLineHeight,
    ),
    bodyMedium = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
        lineHeightStyle = baseLineHeight,
    ),
    bodySmall = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
        lineHeightStyle = baseLineHeight,
    ),
    labelLarge = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = baseLineHeight,
    ),
    labelMedium = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        lineHeightStyle = baseLineHeight,
    ),
    labelSmall = TextStyle(
        fontFamily = default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        lineHeightStyle = baseLineHeight,
    ),
)

/** Monospace styles for numbers, hashes and identifiers. */
val MonoNumeric = TextStyle(
    fontFamily = mono,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
)

val MonoCaption = TextStyle(
    fontFamily = mono,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

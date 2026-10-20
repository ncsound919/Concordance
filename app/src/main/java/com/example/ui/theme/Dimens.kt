package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Design tokens for the Concordance app.
 *
 * One source of truth for spacing, corner radius and minimum interactive sizes.
 * Screens should use these instead of ad-hoc `.dp` literals so spacing stays
 * consistent as the product grows (see UI audit, 2026-10).
 *
 * Accessibility contract:
 *  - [TouchTargetMin] is 48.dp, the Material minimum. Icon-only controls must
 *    be wrapped in a 48dp hit target (IconButton already does this).
 *  - [BodyMin] is the smallest text size allowed for reading copy (14.sp).
 *    Anything at 11-13.sp is reserved for labels/metadata, never body text.
 */
object Dimens {
    // Spacing scale (4dp base grid)
    val SpaceXXS = 2.dp
    val SpaceXS = 4.dp
    val SpaceS = 8.dp
    val SpaceM = 12.dp
    val SpaceL = 16.dp
    val SpaceXL = 24.dp
    val SpaceXXL = 32.dp

    // Corner radii (one family, no strays)
    val RadiusS = 8.dp
    val RadiusM = 12.dp
    val RadiusL = 16.dp
    val RadiusPill = 999.dp

    val ShapeS = RoundedCornerShape(RadiusS)
    val ShapeM = RoundedCornerShape(RadiusM)
    val ShapeL = RoundedCornerShape(RadiusL)
    val ShapePill = RoundedCornerShape(RadiusPill)

    // Layout
    val ScreenPadding = 16.dp
    val CardPadding = 16.dp
    val SectionGap = 20.dp
    val TouchTargetMin = 48.dp

    // Type floors (use as reminders; real scale lives in Type.kt)
    const val BodyMinSp = 14
    const val LabelMinSp = 12
}

package com.example.concordance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.concordance.model.CorrectedPoint
import com.example.ui.theme.*
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun SensitivityCurveChart(
    curve: List<CorrectedPoint>,
    rNull: Double,
    rSig: Double?,
    rLow: Double?,
    rHigh: Double?,
    modifier: Modifier = Modifier
) {
    var touchX by remember { mutableStateOf<Float?>(null) }
    var hoveredPoint by remember { mutableStateOf<CorrectedPoint?>(null) }

    if (curve.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SlateCard),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Threshold curve unavailable (infeasible prevalence bounds)",
                color = TextSecondary,
                style = MaterialTheme.concordance.bodyDense
            )
        }
        return
    }

    val minR = curve.first().r
    val maxR = 1.0
    val maxOrY = 1.4

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SlateCard)
            .padding(14.dp)
    ) {
        // Chart Header & Interactive Tooltip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CORRECTED ODDS RATIO CURVE",
                color = ElectricCyan,
                style = MaterialTheme.concordance.overline,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            if (hoveredPoint != null) {
                val pt = hoveredPoint!!
                Text(
                    text = String.format(
                        Locale.US,
                        "r=%.2f → OR=%.2f [%.2f–%.2f]",
                        pt.r,
                        pt.oddsRatio ?: 0.0,
                        pt.ciLow ?: 0.0,
                        pt.ciHigh ?: 0.0
                    ),
                    color = NeonEmerald,
                    style = MaterialTheme.concordance.monoId,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "Touch/drag to inspect",
                    color = TextMuted,
                    style = MaterialTheme.concordance.monoId
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Canvas Area
        val chartGridColor = SlateCardBorder
        val chartPrimaryText = TextPrimary
        val chartAccent = ElectricCyan
        val chartDanger = VividCoral
        val chartWarning = WarningGold
        val chartSuccess = NeonEmerald
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(curve) {
                    detectTapGestures(
                        onPress = { offset ->
                            touchX = offset.x
                        }
                    )
                }
                .pointerInput(curve) {
                    detectDragGestures(
                        onDragStart = { offset -> touchX = offset.x },
                        onDrag = { change, _ ->
                            touchX = change.position.x
                        },
                        onDragEnd = { touchX = null; hoveredPoint = null },
                        onDragCancel = { touchX = null; hoveredPoint = null }
                    )
                }
        ) {
            val w = size.width
            val h = size.height

            val padLeft = 36f
            val padBottom = 28f
            val padTop = 16f
            val padRight = 16f

            val plotW = w - padLeft - padRight
            val plotH = h - padTop - padBottom

            fun xForR(rVal: Double): Float {
                val norm = ((rVal - minR) / (maxR - minR)).coerceIn(0.0, 1.0)
                return padLeft + (norm * plotW).toFloat()
            }

            fun yForOr(orVal: Double): Float {
                val norm = (orVal / maxOrY).coerceIn(0.0, 1.0)
                return (padTop + plotH - (norm * plotH)).toFloat()
            }

            // Gridlines & Axis labels
            val orLevels = listOf(0.25, 0.50, 0.75, 1.0, 1.25)
            val gridStroke = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))

            for (orVal in orLevels) {
                val y = yForOr(orVal)
                val isNullLine = orVal == 1.0
                val lineColor = if (isNullLine) chartDanger.copy(alpha = 0.8f) else chartGridColor.copy(alpha = 0.7f)
                val lineStroke = if (isNullLine) Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))) else gridStroke

                drawLine(
                    color = lineColor,
                    start = Offset(padLeft, y),
                    end = Offset(padLeft + plotW, y),
                    strokeWidth = lineStroke.width,
                    pathEffect = lineStroke.pathEffect
                )
            }

            // Stated Range Highlight Box
            if (rLow != null && rHigh != null) {
                val xLo = xForR(rLow)
                val xHi = xForR(rHigh)
                drawRect(
                    color = chartWarning.copy(alpha = 0.12f),
                    topLeft = Offset(xLo, padTop),
                    size = androidx.compose.ui.geometry.Size(xHi - xLo, plotH)
                )
            }

            // 95% Confidence Interval Band (Translucent Polygon)
            val ciPath = Path()
            var first = true
            for (pt in curve) {
                val rVal = pt.r
                val hi = pt.ciHigh ?: pt.oddsRatio ?: 0.0
                val x = xForR(rVal)
                val y = yForOr(hi)
                if (first) {
                    ciPath.moveTo(x, y)
                    first = false
                } else {
                    ciPath.lineTo(x, y)
                }
            }
            for (i in curve.indices.reversed()) {
                val pt = curve[i]
                val rVal = pt.r
                val lo = pt.ciLow ?: pt.oddsRatio ?: 0.0
                val x = xForR(rVal)
                val y = yForOr(lo)
                ciPath.lineTo(x, y)
            }
            ciPath.close()

            drawPath(
                path = ciPath,
                color = chartAccent.copy(alpha = 0.18f)
            )

            // Main OR Curve Line
            val curvePath = Path()
            first = true
            for (pt in curve) {
                val rVal = pt.r
                val orVal = pt.oddsRatio ?: 0.0
                val x = xForR(rVal)
                val y = yForOr(orVal)
                if (first) {
                    curvePath.moveTo(x, y)
                    first = false
                } else {
                    curvePath.lineTo(x, y)
                }
            }

            drawPath(
                path = curvePath,
                color = chartAccent,
                style = Stroke(width = 3.5f)
            )

            // r* Null-Crossing Marker
            if (rNull in minR..maxR) {
                val xNull = xForR(rNull)
                val yNull = yForOr(1.0)
                drawLine(
                    color = chartDanger,
                    start = Offset(xNull, padTop),
                    end = Offset(xNull, padTop + plotH),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
                drawCircle(
                    color = chartDanger,
                    radius = 5.5f,
                    center = Offset(xNull, yNull)
                )
            }

            // r_sig Significance Threshold Marker
            if (rSig != null && rSig in minR..maxR) {
                val xSig = xForR(rSig)
                drawLine(
                    color = chartWarning,
                    start = Offset(xSig, padTop),
                    end = Offset(xSig, padTop + plotH),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )
            }

            // Interactive Touch Crosshair
            touchX?.let { tx ->
                val clampedX = tx.coerceIn(padLeft, padLeft + plotW)
                val fraction = (clampedX - padLeft) / plotW
                val rTouch = minR + fraction * (maxR - minR)

                // Find closest point in curve
                val closest = curve.minByOrNull { kotlin.math.abs(it.r - rTouch) }
                if (closest != null) {
                    hoveredPoint = closest
                    val cx = xForR(closest.r)
                    val cy = yForOr(closest.oddsRatio ?: 1.0)

                    drawLine(
                        color = chartPrimaryText.copy(alpha = 0.8f),
                        start = Offset(cx, padTop),
                        end = Offset(cx, padTop + plotH),
                        strokeWidth = 1.5f
                    )
                    drawCircle(
                        color = chartSuccess,
                        radius = 6f,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5f,
                        center = Offset(cx, cy)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Axis and Legend Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(Locale.US, "r = %.2f", minR),
                color = TextSecondary,
                style = MaterialTheme.concordance.monoId,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Relative Sensitivity (r = s_brain / s_non_cns) →",
                color = TextMuted,
                style = MaterialTheme.concordance.monoId
            )
            Text(
                text = "r = 1.00",
                color = TextSecondary,
                style = MaterialTheme.concordance.monoId,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Clean Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(color = chartDanger, label = "r* Null (OR=1.0)", isDash = true)
            if (rSig != null) {
                LegendItem(color = chartWarning, label = "r_sig (${String.format(Locale.US, "%.2f", rSig)})", isDash = true)
            }
            LegendItem(color = chartAccent, label = "Corrected OR (95% CI)", isDash = false)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, isDash: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(3.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.concordance.bodyDense
        )
    }
}

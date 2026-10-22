package com.example.concordance.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Dimens
import com.example.ui.theme.LocalSkin
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.concordance
import com.example.ui.theme.fadeUp
import com.example.ui.theme.rememberBrandBrush
import com.example.ui.theme.rememberCardBrush

/**
 * Shared visual primitives.
 *
 * These replace the duplicated `SectionCard`/`StatTile`/pill helpers that had
 * drifted between screens (different radii, paddings, and type scales). New
 * screens should compose from here so the UI stays consistent as it grows.
 */

@Composable
fun AppSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val skin = LocalSkin
    val shape = RoundedCornerShape(skin.cardRadius)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (skin.layered) Modifier.fadeUp() else Modifier)
            .clip(shape)
            .then(
                if (skin.layered) {
                    // Layered: a surface gradient plus a hairline brand edge
                    // along the top, so a flat box reads as a deliberate object.
                    Modifier
                        .background(rememberCardBrush())
                        .border(1.dp, SlateCardBorder, shape)
                } else {
                    // Website: flat, opaque card with a hairline border.
                    Modifier
                        .background(SlateCard)
                        .border(1.dp, SlateCardBorder, shape)
                }
            )
            .padding(skin.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        if (skin.layered) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(topStart = skin.cardRadius, topEnd = skin.cardRadius))
                    .background(rememberBrandBrush()),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXXS)) {
            Text(
                text = title,
                style = MaterialTheme.concordance.cardTitle,
                color = TextPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.concordance.caption,
                    color = TextSecondary,
                )
            }
        }
        content()
    }
}

@Composable
fun AppStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = TextPrimary,
    hint: String? = null,
) {
    val skin = LocalSkin
    val shape = RoundedCornerShape(skin.cardRadius)
    // The value fades along its own accent colour rather than using a fixed
    // brand gradient: a caller that passes a semantic colour (danger, success)
    // must not have it overwritten by the brand hue.
    val valueBrush = remember(accent) {
        Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.72f)))
    }
    Column(
        modifier = modifier
            .clip(shape)
            .then(
                if (skin.layered) {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(SlateCardElevated, SlateCardElevated.copy(alpha = 0.55f))
                        )
                    )
                } else {
                    Modifier.background(SlateCardElevated)
                }
            )
            .border(1.dp, SlateCardBorder.copy(alpha = 0.7f), shape)
            .padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.concordance.metricValue.copy(brush = valueBrush),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.concordance.metricLabel,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.concordance.caption,
                color = TextMuted,
            )
        }
    }
}

@Composable
fun AppSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.concordance.sectionTitle,
            color = TextPrimary,
        )
        trailing?.invoke()
    }
}

@Composable
fun AppChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary,
    background: Color = SlateCardElevated,
    shape: Shape = Dimens.ShapeS,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .padding(horizontal = Dimens.SpaceS, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.concordance.chip,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpaceXXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SlateCardElevated),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "–",
                style = MaterialTheme.concordance.sectionTitle,
                color = TextMuted,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.concordance.cardTitle,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Shimmering placeholder used while content loads.
 * Preferred over a blocking spinner (perceived-performance guidance).
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    shape: Shape = Dimens.ShapeS,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(SlateCardElevated.copy(alpha = alpha))
            .semantics { contentDescription = "Loading" },
    )
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Dimens.ShapeM)
            .background(SlateCard)
            .border(1.dp, SlateCardBorder, Dimens.ShapeM)
            .padding(Dimens.CardPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        Skeleton(height = 18.dp)
        Skeleton(height = 12.dp, modifier = Modifier.fillMaxWidth(0.7f))
        Spacer(Modifier.height(4.dp))
        Skeleton(height = 12.dp)
    }
}

/** Small status dot + label, used in agent/registry rows. */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Row(
        modifier = modifier
            .clip(Dimens.ShapePill)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = Dimens.SpaceS, vertical = 3.dp)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = text,
            style = MaterialTheme.concordance.chip,
            color = color,
        )
    }
}

/** Standard vertical spacing between top-level sections. */
@Composable
fun SectionSpacer() = Spacer(Modifier.height(Dimens.SectionGap))

/** Default content padding for screen scaffolds. */
val ScreenPadding = PaddingValues(
    horizontal = Dimens.ScreenPadding,
    vertical = Dimens.SpaceM,
)

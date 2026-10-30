package com.example.concordance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Dimens
import com.example.ui.theme.Skin
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.concordance
import androidx.compose.ui.res.stringResource
import com.example.R

/**
 * Skin picker.
 *
 * Each option shows a swatch of the four colours that define the look, so the
 * choice is made by looking rather than by reading. The preview is rendered
 * from the same palette functions the app uses, so it cannot drift from the
 * real thing.
 */
@Composable
fun SkinPickerDialog(
    current: Skin,
    onSelect: (Skin) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.auto_41def7a0febe),
                style = MaterialTheme.concordance.cardTitle,
                color = TextPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                Text(text = stringResource(R.string.auto_3d23a60c611e),
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextSecondary,
                )
                Skin.entries.forEach { skin ->
                    SkinOption(
                        skin = skin,
                        selected = skin == current,
                        onClick = { onSelect(skin) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.auto_e9b450d14bc2), color = TextPrimary)
            }
        },
    )
}

@Composable
private fun SkinOption(
    skin: Skin,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val swatches = skinSwatches(skin)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusM))
            .background(
                if (selected) swatches.accent.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) swatches.accent.copy(alpha = 0.55f) else Color.Transparent,
                shape = RoundedCornerShape(Dimens.RadiusM),
            )
            .clickable(onClick = onClick)
            .padding(Dimens.SpaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            swatches.list.forEach { colour ->
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colour)
                        .border(1.dp, swatches.outline, CircleShape),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = skin.label,
                style = MaterialTheme.concordance.cardTitle,
                color = TextPrimary,
            )
            Text(
                text = skin.blurb,
                style = MaterialTheme.concordance.caption,
                color = TextSecondary,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = swatches.accent,
            )
        }
    }
}

/** The four colours that best identify a skin. */
private data class Swatches(
    val list: List<Color>,
    val accent: Color,
    val outline: Color,
)

@Composable
private fun skinSwatches(skin: Skin): Swatches {
    val dark = com.example.ui.theme.darkPalette(skin)
    val light = com.example.ui.theme.lightPalette(skin)
    // The preview shows the dark palette, because that is where the skin's
    // identity is strongest, with the light accent as the fourth chip.
    return Swatches(
        list = listOf(dark.background, dark.surface, dark.accent, light.accent),
        accent = dark.accent,
        outline = dark.borderStrong,
    )
}

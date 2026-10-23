package com.example.concordance.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.concordance.autonomy.CorpusStatus
import com.example.ui.theme.ClinicalTeal
import com.example.ui.theme.Dimens
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightDeep
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.concordance

/**
 * The papers the automation may read, and the folder grant that allows it.
 *
 * Android scoped storage means the app cannot simply look at `/sdcard/Concordance`; the
 * user picks the folder in the system picker and the grant is persisted. This card is where
 * that choice lives, together with what the last indexing pass actually produced.
 */
@Composable
fun LocalCorpusCard(
    status: CorpusStatus,
    onChooseFolder: () -> Unit,
    onIndexNow: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSectionCard(
        title = "Local corpus",
        subtitle = "Papers on this phone: PDF, txt, csv, md",
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Folder",
                    style = MaterialTheme.concordance.overline,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = status.folderName ?: "No folder chosen",
                    style = MaterialTheme.concordance.bodyDense,
                    color = if (status.hasFolder) TextPrimary else WarningAmber,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onChooseFolder,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClinicalTeal,
                    contentColor = MidnightDeep
                )
            ) {
                Text(
                    if (status.hasFolder) "Change" else "Choose folder",
                    style = MaterialTheme.concordance.buttonSmall
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            CorpusStat("Files", status.files.toString(), Modifier.weight(1f))
            CorpusStat("Chunks", status.chunks.toString(), Modifier.weight(1f))
            CorpusStat("PDFs", status.pdfs.toString(), Modifier.weight(1f))
        }

        if (status.running) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
            ) {
                val transition = rememberInfiniteTransition(label = "corpus-busy")
                val alpha by transition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "corpus-busy-alpha"
                )
                Text(
                    text = "●",
                    style = MaterialTheme.concordance.caption,
                    color = ElectricCyan,
                    modifier = Modifier.alpha(alpha)
                )
                Text(
                    text = "Indexing — PDFs are read page by page, this can take a while.",
                    style = MaterialTheme.concordance.caption,
                    color = ElectricCyan
                )
            }
        } else {
            Text(
                text = when {
                    !status.hasFolder -> "Choose the folder your papers live in. Nothing outside it is read."
                    status.files == 0 -> "Nothing indexed yet."
                    else -> "Indexed ${status.files} files" +
                        (status.note?.let { " · $it" } ?: "")
                },
                style = MaterialTheme.concordance.caption,
                color = if (status.hasFolder) TextSecondary else TextMuted
            )
        }

        if (status.textlessPdfs > 0 && !status.running) {
            Text(
                text = "${status.textlessPdfs} PDFs had no text layer (scans need OCR).",
                style = MaterialTheme.concordance.caption,
                color = WarningAmber
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)
        ) {
            Button(
                onClick = onIndexNow,
                enabled = status.hasFolder && !status.running,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SlateCardElevated,
                    contentColor = TextPrimary
                )
            ) {
                Text(
                    if (status.running) "Indexing…" else "Index now",
                    style = MaterialTheme.concordance.buttonSmall
                )
            }
            if (status.hasFolder) {
                TextButton(onClick = onForget) {
                    Text(
                        "Forget",
                        style = MaterialTheme.concordance.caption,
                        color = TextSecondary
                    )
                }
            }
        }

        Text(
            text = "Reading stays on the phone. PDF text extraction runs locally; nothing is uploaded.",
            style = MaterialTheme.concordance.caption,
            color = TextMuted,
            modifier = Modifier.padding(top = Dimens.SpaceXXS)
        )
    }
}

@Composable
private fun CorpusStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(vertical = Dimens.SpaceXXS),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.concordance.caption,
            color = TextSecondary,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.concordance.bodyDense,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

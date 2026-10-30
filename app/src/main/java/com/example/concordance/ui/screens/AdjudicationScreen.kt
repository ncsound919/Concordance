package com.example.concordance.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.concordance.integration.queue.JobDataClass
import com.example.concordance.integration.queue.WorkerRoutingPolicy
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import com.example.concordance.model.Verdict
import com.example.concordance.ui.ConcordanceViewModel
import com.example.concordance.ui.components.EmptyState
import com.example.concordance.ui.components.SensitivityCurveChart
import com.example.ui.theme.*
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
fun AdjudicationScreen(
    viewModel: ConcordanceViewModel,
    onNavigateToRegistry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val claims by viewModel.claims.collectAsStateWithLifecycle()
    val selectedAnchor by viewModel.selectedAnchor.collectAsStateWithLifecycle()
    val adj by viewModel.adjudicationResult.collectAsStateWithLifecycle()
    val sn by viewModel.sn.collectAsStateWithLifecycle()
    val rLow by viewModel.rLow.collectAsStateWithLifecycle()
    val rHigh by viewModel.rHigh.collectAsStateWithLifecycle()

    var showPresetMenu by remember { mutableStateOf(false) }
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        copiedNotice = "Copied to clipboard!"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightDeep)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // Section 1: Anchor Selector Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Both sides share the row instead of each claiming its intrinsic
                // width: at large font scales that wrapped the link into a vertical
                // stack ("Brows / e / Regis / try").
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.auto_732c4f9a7d35),
                        style = MaterialTheme.concordance.overline,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = stringResource(R.string.auto_108c5211949e),
                        style = MaterialTheme.concordance.buttonSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ClinicalTeal,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable { onNavigateToRegistry() }
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(claims) { anchor ->
                        val isSelected = anchor.id == selectedAnchor?.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectAnchor(anchor) },
                            label = {
                                Text(
                                    text = anchor.label.take(28) + if (anchor.label.length > 28) "..." else "",
                                    style = MaterialTheme.concordance.bodyDense,
                                    maxLines = 1
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SlateCardElevated,
                                selectedLabelColor = ElectricCyan,
                                containerColor = SlateCard,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) ElectricCyan else SlateCardBorder,
                                selectedBorderColor = ElectricCyan
                            )
                        )
                    }
                }
            }
        }

        if (adj != null) {
            val result = adj!!

            // Section 2: Selected Anchor Metadata Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateCard)
                        .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = result.anchor.label,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.concordance.cardTitle,
                            color = TextPrimary
                        )
                        EvidenceBadge(result.anchor.evidenceGrade)
                    }

                    Text(
                        text = result.anchor.source,
                        style = MaterialTheme.concordance.caption,
                        color = TextSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (result.anchor.hasCounts) {
                            DataMetricPill(
                                label = "Brain counts",
                                value = "${result.anchor.brainEvents}/${result.anchor.brainTotal} (${String.format(Locale.US, "%.1f", result.prevalenceBrain * 100)}%)"
                            )
                            DataMetricPill(
                                label = "Other counts",
                                value = "${result.anchor.otherEvents}/${result.anchor.otherTotal} (${String.format(Locale.US, "%.1f", result.prevalenceOther * 100)}%)"
                            )
                        } else {
                            DataMetricPill(
                                label = "Published OR",
                                value = "${result.anchor.oddsRatio} [${result.anchor.orCiLow}–${result.anchor.orCiHigh}]"
                            )
                            DataMetricPill(
                                label = "Ref. prevalence",
                                value = "${String.format(Locale.US, "%.1f", (result.anchor.referencePrevalence ?: 0.0) * 100)}%"
                            )
                        }
                    }
                }
            }

            // Section 3: THE STRATEGIC POWERHOUSE: Decision-Relevant Verdict Banner
            item {
                val verdictColor = Color(result.verdict.badgeColorHex)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(verdictColor.copy(alpha = 0.12f))
                        .border(1.5.dp, verdictColor.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(verdictColor)
                            )
                            Text(
                                text = "VERDICT: ${result.verdict.label.uppercase(Locale.US)}",
                                color = verdictColor,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.concordance.overline,
                                letterSpacing = 1.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                copyToClipboard("Concordance Citable Argument", result.citableArgument)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Copy citable argument",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = "“${result.plainLanguageVerdict}”",
                        color = TextPrimary,
                        style = MaterialTheme.concordance.body,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    )

                    Text(
                        text = result.rationale,
                        color = TextSecondary,
                        style = MaterialTheme.concordance.bodyDense,
                        lineHeight = 17.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                copyToClipboard("Concordance Quote", result.plainLanguageVerdict)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = verdictColor.copy(alpha = 0.25f),
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.auto_580d3d47cd64), style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.generateAndSaveCapsule()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = MidnightDeep
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.auto_e7f0dd418978), style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Section 4: 2026 Clinical Case Report Anchor Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateCard)
                        .border(1.dp, ClinicalTeal.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = ClinicalTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(text = stringResource(R.string.auto_fd1a06c8f535),
                            color = ClinicalTeal,
                            style = MaterialTheme.concordance.overline,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(text = stringResource(R.string.auto_35ea2d2f68de),
                        color = TextSecondary,
                        style = MaterialTheme.concordance.bodyDense,
                        lineHeight = 18.sp
                    )
                }
            }

            // Section 5: Interactive Sensitivity Curve Canvas
            item {
                SensitivityCurveChart(
                    curve = result.curve,
                    rNull = result.rNull,
                    rSig = result.rSignificance,
                    rLow = rLow,
                    rHigh = rHigh
                )
            }

            // Section 6: Sensitivity Threshold Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThresholdCard(
                        title = "ARTIFACT NULL r*",
                        value = String.format(Locale.US, "%.2f×", result.rNull),
                        subtitle = "Depletion = 100% artifact",
                        color = VividCoral,
                        modifier = Modifier.weight(1f)
                    )
                    ThresholdCard(
                        title = "SIGNIFICANCE r_sig",
                        value = if (result.rSignificance != null) String.format(Locale.US, "%.2f×", result.rSignificance) else "None",
                        subtitle = "Distinguishable from 0",
                        color = WarningGold,
                        modifier = Modifier.weight(1f)
                    )
                    ThresholdCard(
                        title = "OBSERVED OR",
                        value = String.format(Locale.US, "%.3f", result.observedOr),
                        subtitle = "95% CI: ${String.format(Locale.US, "%.2f", result.observedCiLow)}–${String.format(Locale.US, "%.2f", result.observedCiHigh)}",
                        color = ElectricCyan,
                        modifier = Modifier.weight(1.1f)
                    )
                }
            }

            // Section 7: Assumptions & Range Controls
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateCard)
                        .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = stringResource(R.string.auto_a8ac84002f18),
                        color = ElectricCyan,
                        style = MaterialTheme.concordance.overline,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    // Non-CNS Sensitivity slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = stringResource(R.string.auto_d38b792218c9),
                                style = MaterialTheme.concordance.bodyDense,
                                color = TextSecondary
                            )
                            Text(
                                text = String.format(Locale.US, "%.2f", sn),
                                style = MaterialTheme.concordance.monoValue,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Slider(
                            value = sn.toFloat(),
                            onValueChange = { viewModel.updateSn(it.toDouble()) },
                            valueRange = 0.5f..1.0f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan,
                                inactiveTrackColor = SlateCardBorder
                            )
                        )
                    }

                    // Stated Range presets
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.auto_684de863585d),
                            style = MaterialTheme.concordance.bodyDense,
                            color = TextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.setRange(0.25, 0.38) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (rLow == 0.25 && rHigh == 0.38) ElectricCyan else TextSecondary
                                )
                            ) {
                                Text(stringResource(R.string.auto_c96864af294a), style = MaterialTheme.concordance.buttonSmall)
                            }

                            OutlinedButton(
                                onClick = { viewModel.setRange(0.80, 1.00) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (rLow == 0.80 && rHigh == 1.00) ElectricCyan else TextSecondary
                                )
                            ) {
                                Text(stringResource(R.string.auto_0f3e62ca4fe1), style = MaterialTheme.concordance.buttonSmall)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.applyPairedRangeToAdjudication() },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.auto_1dc943aaa3f2), style = MaterialTheme.concordance.buttonSmall)
                            }

                            OutlinedButton(
                                onClick = { viewModel.clearRange() },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.auto_a3da10f28f33), style = MaterialTheme.concordance.buttonSmall)
                            }
                        }
                    }
                }
            }

            // Section 8: What Would Change This
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateCard)
                        .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = stringResource(R.string.auto_6922130f8796),
                        color = ElectricCyan,
                        style = MaterialTheme.concordance.overline,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    for (itemText in result.whatWouldChange) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("•", color = ElectricCyan, fontWeight = FontWeight.Bold)
                            Text(
                                text = itemText,
                                color = TextSecondary,
                                style = MaterialTheme.concordance.bodyDense,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // Section 9: Asynchronous Compute Offload & OS Scheduler
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateCard)
                        .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            Text(text = stringResource(R.string.auto_9fd543c11f40),
                                color = ElectricCyan,
                                style = MaterialTheme.concordance.overline,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(text = stringResource(R.string.auto_a05988316ad2),
                            color = ClinicalTeal,
                            style = MaterialTheme.concordance.cardTitle,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(text = stringResource(R.string.auto_8414d8d63d97),
                        style = MaterialTheme.concordance.bodyDense,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.submitAsyncComputeJob(
                                    dataClass = JobDataClass.SENSITIVITY_PERMUTATION,
                                    routing = WorkerRoutingPolicy.AWS_BATCH_CPU,
                                    inputs = "Anchor: ${selectedAnchor?.label ?: "Giannoudis 2024"}",
                                    modelRev = "concordance-bootstrap-null-v2.1",
                                    params = mapOf("iterations" to "10000", "alpha" to "0.05")
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.auto_190541c83c39), style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.submitAsyncComputeJob(
                                    dataClass = JobDataClass.IMAGING_VLM_FIGURE_EXTRACTION,
                                    routing = WorkerRoutingPolicy.AWS_FARGATE_GPU,
                                    inputs = "Anchor: Current Oncology 2026 ESR1 Case Report",
                                    modelRev = "vlm-biomed-qwen2-vl-72b-ocr",
                                    params = mapOf("ocr" to "true", "confidence" to "0.95")
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated, contentColor = ElectricCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.auto_6706a8b20d35), style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // No result yet: explain the next action instead of rendering a blank page.
            item {
                EmptyState(
                    title = if (claims.isEmpty()) "No claim anchors yet" else "Select an anchor to adjudicate",
                    message = if (claims.isEmpty()) {
                        "Add a claim in the Registry, then return here to run the detection correction."
                    } else {
                        "Pick an anchor above. A verdict stays hidden until the evidence supports it."
                    },
                )
            }
        }
    }

    // Copied Toast notification
    if (copiedNotice != null) {
        LaunchedEffect(copiedNotice) {
            kotlinx.coroutines.delay(2000)
            copiedNotice = null
        }
    }
}

@Composable
fun EvidenceBadge(grade: EvidenceGrade) {
    val (color, label) = when (grade) {
        EvidenceGrade.COUNTS -> Pair(NeonEmerald, "2×2 Counts")
        EvidenceGrade.SUMMARY -> Pair(ClinicalTeal, "Summary OR")
        EvidenceGrade.ABSTRACT_ONLY -> Pair(WarningGold, "Abstract Only")
        EvidenceGrade.SYNTHETIC -> Pair(VividCoral, "Synthetic")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.concordance.chip,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun DataMetricPill(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.concordance.metricLabel, color = TextMuted)
        Text(
            text = value,
            style = MaterialTheme.concordance.monoValue,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ThresholdCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SlateCard)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = title, style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = color)
        Text(
            text = value,
            style = MaterialTheme.concordance.metricValue,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace
        )
        Text(text = subtitle, style = MaterialTheme.concordance.caption, color = TextMuted, maxLines = 1)
    }
}

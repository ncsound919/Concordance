package com.example.concordance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.concordance.engine.PairedAnalyzer
import com.example.concordance.ui.ConcordanceViewModel
import com.example.ui.theme.*
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
fun PairedDataScreen(
    viewModel: ConcordanceViewModel,
    onNavigateToAdjudication: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val pairedCsv by viewModel.pairedCsvText.collectAsStateWithLifecycle()
    val pairedResult by viewModel.pairedResult.collectAsStateWithLifecycle()
    val pairedErrors by viewModel.pairedErrors.collectAsStateWithLifecycle()

    val powerTargetR by viewModel.powerTargetR.collectAsStateWithLifecycle()
    val powerDesired by viewModel.powerDesired.collectAsStateWithLifecycle()
    val powerReq by viewModel.powerRequirement.collectAsStateWithLifecycle()
    val powerSweep by viewModel.powerSweep.collectAsStateWithLifecycle()

    var showCsvEditor by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightDeep)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        Text(text = stringResource(R.string.auto_f455521c840f),
            style = MaterialTheme.concordance.overline,
            fontWeight = FontWeight.Bold,
            color = ElectricCyan,
            letterSpacing = 1.sp
        )
        Text(text = stringResource(R.string.auto_3fecf7c00a47),
            style = MaterialTheme.concordance.overline,
            color = TextSecondary,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SlateCard,
            contentColor = ElectricCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = ElectricCyan
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.auto_31bcd9d8d436), style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.auto_d7562af0f614), style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            // TAB 1: PAIRED DATA
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                // Preset selection chips
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.auto_03510c002b89), style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = TextMuted)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val sample = PairedAnalyzer.sampleBenchmarkDataset()
                                    viewModel.updatePairedCsv(sample)
                                    viewModel.analyzePairedCsv(sample, "plasma")
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.auto_0eaa7f0e89e5), style = MaterialTheme.concordance.buttonSmall, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    val syntheticCsv = "patient_id,site,tissue_assay,plasma_assay,tissue_result,plasma_result,data_source\n" +
                                            "SYN-01,brain,Panel,Liquid,yes,yes,synthetic\n" +
                                            "SYN-02,non_cns,Panel,Liquid,yes,yes,synthetic\n"
                                    viewModel.updatePairedCsv(syntheticCsv)
                                    viewModel.analyzePairedCsv(syntheticCsv, "plasma")
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.auto_f20db848e9fc), style = MaterialTheme.concordance.buttonSmall, color = VividCoral, maxLines = 1)
                            }
                        }
                    }
                }

                // Relative Sensitivity Result Card
                if (pairedResult != null) {
                    val res = pairedResult!!
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SlateCard)
                                .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(text = stringResource(R.string.auto_a1f407ddd128),
                                    style = MaterialTheme.concordance.overline,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = "${res.totalRows} paired samples",
                                    style = MaterialTheme.concordance.monoId,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (res.relativeSensitivity != null) {
                                val rEst = res.relativeSensitivity
                                val ci = res.relativeSensitivityCi
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "r = %.3f", rEst),
                                        style = MaterialTheme.concordance.metricValue,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (ci != null) {
                                        Text(
                                            text = "95% CI [${String.format(Locale.US, "%.2f", ci.first)}–${String.format(Locale.US, "%.2f", ci.second)}]",
                                            style = MaterialTheme.concordance.monoValue,
                                            color = TextSecondary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.applyPairedRangeToAdjudication()
                                        onNavigateToAdjudication()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = MidnightDeep
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.auto_4e5ae0000d26), style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text(text = stringResource(R.string.auto_19946116d3a0),
                                    color = VividCoral,
                                    style = MaterialTheme.concordance.bodyDense
                                )
                            }

                            if (res.synthetic) {
                                Text(text = stringResource(R.string.auto_06dfa9706c81),
                                    color = VividCoral,
                                    style = MaterialTheme.concordance.bodyDense,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            for (w in res.warnings) {
                                Text("• Warning: $w", color = WarningGold, style = MaterialTheme.concordance.bodyDense)
                            }
                        }
                    }

                    // 2x2 Concordance Matrices
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
                            Text(text = stringResource(R.string.auto_8d38dc79b010),
                                style = MaterialTheme.concordance.overline,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )

                            // Brain Stratum
                            Text(
                                text = "Brain Lesions (${res.brainTotal} samples):",
                                style = MaterialTheme.concordance.bodyDense,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ConcordanceCell("Tissue+/Plasma+", res.brainBothPos, NeonEmerald)
                                ConcordanceCell("Tissue+/Plasma-", res.brainTissueOnly, VividCoral)
                                ConcordanceCell("Tissue-/Plasma+", res.brainPlasmaOnly, WarningGold)
                                ConcordanceCell("Both-", res.brainBothNeg, TextMuted)
                            }

                            Divider(color = SlateCardBorder)

                            // Non-CNS Stratum
                            Text(
                                text = "Non-CNS Sites (${res.nonCnsTotal} samples):",
                                style = MaterialTheme.concordance.bodyDense,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ConcordanceCell("Tissue+/Plasma+", res.nonCnsBothPos, NeonEmerald)
                                ConcordanceCell("Tissue+/Plasma-", res.nonCnsTissueOnly, VividCoral)
                                ConcordanceCell("Tissue-/Plasma+", res.nonCnsPlasmaOnly, WarningGold)
                                ConcordanceCell("Both-", res.nonCnsBothNeg, TextMuted)
                            }
                        }
                    }
                }

                // CSV Editor Trigger
                item {
                    OutlinedButton(
                        onClick = { showCsvEditor = !showCsvEditor },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (showCsvEditor) "Hide CSV Editor" else "Edit Raw Paired CSV", style = MaterialTheme.concordance.buttonSmall)
                    }
                }

                if (showCsvEditor) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SlateCard)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = pairedCsv,
                                onValueChange = {
                                    viewModel.updatePairedCsv(it)
                                    viewModel.analyzePairedCsv(it, "plasma")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                textStyle = MaterialTheme.concordance.monoId.copy(
                                    color = TextPrimary
                                )
                            )
                        }
                    }
                }

                if (pairedErrors.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(VividCoral.copy(alpha = 0.15f))
                                .padding(10.dp)
                        ) {
                            Text(stringResource(R.string.auto_429778349786), color = VividCoral, fontWeight = FontWeight.Bold, style = MaterialTheme.concordance.bodyDense)
                            for (err in pairedErrors) {
                                Text("• $err", color = TextPrimary, style = MaterialTheme.concordance.bodyDense)
                            }
                        }
                    }
                }
            }
        } else {
            // TAB 2: POWER & SAMPLE SIZE CALCULATOR
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
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
                        Text(text = stringResource(R.string.auto_13d4cc515f37),
                            style = MaterialTheme.concordance.overline,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            letterSpacing = 1.sp
                        )
                        Text(text = stringResource(R.string.auto_987b43566647),
                            style = MaterialTheme.concordance.bodyDense,
                            color = TextSecondary
                        )

                        // Target true r slider
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.auto_8c3b44e09c85), style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
                                Text(
                                    text = String.format(Locale.US, "%.2f×", powerTargetR),
                                    style = MaterialTheme.concordance.monoId,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Slider(
                                value = powerTargetR.toFloat(),
                                onValueChange = { viewModel.updatePowerTargetR(it.toDouble()) },
                                valueRange = 0.35f..1.00f,
                                steps = 13,
                                colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                            )
                        }

                        // Desired power selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.auto_4067e4c47e20), style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = powerDesired == 0.80,
                                    onClick = { viewModel.updatePowerDesired(0.80) },
                                    label = { Text(stringResource(R.string.auto_5366a7416787)) }
                                )
                                FilterChip(
                                    selected = powerDesired == 0.90,
                                    onClick = { viewModel.updatePowerDesired(0.90) },
                                    label = { Text(stringResource(R.string.auto_ce25e2fd4794)) }
                                )
                            }
                        }
                    }
                }

                if (powerReq != null) {
                    val req = powerReq!!
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SlateCardElevated)
                                .border(1.dp, NeonEmerald.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = stringResource(R.string.auto_abe8629c6145),
                                style = MaterialTheme.concordance.overline,
                                fontWeight = FontWeight.Bold,
                                color = NeonEmerald
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RequirementMetric("Brain patients", req.patientsBrain.toString(), Modifier.weight(1f))
                                RequirementMetric("Non-CNS patients", req.patientsNonCns.toString(), Modifier.weight(1f))
                                RequirementMetric("Positives/site (m)", req.truePositivesPerSite.toString(), Modifier.weight(1f))
                            }

                            Text(
                                text = "Based on observed prevalence: ~${String.format(Locale.US, "%.1f", req.piBrain * 100)}% true brain positivity and ~${String.format(Locale.US, "%.1f", req.piOther * 100)}% true non-CNS positivity.",
                                style = MaterialTheme.concordance.bodyDense,
                                color = TextMuted
                            )
                        }
                    }
                }

                // Power Sweep Table
                if (powerSweep.isNotEmpty()) {
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
                            Text(text = stringResource(R.string.auto_196374a59330),
                                style = MaterialTheme.concordance.overline,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                            Text(text = stringResource(R.string.auto_bdd2fcc60a49),
                                style = MaterialTheme.concordance.bodyDense,
                                color = TextSecondary
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SlateCardElevated)
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.auto_e92ef86f3f6f), style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = TextMuted)
                                Text(stringResource(R.string.auto_91e1cbaf33e5), style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = TextMuted)
                                Text(stringResource(R.string.auto_aac18f4a5422), style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = TextMuted)
                                Text(stringResource(R.string.auto_a93333159d9a), style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = TextMuted)
                            }

                            for (row in powerSweep) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(String.format(Locale.US, "%.2f×", row.rTrue), style = MaterialTheme.concordance.monoId, color = ElectricCyan, fontFamily = FontFamily.Monospace)
                                    Text(row.truePositivesPerSite.toString(), style = MaterialTheme.concordance.monoId, color = TextPrimary, fontFamily = FontFamily.Monospace)
                                    Text(row.patientsBrain.toString(), style = MaterialTheme.concordance.monoId, color = NeonEmerald, fontFamily = FontFamily.Monospace)
                                    Text(row.patientsNonCns.toString(), style = MaterialTheme.concordance.monoId, color = TextSecondary, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConcordanceCell(label: String, count: Int, color: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = count.toString(), style = MaterialTheme.concordance.monoValue, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
        Text(text = label, style = MaterialTheme.concordance.monoId, color = TextMuted)
    }
}

@Composable
fun RequirementMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SlateCard)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, style = MaterialTheme.concordance.cardTitle, fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontFamily = FontFamily.Monospace)
        Text(text = label, style = MaterialTheme.concordance.monoId, color = TextMuted, maxLines = 1)
    }
}

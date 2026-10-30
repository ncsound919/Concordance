package com.example.concordance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.concordance.learning.ModelRevision
import com.example.concordance.learning.ParameterSearch
import com.example.concordance.ui.ConcordanceViewModel
import com.example.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R

/**
 * Self-learning tab (Phase 1): runs a bounded dry-run round, shows candidate
 * scores, and requires explicit human approval to promote or revert a model
 * revision. Nothing is ever promoted automatically.
 */
@Composable
fun ModelLearningTab(
    viewModel: ConcordanceViewModel,
    modifier: Modifier = Modifier
) {
    val isRunning by viewModel.isRunningLearningRound.collectAsStateWithLifecycle()
    val report by viewModel.learningReport.collectAsStateWithLifecycle()
    val candidates by viewModel.learningCandidates.collectAsStateWithLifecycle()
    val summary by viewModel.trainingSummary.collectAsStateWithLifecycle()
    val current by viewModel.currentModelRevision.collectAsStateWithLifecycle()
    val revisions by viewModel.modelRevisions.collectAsStateWithLifecycle()
    val diff by viewModel.revisionDiff.collectAsStateWithLifecycle()
    val audit by viewModel.learningAudit.collectAsStateWithLifecycle()
    val health by viewModel.learningHealth.collectAsStateWithLifecycle()
    val autoRoundThreshold by viewModel.autoRoundThreshold.collectAsStateWithLifecycle()
    val stabilityThreshold by viewModel.stabilityThreshold.collectAsStateWithLifecycle()
    val challenges by viewModel.allChallenges.collectAsStateWithLifecycle()
    val lastAutoCount by viewModel.lastAutoRoundChallengeCount.collectAsStateWithLifecycle()
    val telemetrySuggestions by viewModel.telemetrySuggestions.collectAsStateWithLifecycle()

    var approver by remember { mutableStateOf("") }
    var pendingCandidate by remember { mutableStateOf<ParameterSearch.CandidateScore?>(null) }
    var pendingRevert by remember { mutableStateOf<ModelRevision?>(null) }
    var importText by remember { mutableStateOf("") }
    val archiveExport by viewModel.revisionArchiveExport.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            AppSectionCard(title = "CURRENT MODEL REVISION") {
                val r = current
                if (r == null) {
                    Text(
                        "No promoted revision yet. The engine is running on baseline parameters " +
                            "(sn=0.90, referencePrevalence=0.2075).",
                        style = MaterialTheme.concordance.overline,
                        color = TextSecondary
                    )
                } else {
                    Text(
                        "${r.id}  •  promoted by ${r.promotedBy ?: "-"}",
                        style = MaterialTheme.concordance.monoId,
                        fontWeight = FontWeight.Bold,
                        color = NeonEmerald,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "sn=${r.parameters.sn}  referencePrevalence=${r.parameters.referencePrevalence}  " +
                            "z95=${r.parameters.z95}  E=${"%.4f".format(r.metricE)}",
                        style = MaterialTheme.concordance.monoId,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    r.parentRevisionId?.let {
                        Text("parent: $it", style = MaterialTheme.concordance.monoId, color = TextMuted, fontFamily = FontFamily.Monospace)
                     }
                     Spacer(Modifier.height(6.dp))
                     OutlinedButton(
                         onClick = { viewModel.reAdjudicateUnder(r.id) },
                         shape = RoundedCornerShape(8.dp),
                         modifier = Modifier.fillMaxWidth()
                     ) {
                         Text(stringResource(R.string.auto_3fc689486fde), style = MaterialTheme.concordance.overline)
                     }
                    }
                }
            }

        item {
            AppSectionCard(title = "RUN A BOUNDED LEARNING ROUND (DRY RUN)") {
                Text(
                    "Scans a bounded parameter grid and scores each candidate. A dry run " +
                        "never persists or promotes anything — promotion is a separate, " +
                        "human-approved step below.",
                    style = MaterialTheme.concordance.overline,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.runLearningRound() },
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isRunning) "Scanning..." else "Run Dry Run", style = MaterialTheme.concordance.monoId, fontWeight = FontWeight.Bold)
                }
                summary?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.concordance.monoId, color = ClinicalTeal, fontFamily = FontFamily.Monospace)
                }
            }
        }

        report?.let { r ->
            item {
                AppSectionCard(title = "DRY RUN RESULT") {
                    Text(
                        "candidate E=${"%.4f".format(r.candidateResult.total)}   " +
                            "baseline E=${"%.4f".format(r.baselineResult.total)}",
                        style = MaterialTheme.concordance.monoId,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "stability=${"%.3f".format(r.candidateResult.verdictStabilityPenalty)}  " +
                            "resolutionMiss=${"%.3f".format(r.candidateResult.challengeResolutionMissRate)}  " +
                            "calibration=${"%.3f".format(r.candidateResult.calibrationError)}  " +
                            "parsimony=${"%.3f".format(r.candidateResult.parsimony)}",
                        style = MaterialTheme.concordance.monoId,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (r.improved) "WOULD PROMOTE (pending human approval)" else "No promotion",
                        style = MaterialTheme.concordance.monoId,
                        fontWeight = FontWeight.Bold,
                        color = if (r.improved) NeonEmerald else TextMuted
                    )
                    Text(r.notes, style = MaterialTheme.concordance.monoId, color = TextMuted, fontFamily = FontFamily.Monospace)
                }
            }
        }

        diff?.let { d ->
            item {
                AppSectionCard(title = "WHAT CHANGED (${d.fromRevisionId ?: "baseline"} -> ${d.toRevisionId ?: "candidate"})") {
                    Text(
                        "sn ${d.parameterDelta.snFrom} -> ${d.parameterDelta.snTo}   " +
                            "ref ${d.parameterDelta.referencePrevalenceFrom} -> ${d.parameterDelta.referencePrevalenceTo}",
                        style = MaterialTheme.concordance.monoId,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "${d.flipCount} verdict flip(s) across ${d.totalAnchors} anchor(s) " +
                            "(${"%.0f".format(d.flipRate * 100)}%)",
                        style = MaterialTheme.concordance.monoId,
                        fontWeight = FontWeight.Bold,
                        color = if (d.flipCount == 0) NeonEmerald else VividCoral
                    )
                    d.flips.take(20).forEach { f ->
                        Text(
                            "• ${f.anchorLabel}: ${f.fromVerdict.label} -> ${f.toVerdict.label}",
                            style = MaterialTheme.concordance.monoId,
                            color = VividCoral,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (d.flips.isEmpty()) {
                        Text(stringResource(R.string.auto_1f151043399f),
                            style = MaterialTheme.concordance.monoId,
                            color = TextMuted
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { viewModel.clearRevisionDiff() }) { Text(stringResource(R.string.auto_62d8711138c9), style = MaterialTheme.concordance.bodyDense) }
                }
            }
        }

        item {
            AppSectionCard(title = "APPROVER (required to promote)") {
                OutlinedTextField(
                    value = approver,
                    onValueChange = { approver = it },
                    label = { Text(stringResource(R.string.auto_a608b328a9ba), style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense,
                    singleLine = true
                )
            }
        }

        if (candidates.isNotEmpty()) {
            item {
                Text(stringResource(R.string.auto_2f5ac962f9ff),
                    style = MaterialTheme.concordance.overline,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(candidates) { c ->
                AppSectionCard(title = "sn=${c.parameters.sn}  ref=${c.parameters.referencePrevalence}") {
                    Text(
                        "E=${"%.4f".format(c.result.total)}  " +
                            "stability=${"%.3f".format(c.result.verdictStabilityPenalty)}  " +
                            "resolutionMiss=${"%.3f".format(c.result.challengeResolutionMissRate)}",
                        style = MaterialTheme.concordance.monoId,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.previewCandidateDiff(c) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.auto_d57393194772), style = MaterialTheme.concordance.buttonSmall)
                        }
                        Button(
                            onClick = { pendingCandidate = c },
                            enabled = approver.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = MidnightDeep),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (approver.isBlank()) "Approver?" else "Propose",
                                style = MaterialTheme.concordance.bodyDense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (revisions.isNotEmpty()) {
            item {
                Text(stringResource(R.string.auto_db4653da5ad3),
                    style = MaterialTheme.concordance.bodyDense,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(revisions) { rev ->
                AppSectionCard(title = rev.id) {
                    Text(
                        "${if (rev.promotedBy != null) "PROMOTED" else "PROPOSAL"} by ${rev.promotedBy ?: "-"}  •  ${rev.createdAtUtc}",
                        style = MaterialTheme.concordance.monoId,
                        fontWeight = FontWeight.Bold,
                        color = if (rev.promotedBy != null) NeonEmerald else TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "sn=${rev.parameters.sn}  ref=${rev.parameters.referencePrevalence}  E=${"%.4f".format(rev.metricE)}",
                        style = MaterialTheme.concordance.monoId,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    rev.notes?.let {
                        Text(it, style = MaterialTheme.concordance.monoId, color = TextMuted)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.previewRevisionDiff(rev.id) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.auto_566ea8ade947), style = MaterialTheme.concordance.buttonSmall)
                        }
                        OutlinedButton(
                            onClick = { pendingRevert = rev },
                            enabled = approver.isNotBlank() && rev.promotedBy != null && rev.id != current?.id,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.auto_272607a7bd01), style = MaterialTheme.concordance.buttonSmall)
                        }
                    }
                }
            }
        }

        if (audit.isNotEmpty()) {
            item {
                Text(stringResource(R.string.auto_468f285b8094),
                    style = MaterialTheme.concordance.overline,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(audit) { a ->
                val tone = when (a.decision.name) {
                    "PROMOTED" -> NeonEmerald
                    "REJECTED" -> VividCoral
                    "REVERTED" -> WarningAmber
                    else -> TextMuted
                }
                AppSectionCard(title = "${a.decision.name}  •  ${a.createdAtUtc}") {
                    Text(
                        "candidate sn=${a.candidate.sn} ref=${a.candidate.referencePrevalence}  " +
                            "E=${"%.4f".format(a.result.total)}",
                        style = MaterialTheme.concordance.monoId,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "approver: ${a.approver ?: "-"}   revision: ${a.revisionId ?: "-"}",
                        style = MaterialTheme.concordance.monoId,
                        color = tone,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(a.notes, style = MaterialTheme.concordance.monoId, color = TextMuted)
                }
            }
        }
        if (challenges.isNotEmpty()) {
            item {
                Text(
                    "CHALLENGE CORPUS (training signal, ${challenges.size})",
                    style = MaterialTheme.concordance.monoId,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(challenges) { ch ->
                val implied = viewModel.challengeImpliedVerdict(ch)
                AppSectionCard(title = ch.claimId) {
                    Text(
                        "by ${ch.author}  •  ${ch.date}",
                        style = MaterialTheme.concordance.monoId,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "corrected: brain ${ch.updatedBrainEvents}/${ch.updatedBrainTotal}  " +
                            "other ${ch.updatedOtherEvents}/${ch.updatedOtherTotal}",
                        style = MaterialTheme.concordance.monoId,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "implied verdict: ${implied?.label ?: "unavailable"}",
                        style = MaterialTheme.concordance.monoId,
                        color = ClinicalTeal,
                        fontFamily = FontFamily.Monospace
                    )
                    if (ch.notes.isNotBlank()) {
                        Text(ch.notes, style = MaterialTheme.concordance.monoId, color = TextMuted)
                    }
                }
            }
        } else {
            item {
                AppSectionCard(title = "CHALLENGE CORPUS (training signal)") {
                    Text(
                        "No challenges logged yet. The loop's challenge-resolution and " +
                            "calibration terms carry no signal until you log challenges " +
                            "(Registry screen -> Challenge on a claim). Without them, any " +
                            "promotion is correctly refused.",
                        style = MaterialTheme.concordance.overline,
                        color = TextSecondary
                    )
                }
            }
        }

        item {
            AppSectionCard(title = "TELEMETRY & OPTIMIZATION SUGGESTIONS") {
                Text(
                    viewModel.telemetrySummary(),
                    style = MaterialTheme.concordance.monoId,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
                if (telemetrySuggestions.isEmpty()) {
                    Text(
                        "No optimization suggestions yet. Activity across the system (API calls, cache, " +
                            "circuit breaker, learning rounds, syncs) feeds the analyzer.",
                        style = MaterialTheme.concordance.monoId,
                        color = TextSecondary
                    )
                } else {
                    telemetrySuggestions.forEach { s ->
                        val tone = when (s.severity) {
                            com.example.concordance.telemetry.OptimizationSuggestion.Severity.CRITICAL -> VividCoral
                            com.example.concordance.telemetry.OptimizationSuggestion.Severity.WARNING -> WarningAmber
                            com.example.concordance.telemetry.OptimizationSuggestion.Severity.INFO -> ClinicalTeal
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SlateCardElevated)
                                .border(1.dp, SlateCardBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "${s.severity.label}: ${s.title}",
                                style = MaterialTheme.concordance.bodyDense,
                                fontWeight = FontWeight.Bold,
                                color = tone
                            )
                            Text(s.description, style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
                            Text(
                                "Action: ${s.suggestedAction}",
                                style = MaterialTheme.concordance.bodyDense,
                                color = TextMuted
                            )
                            Text(
                                "Impact: ${s.estimatedImpact}",
                                style = MaterialTheme.concordance.bodyDense,
                                color = TextMuted
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.analyzeTelemetry() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.auto_cc56669344ce), style = MaterialTheme.concordance.buttonSmall)
                    }
                    OutlinedButton(
                        onClick = { viewModel.flushTelemetryToCloud() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.auto_f958849905e5), style = MaterialTheme.concordance.buttonSmall)
                    }
                }
            }
        }

        item {
            AppSectionCard(title = "LEARNING HEALTH (Phase 3 dashboard)") {
                Text(
                    "Revisions: ${health.revisionCount}  •  promoted: ${health.promotedCount}",
                    style = MaterialTheme.concordance.monoId,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "Challenges: ${health.resolvedChallenges}/${health.totalChallenges} resolved " +
                        "(${"%.0f".format(health.challengeResolutionRate * 100)}%)",
                    style = MaterialTheme.concordance.monoId,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
                if (health.metricTrend.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.auto_a17169bb44e4), style = MaterialTheme.concordance.monoId, color = TextMuted)
                    health.metricTrend.forEach { (id, e) ->
                        Text(
                            "  $id  E=${"%.4f".format(e)}",
                            style = MaterialTheme.concordance.monoId,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.auto_7c05c5c19b2f), style = MaterialTheme.concordance.monoId, color = TextMuted)
                Text(
                    "Challenges since last round: ${(challenges.size - lastAutoCount).coerceAtLeast(0)} / $autoRoundThreshold",
                    style = MaterialTheme.concordance.monoId,
                    color = if (challenges.size - lastAutoCount >= autoRoundThreshold) NeonEmerald else TextMuted,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = autoRoundThreshold.toString(),
                        onValueChange = { it.toIntOrNull()?.let { v -> viewModel.setAutoRoundThreshold(v) } },
                        label = { Text(stringResource(R.string.auto_1e7b30aade72), style = MaterialTheme.concordance.bodyDense) },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.concordance.bodyDense,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = (stabilityThreshold * 100).toInt().toString(),
                        onValueChange = { it.toIntOrNull()?.let { v -> viewModel.setStabilityThreshold(v / 100.0) } },
                        label = { Text(stringResource(R.string.auto_6485d869e56d), style = MaterialTheme.concordance.bodyDense) },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.concordance.bodyDense,
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { viewModel.checkAutoTrigger() },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.auto_acc5b0d8221c), style = MaterialTheme.concordance.buttonSmall)
                }
            }
        }

        item {
            AppSectionCard(title = "REVISION ARCHIVE (reproducibility)") {
                Text(
                    "Export all revisions as versioned JSON, or import an archive " +
                        "(strict schema + bounds validation).",
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { viewModel.exportRevisions() },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.auto_74d9491f35ba), style = MaterialTheme.concordance.monoId)
                }
                archiveExport?.let { json ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        json,
                        style = MaterialTheme.concordance.monoId,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                    TextButton(onClick = { viewModel.clearRevisionArchiveExport() }) {
                        Text(stringResource(R.string.auto_d259d34a5a1e), style = MaterialTheme.concordance.monoId)
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text(stringResource(R.string.auto_1449540e8ae4), style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.importRevisions(importText) },
                    enabled = importText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = MidnightDeep),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.auto_b4a827f10791), style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Human approval gates for promotion and rollback.
    pendingCandidate?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingCandidate = null },
            title = { Text(stringResource(R.string.auto_d041598f53ad)) },
            text = {
                Text(
                    "This will publish sn=${c.parameters.sn}, ref=${c.parameters.referencePrevalence} " +
                        "as the current revision (E=${"%.4f".format(c.result.total)}). " +
                        "Existing capsules are NOT re-adjudicated; they stay pinned to their " +
                        "original revision.",
                    style = MaterialTheme.concordance.bodyDense
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.promoteCandidate(c, approver)
                    pendingCandidate = null
                }) { Text(stringResource(R.string.auto_61c57736e70d)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCandidate = null }) { Text(stringResource(R.string.auto_77dfd2135f4d)) }
            }
        )
    }

    pendingRevert?.let { rev ->
        AlertDialog(
            onDismissRequest = { pendingRevert = null },
            title = { Text("Revert to ${rev.id}?") },
            text = {
                Text(
                    "Rollback is append-only: a NEW revision carrying ${rev.id}'s parameters " +
                        "will be published and become current. History is never rewritten.",
                    style = MaterialTheme.concordance.bodyDense
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.revertToRevision(rev.id, approver)
                    pendingRevert = null
                }) { Text(stringResource(R.string.auto_272607a7bd01)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevert = null }) { Text(stringResource(R.string.auto_77dfd2135f4d)) }
            }
        )
    }
}

/** Small shared card used across the learning tab. */

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
    val isRunning by viewModel.isRunningLearningRound.collectAsState()
    val report by viewModel.learningReport.collectAsState()
    val candidates by viewModel.learningCandidates.collectAsState()
    val summary by viewModel.trainingSummary.collectAsState()
    val current by viewModel.currentModelRevision.collectAsState()
    val revisions by viewModel.modelRevisions.collectAsState()
    val diff by viewModel.revisionDiff.collectAsState()
    val audit by viewModel.learningAudit.collectAsState()
    val health by viewModel.learningHealth.collectAsState()
    val autoRoundThreshold by viewModel.autoRoundThreshold.collectAsState()
    val stabilityThreshold by viewModel.stabilityThreshold.collectAsState()
    val challenges by viewModel.allChallenges.collectAsState()
    val lastAutoCount by viewModel.lastAutoRoundChallengeCount.collectAsState()
    val telemetrySuggestions by viewModel.telemetrySuggestions.collectAsState()

    var approver by remember { mutableStateOf("") }
    var pendingCandidate by remember { mutableStateOf<ParameterSearch.CandidateScore?>(null) }
    var pendingRevert by remember { mutableStateOf<ModelRevision?>(null) }
    var importText by remember { mutableStateOf("") }
    val archiveExport by viewModel.revisionArchiveExport.collectAsState()

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
                         Text("Re-adjudicate all claims under this revision", style = MaterialTheme.concordance.overline)
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
                        Text(
                            "No verdict changes — this revision would not alter any existing adjudication.",
                            style = MaterialTheme.concordance.monoId,
                            color = TextMuted
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { viewModel.clearRevisionDiff() }) { Text("Close diff", style = MaterialTheme.concordance.bodyDense) }
                }
            }
        }

        item {
            AppSectionCard(title = "APPROVER (required to promote)") {
                OutlinedTextField(
                    value = approver,
                    onValueChange = { approver = it },
                    label = { Text("Your name / handle", style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense,
                    singleLine = true
                )
            }
        }

        if (candidates.isNotEmpty()) {
            item {
                Text(
                    "TOP CANDIDATES (promotion is manual)",
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
                            Text("Preview diff", style = MaterialTheme.concordance.buttonSmall)
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
                Text(
                    "REVISION HISTORY (append-only)",
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
                            Text("Diff vs current", style = MaterialTheme.concordance.buttonSmall)
                        }
                        OutlinedButton(
                            onClick = { pendingRevert = rev },
                            enabled = approver.isNotBlank() && rev.promotedBy != null && rev.id != current?.id,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Revert", style = MaterialTheme.concordance.buttonSmall)
                        }
                    }
                }
            }
        }

        if (audit.isNotEmpty()) {
            item {
                Text(
                    "DECISION AUDIT (append-only, promoted & rejected)",
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
                        Text("Analyze now", style = MaterialTheme.concordance.buttonSmall)
                    }
                    OutlinedButton(
                        onClick = { viewModel.flushTelemetryToCloud() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Flush to CloudWatch", style = MaterialTheme.concordance.buttonSmall)
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
                    Text("Metric trend (promoted revisions):", style = MaterialTheme.concordance.monoId, color = TextMuted)
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
                Text("Continual mode settings:", style = MaterialTheme.concordance.monoId, color = TextMuted)
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
                        label = { Text("Auto-round every N challenges", style = MaterialTheme.concordance.bodyDense) },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.concordance.bodyDense,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = (stabilityThreshold * 100).toInt().toString(),
                        onValueChange = { it.toIntOrNull()?.let { v -> viewModel.setStabilityThreshold(v / 100.0) } },
                        label = { Text("Stability guard %", style = MaterialTheme.concordance.bodyDense) },
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
                    Text("Run auto-trigger check now", style = MaterialTheme.concordance.buttonSmall)
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
                    Text("Export revisions (JSON)", style = MaterialTheme.concordance.monoId)
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
                        Text("Dismiss export", style = MaterialTheme.concordance.monoId)
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("Paste revision JSON to import", style = MaterialTheme.concordance.bodyDense) },
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
                    Text("Import revisions", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Human approval gates for promotion and rollback.
    pendingCandidate?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingCandidate = null },
            title = { Text("Promote model revision?") },
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
                }) { Text("Promote") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCandidate = null }) { Text("Cancel") }
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
                }) { Text("Revert") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevert = null }) { Text("Cancel") }
            }
        )
    }
}

/** Small shared card used across the learning tab. */
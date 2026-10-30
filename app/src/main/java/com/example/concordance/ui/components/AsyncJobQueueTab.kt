package com.example.concordance.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.concordance.integration.queue.*
import com.example.concordance.ui.ConcordanceViewModel
import com.example.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
fun AsyncJobQueueTab(
    viewModel: ConcordanceViewModel,
    onCopyText: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val jobs by viewModel.activeQueueJobs.collectAsStateWithLifecycle()
    val workerNodes by viewModel.workerNodes.collectAsStateWithLifecycle()
    val stuckLogs by viewModel.stuckRecoveryLogs.collectAsStateWithLifecycle()

    var selectedDataClass by remember { mutableStateOf(JobDataClass.IMAGING_VLM_FIGURE_EXTRACTION) }
    var selectedRouting by remember { mutableStateOf(JobDataClass.IMAGING_VLM_FIGURE_EXTRACTION.defaultWorker) }
    var customParams by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Architecture & OS Scheduler Summary Banner
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Text(text = stringResource(R.string.auto_38685d25adc9),
                            style = MaterialTheme.concordance.overline,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            letterSpacing = 1.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ClinicalTeal.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = stringResource(R.string.auto_6d7985237371),
                            style = MaterialTheme.concordance.monoId,
                            fontWeight = FontWeight.Bold,
                            color = ClinicalTeal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Text(text = stringResource(R.string.auto_8ffb2b456fb5),
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                HorizontalDivider(color = SlateCardBorder, modifier = Modifier.padding(vertical = 2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.auto_d756c1ca7157), style = MaterialTheme.concordance.bodyDense, color = TextMuted)
                    Text(stringResource(R.string.auto_007c918a47a0), style = MaterialTheme.concordance.bodyDense, color = NeonEmerald)
                }
            }
        }

        // Job Submission Control Card
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
                Text(text = stringResource(R.string.auto_c17d7dc3db56),
                    style = MaterialTheme.concordance.overline,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )

                Text(text = stringResource(R.string.auto_0d587aa02d21),
                    style = MaterialTheme.concordance.overline,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                JobDataClass.entries.forEach { dc ->
                    val isSelected = selectedDataClass == dc
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) ElectricCyan.copy(alpha = 0.12f) else SlateCardElevated)
                            .border(1.dp, if (isSelected) ElectricCyan else SlateCardBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                selectedDataClass = dc
                                selectedRouting = dc.defaultWorker
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedDataClass = dc
                                selectedRouting = dc.defaultWorker
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = ElectricCyan)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                text = dc.label,
                                style = MaterialTheme.concordance.bodyDense,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) ElectricCyan else TextPrimary
                            )
                            Text(
                                text = "Model: ${dc.defaultModel} • Route: ${dc.defaultWorker.title}",
                                style = MaterialTheme.concordance.monoId,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Target Routing Worker selector
                Text(text = stringResource(R.string.auto_926dba83488c),
                    style = MaterialTheme.concordance.monoId,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WorkerRoutingPolicy.entries.take(3).forEach { policy ->
                        val isSelected = selectedRouting == policy
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedRouting = policy },
                            label = { Text(policy.title.take(16), style = MaterialTheme.concordance.bodyDense) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.submitAsyncComputeJob(
                            dataClass = selectedDataClass,
                            routing = selectedRouting,
                            inputs = "Target: ESR1 Y537S Organotropism Adjudication",
                            modelRev = selectedDataClass.defaultModel,
                            params = mapOf("iterations" to "10000", "confidence" to "0.95")
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.auto_4a885731a5e9), style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Automatic Stuck-Task Recovery Panel
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateCard)
                    .border(1.dp, WarningAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        Icon(Icons.Default.Healing, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                        Text(text = stringResource(R.string.auto_5ee94b84ab3a),
                            style = MaterialTheme.concordance.overline,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber
                        )
                    }
                    Text(text = stringResource(R.string.auto_069ee23c28c1),
                        style = MaterialTheme.concordance.monoId,
                        fontWeight = FontWeight.Bold,
                        color = WarningAmber,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(text = stringResource(R.string.auto_f879c08c57f1),
                    style = MaterialTheme.concordance.monoId,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.triggerStuckTaskRecovery() },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = MidnightDeep),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.auto_cb24b43adde8), style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.simulateStuckTask() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.auto_69b9695e875b), style = MaterialTheme.concordance.buttonSmall)
                    }
                }

                if (stuckLogs.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MidnightDeep)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(stringResource(R.string.auto_3584d2186267), style = MaterialTheme.concordance.monoId, fontWeight = FontWeight.Bold, color = TextMuted)
                        stuckLogs.take(2).forEach { log ->
                            Text(log, style = MaterialTheme.concordance.monoId, color = WarningAmber, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Active Worker Nodes
        item {
            Text(
                text = "ACTIVE WORKER NODES CLUSTER (${workerNodes.size})",
                style = MaterialTheme.concordance.monoId,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp
            )
        }

        items(workerNodes) { worker ->
            WorkerNodeCard(worker = worker)
        }

        // Tasks in Queue Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ASYNC TASKS IN QUEUE & HISTORY (${jobs.size})",
                    style = MaterialTheme.concordance.overline,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Text(text = stringResource(R.string.auto_8c5be9977e27),
                    style = MaterialTheme.concordance.overline,
                    color = ClinicalTeal
                )
            }
        }

        // Task Items
        items(jobs) { job ->
            AsyncJobCard(
                job = job,
                onCopy = onCopyText
            )
        }
    }
}

@Composable
fun WorkerNodeCard(worker: WorkerNode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SlateCard)
            .border(1.dp, SlateCardBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (worker.isHealthy) NeonEmerald else WarningAmber)
                )
                Text(
                    text = worker.workerId,
                    style = MaterialTheme.concordance.monoId,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "${worker.routingPolicy.title} • ${worker.region}",
                style = MaterialTheme.concordance.monoId,
                color = TextSecondary
            )
            Text(
                text = worker.routingPolicy.hardwareTier,
                style = MaterialTheme.concordance.monoId,
                color = TextMuted
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SlateCardElevated)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${worker.activeTasksCount} active",
                    style = MaterialTheme.concordance.bodyDense,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Max: ${worker.routingPolicy.maxDurationMinutes}m",
                style = MaterialTheme.concordance.bodyDense,
                color = TextMuted
            )
        }
    }
}

@Composable
fun AsyncJobCard(
    job: JobStatusResponse,
    onCopy: (String, String) -> Unit
) {
    var expandedLogs by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SlateCard)
            .border(
                1.dp,
                if (job.stuckRecoveryTriggered) WarningAmber else SlateCardBorder,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(job.status.badgeColorHex).copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = job.status.label,
                    style = MaterialTheme.concordance.bodyDense,
                    fontWeight = FontWeight.Bold,
                    color = Color(job.status.badgeColorHex)
                )
            }

            if (job.status == JobExecutionStatus.PROCESSING) {
                Text(
                    text = "ETA: ~${job.estimatedWaitSeconds}s",
                    style = MaterialTheme.concordance.monoId,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    fontFamily = FontFamily.Monospace
                )
            } else if (job.status == JobExecutionStatus.PENDING) {
                Text(
                    text = "Queue Pos: #${job.queuePosition}",
                    style = MaterialTheme.concordance.monoId,
                    color = WarningAmber
                )
            }
        }

        Text(
            text = job.dataClass.label,
            style = MaterialTheme.concordance.monoValue,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Task UUID: ${job.taskUuid.take(18)}...",
                style = MaterialTheme.concordance.monoId,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
            Row(
                modifier = Modifier.clickable { onCopy("Job ID", job.jobId) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.jobId,
                    style = MaterialTheme.concordance.monoId,
                    color = ElectricCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Job ID", tint = ElectricCyan, modifier = Modifier.size(12.dp))
            }
        }

        if (job.status == JobExecutionStatus.PROCESSING) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { job.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = ElectricCyan,
                    trackColor = SlateCardElevated
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Worker: ${job.workerNode?.workerId ?: "Assigning..."}", style = MaterialTheme.concordance.bodyDense, color = TextMuted)
                    Text("${job.progressPercent}%", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold, color = ElectricCyan)
                }
            }
        }

        if (job.resultSummary != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MidnightDeep)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(stringResource(R.string.auto_eb9f9e1638e9), style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = NeonEmerald)
                Text(job.resultSummary, style = MaterialTheme.concordance.overline, color = TextPrimary, lineHeight = 15.sp)

                if (job.citableCapsuleSha256 != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCopy("Capsule SHA", job.citableCapsuleSha256) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SHA: ${job.citableCapsuleSha256.take(24)}...",
                            style = MaterialTheme.concordance.monoId,
                            color = ClinicalTeal,
                            fontFamily = FontFamily.Monospace
                        )
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy SHA", tint = ClinicalTeal, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }

        // Expandable audit trail logs
        if (job.logs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedLogs = !expandedLogs },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expandedLogs) "Hide Execution Logs" else "View ${job.logs.size} Execution Logs",
                    style = MaterialTheme.concordance.bodyDense,
                    color = ClinicalTeal,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expandedLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = ClinicalTeal,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = expandedLogs) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MidnightDeep)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    job.logs.forEach { log ->
                        Text(
                            text = "> $log",
                            style = MaterialTheme.concordance.monoId,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}

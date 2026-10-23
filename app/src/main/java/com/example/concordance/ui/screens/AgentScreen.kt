package com.example.concordance.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.concordance.agent.EasyMode
import com.example.concordance.integration.agent.AgentConfig
import com.example.concordance.autonomy.AutonomyActivity
import com.example.concordance.autonomy.AutonomyModeManager
import com.example.concordance.autonomy.AutonomyScheduler
import com.example.concordance.autonomy.LocalCorpus
import com.example.concordance.autonomy.HighPowerResearchService
import com.example.concordance.integration.agent.AgentFleetRoster
import com.example.concordance.integration.agent.AgentFleetService
import com.example.concordance.integration.agent.AgentLoopResult
import com.example.concordance.integration.agent.AgentPendingDecision
import com.example.concordance.integration.agent.AgentRefereeReport
import com.example.concordance.integration.agent.AgentResearchResult
import com.example.concordance.integration.agent.AgentUpload
import com.example.concordance.integration.agent.AgentUploads
import com.example.concordance.integration.agent.EasyModeView
import com.example.concordance.ui.ConcordanceViewModel
import com.example.concordance.ui.components.AppSectionCard
import com.example.concordance.ui.components.EmptyState
import com.example.concordance.ui.components.LiveWorkCard
import com.example.concordance.ui.components.LocalCorpusCard
import com.example.ui.theme.*
import java.io.File

/**
 * Client for the self-hosted adjudication agent.
 *
 * Three invariants the UI is responsible for:
 *  1. Audio never leaves the phone - dictation is on-device only, and says so.
 *  2. A figure must be acknowledged before upload, and OCR text is shown as unverified.
 *  3. Easy mode is the agent's own translation, rendered as-is; expert mode shows the raw
 *     payload so the two can be compared rather than trusted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(viewModel: ConcordanceViewModel, modifier: Modifier = Modifier) {
    val config by viewModel.agentConfig.collectAsState()
    val status by viewModel.agentStatus.collectAsState()
    val busy by viewModel.agentBusy.collectAsState()
    val anchors by viewModel.agentAnchors.collectAsState()
    val job by viewModel.agentJob.collectAsState()
    val easyView by viewModel.agentEasyView.collectAsState()
    val correction by viewModel.agentCorrection.collectAsState()
    val correctionEasy by viewModel.agentCorrectionEasy.collectAsState()
    val refereeReport by viewModel.agentRefereeReport.collectAsState()
    val refereeExperiments by viewModel.agentRefereeExperiments.collectAsState()
    val upload by viewModel.agentUpload.collectAsState()
    val transcript by viewModel.agentTranscript.collectAsState()
    val researchResult by viewModel.agentResearchResult.collectAsState()
    val pendingDecisions by viewModel.pendingDecisions.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadPendingDecisions() }
    val ack by viewModel.agentUploadAck.collectAsState()
    val easyMode by viewModel.easyMode.collectAsState()
    val communityBaseUrl by viewModel.communityBaseUrl.collectAsState()

    val context = LocalContext.current
    var url by remember { mutableStateOf(config.baseUrl) }
    var apiKey by remember { mutableStateOf(config.apiKey.orEmpty()) }
    var communityUrl by remember { mutableStateOf(communityBaseUrl) }
    var dataClass by remember { mutableStateOf("phi") }

    LaunchedEffect(config.baseUrl) { url = config.baseUrl }
    LaunchedEffect(communityBaseUrl) { communityUrl = communityBaseUrl }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ConnectionCard(
            url = url,
            apiKey = apiKey,
            status = status,
            busy = busy,
            config = config,
            easyMode = easyMode,
            onUrlChange = { url = it },
            onApiKeyChange = { apiKey = it },
            onSave = { viewModel.updateAgentConfig(url, apiKey) },
            onTest = { viewModel.testAgentConnection() },
            onEasyModeChange = { viewModel.setEasyMode(it) },
            speechEngine = viewModel.speechEngineName,
            kokoroReason = viewModel.kokoroUnavailableReason,
            communityUrl = communityUrl,
            onCommunityUrlChange = { communityUrl = it },
            onSaveCommunityUrl = { viewModel.setCommunityBaseUrl(communityUrl) }
        )

        val researchSeed by viewModel.researchSeed.collectAsState()

        DictationCard(
            transcript = transcript,
            onTranscriptChange = { viewModel.setAgentTranscript(it) },
            onToggleDictation = { viewModel.toggleDictation() },
            unavailableReason = viewModel.dictationUnavailableReason,
            onResearch = { useLlm -> viewModel.runAgentResearch(useLlm) },
            busy = busy
        )

        IngestCard(
            seed = researchSeed,
            busy = busy,
            onIngest = { uri -> viewModel.ingestFileAndSeed(uri, context) },
            onClear = { viewModel.clearResearchSeed() },
            onStartResearch = { viewModel.runAgentResearch(true) }
        )

        researchResult?.let { ResearchResultCard(it, onFeedback = { claimId, rating -> viewModel.submitFeedback(claimId, rating) }, onSpeak = { text -> viewModel.speakText(text) }) }

        val fleetRoster by viewModel.agentFleet.collectAsState()
        val loopResult by viewModel.agentLoop.collectAsState()
        EcosystemCard(
            roster = fleetRoster,
            loop = loopResult,
            busy = busy,
            onCheck = { viewModel.loadAgentFleet(true) },
            onRunLoop = { viewModel.runAgentResearchLoop(researchSeed.orEmpty()) },
        )

        if (pendingDecisions.isNotEmpty()) {
            PendingDecisionsCard(
                decisions = pendingDecisions,
                onResolve = { id, choice -> viewModel.resolvePendingDecision(id, choice) }
            )
        }

        val autonomyState by AutonomyModeManager.state.collectAsState()
        val activityEntries by AutonomyActivity.entries.collectAsState()
        LaunchedEffect(Unit) {
            AutonomyModeManager.init(context)
            AutonomyActivity.init(context)
            LocalCorpus.init(context)
        }
        LiveWorkCard(
            state = autonomyState,
            entries = activityEntries,
            onGo = { goal ->
                try {
                    AutonomyModeManager.setCurrentGoal(goal)
                    AutonomyModeManager.go()
                    HighPowerResearchService.start(context, goal)
                    AutonomyScheduler.scheduleSlow(context)
                } catch (e: Exception) {
                    android.util.Log.e("AgentScreen", "Go button failed", e)
                }
            },
            onPause = { AutonomyModeManager.pause() },
            onStop = {
                AutonomyModeManager.stop()
                AutonomyScheduler.cancelSlow(context)
            },
            onRunStep = { AutonomyScheduler.runOnce(context) },
            onCancelSlow = { AutonomyScheduler.cancelSlow(context) },
            onClearLog = { AutonomyActivity.clear() },
        )

        val corpusStatus by LocalCorpus.status.collectAsState()
        val folderPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri -> if (uri != null) viewModel.setCorpusFolder(uri) }
        LocalCorpusCard(
            status = corpusStatus,
            onChooseFolder = { folderPicker.launch(null) },
            onIndexNow = { viewModel.indexCorpusNow() },
            onForget = { viewModel.forgetCorpusFolder() },
        )

        var showQuickReport by remember { mutableStateOf(false) }
        Button(
            onClick = { showQuickReport = true; viewModel.loadPendingDecisions() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SlateCard, contentColor = TextPrimary)
        ) {
            Text("Quick report (state of affairs)", style = MaterialTheme.concordance.buttonSmall)
        }
        if (showQuickReport) {
            AlertDialog(
                onDismissRequest = { showQuickReport = false },
                title = { Text("Quick report", color = TextPrimary) },
                text = {
                    Column {
                        Text("Mode: SLOW (background)", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
                        Text("Pending decisions: ${pendingDecisions.size}", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
                        researchResult?.let { r ->
                            Text("Last run: ${r.runId}", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
                            Text("Claims: ${r.claimUpdates.size}  Critic: ${r.criticVerdict}", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
                            Text("Stop: ${r.stopShouldStop}  Dead ends: ${r.deadEnds.size}", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
                            Text("Paper: ${r.paperArtifacts} files", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
                        } ?: Text("No research run yet.", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQuickReport = false }) { Text("Close", color = ElectricCyan) }
                }
            )
        }

        AdjudicateCard(
            anchors = anchors,
            busy = busy,
            onAdjudicate = { viewModel.runAgentAdjudication(it) }
        )

        CorrectionCard(
            anchors = anchors,
            busy = busy,
            onCorrect = { anchorId, site, sn -> viewModel.runAgentCorrection(anchorId, site, sn) }
        )

        RefereeAuditCard(
            busy = busy,
            onAudit = { claimId, auc, csv ->
                viewModel.runAgentRefereeAudit(claimId, auc, csv)
            }
        )

        UploadCard(
            context = context,
            dataClass = dataClass,
            acknowledged = ack,
            busy = busy,
            onDataClassChange = { dataClass = it },
            onAcknowledge = { viewModel.acknowledgeUploads() },
            onPicked = { viewModel.uploadFigure(it, dataClass) },
            onRejected = { viewModel.notify(it) }
        )

        upload?.let { UploadedFigureCard(it) }

        job?.let { AgentJobCard(it) }

        correction?.let { CorrectionResultCard(it) }

        refereeReport?.let { RefereeAuditResultCard(it) }

        if (refereeExperiments.isNotEmpty()) {
            RefereeExperimentsCard(refereeExperiments)
        }
        val hasResult = job != null || upload != null || correction != null
        if (!hasResult && !busy) {
            EmptyState(
                title = "No agent result yet",
                message = "Test the connection, choose an anchor, then run an adjudication. " +
                    "Results and honest refusals appear here.",
            )
        }

        val plainView = easyView ?: correctionEasy
        val rawPayload = job?.rawJson ?: correction?.rawJson.orEmpty()
        if (plainView != null) {
            SpeechCard(
                view = plainView,
                engineName = viewModel.speechEngineName,
                onSpeak = { viewModel.speakAgentResult(plainView) },
                onStop = { viewModel.stopSpeaking() }
            )
            if (easyMode) EasyModeCard(plainView) else ExpertCard(plainView, rawPayload)
        }

        if (job != null || upload != null || correction != null) {
            TextButton(onClick = { viewModel.clearAgentResult() }) {
                Text("Clear result", color = TextSecondary, style = MaterialTheme.concordance.buttonSmall)
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    url: String,
    apiKey: String,
    status: String,
    busy: Boolean,
    config: AgentConfig,
    easyMode: Boolean,
    onUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onEasyModeChange: (Boolean) -> Unit,
    speechEngine: String,
    kokoroReason: String?,
    communityUrl: String,
    onCommunityUrlChange: (String) -> Unit,
    onSaveCommunityUrl: () -> Unit
) {
    AppSectionCard(title = "Agent connection") {
        Text(
            "Point this at the machine running your agent. Nothing here talks to a managed " +
                "platform: the agent is self-hosted, and figures are read on that machine.",
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary,
            lineHeight = 16.sp
        )
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("Agent base URL", style = MaterialTheme.concordance.bodyDense) },
            placeholder = { Text("http://10.0.2.2:8000", style = MaterialTheme.concordance.bodyDense) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.concordance.monoValue,
            colors = fieldColors()
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API key (leave blank if auth is off)", style = MaterialTheme.concordance.monoId) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.concordance.monoValue,
            colors = fieldColors(),
            visualTransformation = PasswordVisualTransformation()
        )
        OutlinedTextField(
            value = communityUrl,
            onValueChange = onCommunityUrlChange,
            label = { Text("Community website URL (publish destination)", style = MaterialTheme.concordance.monoId) },
            placeholder = { Text("http://10.0.2.2:3001", style = MaterialTheme.concordance.monoId) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.concordance.monoValue,
            colors = fieldColors()
        )
        Button(
            onClick = onSaveCommunityUrl,
            colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated, contentColor = ElectricCyan)
        ) { Text("Save community URL", style = MaterialTheme.concordance.monoId) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated, contentColor = ElectricCyan)
            ) { Text("Save", style = MaterialTheme.concordance.buttonSmall) }
            Button(
                onClick = onTest,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MidnightDeep
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text("Test connection", style = MaterialTheme.concordance.monoId)
            }
            if (!config.isConfigured) {
                Text("Not configured", style = MaterialTheme.concordance.monoId, color = WarningAmber, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        Text(status, style = MaterialTheme.concordance.monoId, color = ClinicalTeal, fontFamily = FontFamily.Monospace)

        HorizontalDivider(color = SlateCardBorder)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = easyMode,
                onCheckedChange = onEasyModeChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MidnightDeep,
                    checkedTrackColor = ElectricCyan
                )
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Plain language first", style = MaterialTheme.concordance.bodyDense, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    "The agent writes the plain version; this app only lays it out. " +
                        "The expert payload stays one tap away.",
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextSecondary,
                    lineHeight = 14.sp
                )
            }
        }

        Text("Voice: $speechEngine", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
        if (kokoroReason != null) {
            Text("Kokoro not in use: $kokoroReason", style = MaterialTheme.concordance.bodyDense, color = TextSecondary, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun DictationCard(
    transcript: String,
    onTranscriptChange: (String) -> Unit,
    onToggleDictation: () -> Unit,
    unavailableReason: String?,
    onResearch: (Boolean) -> Unit,
    busy: Boolean
) {
    var useLlm by remember { mutableStateOf(true) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onToggleDictation()
    }

    AppSectionCard(title = "Dictate the request (on this phone)") {
        Text(
            "Speech is transcribed on the device. Audio is never uploaded - only the text you " +
                "see below is used.",
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary,
            lineHeight = 16.sp
        )
        if (unavailableReason != null) {
            Text(unavailableReason, style = MaterialTheme.concordance.bodyDense, color = WarningAmber, lineHeight = 16.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (unavailableReason != null) return@Button
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                enabled = unavailableReason == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SlateCardElevated,
                    contentColor = if (unavailableReason == null) ElectricCyan else TextSecondary
                )
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start / stop", style = MaterialTheme.concordance.buttonSmall)
            }
            Text("e.g. \"EGFR L858R in brain\"", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
        }
        Button(
            onClick = { onResearch(useLlm) },
            enabled = !busy && transcript.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MidnightDeep)
                Spacer(Modifier.width(6.dp))
            }
            Text("Research & design experiments", style = MaterialTheme.concordance.buttonSmall)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = useLlm,
                onCheckedChange = { useLlm = it },
                enabled = !busy,
                colors = CheckboxDefaults.colors(
                    checkedColor = ElectricCyan,
                    uncheckedColor = TextSecondary
                )
            )
            Column(Modifier.weight(1f)) {
                Text(
                    if (useLlm) "Use cloud model" else "Offline scaffold (no model quota)",
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextPrimary
                )
                Text(
                    "Uncheck when the model is rate-limited. The gates, critic, and local paper still run.",
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextSecondary,
                    lineHeight = 13.sp
                )
            }
        }
        OutlinedTextField(
            value = transcript,
            onValueChange = onTranscriptChange,
            label = { Text("What you said (editable)", style = MaterialTheme.concordance.bodyDense) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.concordance.bodyDense,
            colors = fieldColors()
        )
        Text(
            "Review the text before using it. Speech recognition makes mistakes, and an anchor id " +
                "that is wrong will produce an adjudication of the wrong claim.",
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary,
            lineHeight = 14.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjudicateCard(
    anchors: List<com.example.concordance.integration.agent.AgentAnchor>,
    busy: Boolean,
    onAdjudicate: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("") }

    // Default to a real anchor as soon as the agent offers one.
    LaunchedEffect(anchors) {
        if (selected.isBlank()) selected = anchors.firstOrNull()?.id.orEmpty()
    }

    AppSectionCard(title = "Adjudicate on the agent") {
        Text(
            "The agent applies its own rubric and refuses when the evidence cannot support an " +
                "answer. Test connection first to load the anchor list.",
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary,
            lineHeight = 16.sp
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selected,
                onValueChange = { selected = it },
                label = { Text("Anchor id", style = MaterialTheme.concordance.bodyDense) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                textStyle = MaterialTheme.concordance.monoValue,
                colors = fieldColors(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (anchors.isEmpty()) {
                    DropdownMenuItem(text = { Text("No anchors loaded", style = MaterialTheme.concordance.monoId) }, onClick = { expanded = false })
                }
                anchors.forEach { anchor ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(anchor.label, style = MaterialTheme.concordance.monoId, color = TextPrimary)
                                Text(
                                    "${anchor.id} * ${anchor.evidenceGrade}",
                                    style = MaterialTheme.concordance.monoId,
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        },
                        onClick = {
                            selected = anchor.id
                            expanded = false
                        }
                    )
                }
            }
        }
        Button(
            onClick = { onAdjudicate(selected) },
            enabled = !busy && selected.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep)
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MidnightDeep)
                Spacer(Modifier.width(6.dp))
            }
            Text("Run adjudication", style = MaterialTheme.concordance.buttonSmall)
        }
    }
}

@Composable
private fun CorrectionCard(
    anchors: List<com.example.concordance.integration.agent.AgentAnchor>,
    busy: Boolean,
    onCorrect: (String, String, String) -> Unit
) {
    var anchorId by remember { mutableStateOf("") }
    var site by remember { mutableStateOf("brain") }
    var sn by remember { mutableStateOf("0.90") }

    LaunchedEffect(anchors) {
        if (anchorId.isBlank()) anchorId = anchors.firstOrNull()?.id.orEmpty()
    }

    AppSectionCard(title = "Correct for detection") {
        Text(
            "A reported difference is a blend of the real effect and the assay's blind spot, " +
                "because the marker is less likely to be *found* in the site being compared. " +
                "This asks the agent what the difference becomes once that is accounted for, " +
                "and how wide the honest range is.",
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary,
            lineHeight = 16.sp
        )
        OutlinedTextField(
            value = anchorId,
            onValueChange = { anchorId = it },
            label = { Text("Anchor id", style = MaterialTheme.concordance.bodyDense) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.concordance.monoValue,
            colors = fieldColors()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = site,
                onValueChange = { site = it },
                label = { Text("Site", style = MaterialTheme.concordance.monoId) },
                placeholder = { Text("brain", style = MaterialTheme.concordance.monoId) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.concordance.monoValue,
                colors = fieldColors()
            )
            OutlinedTextField(
                value = sn,
                onValueChange = { sn = it },
                label = { Text("Assay sensitivity", style = MaterialTheme.concordance.monoId) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.concordance.monoValue,
                colors = fieldColors()
            )
        }
        Text(
            "Sensitivity is between 0 and 1: how often the assay detects the marker when it is " +
                "present. 0.90 means it misses about one in ten. Leave the site blank only if " +
                "you know retention is the same at both sites.",
            style = MaterialTheme.concordance.monoId,
            color = TextSecondary,
            lineHeight = 14.sp
        )
        Button(
            onClick = { onCorrect(anchorId, site, sn) },
            enabled = !busy && anchorId.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = MidnightDeep)
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MidnightDeep)
                Spacer(Modifier.width(6.dp))
            }
            Text("Correct", style = MaterialTheme.concordance.buttonSmall)
        }
    }
}

@Composable
private fun RefereeAuditCard(
    busy: Boolean,
    onAudit: (String, Double, String) -> Unit
) {
    var claimId by remember { mutableStateOf("") }
    var claimedAuc by remember { mutableStateOf("") }
    var csvText by remember { mutableStateOf("") }

    AppSectionCard(title = "Referee audit (deterministic)") {
        Text(
            "Audit a patient-level prediction table against a claimed AUC using the Referee engine. " +
                "PHI tables must stay local or on a BAA-covered backend.",
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary,
            lineHeight = 16.sp
        )
        OutlinedTextField(
            value = claimId,
            onValueChange = { claimId = it },
            label = { Text("Claim id", style = MaterialTheme.concordance.bodyDense) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.concordance.monoValue,
            colors = fieldColors()
        )
        OutlinedTextField(
            value = claimedAuc,
            onValueChange = { claimedAuc = it },
            label = { Text("Claimed AUC", style = MaterialTheme.concordance.bodyDense) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.concordance.monoValue,
            colors = fieldColors()
        )
        OutlinedTextField(
            value = csvText,
            onValueChange = { csvText = it },
            label = { Text("Prediction CSV (patient_id,label,split,score)", style = MaterialTheme.concordance.bodyDense) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            textStyle = MaterialTheme.concordance.monoValue,
            colors = fieldColors()
        )
        Button(
            onClick = {
                val auc = claimedAuc.toDoubleOrNull() ?: 0.0
                onAudit(claimId, auc, csvText)
            },
            enabled = !busy && csvText.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MidnightDeep)
                Spacer(Modifier.width(6.dp))
            }
            Text("Run referee audit", style = MaterialTheme.concordance.buttonSmall)
        }
    }
}

@Composable
private fun RefereeAuditResultCard(report: AgentRefereeReport) {
    AppSectionCard(title = "Referee result") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge(text = report.verdict, color = verdictColorForReferee(report.verdict))
        }
        if (report.reasons.isNotEmpty()) {
            report.reasons.forEach { r ->
                Text(r, style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 16.sp)
            }
        }
        if (report.dimensions.isNotEmpty()) {
            Text("Dimensions", style = MaterialTheme.concordance.bodyDense, color = ElectricCyan, fontWeight = FontWeight.Bold)
            report.dimensions.forEach { (k, v) ->
                Text("$k: $v", style = MaterialTheme.concordance.monoId, color = TextSecondary, fontFamily = FontFamily.Monospace)
            }
        }
        if (report.inputSha256.isNotBlank()) {
            Text("input_sha256 ${report.inputSha256.take(24)}...", style = MaterialTheme.concordance.monoId, color = TextSecondary, fontFamily = FontFamily.Monospace)
        }
        if (report.reportSha256.isNotBlank()) {
            Text("report_sha256 ${report.reportSha256.take(24)}...", style = MaterialTheme.concordance.monoId, color = TextSecondary, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun verdictColorForReferee(verdict: String): Color = when (verdict.uppercase()) {
    "REPRODUCED" -> NeonEmerald
    "NOT_REPRODUCED" -> VividCoral
    "LEAKAGE_DETECTED" -> VividCoral
    "FAILS_BASELINE" -> WarningAmber
    "UNVERIFIABLE" -> WarningAmber
    else -> TextSecondary
}

@Composable
private fun RefereeExperimentsCard(experiments: List<com.example.concordance.integration.agent.AgentExperiment>) {
    AppSectionCard(title = "Referee-driven experiments") {
        Text(
            "Follow-up experiments generated from the Referee audit verdict.",
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary,
            lineHeight = 16.sp
        )
        experiments.forEach { e ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(e.title, style = MaterialTheme.concordance.bodyDense, color = TextPrimary, fontWeight = FontWeight.Bold)
                if (e.design.isNotBlank()) Text("Design: ${e.design}", style = MaterialTheme.concordance.monoId, color = TextSecondary, lineHeight = 14.sp)
                if (e.controls.isNotBlank()) Text("Controls: ${e.controls}", style = MaterialTheme.concordance.monoId, color = TextSecondary, lineHeight = 14.sp)
                if (e.endpoints.isNotBlank()) Text("Endpoints: ${e.endpoints}", style = MaterialTheme.concordance.monoId, color = TextSecondary, lineHeight = 14.sp)
                if (e.routing.isNotBlank()) Text("Routing: ${e.routing}", style = MaterialTheme.concordance.monoId, color = ElectricCyan, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun CorrectionResultCard(result: com.example.concordance.integration.agent.CorrectionResult) {
    AppSectionCard(title = "Corrected result") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge(
                text = result.status,
                color = if (result.reason != null) WarningAmber else ClinicalTeal
            )
            Badge(text = "tier: ${result.evidenceTier}", color = TextSecondary)
            Badge(
                text = if (result.feasible) "feasible" else "not feasible",
                color = if (result.feasible) NeonEmerald else WarningAmber
            )
            if (result.significantAtR) Badge(text = "significant at r*", color = NeonEmerald)
        }

        result.correctedOddsRatio?.let { ratio ->
            Text(
                "Corrected odds ratio ${fmt3(ratio)}",
                style = MaterialTheme.concordance.cardTitle,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        result.correctedCi?.let { ci ->
            Text(
                "Plausible range ${fmt3(ci.first)} - ${fmt3(ci.second)}",
                style = MaterialTheme.concordance.monoId,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }
        result.r?.let {
            Text("Reported retention r = ${fmt3(it)}", style = MaterialTheme.concordance.monoId, color = TextSecondary, fontFamily = FontFamily.Monospace)
        }
        result.significanceThresholdR?.let {
            Text("Threshold r* = ${fmt3(it)}", style = MaterialTheme.concordance.monoId, color = TextSecondary, fontFamily = FontFamily.Monospace)
        }
        result.minFeasibleR?.let {
            Text("Smallest defensible r = ${fmt3(it)}", style = MaterialTheme.concordance.monoId, color = TextSecondary, fontFamily = FontFamily.Monospace)
        }

        result.interpretation?.let {
            if (it.isNotBlank()) Text(it, style = MaterialTheme.concordance.monoId, color = TextPrimary, lineHeight = 17.sp)
        }
        result.reason?.let {
            if (it.isNotBlank()) Text(it, style = MaterialTheme.concordance.monoId, color = WarningAmber, lineHeight = 17.sp)
        }
        if (result.reason != null || result.status == "withheld") {
            WarningBox(
                "Withheld is a result, not an error: the agent will not report a corrected " +
                    "number the evidence cannot support."
            )
        }
    }
}

/** Three decimals, always with a dot - a decimal comma would misread as a thousands separator. */
private fun fmt3(value: Double): String = String.format(java.util.Locale.US, "%.3f", value)

@Composable
private fun UploadCard(
    context: Context,
    dataClass: String,
    acknowledged: Boolean,
    busy: Boolean,
    onDataClassChange: (String) -> Unit,
    onAcknowledge: () -> Unit,
    onPicked: (AgentUpload) -> Unit,
    onRejected: (String) -> Unit
) {
    var pendingCapture by remember { mutableStateOf<Uri?>(null) }

    fun readAndSend(uri: Uri) {
        when (val result = AgentUploads.read(context, uri)) {
            is AgentUploads.Result.Ok -> onPicked(result.upload)
            is AgentUploads.Result.Rejected -> onRejected(result.reason)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readAndSend(it) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCapture
        if (ok && uri != null) readAndSend(uri)
        pendingCapture = null
    }

    AppSectionCard(title = "Read a figure or PDF") {
        Text(
            "OCR runs on the agent host (PaddleOCR) - the page is never sent to a managed " +
                "platform. Extract text only: nothing is adjudicated until you confirm the numbers.",
            style = MaterialTheme.concordance.overline,
            color = TextSecondary,
            lineHeight = 16.sp
        )

        Text("How should the agent treat this file?", style = MaterialTheme.concordance.overline, color = TextPrimary, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("public" to "Published", "de_identified" to "De-identified", "phi" to "PHI").forEach { (value, label) ->
                FilterChip(
                    selected = dataClass == value,
                    onClick = { onDataClassChange(value) },
                    label = { Text(label, style = MaterialTheme.concordance.overline) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                        selectedLabelColor = ElectricCyan,
                        labelColor = TextSecondary
                    )
                )
            }
        }
        Text(
            if (dataClass == "phi") {
                "PHI is accepted only by a local agent. Routing PHI anywhere off-host is refused."
            } else {
                "Label it honestly: an unlabeled file is treated as PHI by the agent."
            },
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary,
            lineHeight = 14.sp
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = acknowledged,
                onCheckedChange = { if (it) onAcknowledge() },
                colors = CheckboxDefaults.colors(checkedColor = ElectricCyan, checkmarkColor = MidnightDeep)
            )
            Text(
                "I understand the file is sent to my agent server and its text comes back here.",
                style = MaterialTheme.concordance.bodyDense,
                color = TextSecondary,
                lineHeight = 14.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    picker.launch(arrayOf("image/*", "application/pdf"))
                },
                enabled = acknowledged && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated, contentColor = ElectricCyan)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Choose file", style = MaterialTheme.concordance.buttonSmall)
            }
            Button(
                onClick = {
                    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                    val file = File(dir, "figure-${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    pendingCapture = uri
                    camera.launch(uri)
                },
                enabled = acknowledged && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated, contentColor = ElectricCyan)
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Photograph", style = MaterialTheme.concordance.buttonSmall)
            }
        }
    }
}

@Composable
private fun UploadedFigureCard(upload: com.example.concordance.integration.agent.UploadedFigure) {
    AppSectionCard(title = "Extracted text (unverified)") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(text = "tier: ${upload.evidenceTier}", color = ClinicalTeal)
            Badge(
                text = "confidence %.2f".format(upload.confidence),
                color = if (upload.confidence >= 0.7) ClinicalTeal else WarningAmber
            )
            Badge(text = "${upload.pageCount} page(s)", color = TextSecondary)
        }
        Text(
            "Human check required: OCR output is derived text, not a measurement. Confirm every " +
                "count before it reaches an adjudication.",
            style = MaterialTheme.concordance.monoId,
            color = WarningAmber,
            lineHeight = 14.sp
        )
        Text(
            upload.text.ifBlank { "(no text detected)" },
            style = MaterialTheme.concordance.monoId,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            lineHeight = 17.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateCardElevated, RoundedCornerShape(6.dp))
                .padding(10.dp)
        )
        Text("sha256 ${upload.uploadSha256.take(24)}...", style = MaterialTheme.concordance.monoId, color = TextSecondary, fontFamily = FontFamily.Monospace)
        if (upload.disclaimer.isNotBlank()) {
            Text(upload.disclaimer, style = MaterialTheme.concordance.monoId, color = TextSecondary, lineHeight = 14.sp)
        }
    }
}

/**
 * The ecosystem APIs the agent can reach, and the fleet-driven research loop.
 *
 * The phone never holds the tool token: it asks the agent, which meters each call.
 * That is why a service here can show a measured latency and a measured spend.
 */
@Composable
private fun EcosystemCard(
    roster: AgentFleetRoster?,
    loop: AgentLoopResult?,
    busy: Boolean,
    onCheck: () -> Unit,
    onRunLoop: () -> Unit
) {
    AppSectionCard(title = "Ecosystem APIs") {
        Text(
            "The agent calls these deployed APIs directly. Checking asks each one to answer, " +
                "so the latency below is measured rather than configured.",
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary,
            lineHeight = 16.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onCheck,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = SlateCard, contentColor = TextPrimary)
            ) {
                Text("Check the APIs", style = MaterialTheme.concordance.buttonSmall)
            }
            Button(
                onClick = onRunLoop,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep)
            ) {
                Text("Run research loop", style = MaterialTheme.concordance.buttonSmall)
            }
        }

        roster?.let { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(
                    text = if (r.reachable != null) "${r.reachable}/${r.count} answering" else "${r.deployed}/${r.count} deployed",
                    color = when {
                        r.reachable == null -> TextSecondary
                        r.reachable == r.count -> NeonEmerald
                        r.reachable == 0 -> VividCoral
                        else -> WarningAmber
                    }
                )
                if (r.checkMs != null) Badge(text = "${r.checkMs} ms", color = TextSecondary)
                if (!r.tokenConfigured) Badge(text = "no tool token", color = VividCoral)
            }
            r.services.forEach { service -> FleetServiceRow(service) }
        } ?: Text(
            "Not listed yet. Checking costs the agent a few measured seconds.",
            style = MaterialTheme.concordance.monoId,
            color = TextSecondary
        )

        loop?.let { result ->
            HorizontalDivider(color = SlateCardBorder)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(
                    text = "stopped: ${result.stopReason}",
                    color = if (result.stopReason == "pass") NeonEmerald else WarningAmber
                )
                result.bestGrade?.let { Badge(text = "$it ${result.bestScore ?: 0.0}", color = ElectricCyan) }
            }
            Text(
                "${result.iterations} iteration(s) in ${result.elapsedSeconds.toInt()} s · " +
                    "${result.executed} experiment(s) executed · " +
                    "\$${"%.5f".format(result.spendUsd)} of measured fleet spend",
                style = MaterialTheme.concordance.monoId,
                color = TextSecondary,
                lineHeight = 16.sp
            )
            Text(
                "Journal: ${result.journal}",
                style = MaterialTheme.concordance.monoId,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun FleetServiceRow(service: AgentFleetService) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                service.service,
                style = MaterialTheme.concordance.monoId,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace
            )
            if (service.purpose.isNotBlank()) {
                Text(service.purpose, style = MaterialTheme.concordance.monoId, color = TextSecondary)
            }
        }
        Text(
            when {
                service.reachable == true -> "${service.ms ?: 0} ms"
                service.reachable == false -> service.error?.take(28) ?: "unreachable"
                service.deployed -> "deployed"
                else -> "not deployed"
            },
            style = MaterialTheme.concordance.monoId,
            color = when {
                service.reachable == true -> NeonEmerald
                service.reachable == false -> VividCoral
                else -> TextSecondary
            }
        )
    }
}

@Composable
private fun AgentJobCard(job: com.example.concordance.integration.agent.AgentJob) {
    AppSectionCard(title = "Agent result") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge(text = job.status, color = statusColor(job))
            job.capsule?.let { capsule ->
                Badge(text = capsule.verdict.uppercase(), color = verdictColor(capsule.verdict))
                Badge(text = capsule.evidenceTier, color = TextSecondary)
                Badge(text = "rubric ${capsule.rubricVersion}", color = TextSecondary)
            }
        }
        job.capsule?.let { capsule ->
            if (capsule.reason.isNotBlank()) {
                Text(capsule.reason, style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 17.sp)
            }
            if (capsule.correctedOddsRatio != null) {
                Text(
                    "Corrected OR ${"%.3f".format(capsule.correctedOddsRatio)}" +
                        (capsule.correctedCi?.let { " (range ${"%.3f".format(it.first)}-${"%.3f".format(it.second)})" } ?: ""),
                    style = MaterialTheme.concordance.monoId,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        if (job.wasRefused) {
            Text(
                "Withheld is a result, not a fault: the agent only reports a verdict it can support.",
                style = MaterialTheme.concordance.monoId,
                color = WarningAmber,
                lineHeight = 14.sp
            )
        }
        job.error?.let { Text(it, style = MaterialTheme.concordance.monoId, color = WarningAmber) }
        Text("job ${job.jobId}", style = MaterialTheme.concordance.monoId, color = TextSecondary, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SpeechCard(
    view: EasyModeView,
    engineName: String,
    onSpeak: () -> Unit,
    onStop: () -> Unit
) {
    AppSectionCard(title = "Read it aloud") {
        Text(
            "Spoken text is the agent's own summary plus the disclaimer. Voice: $engineName.",
            style = MaterialTheme.concordance.monoId,
            color = TextSecondary,
            lineHeight = 16.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSpeak,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep)
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Speak", style = MaterialTheme.concordance.buttonSmall)
            }
            TextButton(onClick = onStop) { Text("Stop", style = MaterialTheme.concordance.bodyDense, color = TextSecondary) }
        }
        Text(
            "\"${EasyMode.speakableSummary(view)}\"",
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun EasyModeCard(view: EasyModeView) {
    AppSectionCard(title = "In plain language") {
        Text(view.headline, style = MaterialTheme.concordance.cardTitle, fontWeight = FontWeight.ExtraBold, color = TextPrimary)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(text = "signal: ${view.signalLabel}", color = bandColor(view.signalBand))
            Badge(text = "evidence: ${view.qualityLabel}", color = ClinicalTeal)
        }
        if (view.signalDetail.isNotBlank()) {
            Text(view.signalDetail, style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 17.sp)
        }
        if (view.qualityDetail.isNotBlank()) {
            Text(view.qualityDetail, style = MaterialTheme.concordance.bodyDense, color = TextSecondary, lineHeight = 16.sp)
        }

        EasyMode.absenceWarning(view.signalBand)?.let { WarningBox(it) }
        EasyMode.qualityWarning(view.evidenceTier)?.let { WarningBox(it) }

        BulletList("What we did", view.whatWeDid)
        if (view.whatWeFound.isNotEmpty()) {
            BulletList("What we found", view.whatWeFound.map { (k, v) -> "$k: $v" })
        }
        BulletList("What this does NOT mean", EasyMode.ensureDisclaimer(view.doesNotMean), highlight = true)
        BulletList("What to do next", view.nextSteps)

        if (view.wordsWeUsed.isNotEmpty()) {
            Text("Words we used", style = MaterialTheme.concordance.bodyDense, color = ElectricCyan, fontWeight = FontWeight.Bold)
            view.wordsWeUsed.forEach { term ->
                Column {
                    Text("${term.term} - ${term.plain}", style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 16.sp)
                    if (term.why.isNotBlank()) {
                        Text(term.why, style = MaterialTheme.concordance.bodyDense, color = TextSecondary, lineHeight = 14.sp)
                    }
                }
            }
        }

        if (view.numbers.isNotEmpty()) {
            Text("The numbers (unchanged from the agent)", style = MaterialTheme.concordance.monoId, color = ElectricCyan, fontWeight = FontWeight.Bold)
            view.numbers.forEach { (k, v) ->
                Text("$k = $v", style = MaterialTheme.concordance.monoId, color = TextSecondary, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun ExpertCard(view: EasyModeView, rawJson: String) {
    AppSectionCard(title = "Expert view") {
        Text(
            "The raw payload the agent returned. Compare it with the plain version: the plain " +
                "text must never disagree with these numbers.",
            style = MaterialTheme.concordance.monoId,
            color = TextSecondary,
            lineHeight = 16.sp
        )
        val payload = rawJson.ifBlank { "(the agent did not return a raw payload for this path)" }
        Text(
            payload,
            style = MaterialTheme.concordance.monoId,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .background(MidnightDeep, RoundedCornerShape(6.dp))
                .border(1.dp, SlateCardBorder, RoundedCornerShape(6.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        )
        if (view.signalBand.isNotBlank()) {
            Text("band = ${view.signalBand}", style = MaterialTheme.concordance.monoId, color = TextSecondary, fontFamily = FontFamily.Monospace)
        }
    }
}

// ---- small shared pieces ----

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.concordance.monoId, color = color, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

@Composable
private fun WarningBox(text: String) {
    Text(
        text,
        style = MaterialTheme.concordance.monoId,
        color = WarningAmber,
        lineHeight = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(WarningAmber.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .padding(8.dp)
    )
}

@Composable
private fun BulletList(title: String, items: List<String>, highlight: Boolean = false) {
    if (items.isEmpty()) return
    Text(
        title,
        style = MaterialTheme.concordance.bodyDense,
        fontWeight = FontWeight.Bold,
        color = if (highlight) WarningAmber else ElectricCyan
    )
    items.forEach { item ->
        Row {
            Text("* ", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
            Text(
                item,
                style = MaterialTheme.concordance.bodyDense,
                color = if (highlight) TextPrimary else TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = BorderStrong,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = ElectricCyan,
    unfocusedLabelColor = TextSecondary,
    cursorColor = ElectricCyan
)

@Composable
private fun statusColor(job: com.example.concordance.integration.agent.AgentJob): Color = when {
    job.capsule != null -> ClinicalTeal
    job.wasRefused -> WarningAmber
    else -> TextSecondary
}

@Composable
private fun verdictColor(verdict: String): Color = when (verdict.lowercase()) {
    "supported" -> NeonEmerald
    "not_supported" -> VividCoral
    "inconclusive" -> WarningAmber
    else -> TextSecondary
}

@Composable
private fun bandColor(band: String): Color = when (band) {
    "strong" -> NeonEmerald
    "moderate" -> ClinicalTeal
    "weak" -> WarningAmber
    "none" -> TextSecondary
    else -> TextSecondary
}

@Composable
private fun PendingDecisionsCard(
    decisions: List<AgentPendingDecision>,
    onResolve: (String, String) -> Unit
) {
    AppSectionCard(title = "Pending decisions (${decisions.size})") {
        Text("The decision model flagged these as ambiguous or high-risk. Pick a path.", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
        decisions.forEach { d ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Text("${d.action.uppercase()}  score ${"%.2f".format(d.score)}", style = MaterialTheme.concordance.bodyDense, color = WarningAmber, fontWeight = FontWeight.Bold)
                Text(d.candidate.take(140), style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 14.sp)
                d.reasons.take(2).forEach { r ->
                    Text("   * $r", style = MaterialTheme.concordance.bodyDense, color = TextSecondary, lineHeight = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    listOf("pursue_branch_a", "pursue_branch_b", "pursue_both", "neither").forEach { choice ->
                        TextButton(onClick = { onResolve(d.id, choice) }, modifier = Modifier.height(28.dp)) {
                            Text(choice.replace("_", " "), style = MaterialTheme.concordance.bodyDense, color = ElectricCyan)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IngestCard(
    seed: String?,
    busy: Boolean,
    onIngest: (Uri) -> Unit,
    onClear: () -> Unit,
    onStartResearch: () -> Unit
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) onIngest(uri)
    }
    AppSectionCard(title = "Ingest & seed (start where you want)") {
        Text("Load your papers, datasets, or notes. The agent analyzes them and proposes a founded starting point.", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
        Button(
            onClick = { picker.launch("*/*") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep)
        ) {
            if (busy) { CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MidnightDeep); Spacer(Modifier.width(6.dp)) }
            Text("Choose file to ingest", style = MaterialTheme.concordance.buttonSmall)
        }
        if (!seed.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("Research seed (review, then start):", style = MaterialTheme.concordance.bodyDense, color = TextSecondary, fontWeight = FontWeight.Bold)
            Text(seed.take(1200), style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(onClick = onStartResearch, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = MidnightDeep)) {
                    Text("Start research from seed", style = MaterialTheme.concordance.buttonSmall)
                }
                TextButton(onClick = onClear) { Text("Clear", color = TextSecondary) }
            }
        }
    }
}

@Composable
private fun ResearchResultCard(result: AgentResearchResult, onFeedback: (String, String) -> Unit = { _, _ -> }, onSpeak: (String) -> Unit = {}) {
    AppSectionCard(title = "Research & experiment design") {
        if (result.summary.isNotBlank()) {
            Text(result.summary, style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 16.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            TextButton(onClick = { onSpeak(result.summary + " " + result.nextSteps.joinToString(". ")) }) {
                Text("Speak report", style = MaterialTheme.concordance.bodyDense, color = ElectricCyan)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Checks & balances", style = MaterialTheme.concordance.bodyDense, color = TextSecondary, fontWeight = FontWeight.Bold)
        if (result.runId.isNotBlank()) {
            Text("Run: ${result.runId}", style = MaterialTheme.concordance.monoId, color = TextSecondary)
        }
        val llmText = when {
            !result.llmUsed -> "Model: not used (deterministic scaffold)"
            result.llmOk -> "Model: used"
            else -> "Model: unavailable, deterministic scaffold used"
        }
        Text(
            llmText,
            style = MaterialTheme.concordance.bodyDense,
            color = if (result.llmUsed && result.llmOk) ClinicalTeal else WarningAmber
        )
        if (result.actionCounts.isNotEmpty()) {
            val summary = result.actionCounts.entries.joinToString(", ") { "${it.key} ${it.value}" }
            Text("Decisions: $summary", style = MaterialTheme.concordance.bodyDense, color = TextPrimary)
        }
        result.decisions.take(4).forEach { d ->
            Text(
                "${d.action.uppercase()}  score ${"%.2f".format(d.score)}  conf ${"%.2f".format(d.confidence)}  - ${d.candidate.take(110)}",
                style = MaterialTheme.concordance.bodyDense,
                color = when (d.action) {
                    "proceed" -> ClinicalTeal
                    "revise", "fork" -> WarningAmber
                    else -> TextSecondary
                },
                lineHeight = 14.sp
            )
            d.reasons.take(2).forEach { r ->
                Text("   * $r", style = MaterialTheme.concordance.bodyDense, color = TextSecondary, lineHeight = 13.sp)
            }
        }
        if (result.criticVerdict.isNotBlank()) {
            Text(
                "Critic: ${result.criticVerdict}  (${"%.2f".format(result.criticScore)})",
                style = MaterialTheme.concordance.bodyDense,
                color = if (result.criticVerdict == "pass") ClinicalTeal else WarningAmber,
                fontWeight = FontWeight.Bold
            )
            result.criticFindings.take(6).forEach { f ->
                Text("   * $f", style = MaterialTheme.concordance.bodyDense, color = TextSecondary, lineHeight = 13.sp)
            }
        }
        if (result.stopShouldStop) {
            Spacer(Modifier.height(8.dp))
            Text("Stop condition met:", style = MaterialTheme.concordance.bodyDense, color = WarningAmber, fontWeight = FontWeight.Bold)
            result.stopReasons.forEach { r ->
                Text("   * $r", style = MaterialTheme.concordance.bodyDense, color = WarningAmber, lineHeight = 13.sp)
            }
        }
        if (result.deadEnds.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Dead ends (do not re-propose): ${result.deadEnds.size}", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
        }
        if (result.claimUpdates.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Claims updated", style = MaterialTheme.concordance.bodyDense, color = TextSecondary, fontWeight = FontWeight.Bold)
            result.claimUpdates.forEach { c ->
                Column(Modifier.padding(vertical = 2.dp)) {
                    Text("${c.status.uppercase()}  conf ${"%.2f".format(c.confidence)}  - ${c.claim.take(120)}", style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("useful", "not_useful", "already_known", "out_of_scope").forEach { rating ->
                            TextButton(onClick = { onFeedback(c.id, rating) }, modifier = Modifier.height(24.dp)) {
                                Text(rating.replace("_", " "), style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
        if (result.paperArtifacts > 0) {
            Text(
                "Paper chain: ${result.paperArtifacts} files written locally",
                style = MaterialTheme.concordance.bodyDense,
                color = ClinicalTeal
            )
            if (result.paperDir.isNotBlank()) {
                Text(result.paperDir, style = MaterialTheme.concordance.monoId, color = TextSecondary)
            }
        }
        if (result.hypotheses.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Hypotheses", style = MaterialTheme.concordance.bodyDense, color = TextSecondary, fontWeight = FontWeight.Bold)
            result.hypotheses.forEach { h ->
                Text("- $h", style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 14.sp)
            }
        }
        if (result.experiments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Experiments", style = MaterialTheme.concordance.bodyDense, color = TextSecondary, fontWeight = FontWeight.Bold)
            result.experiments.forEach { e ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(e.title, style = MaterialTheme.concordance.bodyDense, color = ElectricCyan, fontWeight = FontWeight.Bold)
                    if (e.design.isNotBlank()) Text("Design: ${e.design}", style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 14.sp)
                    if (e.controls.isNotBlank()) Text("Controls: ${e.controls}", style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 14.sp)
                    if (e.endpoints.isNotBlank()) Text("Endpoints: ${e.endpoints}", style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 14.sp)
                    if (e.estimatedDuration.isNotBlank()) Text("Duration: ${e.estimatedDuration}", style = MaterialTheme.concordance.bodyDense, color = TextSecondary, lineHeight = 14.sp)
                    if (e.risk.isNotBlank()) Text("Risk: ${e.risk}", style = MaterialTheme.concordance.bodyDense, color = TextSecondary, lineHeight = 14.sp)
                    if (e.routing.isNotBlank()) {
                        Text(
                            "Route: ${e.routing}",
                            style = MaterialTheme.concordance.monoId,
                            color = if (e.routing == "local") TextSecondary else ClinicalTeal
                        )
                    }
                }
            }
        }
        if (result.nextSteps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Next steps", style = MaterialTheme.concordance.bodyDense, color = TextSecondary, fontWeight = FontWeight.Bold)
            result.nextSteps.forEach { n ->
                Text("- $n", style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 14.sp)
            }
        }
        if (result.summary.isBlank() && result.experiments.isEmpty() && result.rawJson.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(result.rawJson.take(600), style = MaterialTheme.concordance.bodyDense, color = TextSecondary, lineHeight = 14.sp)
        }
    }
}

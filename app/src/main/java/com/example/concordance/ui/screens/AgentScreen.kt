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
import com.example.concordance.integration.agent.AgentExperiment
import com.example.concordance.integration.agent.AgentResearchResult
import com.example.concordance.integration.agent.AgentRefereeReport
import com.example.concordance.integration.agent.AgentUpload
import com.example.concordance.integration.agent.AgentUploads
import com.example.concordance.integration.agent.EasyModeView
import com.example.concordance.ui.ConcordanceViewModel
import com.example.concordance.ui.components.AppSectionCard
import com.example.concordance.ui.components.EmptyState
import com.example.ui.theme.*
import java.io.File

/**
 * Client for the self-hosted adjudication agent.
 *
 * Three invariants the UI is responsible for:
 *  1. Audio never leaves the phone — dictation is on-device only, and says so.
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
    val upload by viewModel.agentUpload.collectAsState()
    val transcript by viewModel.agentTranscript.collectAsState()
    val researchResult by viewModel.agentResearchResult.collectAsState()
    val refereeReport by viewModel.agentRefereeReport.collectAsState()
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

        DictationCard(
            transcript = transcript,
            onTranscriptChange = { viewModel.setAgentTranscript(it) },
            onToggleDictation = { viewModel.toggleDictation() },
            unavailableReason = viewModel.dictationUnavailableReason,
            onResearch = { useLlm -> viewModel.runAgentResearch(useLlm) },
            busy = busy
        )

        researchResult?.let { ResearchResultCard(it) }

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

        RefereeAuditCard(
            dataClass = dataClass,
            busy = busy,
            onAudit = { claimJson, csvText ->
                viewModel.runAgentRefereeAudit(claimJson, csvText, dataClass, "local")
            }
        )

        upload?.let { UploadedFigureCard(it) }

        job?.let { AgentJobCard(it) }

        correction?.let { CorrectionResultCard(it) }

        refereeReport?.let { RefereeReportCard(it) }

        val hasResult = job != null || upload != null || correction != null || refereeReport != null
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

        if (job != null || upload != null || correction != null || refereeReport != null) {
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
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onToggleDictation()
    }
    var useLlm by remember { mutableStateOf(true) }

    AppSectionCard(title = "Dictate the request (on this phone)") {
        Text(
            "Speech is transcribed on the device. Audio is never uploaded — only the text you " +
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
                Text("Use cloud model for this run", style = MaterialTheme.concordance.bodyDense, color = TextPrimary)
                Text(
                    "Off runs the deterministic scaffold (no model quota).",
                    style = MaterialTheme.concordance.caption,
                    color = TextSecondary
                )
            }
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
                                    "${anchor.id} · ${anchor.evidenceGrade}",
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
                "Plausible range ${fmt3(ci.first)} – ${fmt3(ci.second)}",
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

/** Three decimals, always with a dot — a decimal comma would misread as a thousands separator. */
private fun fmt3(value: Double): String = String.format(java.util.Locale.US, "%.3f", value)

/**
 * Referee adjudication through the agent.
 *
 * The claim specification (JSON) and the prediction table (CSV) are pasted in
 * and go to the agent the user configured - nowhere else. The referee refuses
 * to judge a claim without data, and a refusal comes back as an answer with
 * its reason rather than as an error.
 */
@Composable
private fun RefereeAuditCard(
    dataClass: String,
    busy: Boolean,
    onAudit: (claimJson: String, csvText: String) -> Unit
) {
    var claim by remember { mutableStateOf("") }
    var csv by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AppSectionCard(title = "Referee adjudication") {
        Text(
            "A deterministic referee replays the claim against your prediction table: " +
                "bootstrap the claimed interval, check the split for leakage, compare against " +
                "locked baselines. It refuses when the data cannot support a verdict.",
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary,
            lineHeight = 16.sp
        )

        TextButton(onClick = { expanded = !expanded }) {
            Text(
                if (expanded) "Hide the inputs" else "Show the inputs",
                color = TextSecondary,
                style = MaterialTheme.concordance.buttonSmall
            )
        }

        if (expanded) {
            OutlinedTextField(
                value = claim,
                onValueChange = { claim = it },
                label = { Text("Claim specification (JSON)", style = MaterialTheme.concordance.bodyDense) },
                placeholder = {
                    Text(
                        "{\"claim_id\": \"...\", \"claimed_auc\": 0.85, ...}",
                        style = MaterialTheme.concordance.monoValue
                    )
                },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.concordance.monoValue,
                colors = fieldColors()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = csv,
                onValueChange = { csv = it },
                label = {
                    Text(
                        "Prediction table (CSV: patient_id, label, split, score)",
                        style = MaterialTheme.concordance.bodyDense
                    )
                },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.concordance.monoValue,
                colors = fieldColors()
            )
            Spacer(Modifier.height(8.dp))
        }

        Text(
            "Data class: $dataClass · routed to your own agent (target backend: local). " +
                "Nothing is sent to a managed platform.",
            style = MaterialTheme.concordance.monoId,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onAudit(claim, csv) },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep)
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MidnightDeep)
                Spacer(Modifier.width(6.dp))
            }
            Text("Run referee audit", style = MaterialTheme.concordance.buttonSmall)
        }
    }
}

/**
 * The referee's verdict.
 *
 * The digests are shown because they are the point: `report_sha256` is what
 * makes the verdict checkable, and `provenance` says whether the numbers were
 * computed from the table or are a reference.
 */
@Composable
private fun RefereeReportCard(report: AgentRefereeReport) {
    AppSectionCard(title = "Referee verdict") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(report.verdict, verdictColor(report.verdict))
        }
        Spacer(Modifier.height(8.dp))

        report.provenance?.takeIf { it.isNotBlank() }?.let { provenance ->
            WarningBox(
                if (provenance.equals("computed", ignoreCase = true)) {
                    "Provenance: computed from the table you supplied."
                } else {
                    "Provenance: $provenance. These numbers are not computed from your table."
                }
            )
            Spacer(Modifier.height(8.dp))
        }

        if (report.reasons.isNotEmpty()) {
            BulletList("Why", report.reasons)
            Spacer(Modifier.height(8.dp))
        }

        if (report.dimensions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                report.dimensions.forEach { (name, value) ->
                    Text(
                        "$name: $value",
                        style = MaterialTheme.concordance.monoId,
                        color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "report_sha256: ${report.reportSha256 ?: "—"}",
                style = MaterialTheme.concordance.monoId,
                color = TextSecondary
            )
            Text(
                "decision_sha256: ${report.decisionSha256 ?: "—"}",
                style = MaterialTheme.concordance.monoId,
                color = TextSecondary
            )
            Text(
                "input_sha256: ${report.inputSha256 ?: "—"}",
                style = MaterialTheme.concordance.monoId,
                color = TextSecondary
            )
        }

        report.disposition?.takeIf { it.isNotBlank() }?.let { disposition ->
            Spacer(Modifier.height(8.dp))
            Text(
                "Disposition: $disposition",
                style = MaterialTheme.concordance.monoId,
                color = TextSecondary
            )
        }
    }
}

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
            "OCR runs on the agent host (PaddleOCR) — the page is never sent to a managed " +
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
        Text("sha256 ${upload.uploadSha256.take(24)}…", style = MaterialTheme.concordance.monoId, color = TextSecondary, fontFamily = FontFamily.Monospace)
        if (upload.disclaimer.isNotBlank()) {
            Text(upload.disclaimer, style = MaterialTheme.concordance.monoId, color = TextSecondary, lineHeight = 14.sp)
        }
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
                        (capsule.correctedCi?.let { " (range ${"%.3f".format(it.first)}–${"%.3f".format(it.second)})" } ?: ""),
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
                    Text("${term.term} — ${term.plain}", style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 16.sp)
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
            Text("• ", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
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
    // Adjudication verdicts.
    "supported" -> NeonEmerald
    "not_supported" -> VividCoral
    "inconclusive" -> WarningAmber
    // Referee verdicts.
    "reproduced" -> NeonEmerald
    "not_reproduced" -> VividCoral
    "fails_baseline" -> VividCoral
    "leakage_detected" -> VividCoral
    "unverifiable" -> WarningAmber
    "out_of_scope" -> TextSecondary
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
private fun ResearchResultCard(result: AgentResearchResult) {
    AppSectionCard(title = "Research & experiment design") {
        if (result.summary.isNotBlank()) {
            Text(result.summary, style = MaterialTheme.concordance.bodyDense, color = TextPrimary, lineHeight = 16.sp)
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

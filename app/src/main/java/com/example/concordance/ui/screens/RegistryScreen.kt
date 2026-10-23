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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.concordance.engine.StatisticalEngine
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import com.example.concordance.ui.ConcordanceViewModel
import com.example.concordance.ui.components.EmptyState
import com.example.concordance.ui.components.SkeletonCard
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun RegistryScreen(
    viewModel: ConcordanceViewModel,
    onSelectAndAdjudicate: (Anchor) -> Unit,
    modifier: Modifier = Modifier
) {
    val claims by viewModel.claims.collectAsState()
    val loadingClaims by viewModel.isLoadingClaims.collectAsState()
    val communityPublishing by viewModel.communityPublishing.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var challengeTarget by remember { mutableStateOf<Anchor?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MidnightDeep,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = ElectricCyan,
                contentColor = MidnightDeep,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Register new claim")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "LIVING CLAIM REGISTRY",
                        style = MaterialTheme.concordance.overline,
                        color = ElectricCyan,
                    )
                    Text(
                        text = "Public repository of organotropism claims, provenance, and empirical challenges. Each claim is evaluated against detection artifact thresholds.",
                        style = MaterialTheme.concordance.bodyDense,
                        color = TextSecondary,
                    )
                }
            }

            if (loadingClaims) {
                // First database read: show the shape of the list rather than a
                // misleading "no claims" message.
                items(3) { SkeletonCard() }
            } else if (claims.isEmpty()) {
                item {
                    EmptyState(
                        title = "No claims yet",
                        message = "Add the first claim anchor to start adjudicating.",
                    )
                }
            }

            items(claims) { claim ->
                // An anchor the correction cannot handle is shown as refused, never as a 0.0.
                val unusable = claim.validationError()
                val metrics = if (unusable == null) {
                    val (pb, pn) = StatisticalEngine.observedPrevalences(claim)
                    val rNull = StatisticalEngine.nullCrossingR(claim)
                    val (orEst, orLo, orHi) = StatisticalEngine.observedOrCi(claim)
                    listOf(orEst, orLo, orHi, rNull, pb, pn)
                } else {
                    null
                }

                ClaimRegistryCard(
                    claim = claim,
                    observedOr = metrics?.get(0),
                    ciLow = metrics?.get(1),
                    ciHigh = metrics?.get(2),
                    rNull = metrics?.get(3),
                    prevBrain = metrics?.get(4) ?: 0.0,
                    prevOther = metrics?.get(5) ?: 0.0,
                    unusableReason = unusable,
                    onAdjudicate = {
                        viewModel.selectAnchor(claim)
                        onSelectAndAdjudicate(claim)
                    },
                    onChallenge = { challengeTarget = claim },
                    onPublish = { viewModel.publishClaimToCommunity(claim) },
                    publishing = communityPublishing
                )
            }
        }
    }

    if (showAddDialog) {
        AddClaimDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { newAnchor ->
                viewModel.addClaim(newAnchor)
                showAddDialog = false
            }
        )
    }

    if (challengeTarget != null) {
        ChallengeClaimDialog(
            claim = challengeTarget!!,
            onDismiss = { challengeTarget = null },
            onSubmit = { author, notes, bE, bT, oE, oT ->
                viewModel.challengeClaim(challengeTarget!!.id, author, notes, bE, bT, oE, oT)
                challengeTarget = null
            }
        )
    }
}

@Composable
fun ClaimRegistryCard(
    claim: Anchor,
    observedOr: Double?,
    ciLow: Double?,
    ciHigh: Double?,
    rNull: Double?,
    prevBrain: Double,
    prevOther: Double,
    /** Non-null when the anchor cannot be corrected; the card then reports the reason. */
    unusableReason: String? = null,
    onAdjudicate: () -> Unit,
    onChallenge: () -> Unit,
    onPublish: () -> Unit = {},
    publishing: Boolean = false
) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = claim.label,
                    style = MaterialTheme.concordance.cardTitle,
                    color = TextPrimary
                )
                Text(
                    text = "ID: ${claim.id} • Added: ${claim.dateAdded}",
                    style = MaterialTheme.concordance.monoId,
                    color = TextMuted
                )
            }
            EvidenceBadge(claim.evidenceGrade)
        }

        Text(
            text = claim.source,
            style = MaterialTheme.concordance.caption,
            color = TextSecondary,
            maxLines = 2
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SlateCardElevated)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Observed OR", style = MaterialTheme.concordance.metricLabel, color = TextMuted)
                Text(
                    text = observedOr?.let { String.format(Locale.US, "%.3f", it) } ?: "not estimable",
                    style = MaterialTheme.concordance.monoValue,
                    color = if (observedOr == null) TextMuted else ElectricCyan
                )
            }

            Column {
                Text("95% Interval", style = MaterialTheme.concordance.metricLabel, color = TextMuted)
                Text(
                    text = if (ciLow != null && ciHigh != null) {
                        "[${String.format(Locale.US, "%.2f", ciLow)}–${String.format(Locale.US, "%.2f", ciHigh)}]"
                    } else {
                        "—"
                    },
                    style = MaterialTheme.concordance.monoValue,
                    color = TextPrimary
                )
            }

            Column {
                Text("Artifact Null r*", style = MaterialTheme.concordance.metricLabel, color = TextMuted)
                Text(
                    text = rNull?.let { String.format(Locale.US, "%.2f×", it) } ?: "—",
                    style = MaterialTheme.concordance.monoValue,
                    color = if (rNull == null) TextMuted else VividCoral
                )
            }
        }

        if (unusableReason != null) {
            Text(
                text = "Not estimable: $unusableReason",
                style = MaterialTheme.concordance.caption,
                color = WarningGold
            )
        }

        if (claim.notes.isNotEmpty()) {
            Text(
                text = claim.notes,
                style = MaterialTheme.concordance.caption,
                color = TextSecondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onChallenge,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningGold)
            ) {
                Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Challenge", style = MaterialTheme.concordance.buttonSmall)
            }

            Button(
                onClick = onAdjudicate,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep)
            ) {
                Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Adjudicate", style = MaterialTheme.concordance.buttonSmall)
            }
        }

        OutlinedButton(
            onClick = onPublish,
            enabled = !publishing,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                if (publishing) "Publishing…" else "Publish to community (timestamped)",
                style = MaterialTheme.concordance.buttonSmall
            )
        }
    }
}

@Composable
fun AddClaimDialog(
    onDismiss: () -> Unit,
    onAdd: (Anchor) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var brainEvents by remember { mutableStateOf("45") }
    var brainTotal by remember { mutableStateOf("700") }
    var otherEvents by remember { mutableStateOf("2800") }
    var otherTotal by remember { mutableStateOf("14000") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register New Claim Anchor", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Claim Label (e.g. Study Name)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("Citation / Source") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brainEvents,
                        onValueChange = { brainEvents = it },
                        label = { Text("Brain Events") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = brainTotal,
                        onValueChange = { brainTotal = it },
                        label = { Text("Brain Total") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = otherEvents,
                        onValueChange = { otherEvents = it },
                        label = { Text("Other Events") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = otherTotal,
                        onValueChange = { otherTotal = it },
                        label = { Text("Other Total") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Assay Details") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bE = brainEvents.toIntOrNull() ?: 1
                    val bT = brainTotal.toIntOrNull() ?: 10
                    val oE = otherEvents.toIntOrNull() ?: 1
                    val oT = otherTotal.toIntOrNull() ?: 10
                    val id = "claim_${System.currentTimeMillis() % 100000}"
                    onAdd(
                        Anchor(
                            id = id,
                            label = if (label.isNotBlank()) label else "Custom Claim $id",
                            source = if (source.isNotBlank()) source else "User registered claim",
                            evidenceGrade = EvidenceGrade.COUNTS,
                            brainEvents = bE,
                            brainTotal = bT,
                            otherEvents = oE,
                            otherTotal = oT,
                            notes = notes,
                            isCustom = true
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep)
            ) {
                Text("Register")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = SlateCard
    )
}

@Composable
fun ChallengeClaimDialog(
    claim: Anchor,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Int, Int, Int, Int) -> Unit
) {
    var author by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var brainEvents by remember { mutableStateOf((claim.brainEvents ?: 53).toString()) }
    var brainTotal by remember { mutableStateOf((claim.brainTotal ?: 822).toString()) }
    var otherEvents by remember { mutableStateOf((claim.otherEvents ?: 3220).toString()) }
    var otherTotal by remember { mutableStateOf((claim.otherTotal ?: 15516).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Challenge Claim: ${claim.label}", style = MaterialTheme.concordance.dialogTitle, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Log alternative counts, audit corrections, or tissue re-evaluations for this claim.",
                    style = MaterialTheme.concordance.dialogBody,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Challenger Name / Lab") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Scientific Rationale / Citation") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brainEvents,
                        onValueChange = { brainEvents = it },
                        label = { Text("Corrected Brain Events") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = brainTotal,
                        onValueChange = { brainTotal = it },
                        label = { Text("Brain Total") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = otherEvents,
                        onValueChange = { otherEvents = it },
                        label = { Text("Corrected Other Events") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = otherTotal,
                        onValueChange = { otherTotal = it },
                        label = { Text("Other Total") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bE = brainEvents.toIntOrNull() ?: (claim.brainEvents ?: 1)
                    val bT = brainTotal.toIntOrNull() ?: (claim.brainTotal ?: 10)
                    val oE = otherEvents.toIntOrNull() ?: (claim.otherEvents ?: 1)
                    val oT = otherTotal.toIntOrNull() ?: (claim.otherTotal ?: 10)
                    onSubmit(if (author.isNotBlank()) author else "Anonymous Researcher", notes, bE, bT, oE, oT)
                },
                colors = ButtonDefaults.buttonColors(containerColor = WarningGold, contentColor = MidnightDeep)
            ) {
                Text("Submit Challenge")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = SlateCard
    )
}

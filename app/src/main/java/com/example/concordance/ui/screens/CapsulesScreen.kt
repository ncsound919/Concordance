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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.concordance.integration.api.Provenance
import com.example.concordance.integration.aws.AwsCloudManager
import com.example.concordance.integration.cloudflare.CloudflareR2Manager
import com.example.concordance.integration.supabase.SyncConflictBus
import com.example.concordance.model.Capsule
import com.example.concordance.ui.ConcordanceViewModel
import com.example.concordance.ui.components.EmptyState
import com.example.concordance.ui.components.SkeletonCard
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun CapsulesScreen(
    viewModel: ConcordanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val capsules by viewModel.savedCapsules.collectAsState()
    val loadingCapsules by viewModel.isLoadingCapsules.collectAsState()
    val verificationResult by viewModel.verificationResult.collectAsState()
    val capsuleProvenance by viewModel.capsuleProvenance.collectAsState()
    val selectedCapsuleId by viewModel.selectedCapsuleId.collectAsState()
    val communityPublishing by viewModel.communityPublishing.collectAsState()
    val syncConflicts by viewModel.syncConflicts.collectAsState()
    var showConflictDialog by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var jsonToVerify by remember { mutableStateOf("") }
    var expandedCapsuleId by remember { mutableStateOf<String?>(null) }
    var userToast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(expandedCapsuleId) {
        viewModel.loadProvenanceForCapsule(expandedCapsuleId)
    }

    fun copyText(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        userToast = "Copied to clipboard!"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightDeep)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Sync conflicts surfaced by the WorkManager sync. A conflict is not a
        // failure to retry: it needs a human decision, so it gets its own card
        // rather than a transient snackbar.
        if (syncConflicts.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(WarningAmber.copy(alpha = 0.12f))
                    .border(1.dp, WarningAmber, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "SYNC CONFLICTS (${syncConflicts.size})",
                    style = MaterialTheme.concordance.overline,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmber
                )
                syncConflicts.take(3).forEach { c ->
                    Text(
                        text = "${c.entityType} ${c.entityId}: ${c.reason}",
                        style = MaterialTheme.concordance.caption,
                        color = TextSecondary
                    )
                }
                TextButton(onClick = { viewModel.clearSyncConflicts() }) {
                    Text("Dismiss", style = MaterialTheme.concordance.buttonSmall, color = WarningAmber)
                }
                TextButton(onClick = { showConflictDialog = true }) {
                    Text("Resolve...", style = MaterialTheme.concordance.buttonSmall, color = WarningAmber)
                }
            }
        }

        if (showConflictDialog && syncConflicts.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { showConflictDialog = false },
                title = { Text("Resolve sync conflicts", style = MaterialTheme.concordance.cardTitle) },
                text = {
                    ConflictResolutionScreen(
                        conflicts = syncConflicts,
                        onResolve = { conflict, resolution ->
                            viewModel.resolveSyncConflict(conflict, resolution)
                            showConflictDialog = false
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showConflictDialog = false }) {
                        Text("Close", style = MaterialTheme.concordance.buttonSmall)
                    }
                }
            )
        }

        // Title and Header
        Text(
            text = "CITABLE ARTIFACT REGISTRY",
            style = MaterialTheme.concordance.overline,
            color = ElectricCyan
        )
        Text(
            text = "Permanent, SHA-256 verifiable claim capsules with DOI-like resolvable identities (e.g. concordance.app/c/<id>).",
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Cloud sync status row
        val s3Status by AwsCloudManager.syncStatus.collectAsState()
        val r2Status by CloudflareR2Manager.syncStatus.collectAsState()
        val s3Synced by AwsCloudManager.syncedObjects.collectAsState()
        val r2Synced by CloudflareR2Manager.syncedObjects.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "S3: ${s3Status.label} (${s3Synced.size})",
                style = MaterialTheme.concordance.monoId,
                color = cloudStatusColor(s3Status.colorHex),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "R2: ${r2Status.label} (${r2Synced.size})",
                style = MaterialTheme.concordance.monoId,
                color = cloudStatusColor(r2Status.colorHex),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
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
                text = { Text("Artifacts (${capsules.size})", style = MaterialTheme.concordance.buttonSmall) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Cryptographic Verifier", style = MaterialTheme.concordance.buttonSmall) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            // Tab 1: Artifacts List
            if (loadingCapsules) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(3) { SkeletonCard() }
                }
            } else if (capsules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = "No capsules minted yet",
                        message = "Run an adjudication and tap 'Mint Capsule' to generate a DOI-like citable artifact.",
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(capsules) { c ->
                        val isExpanded = expandedCapsuleId == c.id
                        CapsuleCard(
                            capsule = c,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedCapsuleId = if (isExpanded) null else c.id
                            },
                            onCopyPermalink = { copyText("Concordance Permalink", "https://${c.permalink}") },
                            onCopyMarkdown = { copyText("Concordance Markdown", c.markdown) },
                            onCopyJson = { copyText("Concordance JSON", c.rawJson) },
                            onDelete = { viewModel.deleteCapsule(c.id) },
                            onSyncToCloud = { viewModel.syncCapsuleToCloud(c) },
                            onPublish = { viewModel.publishCapsuleToCommunity(c) },
                            publishing = communityPublishing,
                            provenanceRecords = if (expandedCapsuleId == c.id) capsuleProvenance else emptyList()
                        )
                    }
                }
            }
        } else {
            // Tab 2: Cryptographic Verifier
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "PASTE CAPSULE JSON TO AUDIT",
                            style = MaterialTheme.concordance.overline,
                            color = ElectricCyan
                        )
                        Text(
                            text = "Tamper detection: Recomputes the SHA-256 digest over the canonical JSON payload to guarantee inputs and results have not been altered.",
                            style = MaterialTheme.concordance.bodyDense,
                            color = TextSecondary
                        )

                        OutlinedTextField(
                            value = jsonToVerify,
                            onValueChange = {
                                jsonToVerify = it
                                viewModel.clearVerification()
                            },
                            placeholder = { Text("Paste capsule JSON here...", style = MaterialTheme.concordance.caption, color = TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            textStyle = MaterialTheme.concordance.monoId.copy(color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = BorderStrong
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (capsules.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        jsonToVerify = capsules.first().rawJson
                                        viewModel.verifyCapsuleJson(jsonToVerify)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Load Recent", style = MaterialTheme.concordance.buttonSmall)
                                }
                            }

                            Button(
                                onClick = {
                                    viewModel.verifyCapsuleJson(jsonToVerify)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = MidnightDeep
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Verify Hash", style = MaterialTheme.concordance.buttonSmall)
                            }
                        }
                    }
                }

                if (verificationResult != null) {
                    val (isValid, message, computedHash) = verificationResult!!
                    item {
                        val statusColor = if (isValid) NeonEmerald else VividCoral
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusColor.copy(alpha = 0.12f))
                                .border(1.5.dp, statusColor, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (isValid) "INTEGRITY VERIFIED: AUTHENTIC" else "VERIFICATION FAILED: TAMPERED",
                                    style = MaterialTheme.concordance.cardTitle,
                                    color = statusColor
                                )
                            }
                            Text(
                                text = message,
                                style = MaterialTheme.concordance.bodyDense,
                                color = TextPrimary
                            )
                            if (computedHash.isNotEmpty()) {
                                Text(
                                    text = "Computed SHA-256:\n$computedHash",
                                    style = MaterialTheme.concordance.monoId,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (userToast != null) {
        LaunchedEffect(userToast) {
            kotlinx.coroutines.delay(2000)
            userToast = null
        }
    }
}

@Composable
fun CapsuleCard(
    capsule: Capsule,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopyPermalink: () -> Unit,
    onCopyMarkdown: () -> Unit,
    onCopyJson: () -> Unit,
    onDelete: () -> Unit,
    onSyncToCloud: () -> Unit = {},
    onPublish: () -> Unit = {},
    publishing: Boolean = false,
    provenanceRecords: List<Provenance> = emptyList()
) {
    val verdictColor = Color(capsule.verdict.badgeColorHex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SlateCard)
            .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
            .clickable { onToggleExpand() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = capsule.anchorLabel,
                    style = MaterialTheme.concordance.cardTitle,
                    color = TextPrimary
                )
                Text(
                    text = "ID: ${capsule.id} • Created: ${capsule.createdUtc}",
                    style = MaterialTheme.concordance.monoId,
                    color = TextMuted
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(verdictColor.copy(alpha = 0.2f))
                    .border(1.dp, verdictColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = capsule.verdict.label,
                    style = MaterialTheme.concordance.chip,
                    color = verdictColor
                )
            }
        }

        // Resolvable Identifier Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(SlateCardElevated)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "https://${capsule.permalink}",
                    style = MaterialTheme.concordance.monoId,
                    color = ElectricCyan
                )
            }

            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy permalink",
                tint = TextSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onCopyPermalink() }
            )
        }

        // Citable Quote
        Text(
            text = "“${capsule.plainLanguageVerdict}”",
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary
        )

        // SHA-256 Digest Tag
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                tint = NeonEmerald,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "SHA-256: ${capsule.sha256.take(16)}...",
                style = MaterialTheme.concordance.monoId,
                color = TextMuted
            )
        }

        if (capsule.provenanceDigest != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Provenance digest: ${capsule.provenanceDigest.take(16)}... (${capsule.provenanceRecordIds.size} source responses)",
                    style = MaterialTheme.concordance.monoId,
                    color = TextMuted
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Divider(color = SlateCardBorder)

                Text(
                    text = "CITABLE REFERENCE (Formatted for papers & preprints):",
                    style = MaterialTheme.concordance.overline,
                    color = ElectricCyan
                )

                Text(
                    text = capsule.citableArgument,
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SlateCardElevated)
                        .padding(8.dp)
                )

                if (isExpanded && provenanceRecords.isNotEmpty()) {
                    Text(
                        text = "LINKED SOURCE RESPONSES (${provenanceRecords.size}):",
                        style = MaterialTheme.concordance.overline,
                        color = ElectricCyan
                    )
                    provenanceRecords.forEach { p ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(SlateCardElevated)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${p.providerId} • HTTP ${p.httpStatus} • ${p.elapsedMs}ms",
                                style = MaterialTheme.concordance.monoId,
                                color = ElectricCyan
                            )
                            Text(
                                text = "Query: ${p.query}",
                                style = MaterialTheme.concordance.caption,
                                color = TextSecondary,
                                maxLines = 1
                            )
                            Text(
                                text = "SHA-256: ${p.responseSha256.take(24)}… • ${p.retrievedUtc}",
                                style = MaterialTheme.concordance.monoId,
                                color = TextMuted
                            )
                        }
                    }
                }

                Button(
                    onClick = onPublish,
                    enabled = !publishing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = MidnightDeep
                    )
                ) {
                    Text(
                        if (publishing) "Publishing…" else "Publish to community (timestamped)",
                        style = MaterialTheme.concordance.buttonSmall
                    )
                }

                // Two rows, not one: three equal-weight buttons plus the delete
                // icon leave ~95dp each on a 384dp screen, which broke
                // "Copy Markdown" mid-word ("Markdo / wn").
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyMarkdown,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Copy Markdown",
                            style = MaterialTheme.concordance.buttonSmall,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = onCopyJson,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Copy JSON",
                            style = MaterialTheme.concordance.buttonSmall,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onSyncToCloud,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Sync to Cloud",
                            style = MaterialTheme.concordance.buttonSmall,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete capsule",
                            tint = VividCoral,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Maps the manager's raw status colour to a theme-aware, contrast-safe role. */
@Composable
private fun cloudStatusColor(hex: Long): Color = when (hex) {
    0xFF00E676 -> NeonEmerald
    0xFF00E5FF -> ElectricCyan
    else -> WarningAmber
}

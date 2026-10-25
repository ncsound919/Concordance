package com.example.concordance.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.concordance.integration.api.OncoKBAdapter
import com.example.concordance.integration.byok.GateResult
import com.example.concordance.integration.byok.IntegrationGate
import com.example.concordance.integration.byok.KeyStore
import com.example.concordance.integration.byok.LicenseStore
import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.local.LocalLlmStatus
import com.example.concordance.local.LocalModelCatalog
import com.example.concordance.local.LocalModels
import com.example.concordance.local.ModelDownloadStatus
import com.example.concordance.ui.ConcordanceViewModel
import com.example.ui.theme.*
import java.io.File

/**
 * BYOK (bring-your-own-key) settings: license acknowledgments and API keys
 * for gated providers such as OncoKB (ACADEMIC_ONLY).
 */
@Composable
fun ByokSettingsTab(
    viewModel: ConcordanceViewModel,
    modifier: Modifier = Modifier
) {
    val keyStore = viewModel.keyStore
    val licenseStore = viewModel.licenseStore
    // Re-read the keystore whenever a config file has been imported.
    val configRevision by viewModel.configRevision.collectAsState()

    var oncoKey by remember(configRevision) { mutableStateOf(keyStore.getKey("oncokb_api").orEmpty()) }
    var academicAck by remember(configRevision) { mutableStateOf(licenseStore.isAcknowledged(LicenseClass.ACADEMIC_ONLY)) }
    var paidAck by remember(configRevision) { mutableStateOf(licenseStore.isAcknowledged(LicenseClass.PAID)) }
    var kaggleToken by remember(configRevision) { mutableStateOf(keyStore.getKey("kaggle_api").orEmpty()) }
    var cfAccountId by remember(configRevision) { mutableStateOf(keyStore.getKey("cloudflare_account_id").orEmpty()) }
    var cfToken by remember(configRevision) { mutableStateOf(keyStore.getKey("cloudflare_r2").orEmpty()) }
    var cfEndpoint by remember(configRevision) { mutableStateOf(keyStore.getKey("cloudflare_r2_endpoint").orEmpty()) }
    var openaiKey by remember(configRevision) { mutableStateOf(keyStore.getKey("openai_api").orEmpty()) }
    var openrouterKey by remember(configRevision) { mutableStateOf(keyStore.getKey("openrouter_api").orEmpty()) }
    var geminiKey by remember(configRevision) { mutableStateOf(keyStore.getKey("gemini_api").orEmpty()) }
    var mistralKey by remember(configRevision) { mutableStateOf(keyStore.getKey("mistral_api").orEmpty()) }
    var nvidiaKey by remember(configRevision) { mutableStateOf(keyStore.getKey("nvidia_api").orEmpty()) }
    var ollamaUrl by remember(configRevision) { mutableStateOf(keyStore.getKey("ollama_url").orEmpty().ifBlank { "http://10.0.2.2:11434" }) }
    var awsApigwEndpoint by remember(configRevision) { mutableStateOf(keyStore.getKey("aws_apigw_endpoint").orEmpty()) }
    var awsApigwKey by remember(configRevision) { mutableStateOf(keyStore.getKey("aws_apigw_key").orEmpty()) }
    var modelName by remember(configRevision) { mutableStateOf(viewModel.agentConfig.value.modelName.orEmpty()) }

    val oncoGate = IntegrationGate.evaluate(OncoKBAdapter.descriptor, keyStore, licenseStore)
    val isSearching by viewModel.isSearchingCurated.collectAsState()
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (json != null) {
                    viewModel.importConfigFromJson(org.json.JSONObject(json))
                }
            } catch (e: Exception) {
                // handled by viewModel notice
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("BRING-YOUR-OWN-KEY (BYOK) SETTINGS", style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = ElectricCyan)
                Button(
                    onClick = { importLauncher.launch("application/json") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import Config from File", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Gated providers (ACADEMIC_ONLY / PAID) require a license acknowledgment " +
                            "and, where applicable, an API key before they can run.",
                    style = MaterialTheme.concordance.overline,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }

        // On-device models: weights live outside the APK in the app's external files dir.
        item {
            val llmStatus by viewModel.localLlmStatus.collectAsState()
            val downloadStatus by viewModel.modelDownloadStatus.collectAsState()
            val models = remember(configRevision) { viewModel.installedLocalModels() }
            var selected by remember(models) { mutableStateOf(models.firstOrNull()) }
            val kokoroReady = remember(configRevision) { viewModel.localKokoroReady() }
            val freeBytes = remember(configRevision) { viewModel.localFreeSpaceBytes() }
            var pendingDelete by remember { mutableStateOf<File?>(null) }
            val activeDownload = downloadStatus as? ModelDownloadStatus.Downloading

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateCard)
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "ON-DEVICE MODELS",
                    style = MaterialTheme.concordance.overline,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
                Text(
                    "Runs entirely on this phone over loopback — no key, no network.",
                    style = MaterialTheme.concordance.overline,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )

                if (models.isEmpty()) {
                    Text(
                        "No model installed yet. Download one below, or copy a .gguf file to " +
                                "Android/data/${context.packageName}/files/models/ and reopen this tab.",
                        style = MaterialTheme.concordance.bodyDense,
                        color = TextSecondary
                    )
                } else {
                    models.forEach { file ->
                        val running = (llmStatus as? LocalLlmStatus.Ready)?.model == file.name
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selected?.absolutePath == file.absolutePath,
                                onClick = { selected = file }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.concordance.bodyDense, color = TextPrimary)
                                Text(
                                    LocalModels.describe(file),
                                    style = MaterialTheme.concordance.overline,
                                    color = TextSecondary
                                )
                            }
                            if (!running) {
                                TextButton(onClick = { pendingDelete = file }) {
                                    Text("Delete", style = MaterialTheme.concordance.overline, color = WarningGold)
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { selected?.let { viewModel.startLocalModel(it) } },
                        enabled = selected != null && llmStatus !is LocalLlmStatus.Starting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Color.Black
                        )
                    ) { Text("Start", fontWeight = FontWeight.SemiBold) }
                    OutlinedButton(onClick = { viewModel.stopLocalModel() }) { Text("Stop") }
                }

                Text(
                    when (val status = llmStatus) {
                        is LocalLlmStatus.Stopped -> "Stopped"
                        is LocalLlmStatus.Starting -> "Loading ${status.model}…"
                        is LocalLlmStatus.Ready -> "Ready — ${status.model} on 127.0.0.1:${status.port}"
                        is LocalLlmStatus.Failed -> "Failed — ${status.message}"
                    },
                    style = MaterialTheme.concordance.bodyDense,
                    color = when (llmStatus) {
                        is LocalLlmStatus.Ready -> Color(0xFF4CAF50)
                        is LocalLlmStatus.Failed -> Color(0xFFE57373)
                        else -> TextSecondary
                    }
                )

                val decisionModelName = (llmStatus as? LocalLlmStatus.Ready)?.model
                Text(
                    if (decisionModelName != null) {
                        "Decision gate: typed rules + on-device second opinion ($decisionModelName)"
                    } else {
                        "Decision gate: typed rules classifier (offline, always on)"
                    },
                    style = MaterialTheme.concordance.overline,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )

                HorizontalDivider(color = SlateCardBorder)

                Text(
                    "ADD A MODEL",
                    style = MaterialTheme.concordance.overline,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
                Text(
                    "${LocalModels.describeBytes(freeBytes)} free on this phone. " +
                        "Downloads are checked against the publisher's SHA-256 before they are usable; " +
                        "the app never loads a partial file.",
                    style = MaterialTheme.concordance.overline,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )

                LocalModelCatalog.entries.forEach { model ->
                    val file = File(LocalModels.modelsDir(context), model.fileName)
                    val installed = file.isFile
                    val sizeMatches = installed && file.length() == model.sizeBytes
                    val downloading = activeDownload?.takeIf { it.fileName == model.fileName }
                    val failure = (downloadStatus as? ModelDownloadStatus.Failed)
                        ?.takeIf { it.fileName == model.fileName }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            model.displayName,
                            style = MaterialTheme.concordance.bodyDense,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${LocalModels.describeBytes(model.sizeBytes)} · ${model.license} · ${model.source}",
                            style = MaterialTheme.concordance.overline,
                            color = TextSecondary
                        )
                        Text(
                            model.description,
                            style = MaterialTheme.concordance.overline,
                            color = TextMuted,
                            lineHeight = 14.sp
                        )

                        when {
                            downloading != null -> {
                                LinearProgressIndicator(
                                    progress = { downloading.fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = ElectricCyan,
                                    trackColor = SlateCardBorder
                                )
                                Text(
                                    "Downloading ${(downloading.fraction * 100).toInt()}% — " +
                                        "${LocalModels.describeBytes(downloading.bytes)} of " +
                                        LocalModels.describeBytes(downloading.total),
                                    style = MaterialTheme.concordance.overline,
                                    color = TextSecondary
                                )
                                OutlinedButton(onClick = { viewModel.cancelLocalModelDownload() }) {
                                    Text("Cancel download")
                                }
                            }

                            sizeMatches -> {
                                Text(
                                    "Installed · size matches",
                                    style = MaterialTheme.concordance.overline,
                                    color = Color(0xFF4CAF50)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { viewModel.verifyLocalModel(model) }) {
                                        Text("Verify")
                                    }
                                    OutlinedButton(onClick = { pendingDelete = file }) {
                                        Text("Delete")
                                    }
                                }
                            }

                            installed -> {
                                Text(
                                    "A different or incomplete file already uses this name.",
                                    style = MaterialTheme.concordance.overline,
                                    color = WarningGold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { pendingDelete = file }) {
                                        Text("Delete")
                                    }
                                    Button(
                                        onClick = { viewModel.downloadLocalModel(model) },
                                        enabled = activeDownload == null,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricCyan,
                                            contentColor = Color.Black
                                        )
                                    ) { Text("Download again") }
                                }
                            }

                            else -> {
                                Button(
                                    onClick = { viewModel.downloadLocalModel(model) },
                                    enabled = activeDownload == null,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = Color.Black
                                    )
                                ) { Text("Download") }
                            }
                        }

                        failure?.let {
                            Text(
                                "Download failed: ${it.message}",
                                style = MaterialTheme.concordance.overline,
                                color = Color(0xFFE57373)
                            )
                        }
                    }
                }

                pendingDelete?.let { file ->
                    AlertDialog(
                        onDismissRequest = { pendingDelete = null },
                        title = { Text("Delete this model?") },
                        text = {
                            Text(
                                "${file.name} will be removed from this phone. " +
                                    "You can download it again from this screen."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.deleteLocalModel(file)
                                    pendingDelete = null
                                }
                            ) { Text("Delete", color = Color(0xFFE57373)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                        }
                    )
                }

                HorizontalDivider(color = SlateCardBorder)

                Text(
                    "VOICE — KOKORO ${LocalModels.DEFAULT_VOICE_NAME.uppercase()}",
                    style = MaterialTheme.concordance.overline,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
                Text(
                    if (kokoroReady) {
                        "Voice files installed in Android/data/${context.packageName}/files/kokoro/."
                    } else {
                        "Voice files missing: copy model.onnx, voices.bin, tokens.txt and espeak-ng-data/ " +
                                "to Android/data/${context.packageName}/files/kokoro/."
                    },
                    style = MaterialTheme.concordance.bodyDense,
                    color = if (kokoroReady) TextSecondary else Color(0xFFE57373)
                )
                OutlinedButton(onClick = { viewModel.testLocalVoice() }) { Text("Test voice") }
            }
        }

        // License acknowledgments
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
                Text("LICENSE ACKNOWLEDGMENTS", style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = ElectricCyan)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = academicAck,
                        onCheckedChange = {
                            academicAck = it
                            if (it) viewModel.acknowledgeLicense(LicenseClass.ACADEMIC_ONLY)
                            else viewModel.revokeLicense(LicenseClass.ACADEMIC_ONLY)
                        }
                    )
                    Text("I acknowledge ACADEMIC_ONLY use (research, non-clinical).", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = paidAck,
                        onCheckedChange = {
                            paidAck = it
                            if (it) viewModel.acknowledgeLicense(LicenseClass.PAID)
                            else viewModel.revokeLicense(LicenseClass.PAID)
                        }
                    )
                    Text("I have a valid PAID license for gated providers.", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
                }
            }
        }

        // OncoKB key
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Text("OncoKB API TOKEN", style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = ElectricCyan)
                }
                Text(
                    "Required for gene-level annotations. Evidence levels are public. " +
                            "Get a free academic token at oncokb.org.",
                    style = MaterialTheme.concordance.overline,
                    color = TextSecondary,
                    lineHeight = 14.sp
                )
                OutlinedTextField(
                    value = oncoKey,
                    onValueChange = { oncoKey = it },
                    placeholder = { Text("paste token here", style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Button(
                    onClick = { viewModel.setApiKey("oncokb_api", oncoKey) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save OncoKB Token", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    Text(
                        text = when (oncoGate) {
                            is GateResult.Allowed -> "Status: allowed"
                            is GateResult.NeedsLicense -> "Status: needs license acknowledgment"
                            is GateResult.NeedsKey -> "Status: needs API token"
                            is GateResult.Blocked -> "Status: blocked"
                        },
                        style = MaterialTheme.concordance.bodyDense,
                        color = when (oncoGate) {
                            is GateResult.Allowed -> NeonEmerald
                            else -> WarningGold
                        }
                    )
                }
            }
        }

        // OncoKB fetch buttons
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.searchOncoKBLevels() },
                    enabled = !isSearching,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("OncoKB Levels", style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { viewModel.searchOncoKBGene("ESR1") },
                    enabled = !isSearching,
                    colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = MidnightDeep),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("OncoKB Gene ESR1", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Runtime tuning
        item {
            var threshold by remember { mutableStateOf(viewModel.cbThreshold.value.toString()) }
            var cooldown by remember { mutableStateOf(viewModel.cbCooldownSeconds.value.toString()) }
            var cacheTtl by remember { mutableStateOf(viewModel.cacheTtlMinutes.value.toString()) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateCard)
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("RUNTIME TUNING", style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = ElectricCyan)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = threshold,
                        onValueChange = { threshold = it },
                        label = { Text("CB threshold", style = MaterialTheme.concordance.overline) },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.concordance.bodyDense
                    )
                    OutlinedTextField(
                        value = cooldown,
                        onValueChange = { cooldown = it },
                        label = { Text("cooldown (s)", style = MaterialTheme.concordance.bodyDense) },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.concordance.bodyDense
                    )
                    OutlinedTextField(
                        value = cacheTtl,
                        onValueChange = { cacheTtl = it },
                        label = { Text("cache TTL (min)", style = MaterialTheme.concordance.bodyDense) },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.concordance.bodyDense
                    )
                }
                Button(
                    onClick = {
                        threshold.toIntOrNull()?.let { viewModel.setFailureThreshold(it) }
                        cooldown.toIntOrNull()?.let { viewModel.setCooldownSeconds(it) }
                        cacheTtl.toIntOrNull()?.let { viewModel.setCacheTtlMinutes(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Runtime Tuning", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
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
                Text("LLM PROVIDERS & MODEL SELECTION", style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = ElectricCyan)
                Text(
                    "Free tiers rotate models often. Type the model name you want (e.g. a stealth/free model id from OpenRouter or OpenCode).",
                    style = MaterialTheme.concordance.overline,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = openaiKey,
                    onValueChange = { openaiKey = it },
                    label = { Text("OpenCode API key", style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = openrouterKey,
                    onValueChange = { openrouterKey = it },
                    label = { Text("OpenRouter API key", style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    label = { Text("Gemini API key", style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = mistralKey,
                    onValueChange = { mistralKey = it },
                    label = { Text("Mistral API key", style = MaterialTheme.concordance.overline) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.overline,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = nvidiaKey,
                    onValueChange = { nvidiaKey = it },
                    label = { Text("NVIDIA NIM API key", style = MaterialTheme.concordance.overline) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.overline,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = ollamaUrl,
                    onValueChange = { ollamaUrl = it },
                    label = { Text("Ollama base URL", style = MaterialTheme.concordance.overline) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense
                )
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model name (free-text)", style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense
                )
                // Quick-pick suggestions (tap to populate the field)
                val suggestions = com.example.concordance.integration.agent.FreeTierModels.openrouter +
                    com.example.concordance.integration.agent.FreeTierModels.gemini +
                    com.example.concordance.integration.agent.FreeTierModels.nvidia
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(suggestions.take(12)) { m ->
                        FilterChip(
                            selected = modelName == m,
                            onClick = { modelName = m },
                            label = { Text(m, style = MaterialTheme.concordance.bodyDense) }
                        )
                    }
                }
                OutlinedTextField(
                    value = awsApigwEndpoint,
                    onValueChange = { awsApigwEndpoint = it },
                    label = { Text("AWS API Gateway endpoint URL", style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense
                )
                OutlinedTextField(
                    value = awsApigwKey,
                    onValueChange = { awsApigwKey = it },
                    label = { Text("AWS API Gateway API key", style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense
                )
                Button(
                    onClick = {
                        keyStore.setKey("openai_api", openaiKey.ifBlank { null })
                        keyStore.setKey("openrouter_api", openrouterKey.ifBlank { null })
                        keyStore.setKey("gemini_api", geminiKey.ifBlank { null })
                        keyStore.setKey("mistral_api", mistralKey.ifBlank { null })
                        keyStore.setKey("nvidia_api", nvidiaKey.ifBlank { null })
                        keyStore.setKey("ollama_url", ollamaUrl.ifBlank { null })
                        keyStore.setKey("aws_apigw_endpoint", awsApigwEndpoint.ifBlank { null })
                        keyStore.setKey("aws_apigw_key", awsApigwKey.ifBlank { null })
                        viewModel.updateAgentConfig(
                            baseUrl = ollamaUrl.ifBlank { "http://10.0.2.2:8000" },
                            apiKey = openaiKey.ifBlank { openrouterKey.ifBlank { null } },
                            modelName = modelName.ifBlank { null }
                        )
                        viewModel.setAwsApiGatewayConfig(awsApigwEndpoint.ifBlank { null }, awsApigwKey.ifBlank { null })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save LLM & API Gateway Settings", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold)
                }
            }
        }

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
                Text("SERVICE TOKENS (Kaggle / Cloudflare R2)", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold, color = ElectricCyan)
                OutlinedTextField(
                    value = kaggleToken,
                    onValueChange = { kaggleToken = it },
                    label = { Text("Kaggle API token", style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = cfAccountId,
                    onValueChange = { cfAccountId = it },
                    label = { Text("Cloudflare account ID", style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense
                )
                OutlinedTextField(
                    value = cfToken,
                    onValueChange = { cfToken = it },
                    label = { Text("Cloudflare R2 API token", style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = cfEndpoint,
                    onValueChange = { cfEndpoint = it },
                    label = { Text("Cloudflare R2 endpoint (optional)", style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense
                )
                Button(
                    onClick = {
                        viewModel.setKaggleToken(kaggleToken.ifBlank { null })
                        viewModel.setCloudflareConfig(
                            accountId = cfAccountId.ifBlank { null },
                            token = cfToken.ifBlank { null },
                            endpoint = cfEndpoint.ifBlank { null }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Service Tokens", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

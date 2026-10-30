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
import com.example.concordance.local.LocalModels
import com.example.concordance.ui.ConcordanceViewModel
import com.example.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R

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
    val configRevision by viewModel.configRevision.collectAsStateWithLifecycle()

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
    val isSearching by viewModel.isSearchingCurated.collectAsStateWithLifecycle()
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
                Text(stringResource(R.string.auto_a9a05a8644b5), style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = ElectricCyan)
                Button(
                    onClick = { importLauncher.launch("application/json") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.auto_943be8b6ca44), fontWeight = FontWeight.SemiBold)
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
            val llmStatus by viewModel.localLlmStatus.collectAsStateWithLifecycle()
            val models = remember(configRevision) { viewModel.installedLocalModels() }
            var selected by remember(models) { mutableStateOf(models.firstOrNull()) }
            val kokoroReady = remember(configRevision) { viewModel.localKokoroReady() }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateCard)
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.auto_a02afc084057),
                    style = MaterialTheme.concordance.overline,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
                Text(stringResource(R.string.auto_03dfd1b8e6b1),
                    style = MaterialTheme.concordance.overline,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )

                if (models.isEmpty()) {
                    Text(
                        "No model installed. Copy a .gguf file to " +
                                "Android/data/${context.packageName}/files/models/ and reopen this tab.",
                        style = MaterialTheme.concordance.bodyDense,
                        color = TextSecondary
                    )
                } else {
                    models.forEach { file ->
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
                    ) { Text(stringResource(R.string.auto_952f375412e8), fontWeight = FontWeight.SemiBold) }
                    OutlinedButton(onClick = { viewModel.stopLocalModel() }) { Text(stringResource(R.string.auto_9e253470c876)) }
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
                OutlinedButton(onClick = { viewModel.testLocalVoice() }) { Text(stringResource(R.string.auto_24e8f7518264)) }
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
                Text(stringResource(R.string.auto_2715273f328f), style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = ElectricCyan)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = academicAck,
                        onCheckedChange = {
                            academicAck = it
                            if (it) viewModel.acknowledgeLicense(LicenseClass.ACADEMIC_ONLY)
                            else viewModel.revokeLicense(LicenseClass.ACADEMIC_ONLY)
                        }
                    )
                    Text(stringResource(R.string.auto_95d911cc279d), style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
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
                    Text(stringResource(R.string.auto_af4be3374624), style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
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
                    Text(stringResource(R.string.auto_5deed7c7eff3), style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = ElectricCyan)
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
                    placeholder = { Text(stringResource(R.string.auto_69c00916ca5e), style = MaterialTheme.concordance.bodyDense) },
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
                    Text(stringResource(R.string.auto_4f585f7198cb), style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
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
                    Text(stringResource(R.string.auto_6b2628612bd2), style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold)
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
                    Text(stringResource(R.string.auto_ef952cee5288), style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
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
                Text(stringResource(R.string.auto_aa0dc73d1bb1), style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = ElectricCyan)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = threshold,
                        onValueChange = { threshold = it },
                        label = { Text(stringResource(R.string.auto_1eecb8b67a3c), style = MaterialTheme.concordance.overline) },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.concordance.bodyDense
                    )
                    OutlinedTextField(
                        value = cooldown,
                        onValueChange = { cooldown = it },
                        label = { Text(stringResource(R.string.auto_178f8bb519b1), style = MaterialTheme.concordance.bodyDense) },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.concordance.bodyDense
                    )
                    OutlinedTextField(
                        value = cacheTtl,
                        onValueChange = { cacheTtl = it },
                        label = { Text(stringResource(R.string.auto_07648ef62d86), style = MaterialTheme.concordance.bodyDense) },
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
                    Text(stringResource(R.string.auto_aeea9b546867), style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
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
                Text(stringResource(R.string.auto_01fbd7021bd4), style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = ElectricCyan)
                Text(stringResource(R.string.auto_9f8046bff005),
                    style = MaterialTheme.concordance.overline,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = openaiKey,
                    onValueChange = { openaiKey = it },
                    label = { Text(stringResource(R.string.auto_bb5bfbd8f300), style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = openrouterKey,
                    onValueChange = { openrouterKey = it },
                    label = { Text(stringResource(R.string.auto_bba2d9fb0bb9), style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    label = { Text(stringResource(R.string.auto_ef66ea16d799), style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = mistralKey,
                    onValueChange = { mistralKey = it },
                    label = { Text(stringResource(R.string.auto_e45f48cd9d0b), style = MaterialTheme.concordance.overline) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.overline,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = nvidiaKey,
                    onValueChange = { nvidiaKey = it },
                    label = { Text(stringResource(R.string.auto_bb152ddca21f), style = MaterialTheme.concordance.overline) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.overline,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = ollamaUrl,
                    onValueChange = { ollamaUrl = it },
                    label = { Text(stringResource(R.string.auto_450ac3c5d77b), style = MaterialTheme.concordance.overline) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense
                )
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text(stringResource(R.string.auto_33559ea9539b), style = MaterialTheme.concordance.bodyDense) },
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
                    label = { Text(stringResource(R.string.auto_311c3c363e77), style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense
                )
                OutlinedTextField(
                    value = awsApigwKey,
                    onValueChange = { awsApigwKey = it },
                    label = { Text(stringResource(R.string.auto_ec6d38c91128), style = MaterialTheme.concordance.bodyDense) },
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
                    Text(stringResource(R.string.auto_916cc5cd5c73), style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold)
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
                Text(stringResource(R.string.auto_5b7ab8114f32), style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold, color = ElectricCyan)
                OutlinedTextField(
                    value = kaggleToken,
                    onValueChange = { kaggleToken = it },
                    label = { Text(stringResource(R.string.auto_13de23019d14), style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = cfAccountId,
                    onValueChange = { cfAccountId = it },
                    label = { Text(stringResource(R.string.auto_b0ef4d9b2ef4), style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense
                )
                OutlinedTextField(
                    value = cfToken,
                    onValueChange = { cfToken = it },
                    label = { Text(stringResource(R.string.auto_fc4324669d19), style = MaterialTheme.concordance.bodyDense) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.concordance.bodyDense,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = cfEndpoint,
                    onValueChange = { cfEndpoint = it },
                    label = { Text(stringResource(R.string.auto_6054bbea7302), style = MaterialTheme.concordance.bodyDense) },
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
                    Text(stringResource(R.string.auto_cd76d3308a6e), style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

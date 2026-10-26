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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.concordance.integration.api.Provenance
import com.example.concordance.integration.aws.AwsSyncStatus
import com.example.concordance.integration.aws.S3SyncedObject
import com.example.concordance.integration.clinicaltrials.ClinicalTrialStudy
import com.example.concordance.integration.kaggle.KaggleComputeJob
import com.example.concordance.integration.kaggle.KaggleDataset
import com.example.concordance.integration.kaggle.KaggleJobStatus
import com.example.concordance.integration.knowledge.IndraContradictions
import com.example.concordance.integration.knowledge.IndraKnowledge
import com.example.concordance.integration.knowledge.IndraStatement
import com.example.concordance.integration.pubchem.PubChemCompound
import com.example.concordance.integration.rotation.ApiProvider
import com.example.concordance.integration.rotation.ProviderStatus
import com.example.concordance.integration.rotation.RotationStrategy
import com.example.concordance.integration.science.AnalysisEngine
import com.example.concordance.integration.science.PaperAnalysisOutput
import com.example.concordance.integration.science.SciencePaper
import com.example.concordance.ui.ConcordanceViewModel
import com.example.concordance.ui.components.AsyncJobQueueTab
import com.example.concordance.ui.components.ByokSettingsTab
import com.example.concordance.ui.components.CuratedEvidenceTab
import com.example.concordance.ui.components.EmptyState
import com.example.concordance.ui.components.ModelLearningTab
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun IntegrationsScreen(
    viewModel: ConcordanceViewModel,
    onNavigateToAdjudication: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // API Rotation State
    val apiProviders by viewModel.apiProviders.collectAsState()
    val rotationStrategy by viewModel.rotationStrategy.collectAsState()

    // Kaggle State
    val kaggleDatasets by viewModel.kaggleDatasets.collectAsState()
    val kaggleJobs by viewModel.kaggleJobs.collectAsState()

    // AWS State
    val awsStatus by viewModel.awsSyncStatus.collectAsState()
    val awsRegion by viewModel.awsRegion.collectAsState()
    val awsBucket by viewModel.awsBucketName.collectAsState()
    val awsEndpoint by viewModel.awsEndpoint.collectAsState()
    var awsFormOpen by remember { mutableStateOf(false) }
    var awsAccessKey by remember { mutableStateOf("") }
    var awsSecretKey by remember { mutableStateOf("") }
    var awsSessionToken by remember { mutableStateOf("") }
    var awsRegionInput by remember(awsRegion) { mutableStateOf(awsRegion) }
    var awsBucketInput by remember(awsBucket) { mutableStateOf(awsBucket) }
    var awsEndpointInput by remember(awsEndpoint) { mutableStateOf(awsEndpoint ?: "") }
    val syncedObjects by viewModel.syncedS3Objects.collectAsState()
    val savedCapsules by viewModel.savedCapsules.collectAsState()

    // Science Paper State
    val sciencePapers by viewModel.sciencePapers.collectAsState()
    val paperEngine by viewModel.paperAnalysisEngine.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingPaper.collectAsState()
    val latestAnalysis by viewModel.latestPaperAnalysis.collectAsState()
    val isLoadingMorePapers by viewModel.isLoadingMorePapers.collectAsState()

    // INDRA prior knowledge (public literature via the agent)
    val indraKnowledge by viewModel.indraKnowledge.collectAsState()
    val indraContradictions by viewModel.indraContradictions.collectAsState()
    val indraLoading by viewModel.indraLoading.collectAsState()
    val indraError by viewModel.indraError.collectAsState()
    var indraSubject by remember { mutableStateOf("") }
    var indraObject by remember { mutableStateOf("") }

    // PubChem State
    val pubChemCompounds by viewModel.pubChemCompounds.collectAsState()

    // Clinical Trials State
    val clinicalTrials by viewModel.clinicalTrials.collectAsState()
    val isLoadingMoreTrials by viewModel.isLoadingMoreTrials.collectAsState()

    // Live API provenance log
    val provenanceRecords by viewModel.provenanceRecords.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    fun copyText(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        toastMessage = "Copied to clipboard!"
    }

    /**
     * Token-based local filter. Live API results are already server-filtered, so
     * requiring the exact phrase would hide them after a multi-word query.
     */
    fun matchesQuery(vararg fields: String): Boolean {
        val q = searchQuery.trim()
        if (q.isEmpty()) return true
        val tokens = q.lowercase(Locale.US).split(Regex("\\s+")).filter { it.length >= 2 }
        if (tokens.isEmpty()) return true
        val haystack = fields.joinToString(" ").lowercase(Locale.US)
        return tokens.any { haystack.contains(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightDeep)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "SCIENTIFIC APIS & COMPUTE PLATFORM",
            style = MaterialTheme.concordance.overline,
            fontWeight = FontWeight.Bold,
            color = ElectricCyan,
            letterSpacing = 1.sp
        )
        Text(
            text = "Free open APIs: NCBI, arXiv, PubChem, ClinicalTrials.gov, CIViC, cBioPortal, Kaggle Compute, AWS Vault, and Local Model.",
            style = MaterialTheme.concordance.overline,
            color = TextSecondary,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = SlateCard,
            contentColor = ElectricCyan,
            edgePadding = 0.dp,
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
                text = { Text("Papers & arXiv", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("PubChem Drugs", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Clinical Trials", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Async Job Queue", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 4,
                onClick = { selectedTab = 4 },
                text = { Text("Imaging", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 5,
                onClick = { selectedTab = 5 },
                text = { Text("Kaggle Compute", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 6,
                onClick = { selectedTab = 6 },
                text = { Text("AWS S3 Vault", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 7,
                onClick = { selectedTab = 7 },
                text = { Text("API Rotation", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 8,
                onClick = { selectedTab = 8 },
                text = { Text("Variant Evidence", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 9,
                onClick = { selectedTab = 9 },
                text = { Text("Settings & Keys", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 10,
                onClick = { selectedTab = 10 },
                text = { Text("Self-Learning", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> {
                // TAB 0: SCIENCE PAPERS, ARXIV, PUBMED, OPENALEX
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    // Engine Selector: Local Micro-Model vs Gemini API
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SlateCard)
                                .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "EXTRACTION INFERENCE ENGINE",
                                style = MaterialTheme.concordance.overline,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = paperEngine == AnalysisEngine.LOCAL_RULE_EXTRACTOR,
                                    onClick = { viewModel.setPaperAnalysisEngine(AnalysisEngine.LOCAL_RULE_EXTRACTOR) },
                                    label = { Text("Small Local Model (0ms, Offline)", style = MaterialTheme.concordance.bodyDense) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = paperEngine == AnalysisEngine.LOCAL_LLM,
                                    onClick = { viewModel.setPaperAnalysisEngine(AnalysisEngine.LOCAL_LLM) },
                                    label = { Text("On-device LLM (llama.cpp)", style = MaterialTheme.concordance.bodyDense) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = "Both engines run on this phone. Cloud LLM keys saved in BYOK " +
                                    "are never used to analyse papers or claims.",
                                style = MaterialTheme.concordance.caption,
                                color = TextMuted
                            )
                        }
                    }

                    // Latest Analysis Output Card (if available)
                    if (latestAnalysis != null) {
                        val analysis = latestAnalysis!!
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeonEmerald.copy(alpha = 0.1f))
                                    .border(1.dp, NeonEmerald, RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "EXTRACTED GENOMIC CLAIM",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.concordance.overline,
                                        color = NeonEmerald
                                    )
                                    Text(
                                        text = "${analysis.engineUsed.badge} (${analysis.executionTimeMs}ms)",
                                        style = MaterialTheme.concordance.monoId,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Text(
                                    text = analysis.extraction.qualitativeSummary,
                                    style = MaterialTheme.concordance.monoId,
                                    color = TextPrimary,
                                    lineHeight = 17.sp
                                )

                                // Typed review gate: rules by default, on-device
                                // model only when it is running and confident.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (analysis.review.needsHuman) "REVIEW: HUMAN CHECK" else "REVIEW: AUTO-ACCEPT",
                                        style = MaterialTheme.concordance.overline,
                                        fontWeight = FontWeight.Bold,
                                        color = if (analysis.review.needsHuman) WarningAmber else NeonEmerald
                                    )
                                    Text(
                                        text = analysis.review.backend.label,
                                        style = MaterialTheme.concordance.monoId,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = analysis.review.reason,
                                    style = MaterialTheme.concordance.monoId,
                                    color = TextSecondary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (analysis.extraction.brainEvents != null) {
                                        DataMetricPill("Brain Events", "${analysis.extraction.brainEvents}/${analysis.extraction.brainTotal}")
                                    }
                                    if (analysis.extraction.otherEvents != null) {
                                        DataMetricPill("Other Events", "${analysis.extraction.otherEvents}/${analysis.extraction.otherTotal}")
                                    }
                                    if (analysis.extraction.oddsRatio != null) {
                                        DataMetricPill("Reported OR", "${analysis.extraction.oddsRatio}")
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.importAnalyzedPaperAsClaim(analysis)
                                        viewModel.selectAnchor(analysis.convertedAnchor)
                                        onNavigateToAdjudication()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = MidnightDeep),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Import as Living Claim & Adjudicate Now", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Prior knowledge (INDRA): public literature already on record
                    item {
                        IndraCard(
                            subject = indraSubject,
                            onSubjectChange = { indraSubject = it },
                            partner = indraObject,
                            onPartnerChange = { indraObject = it },
                            loading = indraLoading,
                            knowledge = indraKnowledge,
                            contradictions = indraContradictions,
                            error = indraError,
                            onLookup = { viewModel.lookupIndra(indraSubject, indraObject) },
                            onClear = { viewModel.clearIndra() }
                        )
                    }

                    // Search Papers TextField
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search PubMed, arXiv, OpenAlex, Crossref (e.g. ESR1, 2603.09142)...", style = MaterialTheme.concordance.bodyDense) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.concordance.bodyDense
                        )
                    }

                    // Live API fan-out button
                    item {
                        Button(
                            onClick = { viewModel.searchSciencePapers(searchQuery) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Search Live APIs (Europe PMC + OpenAlex)", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Paper Cards
                    val filteredPapers = sciencePapers.filter {
                        matchesQuery(it.title, it.authors, it.abstractText, it.id, it.pmid.orEmpty())
                    }

                    if (filteredPapers.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No papers found",
                                message = "Run a live literature search or widen your query.",
                            )
                        }
                    }

                    items(filteredPapers) { paper ->
                        SciencePaperCard(
                            paper = paper,
                            isAnalyzing = isAnalyzing,
                            onAnalyze = { viewModel.analyzeSciencePaper(paper) },
                            onCopyCitation = { copyText("Paper Citation", "${paper.authors} (${paper.year}). ${paper.title}. ${paper.journal}. DOI: ${paper.doi}") }
                        )
                    }

                    if (searchQuery.isNotBlank()) {
                        item {
                            OutlinedButton(
                                onClick = { viewModel.loadMoreSciencePapers(searchQuery) },
                                enabled = !isLoadingMorePapers,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (isLoadingMorePapers) "Loading..." else "Load more papers", style = MaterialTheme.concordance.buttonSmall)
                            }
                        }
                    }
                }
            }

            1 -> {
                // TAB 1: PUBCHEM DRUG & BIOCHEMISTRY TARGETS
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
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "NCBI PUBCHEM · CURATED BENCH + PUG-REST LOOKUP",
                                style = MaterialTheme.concordance.overline,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                            Text(
                                text = "Explore therapeutic compounds (SERDs, SERMs, CDK4/6i) targeting ESR1 resistance mutations and their Blood-Brain Barrier (BBB) penetrance profiles.",
                                style = MaterialTheme.concordance.bodyDense,
                                color = TextSecondary
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search PubChem compound (e.g. Elacestrant, Camizestrant, 54726192)...", style = MaterialTheme.concordance.bodyDense) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.concordance.bodyDense
                        )
                    }

                    val filteredCompounds = pubChemCompounds.filter {
                        searchQuery.isEmpty() ||
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                it.cid.toString().contains(searchQuery) ||
                                it.associatedMutations.any { m -> m.contains(searchQuery, ignoreCase = true) }
                    }

                    if (filteredCompounds.isEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                EmptyState(
                                    title = "No compound in the curated bench",
                                    message = "Look it up live in PubChem, or search an ESR1 compound, gene or mutation above.",
                                )
                                if (searchQuery.isNotBlank()) {
                                    Button(
                                        onClick = { viewModel.queryPubChem(searchQuery) },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Look up \"${searchQuery.take(32)}\" on PubChem",
                                            style = MaterialTheme.concordance.buttonSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(filteredCompounds) { compound ->
                        PubChemCompoundCard(
                            compound = compound,
                            onCopyUrl = { copyText("PubChem Link", compound.pubchemUrl) }
                        )
                    }
                }
            }

            2 -> {
                // TAB 2: CLINICALTRIALS.GOV REST API
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
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "CLINICALTRIALS.GOV V2 REST API TRACKER",
                                style = MaterialTheme.concordance.overline,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                            Text(
                                text = "Monitor clinical studies evaluating brain metastases, CNS recurrence, and serial plasma ctDNA tracking for ESR1 resistance mutations.",
                                style = MaterialTheme.concordance.bodyDense,
                                color = TextSecondary
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search studies (e.g. EMERALD, SERENA, NCT03778931)...", style = MaterialTheme.concordance.bodyDense) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.concordance.bodyDense
                        )
                    }

                    // Live ClinicalTrials.gov v2 query
                    item {
                        Button(
                            onClick = { viewModel.queryClinicalTrials(searchQuery) },
                            colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = MidnightDeep),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Query Live ClinicalTrials.gov v2 API", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    val filteredTrials = clinicalTrials.filter {
                        matchesQuery(
                            it.briefTitle,
                            it.nctId,
                            it.sponsor,
                            it.conditions.joinToString(" "),
                            it.interventions.joinToString(" ")
                        )
                    }

                    if (filteredTrials.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No trials found",
                                message = "Try a different condition, intervention or NCT id.",
                            )
                        }
                    }

                    items(filteredTrials) { trial ->
                        ClinicalTrialCard(
                            study = trial,
                            onCopyNct = { copyText("ClinicalTrials.gov URL", trial.url) }
                        )
                    }

                    if (searchQuery.isNotBlank()) {
                        item {
                            OutlinedButton(
                                onClick = { viewModel.loadMoreClinicalTrials(searchQuery) },
                                enabled = !isLoadingMoreTrials,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (isLoadingMoreTrials) "Loading..." else "Load more studies", style = MaterialTheme.concordance.buttonSmall)
                            }
                        }
                    }
                }
            }

            3 -> {
                // TAB 3: ASYNC JOB QUEUE & WORKERS
                AsyncJobQueueTab(
                    viewModel = viewModel,
                    onCopyText = { label, text -> copyText(label, text) },
                    modifier = Modifier.weight(1f)
                )
            }

            4 -> {
                // TAB 4: IMAGING & TABLE EXTRACTION
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Imaging System", style = MaterialTheme.concordance.metricValue, fontWeight = FontWeight.Bold)
                    Text("Turn scans into validated claim rows.", style = MaterialTheme.concordance.bodyDense, color = TextSecondary)
                }
            }

            5 -> {
                // TAB 5: KAGGLE COMPUTE & DATASET INGESTION
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
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "REMOTE KAGGLE COMPUTE CLUSTER",
                                style = MaterialTheme.concordance.overline,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                            Text(
                                text = "Execute heavy statistical kernels on Kaggle compute kernels to audit claim robustness.",
                                style = MaterialTheme.concordance.bodyDense,
                                color = TextSecondary
                            )

                            Text(
                                text = "Kernel submission is not enabled in this build: no Kaggle credentials are wired, so the app submits nothing. Run the permutation and bootstrap checks locally instead.",
                                style = MaterialTheme.concordance.bodyDense,
                                color = WarningAmber
                            )
                        }
                    }

                    // Running / Completed Kaggle Jobs
                    if (kaggleJobs.isNotEmpty()) {
                        item {
                            Text(
                                text = "DISPATCHED KAGGLE KERNELS",
                                style = MaterialTheme.concordance.overline,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                        }
                        items(kaggleJobs) { job ->
                            KaggleJobCard(job)
                        }
                    }

                    // Kaggle Datasets for Ingestion
                    item {
                        Text(
                            text = "AVAILABLE KAGGLE ONCOLOGY DATASETS",
                            style = MaterialTheme.concordance.overline,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                    }

                    items(kaggleDatasets) { dataset ->
                        KaggleDatasetCard(
                            dataset = dataset,
                            onIngest = {
                                viewModel.ingestKaggleDataset(dataset)
                                onNavigateToAdjudication()
                            }
                        )
                    }
                }
            }

            6 -> {
                // TAB 6: AWS S3 CLOUD VAULT
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
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { awsFormOpen = !awsFormOpen },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AWS CREDENTIALS",
                                    style = MaterialTheme.concordance.overline,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = if (awsFormOpen) "Hide" else "Configure",
                                    style = MaterialTheme.concordance.bodyDense,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = "Temporary STS credentials are supported: paste the session token " +
                                    "alongside the access key. Long-lived IAM user keys work, but a " +
                                    "session token is the safer option on a phone.",
                                style = MaterialTheme.concordance.bodyDense,
                                color = TextMuted,
                                lineHeight = 16.sp
                            )
                            AnimatedVisibility(visible = awsFormOpen) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = awsAccessKey,
                                        onValueChange = { awsAccessKey = it },
                                        label = { Text("Access key ID") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = awsSecretKey,
                                        onValueChange = { awsSecretKey = it },
                                        label = { Text("Secret access key") },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = awsSessionToken,
                                        onValueChange = { awsSessionToken = it },
                                        label = { Text("Session token (STS, optional)") },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = awsRegionInput,
                                        onValueChange = { awsRegionInput = it },
                                        label = { Text("Region") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = awsBucketInput,
                                        onValueChange = { awsBucketInput = it },
                                        label = { Text("Bucket") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = awsEndpointInput,
                                        onValueChange = { awsEndpointInput = it },
                                        label = { Text("Custom endpoint (R2/MinIO, optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.setAwsConfig(
                                                accessKey = awsAccessKey.ifBlank { null },
                                                secretKey = awsSecretKey.ifBlank { null },
                                                region = awsRegionInput.ifBlank { null },
                                                bucket = awsBucketInput.ifBlank { null },
                                                endpoint = awsEndpointInput.ifBlank { null },
                                                sessionToken = awsSessionToken.ifBlank { null }
                                            )
                                            awsAccessKey = ""
                                            awsSecretKey = ""
                                            awsSessionToken = ""
                                            awsFormOpen = false
                                        },
                                        enabled = awsAccessKey.isNotBlank() && awsSecretKey.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricCyan,
                                            contentColor = MidnightDeep
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "Save credentials",
                                            style = MaterialTheme.concordance.bodyDense,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
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
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AWS S3 CLOUD VAULT",
                                    style = MaterialTheme.concordance.overline,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = awsStatus.label,
                                    style = MaterialTheme.concordance.overline,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(awsStatus.colorHex)
                                )
                            }

                            Text(
                                text = "Bucket: s3://$awsBucket | Region: $awsRegion",
                                style = MaterialTheme.concordance.monoId,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )

                            if (savedCapsules.isNotEmpty()) {
                                Button(
                                    onClick = { viewModel.syncCapsuleToAws(savedCapsules.first()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sync Latest Capsule (${savedCapsules.first().id}) to S3 Vault", style = MaterialTheme.concordance.bodyDense, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (syncedObjects.isEmpty()) {
                        item {
                            EmptyState(
                                title = "Nothing synced to S3 yet",
                                message = "Tap 'Sync Latest Capsule' above to archive a capsule.",
                            )
                        }
                    } else {
                        items(syncedObjects) { obj ->
                            S3ObjectCard(
                                obj = obj,
                                onCopyUrl = { copyText("S3 Presigned URL", obj.presignedUrl) }
                            )
                        }
                    }
                }
            }

            7 -> {
                // TAB 7: API ROTATION POOL
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
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "ACTIVE ROTATION STRATEGY",
                                style = MaterialTheme.concordance.overline,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RotationStrategy.entries.forEach { st ->
                                    val isSelected = rotationStrategy == st
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setRotationStrategy(st) },
                                        label = { Text(st.label, style = MaterialTheme.concordance.bodyDense) }
                                    )
                                }
                            }
                            Text(
                                text = rotationStrategy.description,
                                style = MaterialTheme.concordance.bodyDense,
                                color = TextSecondary
                            )
                        }
                    }

                    items(apiProviders) { provider ->
                        ApiProviderCard(
                            provider = provider,
                            onToggle = { viewModel.toggleApiProvider(provider.id) }
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LIVE REQUEST PROVENANCE (${provenanceRecords.size})",
                                style = MaterialTheme.concordance.bodyDense,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                            if (provenanceRecords.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearProvenanceLog() }) {
                                    Text("Clear", style = MaterialTheme.concordance.buttonSmall, color = TextMuted)
                                }
                            }
                        }
                    }

                    if (provenanceRecords.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No live requests logged yet",
                                message = "Run a live paper or trial search to populate the audit trail.",
                            )
                        }
                    } else {
                        items(provenanceRecords.take(20)) { prov ->
                            ProvenanceRow(prov)
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = { viewModel.resetApiRotationStats() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset Request Counters & Latency Metrics", style = MaterialTheme.concordance.buttonSmall)
                        }
                    }
                }
            }

            8 -> {
                // TAB 8: CURATED VARIANT EVIDENCE (CIViC) & COHORTS (cBioPortal)
                CuratedEvidenceTab(
                    viewModel = viewModel,
                    onCopyText = { label, text -> copyText(label, text) },
                    modifier = Modifier.weight(1f)
                )
            }

            9 -> {
                // TAB 9: BYOK SETTINGS & LICENSE GATING
                ByokSettingsTab(
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
            }

            10 -> {
                // TAB 10: SELF-LEARNING (bounded dry-run rounds + human-approved revisions)
                ModelLearningTab(
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (toastMessage != null) {
        LaunchedEffect(toastMessage) {
            kotlinx.coroutines.delay(2000)
            toastMessage = null
        }
    }
}

@Composable
fun SciencePaperCard(
    paper: SciencePaper,
    isAnalyzing: Boolean,
    onAnalyze: () -> Unit,
    onCopyCitation: () -> Unit
) {
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
            Text(
                text = "${paper.journal} • ${paper.year}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.concordance.bodyDense,
                fontWeight = FontWeight.Bold,
                color = ClinicalTeal
            )
            Text(
                text = paper.sourceApi,
                style = MaterialTheme.concordance.bodyDense,
                color = TextMuted
            )
        }

        Text(
            text = paper.title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.concordance.bodyDense,
            color = TextPrimary,
            lineHeight = 19.sp
        )

        Text(
            text = "${paper.authors} | DOI: ${paper.doi}" + (if (paper.pmid != null) " | PMID: ${paper.pmid}" else ""),
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary
        )

        Text(
            text = paper.abstractText,
            style = MaterialTheme.concordance.bodyDense,
            color = TextMuted,
            maxLines = 3,
            lineHeight = 15.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCopyCitation,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Citation", style = MaterialTheme.concordance.buttonSmall)
            }

            Button(
                onClick = onAnalyze,
                enabled = !isAnalyzing,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1.3f)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isAnalyzing) "Analyzing..." else "Analyze Paper", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PubChemCompoundCard(
    compound: PubChemCompound,
    onCopyUrl: () -> Unit
) {
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
            Text(
                text = compound.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.concordance.cardTitle,
                fontWeight = FontWeight.Bold,
                color = ElectricCyan
            )
            Text(
                text = "CID: ${compound.cid}",
                style = MaterialTheme.concordance.monoId,
                fontFamily = FontFamily.Monospace,
                color = TextMuted
            )
        }

        Text(
            text = "${compound.drugClass} • Target: ${compound.targetGene}",
            style = MaterialTheme.concordance.monoId,
            color = ClinicalTeal,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(SlateCardElevated)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Formula", style = MaterialTheme.concordance.monoId, color = TextMuted)
                Text(compound.molecularFormula, style = MaterialTheme.concordance.monoId, color = TextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("Molecular Wt", style = MaterialTheme.concordance.monoId, color = TextMuted)
                Text("${compound.molecularWeight} g/mol", style = MaterialTheme.concordance.monoId, color = TextPrimary, fontFamily = FontFamily.Monospace)
            }
            Column {
                Text("Associated Variants", style = MaterialTheme.concordance.monoId, color = TextMuted)
                Text(compound.associatedMutations.joinToString(", "), style = MaterialTheme.concordance.monoId, color = NeonEmerald, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = "Mechanism: ${compound.mechanism}",
            style = MaterialTheme.concordance.monoId,
            color = TextSecondary,
            lineHeight = 15.sp
        )

        Text(
            text = "BBB Profile: ${compound.bbbPermeability}",
            style = MaterialTheme.concordance.bodyDense,
            color = WarningGold,
            lineHeight = 15.sp
        )

        OutlinedButton(
            onClick = onCopyUrl,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open PubChem Entry (CID: ${compound.cid})", style = MaterialTheme.concordance.bodyDense)
        }
    }
}

@Composable
fun ClinicalTrialCard(
    study: ClinicalTrialStudy,
    onCopyNct: () -> Unit
) {
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
            Text(
                text = "${study.nctId} • ${study.phase}",
                style = MaterialTheme.concordance.monoId,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = ElectricCyan
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(ClinicalTeal.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(study.overallStatus, style = MaterialTheme.concordance.bodyDense, color = ClinicalTeal, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = study.briefTitle,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.concordance.bodyDense,
            color = TextPrimary,
            lineHeight = 18.sp
        )

        Text(
            text = "Sponsor: ${study.sponsor} | Completion: ${study.primaryCompletionDate}",
            style = MaterialTheme.concordance.bodyDense,
            color = TextMuted
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(SlateCardElevated)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "CNS Metastasis Criteria:",
                style = MaterialTheme.concordance.bodyDense,
                fontWeight = FontWeight.Bold,
                color = WarningGold
            )
            Text(
                text = study.brainMetastasisCriteria,
                style = MaterialTheme.concordance.bodyDense,
                color = TextSecondary,
                lineHeight = 15.sp
            )
            Text(
                text = "ESR1 ctDNA Tracking:",
                style = MaterialTheme.concordance.bodyDense,
                fontWeight = FontWeight.Bold,
                color = NeonEmerald
            )
            Text(
                text = study.esr1EndpointTracking,
                style = MaterialTheme.concordance.bodyDense,
                color = TextSecondary,
                lineHeight = 15.sp
            )
        }

        OutlinedButton(
            onClick = onCopyNct,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open ClinicalTrials.gov Record", style = MaterialTheme.concordance.buttonSmall)
        }
    }
}

@Composable
fun ApiProviderCard(
    provider: ApiProvider,
    onToggle: () -> Unit
) {
    val statusColor = Color(provider.status.colorHex)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateCard)
            .border(1.dp, SlateCardBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.concordance.monoValue,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${provider.category.label} • ${provider.baseUrl}",
                    style = MaterialTheme.concordance.monoId,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(
                    text = "License: ${provider.licenseClass.label}",
                    style = MaterialTheme.concordance.monoId,
                    color = TextMuted
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.2f))
                        .border(1.dp, statusColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(provider.status.label, style = MaterialTheme.concordance.bodyDense, color = statusColor, fontWeight = FontWeight.Bold)
                }
                Switch(
                    checked = provider.isActive,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Requests: ${provider.requestCount} (${provider.successCount} ok, ${provider.failureCount} err)", style = MaterialTheme.concordance.monoId, color = TextSecondary)
            Text("Avg Latency: ${provider.avgLatencyMs}ms", style = MaterialTheme.concordance.monoId, color = ElectricCyan, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun ProvenanceRow(prov: Provenance) {
    val statusColor = if (prov.httpStatus in 200..299) NeonEmerald else WarningGold
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SlateCardElevated)
            .border(1.dp, SlateCardBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = prov.providerId,
                style = MaterialTheme.concordance.monoId,
                fontWeight = FontWeight.Bold,
                color = ElectricCyan,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "HTTP ${prov.httpStatus} • ${prov.elapsedMs}ms",
                style = MaterialTheme.concordance.monoId,
                color = statusColor,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = "Query: ${prov.query}",
            style = MaterialTheme.concordance.monoId,
            color = TextSecondary,
            maxLines = 1
        )
        Text(
            text = "SHA-256: ${prov.responseSha256.take(24)}…",
            style = MaterialTheme.concordance.monoId,
            color = TextMuted,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "${prov.licenseClass.label} • ${prov.retrievedUtc}",
            style = MaterialTheme.concordance.monoId,
            color = TextMuted
        )
    }
}

@Composable
fun KaggleDatasetCard(
    dataset: KaggleDataset,
    onIngest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateCard)
            .border(1.dp, SlateCardBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(dataset.title, fontWeight = FontWeight.Bold, style = MaterialTheme.concordance.monoValue, color = TextPrimary)
            Text("${dataset.sizeMb} MB", style = MaterialTheme.concordance.monoId, color = TextMuted, fontFamily = FontFamily.Monospace)
        }
        Text(dataset.description, style = MaterialTheme.concordance.monoId, color = TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${dataset.totalSamples} samples (${dataset.pairedSamples} paired)", style = MaterialTheme.concordance.monoId, color = ElectricCyan, fontFamily = FontFamily.Monospace)
            Button(
                onClick = onIngest,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ingest to Paired Matrix", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun KaggleJobCard(job: KaggleComputeJob) {
    val statusColor = Color(job.status.colorHex)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateCardElevated)
            .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(job.title, fontWeight = FontWeight.Bold, style = MaterialTheme.concordance.bodyDense, color = TextPrimary)
            Text(job.status.label, style = MaterialTheme.concordance.bodyDense, color = statusColor, fontWeight = FontWeight.Bold)
        }

        if (job.status == KaggleJobStatus.RUNNING || job.status == KaggleJobStatus.QUEUED) {
            LinearProgressIndicator(
                progress = { job.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = ElectricCyan,
                trackColor = SlateCardBorder
            )
        }

        if (job.resultSummary != null) {
            Text(
                text = job.resultSummary,
                style = MaterialTheme.concordance.monoId,
                color = NeonEmerald,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun S3ObjectCard(
    obj: S3SyncedObject,
    onCopyUrl: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateCard)
            .border(1.dp, SlateCardBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("s3://${obj.bucket}/${obj.s3Key}", style = MaterialTheme.concordance.monoId, fontWeight = FontWeight.Bold, color = ElectricCyan, fontFamily = FontFamily.Monospace)
            Text("${obj.sizeBytes} B", style = MaterialTheme.concordance.monoId, color = TextMuted)
        }
        Text("SHA-256 ETag: ${obj.sha256Etag.take(16)}... | Modified: ${obj.lastModifiedUtc}", style = MaterialTheme.concordance.monoId, color = TextSecondary, fontFamily = FontFamily.Monospace)
        OutlinedButton(
            onClick = onCopyUrl,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text("Copy Presigned S3 Review URL (Valid 24h)", style = MaterialTheme.concordance.monoId)
        }
    }
}

// ---------------------------------------------------------------------------
// Prior knowledge (INDRA)
// ---------------------------------------------------------------------------

/**
 * Public background for one entity pair. INDRA answers in 10-45 s on the public
 * database, so the card always shows that the query is running instead of
 * pretending to be instant. Nothing here is a verdict: belief scores are
 * model estimates and contradictions are a review trigger.
 */
@Composable
private fun IndraCard(
    subject: String,
    onSubjectChange: (String) -> Unit,
    partner: String,
    onPartnerChange: (String) -> Unit,
    loading: Boolean,
    knowledge: IndraKnowledge?,
    contradictions: IndraContradictions?,
    error: String?,
    onLookup: () -> Unit,
    onClear: () -> Unit
) {
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
            Text(
                text = "PRIOR KNOWLEDGE · INDRA",
                style = MaterialTheme.concordance.overline,
                fontWeight = FontWeight.Bold,
                color = IcyBlue
            )
            Text(
                text = "PUBLIC ONLY",
                style = MaterialTheme.concordance.chip,
                color = TextMuted
            )
        }
        Text(
            text = "What public literature and databases already report about a pair. " +
                "Belief is a model estimate, never a verdict, and never patient data.",
            style = MaterialTheme.concordance.caption,
            color = TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = subject,
                onValueChange = onSubjectChange,
                label = { Text("Gene / protein", style = MaterialTheme.concordance.caption) },
                singleLine = true,
                textStyle = MaterialTheme.concordance.bodyDense,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = partner,
                onValueChange = onPartnerChange,
                label = { Text("Partner (optional)", style = MaterialTheme.concordance.caption) },
                singleLine = true,
                textStyle = MaterialTheme.concordance.bodyDense,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onLookup,
                enabled = !loading && subject.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = IcyBlue, contentColor = MidnightDeep),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Look up", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = IcyBlue
                )
                Text(
                    text = "Querying INDRA (can take 1-2 min)...",
                    style = MaterialTheme.concordance.caption,
                    color = TextSecondary
                )
            }
            if (!loading && (knowledge != null || error != null)) {
                TextButton(onClick = onClear) {
                    Text("Clear", style = MaterialTheme.concordance.buttonSmall, color = TextMuted)
                }
            }
        }

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.concordance.caption,
                color = WarningAmber
            )
        }

        if (knowledge != null) {
            val typeSummary = knowledge.types.entries
                .sortedByDescending { it.value }
                .take(4)
                .joinToString(" · ") { "${it.key} ${it.value}" }
            Text(
                text = "${knowledge.statementCount} statements" +
                    if (typeSummary.isNotBlank()) " · $typeSummary" else "",
                style = MaterialTheme.concordance.monoId,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            knowledge.statements.take(4).forEach { statement ->
                IndraStatementRow(statement)
            }
            if (contradictions?.contradicted == true) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, WarningAmber, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "SOURCES DISAGREE (${contradictions.pairs.size})",
                        style = MaterialTheme.concordance.overline,
                        fontWeight = FontWeight.Bold,
                        color = WarningAmber
                    )
                    contradictions.pairs.take(3).forEach { pair ->
                        Text(
                            text = "${pair.types.joinToString(" vs ")} · " +
                                "${pair.positive.subject} → ${pair.positive.objectName} " +
                                "(belief gap ${indraFmt(pair.beliefGap)})",
                            style = MaterialTheme.concordance.caption,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = "Review before relying on this relationship; the app does not resolve it.",
                        style = MaterialTheme.concordance.caption,
                        color = TextMuted
                    )
                }
            }
            Text(
                text = knowledge.disclaimer,
                style = MaterialTheme.concordance.caption,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun IndraStatementRow(statement: IndraStatement) {
    val pmid = statement.evidence.firstOrNull { it.pmid.isNotBlank() }?.pmid
    val source = statement.evidence.firstOrNull()?.source?.takeIf { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "${statement.statementType} · ${statement.subject} → ${statement.objectName}",
            style = MaterialTheme.concordance.monoId,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = buildString {
                append("belief ").append(indraFmt(statement.belief))
                if (source != null) append(" · ").append(source)
                if (pmid != null) append(" · PMID ").append(pmid)
            },
            style = MaterialTheme.concordance.caption,
            color = TextSecondary
        )
    }
}

private fun indraFmt(value: Double): String = String.format(Locale.US, "%.2f", value)

package com.example.concordance.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.concordance.data.ConcordanceDatabase
import com.example.concordance.data.ConcordanceRepository
import com.example.concordance.data.RuntimeTuning
import com.example.concordance.data.RuntimeTuningStore
import com.example.concordance.autonomy.ResearchRunStore
import com.example.concordance.engine.CapsuleEngine
import com.example.concordance.engine.LocalModelEngine
import com.example.concordance.engine.PairedAnalyzer
import com.example.concordance.engine.StatisticalEngine
import com.example.concordance.integration.agent.AgentAnchor
import com.example.concordance.integration.agent.AgentClient
import com.example.concordance.integration.agent.AgentConfig
import com.example.concordance.integration.agent.AgentExperiment
import com.example.concordance.integration.agent.AgentJob
import com.example.concordance.integration.agent.AgentRefereeReport
import com.example.concordance.integration.agent.AgentResearchRun
import com.example.concordance.integration.agent.AgentResearchRunDetail
import com.example.concordance.integration.agent.AgentResearchResult
import com.example.concordance.integration.agent.AgentResearchVerification
import com.example.concordance.integration.agent.AgentOutcome
import com.example.concordance.integration.agent.AgentPendingDecision
import com.example.concordance.integration.agent.AgentSettingsStore
import com.example.concordance.integration.agent.AgentUpload
import com.example.concordance.integration.agent.CorrectionResult
import com.example.concordance.integration.agent.EasyModeView
import com.example.concordance.integration.agent.UploadedFigure
import com.example.concordance.integration.knowledge.IndraClient
import com.example.concordance.integration.knowledge.IndraContradictions
import com.example.concordance.integration.knowledge.IndraKnowledge
import com.example.concordance.integration.knowledge.IndraResult
import com.example.concordance.integration.community.CommunityPublisher
import com.example.concordance.integration.supabase.PostgRestClient
import com.example.concordance.integration.supabase.SupabaseAuth
import com.example.concordance.integration.supabase.SupabaseSyncManager
import com.example.concordance.integration.supabase.SyncConflict
import com.example.concordance.integration.supabase.SyncConflictBus
import com.example.concordance.integration.supabase.createSupabaseSessionStore
import com.example.concordance.integration.api.CohortStudy
import com.example.concordance.integration.api.CuratedVariantEvidence
import com.example.concordance.integration.api.HttpJsonClient
import com.example.concordance.integration.api.IntegrationRegistry
import com.example.concordance.integration.api.Provenance
import com.example.concordance.integration.api.ProvenanceRecorder
import com.example.concordance.integration.api.SomaticMutation
import com.example.concordance.learning.ChallengeLabel
import com.example.concordance.learning.DryRunReport
import com.example.concordance.learning.EvaluationResult
import com.example.concordance.learning.LearningAudit
import com.example.concordance.learning.LearningController
import com.example.concordance.learning.LearningDecision
import com.example.concordance.learning.LearningEvaluator
import com.example.concordance.learning.LearningHealth
import com.example.concordance.learning.LearningHealthCalculator
import com.example.concordance.learning.ModelParameters
import com.example.concordance.learning.ModelRevision
import com.example.concordance.learning.ParameterSearch
import com.example.concordance.learning.RevisionDiffer
import com.example.concordance.learning.RevisionDiff
import com.example.concordance.learning.RevisionPublisher
import com.example.concordance.learning.TrainingSetBuilder
import com.example.concordance.learning.RevisionArchive
import com.example.concordance.telemetry.OptimizationSuggestion
import com.example.concordance.telemetry.TelemetryEvent
import com.example.concordance.telemetry.TelemetryManager

import com.example.concordance.research.DeepResearchReport
import com.example.concordance.research.DeepResearchSystem
import com.example.concordance.research.ExperimentProposal
import com.example.concordance.research.PaperGrade
import com.example.concordance.research.ResearchInsight
import com.example.concordance.research.ResearchPaper
import com.example.concordance.integration.byok.KeyStore
import com.example.concordance.integration.byok.LicenseStore
import com.example.concordance.integration.byok.createKeyStore
import com.example.concordance.integration.byok.createLicenseStore
import com.example.concordance.integration.aws.AwsCloudManager
import com.example.concordance.integration.aws.AwsSyncStatus
import com.example.concordance.integration.aws.S3SyncedObject
import com.example.concordance.integration.kaggle.KaggleComputeJob
import com.example.concordance.integration.kaggle.KaggleComputeManager
import com.example.concordance.integration.kaggle.KaggleDataset
import com.example.concordance.integration.queue.*
import com.example.concordance.integration.rotation.ApiProvider
import com.example.concordance.integration.rotation.ApiRotationManager
import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.RotationStrategy
import com.example.concordance.integration.science.AnalysisEngine
import com.example.concordance.integration.science.PaperAnalysisOutput
import com.example.concordance.integration.science.SciencePaper
import com.example.concordance.integration.science.SciencePaperManager
import com.example.concordance.model.*
import com.example.concordance.agent.EasyMode
import com.example.concordance.voice.OnDeviceSpeechRecognizer
import com.example.concordance.voice.SpeechEvent
import com.example.concordance.voice.SpeechOutput
import com.example.concordance.voice.SpeechSynthesizer
import com.example.concordance.local.InstallableModel
import com.example.concordance.local.LocalLlmService
import com.example.concordance.local.LocalLlmStatus
import com.example.concordance.local.LocalModels
import com.example.concordance.local.ModelDownloadService
import com.example.concordance.local.ModelDownloadStatus
import com.example.concordance.local.ModelDownloader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ConcordanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ConcordanceRepository = run {
        val db = ConcordanceDatabase.getDatabase(application)
        ConcordanceRepository(db)
    }

    val keyStore: KeyStore = createKeyStore(application)
    val licenseStore: LicenseStore = createLicenseStore(application)

    private val _isLoadingClaims = MutableStateFlow(true)

    /**
     * True until the first database emission arrives. Lets the UI tell "still
     * reading the database" apart from "the database is genuinely empty", so a
     * first-run screen shows a skeleton instead of a misleading empty state.
     */
    val isLoadingClaims: StateFlow<Boolean> = _isLoadingClaims.asStateFlow()

    val claims: StateFlow<List<Anchor>> = repository.getAllClaimsFlow()
        .onEach { _isLoadingClaims.value = false }
        .catch { _isLoadingClaims.value = false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLoadingCapsules = MutableStateFlow(true)

    /** True until the first database emission arrives; see [isLoadingClaims]. */
    val isLoadingCapsules: StateFlow<Boolean> = _isLoadingCapsules.asStateFlow()

    val savedCapsules: StateFlow<List<Capsule>> = repository.getAllCapsulesFlow()
        .onEach { _isLoadingCapsules.value = false }
        .catch { _isLoadingCapsules.value = false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedAnchor = MutableStateFlow<Anchor?>(null)
    val selectedAnchor: StateFlow<Anchor?> = _selectedAnchor.asStateFlow()

    private val _sn = MutableStateFlow(0.90)
    val sn: StateFlow<Double> = _sn.asStateFlow()

    private val _rLow = MutableStateFlow<Double?>(null)
    val rLow: StateFlow<Double?> = _rLow.asStateFlow()

    private val _rHigh = MutableStateFlow<Double?>(null)
    val rHigh: StateFlow<Double?> = _rHigh.asStateFlow()

    private val _syntheticInputs = MutableStateFlow(false)
    val syntheticInputs: StateFlow<Boolean> = _syntheticInputs.asStateFlow()

    val adjudicationResult: StateFlow<AdjudicationResult?> = combine(
        _selectedAnchor,
        _sn,
        _rLow,
        _rHigh,
        _syntheticInputs
    ) { anchor, snVal, rLo, rHi, synth ->
        if (anchor == null) null
        else StatisticalEngine.adjudicate(
            anchor = anchor,
            sn = snVal,
            rLow = rLo,
            rHigh = rHi,
            syntheticInputs = synth
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Paired Data Analysis State
    private val _pairedCsvText = MutableStateFlow(PairedAnalyzer.sampleBenchmarkDataset())
    val pairedCsvText: StateFlow<String> = _pairedCsvText.asStateFlow()

    private val _pairedResult = MutableStateFlow<PairedAnalysisResult?>(null)
    val pairedResult: StateFlow<PairedAnalysisResult?> = _pairedResult.asStateFlow()

    private val _pairedErrors = MutableStateFlow<List<String>>(emptyList())
    val pairedErrors: StateFlow<List<String>> = _pairedErrors.asStateFlow()

    // Power Calculator State
    private val _powerTargetR = MutableStateFlow(0.60)
    val powerTargetR: StateFlow<Double> = _powerTargetR.asStateFlow()

    private val _powerDesired = MutableStateFlow(0.80)
    val powerDesired: StateFlow<Double> = _powerDesired.asStateFlow()

    val powerRequirement: StateFlow<PowerRequirement?> = combine(
        _selectedAnchor,
        _powerTargetR,
        _sn,
        _powerDesired
    ) { anchor, targetR, snVal, pwr ->
        if (anchor == null) null
        else try {
            StatisticalEngine.requiredSample(
                anchor = anchor,
                rTrue = targetR,
                sn = snVal,
                alpha = 0.05,
                power = pwr
            )
        } catch (_: Exception) {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val powerSweep: StateFlow<List<PowerSweepRow>> = combine(
        _selectedAnchor,
        _sn,
        _powerDesired
    ) { anchor, snVal, pwr ->
        if (anchor == null) emptyList()
        else StatisticalEngine.powerSweep(anchor, snVal, 0.05, pwr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Capsule Verification State
    private val _verificationResult = MutableStateFlow<Triple<Boolean, String, String>?>(null)
    val verificationResult: StateFlow<Triple<Boolean, String, String>?> = _verificationResult.asStateFlow()

    // --- INTEGRATIONS: API ROTATION ---
    val apiProviders: StateFlow<List<ApiProvider>> = ApiRotationManager.providers
    val rotationStrategy: StateFlow<RotationStrategy> = ApiRotationManager.strategy

    // --- INTEGRATIONS: KAGGLE COMPUTE & DATASETS ---
    val kaggleDatasets: StateFlow<List<KaggleDataset>> = KaggleComputeManager.availableDatasets
    val kaggleJobs: StateFlow<List<KaggleComputeJob>> = KaggleComputeManager.computeJobs

    // --- INTEGRATIONS: AWS S3 CLOUD VAULT ---
    val awsSyncStatus: StateFlow<AwsSyncStatus> = AwsCloudManager.syncStatus
    val awsRegion: StateFlow<String> = AwsCloudManager.region
    val awsBucketName: StateFlow<String> = AwsCloudManager.bucketName
    val awsEndpoint: StateFlow<String?> = AwsCloudManager.endpoint
    val syncedS3Objects: StateFlow<List<S3SyncedObject>> = AwsCloudManager.syncedObjects

    // --- INTEGRATIONS: SCIENCE PAPER INGESTION & ANALYSIS ---
    val sciencePapers: StateFlow<List<SciencePaper>> = SciencePaperManager.papers
    val paperAnalysisEngine: StateFlow<AnalysisEngine> = SciencePaperManager.selectedEngine
    val isAnalyzingPaper: StateFlow<Boolean> = SciencePaperManager.isAnalyzing
    val latestPaperAnalysis: StateFlow<PaperAnalysisOutput?> = SciencePaperManager.latestAnalysis
    val isLoadingMorePapers: StateFlow<Boolean> = SciencePaperManager.isLoadingMore

    // --- INTEGRATIONS: NCBI PUBCHEM & DRUG TARGETS ---
    val pubChemCompounds: StateFlow<List<com.example.concordance.integration.pubchem.PubChemCompound>> =
        com.example.concordance.integration.pubchem.PubChemManager.compounds
    val isSearchingPubChem: StateFlow<Boolean> =
        com.example.concordance.integration.pubchem.PubChemManager.isSearching

    // --- INTEGRATIONS: LIVE API PROVENANCE LOG ---
    val provenanceRecords: StateFlow<List<Provenance>> = ProvenanceRecorder.records
    val provenanceRecordIds: StateFlow<List<Long>> = ProvenanceRecorder.recordIds
    val allProvenance: StateFlow<List<Provenance>> = repository.getAllProvenanceFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Provenance linked to a selected capsule (for the Capsules screen viewer).
    // --- SELF-LEARNING (bounded, human-approved model revisions) ---
    private val _isRunningLearningRound = MutableStateFlow(false)
    val isRunningLearningRound: StateFlow<Boolean> = _isRunningLearningRound.asStateFlow()

    private val _learningReport = MutableStateFlow<DryRunReport?>(null)
    val learningReport: StateFlow<DryRunReport?> = _learningReport.asStateFlow()

    private val _learningCandidates = MutableStateFlow<List<ParameterSearch.CandidateScore>>(emptyList())
    val learningCandidates: StateFlow<List<ParameterSearch.CandidateScore>> = _learningCandidates.asStateFlow()

    private val _trainingSummary = MutableStateFlow<String?>(null)
    val trainingSummary: StateFlow<String?> = _trainingSummary.asStateFlow()

    private val _currentModelRevision = MutableStateFlow<ModelRevision?>(null)
    val currentModelRevision: StateFlow<ModelRevision?> = _currentModelRevision.asStateFlow()

    val modelRevisions: StateFlow<List<ModelRevision>> = repository.getAllModelRevisionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val learningAudit: StateFlow<List<LearningAudit>> = repository.getAllLearningAuditFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allChallenges: StateFlow<List<ClaimChallenge>> = repository.getAllChallengesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Implied verdict for a challenge under the current revision (for UX preview). */
    fun challengeImpliedVerdict(challenge: ClaimChallenge): Verdict? {
        val params = _currentModelRevision.value?.parameters ?: ModelParameters()
        return LearningEvaluator.impliedVerdict(
            ChallengeLabel(
                challenge.claimId,
                challenge.updatedBrainEvents,
                challenge.updatedBrainTotal,
                challenge.updatedOtherEvents,
                challenge.updatedOtherTotal
            ),
            params
        )
    }

    private val _deepResearchReport = MutableStateFlow<DeepResearchReport?>(null)
    val deepResearchReport: StateFlow<DeepResearchReport?> = _deepResearchReport.asStateFlow()

    // Data ingestion results
    private val _wetLabIngestResult = MutableStateFlow<com.example.concordance.integration.wetlab.WetLabDataIngester.IngestResult?>(null)
    val wetLabIngestResult: StateFlow<com.example.concordance.integration.wetlab.WetLabDataIngester.IngestResult?> = _wetLabIngestResult.asStateFlow()

    private val _imagingIngestResult = MutableStateFlow<com.example.concordance.integration.imaging.ImagingDatasetManager.IngestResult?>(null)
    val imagingIngestResult: StateFlow<com.example.concordance.integration.imaging.ImagingDatasetManager.IngestResult?> = _imagingIngestResult.asStateFlow()

    private val _paperIngestResult = MutableStateFlow<com.example.concordance.integration.science.BatchPaperIngestion.IngestResult?>(null)
    val paperIngestResult: StateFlow<com.example.concordance.integration.science.BatchPaperIngestion.IngestResult?> = _paperIngestResult.asStateFlow()

    private val _revisionDiff = MutableStateFlow<RevisionDiff?>(null)
    val revisionDiff: StateFlow<RevisionDiff?> = _revisionDiff.asStateFlow()

    private val _learningHealth = MutableStateFlow(LearningHealth.empty())
    val learningHealth: StateFlow<LearningHealth> = _learningHealth.asStateFlow()

    private val _revisionArchiveExport = MutableStateFlow<String?>(null)
    val revisionArchiveExport: StateFlow<String?> = _revisionArchiveExport.asStateFlow()

    val telemetrySuggestions: StateFlow<List<OptimizationSuggestion>> = TelemetryManager.suggestions

    fun telemetrySummary(): String = TelemetryManager.summary()

    fun analyzeTelemetry() {
        TelemetryManager.analyze()
    }

    fun flushTelemetryToCloud() {
        viewModelScope.launch {
            val ak = keyStore.getKey("aws_access_key_id")
            val sk = keyStore.getKey("aws_secret_access_key")
            val region = keyStore.getKey("aws_region") ?: "us-east-2"
            TelemetryManager.flushToCloud(ak, sk, region)
            _uiNotice.emit("Telemetry flushed to CloudWatch (count: ${TelemetryManager.flushCount.value}).")
        }
    }

    private fun refreshCurrentRevision() {
        viewModelScope.launch {
            _currentModelRevision.value = repository.getLatestPromotedRevision()
            refreshLearningHealth()
        }
    }

    private suspend fun refreshLearningHealth() {
        val revisions = repository.getAllModelRevisionsFlow().firstOrNull().orEmpty()
        val claims = repository.getAllClaims()
        val challenges = repository.getAllChallenges()
        val current = _currentModelRevision.value
        _learningHealth.value = LearningHealthCalculator.compute(revisions, claims, challenges, current)
    }

    // --- SELF-LEARNING ROUNDS ---

    /** Build the training set from stored anchors + human challenges. */
    private suspend fun buildTrainingSet(baseline: ModelParameters): TrainingSetBuilder.Result {
        val anchors = repository.getAllClaims()
        val challenges = repository.getAllChallenges()
        val byAnchor = challenges.groupBy(
            { it.claimId },
            { ChallengeLabel(it.claimId, it.updatedBrainEvents, it.updatedBrainTotal, it.updatedOtherEvents, it.updatedOtherTotal) }
        )
        return TrainingSetBuilder.build(anchors, byAnchor, baseline)
    }

    /**
     * Run a bounded learning round in DRY-RUN mode. Produces candidate scores and
     * a report; never persists or promotes anything.
     */
    fun runLearningRound() {
        if (_isRunningLearningRound.value) return
        viewModelScope.launch {
            _isRunningLearningRound.value = true
            try {
                val baseline = _currentModelRevision.value?.parameters ?: ModelParameters()
                val ts = buildTrainingSet(baseline)
                if (ts.isAborted) {
                    _trainingSummary.value = "Round ABORTED: ${ts.violations.size} guard violation(s). ${ts.violations.first()}"
                    _learningCandidates.value = emptyList()
                    _learningReport.value = null
                    _uiNotice.emit("Learning round aborted by anti-collapse guard.")
                    return@launch
                }
                if (ts.cases.isEmpty()) {
                    _trainingSummary.value = "No usable anchors for a learning round."
                    _learningCandidates.value = emptyList()
                    _learningReport.value = null
                    return@launch
                }
                val grid = ParameterSearch.grid(baseline)
                val scored = ParameterSearch.search(ts.cases, grid, baseline)
                _learningCandidates.value = scored.take(TOP_CANDIDATES)
                val best = scored.first()
                _learningReport.value = LearningController.dryRun(ts.cases, best.parameters, baseline, _stabilityThreshold.value)
                TelemetryManager.recordLearningRound(_learningReport.value!!.improved, false)
                TelemetryManager.analyze()
                val skippedNote = buildString {
                    if (ts.skipped > 0) {
                        append(" Skipped: ")
                        append(ts.skipReasons.take(2).joinToString("; "))
                        if (ts.skipReasons.size > 2) append(" (+${ts.skipReasons.size - 2} more)")
                        append(".")
                    }
                    if (ts.skippedChallenges > 0) {
                        append(" ${ts.skippedChallenges} challenge(s) skipped: corrected counts are not estimable.")
                    }
                }
                _trainingSummary.value = "Training set: ${ts.cases.size} anchors evaluated, ${ts.challengeCount} challenges, ${ts.skipped} skipped.$skippedNote Scored ${grid.size} bounded candidates."
            } finally {
                _isRunningLearningRound.value = false
            }
        }
    }

    /** Run a deep research round: grade papers, analyze insights, propose experiments. */
    fun runDeepResearch() {
        viewModelScope.launch {
            _uiNotice.emit("Running deep research round...")
            val anchors = repository.getAllClaims()
            val capsules = repository.getAllCapsulesFlow().firstOrNull().orEmpty()
            val challenges = repository.getAllChallenges()
            val current = _currentModelRevision.value

            val papers = anchors.map { anchor ->
                val relatedCapsules = capsules.filter { it.anchorLabel == anchor.label }
                ResearchPaper(
                    id = anchor.id,
                    title = anchor.label,
                    timestampUtc = anchor.dateAdded,
                    abstract = anchor.notes,
                    anchorIds = listOf(anchor.id),
                    capsuleIds = relatedCapsules.map { it.id },
                    challengeCount = challenges.count { it.claimId == anchor.id },
                    verdict = relatedCapsules.firstOrNull()?.verdict,
                    rNull = relatedCapsules.firstOrNull()?.rNull,
                    rSignificance = relatedCapsules.firstOrNull()?.rSignificance,
                    modelRevisionId = anchor.modelRevisionId,
                    keywords = listOf(anchor.evidenceGrade.name)
                )
            }

            val challengeRate = if (challenges.isEmpty()) 1.0 else {
                var total = 0; var resolved = 0
                for (ch in challenges) {
                    val anchor = anchors.firstOrNull { it.id == ch.claimId } ?: continue
                    val params = current?.parameters ?: ModelParameters()
                    val candVerdict = LearningEvaluator.adjudicate(anchor, params) ?: continue
                    // A correction whose counts the engine cannot estimate has no verdict to
                    // match; it must not be scored as a permanent miss.
                    val implied = LearningEvaluator.impliedVerdict(
                        ChallengeLabel(ch.claimId, ch.updatedBrainEvents, ch.updatedBrainTotal, ch.updatedOtherEvents, ch.updatedOtherTotal),
                        params
                    ) ?: continue
                    total++
                    if (implied == candVerdict) resolved++
                }
                if (total == 0) 1.0 else resolved.toDouble() / total
            }

            val report = DeepResearchSystem.run(papers, anchors, capsules, challengeRate)
            _deepResearchReport.value = report

            TelemetryManager.record(
                TelemetryEvent.Category.LEARNING, "deep_research_round", report.overallBreakthroughScore, "score"
            )
            TelemetryManager.analyze()

            _uiNotice.emit("Deep research complete: ${report.papersGraded} papers graded, ${report.experimentProposals.size} proposals. Breakthrough score: ${"%.2f".format(report.overallBreakthroughScore)}.")
        }
    }


    /**
     * Promote a dry-run candidate. This is the ONLY path that makes a revision
     * current, and it requires an explicit human approver.
     */
    fun promoteCandidate(candidate: ParameterSearch.CandidateScore, approver: String) {
        viewModelScope.launch {
            val baseline = _currentModelRevision.value?.parameters ?: ModelParameters()
            val ts = buildTrainingSet(baseline)
            if (ts.isAborted) {
                _uiNotice.emit("Promotion refused: anti-collapse guard tripped.")
                return@launch
            }
            val proposal = RevisionPublisher.propose(
                id = newRevisionId(),
                parentRevisionId = _currentModelRevision.value?.id,
                parameters = candidate.parameters,
                metricE = candidate.result.total,
                createdAtUtc = ProvenanceRecorder.nowUtc(),
                notes = "Promoted from bounded dry-run round"
            )
            val baseResult = LearningEvaluator.evaluate(ts.cases, baseline, baseline)
            val approved = RevisionPublisher.approve(proposal, approver, baseResult, candidate.result)
            if (approved == null) {
                // Every decision is audited, including refusals (plan §6.5).
                recordAudit(
                    candidate = candidate.parameters,
                    baseline = baseline,
                    result = candidate.result,
                    decision = LearningDecision.REJECTED,
                    approver = approver.ifBlank { null },
                    revisionId = null,
                    notes = if (approver.isBlank()) "Refused: approver required."
                    else "Refused: no metric improvement."
                )
                _uiNotice.emit("Promotion refused: no metric improvement or invalid approval.")
                return@launch
            }
            repository.saveModelRevision(approved)
            recordAudit(
                candidate = approved.parameters,
                baseline = baseline,
                result = candidate.result,
                decision = LearningDecision.PROMOTED,
                approver = approved.promotedBy,
                revisionId = approved.id,
                notes = "Promoted (E=${"%.4f".format(candidate.result.total)})."
            )
            TelemetryManager.recordLearningRound(true, true)
            TelemetryManager.analyze()
            refreshCurrentRevision()
            _uiNotice.emit("Promoted model revision ${approved.id} (E=${"%.4f".format(approved.metricE)}).")
        }
    }

    fun ingestWetLabRecords(records: List<com.example.concordance.integration.wetlab.WetLabRecord>) {
        viewModelScope.launch {
            val result = com.example.concordance.integration.wetlab.WetLabDataIngester.ingest(records)
            _wetLabIngestResult.value = result
            for (anchor in result.anchors) {
                repository.addClaim(anchor)
            }
            _uiNotice.emit("Wet-lab ingest: ${result.accepted.size} accepted, ${result.rejected.size} rejected.")
            TelemetryManager.record(com.example.concordance.telemetry.TelemetryEvent.Category.SYSTEM, "wetlab_ingest", result.accepted.size.toDouble())
            TelemetryManager.analyze()
        }
    }

    fun ingestImagingDatasets(datasets: List<com.example.concordance.integration.imaging.ImagingDataset>) {
        viewModelScope.launch {
            val result = com.example.concordance.integration.imaging.ImagingDatasetManager.ingest(datasets)
            _imagingIngestResult.value = result
            for (anchor in result.anchors) {
                repository.addClaim(anchor)
            }
            _uiNotice.emit("Imaging ingest: ${result.accepted.size} accepted, ${result.rejected.size} rejected.")
            TelemetryManager.record(com.example.concordance.telemetry.TelemetryEvent.Category.SYSTEM, "imaging_ingest", result.accepted.size.toDouble())
            TelemetryManager.analyze()
        }
    }

    fun ingestPapersBatch(papers: List<com.example.concordance.integration.science.PaperRecord>, existingCorpus: List<com.example.concordance.integration.science.PaperRecord> = emptyList()) {
        viewModelScope.launch {
            val result = com.example.concordance.integration.science.BatchPaperIngestion.ingest(papers, existingCorpus)
            _paperIngestResult.value = result
            _uiNotice.emit("Paper batch ingest: ${result.accepted.size} accepted, ${result.rejected.size} rejected, ${result.duplicates.size} duplicates.")
            TelemetryManager.record(com.example.concordance.telemetry.TelemetryEvent.Category.SYSTEM, "paper_ingest", result.accepted.size.toDouble())
            TelemetryManager.analyze()
        }
    }


    /** Append-only rollback: publish a new revision carrying the target's parameters. */
    fun revertToRevision(revisionId: String, approver: String) {
        viewModelScope.launch {
            val target = modelRevisions.value.firstOrNull { it.id == revisionId }
            if (target == null) {
                _uiNotice.emit("Revision '$revisionId' not found.")
                return@launch
            }
            val current = _currentModelRevision.value
            val revert = RevisionPublisher.revert(
                id = newRevisionId(),
                target = target,
                currentRevisionId = current?.id,
                createdAtUtc = ProvenanceRecorder.nowUtc(),
                approver = approver
            )
            if (revert == null) {
                _uiNotice.emit("Revert refused.")
                return@launch
            }
            repository.saveModelRevision(revert)
            recordAudit(
                candidate = revert.parameters,
                baseline = current?.parameters ?: ModelParameters(),
                result = EvaluationResult(0.0, 0.0, 0.0, 0.0, target.metricE, 0),
                decision = LearningDecision.REVERTED,
                approver = approver,
                revisionId = revert.id,
                notes = "Revert to '${target.id}'."
            )
            refreshCurrentRevision()
            _uiNotice.emit("Reverted to '${target.id}' via new revision ${revert.id}.")
        }
    }

    /** Re-run adjudication on every stored claim under [revisionId]'s parameters and
     * publish NEW capsules linked via supersedesCapsuleId. Old capsules stay pinned
     * (plan 6.7) - this never mutates or deletes them.
     */
    fun reAdjudicateUnder(revisionId: String?) {
        viewModelScope.launch {
            val target = if (revisionId == null) _currentModelRevision.value
            else modelRevisions.value.firstOrNull { it.id == revisionId } ?: _currentModelRevision.value
            if (target == null) {
                _uiNotice.emit("No revision available to re-adjudicate under.")
                return@launch
            }
            val params = target.parameters
            val claims = repository.getAllClaims()
            if (claims.isEmpty()) {
                _uiNotice.emit("No claims to re-adjudicate.")
                return@launch
            }
            var produced = 0
            var skipped = 0
            for (claim in claims) {
                // Refuse to manufacture a capsule from inputs the correction cannot use.
                if (!claim.isUsable) {
                    skipped++
                    continue
                }
                val adj = try {
                    StatisticalEngine.adjudicate(
                        anchor = claim,
                        sn = params.sn,
                        rLow = 0.30,
                        rHigh = 0.90
                    )
                } catch (e: Exception) {
                    continue
                }
                val prior = repository.getAllCapsulesFlow().firstOrNull()?.firstOrNull { it.anchorLabel == claim.label }
                val capsule = CapsuleEngine.buildCapsule(
                    adj = adj,
                    createdUtc = ProvenanceRecorder.nowUtc(),
                    modelRevisionId = target.id,
                    supersedesCapsuleId = prior?.id
                )
                repository.saveCapsule(capsule, emptyList())
                produced++
            }
            if (skipped > 0) {
                _uiNotice.emit("Skipped $skipped claim(s) whose inputs are not estimable.")
            }
            recordAudit(
                candidate = params,
                baseline = _currentModelRevision.value?.parameters ?: ModelParameters(),
                result = EvaluationResult(0.0, 0.0, 0.0, 0.0, target.metricE, claims.size),
                decision = LearningDecision.READJUDICATED,
                approver = null,
                revisionId = target.id,
                notes = "Re-adjudicated $produced claim(s) under ${target.id}."
            )
            _uiNotice.emit("Re-adjudicated $produced claim(s) under ${target.id}; new capsules published, originals pinned.")
        }
    }

    /** Preview how existing anchors would re-adjudicate under a candidate. */
    fun previewCandidateDiff(candidate: ParameterSearch.CandidateScore) {
        viewModelScope.launch {
            val current = _currentModelRevision.value
            val anchors = repository.getAllClaims()
            _revisionDiff.value = RevisionDiffer.diff(
                anchors = anchors,
                from = current?.parameters ?: ModelParameters(),
                to = candidate.parameters,
                fromRevisionId = current?.id,
                toRevisionId = null
            )
        }
    }

    /** Preview the diff between the current revision and a selected history entry. */
    fun previewRevisionDiff(revisionId: String) {
        viewModelScope.launch {
            val current = _currentModelRevision.value
            val target = modelRevisions.value.firstOrNull { it.id == revisionId } ?: return@launch
            val anchors = repository.getAllClaims()
            _revisionDiff.value = RevisionDiffer.diff(
                anchors = anchors,
                from = current?.parameters ?: ModelParameters(),
                to = target.parameters,
                fromRevisionId = current?.id,
                toRevisionId = target.id
            )
        }
    }

    fun clearRevisionDiff() {
        _revisionDiff.value = null
    }

    /** Export all revisions as a versioned JSON archive (Phase 4). */
    fun exportRevisions() {
        viewModelScope.launch {
            val revisions = repository.getAllModelRevisionsFlow().firstOrNull().orEmpty()
            _revisionArchiveExport.value = RevisionArchive.export(revisions)
            _uiNotice.emit("Exported ${revisions.size} model revision(s) as JSON.")
        }
    }

    fun clearRevisionArchiveExport() {
        _revisionArchiveExport.value = null
    }

    /** Import revisions from a JSON archive. Strict: rejects bad schema/bounds. */
    fun importRevisions(json: String) {
        viewModelScope.launch {
            try {
                val revisions = RevisionArchive.import(json)
                var saved = 0
                for (r in revisions) {
                    if (repository.saveModelRevision(r)) saved++
                }
                refreshCurrentRevision()
                _uiNotice.emit("Imported $saved/${revisions.size} revision(s) from archive.")
            } catch (e: RevisionArchive.ArchiveException) {
                _uiNotice.emit("Import refused: ${e.message}")
            }
        }
    }

    private suspend fun recordAudit(
        candidate: ModelParameters,
        baseline: ModelParameters,
        result: EvaluationResult,
        decision: LearningDecision,
        approver: String?,
        revisionId: String?,
        notes: String
    ) {
        repository.saveLearningAudit(
            LearningAudit(
                candidate = candidate,
                baseline = baseline,
                result = result,
                decision = decision,
                approver = approver,
                revisionId = revisionId,
                createdAtUtc = ProvenanceRecorder.nowUtc(),
                notes = notes
            )
        )
    }

    private fun newRevisionId(): String = "rev-${System.currentTimeMillis()}"

    private companion object {
        const val TOP_CANDIDATES = 10

        /** Agent polling: 2 s for up to 4 minutes covers the durable watchdog floor. */
        const val POLL_ATTEMPTS = 120
        const val POLL_DELAY_MS = 2_000L
    }

    // --- RUNTIME TUNING (persisted) ---
    private val tuningStore = RuntimeTuningStore(getApplication())
    private val _cbThreshold = MutableStateFlow(tuningStore.load().failureThreshold)
    val cbThreshold: StateFlow<Int> = _cbThreshold.asStateFlow()
    private val _cbCooldownSeconds = MutableStateFlow(tuningStore.load().cooldownSeconds)
    val cbCooldownSeconds: StateFlow<Int> = _cbCooldownSeconds.asStateFlow()
    private val _cacheTtlMinutes = MutableStateFlow(tuningStore.load().cacheTtlMinutes)
    val cacheTtlMinutes: StateFlow<Int> = _cacheTtlMinutes.asStateFlow()

    private val _autoRoundThreshold = MutableStateFlow(tuningStore.load().autoRoundThreshold)
    val autoRoundThreshold: StateFlow<Int> = _autoRoundThreshold.asStateFlow()

    private val _lastAutoRoundChallengeCount = MutableStateFlow(tuningStore.load().lastAutoRoundChallengeCount)
    val lastAutoRoundChallengeCount: StateFlow<Int> = _lastAutoRoundChallengeCount.asStateFlow()

    private val _stabilityThreshold = MutableStateFlow(tuningStore.load().stabilityThreshold)
    val stabilityThreshold: StateFlow<Double> = _stabilityThreshold.asStateFlow()

    private val _capsuleProvenance = MutableStateFlow<List<Provenance>>(emptyList())
    val capsuleProvenance: StateFlow<List<Provenance>> = _capsuleProvenance.asStateFlow()

    private val _selectedCapsuleId = MutableStateFlow<String?>(null)
    val selectedCapsuleId: StateFlow<String?> = _selectedCapsuleId.asStateFlow()

    fun loadProvenanceForCapsule(capsuleId: String?) {
        _selectedCapsuleId.value = capsuleId
        if (capsuleId == null) {
            _capsuleProvenance.value = emptyList()
            return
        }
        viewModelScope.launch {
            _capsuleProvenance.value = repository.getProvenanceForCapsule(capsuleId)
        }
    }

    // --- INTEGRATIONS: CURATED VARIANT EVIDENCE (CIViC) & COHORTS (cBioPortal) ---
    private val _curatedEvidence = MutableStateFlow<List<CuratedVariantEvidence>>(emptyList())
    val curatedEvidence: StateFlow<List<CuratedVariantEvidence>> = _curatedEvidence.asStateFlow()

    private val _cohortStudies = MutableStateFlow<List<CohortStudy>>(emptyList())
    val cohortStudies: StateFlow<List<CohortStudy>> = _cohortStudies.asStateFlow()

    private val _somaticMutations = MutableStateFlow<List<SomaticMutation>>(emptyList())
    val somaticMutations: StateFlow<List<SomaticMutation>> = _somaticMutations.asStateFlow()

    private val _isSearchingCurated = MutableStateFlow(false)
    val isSearchingCurated: StateFlow<Boolean> = _isSearchingCurated.asStateFlow()

    // --- INTEGRATIONS: CLINICALTRIALS.GOV REST API ---
    val clinicalTrials: StateFlow<List<com.example.concordance.integration.clinicaltrials.ClinicalTrialStudy>> =
        com.example.concordance.integration.clinicaltrials.ClinicalTrialsManager.studies
    val isSearchingTrials: StateFlow<Boolean> =
        com.example.concordance.integration.clinicaltrials.ClinicalTrialsManager.isSearching
    val isLoadingMoreTrials: StateFlow<Boolean> =
        com.example.concordance.integration.clinicaltrials.ClinicalTrialsManager.isLoadingMore

    // --- ASYNC TASK QUEUE & WORKERS (FASTAPI + POSTGRES PATTERN) ---
    val activeQueueJobs: StateFlow<List<JobStatusResponse>> = JobQueueManager.allJobs
    val workerNodes: StateFlow<List<WorkerNode>> = JobQueueManager.workerNodes
    val stuckRecoveryLogs: StateFlow<List<String>> = JobQueueManager.stuckRecoveryLog

    private val _uiNotice = MutableSharedFlow<String>()
    val uiNotice: SharedFlow<String> = _uiNotice.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.seedDefaultsIfNeeded()
            claims.collect { list ->
                if (_selectedAnchor.value == null && list.isNotEmpty()) {
                    _selectedAnchor.value = list.first()
                }
            }
        }
        // A high-power run happens in a foreground service. Take its result here so the
        // research screen renders the experiments and the routing, not just a notification.
        viewModelScope.launch {
            ResearchRunStore.last.collect { run ->
                when {
                    run == null -> Unit
                    run.result != null -> {
                        _agentResearchResult.value = run.result
                        val routing = run.routing?.summary().orEmpty()
                        val suffix = if (routing.isBlank()) "" else " · $routing"
                        _uiNotice.emit(
                            "High-power run: ${run.result.experiments.size} experiment(s)$suffix"
                        )
                    }
                    run.error != null -> _uiNotice.emit("High-power run failed: ${run.error}")
                }
            }
        }
        ProvenanceRecorder.setPersist { repository.saveProvenance(it) }
        HttpJsonClient.setCache(repository.responseCache())
        // A finished GGUF changes what the BYOK screen lists, and the operator
        // needs to know whether the checksum held.
        viewModelScope.launch {
            ModelDownloadService.status.collect { status ->
                when (status) {
                    is ModelDownloadStatus.Installed -> {
                        _configRevision.value += 1
                        _uiNotice.emit("${status.displayName} installed and verified.")
                    }

                    is ModelDownloadStatus.Failed ->
                        _uiNotice.emit("Download failed: ${status.message}")

                    else -> Unit
                }
            }
        }
        // Apply persisted runtime tuning to the integration layer on startup.
        ApiRotationManager.setFailureThreshold(_cbThreshold.value)
        ApiRotationManager.setCooldownMs(_cbCooldownSeconds.value * 1000L)
        HttpJsonClient.setCacheTtlMs(_cacheTtlMinutes.value * 60_000L)
        // Configure external compute/storage managers from BYOK keys (if present).
        com.example.concordance.integration.kaggle.KaggleComputeManager.updateConfig(
            listOfNotNull(
                keyStore.getKey("kaggle_api"),
                keyStore.getKey("kaggle_api_2"),
                keyStore.getKey("kaggle_api_3")
            )
        )
        com.example.concordance.integration.cloudflare.CloudflareR2Manager.updateConfig(
            accountId = keyStore.getKey("cloudflare_account_id").orEmpty(),
            bucket = "concordance-capsules-r2",
            apiToken = keyStore.getKey("cloudflare_r2"),
            endpoint = keyStore.getKey("cloudflare_r2_endpoint")
        )
        com.example.concordance.integration.aws.AwsCloudManager.updateConfig(
            accessKey = keyStore.getKey("aws_access_key_id"),
            secretKey = keyStore.getKey("aws_secret_access_key"),
            region = keyStore.getKey("aws_region") ?: "us-east-1",
            bucket = keyStore.getKey("aws_s3_bucket") ?: "concordance-capsules",
            endpoint = keyStore.getKey("aws_s3_endpoint")
        )
        com.example.concordance.integration.aws.AwsApiGatewayManager.updateConfig(
            endpoint = keyStore.getKey("aws_apigw_endpoint"),
            apiKey = keyStore.getKey("aws_apigw_key")
        )
        viewModelScope.launch { refreshCurrentRevision() }
        viewModelScope.launch { drainTaskerQueue() }
        analyzePairedCsv(_pairedCsvText.value, "plasma")
    }

    fun selectAnchor(anchor: Anchor) {
        _selectedAnchor.value = anchor
    }

    fun updateSn(newSn: Double) {
        _sn.value = newSn.coerceIn(0.1, 1.0)
    }

    fun setRange(low: Double?, high: Double?) {
        _rLow.value = low
        _rHigh.value = high
    }

    fun clearRange() {
        _rLow.value = null
        _rHigh.value = null
    }

    fun updatePowerTargetR(r: Double) {
        _powerTargetR.value = r.coerceIn(0.1, 1.2)
    }

    fun updatePowerDesired(p: Double) {
        _powerDesired.value = p
    }

    fun updatePairedCsv(text: String) {
        _pairedCsvText.value = text
    }

    fun analyzePairedCsv(csvText: String, claimModality: String = "plasma") {
        val (rows, errors) = PairedAnalyzer.parseCsv(csvText)
        _pairedErrors.value = errors
        if (rows.isNotEmpty()) {
            val res = PairedAnalyzer.analyze(rows, claimModality)
            _pairedResult.value = res
            _syntheticInputs.value = res.synthetic
        } else {
            _pairedResult.value = null
        }
    }

    fun applyPairedRangeToAdjudication() {
        val p = _pairedResult.value
        val ci = p?.relativeSensitivityCi
        if (ci != null) {
            _rLow.value = ci.first
            _rHigh.value = ci.second
            viewModelScope.launch {
                _uiNotice.emit("Applied paired sensitivity range [${String.format(java.util.Locale.US, "%.2f", ci.first)}–${String.format(java.util.Locale.US, "%.2f", ci.second)}] to adjudication.")
            }
        }
    }

    fun generateAndSaveCapsule(): Capsule? {
        val adj = adjudicationResult.value ?: return null
        val paired = pairedResult.value
        val recordIds = ProvenanceRecorder.recordIds.value.filter { it > 0 }
        val capsule = CapsuleEngine.buildCapsule(adj, paired, provenanceRecordIds = recordIds, modelRevisionId = _currentModelRevision.value?.id)
        viewModelScope.launch {
            repository.saveCapsule(capsule, recordIds)
            val suffix = if (recordIds.isNotEmpty()) " Linked ${recordIds.size} provenance records." else ""
            _uiNotice.emit("Generated citable capsule ${capsule.id} (SHA-256 sealed over every field) and added to registry!$suffix")
        }
        return capsule
    }

    fun syncCapsuleToCloud(capsule: Capsule) {
        viewModelScope.launch {
            try {
                val s3 = com.example.concordance.integration.aws.AwsCloudManager.syncCapsuleToS3(capsule)
                val r2 = com.example.concordance.integration.cloudflare.CloudflareR2Manager.syncCapsuleToR2(capsule)
                if (s3.simulated || r2.simulated) {
                    TelemetryManager.recordCapsuleSync(false)
                    TelemetryManager.analyze()
                    // Surface the HTTP status of a failed PUT: a wrong bucket policy or a
                    // path-style endpoint used to look identical to "not configured".
                    val reason = s3.errorDetail?.let { " S3 said: $it" }.orEmpty()
                    _uiNotice.emit(
                        "Cloud sync was NOT performed: no real credential/endpoint, or the upload " +
                            "was rejected.$reason Nothing left the device. Configure S3/R2 in Integrations."
                    )
                } else {
                    TelemetryManager.recordCapsuleSync(true)
                    TelemetryManager.analyze()
                    _uiNotice.emit("Synced ${capsule.id} to S3 (${s3.s3Key}) and R2 (${r2.r2Key}).")
                }
            } catch (e: Exception) {
                TelemetryManager.recordCapsuleSync(false)
                TelemetryManager.analyze()
                _uiNotice.emit("Cloud sync failed: ${e.message}")
            }
        }
    }

    fun deleteCapsule(capsuleId: String) {
        viewModelScope.launch {
            repository.deleteCapsule(capsuleId)
            AwsCloudManager.removeSyncedObject(capsuleId)
            com.example.concordance.integration.cloudflare.CloudflareR2Manager.removeSyncedObject(capsuleId)
            _uiNotice.emit("Capsule removed from local registry.")
        }
    }

    fun verifyCapsuleJson(rawJson: String) {
        _verificationResult.value = CapsuleEngine.verifyJson(rawJson)
    }

    fun clearVerification() {
        _verificationResult.value = null
    }

    fun addClaim(anchor: Anchor) {
        viewModelScope.launch {
            repository.addClaim(anchor)
            _selectedAnchor.value = anchor
            _uiNotice.emit("Added new claim '${anchor.label}' to the registry!")
        }
    }

    fun challengeClaim(
        claimId: String,
        author: String,
        notes: String,
        bEvents: Int,
        bTotal: Int,
        oEvents: Int,
        oTotal: Int
    ) {
        viewModelScope.launch {
            repository.challengeClaim(claimId, author, notes, bEvents, bTotal, oEvents, oTotal)
            val updated = repository.getClaimById(claimId)
            if (updated != null) {
                _selectedAnchor.value = updated
            }
            _uiNotice.emit("Challenge logged against claim '$claimId'. Adjudication recomputed.")
            TelemetryManager.recordChallenge()
            TelemetryManager.analyze()
            checkAutoTrigger()
        }
    }

    // --- INTEGRATION ACTIONS ---

    fun setRotationStrategy(strategy: RotationStrategy) {
        ApiRotationManager.setStrategy(strategy)
        viewModelScope.launch {
            _uiNotice.emit("API Rotation strategy updated: ${strategy.label}")
        }
    }

    fun toggleApiProvider(providerId: String) {
        ApiRotationManager.toggleProviderActive(providerId)
    }

    fun resetApiRotationStats() {
        ApiRotationManager.resetStats()
    }

    fun ingestKaggleDataset(dataset: KaggleDataset) {
        _pairedCsvText.value = dataset.csvData
        analyzePairedCsv(dataset.csvData, "plasma")
        viewModelScope.launch {
            _uiNotice.emit("Ingested Kaggle dataset '${dataset.title}' (${dataset.pairedSamples} paired rows)!")
        }
    }

    fun seedResearchQuestions() {
        viewModelScope.launch {
            try {
                val json = getApplication<Application>().assets.open("research-claims.json").bufferedReader().readText()
                val arr = org.json.JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val anchor = Anchor(
                        id = o.getString("id"),
                        label = o.getString("label"),
                        source = o.optString("source", ""),
                        evidenceGrade = EvidenceGrade.AGGREGATE,
                        brainEvents = o.optInt("brainEvents", 0),
                        brainTotal = o.optInt("brainTotal", 0),
                        otherEvents = o.optInt("otherEvents", 0),
                        otherTotal = o.optInt("otherTotal", 0),
                        notes = o.optString("notes", ""),
                        isCustom = false,
                        dateAdded = o.optString("dateAdded", "2026-10-26"),
                        modelRevisionId = o.optString("modelRevisionId", null)
                    )
                    repository.saveClaim(ClaimEntity.fromAnchor(anchor))
                }
                _uiNotice.emit("Seeded ${arr.length()} research-question anchors into the claim ledger.")
            } catch (e: Exception) {
                _uiNotice.emit("Could not seed research questions: ${e.message}")
            }
        }
    }

    fun seedCrossDomainExperiments() {
        viewModelScope.launch {
            try {
                val json = getApplication<Application>().assets.open("cross-domain-claims.json").bufferedReader().readText()
                val arr = org.json.JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val anchor = Anchor(
                        id = o.getString("id"),
                        label = o.getString("label"),
                        source = o.optString("source", ""),
                        evidenceGrade = EvidenceGrade.AGGREGATE,
                        brainEvents = o.optInt("brainEvents", 0),
                        brainTotal = o.optInt("brainTotal", 0),
                        otherEvents = o.optInt("otherEvents", 0),
                        otherTotal = o.optInt("otherTotal", 0),
                        notes = o.optString("notes", ""),
                        isCustom = false,
                        dateAdded = o.optString("dateAdded", "2026-10-26"),
                        modelRevisionId = o.optString("modelRevisionId", null)
                    )
                    repository.saveClaim(ClaimEntity.fromAnchor(anchor))
                }
                _uiNotice.emit("Seeded ${arr.length()} cross-domain experiment anchors into the claim ledger.")
            } catch (e: Exception) {
                _uiNotice.emit("Could not seed cross-domain experiments: ${e.message}")
            }
        }
    }

    fun runKagglePermutationTest() {
        val anchor = _selectedAnchor.value ?: return
        viewModelScope.launch {
            _uiNotice.emit("Dispatched 10,000-Permutation Test to Kaggle Compute cluster...")
            val job = KaggleComputeManager.triggerPermutationTestKernel(anchor)
            _uiNotice.emit("Kaggle Kernel Finished: ${job.resultSummary?.take(60)}...")
        }
    }

    fun runKaggleBootstrapCI() {
        val anchor = _selectedAnchor.value ?: return
        viewModelScope.launch {
            _uiNotice.emit("Dispatched Non-Parametric Bootstrap to Kaggle Compute cluster...")
            val job = KaggleComputeManager.triggerBootstrapConfidenceIntervalKernel(anchor)
            _uiNotice.emit("Kaggle Kernel Finished: ${job.resultSummary?.take(60)}...")
        }
    }

    fun syncCapsuleToAws(capsule: Capsule) {
        viewModelScope.launch {
            _uiNotice.emit("Syncing capsule ${capsule.id} to AWS S3 Cloud Vault...")
            val obj = AwsCloudManager.syncCapsuleToS3(capsule)
            _uiNotice.emit("Synced to S3! Presigned URL active (${obj.s3Key})")
        }
    }

    fun updateAwsConfig(region: String, bucket: String) {
        AwsCloudManager.updateConfig(region, bucket)
        viewModelScope.launch {
            _uiNotice.emit("AWS S3 config updated (Bucket: $bucket, Region: $region)")
        }
    }

    fun setPaperAnalysisEngine(engine: AnalysisEngine) {
        SciencePaperManager.setEngine(engine)
    }

    fun searchSciencePapers(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiNotice.emit("Querying live literature APIs (Europe PMC + OpenAlex) for '$query'...")
            val result = SciencePaperManager.searchLivePapers(query)
            if (result.offlineFallback) {
                _uiNotice.emit("No live records returned (offline or no match). Showing the cached corpus.")
            } else {
                _uiNotice.emit(
                    "Imported ${result.liveRecords} live records from ${result.providers.joinToString(", ")}."
                )
            }
        }
    }

    fun clearProvenanceLog() {
        ProvenanceRecorder.clear()
    }

    fun loadMoreSciencePapers(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            val added = SciencePaperManager.loadMorePapers(query)
            _uiNotice.emit(
                if (added == 0) "No more live records." else "Loaded $added more records."
            )
        }
    }

    fun loadMoreClinicalTrials(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            val added = com.example.concordance.integration.clinicaltrials.ClinicalTrialsManager.loadMoreTrials(query)
            _uiNotice.emit(
                if (added == 0) "No more studies." else "Loaded $added more studies."
            )
        }
    }

    fun importCuratedEvidenceAsClaim(evidence: CuratedVariantEvidence) {
        val anchor = Anchor(
            id = "civic_${evidence.civicEvidenceId}",
            label = "${evidence.gene} ${evidence.variant} (${evidence.significance})",
            // The "Concordance import" prefix marks this as engine-imported, not
            // human-sourced evidence, so the learning guard refuses to train on it.
            source = "Concordance import (CIViC EID${evidence.civicEvidenceId}): ${evidence.civicUrl}",
            evidenceGrade = EvidenceGrade.ABSTRACT_ONLY,
            notes = "${evidence.description} | Therapies: ${evidence.therapies.joinToString(", ").ifBlank { "none" }} | Disease: ${evidence.disease.ifBlank { "unspecified" }}",
            isCustom = true,
            dateAdded = ProvenanceRecorder.nowUtc().take(10) // yyyy-MM-dd, UTC
        )
        addClaim(anchor)
        viewModelScope.launch {
            _uiNotice.emit("Imported CIViC evidence as draft claim '${anchor.label}'.")
        }
    }

    fun searchCuratedEvidence(geneSymbol: String) {
        if (geneSymbol.isBlank()) return
        viewModelScope.launch {
            _isSearchingCurated.value = true
            try {
                _uiNotice.emit("Querying CIViC GraphQL for curated '$geneSymbol' evidence...")
                val evidence = IntegrationRegistry.searchCuratedEvidence(geneSymbol)
                _curatedEvidence.value = evidence
                _uiNotice.emit(
                    if (evidence.isEmpty()) {
                        "CIViC: no curated evidence returned for '$geneSymbol' (use a gene symbol such as ESR1)."
                    } else {
                        "CIViC: ${evidence.size} curated evidence items for '$geneSymbol'."
                    }
                )
            } finally {
                _isSearchingCurated.value = false
            }
        }
    }

    fun searchCohorts(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            _isSearchingCurated.value = true
            try {
                _uiNotice.emit("Querying cBioPortal for cohorts matching '$keyword'...")
                val cohorts = IntegrationRegistry.searchCohorts(keyword)
                _cohortStudies.value = cohorts
                _uiNotice.emit(
                    if (cohorts.isEmpty()) {
                        "cBioPortal: no cohorts matched '$keyword'."
                    } else {
                        "cBioPortal: ${cohorts.size} public cohorts matched '$keyword'."
                    }
                )
            } finally {
                _isSearchingCurated.value = false
            }
        }
    }

    fun searchSomaticMutations(studyId: String, geneSymbol: String) {
        if (studyId.isBlank() || geneSymbol.isBlank()) return
        viewModelScope.launch {
            _isSearchingCurated.value = true
            try {
                _uiNotice.emit("Querying cBioPortal mutations for $geneSymbol in $studyId...")
                val mutations = IntegrationRegistry.searchMutations(studyId, geneSymbol)
                _somaticMutations.value = mutations
                _uiNotice.emit(
                    if (mutations.isEmpty()) {
                        "cBioPortal: no $geneSymbol mutations found in $studyId."
                    } else {
                        "cBioPortal: ${mutations.size} $geneSymbol mutation calls in $studyId."
                    }
                )
            } finally {
                _isSearchingCurated.value = false
            }
        }
    }

    // --- BYOK: API keys + license acknowledgments ---
    fun setApiKey(providerId: String, key: String?) {
        keyStore.setKey(providerId, key)
    }

    fun acknowledgeLicense(license: LicenseClass) {
        licenseStore.acknowledge(license)
    }

    fun revokeLicense(license: LicenseClass) {
        licenseStore.revoke(license)
    }

    fun searchOncoKBLevels() {
        viewModelScope.launch {
            _isSearchingCurated.value = true
            try {
                _uiNotice.emit("Querying OncoKB evidence levels...")
                val levels = IntegrationRegistry.searchOncoKBLevels(keyStore, licenseStore)
                _uiNotice.emit(
                    if (levels.isEmpty()) {
                        "OncoKB: no levels returned (license not acknowledged?)."
                    } else {
                        "OncoKB: ${levels.size} level entries."
                    }
                )
            } finally {
                _isSearchingCurated.value = false
            }
        }
    }

    fun searchOncoKBGene(geneSymbol: String) {
        if (geneSymbol.isBlank()) return
        viewModelScope.launch {
            _isSearchingCurated.value = true
            try {
                _uiNotice.emit("Querying OncoKB for $geneSymbol...")
                val gene = IntegrationRegistry.searchOncoKBGene(geneSymbol, keyStore, licenseStore)
                _uiNotice.emit(
                    if (gene.isEmpty()) {
                        "OncoKB: no gene annotation (needs license + token)."
                    } else {
                        "OncoKB: ${gene.size} gene annotation entries."
                    }
                )
            } finally {
                _isSearchingCurated.value = false
            }
        }
    }

    // --- Runtime tuning: cache TTL + circuit breaker ---
    fun setFailureThreshold(n: Int) {
        val v = n.coerceAtLeast(1)
        ApiRotationManager.setFailureThreshold(v)
        _cbThreshold.value = v
        persistTuning()
        viewModelScope.launch { _uiNotice.emit("Circuit breaker threshold set to $v failures.") }
    }

    fun setCooldownSeconds(s: Int) {
        val v = s.coerceAtLeast(5)
        ApiRotationManager.setCooldownMs(v * 1000L)
        _cbCooldownSeconds.value = v
        persistTuning()
        viewModelScope.launch { _uiNotice.emit("Circuit breaker cooldown set to ${v}s.") }
    }

    fun setCacheTtlMinutes(minutes: Int) {
        val v = minutes.coerceAtLeast(1)
        HttpJsonClient.setCacheTtlMs(v * 60_000L)
        _cacheTtlMinutes.value = v
        persistTuning()
        viewModelScope.launch { _uiNotice.emit("Response cache TTL set to $v min.") }
    }

    fun setAutoRoundThreshold(n: Int) {
        val v = n.coerceAtLeast(1)
        _autoRoundThreshold.value = v
        persistTuning()
    }

    fun setStabilityThreshold(v: Double) {
        val c = v.coerceIn(0.0, 1.0)
        _stabilityThreshold.value = c
        persistTuning()
    }

    fun setKaggleToken(token: String?) {
        keyStore.setKey("kaggle_api", token)
        com.example.concordance.integration.kaggle.KaggleComputeManager.updateConfig(token)
    }

    fun setCloudflareConfig(accountId: String?, token: String?, endpoint: String?) {
        keyStore.setKey("cloudflare_account_id", accountId)
        keyStore.setKey("cloudflare_r2", token)
        keyStore.setKey("cloudflare_r2_endpoint", endpoint)
        com.example.concordance.integration.cloudflare.CloudflareR2Manager.updateConfig(
            accountId = accountId.orEmpty(),
            bucket = "concordance-capsules-r2",
            apiToken = token,
            endpoint = endpoint
        )
    }

    fun setAwsConfig(
        accessKey: String?,
        secretKey: String?,
        region: String?,
        bucket: String?,
        endpoint: String?,
        sessionToken: String? = null
    ) {
        keyStore.setKey("aws_access_key_id", accessKey)
        keyStore.setKey("aws_secret_access_key", secretKey)
        keyStore.setKey("aws_session_token", sessionToken)
        keyStore.setKey("aws_region", region)
        keyStore.setKey("aws_s3_bucket", bucket)
        keyStore.setKey("aws_s3_endpoint", endpoint)
        com.example.concordance.integration.aws.AwsCloudManager.updateConfig(
            accessKey = accessKey,
            secretKey = secretKey,
            region = region,
            bucket = bucket,
            endpoint = endpoint,
            sessionToken = sessionToken
        )
    }

    fun setAwsApiGatewayConfig(endpoint: String?, apiKey: String?) {
        keyStore.setKey("aws_apigw_endpoint", endpoint)
        keyStore.setKey("aws_apigw_key", apiKey)
        com.example.concordance.integration.aws.AwsApiGatewayManager.updateConfig(endpoint, apiKey)
    }

    /**
     * Drain pending Tasker automation actions (recorded by TaskerTriggerReceiver)
     * and dispatch them. Called on init and whenever the app resumes.
     */
    fun drainTaskerQueue() {
        viewModelScope.launch {
            val ctx = getApplication<android.app.Application>()
            val actions = com.example.concordance.integration.tasker.TaskerBridge.drain(ctx)
            for (entry in actions) {
                val (action, extra) = if (entry.contains("=")) {
                    entry.substringBefore("=") to entry.substringAfter("=")
                } else entry to null
                when (action) {
                    com.example.concordance.integration.tasker.TaskerBridge.ACTION_RUN_LEARNING_ROUND ->
                        runLearningRound()
                    com.example.concordance.integration.tasker.TaskerBridge.ACTION_CHECK_AUTO_TRIGGER ->
                        checkAutoTrigger()
                    com.example.concordance.integration.tasker.TaskerBridge.ACTION_RE_ADJUDICATE ->
                        reAdjudicateUnder(extra)
                    com.example.concordance.integration.tasker.TaskerBridge.ACTION_SYNC_CAPSULES -> {
                        // Best-effort: sync each stored capsule to the configured cloud.
                        // (No-op in simulation mode.)
                    }
                }
            }
            if (actions.isNotEmpty()) {
                _uiNotice.emit("Drained ${actions.size} Tasker automation action(s).")
            }
        }
    }

    /**
     * Continual mode (Phase 3): when the challenge count has grown by at least
     * [autoRoundThreshold] since the last auto-round, run a DRY-RUN round. This
     * NEVER promotes - it only proposes. Human approval is still required.
     */
    fun checkAutoTrigger() {
        viewModelScope.launch {
            val challenges = repository.getAllChallenges()
            val count = challenges.size
            val last = _lastAutoRoundChallengeCount.value
            if (count - last >= _autoRoundThreshold.value) {
                _lastAutoRoundChallengeCount.value = count
                persistTuning()
                _uiNotice.emit("Auto-triggered learning round ($count challenges accumulated). Proposal only - no auto-promotion.")
                runLearningRound()
            }
        }
    }

    private fun persistTuning() {
        tuningStore.save(
            RuntimeTuning(
                failureThreshold = _cbThreshold.value,
                cooldownSeconds = _cbCooldownSeconds.value,
                cacheTtlMinutes = _cacheTtlMinutes.value,
                autoRoundThreshold = _autoRoundThreshold.value,
                lastAutoRoundChallengeCount = _lastAutoRoundChallengeCount.value,
                stabilityThreshold = _stabilityThreshold.value
            )
        )
    }

    fun analyzeSciencePaper(paper: SciencePaper) {
        viewModelScope.launch {
            _uiNotice.emit("Analyzing paper '${paper.title.take(30)}...' with ${SciencePaperManager.selectedEngine.value.label}...")
            val output = SciencePaperManager.analyzePaper(paper)
            _uiNotice.emit("Analysis complete in ${output.executionTimeMs}ms! Extracted ${output.extraction.gene ?: "target"}.")
        }
    }

    fun importAnalyzedPaperAsClaim(output: PaperAnalysisOutput) {
        addClaim(output.convertedAnchor)
    }

    fun queryPubChem(name: String) {
        viewModelScope.launch {
            _uiNotice.emit("Querying NCBI PubChem PUG-REST for '$name'...")
            val res = com.example.concordance.integration.pubchem.PubChemManager.queryPubChemPugRest(name)
            if (res != null) {
                _uiNotice.emit("PubChem: Retrieved ${res.name} (CID: ${res.cid}, Formula: ${res.molecularFormula})")
            } else {
                _uiNotice.emit("PubChem returned no record for '$name'. Check the spelling or try a CID.")
            }
        }
    }

    fun queryClinicalTrials(query: String) {
        viewModelScope.launch {
            _uiNotice.emit("Querying ClinicalTrials.gov v2 REST API for '$query'...")
            val res = com.example.concordance.integration.clinicaltrials.ClinicalTrialsManager.queryClinicalTrialsGov(query)
            val src = if (com.example.concordance.integration.clinicaltrials.ClinicalTrialsManager.isUsingBenchmarkFallback) {
                "bundled corpus"
            } else {
                "live registry"
            }
            _uiNotice.emit("ClinicalTrials: ${res.size} studies returned ($src).")
        }
    }

    // --- ASYNC TASK SUBMISSION & OS SCHEDULER DISPATCH ---

    fun submitAsyncComputeJob(
        dataClass: JobDataClass,
        routing: WorkerRoutingPolicy = dataClass.defaultWorker,
        inputs: String = "",
        modelRev: String = dataClass.defaultModel,
        params: Map<String, String> = emptyMap()
    ) {
        viewModelScope.launch {
            val response = JobQueueManager.submitHeavyProcess(
                dataClass = dataClass,
                routingPolicy = routing,
                inputs = inputs.ifEmpty { "Anchor: ${_selectedAnchor.value?.label ?: "Giannoudis 2024"}" },
                modelRevision = modelRev,
                params = params
            )

            // Schedule on Android WorkManager with network and battery constraints
            ConcordanceWorkManagerScheduler.scheduleComputeTask(
                context = getApplication(),
                jobResponse = response,
                inputs = inputs
            )

            _uiNotice.emit("Submitted to FastAPI worker! Task UUID: ${response.taskUuid.take(16)}... Enqueued on WorkManager scheduler with battery/network constraints.")
        }
    }

    fun submitImagingExtraction(fileUri: String, page: Int) {
        viewModelScope.launch {
            val response = JobQueueManager.submitHeavyProcess(
                dataClass = JobDataClass.IMAGING_VLM_FIGURE_EXTRACTION,
                routingPolicy = JobDataClass.IMAGING_VLM_FIGURE_EXTRACTION.defaultWorker,
                inputs = fileUri,
                modelRevision = JobDataClass.IMAGING_VLM_FIGURE_EXTRACTION.defaultModel,
                params = mapOf("page" to page.toString())
            )
            ConcordanceWorkManagerScheduler.scheduleComputeTask(
                context = getApplication(),
                jobResponse = response,
                inputs = fileUri
            )
            _uiNotice.emit("Imaging extraction job submitted: ${response.taskUuid}")
        }
    }

    fun triggerStuckTaskRecovery() {
        val recovered = JobQueueManager.checkAndRecoverStuckTasks(stuckThresholdMs = 300_000)
        viewModelScope.launch {
            if (recovered > 0) {
                _uiNotice.emit("Stuck-Task Recovery: Detected and reset $recovered task(s) (>5m processing) to PENDING.")
            } else {
                _uiNotice.emit("Stuck-Task Recovery: All worker tasks are active within healthy 5m SLA limits.")
            }
        }
    }

    fun simulateStuckTask() {
        val stuck = JobQueueManager.simulateStuckTaskForTesting()
        viewModelScope.launch {
            _uiNotice.emit("Simulated task '${stuck.taskUuid.take(16)}' stuck for 6m. Tap 'Run Stuck-Task Recovery' to verify auto-reset.")
        }
    }

    // ------------------------------------------------------------------
    // Self-hosted adjudication agent: typed client, on-device voice, figure upload.
    //
    // This is the only server the app talks to whose contents we do not own, so the rules
    // are explicit here: refusals are shown as results (never as errors), audio never
    // leaves the device, OCR text is flagged unverified until a human confirms it, and an
    // upload must be acknowledged before it is sent.
    // ------------------------------------------------------------------

    private val agentSettings = AgentSettingsStore(getApplication())
    private val agentClient = AgentClient()

    private val _agentConfig = MutableStateFlow(agentSettings.load())
    val agentConfig: StateFlow<AgentConfig> = _agentConfig.asStateFlow()

    /** INDRA is called directly from the phone; the agent host is not involved. */
    private val indraClient = IndraClient()

    private val _easyMode = MutableStateFlow(agentSettings.easyModeByDefault())
    val easyMode: StateFlow<Boolean> = _easyMode.asStateFlow()

    private val _agentBusy = MutableStateFlow(false)
    val agentBusy: StateFlow<Boolean> = _agentBusy.asStateFlow()

    private val _agentStatus = MutableStateFlow("Not checked yet.")
    val agentStatus: StateFlow<String> = _agentStatus.asStateFlow()

    /** The ecosystem APIs the agent can call; empty until the roster is asked for. */
    private val _agentFleet = MutableStateFlow<com.example.concordance.integration.agent.AgentFleetRoster?>(null)
    val agentFleet: StateFlow<com.example.concordance.integration.agent.AgentFleetRoster?> = _agentFleet.asStateFlow()

    private val _agentLoop = MutableStateFlow<com.example.concordance.integration.agent.AgentLoopResult?>(null)
    val agentLoop: StateFlow<com.example.concordance.integration.agent.AgentLoopResult?> = _agentLoop.asStateFlow()

    /** Paper chains on the agent, and the one the operator opened. */
    private val _agentPaperRuns = MutableStateFlow<List<AgentResearchRun>>(emptyList())
    val agentPaperRuns: StateFlow<List<AgentResearchRun>> = _agentPaperRuns.asStateFlow()

    private val _agentPaper = MutableStateFlow<AgentResearchRunDetail?>(null)
    val agentPaper: StateFlow<AgentResearchRunDetail?> = _agentPaper.asStateFlow()

    private val _agentPaperVerification = MutableStateFlow<AgentResearchVerification?>(null)
    val agentPaperVerification: StateFlow<AgentResearchVerification?> = _agentPaperVerification.asStateFlow()

    private val _agentPapersBusy = MutableStateFlow(false)
    val agentPapersBusy: StateFlow<Boolean> = _agentPapersBusy.asStateFlow()

    /** Why the last paper-list read failed, so the card can say it instead of "none". */
    private val _agentPapersError = MutableStateFlow<String?>(null)
    val agentPapersError: StateFlow<String?> = _agentPapersError.asStateFlow()

    private val _agentAnchors = MutableStateFlow<List<AgentAnchor>>(emptyList())
    val agentAnchors: StateFlow<List<AgentAnchor>> = _agentAnchors.asStateFlow()

    private val _agentJob = MutableStateFlow<AgentJob?>(null)
    val agentJob: StateFlow<AgentJob?> = _agentJob.asStateFlow()

    private val _agentCorrection = MutableStateFlow<CorrectionResult?>(null)
    val agentCorrection: StateFlow<CorrectionResult?> = _agentCorrection.asStateFlow()

    private val _agentRefereeReport = MutableStateFlow<AgentRefereeReport?>(null)
    val agentRefereeReport: StateFlow<AgentRefereeReport?> = _agentRefereeReport.asStateFlow()

    private val _agentRefereeExperiments = MutableStateFlow<List<AgentExperiment>>(emptyList())
    val agentRefereeExperiments: StateFlow<List<AgentExperiment>> = _agentRefereeExperiments.asStateFlow()

    private val _agentCorrectionEasy = MutableStateFlow<EasyModeView?>(null)
    val agentCorrectionEasy: StateFlow<EasyModeView?> = _agentCorrectionEasy.asStateFlow()

    private val _agentEasyView = MutableStateFlow<EasyModeView?>(null)
    val agentEasyView: StateFlow<EasyModeView?> = _agentEasyView.asStateFlow()

    private val _agentUpload = MutableStateFlow<UploadedFigure?>(null)
    val agentUpload: StateFlow<UploadedFigure?> = _agentUpload.asStateFlow()

    private val _agentTranscript = MutableStateFlow("")
    private val _researchSeed = MutableStateFlow<String?>(null)
    val researchSeed: StateFlow<String?> = _researchSeed.asStateFlow()
    val agentTranscript: StateFlow<String> = _agentTranscript.asStateFlow()

    private val _agentResearchResult = MutableStateFlow<AgentResearchResult?>(null)
    val agentResearchResult: StateFlow<AgentResearchResult?> = _agentResearchResult.asStateFlow()

    private val _agentUploadAck = MutableStateFlow(agentSettings.hasAcknowledgedUpload())
    val agentUploadAck: StateFlow<Boolean> = _agentUploadAck.asStateFlow()

    // ---- INDRA prior knowledge (public literature via the agent) ----
    private val _indraLoading = MutableStateFlow(false)
    val indraLoading: StateFlow<Boolean> = _indraLoading.asStateFlow()

    private val _indraKnowledge = MutableStateFlow<IndraKnowledge?>(null)
    val indraKnowledge: StateFlow<IndraKnowledge?> = _indraKnowledge.asStateFlow()

    private val _indraContradictions = MutableStateFlow<IndraContradictions?>(null)
    val indraContradictions: StateFlow<IndraContradictions?> = _indraContradictions.asStateFlow()

    private val _indraError = MutableStateFlow<String?>(null)
    val indraError: StateFlow<String?> = _indraError.asStateFlow()

    private var indraJob: kotlinx.coroutines.Job? = null

    /**
     * Look up what public sources already say about a gene (and optionally a
     * partner entity). Public knowledge only: this never carries patient data,
     * and the result is background for a human, never a verdict.
     */
    fun lookupIndra(subject: String, objectName: String?) {
        val gene = subject.trim()
        if (gene.isBlank()) {
            agentNotice("Enter a gene or protein first.")
            return
        }
        val partner = objectName?.trim()?.takeIf { it.isNotBlank() }
        indraJob?.cancel()
        indraJob = viewModelScope.launch {
            _indraLoading.value = true
            _indraError.value = null
            _indraKnowledge.value = null
            _indraContradictions.value = null
            val prior = indraClient.priorKnowledge(gene, partner)
            val resolvedPartner = when (prior) {
                is IndraResult.Ok -> {
                    _indraKnowledge.value = prior.value
                    partner ?: prior.value.statements
                        .firstOrNull { it.objectName.isNotBlank() }?.objectName
                }
                is IndraResult.Refused -> {
                    _indraError.value = prior.reason
                    null
                }
                is IndraResult.Unavailable -> {
                    _indraError.value =
                        "INDRA unavailable: ${prior.reason} Everything else keeps working."
                    null
                }
            }
            // Contradictions need an object: use the caller's, or the strongest
            // partner the prior lookup actually returned.
            if (prior is IndraResult.Ok && resolvedPartner != null) {
                when (val contradiction = indraClient.contradictions(gene, resolvedPartner)) {
                    is IndraResult.Ok -> _indraContradictions.value = contradiction.value
                    else -> Unit // secondary signal; the prior card still stands
                }
            }
            _indraLoading.value = false
        }
    }

    fun clearIndra() {
        indraJob?.cancel()
        _indraLoading.value = false
        _indraKnowledge.value = null
        _indraContradictions.value = null
        _indraError.value = null
    }

    // ---- Community publication (phone -> website) ----
    private val supabaseAuth by lazy {
        SupabaseAuth(createSupabaseSessionStore(getApplication()))
    }
    private val _communityBaseUrl = MutableStateFlow(agentSettings.communityBaseUrl())
    val communityBaseUrl: StateFlow<String> = _communityBaseUrl.asStateFlow()

    private val _communityPublishing = MutableStateFlow(false)
    val communityPublishing: StateFlow<Boolean> = _communityPublishing.asStateFlow()

    // ---- Sync conflicts (phone vs server divergence) ----
    private val supabaseSyncManager by lazy {
        SupabaseSyncManager(
            supabaseAuth,
            PostgRestClient(supabaseAuth),
            ConcordanceDatabase.getDatabase(getApplication())
        )
    }
    val syncConflicts = SyncConflictBus.conflicts

    fun clearSyncConflicts() = SyncConflictBus.clear()

    /**
     * Record the operator's choice and apply it to the local/server records.
     * The bus drives the UI banner; the manager does the data mutation on the
     * worker's own dispatcher.
     */
    fun resolveSyncConflict(conflict: SyncConflict, resolution: String) {
        SyncConflictBus.resolve(conflict, resolution)
        viewModelScope.launch {
            supabaseSyncManager.resolveConflict(conflict.entityType, conflict.entityId, resolution)
        }
    }

    /**
     * Bumped after [importConfigFromJson] so settings screens re-read the keystore
     * instead of showing whatever was in their `remember` slot at first composition.
     */
    private val _configRevision = MutableStateFlow(0)
    val configRevision: StateFlow<Int> = _configRevision.asStateFlow()

    /**
     * Import all integration credentials from a JSON object.
     * Accepts the same key names as the ByokSettingsTab fields.
     */
    fun importConfigFromJson(json: org.json.JSONObject) {
        val agentUrl = json.optString("agent_url", "").ifBlank { null }
        val agentKey = json.optString("agent_api_key", "").ifBlank { null }
        if (agentUrl != null) {
            updateAgentConfig(agentUrl, agentKey, json.optString("agent_model").ifBlank { null })
        }
        json.optString("kaggle_api").ifBlank { null }?.let { setKaggleToken(it) }
        json.optString("cloudflare_account_id").ifBlank { null }?.let { acctId ->
            setCloudflareConfig(
                accountId = acctId,
                token = json.optString("cloudflare_r2").ifBlank { null },
                endpoint = json.optString("cloudflare_r2_endpoint").ifBlank { null }
            )
        }
        json.optString("aws_access_key_id").ifBlank { null }?.let { ak ->
            setAwsConfig(
                accessKey = ak,
                secretKey = json.optString("aws_secret_access_key").ifBlank { null },
                region = json.optString("aws_region", "").ifBlank { null },
                bucket = json.optString("aws_s3_bucket", "").ifBlank { null },
                endpoint = json.optString("aws_s3_endpoint", "").ifBlank { null },
                sessionToken = json.optString("aws_session_token", "").ifBlank { null }
            )
        }
        json.optString("aws_apigw_endpoint").ifBlank { null }?.let { ep ->
            setAwsApiGatewayConfig(ep, json.optString("aws_apigw_key").ifBlank { null })
        }
        json.optString("opencode_api").ifBlank { null }?.let { keyStore.setKey("opencode_api", it) }
        json.optString("oncokb_api").ifBlank { null }?.let { keyStore.setKey("oncokb_api", it) }
        json.optString("openai_api").ifBlank { null }?.let { keyStore.setKey("openai_api", it) }
        json.optString("openrouter_api").ifBlank { null }?.let { keyStore.setKey("openrouter_api", it) }
        json.optString("gemini_api").ifBlank { null }?.let { keyStore.setKey("gemini_api", it) }
        json.optString("mistral_api").ifBlank { null }?.let { keyStore.setKey("mistral_api", it) }
        json.optString("nvidia_api").ifBlank { null }?.let { keyStore.setKey("nvidia_api", it) }
        json.optString("ollama_url").ifBlank { null }?.let { keyStore.setKey("ollama_url", it) }
        _configRevision.value = _configRevision.value + 1
        viewModelScope.launch { _uiNotice.emit("Configuration imported successfully.") }
    }

    fun setCommunityBaseUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        if (clean.isBlank()) return
        agentSettings.setCommunityBaseUrl(clean)
        _communityBaseUrl.value = clean
        agentNotice("Community URL saved.")
    }

    fun publishClaimToCommunity(claim: Anchor) {
        if (_communityPublishing.value) return
        viewModelScope.launch {
            _communityPublishing.value = true
            try {
                CommunityPublisher.publishClaim(supabaseAuth, _communityBaseUrl.value, claim)
                    .onSuccess { published ->
                        val stamp = if (published.timestamped) " with an RFC 3161 timestamp" else ""
                        _uiNotice.emit("Published ${claim.label.take(40)} to the community$stamp. ${published.url}")
                    }
                    .onFailure { _uiNotice.emit("Publish failed: ${it.message}") }
            } finally {
                _communityPublishing.value = false
            }
        }
    }

    fun publishCapsuleToCommunity(capsule: Capsule) {
        if (_communityPublishing.value) return
        viewModelScope.launch {
            _communityPublishing.value = true
            try {
                CommunityPublisher.publishCapsule(supabaseAuth, _communityBaseUrl.value, capsule)
                    .onSuccess { published ->
                        val stamp = if (published.timestamped) " with an RFC 3161 timestamp" else ""
                        _uiNotice.emit("Published ${capsule.id} to the community$stamp. ${published.url}")
                    }
                    .onFailure { _uiNotice.emit("Publish failed: ${it.message}") }
            } finally {
                _communityPublishing.value = false
            }
        }
    }

    private val dictation = OnDeviceSpeechRecognizer(getApplication())

    /** Kokoro when its assets are installed, otherwise the phone's own voice. */
    private val speech: SpeechSynthesizer by lazy { SpeechOutput.forContext(getApplication()) }

    /** Live state of the embedded llama.cpp server. */
    val localLlmStatus: StateFlow<LocalLlmStatus> = LocalLlmService.status

    fun installedLocalModels(): List<java.io.File> = LocalModels.installedModels(getApplication())

    fun startLocalModel(model: java.io.File) = LocalLlmService.start(getApplication(), model)

    fun stopLocalModel() = LocalLlmService.stop(getApplication())

    fun localKokoroReady(): Boolean = LocalModels.kokoroReady(getApplication())

    /** Live download state, shared with [ModelDownloadService]. */
    val modelDownloadStatus: StateFlow<ModelDownloadStatus> = ModelDownloadService.status

    fun localFreeSpaceBytes(): Long = LocalModels.freeSpaceBytes(getApplication())

    /**
     * Download a catalog model through the foreground service. Refuses when the
     * remaining space cannot hold the weights plus head-room for the temp file.
     */
    fun downloadLocalModel(model: InstallableModel) {
        if (modelDownloadStatus.value is ModelDownloadStatus.Downloading) {
            agentNotice("A model download is already running.")
            return
        }
        val target = java.io.File(LocalModels.modelsDir(getApplication()), model.fileName)
        if (target.isFile && target.length() == model.sizeBytes) {
            agentNotice("${model.displayName} is already installed. Use Verify to re-hash it.")
            return
        }
        val free = LocalModels.freeSpaceBytes(getApplication())
        val needed = model.sizeBytes + 256L * 1024 * 1024
        if (free in 1 until needed) {
            agentNotice(
                "Not enough storage: ${LocalModels.describeBytes(free)} free, " +
                    "${LocalModels.describeBytes(needed)} needed (model plus temp file)."
            )
            return
        }
        ModelDownloadService.start(getApplication(), model)
    }

    fun cancelLocalModelDownload() = ModelDownloadService.cancel(getApplication())

    fun deleteLocalModel(file: java.io.File) {
        val running = (localLlmStatus.value as? LocalLlmStatus.Ready)?.model == file.name
        if (running) {
            agentNotice("Stop ${file.name} before deleting it.")
            return
        }
        if (LocalModels.deleteModel(getApplication(), file)) {
            _configRevision.value += 1
            agentNotice("Deleted ${file.name}.")
        } else {
            agentNotice("Could not delete ${file.name}.")
        }
    }

    /** Re-hash an installed catalog model and report whether it matches. */
    fun verifyLocalModel(model: InstallableModel) {
        viewModelScope.launch {
            val file = java.io.File(LocalModels.modelsDir(getApplication()), model.fileName)
            if (!file.isFile) {
                _uiNotice.emit("${model.displayName} is not installed.")
                return@launch
            }
            _uiNotice.emit("Verifying ${model.fileName}…")
            val problem = ModelDownloader().verify(file, model)
            _uiNotice.emit(
                if (problem == null) {
                    "Verified ${model.fileName}: SHA-256 matches the publisher."
                } else {
                    "Verification failed: $problem"
                }
            )
        }
    }

    /** Ask the BYOK screen to re-read the models directory. */
    fun refreshLocalModels() {
        _configRevision.value += 1
    }

    /** Speaks a fixed line so the operator can confirm the on-device voice. */
    fun testLocalVoice() {
        viewModelScope.launch {
            val spoken = speech.speak("Concordance on-device voice is ready.")
            if (!spoken) {
                _uiNotice.emit("Voice unavailable: ${speech.engineName}")
            }
        }
    }

    val speechEngineName: String get() = speech.engineName

    /** Why dictation is unavailable, or null when on-device dictation works. */
    val dictationUnavailableReason: String? get() = dictation.unavailableReason()

    /** Why Kokoro is not in use, or null when it is speaking for us. */
    val kokoroUnavailableReason: String?
        get() = if (speech.engineName.startsWith("Kokoro")) {
            null
        } else {
            SpeechOutput.kokoroUnavailableReason(getApplication())
        }

    private var dictationJob: Job? = null

    private fun agentNotice(message: String) {
        viewModelScope.launch { _uiNotice.emit(message) }
    }

    /** Surface a message from the screen (e.g. a rejected file) as a snackbar. */
    fun notify(message: String) = agentNotice(message)

    fun updateAgentConfig(baseUrl: String, apiKey: String?, modelName: String? = null) {
        val updated = _agentConfig.value.copy(
            baseUrl = baseUrl.trim(),
            apiKey = apiKey?.takeIf { it.isNotBlank() },
            modelName = modelName?.takeIf { it.isNotBlank() } ?: _agentConfig.value.modelName
        )
        agentSettings.save(updated)
        _agentConfig.value = updated
        agentNotice("Agent settings saved.")
    }

    fun setEasyMode(enabled: Boolean) {
        _easyMode.value = enabled
        agentSettings.setEasyModeByDefault(enabled)
    }

    fun acknowledgeUploads() {
        agentSettings.setAcknowledgedUpload(true)
        _agentUploadAck.value = true
        agentNotice("Figure uploads acknowledged. Images are read by your own agent, not a managed platform.")
    }

    fun clearAgentResult() {
        _agentJob.value = null
        _agentEasyView.value = null
        _agentUpload.value = null
        _agentCorrection.value = null
        _agentCorrectionEasy.value = null
        _agentRefereeReport.value = null
        _agentRefereeExperiments.value = emptyList()
    }

    /**
     * Detection-artifact correction: what the reported difference becomes once the
     * assay's blind spot is accounted for. Refusals (an inadjudicable anchor, an
     * unknown id) come back as a reason, not as zero.
     */
    fun runAgentCorrection(anchorId: String, site: String, snRaw: String) {
        if (anchorId.isBlank()) {
            agentNotice("Choose or type an anchor id first.")
            return
        }
        val trimmedSn = snRaw.trim()
        val sn = trimmedSn.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        if (trimmedSn.isNotEmpty() && (sn == null || sn <= 0.0 || sn > 1.0)) {
            agentNotice("Assay sensitivity must be a number greater than 0 and at most 1 (for example 0.90).")
            return
        }
        viewModelScope.launch {
            _agentBusy.value = true
            _agentCorrection.value = null
            _agentCorrectionEasy.value = null
            _agentJob.value = null
            _agentEasyView.value = null
            try {
                val config = _agentConfig.value
                val siteArg = site.trim().ifBlank { null }
                when (val outcome = agentClient.correction(config, anchorId, siteArg, sn)) {
                    is AgentOutcome.Ok -> {
                        _agentCorrection.value = outcome.value
                        // The plain half comes from the agent too, when it is wanted.
                        if (_easyMode.value) {
                            val plain = agentClient.correctionEasyMode(config, anchorId, siteArg, sn)
                            if (plain is AgentOutcome.Ok) _agentCorrectionEasy.value = plain.value
                        }
                        _uiNotice.emit(correctionSummary(outcome.value))
                    }
                    is AgentOutcome.Refused -> {
                        agentNotice("The agent refused the correction: ${outcome.reason}")
                    }
                    is AgentOutcome.Failed -> agentNotice(outcome.message)
                }
            } finally {
                _agentBusy.value = false
            }
        }
    }

    /**
     * Run a Referee deterministic audit of a prediction table against a claim.
     * Refusals (PHI routed to a managed backend, oversized CSV, ...) come back as
     * a refusal, never as a fabricated report.
     */
    fun runAgentRefereeAudit(
        claimId: String,
        claimedAuc: Double,
        csvText: String,
        dataClass: String = "phi",
        targetBackend: String = "local"
    ) {
        if (csvText.isBlank()) {
            agentNotice("Provide a prediction CSV first.")
            return
        }
        viewModelScope.launch {
            _agentBusy.value = true
            _agentRefereeReport.value = null
            _agentJob.value = null
            _agentEasyView.value = null
            try {
                val config = _agentConfig.value
                val claim = org.json.JSONObject().apply {
                    put("claim_id", claimId.ifBlank { "referee-audit" })
                    put("claimed_auc", claimedAuc)
                }
                when (val outcome = agentClient.refereeAudit(config, claim, csvText, dataClass, targetBackend)) {
                    is AgentOutcome.Ok -> {
                        _agentRefereeReport.value = outcome.value
                        _uiNotice.emit("Referee verdict: ${outcome.value.verdict}")
                        // Turn the audit into concrete follow-up experiments.
                        val expOutcome = agentClient.refereeExperiments(config, outcome.value.rawJson)
                        if (expOutcome is AgentOutcome.Ok) {
                            _agentRefereeExperiments.value = expOutcome.value
                        }
                    }
                    is AgentOutcome.Refused -> agentNotice("The agent refused the audit: ${outcome.reason}")
                    is AgentOutcome.Failed -> agentNotice(outcome.message)
                }
            } finally {
                _agentBusy.value = false
            }
        }
    }

    private fun correctionSummary(result: CorrectionResult): String {
        val ratio = result.correctedOddsRatio
        return when {
            ratio != null ->
                "Corrected odds ratio ${String.format(java.util.Locale.US, "%.3f", ratio)} " +
                    "(evidence tier ${result.evidenceTier})."
            result.reason != null -> "Withheld: ${result.reason}"
            else -> "Correction status ${result.status}."
        }
    }

    /** Check the agent, and learn what it can do (OCR availability, anchors). */
    fun testAgentConnection() {
        viewModelScope.launch {
            _agentBusy.value = true
            try {
                val config = _agentConfig.value
                val health = agentClient.health(config)
                if (health !is AgentOutcome.Ok) {
                    _agentStatus.value = describeAgentFailure(health, config)
                    return@launch
                }
                // Anchors are asked for before any capability line. `/health` is
                // public, so a wrong key still answers it; reporting "Connected"
                // and then "Could not list anchors" hides the one thing the user
                // needs to fix. A 401/403 here is an auth answer, not a flaw in
                // the anchor list.
                val anchors = agentClient.anchors(config)
                if (anchors is AgentOutcome.Failed && (anchors.status == 401 || anchors.status == 403)) {
                    // No extra notice: a snackbar would cover this line, and the
                    // status already says exactly what is wrong.
                    _agentStatus.value =
                        "${anchors.message} The health route is public, so it answers without a key."
                    return@launch
                }

                val imaging = agentClient.imagingStatus(config)
                val ocrLine = when (imaging) {
                    is AgentOutcome.Ok -> if (imaging.value.ocrAvailable) {
                        "Figure OCR ready on the agent (images stay on your server)."
                    } else {
                        "Figure OCR is not installed on the agent."
                    }
                    else -> "Figure OCR status unknown."
                }
                val anchorLine = when (anchors) {
                    is AgentOutcome.Ok -> {
                        _agentAnchors.value = anchors.value
                        "${anchors.value.size} anchors available."
                    }
                    is AgentOutcome.Refused -> "The agent refused the anchor list: ${anchors.reason}"
                    is AgentOutcome.Failed -> "Could not list anchors."
                }
                _agentStatus.value = "Connected to ${config.normalizedBaseUrl}. $ocrLine $anchorLine"
                _uiNotice.emit("Agent reachable.")
            } finally {
                _agentBusy.value = false
            }
        }
    }

    private fun describeAgentFailure(outcome: AgentOutcome<*>, config: AgentConfig): String = when (outcome) {
        is AgentOutcome.Refused -> "Agent refused the request: ${outcome.reason}"
        // Transport failures already name the host they could not reach; appending
        // it again spent three lines of a phone screen saying it twice.
        is AgentOutcome.Failed -> if (outcome.message.contains(config.normalizedBaseUrl)) {
            outcome.message
        } else {
            "${outcome.message} (target: ${config.normalizedBaseUrl})"
        }
        is AgentOutcome.Ok -> "OK"
    }

    /**
     * List the paper chains the agent has written. Read-only and unmetered: it
     * is a directory listing, so opening this card costs nothing.
     */
    fun loadAgentPaperRuns() {
        viewModelScope.launch {
            _agentPapersBusy.value = true
            _agentPapersError.value = null
            try {
                when (val outcome = agentClient.researchRuns(_agentConfig.value)) {
                    is AgentOutcome.Ok -> {
                        _agentPaperRuns.value = outcome.value
                        _agentPapersError.value = null
                    }
                    is AgentOutcome.Refused -> {
                        _agentPapersError.value = "the agent refused: ${outcome.reason}"
                        _uiNotice.emit("Paper list refused: ${outcome.reason}")
                    }
                    is AgentOutcome.Failed -> {
                        _agentPapersError.value = outcome.message
                        _uiNotice.emit("Could not list papers: ${outcome.message}")
                    }
                }
            } finally {
                _agentPapersBusy.value = false
            }
        }
    }

    /** Open one paper chain. The markdown is returned whole, not re-rendered. */
    fun openAgentPaper(runId: String) {
        if (runId.isBlank()) return
        viewModelScope.launch {
            _agentPapersBusy.value = true
            _agentPaperVerification.value = null
            try {
                when (val outcome = agentClient.researchRun(_agentConfig.value, runId)) {
                    is AgentOutcome.Ok -> {
                        _agentPaper.value = outcome.value
                        if (outcome.value.paper.isBlank()) {
                            _uiNotice.emit("Run $runId has no paper.md; other chain files are available.")
                        }
                    }
                    is AgentOutcome.Refused -> _uiNotice.emit("Paper refused: ${outcome.reason}")
                    is AgentOutcome.Failed -> _uiNotice.emit("Could not read paper: ${outcome.message}")
                }
            } finally {
                _agentPapersBusy.value = false
            }
        }
    }

    /** Re-hash the opened chain and check its signed receipt. */
    fun verifyAgentPaper(runId: String) {
        if (runId.isBlank()) return
        viewModelScope.launch {
            when (val outcome = agentClient.verifyResearchRun(_agentConfig.value, runId)) {
                is AgentOutcome.Ok -> _agentPaperVerification.value = outcome.value
                is AgentOutcome.Refused -> _uiNotice.emit("Verification refused: ${outcome.reason}")
                is AgentOutcome.Failed -> _uiNotice.emit("Could not verify paper: ${outcome.message}")
            }
        }
    }

    fun closeAgentPaper() {
        _agentPaper.value = null
        _agentPaperVerification.value = null
    }

    /**
     * List the deployed ecosystem APIs, and with [check] ask the agent to probe each
     * one. The probe is metered on the agent, so the answer is whether it answers now,
     * not whether a URL is configured.
     */
    fun loadAgentFleet(check: Boolean = true) {
        viewModelScope.launch {
            _agentBusy.value = true
            try {
                when (val outcome = agentClient.fleet(_agentConfig.value, check = check)) {
                    is AgentOutcome.Ok -> {
                        _agentFleet.value = outcome.value
                        val reach = outcome.value.reachable
                        _agentStatus.value = if (reach != null) {
                            "${reach}/${outcome.value.count} ecosystem APIs answer (probed in " +
                                "${outcome.value.checkMs ?: 0} ms)."
                        } else {
                            "${outcome.value.deployed}/${outcome.value.count} ecosystem APIs are deployed."
                        }
                    }
                    is AgentOutcome.Refused -> _uiNotice.emit("Fleet roster refused: ${outcome.reason}")
                    is AgentOutcome.Failed -> _uiNotice.emit("Could not list the fleet: ${outcome.message}")
                }
            } finally {
                _agentBusy.value = false
            }
        }
    }

    /**
     * Run the agent-side research loop: one full design -> evidence -> experiment ->
     * paper pass per iteration, against the deployed ecosystem APIs, until the paper
     * grades high enough or a cap stops it.
     */
    fun runAgentResearchLoop(query: String) {
        // The loop needs a question; the screen offers the ingested seed first, then
        // whatever was dictated, so a spoken question works without an upload.
        val goal = query.trim()
            .ifBlank { _researchSeed.value?.trim().orEmpty() }
            .ifBlank { _agentTranscript.value.trim() }
        if (goal.isBlank()) {
            agentNotice("Give the loop a research question first.")
            return
        }
        viewModelScope.launch {
            _agentBusy.value = true
            _agentLoop.value = null
            try {
                when (val outcome = agentClient.researchLoop(_agentConfig.value, goal)) {
                    is AgentOutcome.Ok -> {
                        _agentLoop.value = outcome.value
                        // The loop writes a paper chain on the agent. Refresh the
                        // list immediately so the result is not a path with no
                        // way to read it.
                        loadAgentPaperRuns()
                        _uiNotice.emit("Loop stopped: ${outcome.value.stopReason}")
                    }
                    is AgentOutcome.Refused -> _uiNotice.emit("Loop refused: ${outcome.reason}")
                    is AgentOutcome.Failed -> {
                        _agentStatus.value = describeAgentFailure(outcome, _agentConfig.value)
                    }
                }
            } finally {
                _agentBusy.value = false
            }
        }
    }

    /**
     * Adjudicate on the agent, then wait for the durable worker and fetch the agent's own
     * plain-language translation. A refusal ends the flow with the refusal shown as the
     * answer.
     */
    fun runAgentAdjudication(anchorId: String, counts: Map<String, Int>? = null) {
        if (anchorId.isBlank()) {
            agentNotice("Choose or type an anchor id first.")
            return
        }
        viewModelScope.launch {
            _agentBusy.value = true
            _agentJob.value = null
            _agentEasyView.value = null
            _agentCorrection.value = null
            _agentCorrectionEasy.value = null
            try {
                val config = _agentConfig.value
                val submitted = agentClient.adjudicate(config, anchorId, counts = counts)
                val job = when (submitted) {
                    is AgentOutcome.Ok -> awaitAgentJob(config, submitted.value.jobId)
                    is AgentOutcome.Refused -> {
                        agentNotice("The agent refused: ${submitted.reason}")
                        return@launch
                    }
                    is AgentOutcome.Failed -> {
                        agentNotice(submitted.message)
                        return@launch
                    }
                }
                _agentJob.value = job
                val jobId = job.jobId
                if (jobId.isNotBlank()) {
                    val explained = agentClient.explainJob(config, jobId)
                    if (explained is AgentOutcome.Ok) _agentEasyView.value = explained.value
                }
                _uiNotice.emit(agentJobSummary(job))
            } finally {
                _agentBusy.value = false
            }
        }
    }

    /** Ask the agent to research a spoken/typed question and design experiments.
     *
     * @param useLlm false runs the agent's deterministic scaffold, which costs no
     *   model quota and is the safe default when the user is rate-limited.
     */
    fun runAgentResearch(useLlm: Boolean = true) {
        val query = _agentTranscript.value.trim()
        if (query.isBlank()) {
            agentNotice("Speak or type a research question first.")
            return
        }
        viewModelScope.launch {
            _agentBusy.value = true
            _agentResearchResult.value = null
            try {
                val config = _agentConfig.value
                val outcome = agentClient.research(config, query, useLlm)
                when (outcome) {
                    is AgentOutcome.Ok -> {
                        _agentResearchResult.value = outcome.value
                        _uiNotice.emit("Research complete: ${outcome.value.experiments.size} experiment(s) proposed.")
                    }
                    is AgentOutcome.Refused -> agentNotice("The agent refused: ${outcome.reason}")
                    is AgentOutcome.Failed -> agentNotice(outcome.message)
                }
            } finally {
                _agentBusy.value = false
            }
        }
    }

    fun ingestFileAndSeed(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            _agentBusy.value = true
            try {
                val name = uri.lastPathSegment ?: "upload.txt"
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (content.isNullOrBlank()) {
                    agentNotice("Could not read the file.")
                    return@launch
                }
                val config = _agentConfig.value
                when (val ingest = agentClient.ingestContent(config, content, name)) {
                    is AgentOutcome.Ok -> {
                        agentNotice("Ingested $name (${ingest.value.optString("chunks")} chunks).")
                        val analysis = agentClient.analyzeCorpus(config)
                        if (analysis is AgentOutcome.Ok) {
                            val seed = agentClient.generateSeed(config, null)
                            if (seed is AgentOutcome.Ok) {
                                _researchSeed.value = seed.value.optString("markdown")
                                val q = seed.value.optJSONObject("seed")?.optString("question").orEmpty()
                                if (q.isNotBlank()) _agentTranscript.value = q
                                agentNotice("Research seed ready. Review and tap Research.")
                            } else {
                                agentNotice("Seed generation failed: ${(seed as? AgentOutcome.Failed)?.message}")
                            }
                        } else {
                            agentNotice("Analysis failed: ${(analysis as? AgentOutcome.Failed)?.message}")
                        }
                    }
                    is AgentOutcome.Refused -> agentNotice("Ingest refused: ${ingest.reason}")
                    is AgentOutcome.Failed -> agentNotice(ingest.message)
                }
            } catch (e: Exception) {
                agentNotice("Ingest error: ${e.message}")
            } finally {
                _agentBusy.value = false
            }
        }
    }

    fun clearResearchSeed() { _researchSeed.value = null }

    /**
     * Keeps the folder grant and indexes a first batch. PDFs are extracted one page set at a
     * time, so a folder of papers is indexed across passes rather than in one blocking go.
     */
    fun setCorpusFolder(uri: android.net.Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (!com.example.concordance.autonomy.LocalCorpus.grant(app, uri)) {
                _uiNotice.emit("Could not keep access to that folder. Choose it again.")
                return@launch
            }
            _uiNotice.emit("Folder granted. Indexing the first batch…")
            val status = com.example.concordance.autonomy.LocalCorpus.index(app, maxNewFiles = 40)
            _uiNotice.emit(corpusSummary(status))
        }
    }

    fun indexCorpusNow() {
        viewModelScope.launch {
            val status = com.example.concordance.autonomy.LocalCorpus.index(
                getApplication<Application>(),
                maxNewFiles = 40
            )
            _uiNotice.emit(corpusSummary(status))
        }
    }

    fun forgetCorpusFolder() {
        com.example.concordance.autonomy.LocalCorpus.forget(getApplication<Application>())
        viewModelScope.launch { _uiNotice.emit("Corpus folder cleared.") }
    }

    private fun corpusSummary(status: com.example.concordance.autonomy.CorpusStatus): String = when {
        !status.hasFolder -> "No corpus folder chosen."
        status.files == 0 -> status.note ?: "Nothing indexable in that folder."
        else -> "Corpus: ${status.files} files, ${status.chunks} chunks." +
            (status.note?.let { " $it" } ?: "")
    }
    fun submitFeedback(claimId: String, rating: String) {
        viewModelScope.launch {
            val config = _agentConfig.value
            when (val outcome = agentClient.feedback(config, claimId, rating)) {
                is AgentOutcome.Ok -> agentNotice("Feedback recorded: $rating")
                is AgentOutcome.Refused -> agentNotice("Feedback refused: ${outcome.reason}")
                is AgentOutcome.Failed -> agentNotice(outcome.message)
            }
        }
    }

    private val _pendingDecisions = MutableStateFlow<List<AgentPendingDecision>>(emptyList())
    val pendingDecisions: StateFlow<List<AgentPendingDecision>> = _pendingDecisions.asStateFlow()

    fun loadPendingDecisions() {
        viewModelScope.launch {
            val config = _agentConfig.value
            when (val outcome = agentClient.pendingDecisions(config)) {
                is AgentOutcome.Ok -> _pendingDecisions.value = outcome.value
                else -> {}
            }
        }
    }

    fun resolvePendingDecision(id: String, choice: String, rationale: String = "") {
        viewModelScope.launch {
            val config = _agentConfig.value
            when (val outcome = agentClient.resolveDecision(config, id, choice, rationale)) {
                is AgentOutcome.Ok -> {
                    agentNotice("Decision resolved: $choice")
                    loadPendingDecisions()
                }
                is AgentOutcome.Refused -> agentNotice("Resolve refused: ${outcome.reason}")
                is AgentOutcome.Failed -> agentNotice(outcome.message)
            }
        }
    }

    private fun agentJobSummary(job: AgentJob): String = when {
        job.capsule != null -> "Verdict: ${job.capsule.verdict.uppercase()} (${job.capsule.evidenceTier})."
        job.wasRefused -> "The agent withheld a verdict. Read the reason."
        job.error != null -> "Job error: ${job.error}"
        else -> "Job ${job.status}."
    }

    /** Poll until the job is terminal, publishing each status so the UI shows progress. */
    private suspend fun awaitAgentJob(config: AgentConfig, jobId: String): AgentJob {
        var last: AgentJob? = null
        repeat(POLL_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(POLL_DELAY_MS)
            when (val polled = agentClient.job(config, jobId)) {
                is AgentOutcome.Ok -> {
                    last = polled.value
                    // Publish progress: the durable worker can take minutes to finish.
                    _agentJob.value = polled.value
                    if (polled.value.isTerminal) return polled.value
                }
                is AgentOutcome.Refused -> return last ?: AgentJob(jobId, "withheld", null, polled.reason, "")
                is AgentOutcome.Failed -> {
                    // A transport failure is worth one more try; a 4xx is final.
                    if (polled.status in 400..499) {
                        return last ?: AgentJob(jobId, "failed", null, polled.message, "")
                    }
                }
            }
        }
        val observed = last
        return if (observed == null) {
            AgentJob(jobId, "unknown", null, "The agent did not answer while polling.", "")
        } else {
            observed.copy(
                error = observed.error
                    ?: "Still running on the agent after ${POLL_ATTEMPTS * POLL_DELAY_MS / 1000} seconds. " +
                    "The agent keeps working on job $jobId — nothing has been lost."
            )
        }
    }

    /** Upload a figure or PDF for OCR on the agent host. Never auto-adjudicates from it. */
    fun uploadFigure(upload: AgentUpload, dataClass: String) {
        if (!_agentUploadAck.value) {
            agentNotice("Acknowledge the upload first: the figure is sent to your agent server.")
            return
        }
        viewModelScope.launch {
            _agentBusy.value = true
            try {
                val outcome = agentClient.uploadFigure(
                    config = _agentConfig.value,
                    bytes = upload.bytes,
                    fileName = upload.fileName,
                    contentType = upload.contentType,
                    dataClass = dataClass
                )
                when (outcome) {
                    is AgentOutcome.Ok -> {
                        _agentUpload.value = outcome.value
                        val confidence = String.format(java.util.Locale.US, "%.2f", outcome.value.confidence)
                        _uiNotice.emit(
                            "Read ${outcome.value.text.length} characters at $confidence confidence " +
                                "(tier ${outcome.value.evidenceTier}, unverified)."
                        )
                    }
                    is AgentOutcome.Refused -> agentNotice("The agent refused the upload: ${outcome.reason}")
                    is AgentOutcome.Failed -> agentNotice(outcome.message)
                }
            } finally {
                _agentBusy.value = false
            }
        }
    }

    /** Start/stop on-device dictation. Audio is never sent to the agent. */
    fun toggleDictation() {
        if (dictationJob?.isActive == true) {
            dictationJob?.cancel()
            dictationJob = null
            return
        }
        val reason = dictation.unavailableReason()
        if (reason != null) {
            agentNotice(reason)
            return
        }
        dictationJob = viewModelScope.launch {
            dictation.listen().collect { event ->
                when (event) {
                    is SpeechEvent.Partial -> _agentTranscript.value = event.text
                    is SpeechEvent.Final -> {
                        _agentTranscript.value = event.text
                        _uiNotice.emit("Heard: ${event.text}")
                    }
                    is SpeechEvent.Failed -> _uiNotice.emit(event.message)
                    SpeechEvent.Unavailable -> _uiNotice.emit("On-device dictation is unavailable.")
                }
            }
        }
    }

    fun setAgentTranscript(text: String) {
        _agentTranscript.value = text
    }

    /** Read a specific plain-language result aloud using the on-device voice. */
    fun speakAgentResult(view: EasyModeView?) {
        if (view == null) {
            agentNotice("Nothing to read out yet — run an adjudication or a correction first.")
            return
        }
        viewModelScope.launch {
            val spoken = speech.speak(EasyMode.speakableSummary(view))
            if (!spoken) {
                _uiNotice.emit("Could not speak the result with ${speech.engineName}.")
            }
        }
    }

    fun speakText(text: String) {
        viewModelScope.launch {
            val spoken = speech.speak(text.take(2000))
            if (!spoken) _uiNotice.emit("Could not speak with ${speech.engineName}.")
        }
    }

    fun stopSpeaking() {
        speech.stop()
    }

    override fun onCleared() {
        super.onCleared()
        dictationJob?.cancel()
        runCatching { speech.shutdown() }
    }
}

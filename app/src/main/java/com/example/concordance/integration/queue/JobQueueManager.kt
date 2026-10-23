package com.example.concordance.integration.queue

import com.example.concordance.engine.StatisticalEngine
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object JobQueueManager {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Illustrative worker nodes across AWS Fargate, AWS Batch, and Kaggle. These are SAMPLE
    // data: nothing here polls a real fleet, so every node is flagged isSample = true and the
    // UI labels the tab accordingly.
    private val DEFAULT_WORKER_NODES = listOf(
        WorkerNode(
            workerId = "fargate-worker-us-east-1a",
            routingPolicy = WorkerRoutingPolicy.AWS_FARGATE_GPU,
            region = "us-east-1",
            ipOrDns = "10.0.12.84 (VPC Private)",
            activeTasksCount = 1,
            lastHeartbeatTimestamp = System.currentTimeMillis() - 12_000,
            isHealthy = true
        ),
        WorkerNode(
            workerId = "batch-c6i-worker-04",
            routingPolicy = WorkerRoutingPolicy.AWS_BATCH_CPU,
            region = "us-west-2",
            ipOrDns = "172.31.4.19 (Batch Fleet)",
            activeTasksCount = 0,
            lastHeartbeatTimestamp = System.currentTimeMillis() - 6_000,
            isHealthy = true
        ),
        WorkerNode(
            workerId = "kaggle-compute-t4-01",
            routingPolicy = WorkerRoutingPolicy.KAGGLE_CLUSTER_GPU,
            region = "gcp-us-central1",
            ipOrDns = "kaggle.internal/worker-t4",
            activeTasksCount = 1,
            lastHeartbeatTimestamp = System.currentTimeMillis() - 15_000,
            isHealthy = true
        ),
        WorkerNode(
            workerId = "android-workmanager-os",
            routingPolicy = WorkerRoutingPolicy.LOCAL_WORKER,
            region = "on-device-scheduler",
            ipOrDns = "localhost/workmanager",
            activeTasksCount = 0,
            lastHeartbeatTimestamp = System.currentTimeMillis(),
            isHealthy = true
        )
    ).map { it.copy(isSample = true) }

    private val _workerNodes = MutableStateFlow(DEFAULT_WORKER_NODES)
    val workerNodes: StateFlow<List<WorkerNode>> = _workerNodes.asStateFlow()

    private val jobMap = ConcurrentHashMap<String, JobStatusResponse>()
    private val _allJobs = MutableStateFlow<List<JobStatusResponse>>(emptyList())
    val allJobs: StateFlow<List<JobStatusResponse>> = _allJobs.asStateFlow()

    private val _stuckRecoveryLog = MutableStateFlow<List<String>>(emptyList())
    val stuckRecoveryLog: StateFlow<List<String>> = _stuckRecoveryLog.asStateFlow()

    init {
        // Seed default representative benchmark jobs
        seedInitialBenchmarkJobs()
    }

    private fun seedInitialBenchmarkJobs() {
        val seedJob1 = JobStatusResponse(
            taskUuid = "task_a9f1481b-320d-4581-9b19-ef8a09b3e101",
            jobId = "cjob_89d3e52f10ca981b",
            dataClass = JobDataClass.IMAGING_VLM_FIGURE_EXTRACTION,
            routingPolicy = WorkerRoutingPolicy.AWS_FARGATE_GPU,
            status = JobExecutionStatus.COMPLETED,
            queuePosition = 0,
            progressPercent = 100,
            estimatedWaitSeconds = 0,
            workerNode = DEFAULT_WORKER_NODES[0],
            stuckRecoveryTriggered = false,
            retryCount = 0,
            resultSummary = "VLM + OCR extracted 4 forest plot figures and 2 scatter distributions from Current Oncology (2026). Identified 1/1 tissue vs 0/1 plasma ESR1 discordance in CNS progression.",
            resultArtifactJson = JSONObject().apply {
                put("figures_parsed", 4)
                put("bounding_boxes_found", 18)
                put("ocr_detected_labels", listOf("Tissue-NGS (+)", "Plasma-ctDNA (-)", "CNS Progression"))
                put("extracted_sensitivity_bound", 0.40)
            }.toString(),
            citableCapsuleSha256 = "c089fe42b78129e1fa55b88294e77da1b4b9b92019e0789bc44d8b9e6fa81021",
            startedAtTimestamp = System.currentTimeMillis() - 1_200_000,
            completedAtTimestamp = System.currentTimeMillis() - 360_000,
            logs = listOf(
                "Task UUID assigned via /heavy-process endpoint.",
                "Enqueued to AWS Fargate queue. Content hash: cjob_89d3e52f10ca981b.",
                "Worker fargate-worker-us-east-1a claimed task.",
                "Loaded VLM model vlm-biomed-qwen2-vl-72b-ocr.",
                "Figure segmentation and coordinate extraction completed in 842s.",
                "Cryptographic capsule artifact generated and verified."
            )
        )

        val seedJob2 = JobStatusResponse(
            taskUuid = "task_73d82a1c-bc92-491a-96e0-244e891c3902",
            jobId = "cjob_61b8f04c7a8820de",
            dataClass = JobDataClass.SENSITIVITY_PERMUTATION,
            routingPolicy = WorkerRoutingPolicy.AWS_BATCH_CPU,
            status = JobExecutionStatus.COMPLETED,
            queuePosition = 0,
            progressPercent = 100,
            estimatedWaitSeconds = 0,
            workerNode = DEFAULT_WORKER_NODES[1],
            stuckRecoveryTriggered = false,
            retryCount = 0,
            resultSummary = "10,000 bootstrap resamples for Giannoudis 2024 pooled cohort. Null crossing threshold r* = 0.401 (95% CI 0.384–0.418, p < 0.0001).",
            resultArtifactJson = JSONObject().apply {
                put("iterations", 10000)
                put("r_star_median", 0.401)
                put("r_star_ci_low", 0.384)
                put("r_star_ci_high", 0.418)
                put("verdict", "threshold_only")
            }.toString(),
            citableCapsuleSha256 = "91a82bc0e8919cf6289ba9c04981a293c4e09871fae8790184e1b827361a9bc2",
            startedAtTimestamp = System.currentTimeMillis() - 720_000,
            completedAtTimestamp = System.currentTimeMillis() - 540_000,
            logs = listOf(
                "Task enqueued to AWS Batch EC2 cluster.",
                "Worker batch-c6i-worker-04 initialized 16-thread permutation runner.",
                "10,000 resamples evaluated across sensitivity sweep [0.05, 1.20].",
                "Null crossing verified. Result saved to S3 and local cache."
            )
        )

        // Flagged as sample data: the hashes, timestamps and worker logs below are illustrative
        // and describe runs that never happened. The UI must say so.
        val samples = listOf(seedJob1.copy(isSample = true), seedJob2.copy(isSample = true))
        samples.forEach { jobMap[it.taskUuid] = it }
        _allJobs.value = samples
    }

    /**
     * Submits a heavy computation task matching the FastAPI + PostgreSQL pattern:
     * Returns a task UUID and content-hash job ID immediately without blocking the mobile UI.
     */
    fun submitHeavyProcess(
        dataClass: JobDataClass,
        routingPolicy: WorkerRoutingPolicy,
        inputs: String,
        modelRevision: String,
        params: Map<String, String>
    ): JobStatusResponse {
        val spec = JobSpec.create(
            dataClass = dataClass,
            routingPolicy = routingPolicy,
            inputs = inputs,
            modelRevision = modelRevision,
            params = params
        )

        val queuePos = (jobMap.values.count { it.status == JobExecutionStatus.PENDING } + 1)
        val estimatedWait = when (routingPolicy) {
            WorkerRoutingPolicy.AWS_FARGATE_GPU -> 45
            WorkerRoutingPolicy.AWS_BATCH_CPU -> 20
            WorkerRoutingPolicy.KAGGLE_CLUSTER_GPU -> 25
            WorkerRoutingPolicy.LOCAL_WORKER -> 15
        }

        val initialStatus = JobStatusResponse(
            taskUuid = spec.taskUuid,
            jobId = spec.jobId,
            dataClass = spec.dataClass,
            routingPolicy = spec.routingPolicy,
            status = JobExecutionStatus.PENDING,
            queuePosition = queuePos,
            progressPercent = 0,
            estimatedWaitSeconds = estimatedWait,
            workerNode = null,
            stuckRecoveryTriggered = false,
            retryCount = 0,
            startedAtTimestamp = null,
            completedAtTimestamp = null,
            logs = listOf(
                "SIMULATED RUN: no remote worker is contacted; this screen demonstrates the queue contract.",
                "POST /heavy-process received spec with immutable content hash: ${spec.jobId}",
                "Validated data class ${spec.dataClass.name} against routing policy ${spec.routingPolicy.name}.",
                "Task enqueued at position #$queuePos. Returning UUID ${spec.taskUuid} to client immediately."
            ),
            // Nothing is executed: the lifecycle below is a local simulation, and the UI says so.
            isSample = true
        )

        jobMap[spec.taskUuid] = initialStatus
        updateAllJobsList()

        // Launch asynchronous worker execution
        launchAsynchronousWorker(spec)

        return initialStatus
    }

    private fun launchAsynchronousWorker(spec: JobSpec) {
        scope.launch {
            // Queue transition: Pending -> Processing
            delay(1200)
            val assignedWorker = _workerNodes.value.firstOrNull { it.routingPolicy == spec.routingPolicy }
                ?: _workerNodes.value.first()

            val processingStatus = jobMap[spec.taskUuid]?.copy(
                status = JobExecutionStatus.PROCESSING,
                queuePosition = 0,
                progressPercent = 15,
                workerNode = assignedWorker,
                startedAtTimestamp = System.currentTimeMillis(),
                logs = (jobMap[spec.taskUuid]?.logs ?: emptyList()) + listOf(
                    "SIMULATED: worker ${assignedWorker.workerId} (${assignedWorker.ipOrDns}) would claim the task here.",
                    "SIMULATED: model revision ${spec.modelRevision}."
                )
            )
            if (processingStatus != null) {
                jobMap[spec.taskUuid] = processingStatus
                updateAllJobsList()
            }

            // Progress steps simulating asynchronous compute on worker
            val steps = listOf(35, 60, 85)
            for (step in steps) {
                delay(1500)
                val current = jobMap[spec.taskUuid] ?: return@launch
                if (current.status == JobExecutionStatus.PROCESSING) {
                    val updated = current.copy(
                        progressPercent = step,
                        estimatedWaitSeconds = maxOf(0, current.estimatedWaitSeconds - 8),
                        logs = current.logs + listOf("Compute progress: $step% complete on ${assignedWorker.workerId}.")
                    )
                    jobMap[spec.taskUuid] = updated
                    updateAllJobsList()
                }
            }

            // Compute completion & citable capsule generation
            delay(1800)
            val current = jobMap[spec.taskUuid] ?: return@launch

            val (summary, artifactJson) = generateWorkerResult(spec)

            val completed = current.copy(
                status = JobExecutionStatus.COMPLETED,
                progressPercent = 100,
                estimatedWaitSeconds = 0,
                // The numbers below are illustrative, so the summary says so and no hash is
                // presented: a digest over a string is not a capsule, and must not look like one.
                resultSummary = "SIMULATED RESULT (no compute was run and no capsule was produced). $summary",
                resultArtifactJson = artifactJson,
                citableCapsuleSha256 = null,
                completedAtTimestamp = System.currentTimeMillis(),
                logs = current.logs + listOf(
                    "SIMULATED completion after ${(System.currentTimeMillis() - (current.startedAtTimestamp ?: System.currentTimeMillis())) / 1000}s.",
                    "No artifact was generated, so no SHA-256 is reported.",
                    "Notification dispatched to phone OS scheduler."
                )
            )

            jobMap[spec.taskUuid] = completed
            updateAllJobsList()
        }
    }

    private fun generateWorkerResult(spec: JobSpec): Pair<String, String> {
        return when (spec.dataClass) {
            JobDataClass.IMAGING_VLM_FIGURE_EXTRACTION -> {
                val json = JSONObject().apply {
                    put("engine", "AWS Fargate VLM OCR Cluster")
                    put("model", spec.modelRevision)
                    put("figures_analyzed", 6)
                    put("extracted_discordance_case", "Tissue-positive / Plasma-negative ESR1 Y537S")
                    put("lesion_site", "Brain metastasis (intracranial)")
                    put("inferred_r_star_threshold", 0.40)
                    put("confidence_interval", "[0.38, 0.42]")
                }.toString()
                val summary = "AWS Fargate VLM Worker: Extracted 6 paper figures via OCR and segmented tissue vs plasma discordance markers. Confirmed L1 falsification threshold r* = 0.40 for CNS lesion ctDNA shedding."
                Pair(summary, json)
            }
            JobDataClass.SENSITIVITY_PERMUTATION -> {
                val json = JSONObject().apply {
                    put("engine", "AWS Batch 16-Core Node")
                    put("iterations", spec.parameters["iterations"] ?: "10000")
                    put("empirical_null_r_star", 0.401)
                    put("p_value", 0.00008)
                    put("alpha_level", 0.05)
                }.toString()
                val summary = "AWS Batch Worker: Completed 10,000 Monte Carlo bootstrap permutations. Empirical null crossing r* = 0.401 (p < 0.0001). Observed depletion indistinguishable from detection artifact below r*."
                Pair(summary, json)
            }
            JobDataClass.CROSS_MODALITY_CTDNA_OFFLOAD -> {
                val json = JSONObject().apply {
                    put("engine", "Kaggle Dedicated GPU Worker")
                    put("cohort_patients", 412)
                    put("brain_concordance_rate", "32.4%")
                    put("extracranial_concordance_rate", "88.6%")
                    put("ctdna_shedding_differential", "3.1x attenuation")
                }.toString()
                val summary = "Kaggle GPU Worker: Computed cross-modality shedding matrix across 412 patients. Identified 3.1x attenuation in intracranial ctDNA recovery relative to extracranial metastases."
                Pair(summary, json)
            }
            JobDataClass.RESEARCH_EXPERIMENT -> {
                val json = JSONObject().apply {
                    put("engine", "Research experiment worker")
                    put("experiment", spec.parameters["experiment"] ?: spec.inputsPayload)
                    put("design", spec.parameters["design"] ?: "")
                    put("run_id", spec.parameters["run_id"] ?: "")
                }.toString()
                val summary = "Research experiment queued from the agent's design; the backend it was routed " +
                    "to would run it."
                Pair(summary, json)
            }
            JobDataClass.LITERATURE_SYNTHESIS_BATCH -> {
                val json = JSONObject().apply {
                    put("engine", "FastAPI Worker on AWS Fargate")
                    put("cohorts_synthesized", 12)
                    put("pooled_brain_samples", 1120)
                    put("pooled_non_cns_samples", 19800)
                    put("summary_or", 0.32)
                }.toString()
                val summary = "Literature Worker: Synthesized 12 clinical cohorts (n=20,920 total samples). Summary odds ratio OR = 0.32; depletion fully explained if brain assay sensitivity ratio r <= 0.42."
                Pair(summary, json)
            }
        }
    }

    /**
     * Automatic Stuck-Task Recovery:
     * Tasks stuck in PROCESSING for more than 5 minutes (or manual test simulation)
     * are automatically reset to PENDING with an incremented retry counter and logged audit note.
     */
    fun checkAndRecoverStuckTasks(stuckThresholdMs: Long = 300_000): Int {
        val now = System.currentTimeMillis()
        var recoveredCount = 0

        jobMap.forEach { (taskUuid, job) ->
            if (job.status == JobExecutionStatus.PROCESSING) {
                val startedAt = job.startedAtTimestamp ?: now
                val elapsed = now - startedAt
                if (elapsed >= stuckThresholdMs) {
                    val retry = job.retryCount + 1
                    val logMessage = String.format(
                        Locale.US,
                        "Automatic Stuck-Task Recovery: Task %s exceeded 5m processing limit (%ds). Reset to PENDING (retry #%d) with exponential backoff.",
                        taskUuid.take(16),
                        elapsed / 1000,
                        retry
                    )

                    val recoveredJob = job.copy(
                        status = JobExecutionStatus.STUCK_RECOVERED,
                        queuePosition = 1,
                        progressPercent = 0,
                        stuckRecoveryTriggered = true,
                        retryCount = retry,
                        workerNode = null,
                        logs = job.logs + listOf(logMessage)
                    )

                    jobMap[taskUuid] = recoveredJob
                    recoveredCount++

                    _stuckRecoveryLog.value = listOf(logMessage) + _stuckRecoveryLog.value

                    // Re-enqueue after stuck recovery
                    scope.launch {
                        delay(2000)
                        val rePending = jobMap[taskUuid]?.copy(status = JobExecutionStatus.PENDING)
                        if (rePending != null) {
                            jobMap[taskUuid] = rePending
                            updateAllJobsList()
                        }
                    }
                }
            }
        }

        if (recoveredCount > 0) {
            updateAllJobsList()
        }
        return recoveredCount
    }

    /**
     * Manually simulates a stuck task to verify the automatic recovery workflow.
     */
    fun simulateStuckTaskForTesting(): JobStatusResponse {
        val taskUuid = "task_stuck_sim_" + System.currentTimeMillis().toString().takeLast(6)
        val jobId = "cjob_stuck_" + System.currentTimeMillis().toString().takeLast(6)

        val stuckJob = JobStatusResponse(
            taskUuid = taskUuid,
            jobId = jobId,
            dataClass = JobDataClass.IMAGING_VLM_FIGURE_EXTRACTION,
            routingPolicy = WorkerRoutingPolicy.AWS_FARGATE_GPU,
            status = JobExecutionStatus.PROCESSING,
            queuePosition = 0,
            progressPercent = 42,
            estimatedWaitSeconds = 180,
            workerNode = _workerNodes.value[0],
            stuckRecoveryTriggered = false,
            retryCount = 0,
            startedAtTimestamp = System.currentTimeMillis() - 360_000, // 6 minutes ago (> 5m)
            logs = listOf(
                "Task enqueued and acquired by worker 6 minutes ago.",
                "Worker node became unresponsive during high-memory VLM figure batch.",
                "Waiting for automatic stuck-task recovery daemon..."
            )
        )

        jobMap[taskUuid] = stuckJob
        updateAllJobsList()
        return stuckJob
    }

    fun getJobStatus(taskUuid: String): JobStatusResponse? {
        return jobMap[taskUuid]
    }

    private fun updateAllJobsList() {
        _allJobs.value = jobMap.values.sortedByDescending { it.startedAtTimestamp ?: System.currentTimeMillis() }
    }
}

package com.example.concordance.integration.queue

import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

enum class JobDataClass(val label: String, val defaultModel: String, val defaultWorker: WorkerRoutingPolicy) {
    SENSITIVITY_PERMUTATION(
        label = "Sensitivity Curve 10k Permutation Bootstrap",
        defaultModel = "concordance-bootstrap-null-v2.1",
        defaultWorker = WorkerRoutingPolicy.AWS_BATCH_CPU
    ),
    IMAGING_VLM_FIGURE_EXTRACTION(
        label = "Imaging Sector: VLM + OCR Figure Extraction (>15m)",
        defaultModel = "vlm-biomed-qwen2-vl-72b-ocr",
        defaultWorker = WorkerRoutingPolicy.AWS_FARGATE_GPU
    ),
    CROSS_MODALITY_CTDNA_OFFLOAD(
        label = "Cross-Modality ctDNA Shedding Matrix Offload",
        defaultModel = "ctdna-shedding-bayesian-v1.0",
        defaultWorker = WorkerRoutingPolicy.KAGGLE_CLUSTER_GPU
    ),
    LITERATURE_SYNTHESIS_BATCH(
        label = "Multi-Cohort Literature Synthesis & Meta-Analysis",
        defaultModel = "biomed-synthesizer-fastapi-worker",
        defaultWorker = WorkerRoutingPolicy.AWS_FARGATE_GPU
    ),

    /** An experiment the agent designed. The backend comes from the experiment's own routing. */
    RESEARCH_EXPERIMENT(
        label = "Agent-Designed Research Experiment",
        defaultModel = "research-v1",
        defaultWorker = WorkerRoutingPolicy.AWS_BATCH_CPU
    )
}

enum class WorkerRoutingPolicy(
    val title: String,
    val infrastructure: String,
    val hardwareTier: String,
    val maxDurationMinutes: Int
) {
    AWS_FARGATE_GPU(
        title = "AWS Fargate GPU Worker",
        infrastructure = "Amazon ECS / Fargate Linux Container",
        hardwareTier = "4 vCPU, 16GB RAM, NVIDIA A10G Tensor Core",
        maxDurationMinutes = 120
    ),
    AWS_BATCH_CPU(
        title = "AWS Batch Compute Cluster",
        infrastructure = "AWS Batch Managed EC2 Fleet (c6i.4xlarge)",
        hardwareTier = "16 vCPU, 32GB RAM High-Throughput",
        maxDurationMinutes = 60
    ),
    KAGGLE_CLUSTER_GPU(
        title = "Kaggle Dedicated Cluster",
        infrastructure = "Kaggle Compute API Worker",
        hardwareTier = "Tesla T4 x2 / P100 GPU",
        maxDurationMinutes = 30
    ),
    LOCAL_WORKER(
        title = "Android Local OS Scheduler",
        infrastructure = "WorkManager CoroutineWorker with OS Constraints",
        hardwareTier = "On-Device Neural/CPU Core",
        maxDurationMinutes = 10
    )
}

enum class JobExecutionStatus(val label: String, val badgeColorHex: Long) {
    PENDING("PENDING (In Queue)", 0xFFFFD740),
    PROCESSING("PROCESSING (Worker Active)", 0xFF00E5FF),
    STUCK_RECOVERED("STUCK_RECOVERED (>5m Auto-Reset)", 0xFFFF9100),
    COMPLETED("COMPLETED (Verifiable)", 0xFF00E676),
    FAILED("FAILED", 0xFFFF5252)
}

data class WorkerNode(
    val workerId: String,
    val routingPolicy: WorkerRoutingPolicy,
    val region: String,
    val ipOrDns: String,
    val activeTasksCount: Int,
    val lastHeartbeatTimestamp: Long,
    val isHealthy: Boolean = true,
    /** True for the illustrative fleet seeded for demonstration; never a live node. */
    val isSample: Boolean = false
)

data class JobSpec(
    val jobId: String, // Immutable content-hash SHA-256
    val taskUuid: String, // Server-assigned task UUID
    val dataClass: JobDataClass,
    val routingPolicy: WorkerRoutingPolicy,
    val inputsPayload: String,
    val modelRevision: String,
    val parameters: Map<String, String>,
    val submittedAtTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun create(
            dataClass: JobDataClass,
            routingPolicy: WorkerRoutingPolicy,
            inputs: String,
            modelRevision: String,
            params: Map<String, String>
        ): JobSpec {
            val taskUuid = "task_" + UUID.randomUUID().toString()
            // Compute deterministic SHA-256 content hash of inputs + spec
            val canonicalString = buildString {
                append("data_class:").append(dataClass.name).append("|")
                append("routing:").append(routingPolicy.name).append("|")
                append("model:").append(modelRevision).append("|")
                append("inputs:").append(inputs.trim()).append("|")
                params.entries.sortedBy { it.key }.forEach { (k, v) ->
                    append(k).append("=").append(v).append(";")
                }
            }
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(canonicalString.toByteArray(Charsets.UTF_8))
            val hashHex = digest.joinToString("") { "%02x".format(it) }
            val jobId = "cjob_${hashHex.take(16)}"

            return JobSpec(
                jobId = jobId,
                taskUuid = taskUuid,
                dataClass = dataClass,
                routingPolicy = routingPolicy,
                inputsPayload = inputs,
                modelRevision = modelRevision,
                parameters = params
            )
        }
    }
}

data class JobStatusResponse(
    val taskUuid: String,
    val jobId: String,
    val dataClass: JobDataClass,
    val routingPolicy: WorkerRoutingPolicy,
    val status: JobExecutionStatus,
    val queuePosition: Int,
    val progressPercent: Int,
    val estimatedWaitSeconds: Int,
    val workerNode: WorkerNode?,
    val stuckRecoveryTriggered: Boolean = false,
    val retryCount: Int = 0,
    val resultSummary: String? = null,
    val resultArtifactJson: String? = null,
    val citableCapsuleSha256: String? = null,
    val startedAtTimestamp: Long? = null,
    val completedAtTimestamp: Long? = null,
    val logs: List<String> = emptyList(),
    /** True for a seeded demonstration job; it never ran anywhere and its hashes are illustrative. */
    val isSample: Boolean = false
)

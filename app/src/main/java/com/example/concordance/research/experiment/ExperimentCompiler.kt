package com.example.concordance.research.experiment

/**
 * Compiles a [ExperimentSpec] into a [RunPlan] - or refuses.
 *
 * The compiler **fails closed**. An unresolved dataset, an unpinned image, a PHI-class input on
 * a remote backend, an over-budget run, or a confirmatory spec without a preregistration produces
 * a refusal with a reason code. There is no "best effort" path: a refusal is a result and the UI
 * shows it as one.
 */
object ExperimentCompiler {

    fun compile(
        spec: ExperimentSpec,
        backend: BackendCapability,
        preregistration: Preregistration? = null,
    ): CompileResult {
        val problems = mutableListOf<SpecProblem>()
        problems += spec.problems()

        // A class that is a document can never be submitted, on any backend.
        if (!spec.experimentClass.isExecutable) {
            problems += SpecProblem(
                "CLASS_NOT_EXECUTABLE",
                "${spec.experimentClass.label} is a design artifact and is never submitted"
            )
        }

        if (spec.experimentClass !in backend.classes) {
            problems += SpecProblem(
                "BACKEND_UNSUPPORTED",
                "backend '${backend.id}' cannot run ${spec.experimentClass.label}"
            )
        }

        // Data residency: PHI never leaves the host, and a remote backend is "leaving".
        spec.datasets.forEach { ds ->
            if (!ds.dataClass.mayLeaveHost && !backend.isLocal) {
                problems += SpecProblem(
                    "DATA_CLASS_BLOCKED",
                    "dataset '${ds.ref}' is ${ds.dataClass.wireValue} and cannot go to '${backend.id}'"
                )
            }
            if (ds.access == DataAccess.CONTROLLED && !backend.allowsControlledData) {
                problems += SpecProblem(
                    "CONTROLLED_DATA_REMOTE",
                    "dataset '${ds.ref}' is controlled-access; it must stay on a local backend"
                )
            }
            if (ds.sha256.isBlank() || ds.version.isBlank()) {
                problems += SpecProblem(
                    "DATASET_UNPINNED",
                    "dataset '${ds.ref}' is not pinned; it needs a version and a sha256"
                )
            }
        }

        // An image digest is what makes a BITWISE claim reproducible.
        if (backend.pinnedImages && spec.method.imageDigest.isBlank()) {
            problems += SpecProblem(
                "IMAGE_UNPINNED",
                "method.imageDigest is empty; a reproducible run needs a pinned image"
            )
        }

        // Confirmatory work needs a locked plan written before the result existed.
        if (spec.confirmatory) {
            when {
                preregistration == null -> problems += SpecProblem(
                    "PREREGISTRATION_REQUIRED",
                    "a confirmatory run must be preregistered before submission"
                )
                preregistration.specHash != spec.sha256() -> problems += SpecProblem(
                    "PREREGISTRATION_STALE",
                    "preregistration hash does not match this spec version"
                )
                preregistration.lockedAtUtc.isBlank() -> problems += SpecProblem(
                    "PREREGISTRATION_TIME",
                    "preregistration has no lock time"
                )
            }
        }

        if (spec.budgetGpuMinutes > backend.maxGpuMinutes) {
            problems += SpecProblem(
                "BUDGET_GPU",
                "spec asks for ${spec.budgetGpuMinutes} GPU minutes; '${backend.id}' allows " +
                    backend.maxGpuMinutes
            )
        }
        if (spec.budgetCpuMinutes > backend.maxCpuMinutes) {
            problems += SpecProblem(
                "BUDGET_CPU",
                "spec asks for ${spec.budgetCpuMinutes} CPU minutes; '${backend.id}' allows " +
                    backend.maxCpuMinutes
            )
        }
        if (spec.budgetUsd > backend.maxUsd) {
            problems += SpecProblem(
                "BUDGET_USD",
                "spec asks for $${spec.budgetUsd}; '${backend.id}' allows $${backend.maxUsd}"
            )
        }

        if (problems.isNotEmpty()) return CompileResult.Refused(problems.distinctBy { it.code + it.message })

        val resolved = spec.datasets.map { ds ->
            ResolvedDataset(
                ref = ds.ref,
                version = ds.version,
                sha256 = ds.sha256,
                mountPath = backend.mountPathFor(ds),
            )
        }

        val seeds = if (backend.determinism == Determinism.STATISTICAL) {
            // A statistical run is reported as a distribution over seeds, never as one number.
            listOf(spec.method.seed, spec.method.seed + 1, spec.method.seed + 2)
        } else {
            listOf(spec.method.seed)
        }

        val checks = buildList {
            add("spec hash matches preregistration")
            add("all datasets pinned by sha256")
            add("image digest pinned")
            add("artifact set matches expectedArtifacts")
            if (backend.determinism == Determinism.BITWISE) add("artifact hashes reproduce bitwise")
            else add("verdict reproduces across ${seeds.size} seeds")
        }

        val plan = RunPlan(
            specId = spec.id,
            specVersion = spec.version,
            specHash = spec.sha256(),
            planHash = "",
            backendId = backend.id,
            kernelRef = backend.kernelRefFor(spec),
            datasets = resolved,
            seeds = seeds,
            determinism = backend.determinism,
            estimatedCpuMinutes = spec.budgetCpuMinutes,
            estimatedGpuMinutes = spec.budgetGpuMinutes,
            requiredArtifacts = spec.expectedArtifacts.sorted(),
            checks = checks,
        )
        return CompileResult.Ready(plan.copy(planHash = plan.computeHash()))
    }
}

sealed interface CompileResult {
    data class Ready(val plan: RunPlan) : CompileResult
    data class Refused(val problems: List<SpecProblem>) : CompileResult {
        fun reason(): String = problems.joinToString("; ") { it.code }
    }
}

data class ResolvedDataset(
    val ref: String,
    val version: String,
    val sha256: String,
    val mountPath: String,
)

data class RunPlan(
    val specId: String,
    val specVersion: Int,
    val specHash: String,
    val planHash: String,
    val backendId: String,
    val kernelRef: String,
    val datasets: List<ResolvedDataset>,
    val seeds: List<Long>,
    val determinism: Determinism,
    val estimatedCpuMinutes: Int,
    val estimatedGpuMinutes: Int,
    val requiredArtifacts: List<String>,
    val checks: List<String>,
) {
    /**
     * The plan hash covers everything a runner needs, so a plan that drifts from its spec is
     * detectable without comparing objects field by field.
     */
    fun computeHash(): String = ExperimentSpec.sha256Hex(
        buildString {
            append("runplan/v1").append('\u001E')
            append(specId).append('\u001F')
            append(specVersion).append('\u001F')
            append(specHash).append('\u001F')
            append(backendId).append('\u001F')
            append(kernelRef).append('\u001F')
            datasets.sortedBy { it.ref }.forEach {
                append(it.ref).append('|').append(it.version).append('|')
                append(it.sha256).append('|').append(it.mountPath).append('\u001E')
            }
            append(seeds.joinToString(",")).append('\u001F')
            append(determinism.name).append('\u001F')
            append(estimatedCpuMinutes).append('\u001F')
            append(estimatedGpuMinutes).append('\u001F')
            append(requiredArtifacts.joinToString(",")).append('\u001F')
            append(checks.joinToString(","))
        }
    )
}

enum class Determinism {
    /** Re-running must reproduce artifact hashes exactly. */
    BITWISE,

    /** Stochastic: the verdict must reproduce across [RunPlan.seeds], not the bytes. */
    STATISTICAL,
}

/**
 * What a backend can actually do. This exists so the app can say "Kaggle: not configured" or
 * "cannot run this class" instead of pretending every experiment is launchable.
 */
data class BackendCapability(
    val id: String,
    val classes: Set<ExperimentClass>,
    val allowsControlledData: Boolean,
    val maxCpuMinutes: Int,
    val maxGpuMinutes: Int,
    val maxUsd: Double,
    val pinnedImages: Boolean,
    val determinism: Determinism,
    val isLocal: Boolean,
    val datasetMountPrefix: String,
) {
    fun mountPathFor(ds: DatasetRef): String =
        if (isLocal) {
            // Everything the phone runs is read from its own storage, whatever the access class.
            "file:///data/${ds.ref}/${ds.version}"
        } else {
            "${datasetMountPrefix.trimEnd('/')}/${ds.ref.replace('/', '_')}/${ds.version}"
        }

    fun kernelRefFor(spec: ExperimentSpec): String = "concordance/${spec.id}"
}

object Backends {

    /**
     * Kaggle: the compute lane. 540 GPU minutes is a deliberate ceiling well under the ~30
     * GPU-hours/week account quota, so one runaway kernel cannot consume the week.
     */
    val KAGGLE = BackendCapability(
        id = "kaggle",
        classes = setOf(
            ExperimentClass.REPLICATION,
            ExperimentClass.ROBUSTNESS,
            ExperimentClass.DATASET,
            ExperimentClass.NEGATIVE_CONTROL,
            ExperimentClass.BENCHMARK,
            ExperimentClass.SIMULATION,
        ),
        allowsControlledData = false,
        maxCpuMinutes = 720,
        maxGpuMinutes = 540,
        maxUsd = 0.0,
        pinnedImages = true,
        determinism = Determinism.BITWISE,
        isLocal = false,
        datasetMountPrefix = "/kaggle/input",
    )

    /**
     * The phone. Small exact arithmetic only: it holds the statistical engine, not the data
     * pipelines. Controlled-access files are allowed because nothing leaves the device.
     */
    val PHONE = BackendCapability(
        id = "phone",
        classes = setOf(
            ExperimentClass.REPLICATION,
            ExperimentClass.ROBUSTNESS,
            ExperimentClass.NEGATIVE_CONTROL,
            ExperimentClass.BENCHMARK,
        ),
        allowsControlledData = true,
        maxCpuMinutes = 20,
        maxGpuMinutes = 0,
        maxUsd = 0.0,
        pinnedImages = false,
        determinism = Determinism.BITWISE,
        isLocal = true,
        datasetMountPrefix = "",
    )
}

package com.example.concordance.research.experiment

import org.json.JSONArray
import org.json.JSONObject

/**
 * The bundled, typed catalog of experiments.
 *
 * This replaces the old "12 ready-to-run sub-experiments" string list. A spec is only `READY`
 * when it would actually compile: a complete spec, pinned data, a pinned image, a decision rule,
 * a class this backend can run, and no data-residency violation. Otherwise the entry carries the
 * specific reason it cannot run, and the UI renders that reason instead of a launch button.
 */
object ExperimentCatalog {

    fun parse(json: String): List<CatalogEntry> {
        val array = JSONArray(json)
        return (0 until array.length()).map { index -> entryFrom(array.getJSONObject(index)) }
            .sortedWith(compareBy({ it.status.ordinal }, { it.spec.experimentClass.ordinal }, { it.spec.id }))
    }

    fun entryFrom(o: JSONObject, backend: BackendCapability = Backends.KAGGLE): CatalogEntry {
        val spec = specFrom(o)
        val (status, reason) = statusFor(spec, backend)
        return CatalogEntry(spec = spec, status = status, reason = reason)
    }

    /**
     * The catalog's verdict on a spec.
     *
     * `READY` is defined as **"would compile with a fresh preregistration"** - it delegates to
     * [ExperimentCompiler] rather than restating its rules, so the two cannot drift apart. The
     * only additional predicate is a decision rule: a run that cannot judge a result is not ready
     * for science even if it would execute.
     */
    fun statusFor(
        spec: ExperimentSpec,
        backend: BackendCapability = Backends.KAGGLE,
    ): Pair<SpecStatus, String> {
        if (spec.experimentClass == ExperimentClass.UNKNOWN) {
            return SpecStatus.UNSUPPORTED to "experiment class is not a known value"
        }
        if (!spec.experimentClass.isExecutable) {
            return SpecStatus.DESIGN_ONLY to
                "${spec.experimentClass.label} is a design artifact; it is never submitted"
        }

        val problems = spec.problems()
        if (problems.isNotEmpty()) return SpecStatus.UNSUPPORTED to problems.first().message

        if (spec.decisionRule == null) {
            return SpecStatus.UNSUPPORTED to "no decision rule; a result could not be judged"
        }

        // Compile exactly as a submission would, with a synthetic lock. Anything the compiler
        // refuses is a reason the catalog reports verbatim.
        val probe = Preregistration(spec.sha256(), CATALOG_PROBE_TIME, "catalog")
        return when (val result = ExperimentCompiler.compile(spec, backend, probe)) {
            is CompileResult.Ready -> SpecStatus.READY to "ready to preregister"
            is CompileResult.Refused -> SpecStatus.UNSUPPORTED to result.problems.first().message
        }
    }

    /** A fixed, obviously-not-real lock time so a probe can never be mistaken for a real one. */
    private const val CATALOG_PROBE_TIME = "1970-01-01T00:00:00Z (catalog probe)"

    private fun specFrom(o: JSONObject): ExperimentSpec {
        val datasets = o.optJSONArray("datasets")?.let { arr ->
            (0 until arr.length()).map { i ->
                val d = arr.getJSONObject(i)
                DatasetRef(
                    ref = d.optString("ref"),
                    version = d.optString("version"),
                    sha256 = d.optString("sha256"),
                    license = d.optString("license"),
                    access = enumOrDefault(d.optString("access"), DataAccess.UNKNOWN),
                    dataClass = enumOrDefault(d.optString("dataClass"), DataClass.UNKNOWN),
                )
            }
        } ?: emptyList()

        val methodObj = o.optJSONObject("method") ?: JSONObject()
        val params = methodObj.optJSONObject("params")?.let { p ->
            p.keys().asSequence().associateWith { key -> p.optString(key) }
        } ?: emptyMap()

        val method = MethodRef(
            pipeline = methodObj.optString("pipeline"),
            version = methodObj.optString("version"),
            imageDigest = methodObj.optString("imageDigest"),
            params = params,
            seed = methodObj.optLong("seed", 0L),
        )

        val rule = o.optJSONObject("decisionRule")?.let { r ->
            DecisionRule(
                metric = r.optString("metric"),
                comparison = r.optString("comparison", DecisionRule.LT),
                threshold = r.optDouble("threshold", Double.NaN),
                alpha = r.optDouble("alpha", 0.05),
                powerTarget = r.optDouble("powerTarget", 0.80),
                multiplicity = r.optString("multiplicity", "none"),
                equivalenceMargin = if (r.has("equivalenceMargin")) r.optDouble("equivalenceMargin") else null,
            )
        }

        val artifacts = o.optJSONArray("expectedArtifacts")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()

        return ExperimentSpec(
            id = o.optString("id"),
            version = o.optInt("version", 1),
            experimentClass = enumOrDefault(o.optString("class"), ExperimentClass.UNKNOWN),
            title = o.optString("title"),
            hypothesis = o.optString("hypothesis"),
            direction = o.optString("direction"),
            design = o.optString("design"),
            unit = o.optString("unit"),
            contrast = o.optString("contrast"),
            outcome = o.optString("outcome"),
            datasets = datasets,
            method = method,
            decisionRule = rule,
            confirmatory = o.optBoolean("confirmatory", true),
            expectedArtifacts = artifacts,
            budgetUsd = o.optDouble("budgetUsd", 0.0),
            budgetCpuMinutes = o.optInt("budgetCpuMinutes", 0),
            budgetGpuMinutes = o.optInt("budgetGpuMinutes", 0),
            ethics = o.optString("ethics"),
        )
    }

    /**
     * Exact enum parsing. There is **no default value**: an unknown value returns [fallback],
     * which the callers set to the `UNKNOWN` member so the spec fails closed instead of silently
     * becoming the most permissive option.
     */
    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String, fallback: T): T {
        if (raw.isBlank()) return fallback
        return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback
    }
}

data class CatalogEntry(
    val spec: ExperimentSpec,
    val status: SpecStatus,
    val reason: String,
) {
    val title: String get() = spec.title
    val id: String get() = spec.id
}

enum class SpecStatus {
    /** Complete, pinned, and would compile with a fresh preregistration. */
    READY,

    /** A protocol or hypothesis document. Never submitted. */
    DESIGN_ONLY,

    /** Executable in principle but missing something concrete. */
    UNSUPPORTED,
}

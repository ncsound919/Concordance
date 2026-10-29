package com.example.concordance.evidence

/**
 * Storage contract for the evidence backbone.
 *
 * The in-memory implementation is the reference behaviour: content-addressed artifacts dedup,
 * edges merge their evidence, hypotheses cannot reference an edge that is not there. A Room/D1
 * implementation must reproduce these semantics, and its tests should reuse the same cases.
 */
interface EvidenceStore {
    fun putArtifact(artifact: SourceArtifact): PutResult<SourceArtifact>
    fun putDataset(dataset: DatasetRecord): PutResult<DatasetRecord>
    fun putEdge(edge: KgEdge): PutResult<KgEdge>
    fun putHypothesis(hypothesis: Hypothesis): PutResult<Hypothesis>
    fun addLineage(edge: LineageEdge): PutResult<LineageEdge>

    fun artifact(id: String): SourceArtifact?
    fun dataset(id: String): DatasetRecord?
    fun edge(id: String): KgEdge?
    fun hypothesis(id: String): Hypothesis?

    fun edges(): List<KgEdge>
    fun datasets(): List<DatasetRecord>
    fun artifacts(): List<SourceArtifact>
    fun hypotheses(): List<Hypothesis>
    fun lineageFor(derivedId: String): List<LineageEdge>
}

sealed interface PutResult<out T> {
    /** [created] is false when the record already existed (dedup or merge). */
    data class Stored<T>(val value: T, val created: Boolean) : PutResult<T>
    data class Rejected(val problems: List<EvidenceProblem>) : PutResult<Nothing>
}

class InMemoryEvidenceStore : EvidenceStore {

    private val artifacts = LinkedHashMap<String, SourceArtifact>()
    private val datasets = LinkedHashMap<String, DatasetRecord>()
    private val edges = LinkedHashMap<String, KgEdge>()
    private val hypotheses = LinkedHashMap<String, Hypothesis>()
    private val lineage = mutableListOf<LineageEdge>()

    override fun putArtifact(artifact: SourceArtifact): PutResult<SourceArtifact> {
        val problems = EvidenceRules.validateArtifact(artifact)
        if (problems.isNotEmpty()) return PutResult.Rejected(problems)
        val existing = artifacts[artifact.artifactId]
        if (existing != null) return PutResult.Stored(existing, created = false)
        artifacts[artifact.artifactId] = artifact
        return PutResult.Stored(artifact, created = true)
    }

    override fun putDataset(dataset: DatasetRecord): PutResult<DatasetRecord> {
        val problems = EvidenceRules.validateDataset(dataset)
        if (problems.isNotEmpty()) return PutResult.Rejected(problems)
        val existing = datasets[dataset.datasetId]
        if (existing != null) return PutResult.Stored(existing, created = false)
        datasets[dataset.datasetId] = dataset
        return PutResult.Stored(dataset, created = true)
    }

    override fun putEdge(edge: KgEdge): PutResult<KgEdge> {
        val problems = EvidenceRules.validateEdge(edge)
        if (problems.isNotEmpty()) return PutResult.Rejected(problems)
        val existing = edges[edge.edgeId]
        if (existing == null) {
            edges[edge.edgeId] = edge
            return PutResult.Stored(edge, created = true)
        }
        val merged = EvidenceRules.mergeEdges(existing, edge)
        edges[edge.edgeId] = merged
        return PutResult.Stored(merged, created = false)
    }

    override fun putHypothesis(hypothesis: Hypothesis): PutResult<Hypothesis> {
        val problems = EvidenceRules.validateHypothesis(hypothesis, edges.keys.toSet())
        if (problems.isNotEmpty()) return PutResult.Rejected(problems)
        val existed = hypotheses.containsKey(hypothesis.hypothesisId)
        hypotheses[hypothesis.hypothesisId] = hypothesis
        return PutResult.Stored(hypothesis, created = !existed)
    }

    override fun addLineage(edge: LineageEdge): PutResult<LineageEdge> {
        val problems = buildList {
            if (edge.derivedId.isBlank()) add(EvidenceProblem("LINEAGE_DERIVED", "derivedId is empty"))
            if (edge.artifactId.isBlank()) add(EvidenceProblem("LINEAGE_ARTIFACT", "artifactId is empty"))
            if (edge.transform.isBlank()) add(EvidenceProblem("LINEAGE_TRANSFORM", "transform is empty"))
            if (edge.transformVersion.isBlank()) {
                add(EvidenceProblem("LINEAGE_VERSION", "transformVersion is empty"))
            }
            if (edge.createdAtUtc.isBlank()) add(EvidenceProblem("LINEAGE_TIME", "createdAtUtc is empty"))
        }
        if (problems.isNotEmpty()) return PutResult.Rejected(problems)

        val duplicate = lineage.any {
            it.derivedKind == edge.derivedKind &&
                it.derivedId == edge.derivedId &&
                it.artifactId == edge.artifactId &&
                it.transform == edge.transform &&
                it.transformVersion == edge.transformVersion
        }
        if (duplicate) return PutResult.Stored(edge, created = false)
        lineage += edge
        return PutResult.Stored(edge, created = true)
    }

    override fun artifact(id: String): SourceArtifact? = artifacts[id]
    override fun dataset(id: String): DatasetRecord? = datasets[id]
    override fun edge(id: String): KgEdge? = edges[id]
    override fun hypothesis(id: String): Hypothesis? = hypotheses[id]

    override fun edges(): List<KgEdge> = edges.values.toList()
    override fun datasets(): List<DatasetRecord> = datasets.values.toList()
    override fun artifacts(): List<SourceArtifact> = artifacts.values.toList()
    override fun hypotheses(): List<Hypothesis> = hypotheses.values.toList()

    override fun lineageFor(derivedId: String): List<LineageEdge> =
        lineage.filter { it.derivedId == derivedId }
}

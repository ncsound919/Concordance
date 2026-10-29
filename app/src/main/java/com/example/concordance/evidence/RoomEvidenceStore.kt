package com.example.concordance.evidence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Room-backed [EvidenceStore].
 *
 * The interface is deliberately synchronous: callers (the ViewModel, the connectors) already run
 * inside a coroutine scope, and keeping the store synchronous means the in-memory store and the
 * Room store are interchangeable in tests. Internally this blocks an IO thread.
 *
 * Room DAOs are thread-safe, so no external lock is needed; a `synchronized` block is actively
 * wrong here because it forbids the suspend calls it would contain.
 */
class RoomEvidenceStore(
    private val dao: EvidenceDao,
    private val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : EvidenceStore {

    override fun putArtifact(artifact: SourceArtifact): PutResult<SourceArtifact> {
        val problems = EvidenceRules.validateArtifact(artifact)
        if (problems.isNotEmpty()) return PutResult.Rejected(problems)
        return runBlocking(io) {
            val existing = dao.getArtifact(artifact.artifactId)
            if (existing != null) {
                PutResult.Stored(existing.toDomain(), created = false)
            } else {
                dao.upsertArtifact(SourceArtifactEntity.fromDomain(artifact))
                PutResult.Stored(artifact, created = true)
            }
        }
    }

    override fun putDataset(dataset: DatasetRecord): PutResult<DatasetRecord> {
        val problems = EvidenceRules.validateDataset(dataset)
        if (problems.isNotEmpty()) return PutResult.Rejected(problems)
        return runBlocking(io) {
            val existing = dao.getDataset(dataset.datasetId)
            if (existing != null) {
                PutResult.Stored(existing.toDomain(), created = false)
            } else {
                dao.upsertDataset(DatasetRecordEntity.fromDomain(dataset))
                PutResult.Stored(dataset, created = true)
            }
        }
    }

    override fun putEdge(edge: KgEdge): PutResult<KgEdge> {
        val problems = EvidenceRules.validateEdge(edge)
        if (problems.isNotEmpty()) return PutResult.Rejected(problems)
        return runBlocking(io) {
            val existing = dao.getEdge(edge.edgeId)
            if (existing == null) {
                dao.upsertEdge(KgEdgeEntity.fromDomain(edge))
                PutResult.Stored(edge, created = true)
            } else {
                val merged = EvidenceRules.mergeEdges(existing.toDomain(), edge)
                dao.upsertEdge(KgEdgeEntity.fromDomain(merged))
                PutResult.Stored(merged, created = false)
            }
        }
    }

    override fun putHypothesis(hypothesis: Hypothesis): PutResult<Hypothesis> {
        val problems = EvidenceRules.validateHypothesis(hypothesis, knownEdgeIds())
        if (problems.isNotEmpty()) return PutResult.Rejected(problems)
        return runBlocking(io) {
            val existed = dao.getHypothesis(hypothesis.hypothesisId) != null
            dao.upsertHypothesis(HypothesisEntity.fromDomain(hypothesis))
            PutResult.Stored(hypothesis, created = !existed)
        }
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
        return runBlocking(io) {
            val duplicate = dao.allLineage().any {
                it.derivedKind == edge.derivedKind.name &&
                    it.derivedId == edge.derivedId &&
                    it.artifactId == edge.artifactId &&
                    it.transform == edge.transform &&
                    it.transformVersion == edge.transformVersion
            }
            if (duplicate) {
                PutResult.Stored(edge, created = false)
            } else {
                dao.insertLineage(LineageEdgeEntity.fromDomain(edge))
                PutResult.Stored(edge, created = true)
            }
        }
    }

    override fun artifact(id: String): SourceArtifact? =
        runBlocking(io) { dao.getArtifact(id)?.toDomain() }

    override fun dataset(id: String): DatasetRecord? =
        runBlocking(io) { dao.getDataset(id)?.toDomain() }

    override fun edge(id: String): KgEdge? =
        runBlocking(io) { dao.getEdge(id)?.toDomain() }

    override fun hypothesis(id: String): Hypothesis? =
        runBlocking(io) { dao.getHypothesis(id)?.toDomain() }

    override fun edges(): List<KgEdge> =
        runBlocking(io) { dao.allEdges().map { it.toDomain() } }

    override fun datasets(): List<DatasetRecord> =
        runBlocking(io) { dao.allDatasets().map { it.toDomain() } }

    override fun artifacts(): List<SourceArtifact> =
        runBlocking(io) { dao.allArtifacts().map { it.toDomain() } }

    override fun hypotheses(): List<Hypothesis> =
        runBlocking(io) { dao.allHypotheses().map { it.toDomain() } }

    override fun lineageFor(derivedId: String): List<LineageEdge> =
        runBlocking(io) { dao.lineageFor(derivedId).map { it.toDomain() } }

    private fun knownEdgeIds(): Set<String> = runBlocking(io) { dao.allEdges().map { it.edgeId }.toSet() }
}

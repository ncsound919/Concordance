package com.example.concordance.evidence

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Room-backed [EvidenceStore].
 *
 * The interface is deliberately synchronous: callers (the ViewModel, the connectors) already run
 * inside a coroutine scope, and keeping the store synchronous means the in-memory store and the
 * Room store are interchangeable in tests. Internally this blocks an IO thread.
 *
 * Every read-modify-write below runs inside a single Room transaction ([inTransaction]), so two
 * concurrent imports cannot both observe "absent" and both report `created = true`, and an edge
 * merge can never overwrite evidence it did not first read. A `synchronized` block would be
 * actively wrong here because it only serialises one process and forbids the suspend calls.
 */
class RoomEvidenceStore(
    private val dao: EvidenceDao,
    private val db: RoomDatabase,
    private val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : EvidenceStore {

    /** Runs a read-modify-write sequence atomically; readers outside see before or after, never between. */
    private fun <T> inTransaction(block: suspend () -> T): T = runBlocking(io) {
        db.withTransaction { block() }
    }

    override fun putArtifact(artifact: SourceArtifact): PutResult<SourceArtifact> {
        val problems = EvidenceRules.validateArtifact(artifact)
        if (problems.isNotEmpty()) return PutResult.Rejected(problems)
        return inTransaction {
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
        return inTransaction {
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
        return inTransaction {
            val existing = dao.getEdge(edge.edgeId)
            if (existing == null) {
                dao.upsertEdge(KgEdgeEntity.fromDomain(edge))
                PutResult.Stored(edge, created = true)
            } else {
                // Merge against the row read in *this* transaction: a concurrent
                // merge of the same edge cannot be lost.
                val merged = EvidenceRules.mergeEdges(existing.toDomain(), edge)
                dao.upsertEdge(KgEdgeEntity.fromDomain(merged))
                PutResult.Stored(merged, created = false)
            }
        }
    }

    override fun putHypothesis(hypothesis: Hypothesis): PutResult<Hypothesis> {
        val problems = EvidenceRules.validateHypothesis(hypothesis, knownEdgeIds())
        if (problems.isNotEmpty()) return PutResult.Rejected(problems)
        return inTransaction {
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
            if (edge.createdAtUtc.isBlank()) {
                add(EvidenceProblem("LINEAGE_TIME", "createdAtUtc is empty"))
            }
        }
        if (problems.isNotEmpty()) return PutResult.Rejected(problems)
        return inTransaction {
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

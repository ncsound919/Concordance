package com.example.concordance.evidence.connectors

import com.example.concordance.evidence.DatasetRecord
import com.example.concordance.evidence.DerivedKind
import com.example.concordance.evidence.EvidenceHash
import com.example.concordance.evidence.EvidenceKind
import com.example.concordance.evidence.EvidenceProblem
import com.example.concordance.evidence.EvidenceRef
import com.example.concordance.evidence.EvidenceStore
import com.example.concordance.evidence.KgEdge
import com.example.concordance.evidence.LineageEdge
import com.example.concordance.evidence.NodeKind
import com.example.concordance.evidence.NodeRef
import com.example.concordance.evidence.Predicate
import com.example.concordance.evidence.PutResult
import com.example.concordance.evidence.SourceArtifact
import com.example.concordance.evidence.ArtifactType
import com.example.concordance.integration.api.CohortStudy
import com.example.concordance.integration.api.CuratedVariantEvidence
import com.example.concordance.integration.science.SciencePaper
import com.example.concordance.integration.wetlab.WetLabRecord
import java.util.Locale

/**
 * Connectors turn an existing integration's result into evidence-backbone records.
 *
 * They are pure: no network, no store, no clock of their own. That is what makes them testable
 * from fixtures and what stops a connector from inventing evidence it did not receive.
 *
 * Every connector reports problems instead of dropping data quietly. A cohort with no sample
 * count is refused, not stored with zero; a CIViC item with no therapy and no disease is refused,
 * not given a placeholder object.
 */
data class ConnectorResult(
    val artifacts: List<SourceArtifact> = emptyList(),
    val datasets: List<DatasetRecord> = emptyList(),
    val edges: List<KgEdge> = emptyList(),
    val lineage: List<LineageEdge> = emptyList(),
    val problems: List<EvidenceProblem> = emptyList(),
) {
    val isEmpty: Boolean
        get() = artifacts.isEmpty() && datasets.isEmpty() && edges.isEmpty() && lineage.isEmpty()
}

/** What [ConnectorIngest] actually wrote. */
data class IngestSummary(
    val artifactsCreated: Int,
    val artifactsDeduplicated: Int,
    val datasetsCreated: Int,
    val edgesCreated: Int,
    val edgesMerged: Int,
    val lineageAdded: Int,
    val rejected: List<EvidenceProblem>,
) {
    val wrote: Boolean get() = artifactsCreated + datasetsCreated + edgesCreated + lineageAdded > 0
}

object ConnectorIngest {

    fun write(store: EvidenceStore, result: ConnectorResult): IngestSummary {
        var artifactsCreated = 0
        var artifactsDeduplicated = 0
        var datasetsCreated = 0
        var edgesCreated = 0
        var edgesMerged = 0
        var lineageAdded = 0
        val rejected = result.problems.toMutableList()

        result.artifacts.forEach { artifact ->
            when (val put = store.putArtifact(artifact)) {
                is PutResult.Stored -> if (put.created) artifactsCreated++ else artifactsDeduplicated++
                is PutResult.Rejected -> rejected += put.problems
            }
        }
        result.datasets.forEach { dataset ->
            when (val put = store.putDataset(dataset)) {
                is PutResult.Stored -> if (put.created) datasetsCreated++
                is PutResult.Rejected -> rejected += put.problems
            }
        }
        result.edges.forEach { edge ->
            when (val put = store.putEdge(edge)) {
                is PutResult.Stored -> if (put.created) edgesCreated++ else edgesMerged++
                is PutResult.Rejected -> rejected += put.problems
            }
        }
        result.lineage.forEach { line ->
            when (val put = store.addLineage(line)) {
                is PutResult.Stored -> if (put.created) lineageAdded++
                is PutResult.Rejected -> rejected += put.problems
            }
        }

        return IngestSummary(
            artifactsCreated = artifactsCreated,
            artifactsDeduplicated = artifactsDeduplicated,
            datasetsCreated = datasetsCreated,
            edgesCreated = edgesCreated,
            edgesMerged = edgesMerged,
            lineageAdded = lineageAdded,
            rejected = rejected,
        )
    }
}

// --- literature ------------------------------------------------------------------------------

object LiteratureConnector {

    /** A paper becomes a content-addressed artifact, so the same paper is never stored twice. */
    fun fromPaper(paper: SciencePaper, atUtc: String): ConnectorResult {
        val identity = listOf(
            paper.doi.lowercase(Locale.US),
            paper.pmid.orEmpty(),
            paper.title.trim().lowercase(Locale.US),
            paper.abstractText.trim(),
        ).joinToString("|")
        val hash = EvidenceHash.sha256(identity)
        val origin = when {
            !paper.doi.isBlank() -> "doi:${paper.doi}"
            !paper.pmid.isNullOrBlank() -> "pmid:${paper.pmid}"
            else -> "paper:${paper.id}"
        }
        val artifact = SourceArtifact.create(
            contentHash = hash,
            type = ArtifactType.PAPER_XML,
            originUri = origin,
            mimeType = "application/json",
            sizeBytes = paper.abstractText.toByteArray(Charsets.UTF_8).size.toLong(),
            ingestedAtUtc = atUtc,
        )
        return ConnectorResult(artifacts = listOf(artifact))
    }

    fun evidenceRef(paper: SciencePaper): EvidenceRef = EvidenceRef(
        kind = EvidenceKind.PAPER,
        id = when {
            !paper.pmid.isNullOrBlank() -> "pmid:${paper.pmid}"
            !paper.doi.isBlank() -> "doi:${paper.doi}"
            else -> "paper:${paper.id}"
        },
    )
}

// --- cBioPortal cohorts ----------------------------------------------------------------------

object CohortConnector {

    /**
     * A cBioPortal study becomes a dataset record. It arrives **unpinned**: cBioPortal does not
     * give us a content hash, and inventing one would defeat the pin.
     */
    fun fromCohort(cohort: CohortStudy): ConnectorResult {
        val samples = cohort.sampleCount ?: 0
        if (samples <= 0) {
            return ConnectorResult(
                problems = listOf(
                    EvidenceProblem(
                        "COHORT_NO_SAMPLES",
                        "study '${cohort.studyId}' has no sample count; it cannot be a dataset record",
                    )
                )
            )
        }
        val dataset = DatasetRecord.create(
            source = "cbioportal",
            studyId = cohort.studyId,
            cancerType = cohort.cancerType?.takeIf { it.isNotBlank() } ?: "unspecified",
            assayType = "unspecified",
            sampleCount = samples,
            checksum = "",
            license = "per-study terms (${cohort.url})",
        )
        return ConnectorResult(datasets = listOf(dataset))
    }
}

// --- CIViC curated evidence ------------------------------------------------------------------

object CivicConnector {

    /**
     * One curated CIViC item becomes one graph edge, backed by one artifact.
     *
     * Direction matters: a sensitivity claim and a resistance claim are different predicates.
     * Collapsing them into "associated_with" would let a resistance result support a therapy.
     */
    fun fromEvidence(evidence: CuratedVariantEvidence, atUtc: String): ConnectorResult {
        val gene = evidence.gene.trim().uppercase(Locale.US)
        if (gene.isBlank()) {
            return ConnectorResult(problems = listOf(EvidenceProblem("CIVIC_NO_GENE", "CIViC item has no gene")))
        }
        val therapies = evidence.therapies.map { it.trim() }.filter { it.isNotBlank() }
        val disease = evidence.disease.trim()

        val predicate = when {
            evidence.significance.contains("sensitivity", ignoreCase = true) ||
                evidence.significance.contains("response", ignoreCase = true) -> Predicate.SENSITIVE_TO
            evidence.significance.contains("resistance", ignoreCase = true) -> Predicate.RESISTANT_TO
            else -> Predicate.ASSOCIATED_WITH
        }

        val objectRef = when {
            therapies.isNotEmpty() -> NodeRef(
                kind = NodeKind.DRUG,
                id = "drug:" + slug(therapies.first()),
                label = therapies.first(),
            )
            disease.isNotBlank() -> NodeRef(
                kind = NodeKind.CANCER_TYPE,
                id = "disease:" + slug(disease),
                label = disease,
            )
            else -> return ConnectorResult(
                problems = listOf(
                    EvidenceProblem(
                        "CIVIC_NO_OBJECT",
                        "CIViC EID${evidence.civicEvidenceId} has neither a therapy nor a disease",
                    )
                )
            )
        }

        val artifact = SourceArtifact.create(
            contentHash = EvidenceHash.sha256(
                listOf(
                    evidence.civicEvidenceId.toString(),
                    gene,
                    evidence.variant,
                    evidence.significance,
                    evidence.disease,
                    evidence.therapies.joinToString(","),
                    evidence.description,
                ).joinToString("|")
            ),
            type = ArtifactType.API_RECORD,
            originUri = evidence.civicUrl.ifBlank { "civic:EID${evidence.civicEvidenceId}" },
            mimeType = "application/json",
            sizeBytes = evidence.description.toByteArray(Charsets.UTF_8).size.toLong(),
            ingestedAtUtc = atUtc,
        )

        val subject = NodeRef(NodeKind.GENE, "gene:$gene", gene)
        val edge = KgEdge(
            edgeId = KgEdge.idFor(subject, predicate, objectRef),
            subject = subject,
            predicate = predicate,
            objectRef = objectRef,
            evidence = listOf(EvidenceRef(EvidenceKind.ARTIFACT, artifact.artifactId)),
            // CIViC evidence levels are ordinal letters; this is a coarse, documented mapping and
            // deliberately not a substitute for the level itself.
            score = scoreFor(evidence.evidenceLevel, evidence.evidenceRating),
            createdBy = "civic-connector/1",
            createdAtUtc = atUtc,
        )

        val lineage = LineageEdge(
            derivedKind = DerivedKind.KG_EDGE,
            derivedId = edge.edgeId,
            artifactId = artifact.artifactId,
            transform = "civic_to_edge",
            transformVersion = "1",
            createdAtUtc = atUtc,
        )

        return ConnectorResult(
            artifacts = listOf(artifact),
            edges = listOf(edge),
            lineage = listOf(lineage),
        )
    }

    /** A/B are high-confidence, C is moderate, D/E and unknown are low. Rating nudges upward. */
    private fun scoreFor(level: String, rating: Int?): Double {
        val base = when (level.trim().uppercase(Locale.US)) {
            "A" -> 0.90
            "B" -> 0.80
            "C" -> 0.65
            "D" -> 0.50
            "E" -> 0.40
            else -> 0.30
        }
        val nudge = when {
            rating == null -> 0.0
            rating >= 4 -> 0.05
            rating <= 1 -> -0.05
            else -> 0.0
        }
        return (base + nudge).coerceIn(0.0, 1.0)
    }

    private fun slug(value: String): String = value.trim().lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}

// --- wet lab ---------------------------------------------------------------------------------

object WetLabConnector {

    /**
     * A [WetLabRecord] is already hash-verified by [com.example.concordance.integration.wetlab.WetLabDataIngester].
     * Here it becomes an artifact so any edge built from it can cite bytes rather than a label.
     */
    fun fromRecord(record: WetLabRecord, atUtc: String): ConnectorResult {
        if (!record.isVerified()) {
            return ConnectorResult(
                problems = listOf(
                    EvidenceProblem(
                        "WETLAB_UNVERIFIED",
                        "record '${record.id}' does not match its verification hash",
                    )
                )
            )
        }
        val artifact = SourceArtifact.create(
            contentHash = record.computeHash(),
            type = ArtifactType.WETLAB_FILE,
            originUri = "wetlab:${record.source}",
            mimeType = "application/json",
            sizeBytes = record.notes.toByteArray(Charsets.UTF_8).size.toLong(),
            ingestedAtUtc = atUtc,
        )
        return ConnectorResult(artifacts = listOf(artifact))
    }

    fun evidenceRef(record: WetLabRecord): EvidenceRef =
        EvidenceRef(EvidenceKind.WETLAB, "wet:" + record.computeHash().take(16))
}

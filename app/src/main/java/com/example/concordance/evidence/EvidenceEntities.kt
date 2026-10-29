package com.example.concordance.evidence

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.concordance.research.experiment.DataAccess
import com.example.concordance.research.experiment.DataClass

/**
 * Room mirror of the evidence backbone. Lists are stored as JSON strings, matching the
 * convention used for `provenanceRecordIds` on `CapsuleEntity`.
 */
@Entity(tableName = "source_artifacts")
data class SourceArtifactEntity(
    @PrimaryKey val artifactId: String,
    val type: String,
    val originUri: String,
    val contentHash: String,
    val mimeType: String,
    val sizeBytes: Long,
    val ingestedAtUtc: String,
    val ingestionRunId: String?,
) {
    fun toDomain(): SourceArtifact = SourceArtifact(
        artifactId = artifactId,
        type = ArtifactType.valueOf(type),
        originUri = originUri,
        contentHash = contentHash,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        ingestedAtUtc = ingestedAtUtc,
        ingestionRunId = ingestionRunId,
    )

    companion object {
        fun fromDomain(a: SourceArtifact) = SourceArtifactEntity(
            artifactId = a.artifactId,
            type = a.type.name,
            originUri = a.originUri,
            contentHash = a.contentHash,
            mimeType = a.mimeType,
            sizeBytes = a.sizeBytes,
            ingestedAtUtc = a.ingestedAtUtc,
            ingestionRunId = a.ingestionRunId,
        )
    }
}

@Entity(tableName = "lineage_edges")
data class LineageEdgeEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val derivedKind: String,
    val derivedId: String,
    val artifactId: String,
    val transform: String,
    val transformVersion: String,
    val createdAtUtc: String,
) {
    fun toDomain(): LineageEdge = LineageEdge(
        derivedKind = DerivedKind.valueOf(derivedKind),
        derivedId = derivedId,
        artifactId = artifactId,
        transform = transform,
        transformVersion = transformVersion,
        createdAtUtc = createdAtUtc,
    )

    companion object {
        fun fromDomain(e: LineageEdge) = LineageEdgeEntity(
            derivedKind = e.derivedKind.name,
            derivedId = e.derivedId,
            artifactId = e.artifactId,
            transform = e.transform,
            transformVersion = e.transformVersion,
            createdAtUtc = e.createdAtUtc,
        )
    }
}

@Entity(tableName = "datasets")
data class DatasetRecordEntity(
    @PrimaryKey val datasetId: String,
    val source: String,
    val studyId: String,
    val cancerType: String,
    val assayType: String,
    val sampleCount: Int,
    val checksum: String,
    val license: String,
    val access: String,
    val dataClass: String,
) {
    fun toDomain(): DatasetRecord = DatasetRecord(
        datasetId = datasetId,
        source = source,
        studyId = studyId,
        cancerType = cancerType,
        assayType = assayType,
        sampleCount = sampleCount,
        checksum = checksum,
        license = license,
        access = DataAccess.valueOf(access),
        dataClass = DataClass.valueOf(dataClass),
    )

    companion object {
        fun fromDomain(d: DatasetRecord) = DatasetRecordEntity(
            datasetId = d.datasetId,
            source = d.source,
            studyId = d.studyId,
            cancerType = d.cancerType,
            assayType = d.assayType,
            sampleCount = d.sampleCount,
            checksum = d.checksum,
            license = d.license,
            access = d.access.name,
            dataClass = d.dataClass.name,
        )
    }
}

@Entity(tableName = "kg_edges")
data class KgEdgeEntity(
    @PrimaryKey val edgeId: String,
    val subjectKind: String,
    val subjectId: String,
    val subjectLabel: String,
    val predicate: String,
    val objectKind: String,
    val objectId: String,
    val objectLabel: String,
    val evidenceJson: String,
    val score: Double,
    val createdBy: String,
    val createdAtUtc: String,
) {
    fun toDomain(): KgEdge = KgEdge(
        edgeId = edgeId,
        subject = NodeRef(NodeKind.valueOf(subjectKind), subjectId, subjectLabel),
        predicate = Predicate.valueOf(predicate),
        objectRef = NodeRef(NodeKind.valueOf(objectKind), objectId, objectLabel),
        evidence = parseRefs(evidenceJson),
        score = score,
        createdBy = createdBy,
        createdAtUtc = createdAtUtc,
    )

    private fun parseRefs(json: String): List<EvidenceRef> {
        if (json.isBlank()) return emptyList()
        return json.split(";", ignoreCase = false, limit = 0)
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val parts = token.split(":", limit = 2)
                if (parts.size != 2) null
                else runCatching {
                    EvidenceRef(EvidenceKind.valueOf(parts[0]), parts[1])
                }.getOrNull()
            }
    }

    companion object {
        fun fromDomain(e: KgEdge) = KgEdgeEntity(
            edgeId = e.edgeId,
            subjectKind = e.subject.kind.name,
            subjectId = e.subject.id,
            subjectLabel = e.subject.label,
            predicate = e.predicate.name,
            objectKind = e.objectRef.kind.name,
            objectId = e.objectRef.id,
            objectLabel = e.objectRef.label,
            evidenceJson = e.evidence.joinToString(";") { "${it.kind.name}:${it.id}" },
            score = e.score,
            createdBy = e.createdBy,
            createdAtUtc = e.createdAtUtc,
        )
    }
}

@Entity(tableName = "hypotheses")
data class HypothesisEntity(
    @PrimaryKey val hypothesisId: String,
    val cancerType: String,
    val biomarkerSymbol: String,
    val biomarkerType: String,
    val candidateDrug: String,
    val drugRxnorm: String,
    val rationale: String,
    val supportingEdgeIdsJson: String,
    val pubmedIdsJson: String,
    val datasetIdsJson: String,
    val wetlabIdsJson: String,
    val rankScore: Double,
    val modelVersion: String,
    val generatedAtUtc: String,
    val reviewStatus: String,
    val reviewer: String?,
    val reviewedAtUtc: String?,
) {
    private fun parseList(json: String): List<String> =
        if (json.isBlank()) emptyList() else json.split("\u001F").filter { it.isNotBlank() }

    private fun encodeList(items: List<String>): String = items.joinToString("\u001F")

    fun toDomain(): Hypothesis = Hypothesis(
        hypothesisId = hypothesisId,
        cancerType = cancerType,
        biomarkerSymbol = biomarkerSymbol,
        biomarkerType = biomarkerType,
        candidateDrug = candidateDrug,
        drugRxnorm = drugRxnorm,
        rationale = rationale,
        supportingEdgeIds = parseList(supportingEdgeIdsJson),
        pubmedIds = parseList(pubmedIdsJson),
        datasetIds = parseList(datasetIdsJson),
        wetlabIds = parseList(wetlabIdsJson),
        rankScore = rankScore,
        modelVersion = modelVersion,
        generatedAtUtc = generatedAtUtc,
        reviewStatus = ReviewStatus.valueOf(reviewStatus),
        reviewer = reviewer,
        reviewedAtUtc = reviewedAtUtc,
    )

    companion object {
        fun fromDomain(h: Hypothesis) = HypothesisEntity(
            hypothesisId = h.hypothesisId,
            cancerType = h.cancerType,
            biomarkerSymbol = h.biomarkerSymbol,
            biomarkerType = h.biomarkerType,
            candidateDrug = h.candidateDrug,
            drugRxnorm = h.drugRxnorm,
            rationale = h.rationale,
            supportingEdgeIdsJson = h.supportingEdgeIds.joinToString("\u001F"),
            pubmedIdsJson = h.pubmedIds.joinToString("\u001F"),
            datasetIdsJson = h.datasetIds.joinToString("\u001F"),
            wetlabIdsJson = h.wetlabIds.joinToString("\u001F"),
            rankScore = h.rankScore,
            modelVersion = h.modelVersion,
            generatedAtUtc = h.generatedAtUtc,
            reviewStatus = h.reviewStatus.name,
            reviewer = h.reviewer,
            reviewedAtUtc = h.reviewedAtUtc,
        )
    }
}

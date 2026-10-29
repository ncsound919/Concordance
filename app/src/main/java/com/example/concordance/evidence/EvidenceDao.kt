package com.example.concordance.evidence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EvidenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtifact(entity: SourceArtifactEntity)

    @Query("SELECT * FROM source_artifacts WHERE artifactId = :id LIMIT 1")
    suspend fun getArtifact(id: String): SourceArtifactEntity?

    @Query("SELECT * FROM source_artifacts ORDER BY ingestedAtUtc DESC")
    suspend fun allArtifacts(): List<SourceArtifactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDataset(entity: DatasetRecordEntity)

    @Query("SELECT * FROM datasets WHERE datasetId = :id LIMIT 1")
    suspend fun getDataset(id: String): DatasetRecordEntity?

    @Query("SELECT * FROM datasets ORDER BY source, studyId")
    suspend fun allDatasets(): List<DatasetRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEdge(entity: KgEdgeEntity)

    @Query("SELECT * FROM kg_edges WHERE edgeId = :id LIMIT 1")
    suspend fun getEdge(id: String): KgEdgeEntity?

    @Query("SELECT * FROM kg_edges ORDER BY createdAtUtc DESC")
    suspend fun allEdges(): List<KgEdgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHypothesis(entity: HypothesisEntity)

    @Query("SELECT * FROM hypotheses WHERE hypothesisId = :id LIMIT 1")
    suspend fun getHypothesis(id: String): HypothesisEntity?

    @Query("SELECT * FROM hypotheses ORDER BY generatedAtUtc DESC")
    suspend fun allHypotheses(): List<HypothesisEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLineage(entity: LineageEdgeEntity)

    @Query("SELECT * FROM lineage_edges WHERE derivedId = :derivedId")
    suspend fun lineageFor(derivedId: String): List<LineageEdgeEntity>

    @Query("SELECT * FROM lineage_edges")
    suspend fun allLineage(): List<LineageEdgeEntity>
}

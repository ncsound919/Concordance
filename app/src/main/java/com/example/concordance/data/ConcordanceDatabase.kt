package com.example.concordance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.concordance.data.ClaimEntity
import com.example.concordance.evidence.DatasetRecordEntity
import com.example.concordance.evidence.EvidenceDao
import com.example.concordance.evidence.HypothesisEntity
import com.example.concordance.evidence.KgEdgeEntity
import com.example.concordance.evidence.LineageEdgeEntity
import com.example.concordance.evidence.SourceArtifactEntity

@Database(
    entities = [ClaimEntity::class, CapsuleEntity::class, ChallengeEntity::class, ProvenanceEntity::class, CachedResponseEntity::class, ModelRevisionEntity::class, LearningAuditEntity::class, SyncCursorEntity::class, SourceArtifactEntity::class, LineageEdgeEntity::class, DatasetRecordEntity::class, KgEdgeEntity::class, HypothesisEntity::class],
    version = 8,
    exportSchema = false
)
abstract class ConcordanceDatabase : RoomDatabase() {
    abstract fun claimDao(): ClaimDao
    abstract fun capsuleDao(): CapsuleDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun provenanceDao(): ProvenanceDao
    abstract fun cachedResponseDao(): CachedResponseDao
    abstract fun modelRevisionDao(): ModelRevisionDao
    abstract fun learningAuditDao(): LearningAuditDao
    abstract fun syncCursorDao(): SyncCursorDao
    abstract fun evidenceDao(): EvidenceDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS provenance_records (" +
                        "pk INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "providerId TEXT NOT NULL, " +
                        "endpoint TEXT NOT NULL, " +
                        "query TEXT NOT NULL, " +
                        "httpStatus INTEGER NOT NULL, " +
                        "retrievedUtc TEXT NOT NULL, " +
                        "responseSha256 TEXT NOT NULL, " +
                        "licenseClass TEXT NOT NULL, " +
                        "elapsedMs INTEGER NOT NULL, " +
                        "capsuleId TEXT" +
                        ")"
                )
                database.execSQL("ALTER TABLE capsules ADD COLUMN provenanceDigest TEXT")
                database.execSQL("ALTER TABLE capsules ADD COLUMN provenanceRecordIds TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS response_cache (" +
                        "cacheKey TEXT NOT NULL PRIMARY KEY, " +
                        "body TEXT NOT NULL, " +
                        "sha256 TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "ttlMs INTEGER NOT NULL" +
                        ")"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Link claims and capsules to the model revision that produced them
                // (null = baseline). Append-only revision log.
                database.execSQL("ALTER TABLE claims ADD COLUMN modelRevisionId TEXT")
                database.execSQL("ALTER TABLE capsules ADD COLUMN modelRevisionId TEXT")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS model_revisions (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "parentRevisionId TEXT, " +
                        "createdAtUtc TEXT NOT NULL, " +
                        "parametersJson TEXT NOT NULL, " +
                        "metricE REAL NOT NULL, " +
                        "promotedBy TEXT, " +
                        "notes TEXT" +
                        ")"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS learning_audit (" +
                        "pk INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "candidateParamsJson TEXT NOT NULL, " +
                        "baselineParamsJson TEXT NOT NULL, " +
                        "metricTotal REAL NOT NULL, " +
                        "stabilityPenalty REAL NOT NULL, " +
                        "resolutionMiss REAL NOT NULL, " +
                        "calibrationError REAL NOT NULL, " +
                        "parsimony REAL NOT NULL, " +
                        "decision TEXT NOT NULL, " +
                        "approver TEXT, " +
                        "revisionId TEXT, " +
                        "createdAtUtc TEXT NOT NULL, " +
                        "notes TEXT NOT NULL" +
                        ")"
                )
                database.execSQL("ALTER TABLE capsules ADD COLUMN supersedesCapsuleId TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Sync metadata columns
                database.execSQL("ALTER TABLE claims ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'local'")
                database.execSQL("ALTER TABLE claims ADD COLUMN serverUpdatedAt INTEGER")
                database.execSQL("ALTER TABLE claims ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE claims ADD COLUMN deletedAt INTEGER")
                database.execSQL("ALTER TABLE capsules ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'local'")
                database.execSQL("ALTER TABLE capsules ADD COLUMN serverUpdatedAt INTEGER")
                database.execSQL("ALTER TABLE capsules ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE capsules ADD COLUMN deletedAt INTEGER")
                database.execSQL("ALTER TABLE challenges ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'local'")
                database.execSQL("ALTER TABLE challenges ADD COLUMN serverUpdatedAt INTEGER")
                database.execSQL("ALTER TABLE challenges ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE challenges ADD COLUMN deletedAt INTEGER")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS sync_cursor (" +
                        "entity TEXT NOT NULL PRIMARY KEY, " +
                        "lastPulledAt TEXT" +
                        ")"
                )
            }
        }

        /**
         * Records carry their own data classification so the sync boundary can
         * check it instead of asserting `aggregate` on every push. Existing rows
         * were all pushed as aggregate, so they migrate to that value.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE claims ADD COLUMN dataClass TEXT NOT NULL DEFAULT 'aggregate'"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS source_artifacts (" +
                        "artifactId TEXT NOT NULL PRIMARY KEY, " +
                        "type TEXT NOT NULL, " +
                        "originUri TEXT NOT NULL, " +
                        "contentHash TEXT NOT NULL, " +
                        "mimeType TEXT NOT NULL, " +
                        "sizeBytes INTEGER NOT NULL, " +
                        "ingestedAtUtc TEXT NOT NULL, " +
                        "ingestionRunId TEXT" +
                        ")"
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_artifact_hash ON source_artifacts(contentHash)")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS lineage_edges (" +
                        "pk INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "derivedKind TEXT NOT NULL, " +
                        "derivedId TEXT NOT NULL, " +
                        "artifactId TEXT NOT NULL, " +
                        "transform TEXT NOT NULL, " +
                        "transformVersion TEXT NOT NULL, " +
                        "createdAtUtc TEXT NOT NULL" +
                        ")"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_lineage_derived ON lineage_edges(derivedId)")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS datasets (" +
                        "datasetId TEXT NOT NULL PRIMARY KEY, " +
                        "source TEXT NOT NULL, " +
                        "studyId TEXT NOT NULL, " +
                        "cancerType TEXT NOT NULL, " +
                        "assayType TEXT NOT NULL, " +
                        "sampleCount INTEGER NOT NULL, " +
                        "checksum TEXT NOT NULL, " +
                        "license TEXT NOT NULL, " +
                        "access TEXT NOT NULL, " +
                        "dataClass TEXT NOT NULL" +
                        ")"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS kg_edges (" +
                        "edgeId TEXT NOT NULL PRIMARY KEY, " +
                        "subjectKind TEXT NOT NULL, " +
                        "subjectId TEXT NOT NULL, " +
                        "subjectLabel TEXT NOT NULL, " +
                        "predicate TEXT NOT NULL, " +
                        "objectKind TEXT NOT NULL, " +
                        "objectId TEXT NOT NULL, " +
                        "objectLabel TEXT NOT NULL, " +
                        "evidenceJson TEXT NOT NULL, " +
                        "score REAL NOT NULL, " +
                        "createdBy TEXT NOT NULL, " +
                        "createdAtUtc TEXT NOT NULL" +
                        ")"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS hypotheses (" +
                        "hypothesisId TEXT NOT NULL PRIMARY KEY, " +
                        "cancerType TEXT NOT NULL, " +
                        "biomarkerSymbol TEXT NOT NULL, " +
                        "biomarkerType TEXT NOT NULL, " +
                        "candidateDrug TEXT NOT NULL, " +
                        "drugRxnorm TEXT NOT NULL, " +
                        "rationale TEXT NOT NULL, " +
                        "supportingEdgeIdsJson TEXT NOT NULL, " +
                        "pubmedIdsJson TEXT NOT NULL, " +
                        "datasetIdsJson TEXT NOT NULL, " +
                        "wetlabIdsJson TEXT NOT NULL, " +
                        "rankScore REAL NOT NULL, " +
                        "modelVersion TEXT NOT NULL, " +
                        "generatedAtUtc TEXT NOT NULL, " +
                        "reviewStatus TEXT NOT NULL, " +
                        "reviewer TEXT, " +
                        "reviewedAtUtc TEXT" +
                        ")"
                )
            }
        }

        @Volatile
        private var INSTANCE: ConcordanceDatabase? = null

        fun getDatabase(context: Context): ConcordanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ConcordanceDatabase::class.java,
                    "concordance_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

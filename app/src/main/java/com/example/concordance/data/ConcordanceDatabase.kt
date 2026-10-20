package com.example.concordance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ClaimEntity::class, CapsuleEntity::class, ChallengeEntity::class, ProvenanceEntity::class, CachedResponseEntity::class, ModelRevisionEntity::class, LearningAuditEntity::class, SyncCursorEntity::class],
    version = 6,
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

        @Volatile
        private var INSTANCE: ConcordanceDatabase? = null

        fun getDatabase(context: Context): ConcordanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ConcordanceDatabase::class.java,
                    "concordance_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

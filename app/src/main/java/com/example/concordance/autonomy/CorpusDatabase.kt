package com.example.concordance.autonomy

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * On-disk store for the local corpus: one row per indexed file, the extracted text split into
 * overlapping chunks, and a full-text index over those chunks.
 *
 * The previous version kept every paper's text in a JSON file and loaded all of it into RAM,
 * which capped the corpus at a few megabytes — roughly sixty papers. A folder of thousands of
 * papers does not fit in a list, and it does not need to: SQLite holds the text, and FTS4 finds
 * the matching chunks without reading the rest.
 *
 * The FTS table is an *external content* table over [CHUNKS], so each chunk's text is stored
 * once and the index holds only tokens pointing at it. Triggers keep the two in step, which
 * means deleting one file's chunks (when it changes) touches only that file's rows instead of
 * rescanning the whole index.
 */
internal class CorpusDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    /** What is remembered about one indexed file, without its text. */
    data class FileMeta(
        val uri: String,
        val name: String,
        val mtime: Long,
        val size: Long,
        val isPdf: Boolean,
        val chunks: Int,
        val textless: Boolean,
    )

    /** Totals for the UI, read from the database rather than from memory. */
    data class Counts(
        val files: Int = 0,
        val chunks: Int = 0,
        val pdfs: Int = 0,
        val textless: Int = 0,
    )

    override fun onCreate(db: SQLiteDatabase) {
        createSchema(db)
    }

    /**
     * Every statement is `IF NOT EXISTS`, so a database left half-created by a crash heals on
     * the next open instead of failing every query from then on.
     */
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) createSchema(db)
    }

    /**
     * Everything stored here is a cache of text that can be extracted again from the folder the
     * user granted, so a schema change starts over instead of carrying a half-migrated index.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TRIGGER IF EXISTS ${CHUNKS}_ai")
        db.execSQL("DROP TRIGGER IF EXISTS ${CHUNKS}_bd")
        db.execSQL("DROP TABLE IF EXISTS $FTS")
        db.execSQL("DROP TABLE IF EXISTS $CHUNKS")
        db.execSQL("DROP TABLE IF EXISTS $FILES")
        db.execSQL("DROP TABLE IF EXISTS $SCAN")
        createSchema(db)
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $FILES (" +
                "uri TEXT NOT NULL PRIMARY KEY, " +
                "name TEXT NOT NULL, " +
                "mtime INTEGER NOT NULL, " +
                "size INTEGER NOT NULL, " +
                "is_pdf INTEGER NOT NULL, " +
                "chunks INTEGER NOT NULL, " +
                "textless INTEGER NOT NULL, " +
                "indexed_at INTEGER NOT NULL" +
                ")"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $CHUNKS (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "uri TEXT NOT NULL, " +
                "source TEXT NOT NULL, " +
                "body TEXT NOT NULL" +
                ")"
        )
        // Deleting or replacing one file's chunks goes through this index, not a table scan.
        db.execSQL("CREATE INDEX IF NOT EXISTS ${CHUNKS}_uri ON $CHUNKS (uri)")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS $FTS USING fts4(" +
                "body, content='$CHUNKS', tokenize=unicode61" +
                ")"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS ${CHUNKS}_ai AFTER INSERT ON $CHUNKS BEGIN " +
                "INSERT INTO $FTS (docid, body) VALUES (new.id, new.body); END"
        )
        // BEFORE DELETE, not AFTER: an external-content FTS table reads the content row to
        // learn which terms to remove, so the row must still exist when the index is updated.
        // The 'delete' special command needs every column passed by hand and rejects a
        // single-column table; deleting through the docid does the same job.
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS ${CHUNKS}_bd BEFORE DELETE ON $CHUNKS BEGIN " +
                "DELETE FROM $FTS WHERE docid = old.id; END"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $SCAN (" +
                "id INTEGER PRIMARY KEY CHECK (id = 1), " +
                "cursor TEXT, " +
                "swept_supported INTEGER NOT NULL DEFAULT 0" +
                ")"
        )
        db.execSQL("INSERT OR IGNORE INTO $SCAN (id, cursor) VALUES (1, NULL)")
    }

    /** Totals. Two counts over the corpus, read once per pass and once at startup. */
    fun counts(): Counts {
        val db = readableDatabase
        var files = 0
        var pdfs = 0
        var textless = 0
        var chunks = 0
        db.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(is_pdf), 0), COALESCE(SUM(textless), 0) FROM $FILES",
            null
        ).use { c ->
            if (c.moveToFirst()) {
                files = c.getInt(0)
                pdfs = c.getInt(1)
                textless = c.getInt(2)
            }
        }
        db.rawQuery("SELECT COUNT(*) FROM $CHUNKS", null).use { c ->
            if (c.moveToFirst()) chunks = c.getInt(0)
        }
        return Counts(files = files, chunks = chunks, pdfs = pdfs, textless = textless)
    }

    /** Metadata for every indexed file, keyed by URI. Text stays in the database. */
    fun fileMeta(): Map<String, FileMeta> {
        val out = HashMap<String, FileMeta>()
        readableDatabase.rawQuery(
            "SELECT uri, name, mtime, size, is_pdf, chunks, textless FROM $FILES",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out[c.getString(0)] = FileMeta(
                    uri = c.getString(0),
                    name = c.getString(1),
                    mtime = c.getLong(2),
                    size = c.getLong(3),
                    isPdf = c.getInt(4) != 0,
                    chunks = c.getInt(5),
                    textless = c.getInt(6) != 0,
                )
            }
        }
        return out
    }

    /**
     * Replaces everything known about one file: its old chunks are deleted (the trigger removes
     * their index entries) and the new ones inserted, in one transaction so a killed process
     * cannot leave a file recorded as indexed with no text behind it.
     */
    fun writeFile(
        uri: String,
        name: String,
        mtime: Long,
        size: Long,
        isPdf: Boolean,
        pieces: List<String>,
        textless: Boolean,
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(CHUNKS, "uri = ?", arrayOf(uri))
            val insert = db.compileStatement("INSERT INTO $CHUNKS (uri, source, body) VALUES (?, ?, ?)")
            pieces.forEach { body ->
                insert.bindString(1, uri)
                insert.bindString(2, name)
                insert.bindString(3, body)
                insert.executeInsert()
                insert.clearBindings()
            }
            val values = ContentValues().apply {
                put("uri", uri)
                put("name", name)
                put("mtime", mtime)
                put("size", size)
                put("is_pdf", if (isPdf) 1 else 0)
                put("chunks", pieces.size)
                put("textless", if (textless) 1 else 0)
                put("indexed_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(FILES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Chunks matching an FTS query, joined back to their text. `MATCH` does the filtering in the
     * index; callers re-rank the survivors, so [limit] bounds how many rows leave SQLite.
     */
    fun search(match: String, limit: Int): List<LocalCorpus.Chunk> {
        val out = ArrayList<LocalCorpus.Chunk>()
        readableDatabase.rawQuery(
            "SELECT c.source, c.body FROM $FTS " +
                "JOIN $CHUNKS c ON c.id = $FTS.rowid " +
                "WHERE $FTS MATCH ? LIMIT ?",
            arrayOf(match, limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(LocalCorpus.Chunk(c.getString(0), c.getString(1)))
            }
        }
        return out
    }

    /** Where the last pass stopped, as a relative path, so the next one can resume there. */
    fun cursor(): String? =
        readableDatabase.rawQuery("SELECT cursor FROM $SCAN WHERE id = 1", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }

    fun setCursor(path: String?) {
        val values = ContentValues().apply {
            if (path == null) putNull("cursor") else put("cursor", path)
        }
        writableDatabase.update(SCAN, values, "id = 1", null)
    }

    /** Supported files found during the last complete walk of the folder, or 0 if unknown. */
    fun sweptSupported(): Int =
        readableDatabase.rawQuery("SELECT swept_supported FROM $SCAN WHERE id = 1", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun recordSweep(supported: Int) {
        val values = ContentValues().apply { put("swept_supported", supported) }
        writableDatabase.update(SCAN, values, "id = 1", null)
    }

    /** Empties the corpus, keeping the folder grant. Text is re-readable from the folder. */
    fun clearAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(CHUNKS, null, null)
            db.delete(FILES, null, null)
            db.delete(SCAN, null, null)
            db.execSQL("INSERT INTO $SCAN (id, cursor, swept_supported) VALUES (1, NULL, 0)")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val NAME = "corpus.db"
        const val VERSION = 1
        const val FILES = "corpus_files"
        const val CHUNKS = "corpus_chunks"
        const val FTS = "corpus_fts"
        const val SCAN = "corpus_scan"
    }
}

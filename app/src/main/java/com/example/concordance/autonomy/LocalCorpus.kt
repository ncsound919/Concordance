package com.example.concordance.autonomy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the corpus looks like right now, for the UI and the activity log. */
data class CorpusStatus(
    val folderName: String? = null,
    val files: Int = 0,
    val chunks: Int = 0,
    val pdfs: Int = 0,
    val textlessPdfs: Int = 0,
    val failed: Int = 0,
    val unchanged: Int = 0,
    val truncated: Boolean = false,
    val running: Boolean = false,
    val lastRunAt: Long = 0,
    val note: String? = null,
) {
    val hasFolder: Boolean get() = folderName != null
}

/**
 * Keyword search over papers on the phone.
 *
 * The folder is chosen by the user through the system picker (`ACTION_OPEN_DOCUMENT_TREE`)
 * and the grant is persisted, because scoped storage hides app-external files from a plain
 * `File` walk: an earlier version walked `/sdcard/Concordance` and quietly indexed nothing.
 *
 * PDFs are read with PDFBox (the platform has `PdfRenderer`, which produces bitmaps but no
 * text). The extracted text goes straight into [CorpusDatabase], which holds it on disk and
 * indexes it with FTS4 — a folder of thousands of papers does not fit in memory, and loading it
 * there is what capped the corpus at a few dozen before.
 *
 * A pass is bounded twice: by how many documents it inspects and by how many it extracts. The
 * position reached is saved, so the next pass resumes there instead of re-walking the folder
 * from the top, and once the walk reaches the end it wraps around to catch files that changed.
 */
object LocalCorpus {

    data class Chunk(val source: String, val text: String)

    private const val TAG = "LocalCorpus"

    private const val MAX_FILE_BYTES = 30L * 1024 * 1024
    private const val MAX_PDF_PAGES = 40
    private const val MAX_DEPTH = 8

    /**
     * Documents inspected per pass. Checking a file's name, size and timestamp costs a few
     * provider round trips; extracting a PDF costs thousands of times that. Bounding the cheap
     * part keeps one pass from running for minutes on a large folder.
     */
    private const val MAX_EXAMINED = 800

    /**
     * Separator for the resume key. It sorts before any character a file name can contain, so
     * "sort the children, then recurse" order and plain lexicographic order agree — which is
     * what lets a pass resume at a saved path.
     */
    private const val SEP = '\u0001'

    private val _status = MutableStateFlow(CorpusStatus())
    val status: StateFlow<CorpusStatus> = _status.asStateFlow()

    private val indexMutex = Mutex()
    private var database: CorpusDatabase? = null
    private var loaded = false
    private var pdfBoxReady = false

    /** Opens the database and reports what it already holds. Safe to call repeatedly. */
    fun init(context: Context) {
        val app = context.applicationContext
        if (loaded) return
        loaded = true
        val db = runCatching { CorpusDatabase(app) }.getOrNull()
        database = db
        if (db == null) {
            _status.value = CorpusStatus(note = "corpus storage is unavailable")
            return
        }
        importLegacyIndex(app, db)
        val counts = runCatching { db.counts() }.getOrDefault(CorpusDatabase.Counts())
        _status.value = CorpusStatus(
            folderName = CorpusStore.folderName(app),
            files = counts.files,
            chunks = counts.chunks,
            pdfs = counts.pdfs,
            textlessPdfs = counts.textless,
        )
    }

    /**
     * Persists the folder the user picked. Returns false when the system will not keep the
     * grant, which the UI must say out loud rather than silently indexing nothing.
     */
    fun grant(context: Context, treeUri: Uri): Boolean {
        val app = context.applicationContext
        val previous = CorpusStore.folderUri(app)?.toString()
        return runCatching {
            app.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val name = DocumentFile.fromTreeUri(app, treeUri)?.name
                ?: treeUri.lastPathSegment
                ?: "chosen folder"
            CorpusStore.saveFolder(app, treeUri, name)
            init(app)
            // A different folder shares no URIs with the old one, so keeping its rows would
            // report papers that are no longer part of the corpus.
            if (previous != null && previous != treeUri.toString()) {
                database?.clearAll()
                refreshStatus(name, note = "folder changed — indexing starts over")
            } else {
                refreshStatus(name, note = null)
            }
            true
        }.getOrDefault(false)
    }

    fun forget(context: Context) {
        val app = context.applicationContext
        CorpusStore.clearFolder(app)
        database?.clearAll()
        loaded = true
        _status.value = CorpusStatus(note = "folder cleared")
    }

    /**
     * Inspects the granted folder and extracts text from files that are new or changed since
     * the last pass, resuming where that pass stopped. Suspends off the main thread; safe to
     * call from a worker and from the UI.
     */
    suspend fun index(
        context: Context,
        maxNewFiles: Int = 20,
        maxExamined: Int = MAX_EXAMINED,
    ): CorpusStatus = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        init(app)
        val db = database
            ?: return@withContext CorpusStatus(note = "corpus storage is unavailable")

        val treeUri = CorpusStore.folderUri(app)
        if (treeUri == null) {
            val s = CorpusStatus(folderName = null, note = "no corpus folder chosen")
            _status.value = s
            return@withContext s
        }

        indexMutex.withLock {
            val folderName = CorpusStore.folderName(app) ?: "chosen folder"
            _status.update { it.copy(folderName = folderName, running = true, note = null) }

            val root = DocumentFile.fromTreeUri(app, treeUri)
            if (root == null || !root.exists() || !root.canRead()) {
                return@withLock refreshStatus(
                    folderName = folderName,
                    note = "the folder grant is no longer valid — choose the folder again",
                )
            }

            val known = runCatching { db.fileMeta() }.getOrDefault(emptyMap())
            val startCursor = runCatching { db.cursor() }.getOrNull()
            var newFiles = 0
            var unchanged = 0
            var failed = 0
            var textless = 0
            var examined = 0
            var seenSupported = 0
            var stopped = false
            var lastSeen: String? = startCursor

            for (leaf in walk(root, startCursor)) {
                if (!CorpusIndex.isSupported(leaf.name)) continue

                // Counted before any work so the batch limits apply to what was looked at,
                // not only to what was successfully read.
                examined++
                seenSupported++
                lastSeen = leaf.path

                val key = leaf.file.uri.toString()
                val mtime = leaf.file.lastModified()
                val size = leaf.file.length()
                val isPdf = CorpusIndex.extensionOf(leaf.name) == "pdf"
                val cached = known[key]

                when {
                    cached != null &&
                        CorpusIndex.isUnchanged(cached.mtime, cached.size, mtime, size) -> unchanged++

                    size > MAX_FILE_BYTES -> failed++

                    else -> {
                        val text = extractText(app, leaf.file, leaf.name)
                        if (text.isNullOrBlank()) {
                            if (isPdf) textless++
                            failed++
                            // Recorded as read anyway: a scan without a text layer will not
                            // gain one by being tried again on the next pass.
                            db.writeFile(
                                key, leaf.name, mtime, size, isPdf,
                                pieces = emptyList(), textless = true,
                            )
                        } else {
                            db.writeFile(
                                key, leaf.name, mtime, size, isPdf,
                                pieces = CorpusIndex.chunk(leaf.name, text).map { it.text },
                                textless = false,
                            )
                            newFiles++
                        }
                    }
                }

                if (newFiles >= maxNewFiles || examined >= maxExamined) {
                    stopped = true
                    break
                }
            }

            if (stopped) {
                // Resume after the document this pass last looked at.
                db.setCursor(lastSeen)
            } else {
                // The walk reached the end, so the next pass starts from the top and picks up
                // files that changed. A sweep is only a full one if it began at the top.
                db.setCursor(null)
                if (startCursor == null) db.recordSweep(seenSupported)
            }

            val counts = runCatching { db.counts() }.getOrDefault(CorpusDatabase.Counts())
            val swept = runCatching { db.sweptSupported() }.getOrDefault(0)
            refreshStatus(
                folderName = folderName,
                unchanged = unchanged,
                failed = failed,
                truncated = stopped,
                note = buildNote(stopped = stopped, counts = counts, swept = swept, textless = textless),
            )
        }
    }

    /**
     * Chunks whose text contains every term, best first. Filtering happens in SQLite's index;
     * the survivors are re-ranked with [CorpusIndex.score], which weighs term hits the same way
     * the in-memory version did.
     */
    suspend fun search(query: String, topK: Int = 5): List<Chunk> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext emptyList()
        val terms = query.lowercase().split(Regex("\\s+"))
            .filter { it.length > 2 && it.any(Char::isLetterOrDigit) }
            .distinct()
        if (terms.isEmpty()) return@withContext emptyList()

        // Each term is quoted so FTS operators in a user's query cannot change its meaning,
        // and the terms are separated by a space: FTS4's standard query syntax has no AND
        // operator ("egfr AND lung" searches for the word "and"), a space means AND.
        val match = terms.joinToString(" ") { "\"" + it.replace("\"", "\"\"") + "\"" }
        val hits = runCatching { db.search(match, limit = 400) }
            .onFailure { Log.w(TAG, "corpus search failed", it) }
            .getOrDefault(emptyList())
        hits
            .map { it to CorpusIndex.score(it.text, terms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    fun chunkCount(): Int = database?.let { runCatching { it.counts().chunks }.getOrNull() } ?: 0

    fun fileCount(): Int = database?.let { runCatching { it.counts().files }.getOrNull() } ?: 0

    /** Test seam: drops the open database so a fresh application gets a fresh one. */
    internal fun resetForTests() {
        runCatching { database?.close() }
        database = null
        loaded = false
        pdfBoxReady = false
        _status.value = CorpusStatus()
    }

    private fun refreshStatus(
        folderName: String?,
        unchanged: Int = 0,
        failed: Int = 0,
        truncated: Boolean = false,
        note: String? = null,
    ): CorpusStatus {
        val counts = database?.let { runCatching { it.counts() }.getOrNull() } ?: CorpusDatabase.Counts()
        val s = CorpusStatus(
            folderName = folderName,
            files = counts.files,
            chunks = counts.chunks,
            pdfs = counts.pdfs,
            textlessPdfs = counts.textless,
            failed = failed,
            unchanged = unchanged,
            truncated = truncated,
            running = false,
            lastRunAt = System.currentTimeMillis(),
            note = note,
        )
        _status.value = s
        return s
    }

    /**
     * Says what is left to read. "0 new files" on its own reads as success even when a folder
     * of thousands has barely been touched, so the count comes from the last complete walk of
     * the folder rather than from this pass alone.
     */
    private fun buildNote(
        stopped: Boolean,
        counts: CorpusDatabase.Counts,
        swept: Int,
        textless: Int,
    ): String? {
        val pending = if (swept > 0) (swept - counts.files).coerceAtLeast(0) else 0
        return when {
            stopped && pending > 0 -> "more to index — $pending files left in this sweep"
            stopped -> "more to index — slow mode continues on the next step"
            pending > 0 -> "$pending files left out of the index"
            textless > 0 -> "$textless PDFs had no text layer (scans need OCR)"
            counts.files == 0 -> "nothing indexable in that folder yet"
            else -> null
        }
    }

    /**
     * Imports the index the previous version kept as JSON. Its 4 MB memory cap is gone, but the
     * papers it did read are still worth keeping instead of extracting them all over again.
     */
    private fun importLegacyIndex(app: Context, db: CorpusDatabase) {
        val legacy = runCatching { CorpusStore.readLegacyIndex(app) }.getOrDefault(emptyList())
        if (legacy.isEmpty()) {
            CorpusStore.dropLegacyIndex(app)
            return
        }
        runCatching {
            legacy.forEach { file ->
                db.writeFile(
                    uri = file.uri,
                    name = file.name,
                    mtime = file.mtime,
                    size = file.size,
                    isPdf = file.isPdf,
                    pieces = CorpusIndex.chunk(file.name, file.text).map { it.text },
                    textless = false,
                )
            }
        }.onSuccess {
            CorpusStore.dropLegacyIndex(app)
            Log.i(TAG, "imported ${legacy.size} files from the previous index")
        }.onFailure {
            Log.w(TAG, "could not import the previous index", it)
        }
    }

    private data class Leaf(val file: DocumentFile, val name: String, val path: String)

    /**
     * Walks the granted tree depth-first in a stable order, skipping everything at or before
     * [after]. The walk is a sequence, so a pass that stops early never enumerates the rest of
     * a folder that may hold thousands of files.
     */
    private fun walk(root: DocumentFile, after: String?): Sequence<Leaf> = sequence {
        class Frame(val dir: DocumentFile, val prefix: String) {
            var children: List<DocumentFile>? = null
            var index = 0
        }

        val stack = ArrayDeque<Frame>()
        stack.addLast(Frame(root, ""))
        while (stack.isNotEmpty()) {
            val frame = stack.last()
            val children = frame.children ?: runCatching {
                frame.dir.listFiles().sortedBy { it.name ?: "" }
            }.getOrDefault(emptyList()).also { frame.children = it }

            if (frame.index >= children.size) {
                stack.removeLast()
                continue
            }
            val child = children[frame.index++]
            val name = child.name ?: continue
            val path = if (frame.prefix.isEmpty()) name else frame.prefix + SEP + name
            if (child.isDirectory) {
                if (stack.size < MAX_DEPTH) stack.addLast(Frame(child, path))
                continue
            }
            if (after != null && path <= after) continue
            yield(Leaf(child, name, path))
        }
    }

    private fun extractText(app: Context, doc: DocumentFile, name: String): String? {
        val isPdf = CorpusIndex.extensionOf(name) == "pdf"
        return runCatching {
            if (isPdf) extractPdf(app, doc) else {
                app.contentResolver.openInputStream(doc.uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
            }
        }.getOrNull()
    }

    private fun extractPdf(app: Context, doc: DocumentFile): String? =
        runCatching {
            app.contentResolver.openInputStream(doc.uri)?.use { input ->
                extractPdfText(input, app)
            }
        }.getOrNull()

    /**
     * Reads the text layer of a PDF, up to [maxPages]. Internal so a JVM test can feed it a
     * document without a device, a SAF grant or a filesystem.
     */
    internal fun extractPdfText(
        input: java.io.InputStream,
        context: Context? = null,
        maxPages: Int = MAX_PDF_PAGES,
    ): String? {
        if (!pdfBoxReady && context != null) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxReady = true
        }
        return runCatching {
            PDDocument.load(input).use { pdf ->
                if (pdf.numberOfPages == 0) return@use null
                val stripper = PDFTextStripper()
                stripper.startPage = 1
                stripper.endPage = minOf(pdf.numberOfPages, maxPages)
                stripper.getText(pdf).trim().ifEmpty { null }
            }
        }.getOrNull()
    }
}

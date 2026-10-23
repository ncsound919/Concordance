package com.example.concordance.autonomy

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.io.File

/** One file read by the previous, in-memory corpus. Kept only for the one-time import. */
data class IndexedFile(
    val uri: String,
    val name: String,
    val mtime: Long,
    val size: Long,
    val text: String,
    val isPdf: Boolean,
)

/**
 * The folder grant, remembered across restarts, plus the index the previous version wrote.
 *
 * The extracted text now lives in [CorpusDatabase]: it is measured in megabytes and belongs on
 * disk, not in SharedPreferences and not in memory.
 */
internal object CorpusStore {
    private const val PREFS = "concordance_corpus"
    private const val KEY_FOLDER_URI = "folder_uri"
    private const val KEY_FOLDER_NAME = "folder_name"
    private const val LEGACY_INDEX_FILE = "corpus_index.json"

    fun folderUri(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_URI, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun folderName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_NAME, null)
            ?.takeIf { it.isNotBlank() }

    fun saveFolder(context: Context, uri: Uri, name: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FOLDER_URI, uri.toString())
            .putString(KEY_FOLDER_NAME, name)
            .apply()
    }

    /** Forgets the folder and the text cached for it. */
    fun clearFolder(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_FOLDER_URI)
            .remove(KEY_FOLDER_NAME)
            .apply()
        dropLegacyIndex(context)
    }

    private fun legacyIndexFile(context: Context): File =
        File(context.filesDir, LEGACY_INDEX_FILE)

    /**
     * Reads the JSON index the in-memory version wrote, if it is still there. Papers it had
     * already extracted are imported once so they are not read and chunked again.
     */
    fun readLegacyIndex(context: Context): List<IndexedFile> {
        val file = legacyIndexFile(context)
        if (!file.isFile) return emptyList()
        val array = JSONArray(file.readText())
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val text = o.optString("text")
            if (text.isBlank()) return@mapNotNull null
            IndexedFile(
                uri = o.optString("uri"),
                name = o.optString("name"),
                mtime = o.optLong("mtime"),
                size = o.optLong("size"),
                text = text,
                isPdf = o.optBoolean("isPdf"),
            )
        }
    }

    fun dropLegacyIndex(context: Context) {
        runCatching { legacyIndexFile(context).delete() }
    }
}

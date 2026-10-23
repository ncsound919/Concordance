package com.example.concordance.autonomy

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Simple JSONL outbox for deferred cloud calls.
 * When offline, cloud calls are appended here and drained by SyncWorker on reconnect.
 */
object OutboxRepository {
    private const val FILE_NAME = "autonomy_outbox.jsonl"

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun append(context: Context, kind: String, payload: String) {
        val f = file(context)
        val entry = JSONObject().apply {
            put("kind", kind)
            put("payload", payload)
            put("created_at", System.currentTimeMillis())
            put("attempts", 0)
        }
        f.appendText(entry.toString() + "\n")
        updateCount(context)
    }

    fun readAll(context: Context): List<String> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return f.readLines().filter { it.isNotBlank() }
    }

    fun clear(context: Context) {
        file(context).delete()
        updateCount(context)
    }

    fun removeEntry(context: Context, line: String) {
        val f = file(context)
        if (!f.exists()) return
        val remaining = f.readLines().filter { it.trim() != line.trim() && it.isNotBlank() }
        f.writeText(remaining.joinToString("\n") + if (remaining.isNotEmpty()) "\n" else "")
        updateCount(context)
    }

    fun count(context: Context): Int = readAll(context).size

    private fun updateCount(context: Context) {
        AutonomyModeManager.init(context)
        AutonomyModeManager.setOutboxCount(count(context))
    }
}

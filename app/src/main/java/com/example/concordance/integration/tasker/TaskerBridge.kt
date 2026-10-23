package com.example.concordance.integration.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Bridge for Tasker (and other automation apps) to trigger Concordance actions
 * without holding a UI reference. Tasker can send an ordered broadcast with one
 * of the ACTION_* constants; the receiver records it into a SharedPreferences
 * queue that the app drains on the next foreground pass (or immediately if the
 * app is already running).
 *
 * No secrets or credentials are ever exposed through this bridge - it only
 * triggers already-defined, human-gated workflows (learning rounds stay in
 * proposal state; promotion still requires explicit approval in the UI).
 */
object TaskerBridge {

    const val ACTION_RUN_LEARNING_ROUND = "concordance.action.RUN_LEARNING_ROUND"
    const val ACTION_CHECK_AUTO_TRIGGER = "concordance.action.CHECK_AUTO_TRIGGER"
    const val ACTION_RE_ADJUDICATE = "concordance.action.RE_ADJUDICATE"
    const val ACTION_SYNC_CAPSULES = "concordance.action.SYNC_CAPSULES"

    const val EXTRA_REVISION_ID = "revisionId"

    private const val PREFS = "tasker_bridge"
    private const val KEY_QUEUE = "pending_actions"
    private const val SEP = "\u001f"

    /**
     * The receiver is exported so automation apps can call it, which also means any installed app
     * can. The queue is therefore a bounded, deduplicated buffer rather than an unbounded prefs
     * string: a hostile caller can waste at most [MAX_QUEUE] slots, and repeat taps collapse.
     */
    const val MAX_QUEUE = 25
    private const val MAX_EXTRA_LENGTH = 120L
    private const val DEDUPE_WINDOW_MS = 2_000L
    private const val KEY_LAST_ENTRY = "last_entry"
    private const val KEY_LAST_AT = "last_at"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Record an action for later execution. Safe to call from any thread. */
    fun enqueue(context: Context, action: String, extra: String? = null) {
        val entry = if (extra.isNullOrBlank()) action else "$action=${extra.take(MAX_EXTRA_LENGTH.toInt())}"
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val lastEntry = p.getString(KEY_LAST_ENTRY, null)
        val lastAt = p.getLong(KEY_LAST_AT, 0L)
        if (entry == lastEntry && now - lastAt < DEDUPE_WINDOW_MS) return

        val current = p.getString(KEY_QUEUE, "").orEmpty()
        val entries = if (current.isBlank()) mutableListOf() else current.split(SEP).toMutableList()
        entries.add(entry)
        while (entries.size > MAX_QUEUE) entries.removeAt(0)
        p.edit {
            putString(KEY_QUEUE, entries.joinToString(SEP))
            putString(KEY_LAST_ENTRY, entry)
            putLong(KEY_LAST_AT, now)
        }
    }

    /** Drain and return all queued actions, clearing the queue. */
    fun drain(context: Context): List<String> {
        val p = prefs(context)
        val raw = p.getString(KEY_QUEUE, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        val entries = raw.split(SEP).filter { it.isNotBlank() }
        p.edit { remove(KEY_QUEUE) }
        return entries
    }

    /** Size of the pending queue without draining. */
    fun pendingCount(context: Context): Int {
        val raw = prefs(context).getString(KEY_QUEUE, "").orEmpty()
        return if (raw.isBlank()) 0 else raw.split(SEP).count { it.isNotBlank() }
    }

    /**
     * Entry point used by the manifest-registered receiver. Records the action
     * (and any revisionId extra) into the queue; the app drains it when ready.
     */
    fun handleIntent(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in setOf(ACTION_RUN_LEARNING_ROUND, ACTION_CHECK_AUTO_TRIGGER,
                ACTION_RE_ADJUDICATE, ACTION_SYNC_CAPSULES)) return
        val extra = intent.getStringExtra(EXTRA_REVISION_ID)
        enqueue(context, action, extra)
    }
}

/**
 * Manifest-registered receiver. Tasker sends an ordered broadcast with one of
 * the TaskerBridge.ACTION_* actions; we record it for the app to drain.
 */
class TaskerTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        TaskerBridge.handleIntent(context, intent)
    }
}

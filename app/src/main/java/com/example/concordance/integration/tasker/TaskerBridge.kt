package com.example.concordance.integration.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Bridge for automation to trigger Concordance actions without holding a UI
 * reference. The manifest receiver is NOT exported (an exported receiver let
 * any app queue these actions), so triggers now come from in-app callers that
 * [enqueue] directly; the queue is drained on the next foreground pass (or
 * immediately if the app is already running).
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

    /** Cap on queued actions so a flood cannot grow the queue without bound. */
    private const val MAX_QUEUE = 32
    /** Cap on a single extra (e.g. revisionId) so a giant payload cannot be persisted. */
    private const val MAX_EXTRA_LENGTH = 128

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Record an action for later execution. Safe to call from any thread.
     *
     * The extra is length-capped and stripped of the queue separator so a value
     * cannot inject a second fake entry, and the queue itself is capped so a
     * flood of triggers cannot grow storage without bound.
     */
    fun enqueue(context: Context, action: String, extra: String? = null) {
        val cleanExtra = extra
            ?.take(MAX_EXTRA_LENGTH)
            ?.replace(SEP, " ")
            ?.trim()
        val entry = if (cleanExtra.isNullOrEmpty()) action else "$action=$cleanExtra"
        val p = prefs(context)
        val current = p.getString(KEY_QUEUE, "").orEmpty()
        val entries = if (current.isBlank()) mutableListOf()
        else current.split(SEP).filter { it.isNotBlank() }.toMutableList()
        if (entries.size >= MAX_QUEUE) return
        entries.add(entry)
        p.edit { putString(KEY_QUEUE, entries.joinToString(SEP)) }
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

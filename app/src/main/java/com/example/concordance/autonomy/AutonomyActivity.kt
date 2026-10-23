package com.example.concordance.autonomy

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** How a unit of automation work ended (or that it is still running). */
enum class ActivityLevel(val label: String) {
    RUNNING("running"),
    OK("done"),
    SKIPPED("skipped"),
    FAILED("failed")
}

/**
 * One thing the automation did, with when it started and how long it took.
 *
 * [source] is the worker that acted ("slow", "high-power", "sync"), so a log row can be
 * attributed without guessing from the message.
 */
data class ActivityEntry(
    val id: Long,
    val at: Long,
    val source: String,
    val message: String,
    val level: ActivityLevel,
    val durationMs: Long? = null
)

/**
 * A small, persisted log of what the background automation actually did.
 *
 * The autonomy state only keeps counters (step, last run); on a phone that is not
 * enough to answer "is it working, and on what?". Entries live in a ring buffer so the
 * Agent screen can show the last few steps, and they survive a restart so an interrupted
 * run is visible instead of silently vanishing.
 */
object AutonomyActivity {
    const val SOURCE_SLOW = "slow"
    const val SOURCE_HIGH_POWER = "high-power"
    const val SOURCE_SYNC = "sync"
    const val SOURCE_MANUAL = "manual"

    private const val PREFS = "concordance_autonomy_activity"
    private const val KEY_ENTRIES = "entries_json"
    private const val MAX_ENTRIES = 40

    private val _entries = MutableStateFlow<List<ActivityEntry>>(emptyList())
    val entries: StateFlow<List<ActivityEntry>> = _entries.asStateFlow()

    private var prefs: SharedPreferences? = null
    private val idCounter = AtomicInteger(0)

    /** Test seam: a fixed clock keeps durations and ordering deterministic. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        load()
    }

    /** Kept for callers that only need the store: no-op when already initialised. */
    fun ensureInitialised(context: Context) = init(context)

    /** Re-reads the persisted log; also how a fresh process picks up interrupted work. */
    internal fun reload() = load()

    private fun load() {
        val raw = prefs?.getString(KEY_ENTRIES, null) ?: return
        val parsed = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                ActivityEntry(
                    id = o.optLong("id"),
                    at = o.optLong("at"),
                    source = o.optString("source"),
                    message = o.optString("message"),
                    level = runCatching {
                        ActivityLevel.valueOf(o.optString("level"))
                    }.getOrDefault(ActivityLevel.OK),
                    durationMs = if (o.has("durationMs")) o.optLong("durationMs") else null
                )
            }
        }.getOrDefault(emptyList())

        // Nothing from a previous process can still be running: mark it so the UI does
        // not claim a job is live after a crash or a force-stop.
        val now = clock()
        _entries.value = parsed.map { entry ->
            if (entry.level == ActivityLevel.RUNNING) {
                entry.copy(
                    level = ActivityLevel.FAILED,
                    message = "${entry.message} — interrupted before it finished",
                    durationMs = (now - entry.at).coerceAtLeast(0L)
                )
            } else {
                entry
            }
        }
        persist()
    }

    private fun persist() {
        val array = JSONArray()
        _entries.value.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("at", entry.at)
                    put("source", entry.source)
                    put("message", entry.message)
                    put("level", entry.level.name)
                    entry.durationMs?.let { put("durationMs", it) }
                }
            )
        }
        prefs?.edit()?.putString(KEY_ENTRIES, array.toString())?.apply()
    }

    private fun nextId(): Long {
        val counter = idCounter.incrementAndGet()
        return clock() * 1000L + counter
    }

    /** Records work starting. Pair with [finish] so the row gets an outcome and duration. */
    fun start(source: String, message: String): Long {
        val entry = ActivityEntry(
            id = nextId(),
            at = clock(),
            source = source,
            message = message,
            level = ActivityLevel.RUNNING
        )
        append(entry)
        return entry.id
    }

    /** Completes a [start]ed entry. Unknown ids are ignored rather than throwing. */
    fun finish(id: Long, level: ActivityLevel, message: String? = null) {
        val now = clock()
        update { list ->
            list.map { entry ->
                if (entry.id != id) {
                    entry
                } else {
                    entry.copy(
                        level = level,
                        message = message?.takeIf { it.isNotBlank() } ?: entry.message,
                        durationMs = (now - entry.at).coerceAtLeast(0L)
                    )
                }
            }
        }
    }

    /** One-shot entry for work that has no meaningful duration (skips, refusals). */
    fun record(source: String, message: String, level: ActivityLevel) {
        append(
            ActivityEntry(
                id = nextId(),
                at = clock(),
                source = source,
                message = message,
                level = level
            )
        )
    }

    fun clear() {
        _entries.value = emptyList()
        persist()
    }

    /** The newest entry that is still running, if there is one. */
    fun activeEntry(list: List<ActivityEntry> = _entries.value): ActivityEntry? =
        list.firstOrNull { it.level == ActivityLevel.RUNNING }

    private fun append(entry: ActivityEntry) {
        update { list -> (listOf(entry) + list).take(MAX_ENTRIES) }
    }

    private fun update(transform: (List<ActivityEntry>) -> List<ActivityEntry>) {
        _entries.value = transform(_entries.value).take(MAX_ENTRIES)
        persist()
    }

    /** "12s", "4m 05s", "1h 02m" — compact enough for one line on a phone. */
    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        return when {
            totalSeconds < 60 -> "${totalSeconds}s"
            totalSeconds < 3600 -> {
                val m = totalSeconds / 60
                val s = totalSeconds % 60
                "${m}m ${s.toString().padStart(2, '0')}s"
            }
            else -> {
                val h = totalSeconds / 3600
                val m = (totalSeconds % 3600) / 60
                "${h}h ${m.toString().padStart(2, '0')}m"
            }
        }
    }

    /** "just now", "3m ago", "2h ago" — for rows that already finished. */
    fun formatAge(now: Long, at: Long): String {
        val seconds = ((now - at) / 1000).coerceAtLeast(0)
        return when {
            seconds < 5 -> "just now"
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86_400 -> "${seconds / 3600}h ago"
            else -> "${seconds / 86_400}d ago"
        }
    }
}

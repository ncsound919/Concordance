package com.example.concordance.autonomy

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class AutonomyMode(val label: String) {
    HIGH_POWER("High power"),
    SLOW("Slow (background)"),
    OFFLINE("Offline"),
    PAUSED("Paused")
}

data class AutonomyState(
    val mode: AutonomyMode = AutonomyMode.PAUSED,
    val auto: Boolean = true,
    val currentGoal: String? = null,
    val currentStep: Int = 0,
    val lastRunAt: Long = 0L,
    val queueSize: Int = 0,
    val outboxCount: Int = 0,
    val lastReportSeen: String? = null,
    val batteryOk: Boolean = true,
    val online: Boolean = true
)

object AutonomyModeManager {
    private const val PREFS = "concordance_autonomy"
    private const val KEY_STATE = "state_json"

    private val _state = MutableStateFlow(AutonomyState())
    val state: StateFlow<AutonomyState> = _state.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        load()
    }

    fun load() {
        val raw = prefs?.getString(KEY_STATE, null) ?: return
        try {
            val o = JSONObject(raw)
            _state.value = AutonomyState(
                mode = AutonomyMode.valueOf(o.optString("mode", "PAUSED")),
                auto = o.optBoolean("auto", true),
                currentGoal = o.optString("currentGoal").ifBlank { null },
                currentStep = o.optInt("currentStep", 0),
                lastRunAt = o.optLong("lastRunAt", 0L),
                queueSize = o.optInt("queueSize", 0),
                outboxCount = o.optInt("outboxCount", 0),
                lastReportSeen = o.optString("lastReportSeen").ifBlank { null },
                batteryOk = o.optBoolean("batteryOk", true),
                online = o.optBoolean("online", true)
            )
        } catch (_: Exception) { /* optional persisted field: defaults apply when it is absent */ }
    }

    private fun persist() {
        val s = _state.value
        val o = JSONObject().apply {
            put("mode", s.mode.name)
            put("auto", s.auto)
            put("currentGoal", s.currentGoal ?: "")
            put("currentStep", s.currentStep)
            put("lastRunAt", s.lastRunAt)
            put("queueSize", s.queueSize)
            put("outboxCount", s.outboxCount)
            put("lastReportSeen", s.lastReportSeen ?: "")
            put("batteryOk", s.batteryOk)
            put("online", s.online)
        }
        prefs?.edit()?.putString(KEY_STATE, o.toString())?.apply()
    }

    fun update(transform: (AutonomyState) -> AutonomyState) {
        _state.value = transform(_state.value)
        persist()
    }

    fun go() {
        update { it.copy(mode = if (it.online) AutonomyMode.HIGH_POWER else AutonomyMode.SLOW) }
    }

    fun pause() {
        update { it.copy(mode = AutonomyMode.PAUSED) }
    }

    fun stop() {
        update { it.copy(mode = AutonomyMode.PAUSED, currentGoal = null, currentStep = 0, queueSize = 0) }
    }

    fun setSlow() {
        update { it.copy(mode = AutonomyMode.SLOW) }
    }

    fun setOffline(offline: Boolean) {
        update { it.copy(online = !offline, mode = if (offline) AutonomyMode.OFFLINE else it.mode) }
    }

    fun setBatteryOk(ok: Boolean) {
        update { it.copy(batteryOk = ok, mode = if (!ok) AutonomyMode.PAUSED else it.mode) }
    }

    fun setCurrentGoal(goal: String?) {
        update { it.copy(currentGoal = goal, currentStep = 0) }
    }

    fun tickStep() {
        update { it.copy(currentStep = it.currentStep + 1, lastRunAt = System.currentTimeMillis()) }
    }

    fun setLastReportSeen(ts: String?) {
        update { it.copy(lastReportSeen = ts) }
    }

    fun setOutboxCount(n: Int) {
        update { it.copy(outboxCount = n) }
    }
}

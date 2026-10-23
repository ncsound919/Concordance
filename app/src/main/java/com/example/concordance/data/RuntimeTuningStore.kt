package com.example.concordance.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persisted (non-secret) runtime tuning for the integration layer: circuit-breaker
 * thresholds and the response-cache TTL. Plain SharedPreferences is sufficient
 * because these are configuration values, not credentials.
 */
data class RuntimeTuning(
    val failureThreshold: Int = 3,
    val cooldownSeconds: Int = 60,
    val cacheTtlMinutes: Int = 10,
    // Continual (Phase 3) settings
    val autoRoundThreshold: Int = 5,
    val lastAutoRoundChallengeCount: Int = 0,
    val stabilityThreshold: Double = 0.25
)

class RuntimeTuningStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("runtime_tuning", Context.MODE_PRIVATE)

    fun load(): RuntimeTuning = RuntimeTuning(
        failureThreshold = prefs.getInt("cb_threshold", 3).coerceAtLeast(1),
        cooldownSeconds = prefs.getInt("cb_cooldown_sec", 60).coerceAtLeast(5),
        cacheTtlMinutes = prefs.getInt("cache_ttl_min", 10).coerceAtLeast(1),
        autoRoundThreshold = prefs.getInt("auto_round_threshold", 5).coerceAtLeast(1),
        lastAutoRoundChallengeCount = prefs.getInt("last_auto_round_challenges", 0).coerceAtLeast(0),
        stabilityThreshold = prefs.getFloat("stability_threshold", 0.25f).toDouble().coerceIn(0.0, 1.0)
    )

    fun save(tuning: RuntimeTuning) {
        prefs.edit {
            putInt("cb_threshold", tuning.failureThreshold)
            putInt("cb_cooldown_sec", tuning.cooldownSeconds)
            putInt("cache_ttl_min", tuning.cacheTtlMinutes)
            putInt("auto_round_threshold", tuning.autoRoundThreshold)
            putInt("last_auto_round_challenges", tuning.lastAutoRoundChallengeCount)
            putFloat("stability_threshold", tuning.stabilityThreshold.toFloat())
        }
    }
}
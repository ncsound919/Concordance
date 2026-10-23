package com.example.concordance.autonomy

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager

/**
 * Battery and power-save gating for autonomous work.
 * Pauses everything below the threshold or in power-save mode.
 */
object BatteryGate {
    private const val DEFAULT_THRESHOLD = 20

    fun batteryPercent(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale < 0) return 100
        return (level * 100 / scale)
    }

    fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun isPowerSaveMode(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isPowerSaveMode
    }

    fun isOk(context: Context, threshold: Int = DEFAULT_THRESHOLD): Boolean {
        val pct = batteryPercent(context)
        if (pct < threshold) return false
        if (isPowerSaveMode(context)) return false
        return true
    }

    fun updateAutonomy(context: Context, threshold: Int = DEFAULT_THRESHOLD) {
        AutonomyModeManager.init(context)
        AutonomyModeManager.setBatteryOk(isOk(context, threshold))
    }
}

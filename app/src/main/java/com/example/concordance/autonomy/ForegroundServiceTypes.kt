package com.example.concordance.autonomy

import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build

/**
 * Foreground-service type helpers.
 *
 * Android 14 (API 34) refuses a foreground service whose type is "none" when the app
 * targets API 34 or newer: `startForeground()` throws
 * `InvalidForegroundServiceTypeException: Starting FGS with type none ... has been
 * prohibited`. The 2-argument `Service.startForeground(id, notification)` overload always
 * passes the type "none", so every foreground service in this app must call the
 * 3-argument overload with an explicit type on API 29+.
 *
 * The types used here must match the `android:foregroundServiceType` declared for the
 * service in AndroidManifest.xml, otherwise the platform throws a SecurityException
 * ("does not match the one declared in the manifest").
 */
object ForegroundServiceTypes {

    /** Declared as `specialUse` in the manifest for the on-device LLM server. */
    const val SPECIAL_USE: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

    /** Declared as `dataSync` in the manifest for WorkManager's foreground service. */
    const val DATA_SYNC: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

    /**
     * Starts [service] in the foreground with an explicit foreground service type.
     *
     * On API 29+ the type is passed straight through. Below API 29 the 3-argument
     * overload does not exist and types did not exist at all, so the legacy 2-argument
     * call is used.
     */
    fun startForeground(service: Service, id: Int, notification: android.app.Notification, type: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(id, notification, type)
        } else {
            @Suppress("DEPRECATION")
            service.startForeground(id, notification)
        }
    }
}

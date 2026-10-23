package com.example

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.example.concordance.autonomy.ForegroundServiceTypes
import com.example.concordance.autonomy.SlowResearchWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the "Go" button crash.
 *
 * Symptom: pressing Go on the Agent screen's Autonomy card killed the app.
 * Root cause: `SlowResearchWorker` called `setForeground()` with a `ForegroundInfo` that
 * carried no foreground service type. WorkManager passes that type to
 * `Service.startForeground(id, notification, type)`, and on Android 14+ (targetSdk 36)
 * the platform throws
 * `android.app.InvalidForegroundServiceTypeException: Starting FGS with type none ... has
 * been prohibited` from inside `SystemForegroundService`, taking the whole process down.
 *
 * The fix has two halves, and both are asserted here:
 *  1. the code must pass a real foreground service type, and
 *  2. the manifest must declare that same type for the service that will be started.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForegroundServiceTypeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun serviceInfo(className: String): ServiceInfo {
        val pm = context.packageManager
        return pm.getServiceInfo(ComponentName(context, className), PackageManager.GET_META_DATA)
    }

    @Test
    fun `work manager foreground service declares the dataSync type`() {
        val info = serviceInfo("androidx.work.impl.foreground.SystemForegroundService")
        assertEquals(
            "WorkManager's foreground service must declare a foreground service type, " +
                "otherwise Android 14+ refuses to start it.",
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            info.foregroundServiceType
        )
    }

    @Test
    fun `app foreground services declare the specialUse type`() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            serviceInfo("com.example.concordance.autonomy.HighPowerResearchService").foregroundServiceType
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            serviceInfo("com.example.concordance.local.LocalLlmService").foregroundServiceType
        )
    }

    @Test
    fun `foreground service permissions are requested`() {
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toSet()
        assertTrue(
            "FOREGROUND_SERVICE_DATA_SYNC is required for a dataSync foreground service",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC" in requested
        )
        assertTrue(
            "FOREGROUND_SERVICE_SPECIAL_USE is required for a specialUse foreground service",
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE" in requested
        )
    }

    @Test
    fun `slow research worker foreground info carries a foreground service type`() {
        val info = SlowResearchWorker.createForegroundInfo(context, "Slow mode running")
        assertNotEquals(
            "A ForegroundInfo with type NONE makes Android 14+ throw " +
                "InvalidForegroundServiceTypeException and kill the app.",
            ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE,
            info.foregroundServiceType
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            info.foregroundServiceType
        )
    }

    @Test
    fun `worker foreground type matches the type declared in the manifest`() {
        val declared = serviceInfo("androidx.work.impl.foreground.SystemForegroundService")
            .foregroundServiceType
        val used = SlowResearchWorker.createForegroundInfo(context, "Slow mode running")
            .foregroundServiceType
        assertEquals(
            "The type passed to startForeground() must be declared in the manifest, " +
                "otherwise the platform throws a SecurityException.",
            declared,
            used
        )
    }

    @Test
    fun `foreground service type constants are the platform values`() {
        assertEquals(1, ForegroundServiceTypes.DATA_SYNC)
        assertEquals(0x40000000, ForegroundServiceTypes.SPECIAL_USE)
    }
}

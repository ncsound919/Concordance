package com.example

import android.app.Application
import com.example.concordance.telemetry.CrashReporter
import com.example.concordance.telemetry.EcosystemTelemetry

/**
 * Process entry point. Its only job is to install [CrashReporter] before any
 * screen or worker can fail.
 */
class ConcordanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        // Empty in a default build; see app/build.gradle.kts. Configured here so
        // every agent call can record without threading a config object through.
        EcosystemTelemetry.configure(BuildConfig.SIEVE_TELEMETRY_URL)
    }
}

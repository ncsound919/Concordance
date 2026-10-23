package com.example

import android.app.Application
import com.example.concordance.telemetry.CrashReporter

/**
 * Process entry point. Its only job is to install [CrashReporter] before any
 * screen or worker can fail.
 */
class ConcordanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}

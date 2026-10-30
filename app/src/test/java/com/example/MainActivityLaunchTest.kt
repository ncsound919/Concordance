package com.example

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityLaunchTest {
    @Test
    fun launch() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        androidx.work.WorkManager.initialize(ctx, androidx.work.Configuration.Builder().build())
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity
    }
}

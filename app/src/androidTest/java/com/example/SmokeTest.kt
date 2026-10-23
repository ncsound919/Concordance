package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeTest {
    @Test
    fun appContextLoads() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(appContext.packageName == "com.aistudio.concordance.rkzv")
    }
}
package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.concordance.local.LocalModels
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalModelsTest {
    @Test
    fun describe_gb() {
        val f = object : File("fake.bin") {
            override fun length(): Long = 2L * 1024 * 1024 * 1024 + 100
        }
        assertTrue(LocalModels.describe(f).endsWith("GB"))
    }

    @Test
    fun describe_mb() {
        val f = File.createTempFile("mod", ".bin")
        f.writeBytes(ByteArray(5 * 1024 * 1024))
        assertTrue(LocalModels.describe(f).endsWith("MB"))
        f.delete()
    }

    @Test
    fun kokoroReady_falseWhenMissing() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(LocalModels.kokoroReady(ctx))
    }

    @Test
    fun installedModels_empty() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // returns a list (possibly empty) without crashing
        val list = LocalModels.installedModels(ctx)
        assertTrue(list is List<File>)
        assertEquals(null, LocalModels.defaultModel(ctx)) // likely null in test env
    }

    @Test
    fun runtimeReady() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // just call
        LocalModels.runtimeReady(ctx)
        LocalModels.runtimeBinary(ctx)
        LocalModels.modelsDir(ctx)
        LocalModels.kokoroDir(ctx)
        assertEquals(8080, LocalModels.LLM_PORT)
    }
}

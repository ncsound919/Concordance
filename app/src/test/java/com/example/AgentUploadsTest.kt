package com.example

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.concordance.integration.agent.AgentUploads
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentUploadsTest {
    @Test
    fun constants() {
        assertEquals(25 * 1024 * 1024, AgentUploads.MAX_BYTES)
        assertTrue(AgentUploads.ACCEPTED_TYPES.contains("application/pdf"))
    }

    @Test
    fun read_emptyFileRejected() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val f = File(ctx.cacheDir, "empty.png")
        f.writeBytes(ByteArray(0))
        val res = AgentUploads.read(ctx, Uri.fromFile(f))
        assertTrue(res is AgentUploads.Result.Rejected)
    }

    @Test
    fun read_validFileOk() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val f = File(ctx.cacheDir, "fig.png")
        f.writeBytes(byteArrayOf(1, 2, 3))
        val res = AgentUploads.read(ctx, Uri.fromFile(f))
        assertTrue(res is AgentUploads.Result.Ok)
        val up = (res as AgentUploads.Result.Ok).upload
        assertTrue(up.fileName.isNotBlank())
    }

    @Test
    fun read_tooLargeRejected() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val f = File(ctx.cacheDir, "big.png")
        // 26 MB
        f.writeBytes(ByteArray(26 * 1024 * 1024))
        val res = AgentUploads.read(ctx, Uri.fromFile(f))
        assertTrue(res is AgentUploads.Result.Rejected)
    }
}

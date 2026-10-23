package com.example

import com.example.concordance.integration.kaggle.KaggleComputeManager
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KaggleComputeManagerTest {

    @Test
    fun searchDatasetsFiltersPresetsWhenUnconfigured() {
        KaggleComputeManager.updateConfig(null)
        val results = KaggleComputeManager.searchDatasets("esr1")
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun fetchRemoteDatasetsHitsMockServerWhenConfigured() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val url = server.url("/").toString().trimEnd('/')
            KaggleComputeManager.updateConfig("tok-abc", baseUrl = url)
            server.enqueue(okhttp3.mockwebserver.MockResponse()
                .setResponseCode(200)
                .setBody("""[{"ref":"u/d","title":"ESR1 dataset","subtitle":"desc","totalBytes":102400,"lastUpdated":"2026-09-02"}]"""))
            val results = KaggleComputeManager.fetchRemoteDatasets("esr1")
            val req = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals("GET", req?.method)
            assertEquals("Bearer tok-abc", req?.getHeader("Authorization"))
            assertTrue(results.isNotEmpty())
            assertEquals("ESR1 dataset", results[0].title)
        } finally {
            server.shutdown()
            KaggleComputeManager.updateConfig(null)
        }
    }

    @Test
    fun isConfiguredReflectsToken() {
        KaggleComputeManager.updateConfig(null)
        assertEquals(false, KaggleComputeManager.isConfigured)
        KaggleComputeManager.updateConfig("tok-abc")
        assertEquals(true, KaggleComputeManager.isConfigured)
        KaggleComputeManager.updateConfig(null)
    }
}

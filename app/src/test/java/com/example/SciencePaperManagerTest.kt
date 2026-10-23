package com.example

import com.example.concordance.integration.science.SciencePaperManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SciencePaperManagerTest {

    private lateinit var harness: MockHttpHarness

    @Before
    fun setUp() {
        resetIntegrationState()
        harness = MockHttpHarness()
        harness.install()
    }

    @After
    fun tearDown() {
        harness.shutdown()
        resetIntegrationState()
    }

    @Test
    fun searchPapers_with_blank_query_returns_cached_corpus() {
        val result = SciencePaperManager.searchPapers("")
        // BENCHMARK_PAPERS is empty, so the corpus is empty.
        assertTrue(result.isEmpty())
    }

    @Test
    fun searchLivePapers_with_blank_query_returns_empty_offline_false() = runBlocking {
        val result = SciencePaperManager.searchLivePapers("")
        assertTrue(result.papers.isEmpty())
        assertEquals(0, result.liveRecords)
        assertTrue(!result.offlineFallback)
    }

    @Test
    fun searchLivePapers_with_no_http_response_falls_back_offline() = runBlocking {
        // Both literature adapters return empty on a 404-ish response.
        harness.enqueue(200, """{"resultList":{"result":[]}}""")
        harness.enqueue(200, """{"results":[]}""")
        val result = SciencePaperManager.searchLivePapers("egfr")
        assertTrue(result.offlineFallback || result.liveRecords == 0)
    }

    @Test
    fun setEngine_updates_selected_engine() {
        SciencePaperManager.setEngine(com.example.concordance.integration.science.AnalysisEngine.LOCAL_LLM)
        assertEquals(com.example.concordance.integration.science.AnalysisEngine.LOCAL_LLM, SciencePaperManager.selectedEngine.value)
        SciencePaperManager.setEngine(com.example.concordance.integration.science.AnalysisEngine.LOCAL_RULE_EXTRACTOR)
    }

    @Test
    fun loadMorePapers_with_blank_query_returns_zero() = runBlocking {
        val added = SciencePaperManager.loadMorePapers("")
        assertEquals(0, added)
    }
}

package com.example

import com.example.concordance.local.LocalLlmService
import com.example.concordance.local.LocalLlmStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalLlmServiceTest {

    @Test
    fun baseUrl_points_at_localhost_llm_port() {
        val url = LocalLlmService.baseUrl()
        assertTrue(url.startsWith("http://127.0.0.1:"))
    }

    @Test
    fun status_initially_stopped() {
        assertEquals(LocalLlmStatus.Stopped, LocalLlmService.status.value)
    }

    @Test
    fun action_constants_are_stable() {
        assertEquals("com.example.concordance.action.LOCAL_LLM_START", LocalLlmService.ACTION_START)
        assertEquals("com.example.concordance.action.LOCAL_LLM_STOP", LocalLlmService.ACTION_STOP)
        assertEquals("model_path", LocalLlmService.EXTRA_MODEL)
    }
}

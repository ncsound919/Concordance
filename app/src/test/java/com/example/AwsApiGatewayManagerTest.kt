package com.example

import com.example.concordance.integration.aws.AwsApiGatewayManager
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AwsApiGatewayManagerTest {

    @Test
    fun invokeReturnsNullWhenUnconfigured() = runBlocking {
        AwsApiGatewayManager.updateConfig(null, null)
        val result = AwsApiGatewayManager.invoke("""{"q":"hi"}""")
        assertEquals(null, result)
    }

    @Test
    fun invokeHitsMockServerWhenConfigured() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val url = server.url("/").toString().trimEnd('/')
            AwsApiGatewayManager.updateConfig(url, "apigw-key-123")
            server.enqueue(okhttp3.mockwebserver.MockResponse()
                .setResponseCode(200)
                .setBody("""{"answer":"ok"}"""))
            val result = AwsApiGatewayManager.invoke("""{"q":"hi"}""")
            val req = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals("POST", req?.method)
            assertEquals("apigw-key-123", req?.getHeader("x-api-key"))
            assertEquals("""{"answer":"ok"}""", result)
        } finally {
            server.shutdown()
            AwsApiGatewayManager.updateConfig(null, null)
        }
    }

    @Test
    fun isConfiguredReflectsEndpoint() {
        AwsApiGatewayManager.updateConfig(null, null)
        assertEquals(false, AwsApiGatewayManager.isConfigured)
        AwsApiGatewayManager.updateConfig("https://example.com", "key")
        assertEquals(true, AwsApiGatewayManager.isConfigured)
        AwsApiGatewayManager.updateConfig(null, null)
    }
}

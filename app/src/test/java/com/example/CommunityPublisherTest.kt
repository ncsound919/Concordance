package com.example

import com.example.concordance.integration.community.CommunityPublisher
import com.example.concordance.integration.supabase.InMemorySupabaseSessionStore
import com.example.concordance.integration.supabase.SupabaseAuth
import com.example.concordance.integration.supabase.SupabaseSession
import com.example.concordance.model.Capsule
import com.example.concordance.model.Verdict
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class CommunityPublisherTest {

    private fun auth(): SupabaseAuth {
        val store = InMemorySupabaseSessionStore()
        store.save(
            SupabaseSession(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                expiresAt = System.currentTimeMillis() / 1000 + 3600,
                userId = "user-1",
                email = "researcher@example.com"
            )
        )
        return SupabaseAuth(store)
    }

    private fun capsule() = Capsule(
        id = "cap-community-1",
        permalink = "concordance.app/c/cap-community-1",
        createdUtc = "2026-10-20T00:00:00Z",
        sha256 = "a".repeat(64),
        question = "Does the effect survive?",
        anchorLabel = "Fixture",
        verdict = Verdict.SUPPORTED,
        citableArgument = "argument",
        plainLanguageVerdict = "plain",
        rNull = 0.5,
        rSignificance = 0.4,
        snAssumed = 0.9,
        rLow = 0.3,
        rHigh = 0.9,
        observedOr = 0.3,
        rawJson = """{"sha256":"${"a".repeat(64)}"}""",
        markdown = "# capsule"
    )

    @Test
    fun publishesWithBearerTokenAndParsesTimestamp() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(201)
                    .setBody(
                        """{"publication":{"artifact_id":"cap-community-1"},"url":"https://example.test/registry/cap-community-1","timestamped":true,"timestamp":{"genTime":"2026-10-20T00:00:01Z","tsa":"https://freetsa.org/tsr"}}"""
                    )
            )
            val result = CommunityPublisher.publishCapsule(
                auth = auth(),
                baseUrl = server.url("/").toString().trimEnd('/'),
                capsule = capsule()
            )
            assertTrue("publish failed: ${result.exceptionOrNull()}", result.isSuccess)
            val published = result.getOrThrow()
            assertEquals("cap-community-1", published.artifactId)
            assertTrue(published.url.endsWith("/registry/cap-community-1"))
            assertTrue(published.timestamped)
            assertEquals("https://freetsa.org/tsr", published.tsa)

            val request = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("/api/app/publish", request?.path)
            assertEquals("Bearer access-token", request?.getHeader("Authorization"))
            assertTrue(request?.body?.readUtf8()?.contains("\"artifactType\":\"capsule\"") == true)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun requiresWebsiteUrlBeforePublishing() = runBlocking {
        val result = CommunityPublisher.publishCapsule(
            auth = auth(),
            baseUrl = "",
            capsule = capsule()
        )
        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("website URL") == true
        )
    }

    /**
     * A fresh install is both signed out and unconfigured. The URL is the first thing
     * the user can actually fix, so it has to be the message they see.
     */
    @Test
    fun reportsMissingWebsiteUrlBeforeSignIn() = runBlocking {
        val result = CommunityPublisher.publishCapsule(
            auth = SupabaseAuth(InMemorySupabaseSessionStore()),
            baseUrl = "",
            capsule = capsule()
        )
        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("expected the URL hint, got: $message", message.contains("website URL"))
    }

    @Test
    fun asksForSignInOnceUrlIsSet() = runBlocking {
        val result = CommunityPublisher.publishCapsule(
            auth = SupabaseAuth(InMemorySupabaseSessionStore()),
            baseUrl = "https://concordance.test",
            capsule = capsule()
        )
        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("expected the sign-in hint, got: $message", message.contains("Sign in"))
    }

    @Test
    fun surfacesServerRefusal() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"content changed"}"""))
            val result = CommunityPublisher.publishCapsule(
                auth = auth(),
                baseUrl = server.url("/").toString().trimEnd('/'),
                capsule = capsule()
            )
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("content changed") == true)
        } finally {
            server.shutdown()
        }
    }
}

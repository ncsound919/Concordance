package com.example

import com.example.concordance.integration.supabase.SupabaseSession
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SupabaseSessionTest {
    @Test
    fun jsonRoundTrip() {
        val s = SupabaseSession("access", "refresh", 12345L, "uid-1", "a@b.com")
        val back = SupabaseSession.fromJson(s.toJson())
        assertEquals(s.accessToken, back.accessToken)
        assertEquals(s.refreshToken, back.refreshToken)
        assertEquals(s.expiresAt, back.expiresAt)
        assertEquals(s.userId, back.userId)
        assertEquals(s.email, back.email)
    }

    @Test
    fun fromAuthResponse() {
        val o = JSONObject("""{"access_token":"at","refresh_token":"rt","expires_in":3600,"user":{"id":"u1","email":"x@y.com"}}""")
        val s = SupabaseSession.fromJson(o)
        assertEquals("at", s.accessToken)
        assertEquals("rt", s.refreshToken)
        assertEquals("u1", s.userId)
        assertEquals("x@y.com", s.email)
        assertFalse(s.isExpired)
    }

    @Test
    fun expiredWhenPastExpiry() {
        val s = SupabaseSession("a", "r", 1L, "u", null)
        assertTrue(s.isExpired)
    }
}
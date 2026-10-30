package com.example

import com.example.concordance.data.ClaimEntity
import com.example.concordance.integration.supabase.SyncMappers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncMappersTest {
    @Test
    fun claimRoundTrip() {
        val claim = ClaimEntity(
            id = "c1",
            label = "Test claim",
            source = "src",
            evidenceGrade = "FULL_TEXT",
            brainEvents = 5,
            brainTotal = 10,
            otherEvents = 2,
            otherTotal = 10,
            oddsRatio = 2.5,
            orCiLow = 1.2,
            orCiHigh = 4.0,
            referencePrevalence = 0.1,
            denominator = "per 100",
            notes = "note",
            isCustom = true,
            dateAdded = "2026-10-01",
            challengeCount = 1
        )
        val json = SyncMappers.claimToSupabase(claim)
        val back = SyncMappers.claimFromSupabase(json)
        assertEquals(claim.id, back.id)
        assertEquals(claim.label, back.label)
        assertEquals(claim.evidenceGrade, back.evidenceGrade)
        assertEquals(claim.oddsRatio, back.oddsRatio)
        assertEquals("synced", back.syncStatus)
    }

    @Test
    fun claimToSupabase_omitsFreeTextNotes() {
        val claim = ClaimEntity(
            id = "c1", label = "L", source = "s", evidenceGrade = "FULL_TEXT",
            brainEvents = 1, brainTotal = 2, otherEvents = 1, otherTotal = 2,
            oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = null,
            denominator = "d", notes = "PHI-looking free text", isCustom = false,
            dateAdded = "2026-10-01"
        )
        val json = SyncMappers.claimToSupabase(claim)
        // Free-text notes never leave the device (H1).
        org.junit.Assert.assertFalse(json.has("notes"))
    }

    @Test
    fun claimToSupabase_defaultsVisibilityToPrivate_evenForNonCustom() {
        val claim = ClaimEntity(
            id = "c1", label = "L", source = "s", evidenceGrade = "FULL_TEXT",
            brainEvents = 1, brainTotal = 2, otherEvents = 1, otherTotal = 2,
            oddsRatio = null, orCiLow = null, orCiHigh = null, referencePrevalence = null,
            denominator = "d", notes = "n", isCustom = false, // built-in anchor
            dateAdded = "2026-10-01"
        )
        val json = SyncMappers.claimToSupabase(claim)
        // Everything syncs private; public visibility needs an explicit publish (H2).
        assertEquals("private", json.getString("visibility"))
    }

    @Test
    fun parseJsonArray() {
        val text = """[{"id":"a"},{"id":"b"}]"""
        val arr = SyncMappers.parseJsonArray(text)
        assertEquals(2, arr.size)
        assertEquals("a", arr[0].optString("id"))
    }
}
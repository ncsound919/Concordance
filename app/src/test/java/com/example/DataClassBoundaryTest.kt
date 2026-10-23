package com.example

import com.example.concordance.data.ClaimEntity
import com.example.concordance.integration.supabase.SupabaseSyncManager
import com.example.concordance.integration.supabase.SyncMappers
import com.example.concordance.model.Anchor
import com.example.concordance.model.EvidenceGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app used to stamp `data_class = "aggregate"` on every pushed claim, so a
 * record containing patient-identifiable text would leave the device labelled as
 * aggregate. The classification now lives on the record, and the boundary checks
 * it instead of asserting it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataClassBoundaryTest {

    private fun anchor() = Anchor(
        id = "claim-1",
        label = "ESR1 depletion",
        source = "test",
        evidenceGrade = EvidenceGrade.COUNTS,
        brainEvents = 3,
        brainTotal = 40,
        otherEvents = 9,
        otherTotal = 40,
        oddsRatio = null,
        orCiLow = null,
        orCiHigh = null,
        referencePrevalence = null,
        denominator = "patients",
        notes = "note",
        isCustom = true,
        dateAdded = "2026-10-21"
    )

    @Test
    fun newClaimsDefaultToAggregate() {
        val claim = ClaimEntity.fromAnchor(anchor())
        assertEquals(ClaimEntity.DATA_CLASS_AGGREGATE, claim.dataClass)
        assertTrue(SupabaseSyncManager.mayLeaveDevice(claim))
    }

    @Test
    fun classifiedClaimsAreHeldLocally() {
        val phi = ClaimEntity.fromAnchor(anchor(), dataClass = ClaimEntity.DATA_CLASS_PHI)
        assertFalse(SupabaseSyncManager.mayLeaveDevice(phi))

        val deidentified = ClaimEntity.fromAnchor(anchor(), dataClass = "deidentified")
        assertFalse(SupabaseSyncManager.mayLeaveDevice(deidentified))
    }

    @Test
    fun wirePayloadCarriesTheRecordsOwnClass() {
        val phi = ClaimEntity.fromAnchor(anchor(), dataClass = ClaimEntity.DATA_CLASS_PHI)
        assertEquals("phi", SyncMappers.claimToSupabase(phi).getString("data_class"))

        val aggregate = ClaimEntity.fromAnchor(anchor())
        assertEquals("aggregate", SyncMappers.claimToSupabase(aggregate).getString("data_class"))
    }

    @Test
    fun remoteRowsWithoutAClassAreTreatedAsAggregate() {
        val remote = SyncMappers.claimFromSupabase(
            org.json.JSONObject().apply {
                put("id", "remote-1")
                put("label", "L")
                put("source", "s")
                put("evidence_grade", "COUNTS")
                put("denominator", "n")
                put("notes", "")
                put("is_custom", false)
                put("date_added", "2026-10-21")
            }
        )
        assertEquals(ClaimEntity.DATA_CLASS_AGGREGATE, remote.dataClass)
    }

    @Test
    fun remoteRowsKeepAnExplicitClass() {
        val remote = SyncMappers.claimFromSupabase(
            org.json.JSONObject().apply {
                put("id", "remote-2")
                put("label", "L")
                put("source", "s")
                put("evidence_grade", "COUNTS")
                put("denominator", "n")
                put("notes", "")
                put("is_custom", false)
                put("date_added", "2026-10-21")
                put("data_class", "phi")
            }
        )
        assertEquals(ClaimEntity.DATA_CLASS_PHI, remote.dataClass)
        assertFalse(SupabaseSyncManager.mayLeaveDevice(remote))
    }
}

package com.example

import com.example.concordance.integration.api.AdapterDescriptor
import com.example.concordance.integration.api.AuthType
import com.example.concordance.integration.api.Capability
import com.example.concordance.integration.api.RateLimitSpec
import com.example.concordance.integration.byok.GateResult
import com.example.concordance.integration.byok.InMemoryKeyStore
import com.example.concordance.integration.byok.InMemoryLicenseStore
import com.example.concordance.integration.byok.IntegrationGate
import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationGateTest {

    private fun descriptor(
        id: String = "test",
        auth: AuthType = AuthType.NONE,
        license: LicenseClass = LicenseClass.OPEN
    ): AdapterDescriptor = AdapterDescriptor(
        id = id,
        displayName = id,
        category = ProviderCategory.VARIANT_EVIDENCE,
        authType = auth,
        licenseClass = license,
        rateLimit = RateLimitSpec(1.0, 1),
        capabilities = setOf(Capability.SEARCH),
        docsUrl = "https://example.com"
    )

    @Test
    fun openKeylessProviderIsAllowed() {
        val d = descriptor()
        val keys = InMemoryKeyStore()
        val licenses = InMemoryLicenseStore()
        assertEquals(GateResult.Allowed, IntegrationGate.evaluate(d, keys, licenses))
    }

    @Test
    fun academicProviderNeedsLicenseFirst() {
        val d = descriptor(auth = AuthType.TOKEN, license = LicenseClass.ACADEMIC_ONLY)
        val keys = InMemoryKeyStore()
        val licenses = InMemoryLicenseStore()
        assertTrue(IntegrationGate.evaluate(d, keys, licenses) is GateResult.NeedsLicense)
    }

    @Test
    fun academicProviderNeedsKeyAfterLicense() {
        val d = descriptor(auth = AuthType.TOKEN, license = LicenseClass.ACADEMIC_ONLY)
        val keys = InMemoryKeyStore()
        val licenses = InMemoryLicenseStore()
        licenses.acknowledge(LicenseClass.ACADEMIC_ONLY)
        assertTrue(IntegrationGate.evaluate(d, keys, licenses) is GateResult.NeedsKey)
    }

    @Test
    fun academicProviderAllowedWithLicenseAndKey() {
        val d = descriptor(id = "oncokb_api", auth = AuthType.TOKEN, license = LicenseClass.ACADEMIC_ONLY)
        val keys = InMemoryKeyStore()
        val licenses = InMemoryLicenseStore()
        licenses.acknowledge(LicenseClass.ACADEMIC_ONLY)
        keys.setKey("oncokb_api", "secret-token")
        assertEquals(GateResult.Allowed, IntegrationGate.evaluate(d, keys, licenses))
    }

    @Test
    fun paidProviderNeedsLicense() {
        val d = descriptor(auth = AuthType.API_KEY, license = LicenseClass.PAID)
        val keys = InMemoryKeyStore()
        val licenses = InMemoryLicenseStore()
        assertTrue(IntegrationGate.evaluate(d, keys, licenses) is GateResult.NeedsLicense)
    }
}

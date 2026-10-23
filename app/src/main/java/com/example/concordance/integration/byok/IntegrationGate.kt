package com.example.concordance.integration.byok

import com.example.concordance.integration.api.AdapterDescriptor
import com.example.concordance.integration.rotation.LicenseClass

/**
 * Pure gate logic: decides whether an adapter may run given the current keys
 * and license acknowledgments. Kept free of Android so it is unit-testable.
 */
sealed class GateResult {
    object Allowed : GateResult()
    data class NeedsKey(val providerId: String) : GateResult()
    data class NeedsLicense(val license: LicenseClass) : GateResult()
    data class Blocked(val reason: String) : GateResult()
}

object IntegrationGate {
    /**
     * Evaluates whether [descriptor] may execute.
     * - OPEN providers with no auth always run.
     * - Providers needing a key (API_KEY/OAUTH/TOKEN/ACCESS_TOKEN) require a
     *   non-blank key in [keys].
     * - ACADEMIC_ONLY / PAID providers require prior acknowledgment in [licenses].
     */
    fun evaluate(
        descriptor: AdapterDescriptor,
        keys: KeyStore,
        licenses: LicenseStore
    ): GateResult {
        // License acknowledgment first: a gated license must be acknowledged
        // before any key check, so the user is never asked for a key for a
        // license they have not accepted.
        if (descriptor.licenseClass == LicenseClass.ACADEMIC_ONLY ||
            descriptor.licenseClass == LicenseClass.PAID
        ) {
            if (!licenses.isAcknowledged(descriptor.licenseClass)) {
                return GateResult.NeedsLicense(descriptor.licenseClass)
            }
        }

        // Key requirement.
        when (descriptor.authType) {
            com.example.concordance.integration.api.AuthType.NONE -> Unit
            com.example.concordance.integration.api.AuthType.API_KEY,
            com.example.concordance.integration.api.AuthType.OAUTH,
            com.example.concordance.integration.api.AuthType.TOKEN,
            com.example.concordance.integration.api.AuthType.ACCESS_TOKEN -> {
                if (!keys.hasKey(descriptor.id)) {
                    return GateResult.NeedsKey(descriptor.id)
                }
            }
        }

        return GateResult.Allowed
    }
}

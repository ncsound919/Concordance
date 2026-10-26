package com.example.concordance.integration.api

import com.example.concordance.integration.byok.KeyStore
import com.example.concordance.integration.byok.LicenseStore
import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import com.example.concordance.integration.rotation.RateLimitSpec

/**
 * Kaggle Compute Adapter — wraps the Kaggle kernel API for running
 * reproducible oncology experiments (bounties) defined by the deep-research
 * plan. Provides a thin client over the public Kaggle kernel API.
 */
object KaggleComputeAdapter : IntegrationAdapter {
    override val descriptor: AdapterDescriptor = AdapterDescriptor(
        id = "kaggle_compute",
        name = "Kaggle Compute (bounty execution)",
        category = ProviderCategory.DATASET_COMPUTE,
        authType = AuthType.API_KEY,
        capabilities = setOf(Capability.SEARCH, Capability.COMPUTE),
        rateLimit = RateLimitSpec(
            requestsPerMinute = 6,
            burstSize = 3,
            backoffMs = 1000
        ),
        licenseClass = LicenseClass.BYOK
    )

    /** Submit a notebook kernel run for a bounty experiment. Returns the kernel URL. */
    suspend fun submitBountyRun(bountyId: Int, notebookPath: String, keys: KeyStore): String? {
        // Stub: real implementation would POST to Kaggle kernels API
        // POST https://www.kaggle.com/api/v1/kernels/push
        return null
    }

    /** Poll the status of a previously submitted bounty run. */
    suspend fun pollBountyStatus(kernelRef: String, keys: KeyStore): String? {
        // Stub: returns "queued" | "running" | "complete" | "error"
        return null
    }

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> = emptyList()
}

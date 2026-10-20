package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory

/**
 * How a provider authenticates. Concordance ships BYOK (bring-your-own-key);
 * providers with authType NONE work out of the box.
 */
enum class AuthType { NONE, API_KEY, OAUTH, TOKEN, ACCESS_TOKEN }

/** What a provider can be used for. Keeps routing explicit. */
enum class Capability {
    SEARCH,
    FETCH_METADATA,
    FETCH_COUNTS,
    FULLTEXT_LINK,
    TRIALS,
    DOI_MINT,
    COMPUTE
}

/**
 * Token-bucket specification for a provider. Values are taken from the
 * provider's published policy (see concordance_integrations_outline.md).
 */
data class RateLimitSpec(
    val requestsPerSecond: Double,
    val burst: Int,
    val dailyQuota: Int? = null
)

data class AdapterDescriptor(
    val id: String,
    val displayName: String,
    val category: ProviderCategory,
    val authType: AuthType,
    val licenseClass: LicenseClass,
    val rateLimit: RateLimitSpec,
    val capabilities: Set<Capability>,
    val docsUrl: String,
    val requiresEmail: Boolean = false
)

data class IntegrationQuery(
    val text: String,
    val pageSize: Int = 25,
    val page: Int = 0,
    val contactEmail: String? = "research@concordance.app"
)

/**
 * Auditable record of a single external request. Embedded in capsules so an
 * adjudication can always be traced back to the exact source response.
 */
data class Provenance(
    val providerId: String,
    val endpoint: String,
    val query: String,
    val httpStatus: Int,
    val retrievedUtc: String,
    val responseSha256: String,
    val licenseClass: LicenseClass,
    val elapsedMs: Long
)

/**
 * A provider-agnostic record. Adapters normalize their own JSON into this so
 * downstream engines never depend on a specific API shape.
 */
data class NormalizedRecord(
    val externalId: String,
    val source: String,
    val title: String,
    val authors: String,
    val journal: String,
    val year: Int?,
    val doi: String?,
    val pmid: String?,
    val abstractText: String,
    val url: String?,
    val citationCount: Int?,
    val isOpenAccess: Boolean?,
    val provenance: Provenance
)

interface IntegrationAdapter {
    val descriptor: AdapterDescriptor
    suspend fun search(query: IntegrationQuery): List<NormalizedRecord>
}

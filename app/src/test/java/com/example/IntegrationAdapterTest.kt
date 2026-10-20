package com.example

import com.example.concordance.integration.api.AuthType
import com.example.concordance.integration.api.CBioPortalAdapter
import com.example.concordance.integration.api.CivicAdapter
import com.example.concordance.integration.api.IntegrationRegistry
import com.example.concordance.integration.api.ProviderRateLimiter
import com.example.concordance.integration.api.RateLimitSpec
import com.example.concordance.integration.rotation.ApiRotationManager
import com.example.concordance.integration.rotation.LicenseClass
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IntegrationAdapterTest {

    @Test
    fun everyAdapterIdMatchesARegisteredProvider() {
        val registered = ApiRotationManager.providers.value.map { it.id }.toSet()
        for (adapter in IntegrationRegistry.adapters) {
            assertTrue(
                "Adapter '${adapter.descriptor.id}' has no rotation-pool entry",
                adapter.descriptor.id in registered
            )
        }
    }

    @Test
    fun adaptersDeclareConsistentAuthAndLicensing() {
        for (adapter in IntegrationRegistry.adapters) {
            val d = adapter.descriptor
            assertTrue(d.rateLimit.requestsPerSecond > 0.0)
            assertTrue(d.rateLimit.burst >= 1)
            assertTrue(d.docsUrl.startsWith("https://"))
            assertTrue(d.capabilities.isNotEmpty())
            if (d.licenseClass == LicenseClass.OPEN) {
                assertEquals("${d.id} is OPEN but requires a key", AuthType.NONE, d.authType)
            } else {
                assertTrue(
                    "${d.id} is gated but has no auth requirement",
                    d.authType != AuthType.NONE
                )
            }
        }
    }

    @Test
    fun rateLimiterAllowsBurstThenThrottles() = runBlocking {
        val spec = RateLimitSpec(requestsPerSecond = 100.0, burst = 2)
        val start = System.currentTimeMillis()
        repeat(4) { ProviderRateLimiter.acquire("test_provider", spec) }
        val elapsed = System.currentTimeMillis() - start
        assertTrue("Expected throttling beyond the burst, took ${elapsed}ms", elapsed >= 10)
    }

    @Test
    fun rotationPoolLicensesAreDeclared() {
        for (provider in ApiRotationManager.providers.value) {
            assertTrue(
                "${provider.id} has an invalid license class",
                provider.licenseClass in LicenseClass.entries
            )
        }
        // At least one gated provider exists now (OncoKB).
        assertTrue(
            ApiRotationManager.providers.value.any { it.licenseClass != LicenseClass.OPEN }
        )
    }

    @Test
    fun civicEvidenceIsParsedAndDeDuplicated() {
        val json = """
            {
              "data": {
                "gene": {
                  "name": "ESR1",
                  "variants": {
                    "nodes": [
                      {
                        "name": "D538G",
                        "variantTypes": [ { "name": "Missense Variant" } ],
                        "molecularProfiles": {
                          "nodes": [
                            {
                              "name": "ESR1 D538G",
                              "evidenceItems": {
                                "nodes": [
                                  {
                                    "id": 243,
                                    "link": "/evidence/243",
                                    "evidenceType": "PREDICTIVE",
                                    "evidenceLevel": "D",
                                    "evidenceDirection": "SUPPORTS",
                                    "significance": "RESISTANCE",
                                    "evidenceRating": 3,
                                    "disease": { "name": "Breast Cancer" },
                                    "therapies": [ { "name": "Hormone Therapy" } ],
                                    "description": "MCF7 resistance to hormone therapy."
                                  },
                                  {
                                    "id": 999,
                                    "evidenceType": "PREDICTIVE",
                                    "evidenceLevel": "C",
                                    "evidenceDirection": "SUPPORTS",
                                    "significance": "SENSITIVITYRESPONSE",
                                    "disease": { "name": "Breast Cancer" },
                                    "therapies": [],
                                    "description": "No link field, so a fallback URL is built."
                                  }
                                ]
                              }
                            },
                            {
                              "name": "ESR1 D538G (mirrored profile)",
                              "evidenceItems": {
                                "nodes": [ { "id": 243, "description": "duplicate must be dropped" } ]
                              }
                            }
                          ]
                        }
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        val evidence = CivicAdapter.parseEvidence(json)

        assertEquals(2, evidence.size)
        val first = evidence.first { it.civicEvidenceId == 243 }
        assertEquals("ESR1", first.gene)
        assertEquals("D538G", first.variant)
        assertEquals("https://civicdb.org/evidence/243", first.civicUrl)
        assertEquals(listOf("Missense Variant"), first.variantTypes)
        assertEquals(listOf("Hormone Therapy"), first.therapies)
        assertEquals(3, first.evidenceRating)

        val noLink = evidence.first { it.civicEvidenceId == 999 }
        assertEquals("https://civicdb.org/evidence/999", noLink.civicUrl)
        assertNull(noLink.evidenceRating)
    }

    @Test
    fun civicEvidenceParsesEmptyAndMalformedResponsesSafely() {
        assertTrue(CivicAdapter.parseEvidence("{}").isEmpty())
        assertTrue(CivicAdapter.parseEvidence("not json").isEmpty())
    }

    @Test
    fun cBioPortalStudiesAreParsed() {
        val json = """
            [
              {
                "studyId": "brca_metastatic",
                "name": "Breast Cancer (Metastatic)",
                "cancerTypeId": "brca",
                "description": "Metastatic breast cancer cohort.",
                "allSampleCount": 150,
                "referenceGenome": "hg38",
                "pmid": "12345678",
                "citation": "Author et al. 2025"
              },
              { "studyId": "", "name": "invalid entry" }
            ]
        """.trimIndent()

        val studies = CBioPortalAdapter.parseStudies(json)

        assertEquals(1, studies.size)
        val study = studies.first()
        assertEquals("brca_metastatic", study.studyId)
        assertEquals("brca", study.cancerType)
        assertEquals(150, study.sampleCount)
        assertEquals("https://www.cbioportal.org/study/summary?id=brca_metastatic", study.url)
    }

    @Test
    fun cBioPortalParsesMalformedResponseSafely() {
        assertTrue(CBioPortalAdapter.parseStudies("{ not an array }").isEmpty())
    }
}

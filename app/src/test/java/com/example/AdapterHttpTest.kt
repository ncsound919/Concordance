package com.example

import com.example.concordance.integration.api.ClinicalTrialsGovAdapter
import com.example.concordance.integration.api.EuropePmcAdapter
import com.example.concordance.integration.api.IntegrationQuery
import com.example.concordance.integration.api.OncoKBAdapter
import com.example.concordance.integration.api.OpenAlexAdapter
import com.example.concordance.integration.byok.InMemoryKeyStore
import com.example.concordance.integration.byok.InMemoryLicenseStore
import com.example.concordance.integration.rotation.LicenseClass
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The adapters that had no test at all, exercised end to end against a local
 * server.
 *
 * Each adapter is a mapping from one provider's JSON shape into a
 * [com.example.concordance.integration.api.NormalizedRecord]. The mapping is
 * where the risk lives: a field read from the wrong place, a null that becomes
 * the string "null", an empty array that becomes one empty record.
 *
 * The adapters build their own absolute URLs, so the harness rewrites the host
 * and keeps the path - which means the test also pins the path each adapter
 * asks for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdapterHttpTest {

    private lateinit var harness: MockHttpHarness

    @Before
    fun setUp() {
        resetIntegrationState()
        harness = MockHttpHarness()
        harness.install()
    }

    @After
    fun tearDown() {
        harness.shutdown()
        resetIntegrationState()
    }

    // =======================================================================
    // ClinicalTrials.gov
    // =======================================================================

    @Test
    fun `clinical trials asks for the studies endpoint with the query encoded`() = runBlocking {
        harness.enqueue(200, """{"studies":[]}""")
        ClinicalTrialsGovAdapter.search(IntegrationQuery(text = "ESR1 brain met"))
        val request = harness.takeRequest()
        assertTrue(
            "path was ${request.path}",
            request.path!!.startsWith("/api/v2/studies?")
        )
        assertTrue("query was not encoded", request.path!!.contains("query.term=ESR1+brain+met"))
        assertTrue(request.path!!.contains("pageSize=25"))
        assertTrue(request.path!!.contains("page=1"))
    }

    @Test
    fun `clinical trials maps a study into a normalized record`() = runBlocking {
        harness.enqueue(200, CLINICAL_TRIALS_RESPONSE)
        val records = ClinicalTrialsGovAdapter.search(IntegrationQuery(text = "ESR1"))

        assertEquals(1, records.size)
        val record = records.first()
        assertEquals("NCT:NCT01234567", record.externalId)
        assertEquals("ClinicalTrials.gov", record.source)
        assertEquals("ESR1 in brain metastases", record.title)
        assertEquals("2024", record.year?.toString())
        assertEquals(true, record.isOpenAccess)
        assertEquals("https://clinicaltrials.gov/study/NCT01234567", record.url)
    }

    @Test
    fun `clinical trials keeps the trial-only fields on the registry entry point`() = runBlocking {
        harness.enqueue(200, CLINICAL_TRIALS_RESPONSE)
        val studies = ClinicalTrialsGovAdapter.searchStudies(IntegrationQuery(text = "ESR1"))
        assertEquals(1, studies.size)
        val study = studies.first()
        assertEquals("NCT01234567", study.nctId)
        assertEquals("Phase 2", study.phase)
        assertEquals("ACME Pharma", study.sponsor)
        assertTrue(study.conditions.contains("Breast Cancer"))
        assertEquals("2024-06-01", study.primaryCompletionDate)
    }

    @Test
    fun `clinical trials flags a record that mentions CNS involvement`() = runBlocking {
        harness.enqueue(200, CLINICAL_TRIALS_RESPONSE)
        val study = ClinicalTrialsGovAdapter.searchStudies(IntegrationQuery(text = "ESR1")).first()
        assertTrue(
            "brain criteria was: ${study.brainMetastasisCriteria}",
            study.brainMetastasisCriteria.contains("CNS involvement")
        )
        assertTrue(
            "esr1 tracking was: ${study.esr1EndpointTracking}",
            study.esr1EndpointTracking.contains("ctDNA")
        )
    }

    @Test
    fun `clinical trials says plainly when a record mentions nothing of interest`() = runBlocking {
        harness.enqueue(200, """{"studies":[{"protocolSection":{"identificationModule":{"nctId":"NCT1","briefTitle":"T"}}}]}""")
        val study = ClinicalTrialsGovAdapter.searchStudies(IntegrationQuery(text = "x")).first()
        assertTrue(study.brainMetastasisCriteria.contains("No explicit CNS"))
        assertTrue(study.esr1EndpointTracking.contains("No ctDNA"))
        assertEquals("Not specified", study.phase)
        assertEquals("Not specified", study.sponsor)
    }

    @Test
    fun `clinical trials drops a study with no nct id`() = runBlocking {
        harness.enqueue(200, """{"studies":[{"protocolSection":{"identificationModule":{"nctId":""}}}]}""")
        assertTrue(ClinicalTrialsGovAdapter.search(IntegrationQuery(text = "x")).isEmpty())
    }

    @Test
    fun `clinical trials survives a malformed response`() = runBlocking {
        harness.enqueue(200, "not json at all")
        assertTrue(ClinicalTrialsGovAdapter.search(IntegrationQuery(text = "x")).isEmpty())
    }

    @Test
    fun `clinical trials returns nothing when the server fails`() = runBlocking {
        harness.enqueue(500, "{}")
        assertTrue(ClinicalTrialsGovAdapter.search(IntegrationQuery(text = "x")).isEmpty())
    }

    @Test
    fun `clinical trials does not call out for a blank query`() = runBlocking {
        assertTrue(ClinicalTrialsGovAdapter.search(IntegrationQuery(text = "   ")).isEmpty())
        assertEquals(0, harness.requestCount())
    }

    // =======================================================================
    // Europe PMC
    // =======================================================================

    @Test
    fun `europe pmc asks for the restful search endpoint`() = runBlocking {
        harness.enqueue(200, """{"resultList":{"result":[]}}""")
        EuropePmcAdapter.search(IntegrationQuery(text = "ESR1 mutation"))
        val request = harness.takeRequest()
        assertTrue("path was ${request.path}", request.path!!.startsWith("/europepmc/webservices/rest/search?"))
        assertTrue(request.path!!.contains("resultType=core"))
        assertTrue(request.path!!.contains("format=json"))
    }

    @Test
    fun `europe pmc maps a result into a normalized record`() = runBlocking {
        harness.enqueue(200, EUROPE_PMC_RESPONSE)
        val records = EuropePmcAdapter.search(IntegrationQuery(text = "ESR1"))
        assertEquals(1, records.size)
        val record = records.first()
        assertEquals("EPMC:32887766", record.externalId)
        assertEquals("Europe PMC (MED)", record.source)
        assertEquals("ESR1 mutations in metastatic breast cancer", record.title)
        assertEquals("Doe J, Roe R", record.authors)
        assertEquals("Journal of Tests", record.journal)
        assertEquals(2024, record.year)
        assertEquals("10.1000/xyz", record.doi)
        assertEquals("31234567", record.pmid)
        assertEquals(42, record.citationCount)
        assertEquals(true, record.isOpenAccess)
        assertEquals("https://europepmc.org/article/MED/32887766", record.url)
    }

    @Test
    fun `europe pmc leaves an absent doi and pmid null rather than empty`() = runBlocking {
        harness.enqueue(200, """{"resultList":{"result":[{"id":"1","source":"MED","title":"T"}]}}""")
        val record = EuropePmcAdapter.search(IntegrationQuery(text = "x")).first()
        assertEquals(null, record.doi)
        assertEquals(null, record.pmid)
        assertEquals(null, record.citationCount)
        // The open-access flag is a definite boolean, not a nullable one.
        assertEquals(false, record.isOpenAccess)
    }

    @Test
    fun `europe pmc survives a malformed response`() = runBlocking {
        harness.enqueue(200, "}{")
        assertTrue(EuropePmcAdapter.search(IntegrationQuery(text = "x")).isEmpty())
    }

    @Test
    fun `europe pmc returns nothing when the server fails`() = runBlocking {
        harness.enqueue(503, "{}")
        assertTrue(EuropePmcAdapter.search(IntegrationQuery(text = "x")).isEmpty())
    }

    @Test
    fun `europe pmc does not call out for a blank query`() = runBlocking {
        assertTrue(EuropePmcAdapter.search(IntegrationQuery(text = "")).isEmpty())
        assertEquals(0, harness.requestCount())
    }

    // =======================================================================
    // OpenAlex
    // =======================================================================

    @Test
    fun `openalex asks for the works endpoint with a contact email`() = runBlocking {
        harness.enqueue(200, """{"results":[]}""")
        OpenAlexAdapter.search(IntegrationQuery(text = "ESR1"))
        val request = harness.takeRequest()
        assertTrue("path was ${request.path}", request.path!!.startsWith("/works?"))
        assertTrue(request.path!!.contains("mailto=research%40concordance.app"))
        assertTrue(request.path!!.contains("per-page=25"))
    }

    @Test
    fun `openalex maps a work into a normalized record`() = runBlocking {
        harness.enqueue(200, OPENALEX_RESPONSE)
        val records = OpenAlexAdapter.search(IntegrationQuery(text = "ESR1"))
        assertEquals(1, records.size)
        val record = records.first()
        assertEquals("OpenAlex:W123", record.externalId)
        assertEquals("OpenAlex", record.source)
        assertEquals("ESR1 and outcomes", record.title)
        assertEquals("Journal of Tests", record.journal)
        assertEquals(2023, record.year)
        assertEquals("10.1000/abc", record.doi)
        assertEquals(7, record.citationCount)
        assertEquals("https://doi.org/10.1000/abc", record.url)
    }

    @Test
    fun `openalex rebuilds the abstract from the inverted index`() = runBlocking {
        harness.enqueue(200, OPENALEX_RESPONSE)
        val record = OpenAlexAdapter.search(IntegrationQuery(text = "ESR1")).first()
        assertEquals("The first second third", record.abstractText)
    }

    @Test
    fun `openalex lists the authors and truncates a long list`() = runBlocking {
        harness.enqueue(200, OPENALEX_RESPONSE)
        val record = OpenAlexAdapter.search(IntegrationQuery(text = "ESR1")).first()
        assertEquals("A One, B Two", record.authors)
    }

    @Test
    fun `openalex truncates more than four authors with an et al`() = runBlocking {
        harness.enqueue(
            200,
            """
            {
              "results": [
                {
                  "id": "http://x/W1",
                  "display_name": "T",
                  "authorships": [
                    { "author": { "display_name": "Author 1" } },
                    { "author": { "display_name": "Author 2" } },
                    { "author": { "display_name": "Author 3" } },
                    { "author": { "display_name": "Author 4" } },
                    { "author": { "display_name": "Author 5" } },
                    { "author": { "display_name": "Author 6" } }
                  ]
                }
              ]
            }
            """.trimIndent()
        )
        val record = OpenAlexAdapter.search(IntegrationQuery(text = "x")).first()
        assertEquals("Author 1, Author 2, Author 3 et al.", record.authors)
    }

    @Test
    fun `openalex survives a malformed response`() = runBlocking {
        harness.enqueue(200, "not json")
        assertTrue(OpenAlexAdapter.search(IntegrationQuery(text = "x")).isEmpty())
    }

    @Test
    fun `openalex returns nothing when the server fails`() = runBlocking {
        harness.enqueue(500, "{}")
        assertTrue(OpenAlexAdapter.search(IntegrationQuery(text = "x")).isEmpty())
    }

    // =======================================================================
    // OncoKB
    // =======================================================================

    @Test
    fun `oncokb refuses to run before the licence is acknowledged`() = runBlocking {
        val keys = InMemoryKeyStore()
        val licenses = InMemoryLicenseStore()
        assertTrue(OncoKBAdapter.fetchLevels(keys, licenses).isEmpty())
        assertTrue(OncoKBAdapter.fetchGene("ESR1", keys, licenses).isEmpty())
        assertEquals(0, harness.requestCount())
    }

    @Test
    fun `oncokb fetches the public levels catalog once the licence is acknowledged`() = runBlocking {
        val keys = InMemoryKeyStore()
        val licenses = InMemoryLicenseStore()
        licenses.acknowledge(LicenseClass.ACADEMIC_ONLY)
        harness.enqueue(200, """{"LEVEL_1":"Sensitive therapy","LEVEL_2":"Sensitive predictive"}""")

        // The levels catalog is public: the licence is enough, no token needed.
        val records = OncoKBAdapter.fetchLevels(keys, licenses)
        assertEquals(1, records.size)
        val record = records.first()
        assertEquals("OncoKB:levels", record.externalId)
        assertEquals("OncoKB", record.source)
        assertTrue(record.abstractText.contains("LEVEL_1: Sensitive therapy"))
        assertEquals(false, record.isOpenAccess)
        assertEquals("/api/v1/levels", harness.takeRequest().path)
    }

    @Test
    fun `oncokb needs a token for a gene annotation`() = runBlocking {
        val keys = InMemoryKeyStore()
        val licenses = InMemoryLicenseStore()
        licenses.acknowledge(LicenseClass.ACADEMIC_ONLY)
        assertTrue(OncoKBAdapter.fetchGene("ESR1", keys, licenses).isEmpty())
        assertEquals(0, harness.requestCount())
    }

    @Test
    fun `oncokb fetches a gene annotation with the token as a bearer credential`() = runBlocking {
        val keys = InMemoryKeyStore()
        keys.setKey("oncokb_api", "user-token")
        val licenses = InMemoryLicenseStore()
        licenses.acknowledge(LicenseClass.ACADEMIC_ONLY)
        harness.enqueue(200, """{"oncokbAnnotatedVariants":"ESR1 D538G is annotated"}""")

        val records = OncoKBAdapter.fetchGene("ESR1", keys, licenses)
        assertEquals(1, records.size)
        assertEquals("OncoKB:gene:ESR1", records.first().externalId)
        assertTrue(records.first().abstractText.contains("D538G"))
        val request = harness.takeRequest()
        assertEquals("/api/v1/genes/ESR1", request.path)
        assertEquals("Bearer user-token", request.getHeader("Authorization"))
    }

    @Test
    fun `oncokb refuses a blank gene symbol`() = runBlocking {
        val keys = InMemoryKeyStore()
        keys.setKey("oncokb_api", "user-token")
        val licenses = InMemoryLicenseStore()
        licenses.acknowledge(LicenseClass.ACADEMIC_ONLY)
        assertTrue(OncoKBAdapter.fetchGene("  ", keys, licenses).isEmpty())
        assertEquals(0, harness.requestCount())
    }

    @Test
    fun `oncokb reports the gate status for the user interface`() {
        val keys = InMemoryKeyStore()
        val licenses = InMemoryLicenseStore()
        val gate = OncoKBAdapter.gateStatus(keys, licenses)
        assertNotNull(gate)
        assertTrue(gate is com.example.concordance.integration.byok.GateResult.NeedsLicense)
    }

    // =======================================================================
    // Shared behaviour
    // =======================================================================

    @Test
    fun `an adapter that gets a server error returns nothing and does not throw`() = runBlocking {
        harness.enqueue(500, "{}")
        assertTrue(ClinicalTrialsGovAdapter.search(IntegrationQuery(text = "x")).isEmpty())
        assertTrue(EuropePmcAdapter.search(IntegrationQuery(text = "x")).isEmpty())
        assertTrue(OpenAlexAdapter.search(IntegrationQuery(text = "x")).isEmpty())
    }

    @Test
    fun `every response is recorded in the provenance log`() = runBlocking {
        harness.enqueue(200, """{"studies":[]}""")
        ClinicalTrialsGovAdapter.search(IntegrationQuery(text = "ESR1"))
        val record = com.example.concordance.integration.api.ProvenanceRecorder.records.value.first()
        assertEquals("clinical_trials", record.providerId)
        assertEquals("ESR1", record.query)
        assertEquals(200, record.httpStatus)
    }

    // =======================================================================
    // Fixtures
    // =======================================================================

    private val CLINICAL_TRIALS_RESPONSE = """
        {
          "studies": [
            {
              "protocolSection": {
                "identificationModule": {
                  "nctId": "NCT01234567",
                  "briefTitle": "ESR1 in brain metastases",
                  "officialTitle": "A phase 2 study of ESR1 in brain metastases"
                },
                "statusModule": {
                  "overallStatus": "RECRUITING",
                  "primaryCompletionDateStruct": { "date": "2024-06-01" }
                },
                "designModule": { "phases": [ "PHASE2" ] },
                "conditionsModule": { "conditions": [ "Breast Cancer", "Brain Metastasis" ] },
                "armsInterventionsModule": {
                  "interventions": [ { "type": "DRUG", "name": "Elacestrant" } ]
                },
                "sponsorCollaboratorsModule": {
                  "leadSponsor": { "name": "ACME Pharma" }
                },
                "eligibilityModule": {
                  "eligibilityCriteria": "Patients with brain metastases and measurable ctDNA."
                },
                "descriptionModule": {
                  "briefSummary": "Enrols patients with CNS disease."
                }
              }
            }
          ]
        }
    """.trimIndent()

    private val EUROPE_PMC_RESPONSE = """
        {
          "resultList": {
            "result": [
              {
                "id": "32887766",
                "source": "MED",
                "title": "ESR1 mutations in metastatic breast cancer",
                "authorString": "Doe J, Roe R",
                "journalTitle": "Journal of Tests",
                "pubYear": "2024",
                "doi": "10.1000/xyz",
                "pmid": "31234567",
                "abstractText": "An abstract.",
                "citedByCount": 42,
                "isOpenAccess": "Y"
              }
            ]
          }
        }
    """.trimIndent()

    private val OPENALEX_RESPONSE = """
        {
          "results": [
            {
              "id": "http://openalex.org/W123",
              "doi": "https://doi.org/10.1000/abc",
              "display_name": "ESR1 and outcomes",
              "publication_year": 2023,
              "cited_by_count": 7,
              "open_access": { "is_oa": true },
              "primary_location": { "source": { "display_name": "Journal of Tests" } },
              "authorships": [
                { "author": { "display_name": "A One" } },
                { "author": { "display_name": "B Two" } }
              ],
              "abstract_inverted_index": {
                "The": [ 0 ], "first": [ 1 ], "second": [ 2 ], "third": [ 3 ]
              }
            }
          ]
        }
    """.trimIndent()
}

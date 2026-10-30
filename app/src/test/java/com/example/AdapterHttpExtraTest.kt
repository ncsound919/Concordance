package com.example

import com.example.concordance.integration.api.CBioPortalAdapter
import com.example.concordance.integration.api.FigshareAdapter
import com.example.concordance.integration.api.GdcAdapter
import com.example.concordance.integration.api.IntegrationQuery
import com.example.concordance.integration.api.OsfAdapter
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdapterHttpExtraTest {

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

    // -----------------------------------------------------------------------
    // CBioPortal
    // -----------------------------------------------------------------------

    @Test
    fun `cbioportal searchStudies hits the studies endpoint with keyword and pageSize`() = runBlocking {
        harness.enqueue(200, """[{"studyId":"brca_tcga","name":"Breast Invasive Carcinoma","cancerTypeId":"brca","allSampleCount":1084}]""")
        val studies = CBioPortalAdapter.searchStudies("breast", 5)
        assertEquals(1, studies.size)
        assertEquals("brca_tcga", studies[0].studyId)
        assertEquals(1084, studies[0].sampleCount)
        val req = harness.takeRequest()
        assertTrue(req.path!!.contains("/api/studies"))
        assertTrue(req.path!!.contains("keyword=breast"))
        assertTrue(req.path!!.contains("pageSize=5"))
    }

    @Test
    fun `cbioportal malformed payload yields empty list`() = runBlocking {
        harness.enqueue(200, "not json")
        assertTrue(CBioPortalAdapter.searchStudies("breast").isEmpty())
    }

    @Test
    fun `cbioportal empty array yields empty list`() = runBlocking {
        harness.enqueue(200, "[]")
        assertTrue(CBioPortalAdapter.searchStudies("breast").isEmpty())
    }

    // -----------------------------------------------------------------------
    // Figshare
    // -----------------------------------------------------------------------

    @Test
    fun `figshare searchArticles posts to the search endpoint`() = runBlocking {
        harness.enqueue(200, """[{"id":12345,"title":"A figshare article","tags":["a"],"categories":[{"name":"Medicine"}]}]""")
        val (articles, prov) = FigshareAdapter.searchArticles("cancer")!!
        assertEquals(1, articles.size)
        assertEquals(12345L, articles[0].id)
        assertEquals("A figshare article", articles[0].title)
        assertEquals(listOf("a"), articles[0].tags)
        assertEquals(listOf("Medicine"), articles[0].categories)
        assertNotNull(prov)
        val req = harness.takeRequest()
        assertTrue(req.path!!.contains("/v2/articles/search"))
        assertEquals("POST", req.method)
    }

    @Test
    fun `figshare malformed payload yields empty list`() = runBlocking {
        harness.enqueue(200, "not json")
        val result = FigshareAdapter.searchArticles("cancer")
        assertTrue(result == null || result.first.isEmpty())
    }

    // -----------------------------------------------------------------------
    // OSF
    // -----------------------------------------------------------------------

    @Test
    fun `osf searchNodes hits the nodes endpoint with title filter`() = runBlocking {
        harness.enqueue(200, """{"data":[{"id":"abc123","attributes":{"title":"An OSF project","public":true,"category":"project"}}]}""")
        val (nodes, prov) = OsfAdapter.searchNodes("osf")!!
        assertEquals(1, nodes.size)
        assertEquals("abc123", nodes[0].id)
        assertEquals("An OSF project", nodes[0].title)
        assertTrue(nodes[0].public)
        assertNotNull(prov)
        val req = harness.takeRequest()
        assertTrue(req.path!!.contains("/v2/nodes/"))
        assertTrue(req.path!!.contains("filter%5Btitle%5D=osf") || req.path!!.contains("filter[title]=osf"))
    }

    @Test
    fun `osf malformed payload yields empty list`() = runBlocking {
        harness.enqueue(200, "not json")
        val result = OsfAdapter.searchNodes("osf")
        assertTrue(result == null || result.first.isEmpty())
    }

    // -----------------------------------------------------------------------
    // GDC
    // -----------------------------------------------------------------------

    @Test
    fun `gdc searchProjects hits the projects endpoint`() = runBlocking {
        harness.enqueue(200, """{"data":{"hits":[{"project_id":"TCGA-BRCA","name":"Breast","disease_type":["Adenomas"],"primary_site":["Breast"],"summary":{"case_count":1098}}]}}""")
        val projects = GdcAdapter.searchProjects("breast", 5)
        assertEquals(1, projects.size)
        assertEquals("TCGA-BRCA", projects[0].projectId)
        assertEquals(1098, projects[0].caseCount)
        val req = harness.takeRequest()
        assertTrue(req.path!!.contains("/v0/projects"))
    }

    @Test
    fun `gdc malformed payload yields empty list`() = runBlocking {
        harness.enqueue(200, "not json")
        assertTrue(GdcAdapter.searchProjects("breast").isEmpty())
    }

    @Test
    fun `gdc empty hits yields empty list`() = runBlocking {
        harness.enqueue(200, """{"data":{"hits":[]}}""")
        assertTrue(GdcAdapter.searchProjects("breast").isEmpty())
    }
}

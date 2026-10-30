package com.example

import com.example.concordance.integration.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveIntegrationSmokeTest {

    private fun live() = System.getenv("LIVE_TESTS") == "1"

    @Test
    fun semanticScholarSearch() = runBlocking {
        Assume.assumeTrue(live())
        val r = SemanticScholarAdapter.search(IntegrationQuery(text = "breast cancer metastasis", pageSize = 5))
        println("S2: ${r.size} records")
        Assume.assumeTrue("Semantic Scholar rate-limited or empty", r.isNotEmpty())
    }

    @Test
    fun unpaywallLookup() = runBlocking {
        Assume.assumeTrue(live())
        val r = UnpaywallAdapter.fetchOa("10.1038/s41586-020-2649-2", "research@concordance.app")
        println("Unpaywall: oa=${r?.isOa} url=${r?.bestOaUrl}")
        assertTrue("Unpaywall returned null", r != null)
    }

    @Test
    fun biorxivVersionHistory() = runBlocking {
        Assume.assumeTrue(live())
        val v = BiorxivAdapter.fetchVersionHistory("10.1101/2020.03.30.20047993")
        println("bioRxiv: ${v.size} versions")
        assertTrue("bioRxiv returned no versions", v.isNotEmpty())
    }

    @Test
    fun osfSearch() = runBlocking {
        Assume.assumeTrue(live())
        val r = OsfAdapter.searchNodes("reproducibility", 5, null)
        println("OSF: ${r?.first?.size} nodes")
        assertTrue("OSF returned no nodes", (r?.first?.size ?: 0) > 0)
    }

    @Test
    fun figshareSearch() = runBlocking {
        Assume.assumeTrue(live())
        val r = FigshareAdapter.searchArticles("dataset", 5, null)
        println("Figshare: ${r?.first?.size} articles")
        assertTrue("Figshare returned no articles", (r?.first?.size ?: 0) > 0)
    }

    @Test
    fun pubpeerLookup() = runBlocking {
        Assume.assumeTrue(live())
        val r = PubPeerAdapter.fetchByDoi("10.1038/nature12373", null)
        println("PubPeer: ${r.size} publications (endpoint may be unavailable)")
        Assume.assumeTrue("PubPeer returned no publications", r.isNotEmpty())
    }

    @Test
    fun uniProtSearch() = runBlocking {
        Assume.assumeTrue(live())
        val r = UniProtAdapter.fetchProteins("gene:TP53 AND organism_id:9606", 5)
        println("UniProt: ${r?.first?.size} proteins (null=${r == null})")
        assertTrue("UniProt returned no proteins", (r?.first?.size ?: 0) > 0)
    }

    @Test
    fun chemblSearch() = runBlocking {
        Assume.assumeTrue(live())
        val r = ChemblAdapter.fetchMolecules("imatinib", 5)
        println("ChEMBL: ${r?.first?.size} molecules")
        assertTrue("ChEMBL returned no molecules", (r?.first?.size ?: 0) > 0)
    }

    @Test
    fun gdcSearch() = runBlocking {
        Assume.assumeTrue(live())
        val r = GdcAdapter.searchProjects("breast", 5)
        println("GDC: ${r.size} projects")
        assertTrue("GDC returned no projects", r.isNotEmpty())
    }

    @Test
    fun gnomadVariant() = runBlocking {
        Assume.assumeTrue(live())
        val v = GnomadAdapter.fetchVariant("17-43045708-G-A")
        println("gnomAD: ${v?.variantId} af=${v?.combinedAf()}")
    }
}

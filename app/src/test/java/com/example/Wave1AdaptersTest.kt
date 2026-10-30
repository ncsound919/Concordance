package com.example

import com.example.concordance.integration.api.BiorxivAdapter
import com.example.concordance.integration.api.ChemblAdapter
import com.example.concordance.integration.api.EUtilsAdapter
import com.example.concordance.integration.api.GdcAdapter
import com.example.concordance.integration.api.GnomadAdapter
import com.example.concordance.integration.api.IntegrationRegistry
import com.example.concordance.integration.api.SemanticScholarAdapter
import com.example.concordance.integration.api.UniProtAdapter
import com.example.concordance.integration.api.UnpaywallAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fixture tests for the Wave-1 (2026-10) evidence-intake adapters.
 * Parse functions are pure, so no network is involved; consistency for the
 * new registry entries (rotation pool registration, OPEN⇒keyless) is covered
 * by the shared checks in IntegrationAdapterTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Wave1AdaptersTest {

    private val prov = com.example.concordance.integration.api.Provenance(
        providerId = "test",
        endpoint = "https://test",
        query = "q",
        httpStatus = 200,
        retrievedUtc = "2026-10-21T00:00:00Z",
        responseSha256 = "abc",
        licenseClass = com.example.concordance.integration.rotation.LicenseClass.OPEN,
        elapsedMs = 10
    )

    // ---- Semantic Scholar -------------------------------------------------

    @Test
    fun s2SearchRecordsAreParsed() {
        val json = """
            {
              "data": [
                {
                  "paperId": "p1",
                  "title": "Example oncology claim study",
                  "year": 2025,
                  "venue": "Nature Medicine",
                  "citationCount": 12,
                  "isOpenAccess": true,
                  "externalIds": { "DOI": "10.1038/ex1", "PubMed": "123" },
                  "authors": [ { "name": "A" }, { "name": "B" }, { "name": "C" }, { "name": "D" }, { "name": "E" } ]
                },
                { "title": "no paperId — dropped" }
              ]
            }
        """.trimIndent()

        val records = SemanticScholarAdapter.parseSearch(json, prov)

        assertEquals(1, records.size)
        val r = records.first()
        assertEquals("S2:p1", r.externalId)
        assertEquals("10.1038/ex1", r.doi)
        assertEquals("123", r.pmid)
        assertEquals("https://doi.org/10.1038/ex1", r.url)
        assertEquals("Semantic Scholar", r.source)
        assertEquals("A, B, C et al.", r.authors)
        assertEquals(12, r.citationCount)
    }

    @Test
    fun s2CitationsUnwrapTheCitingPaper() {
        val json = """
            { "data": [ { "citingPaper": { "paperId": "c1", "title": "Follow-up", "externalIds": {} } } ] }
        """.trimIndent()

        val records = SemanticScholarAdapter.parseCitations(json, prov)

        assertEquals("S2:c1", records.first().externalId)
        assertEquals("Follow-up", records.first().title)
        assertTrue(records.first().url!!.contains("semanticscholar.org/paper/c1"))
    }

    @Test
    fun s2ParsesMalformedResponsesSafely() {
        assertTrue(SemanticScholarAdapter.parseSearch("not json", prov).isEmpty())
        assertTrue(SemanticScholarAdapter.parseCitations("{}", prov).isEmpty())
    }

    // ---- Unpaywall --------------------------------------------------------

    @Test
    fun unpaywallRecordIsParsed() {
        val json = """
            {
              "doi": "10.1038/ex1",
              "title": "Example study",
              "journal_name": "Nature Medicine",
              "year": 2025,
              "is_oa": true,
              "oa_status": "gold",
              "best_oa_location": { "url": "https://example.org/pdf", "url_for_pdf": "https://example.org/direct.pdf" },
              "z_authors": [ { "given": "Ada", "family": "Lovelace" } ]
            }
        """.trimIndent()

        val rec = UnpaywallAdapter.parseRecord(json, prov)

        assertEquals("10.1038/ex1", rec!!.doi)
        assertEquals("gold", rec.oaStatus)
        assertEquals("https://example.org/pdf", rec.bestOaUrl)
        assertEquals("https://example.org/direct.pdf", rec.bestOaPdfUrl)
        assertEquals("Ada Lovelace", rec.authors)
        assertTrue(rec.isOa)
        assertEquals("Unpaywall:10.1038/ex1", rec.toNormalizedRecord().externalId)
    }

    @Test
    fun unpaywallDoiNormalization() {
        assertEquals("10.1/x", UnpaywallAdapter.normalizeDoi("doi:10.1/x"))
        assertEquals("10.1/x", UnpaywallAdapter.normalizeDoi("https://doi.org/10.1/x"))
        assertFalse(UnpaywallAdapter.looksLikeDoi("a plain keyword"))
        assertTrue(UnpaywallAdapter.looksLikeDoi("10.1038/ex1"))
    }

    // ---- E-utilities (PubMed / PMC) ----------------------------------------

    @Test
    fun eutilsSearchIdsAreParsed() {
        val json = """{ "esearchresult": { "idlist": [ "123", "456" ] } }"""

        assertEquals(listOf("123", "456"), EUtilsAdapter.parseSearchIds(json))
        assertTrue(EUtilsAdapter.parseSearchIds("{}").isEmpty())
    }

    @Test
    fun eutilsSummariesAreParsed() {
        val json = """
            {
              "result": {
                "uids": [ "123" ],
                "123": {
                  "title": "Example paper",
                  "fulljournalname": "Journal of Example",
                  "pubdate": "2025 Mar 4",
                  "authors": [ { "name": "A" }, { "name": "B" } ],
                  "articleids": [
                    { "idtype": "pmcid", "value": "PMC1234567" },
                    { "idtype": "doi", "value": "10.1/x" }
                  ]
                }
              }
            }
        """.trimIndent()

        val records = EUtilsAdapter.parseSummaries(json, prov)

        assertEquals(1, records.size)
        val r = records.first()
        assertEquals("PubMed:123", r.externalId)
        assertEquals("10.1/x", r.doi)
        assertEquals("123", r.pmid)
        assertEquals("Journal of Example", r.journal)
        assertEquals(2025, r.year)
        assertEquals(true, r.isOpenAccess)
        assertEquals("https://pmc.ncbi.nlm.nih.gov/articles/PMC1234567/", r.url)
    }

    // ---- bioRxiv / medRxiv --------------------------------------------------

    @Test
    fun preprintVersionsAreSortedNewestFirst() {
        val json = """
            {
              "collection": [
                { "doi": "10.1101/x.2", "version": 2, "title": "v2", "authors": "B", "date": "2025-06-01", "status": "not_published" },
                { "doi": "10.1101/x.1", "version": 1, "title": "v1", "authors": "A", "date": "2024-01-01", "published": "Nature Medicine", "status": "" }
              ]
            }
        """.trimIndent()

        val versions = BiorxivAdapter.parseVersions(json, "biorxiv")

        assertEquals(listOf(2, 1), versions.map { it.version })
        assertEquals("Nature Medicine", versions[1].publishedJournal)
        assertTrue(versions.all { it.server == "biorxiv" })
    }

    @Test
    fun preprintVersionsParseMalformedSafely() {
        assertTrue(BiorxivAdapter.parseVersions("{}", "biorxiv").isEmpty())
        assertTrue(BiorxivAdapter.parseVersions("{'collection': 'not-an-array'}", "biorxiv").isEmpty())
    }

    // ---- ChEMBL -------------------------------------------------------------

    @Test
    fun chemblMoleculesAreParsed() {
        val json = """
            {
              "molecules": [
                { "molecule_chembl_id": "CHEMBL123", "pref_name": "Imatinib", "molecule_type": "Small molecule", "max_phase": 4,
                  "molecule_properties": { "full_molformula": "C29H31N7O" } },
                { "pref_name": "no id — dropped" }
              ]
            }
        """.trimIndent()

        val molecules = ChemblAdapter.parseMolecules(json)

        assertEquals(1, molecules.size)
        val m = molecules.first()
        assertEquals("CHEMBL123", m.chemblId)
        assertEquals("Imatinib", m.prefName)
        assertEquals(4, m.maxPhase)
        assertEquals("C29H31N7O", m.molecularFormula)
    }

    @Test
    fun chemblMechanismsAreParsed() {
        val json = """
            {
              "mechanism_of_actions": [
                {
                  "molecule_chembl_id": "CHEMBL123",
                  "mechanism_of_action": "Bcr-Abl tyrosine kinase inhibitor",
                  "action_type": "INHIBITOR",
                  "target_chembl_id": "CHEMBLT1",
                  "target_pref_name": "Bcr-Abl"
                },
                { "molecule_chembl_id": "CHEMBL123", "mechanism_of_action": "", "action_type": null }
              ]
            }
        """.trimIndent()

        val mechanisms = ChemblAdapter.parseMechanisms(json)

        assertEquals(1, mechanisms.size)
        val m = mechanisms.first()
        assertEquals("Bcr-Abl tyrosine kinase inhibitor", m.mechanism)
        assertEquals("INHIBITOR", m.actionType)
        assertEquals("CHEMBLT1", m.targetChEMBLId)
        assertEquals("Bcr-Abl", m.targetName)
    }

    // ---- UniProt ------------------------------------------------------------

    @Test
    fun uniprotProteinsAreParsed() {
        val json = """
            {
              "results": [
                {
                  "primaryAccession": "P04637",
                  "uniProtkbId": "P53_HUMAN",
                  "proteinDescription": {
                    "recommendedProteinNames": [ { "fullName": { "value": "Cellular tumor antigen p53" } } ]
                  },
                  "genes": [ { "geneName": { "value": "TP53" } } ],
                  "organism": { "scientificName": "Homo sapiens" },
                  "comments": [
                    { "commentType": "FUNCTION", "texts": [ { "value": "Tumor suppressor." } ] },
                    { "commentType": "PTM", "texts": [ { "value": "ignored" } ] }
                  ],
                  "uniProtKBCrossReferences": [
                    { "database": "ChEMBL" }, { "database": "PDB" }, { "database": "ChEMBL" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val proteins = UniProtAdapter.parseProteins(json)

        assertEquals(1, proteins.size)
        val p = proteins.first()
        assertEquals("P04637", p.accession)
        assertEquals("Cellular tumor antigen p53", p.proteinName)
        assertEquals("TP53", p.geneSymbol)
        assertEquals("Homo sapiens", p.organism)
        assertEquals("Tumor suppressor.", p.functionSummary)
        assertEquals(listOf("ChEMBL", "PDB"), p.crossReferencedDatabases)
    }

    @Test
    fun uniprotParsesMalformedSafely() {
        assertTrue(UniProtAdapter.parseProteins("not json").isEmpty())
    }

    // ---- gnomAD ---------------------------------------------------------------

    @Test
    fun gnomadVariantIsParsedWithStats() {
        val json = """
            {
              "data": {
                "variant": {
                  "id": "1-55516888-G-A",
                  "reference_genome": "GRCh38",
                  "exome": { "ac": 10, "an": 500, "af": 0.02, "populations": [ { "id": "afr", "ac": 8, "an": 200, "af": 0.04 } ] },
                  "genome": { "ac": 90, "an": 9500, "af": 0.01, "populations": [ { "id": "afr", "ac": 70, "an": 4000, "af": 0.02 } ] },
                  "transcript_consequences": [
                    { "gene_symbol": "BRCA1", "consequence_terms": [ "missense_variant", "moderate" ] }
                  ]
                }
              }
            }
        """.trimIndent()

        val v = GnomadAdapter.parseVariant(json)!!

        assertEquals("1-55516888-G-A", v.variantId)
        assertEquals("GRCh38", v.referenceGenome)
        assertEquals(10L, v.exomeAc)
        assertEquals(0.02, v.exomeAf!!, 1e-9)
        assertEquals(90L, v.genomeAc)
        // Combined AF: (10+90)/(500+9500)
        assertEquals(0.01, v.combinedAf()!!, 1e-9)
        assertEquals(1, v.populations.size)
        assertEquals(1, v.consequences.size)
        assertEquals("BRCA1", v.consequences.first().geneSymbol)
        assertTrue(v.summaryLine().contains("BRCA1"))
    }

    @Test
    fun gnomadVariantParsesMalformedSafely() {
        assertNull(GnomadAdapter.parseVariant("{}"))
        assertNull(GnomadAdapter.parseVariant("not json"))
    }

    // ---- NCI GDC ---------------------------------------------------------------

    @Test
    fun gdcProjectsAreParsed() {
        val json = """
            {
              "data": {
                "hits": [
                  {
                    "project_id": "TCGA-BRCA",
                    "name": "Breast Invasive Carcinoma",
                    "disease_type": [ "Breast Cancer" ],
                    "primary_site": [ "Breast" ],
                    "summary": { "case_count": 1000 }
                  },
                  { "name": "no project_id — dropped" }
                ]
              }
            }
        """.trimIndent()

        val projects = GdcAdapter.parseProjects(json)

        assertEquals(1, projects.size)
        val p = projects.first()
        assertEquals("TCGA-BRCA", p.projectId)
        assertEquals("Breast Invasive Carcinoma", p.name)
        assertEquals("Breast Cancer", p.diseaseType)
        assertEquals(listOf("Breast"), p.primarySites)
        assertEquals(1000, p.caseCount)
    }

    @Test
    fun gdcParsesMalformedSafely() {
        assertTrue(GdcAdapter.parseProjects("{}").isEmpty())
    }

    // ---- Cross-cutting ----------------------------------------------------------

    @Test
    fun wave1RegistryLookupsDoNotReachTheNetworkOnBadInput() = runBlocking {
        assertNull(IntegrationRegistry.lookupOpenAccess("not a doi"))
        assertNull(IntegrationRegistry.variantFrequency(""))
        assertTrue(IntegrationRegistry.fetchPreprintVersions("") .isEmpty())
        assertTrue(IntegrationRegistry.searchMolecules("").isEmpty())
        assertTrue(IntegrationRegistry.searchProteins("").isEmpty())
        assertTrue(IntegrationRegistry.fetchCitingPapers("").isEmpty())
    }
}

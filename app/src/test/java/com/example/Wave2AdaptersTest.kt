package com.example

import com.example.concordance.integration.api.FigshareAdapter
import com.example.concordance.integration.api.OsfAdapter
import com.example.concordance.integration.api.PubPeerAdapter
import com.example.concordance.integration.api.ZoteroAdapter
import com.example.concordance.integration.retraction.RetractionCheck
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Wave2AdaptersTest {

    // ---- PubPeer ----------------------------------------------------------

    @Test
    fun pubpeerPublicationsAreParsed() {
        val json = """
            {
              "publications": [
                {
                  "id": "pub-1",
                  "title": "Example oncology paper",
                  "doi": "10.1038/ex1",
                  "pmid": "12345",
                  "comments": [
                    { "id": "c1", "author_name": "Reviewer A", "text": "Figure 2 looks off.", "created_at": "2025-01-02", "comment_type": "concern" },
                    { "id": "c2", "author": "Reviewer B", "text": "Replicated." }
                  ]
                },
                { "title": "no id, still parsed" }
              ]
            }
        """.trimIndent()

        val pubs = PubPeerAdapter.parsePublications(json)

        assertEquals(2, pubs.size)
        val p = pubs.first()
        assertEquals("pub-1", p.id)
        assertEquals("10.1038/ex1", p.doi)
        assertEquals("12345", p.pmid)
        assertEquals(2, p.comments.size)
        assertEquals("Reviewer A", p.comments[0].author)
        assertEquals("concern", p.comments[0].type)
        assertEquals("Reviewer B", p.comments[1].author)
    }

    @Test
    fun pubpeerParsesMalformedSafely() {
        assertTrue(PubPeerAdapter.parsePublications("not json").isEmpty())
        assertTrue(PubPeerAdapter.parsePublications("{}").isEmpty())
    }

    // ---- OSF --------------------------------------------------------------

    @Test
    fun osfNodesAreParsed() {
        val json = """
            {
              "data": [
                {
                  "id": "abc12",
                  "attributes": {
                    "title": "Breast cohort dataset",
                    "description": "Counts and metadata.",
                    "category": "project",
                    "date_created": "2025-03-01T00:00:00",
                    "public": true
                  }
                },
                { "attributes": { "title": "no id — dropped" } }
              ]
            }
        """.trimIndent()

        val nodes = OsfAdapter.parseNodes(json)

        assertEquals(1, nodes.size)
        val n = nodes.first()
        assertEquals("abc12", n.id)
        assertEquals("Breast cohort dataset", n.title)
        assertEquals("project", n.category)
        assertEquals(true, n.public)
        assertEquals(2025, n.dateCreated?.take(4)?.toInt())
    }

    @Test
    fun osfFilesAreParsed() {
        val json = """
            {
              "data": [
                {
                  "id": "file-1",
                  "attributes": { "name": "counts.csv", "kind": "file", "size": 1024, "download": "https://osf.io/download/x" }
                }
              ]
            }
        """.trimIndent()

        val files = OsfAdapter.parseFiles(json)

        assertEquals(1, files.size)
        assertEquals("counts.csv", files.first().name)
        assertEquals(1024L, files.first().size)
        assertEquals("https://osf.io/download/x", files.first().downloadUrl)
    }

    @Test
    fun osfParsesMalformedSafely() {
        assertTrue(OsfAdapter.parseNodes("not json").isEmpty())
        assertTrue(OsfAdapter.parseFiles("{}").isEmpty())
    }

    // ---- Figshare ---------------------------------------------------------

    @Test
    fun figshareArticlesAreParsed() {
        val json = """
            [
              {
                "id": 12345,
                "title": "Aggregate dataset",
                "description": "Pooled counts.",
                "doi": "10.6084/m9.figshare.12345",
                "published_date": "2025-04-01",
                "url": "https://figshare.com/articles/12345",
                "tags": ["oncology", "counts"],
                "categories": [ { "name": "Medicine" } ]
              },
              { "title": "no id — dropped" }
            ]
        """.trimIndent()

        val articles = FigshareAdapter.parseArticles(json)

        assertEquals(1, articles.size)
        val a = articles.first()
        assertEquals(12345L, a.id)
        assertEquals("10.6084/m9.figshare.12345", a.doi)
        assertEquals(listOf("oncology", "counts"), a.tags)
        assertEquals(listOf("Medicine"), a.categories)
    }

    @Test
    fun figshareSingleArticleIsParsed() {
        val json = """
            { "id": 99, "title": "Single", "description": "d", "doi": null, "published_date": "2024", "url": null, "tags": [], "categories": [] }
        """.trimIndent()

        val a = FigshareAdapter.parseArticle(json)!!

        assertEquals(99L, a.id)
        assertEquals("Single", a.title)
        assertEquals(null, a.doi)
    }

    @Test
    fun figshareParsesMalformedSafely() {
        assertTrue(FigshareAdapter.parseArticles("not json").isEmpty())
        assertEquals(null, FigshareAdapter.parseArticle("not json"))
    }

    // ---- Zotero -----------------------------------------------------------

    @Test
    fun zoteroItemsAreParsed() {
        val json = """
            [
              {
                "key": "KEY1",
                "version": 5,
                "data": {
                  "itemType": "journalArticle",
                  "title": "Zotero paper",
                  "creators": [
                    { "creatorType": "author", "firstName": "Ada", "lastName": "Lovelace" },
                    { "creatorType": "author", "lastName": "Turing" }
                  ],
                  "publicationTitle": "Nature",
                  "date": "2025-02-03",
                  "DOI": "10.1038/z",
                  "url": "https://example.org/z",
                  "abstractNote": "An abstract.",
                  "tags": [ { "tag": "oncology" }, { "tag": "reviewed" } ]
                }
              },
              { "data": { "title": "" } }
            ]
        """.trimIndent()

        val items = ZoteroAdapter.parseItems(json)

        assertEquals(1, items.size)
        val it = items.first()
        assertEquals("KEY1", it.key)
        assertEquals(5, it.version)
        assertEquals("Zotero paper", it.title)
        assertEquals("Ada Lovelace, Turing", it.creators)
        assertEquals("Nature", it.publicationTitle)
        assertEquals("10.1038/z", it.doi)
        assertEquals(listOf("oncology", "reviewed"), it.tags)
    }

    @Test
    fun zoteroParsesMalformedSafely() {
        assertTrue(ZoteroAdapter.parseItems("not json").isEmpty())
        assertTrue(ZoteroAdapter.parseItems("[]").isEmpty())
    }

    // ---- RetractionCheck ---------------------------------------------------

    @Test
    fun retractionFlagBodyIsWellFormed() {
        val body = RetractionCheck.flagBody("capsule", "cap-1", "source retracted")
        val o = JSONObject(body)
        assertEquals("capsule", o.getString("artifact_type"))
        assertEquals("cap-1", o.getString("artifact_id"))
        assertEquals("source retracted", o.getString("reason"))
    }
}

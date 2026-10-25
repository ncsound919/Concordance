package com.example

import com.example.concordance.local.InstallableModel
import com.example.concordance.local.LocalModelCatalog
import com.example.concordance.local.ModelDownloadResult
import com.example.concordance.local.ModelDownloader
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ModelDownloaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var directory: File
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        directory = temp.newFolder("models")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { String.format("%02x", it) }

    private fun model(bytes: ByteArray, name: String = "Qwen3.5-0.8B-Q4_K_M.gguf") =
        InstallableModel(
            id = "test",
            fileName = name,
            displayName = "test",
            description = "test",
            sizeBytes = bytes.size.toLong(),
            sha256 = sha256(bytes),
            url = server.url("/model.gguf").toString(),
            source = "test",
            license = "test"
        )

    @Test
    fun downloadsAndVerifiesTheFile() = runBlocking {
        val bytes = ByteArray(64 * 1024) { (it % 251).toByte() }
        val target = model(bytes)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))

        val result = ModelDownloader(client).download(target, directory)

        assertTrue("expected success, got $result", result is ModelDownloadResult.Success)
        val installed = File(directory, target.fileName)
        assertEquals(bytes.size.toLong(), installed.length())
        assertTrue(bytes.contentEquals(installed.readBytes()))
        assertFalse("part file must not survive a successful download", installed.partFile().exists())
        assertNull(ModelDownloader(client).verify(installed, target))
    }

    @Test
    fun rejectsBytesThatDoNotMatchThePublishedHash() = runBlocking {
        val published = ByteArray(4096) { 1 }
        val served = ByteArray(4096) { 2 }
        val target = model(published)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(served)))

        val result = ModelDownloader(client).download(target, directory)

        assertTrue("expected failure, got $result", result is ModelDownloadResult.Failure)
        assertTrue((result as ModelDownloadResult.Failure).message.contains("SHA-256"))
        assertFalse(File(directory, target.fileName).exists())
        assertFalse(File(directory, target.fileName).partFile().exists())
    }

    @Test
    fun resumesAPartialDownloadWithARangeRequest() = runBlocking {
        val bytes = ByteArray(8 * 1024) { (it % 97).toByte() }
        val target = model(bytes)
        val prefix = bytes.size / 2
        val part = File(directory, target.fileName).partFile()
        directory.mkdirs()
        part.writeBytes(bytes.copyOfRange(0, prefix))

        val remainder = okio.Buffer().write(bytes, prefix, bytes.size - prefix)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .addHeader("Content-Range", "bytes $prefix-${bytes.size - 1}/${bytes.size}")
                .setBody(remainder)
        )

        val result = ModelDownloader(client).download(target, directory)

        assertTrue("expected success, got $result", result is ModelDownloadResult.Success)
        assertTrue(bytes.contentEquals(File(directory, target.fileName).readBytes()))
        val request = server.takeRequest()
        assertEquals("bytes=$prefix-", request.getHeader("Range"))
        assertEquals("identity", request.getHeader("Accept-Encoding"))
    }

    @Test
    fun restartsWhenTheServerIgnoresTheRangeHeader() = runBlocking {
        val bytes = ByteArray(4 * 1024) { (it % 13).toByte() }
        val target = model(bytes)
        val part = File(directory, target.fileName).partFile()
        directory.mkdirs()
        part.writeBytes(bytes.copyOfRange(0, 1024))

        // 200 rather than 206: the server sent the whole file from the start.
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))

        val result = ModelDownloader(client).download(target, directory)

        assertTrue("expected success, got $result", result is ModelDownloadResult.Success)
        assertTrue(bytes.contentEquals(File(directory, target.fileName).readBytes()))
    }

    @Test
    fun reportsAnIncompleteBodyInsteadOfInstallingIt() = runBlocking {
        val bytes = ByteArray(4096) { 7 }
        val target = model(bytes)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes, 0, 1000)))

        val result = ModelDownloader(client).download(target, directory)

        assertTrue("expected failure, got $result", result is ModelDownloadResult.Failure)
        assertFalse(File(directory, target.fileName).exists())
    }

    @Test
    fun catalogEntriesAreUniqueAndWellFormed() {
        val entries = LocalModelCatalog.entries
        assertEquals(entries.size, entries.map { it.fileName }.toSet().size)
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        entries.forEach { model ->
            assertTrue(model.sizeBytes > 0)
            assertEquals(64, model.sha256.length)
            assertTrue(model.url.endsWith(model.fileName))
            assertTrue(model.displayName.isNotBlank())
        }
        // The installed tier is Qwen3.5, not the superseded 2.5 generation.
        entries.forEach { assertTrue(it.displayName, it.displayName.contains("Qwen3.5")) }
        assertNotNull(LocalModelCatalog.forFileName(LocalModelCatalog.QWEN_4B.fileName))
    }

    private fun File.partFile(): File = File(parentFile, "$name.part")
}

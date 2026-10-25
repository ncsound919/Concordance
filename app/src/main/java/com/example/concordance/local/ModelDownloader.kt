package com.example.concordance.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Result of a model download; [Failure] carries a sentence the UI can show. */
sealed interface ModelDownloadResult {
    data class Success(val file: File) : ModelDownloadResult
    data class Failure(val message: String) : ModelDownloadResult
}

/**
 * Downloads a GGUF into the app's model directory and verifies it.
 *
 * The file is written to `<name>.gguf.part` and renamed only after the SHA-256
 * matches the publisher's checksum, so a killed process or a corrupted transfer
 * can never leave a half-file that the app would try to load. A leftover part
 * resumes with an HTTP Range request; servers that ignore Range restart cleanly.
 *
 * No Android types here: this is exercised by plain JVM tests with MockWebServer.
 */
class ModelDownloader(
    private val client: OkHttpClient = defaultClient()
) {

    suspend fun download(
        model: InstallableModel,
        directory: File,
        onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> }
    ): ModelDownloadResult = withContext(Dispatchers.IO) {
        directory.mkdirs()
        onProgress(0L, model.sizeBytes)

        val target = File(directory, model.fileName)
        if (target.isFile && target.length() == model.sizeBytes) {
            val problem = verify(target, model)
            if (problem == null) return@withContext ModelDownloadResult.Success(target)
            // Same size, wrong bytes: it is a bad copy, not a usable model.
            target.delete()
        } else if (target.isFile) {
            target.delete()
        }

        val part = partFile(directory, model)
        if (part.isFile && part.length() > model.sizeBytes) part.delete()
        if (part.isFile && part.length() == model.sizeBytes) {
            // A previous attempt finished writing but died before the rename.
            val problem = verify(part, model)
            if (problem == null) {
                if (target.exists()) target.delete()
                if (part.renameTo(target)) {
                    onProgress(model.sizeBytes, model.sizeBytes)
                    return@withContext ModelDownloadResult.Success(target)
                }
            }
            part.delete()
        }

        var existing = if (part.isFile) part.length() else 0L
        var digest = MessageDigest.getInstance("SHA-256")
        if (existing > 0) {
            try {
                hashInto(part, digest)
            } catch (e: IOException) {
                part.delete()
                existing = 0
                digest = MessageDigest.getInstance("SHA-256")
            }
            onProgress(existing, model.sizeBytes)
        }

        val request = Request.Builder()
            .url(model.url)
            // Byte ranges and hashes are over the raw file, not a gzip stream.
            .header("Accept-Encoding", "identity")
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (existing > 0 && response.code == 200) {
                    // The server ignored Range. Start over rather than appending a
                    // whole second copy.
                    part.delete()
                    existing = 0
                    digest = MessageDigest.getInstance("SHA-256")
                } else if (existing > 0 && response.code == 206) {
                    val contentRange = response.header("Content-Range")
                    if (contentRange != null && !contentRange.contains("$existing-")) {
                        return@withContext ModelDownloadResult.Failure(
                            "server resumed at the wrong offset ($contentRange)"
                        )
                    }
                } else if (response.code == 416) {
                    part.delete()
                    return@withContext ModelDownloadResult.Failure(
                        "the partial file no longer matches the remote model; download again"
                    )
                } else if (response.code !in 200..299) {
                    return@withContext ModelDownloadResult.Failure("HTTP ${response.code}")
                }

                val body = response.body
                    ?: return@withContext ModelDownloadResult.Failure("empty response")
                val appending = response.code == 206 && existing > 0
                var written = if (appending) existing else 0L
                var lastReport = 0L

                FileOutputStream(part, appending).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (written + read > model.sizeBytes) {
                                throw IOException(
                                    "server sent more than the expected ${model.sizeBytes} bytes"
                                )
                            }
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read
                            val now = System.nanoTime()
                            if (now - lastReport >= PROGRESS_INTERVAL_NS || written == model.sizeBytes) {
                                onProgress(written, model.sizeBytes)
                                lastReport = now
                            }
                        }
                    }
                }

                if (written != model.sizeBytes || part.length() != model.sizeBytes) {
                    return@withContext ModelDownloadResult.Failure(
                        "incomplete download (${part.length()} of ${model.sizeBytes} bytes)"
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (part.length() > model.sizeBytes) part.delete()
            return@withContext ModelDownloadResult.Failure(
                e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            )
        }

        val actual = hex(digest.digest())
        if (!actual.equals(model.sha256, ignoreCase = true)) {
            part.delete()
            return@withContext ModelDownloadResult.Failure(
                "SHA-256 mismatch (got ${actual.take(12)}…); the download was discarded"
            )
        }

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            // Some emulated storage refuses a rename across providers; copy instead.
            try {
                part.copyTo(target, overwrite = true)
                part.delete()
            } catch (e: IOException) {
                part.delete()
                return@withContext ModelDownloadResult.Failure(
                    "could not move the verified file into place: ${e.message}"
                )
            }
        }
        onProgress(model.sizeBytes, model.sizeBytes)
        ModelDownloadResult.Success(target)
    }

    /**
     * Re-hash an installed file. Returns null when it matches the catalog, or a
     * sentence naming the first problem. Blocking; call from IO.
     */
    suspend fun verify(file: File, model: InstallableModel): String? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext "not installed"
        if (file.length() != model.sizeBytes) {
            return@withContext "size is ${file.length()} bytes, expected ${model.sizeBytes}"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            hashInto(file, digest)
        } catch (e: IOException) {
            return@withContext "could not read the file: ${e.message}"
        }
        val actual = hex(digest.digest())
        if (!actual.equals(model.sha256, ignoreCase = true)) {
            "SHA-256 is ${actual.take(12)}…, expected ${model.sha256.take(12)}…"
        } else {
            null
        }
    }

    private fun hashInto(file: File, digest: MessageDigest) {
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
    }

    companion object {
        private const val BUFFER_BYTES = 256 * 1024
        private const val PROGRESS_INTERVAL_NS = 100_000_000L

        fun partFile(directory: File, model: InstallableModel): File =
            File(directory, "${model.fileName}.part")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        private fun hex(bytes: ByteArray): String =
            bytes.joinToString("") { String.format(Locale.US, "%02x", it) }
    }
}

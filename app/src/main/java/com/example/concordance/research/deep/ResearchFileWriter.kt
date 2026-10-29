package com.example.concordance.research.deep

import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Writes a research round's artifacts to a directory and produces a verifiable manifest.
 *
 * The manifest is the contract: `paper.md` may not contain a number that is not in `results.json`,
 * and every file's sha256 must match the manifest on re-read. This is the on-device equivalent
 * of the agent's `paper_chain.py` signed receipt.
 */
object ResearchFileWriter {

    data class WrittenFile(val name: String, val sha256: String, val bytes: Int)

    data class Manifest(
        val runId: String,
        val createdAtUtc: String,
        val files: List<WrittenFile>,
        val receipt: String,
    )

    /**
     * Writes every entry of [files] into [runDir] and returns the manifest. [files] is a map
     * from filename to content. The manifest itself is written as `manifest.json`.
     *
     * A missing directory is created. An existing file is overwritten, which is why this must
     * only be called for a fresh run id.
     */
    fun write(runDir: File, runId: String, createdAtUtc: String, files: Map<String, String>, hmacKey: String? = null): Manifest {
        runDir.mkdirs()
        val written = files.map { (name, content) ->
            validateName(name)
            val file = File(runDir, name)
            file.writeText(content, Charsets.UTF_8)
            WrittenFile(name = name, sha256 = sha256(content), bytes = content.toByteArray(Charsets.UTF_8).size)
        }.sortedBy { it.name }

        val manifestBody = buildString {
            append("research-manifest/v1\n")
            append("runId=").append(runId).append("\n")
            append("createdAtUtc=").append(createdAtUtc).append("\n")
            written.forEach { append(it.name).append("=").append(it.sha256).append("\n") }
        }
        val receipt = if (hmacKey.isNullOrBlank()) {
            sha256(manifestBody)
        } else {
            hmacSha256(hmacKey, manifestBody)
        }

        val manifest = Manifest(runId = runId, createdAtUtc = createdAtUtc, files = written, receipt = receipt)
        File(runDir, "manifest.json").writeText(manifestJson(manifest), Charsets.UTF_8)
        return manifest
    }

    /** Re-reads [runDir]/manifest.json and verifies every file. Returns the problems found. */
    fun verify(runDir: File): List<String> {
        val manifestFile = File(runDir, "manifest.json")
        if (!manifestFile.isFile) return listOf("manifest.json is missing")
        val manifest = org.json.JSONObject(manifestFile.readText(Charsets.UTF_8))
        val files = manifest.getJSONArray("files")
        val problems = mutableListOf<String>()
        for (i in 0 until files.length()) {
            val o = files.getJSONObject(i)
            val name = o.getString("name")
            val expected = o.getString("sha256")
            val file = File(runDir, name)
            if (!file.isFile) {
                problems += "$name is missing"
                continue
            }
            val actual = sha256(file.readText(Charsets.UTF_8))
            if (actual != expected) problems += "$name hash mismatch"
        }
        return problems
    }

    private fun validateName(name: String) {
        require(name.isNotBlank()) { "file name is blank" }
        require(!name.contains("..") && !name.startsWith("/")) { "unsafe file name: $name" }
        require(!name.contains("\"")) { "file name contains a quote: $name" }
        require(name.endsWith(".md") || name.endsWith(".json")) { "unsupported file type: $name" }
    }

    private fun manifestJson(manifest: Manifest): String {
        val files = manifest.files.joinToString(",\n") { f ->
            """{"name":"${f.name}","sha256":"${f.sha256}","bytes":${f.bytes}}"""
        }
        return """
            {
              "runId": "${manifest.runId}",
              "createdAtUtc": "${manifest.createdAtUtc}",
              "receipt": "${manifest.receipt}",
              "files": [$files]
            }
        """.trimIndent()
    }

    fun sha256(text: String): String = sha256Bytes(text.toByteArray(Charsets.UTF_8))

    private fun sha256Bytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256(key: String, text: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

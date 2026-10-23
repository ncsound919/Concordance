package com.example.concordance.integration.agent

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** A figure/PDF chosen or captured by the operator, ready to upload. */
data class AgentUpload(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        other is AgentUpload && fileName == other.fileName && contentType == other.contentType &&
            bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = fileName.hashCode() * 31 + bytes.contentHashCode()
}

/**
 * Reads a picked/captured document into memory, refusing anything the agent would reject
 * (unsupported type, or larger than the agent's own limit) so the operator gets the answer
 * on the phone instead of after a wasted upload.
 */
object AgentUploads {

    /** Must stay in step with the agent's AGENT_MAX_UPLOAD_MB default. */
    const val MAX_BYTES = 25 * 1024 * 1024

    val ACCEPTED_TYPES = listOf(
        "image/png", "image/jpeg", "image/webp", "image/bmp", "image/tiff", "application/pdf"
    )

    /** Result of a read: either an upload, or the reason it cannot be used. */
    sealed interface Result {
        data class Ok(val upload: AgentUpload) : Result
        data class Rejected(val reason: String) : Result
    }

    fun read(context: Context, uri: Uri): Result {
        val resolver = context.contentResolver
        val mime = (resolver.getType(uri) ?: "").lowercase()
        if (mime.isNotEmpty() && mime !in ACCEPTED_TYPES) {
            return Result.Rejected("Unsupported file type '$mime'. Use a PNG/JPEG/WebP/TIFF image or a PDF.")
        }

        val bytes = try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return Result.Rejected("Could not read the selected file.")

        if (bytes.isEmpty()) return Result.Rejected("The selected file is empty.")
        if (bytes.size > MAX_BYTES) {
            return Result.Rejected(
                "That file is ${bytes.size / (1024 * 1024)} MB; the agent accepts up to ${MAX_BYTES / (1024 * 1024)} MB."
            )
        }

        val name = displayName(context, uri) ?: "figure.png"
        return Result.Ok(
            AgentUpload(
                fileName = name,
                contentType = mime.ifEmpty { guessFromName(name) },
                bytes = bytes
            )
        )
    }

    private fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()

    private fun guessFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "tif", "tiff" -> "image/tiff"
        "pdf" -> "application/pdf"
        else -> "image/png"
    }
}

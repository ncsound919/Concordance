package com.example.concordance.links

import android.net.Uri

/**
 * Incoming link handling for Concordance.
 *
 * Only two forms are accepted, and both must name an anchor:
 *
 *   - the verified App Link `https://<hub>/concordance/anchor/<id>`, which
 *     Android only delivers after checking assetlinks.json for this package
 *     and signing certificate;
 *   - `concordance://anchor/<id>`, kept for in-app navigation from a browser
 *     fallback. It is not used for anything sensitive.
 *
 * Anything else — another host, another path, `javascript:`/`file:` URIs, or a
 * link carrying tokens — returns null and the activity opens normally.
 */
object ConcordanceLinks {

    const val HOST = "concordance-web.tap4500.workers.dev"
    const val SCHEME = "concordance"

    /**
     * Anchor ids are UUIDs or short slugs; anything else is refused. The first
     * character must be alphanumeric so `.` / `..` path traversal cannot pass.
     */
    private val ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")

    fun anchorId(uri: Uri?): String? {
        if (uri == null) return null

        val scheme = uri.scheme?.lowercase() ?: return null
        val segments: List<String> = uri.pathSegments.orEmpty().filter { it.isNotBlank() }

        val candidate: String? = when (scheme) {
            "https" -> {
                if (uri.host?.lowercase() != HOST) return null
                if (segments.size < 3) return null
                if (!segments[0].equals("concordance", ignoreCase = true)) return null
                if (!segments[1].equals("anchor", ignoreCase = true)) return null
                segments[2]
            }
            SCHEME -> {
                // concordance://anchor/<id> puts "anchor" in the authority slot.
                val authority = uri.host?.lowercase()
                when {
                    authority == "anchor" -> segments.firstOrNull()
                    segments.size >= 2 && segments[0].equals("anchor", ignoreCase = true) -> segments[1]
                    else -> null
                }
            }
            else -> null
        }

        val id = candidate?.trim()
        if (id.isNullOrEmpty() || !ID_PATTERN.matches(id)) return null
        return id
    }
}

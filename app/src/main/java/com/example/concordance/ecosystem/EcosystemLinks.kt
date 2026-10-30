package com.example.concordance.ecosystem

/**
 * Concordance's allow-listed deep links into the Overlay ecosystem.
 *
 * The canonical service list is `eco/wiring/registry.json`; the mesh contract
 * this app shares with the other clients is
 * `phone-apps/shared/llm-mesh/spec/llm-mesh.openapi.json`.
 *
 * A deep link is only built for a declared peer and a declared path. Anything
 * else returns null, so an untrusted string can never become a navigation.
 */
object EcosystemLinks {

    const val REGISTRY_PATH = "eco/wiring/registry.json"
    const val MESH_CONTRACT_PATH = "phone-apps/shared/llm-mesh/spec/llm-mesh.openapi.json"

    data class Peer(val id: String, val name: String, val scheme: String, val paths: Set<String>)

    data class Link(val peer: Peer, val path: String, val params: Map<String, String>)

    val PEERS: List<Peer> = listOf(
        Peer("sieve", "Sieve", "sieve", setOf("recall", "ask")),
        Peer("recourse", "Recourse", "recourse", setOf("research", "idea")),
        Peer("agent-support", "Agent Support", "agentsupport", setOf("context", "task")),
        Peer("aetherdesk", "AetherDesk", "aetherdesk", setOf("campaigns")),
        Peer("nc-field", "NC Field", "ncfield", setOf("record", "kit")),
        Peer("math-x", "Math X", "mathx", setOf("proof")),
    )

    fun peer(id: String): Peer? = PEERS.firstOrNull { it.id == id }

    /** Build a deep link, or null when the peer/path pair is not declared. */
    fun build(peerId: String, path: String, params: Map<String, String> = emptyMap()): String? {
        val target = peer(peerId) ?: return null
        val cleanPath = path.trim('/')
        if (cleanPath !in target.paths) return null
        val query = params
            .filter { (_, value) -> value.isNotEmpty() }
            .entries
            .joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return "${target.scheme}://$cleanPath" + if (query.isEmpty()) "" else "?$query"
    }

    /**
     * Parse a deep link into a peer/path/params triple. Returns null for a
     * foreign scheme, an undeclared path, or a missing path.
     */
    fun parse(raw: String): Link? {
        if (raw.isBlank() || raw.startsWith("http://") || raw.startsWith("https://")) return null
        val separator = raw.indexOf("://")
        if (separator <= 0) return null
        val scheme = raw.substring(0, separator)
        val target = PEERS.firstOrNull { it.scheme == scheme } ?: return null
        val rest = raw.substring(separator + 3)
        val withoutQuery = rest.substringBefore('?')
        val path = withoutQuery.trim('/')
        if (path.isEmpty() || path !in target.paths) return null
        val params = rest.substringAfter('?', "")
            .split("&")
            .filter { it.contains('=') }
            .associate { pair ->
                decode(pair.substringBefore('=')) to decode(pair.substringAfter('='))
            }
        return Link(target, path, params)
    }

    private fun encode(value: String): String =
        // URLEncoder is form encoding, where space becomes '+'. A deep link is a
        // URI, so use the RFC 3986 form (%20) that the link contract expects.
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun decode(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
}

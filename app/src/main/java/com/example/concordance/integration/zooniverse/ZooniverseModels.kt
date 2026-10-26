package com.example.concordance.integration.zooniverse

data class ZooniverseProject(
    val id: String,
    val slug: String,
    val title: String,
    val description: String
)

sealed interface ZooniverseResult<out T> {
    data class Ok<T>(val value: T) : ZooniverseResult<T>
    data class Unavailable(val reason: String) : ZooniverseResult<Nothing>
}

class ZooniverseClient {
    fun projectsUrl(base: String = "https://www.zooniverse.org/api", query: String = "", pageSize: Int = 10): String =
        "${base.trimEnd('/')}/projects?search=${java.net.URLEncoder.encode(query, "UTF-8")}&page_size=$pageSize"

    fun isPhiSafe(dataClass: String): Boolean = dataClass.lowercase() != "phi"
}

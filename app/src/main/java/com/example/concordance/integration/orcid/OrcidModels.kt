package com.example.concordance.integration.orcid

data class OrcidPerson(
    val orcidId: String,
    val givenName: String,
    val familyName: String,
    val fullName: String,
    val biography: String?
)

sealed interface OrcidResult<out T> {
    data class Ok<T>(val value: T) : OrcidResult<T>
    data class Unavailable(val reason: String) : OrcidResult<Nothing>
    data class Refused(val reason: String) : OrcidResult<Nothing>
}

class OrcidClient(private val baseUrl: String = "https://pub.orcid.org/v3.0") {
    fun isValidId(id: String): Boolean = id.trim().contains("/")

    fun personUrl(id: String): String = "${baseUrl.trimEnd('/')}/${id.trim()}/person"

    fun validateId(id: String): String? {
        val v = id.trim()
        if (v.isBlank()) return "Enter an ORCID iD."
        if (!v.contains("/")) return "ORCID iD must contain a slash (e.g. 0000-0001-2345-6789)."
        return null
    }
}

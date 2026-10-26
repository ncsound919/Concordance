package com.example.concordance.integration.zenodo

data class ZenodoDeposit(
    val depositId: String?,
    val doi: String?,
    val recordUrl: String?,
    val configured: Boolean
)

sealed interface ZenodoResult<out T> {
    data class Ok<T>(val value: T) : ZenodoResult<T>
    data class Unavailable(val reason: String) : ZenodoResult<Nothing>
    data class Refused(val reason: String) : ZenodoResult<Nothing>
}

class ZenodoClient {
    fun isConfigured(token: String?): Boolean = !token.isNullOrBlank()

    fun depositUrl(base: String = "https://sandbox.zenodo.org/api"): String =
        "${base.trimEnd('/')}/deposit/depositions"

    fun buildPayload(title: String, description: String, creators: List<String>): Map<String, Any> =
        mapOf(
            "metadata" to mapOf(
                "title" to title,
                "description" to description,
                "upload_type" to "dataset",
                "creators" to creators.map { mapOf("name" to it) },
                "access_right" to "open",
                "license" to "cc-by-4.0"
            )
        )
}

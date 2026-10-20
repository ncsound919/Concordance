package com.example.concordance.integration.community

import com.example.concordance.integration.supabase.SupabaseAuth
import com.example.concordance.model.Anchor
import com.example.concordance.model.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PublishedArtifact(
    val artifactId: String,
    val url: String,
    val timestamped: Boolean,
    val genTime: String?,
    val tsa: String?
)

/**
 * Forwards a local claim or capsule to the Concordance website's publication
 * endpoint. The website owns the RFC 3161 stamping and the public registry;
 * the phone only supplies the payload and the user's Supabase bearer token.
 *
 * Nothing is sent unless the user is signed in and explicitly presses Publish.
 */
object CommunityPublisher {

    private val JSON = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun publishClaim(
        auth: SupabaseAuth,
        baseUrl: String,
        claim: Anchor,
        license: String = "CC-BY-4.0"
    ): Result<PublishedArtifact> = publish(auth, baseUrl) {
        JSONObject().apply {
            put("artifactType", "claim")
            put("license", license)
            put("artifact", JSONObject().apply {
                put("id", claim.id)
                put("label", claim.label)
                put("source", claim.source)
                put("evidenceGrade", claim.evidenceGrade.name)
                claim.brainEvents?.let { put("brainEvents", it) }
                claim.brainTotal?.let { put("brainTotal", it) }
                claim.otherEvents?.let { put("otherEvents", it) }
                claim.otherTotal?.let { put("otherTotal", it) }
                claim.oddsRatio?.let { put("oddsRatio", it) }
                claim.orCiLow?.let { put("orCiLow", it) }
                claim.orCiHigh?.let { put("orCiHigh", it) }
                claim.referencePrevalence?.let { put("referencePrevalence", it) }
                put("denominator", claim.denominator)
                put("notes", claim.notes)
                put("dataClass", "aggregate")
            })
        }
    }

    suspend fun publishCapsule(
        auth: SupabaseAuth,
        baseUrl: String,
        capsule: Capsule,
        license: String = "CC-BY-4.0"
    ): Result<PublishedArtifact> = publish(auth, baseUrl) {
        JSONObject().apply {
            put("artifactType", "capsule")
            put("license", license)
            put("artifact", JSONObject().apply {
                put("id", capsule.id)
                put("permalink", capsule.permalink)
                put("capsuleVersion", capsule.capsuleVersion)
                put("softwareVersion", capsule.softwareVersion)
                put("createdUtc", capsule.createdUtc)
                put("sha256", capsule.sha256)
                put("question", capsule.question)
                put("anchorLabel", capsule.anchorLabel)
                put("verdict", capsule.verdict.name.lowercase(Locale.US))
                put("citableArgument", capsule.citableArgument)
                put("plainLanguageVerdict", capsule.plainLanguageVerdict)
                put("rNull", capsule.rNull)
                capsule.rSignificance?.let { put("rSignificance", it) }
                put("snAssumed", capsule.snAssumed)
                capsule.rLow?.let { put("rLow", it) }
                capsule.rHigh?.let { put("rHigh", it) }
                put("observedOr", capsule.observedOr)
                put("rawJson", capsule.rawJson)
                put("markdown", capsule.markdown)
                capsule.provenanceDigest?.let { put("provenanceDigest", it) }
                if (capsule.provenanceRecordIds.isNotEmpty()) {
                    put("provenanceRecordIds", capsule.provenanceRecordIds.joinToString(","))
                }
                capsule.modelRevisionId?.let { put("modelRevisionId", it) }
                capsule.supersedesCapsuleId?.let { put("supersedesCapsuleId", it) }
                put("dataClass", "aggregate")
            })
        }
    }

    private suspend fun publish(
        auth: SupabaseAuth,
        baseUrl: String,
        body: () -> JSONObject
    ): Result<PublishedArtifact> = withContext(Dispatchers.IO) {
        runCatching {
            val token = auth.accessToken() ?: error("Sign in to publish to the community")
            val endpoint = baseUrl.trimEnd('/') + "/api/app/publish"
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(body().toString().toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(text).optString("error") }.getOrNull()
                    error(detail?.takeIf { it.isNotBlank() } ?: "Publish failed (${response.code})")
                }
                val json = JSONObject(text)
                val timestamp = json.optJSONObject("timestamp")
                PublishedArtifact(
                    artifactId = json.optJSONObject("publication")?.optString("artifact_id").orEmpty(),
                    url = json.optString("url"),
                    timestamped = json.optBoolean("timestamped", false),
                    genTime = timestamp?.optString("genTime")?.takeIf { it.isNotBlank() },
                    tsa = timestamp?.optString("tsa")?.takeIf { it.isNotBlank() }
                )
            }
        }
    }
}

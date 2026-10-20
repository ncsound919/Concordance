package com.example.concordance.integration.aws

import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal AWS Signature Version 4 signer for S3 PUT/GET requests.
 * Only the headers needed for a simple object upload are signed.
 */
object S3SigV4 {

    fun signPut(
        accessKey: String,
        secretKey: String,
        region: String,
        bucket: String,
        key: String,
        body: String,
        endpoint: String = "https://s3.$region.amazonaws.com"
    ): Pair<String, Map<String, String>> {
        val service = "s3"
        val host = endpoint.removePrefix("https://").removePrefix("http://").trimEnd('/')
        // host should be like s3.us-east-2.amazonaws.com or bucket.s3...
        val url = "$endpoint/$bucket/$key"
        val now = Date()
        val amzDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(now)
        val dateStamp = amzDate.substring(0, 8)

        val payloadHash = sha256Hex(body)

        val canonicalUri = "/$bucket/$key".let { it.replace("//", "/") }
        val canonicalQuerystring = ""
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val canonicalHeaders = "host:$host\nx-amz-content-sha256:$payloadHash\nx-amz-date:$amzDate\n"

        val canonicalRequest = "PUT\n$canonicalUri\n$canonicalQuerystring\n$canonicalHeaders\n$signedHeaders\n$payloadHash"

        val algorithm = "AWS4-HMAC-SHA256"
        val credentialScope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = "$algorithm\n$amzDate\n$credentialScope\n${sha256Hex(canonicalRequest)}"

        val signingKey = getSignatureKey(secretKey, dateStamp, region, service)
        val signature = hmacSha256Hex(signingKey, stringToSign)

        val authorization = "$algorithm Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        val headers = mapOf(
            "Host" to host,
            "x-amz-date" to amzDate,
            "x-amz-content-sha256" to payloadHash,
            "Authorization" to authorization,
            "Content-Type" to "application/json"
        )
        return url to headers
    }

    fun signGet(
        accessKey: String,
        secretKey: String,
        region: String,
        bucket: String,
        key: String,
        endpoint: String = "https://s3.$region.amazonaws.com"
    ): Pair<String, Map<String, String>> {
        val service = "s3"
        val host = endpoint.removePrefix("https://").removePrefix("http://").trimEnd('/')
        val url = "$endpoint/$bucket/$key"
        val now = Date()
        val amzDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(now)
        val dateStamp = amzDate.substring(0, 8)

        val payloadHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" // empty body SHA-256

        val canonicalUri = "/$bucket/$key".replace("//", "/")
        val canonicalQuerystring = ""
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val canonicalHeaders = "host:$host\nx-amz-content-sha256:$payloadHash\nx-amz-date:$amzDate\n"

        val canonicalRequest = "GET\n$canonicalUri\n$canonicalQuerystring\n$canonicalHeaders\n$signedHeaders\n$payloadHash"

        val algorithm = "AWS4-HMAC-SHA256"
        val credentialScope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = "$algorithm\n$amzDate\n$credentialScope\n${sha256Hex(canonicalRequest)}"

        val signingKey = getSignatureKey(secretKey, dateStamp, region, service)
        val signature = hmacSha256Hex(signingKey, stringToSign)

        val authorization = "$algorithm Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        val headers = mapOf(
            "Host" to host,
            "x-amz-date" to amzDate,
            "x-amz-content-sha256" to payloadHash,
            "Authorization" to authorization
        )
        return url to headers
    }
    /**
     * Generic SigV4 signer for any AWS service. Returns (url, headers).
     */
    fun sign(
        method: String,
        service: String,
        region: String,
        endpoint: String,
        uri: String,
        queryString: String = "",
        payload: String,
        accessKey: String,
        secretKey: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): Pair<String, Map<String, String>> {
        val host = endpoint.removePrefix("https://").removePrefix("http://").trimEnd('/')
        val url = "$endpoint$uri" + if (queryString.isNotBlank()) "?$queryString" else ""
        val now = Date()
        val amzDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(now)
        val dateStamp = amzDate.substring(0, 8)

        val payloadHash = sha256Hex(payload)
        val signedHeaderNames = (extraHeaders.keys.map { it.lowercase() } + listOf("host", "x-amz-content-sha256", "x-amz-date")).distinct().sorted()
        val canonicalHeaders = (listOf("host" to host, "x-amz-content-sha256" to payloadHash, "x-amz-date" to amzDate) + extraHeaders.map { it.key.lowercase() to it.value })
            .distinctBy { it.first }
            .sortedBy { it.first }
            .joinToString("\n") { "${it.first}:${it.second}" } + "\n"
        val signedHeaders = signedHeaderNames.joinToString(";")

        val canonicalRequest = "$method\n$uri\n$queryString\n$canonicalHeaders\n$signedHeaders\n$payloadHash"

        val algorithm = "AWS4-HMAC-SHA256"
        val credentialScope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = "$algorithm\n$amzDate\n$credentialScope\n${sha256Hex(canonicalRequest)}"

        val signingKey = getSignatureKey(secretKey, dateStamp, region, service)
        val signature = hmacSha256Hex(signingKey, stringToSign)

        val authorization = "$algorithm Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        val headers = mutableMapOf<String, String>()
        headers["Host"] = host
        headers["x-amz-date"] = amzDate
        headers["x-amz-content-sha256"] = payloadHash
        headers["Authorization"] = authorization
        extraHeaders.forEach { headers[it.key] = it.value }
        return url to headers
    }
    private fun getSignatureKey(key: String, dateStamp: String, region: String, service: String): ByteArray {
        val kSecret = ("AWS4" + key).toByteArray(Charsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, service)
        return hmacSha256(kService, "aws4_request")
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hmacSha256Hex(key: ByteArray, data: String): String {
        return hmacSha256(key, data).joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

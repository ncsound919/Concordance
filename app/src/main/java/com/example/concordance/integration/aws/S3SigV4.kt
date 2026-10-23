package com.example.concordance.integration.aws

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal AWS Signature Version 4 signer for S3 and other JSON/query services.
 *
 * S3 rules that matter here:
 * - Buckets created after 2020-09-30 accept only **virtual-hosted-style** requests, so the default
 *   address is `https://<bucket>.s3.<region>.amazonaws.com/<key>`. Custom endpoints (MinIO, R2,
 *   a mock server) keep path-style addressing, which is what those servers expect.
 * - A "presigned URL" is a URL carrying `X-Amz-Signature` and friends in the query string. A plain
 *   object URL is not presigned and returns 403 for a private object.
 * - Temporary credentials (STS, Cognito) need `x-amz-security-token`; without it every request
 *   signed with a session credential fails.
 */
object S3SigV4 {

    private const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val MAX_EXPIRES_SECONDS = 604_800L

    fun signPut(
        accessKey: String,
        secretKey: String,
        region: String,
        bucket: String,
        key: String,
        body: String,
        endpoint: String? = null,
        sessionToken: String? = null,
        pathStyle: Boolean = false
    ): Pair<String, Map<String, String>> = signObject(
        method = "PUT",
        accessKey = accessKey,
        secretKey = secretKey,
        sessionToken = sessionToken,
        region = region,
        bucket = bucket,
        key = key,
        payloadHash = sha256Hex(body),
        endpoint = endpoint,
        pathStyle = pathStyle,
        contentType = "application/json"
    )

    fun signGet(
        accessKey: String,
        secretKey: String,
        region: String,
        bucket: String,
        key: String,
        endpoint: String? = null,
        sessionToken: String? = null,
        pathStyle: Boolean = false
    ): Pair<String, Map<String, String>> = signObject(
        method = "GET",
        accessKey = accessKey,
        secretKey = secretKey,
        sessionToken = sessionToken,
        region = region,
        bucket = bucket,
        key = key,
        payloadHash = EMPTY_SHA256,
        endpoint = endpoint,
        pathStyle = pathStyle,
        contentType = null
    )

    /**
     * A real presigned GET URL: the signature travels in the query string, so a reviewer holding
     * only the link can fetch a private object until [expiresSeconds] elapses.
     */
    fun presignGet(
        accessKey: String,
        secretKey: String,
        region: String,
        bucket: String,
        key: String,
        endpoint: String? = null,
        sessionToken: String? = null,
        pathStyle: Boolean = false,
        expiresSeconds: Long = 3600,
        now: Date = Date()
    ): String {
        val expires = expiresSeconds.coerceIn(1L, MAX_EXPIRES_SECONDS)
        val host = host(bucket, region, endpoint, pathStyle)
        val scheme = scheme(endpoint)
        val canonicalUri = canonicalUri(bucket, key, pathStyle)
        val (amzDate, dateStamp) = timestamps(now)
        val scope = "$dateStamp/$region/s3/aws4_request"

        val params = linkedMapOf(
            "X-Amz-Algorithm" to ALGORITHM,
            "X-Amz-Credential" to "$accessKey/$scope",
            "X-Amz-Date" to amzDate,
            "X-Amz-Expires" to expires.toString(),
            "X-Amz-SignedHeaders" to "host"
        )
        if (!sessionToken.isNullOrBlank()) params["X-Amz-Security-Token"] = sessionToken
        val canonicalQuery = params.entries
            .sortedBy { it.key }
            .joinToString("&") { "${uriEncode(it.key)}=${uriEncode(it.value)}" }

        val canonicalRequest = "GET\n$canonicalUri\n$canonicalQuery\nhost:$host\n\nhost\nUNSIGNED-PAYLOAD"
        val stringToSign = "$ALGORITHM\n$amzDate\n$scope\n${sha256Hex(canonicalRequest)}"
        val signature = hmacSha256Hex(signingKey(secretKey, dateStamp, region, "s3"), stringToSign)

        return "$scheme://$host$canonicalUri?$canonicalQuery&X-Amz-Signature=$signature"
    }

    /** Generic SigV4 signer for any AWS service (CloudWatch Logs, API Gateway, …). */
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
        sessionToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): Pair<String, Map<String, String>> {
        val host = stripScheme(endpoint)
        val url = "$endpoint$uri" + if (queryString.isNotBlank()) "?$queryString" else ""
        val (amzDate, dateStamp) = timestamps(Date())
        val payloadHash = sha256Hex(payload)

        val headers = linkedMapOf(
            "host" to host,
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to amzDate
        )
        if (!sessionToken.isNullOrBlank()) headers["x-amz-security-token"] = sessionToken
        extraHeaders.forEach { (k, v) -> headers.putIfAbsent(k.lowercase(), v) }

        val signedHeaderNames = headers.keys.sorted()
        val canonicalHeaders = signedHeaderNames.joinToString("\n") { "${it}:${headers.getValue(it)}" } + "\n"
        val signedHeaders = signedHeaderNames.joinToString(";")
        val canonicalRequest = "$method\n$uri\n$queryString\n$canonicalHeaders\n$signedHeaders\n$payloadHash"

        val scope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = "$ALGORITHM\n$amzDate\n$scope\n${sha256Hex(canonicalRequest)}"
        val signature = hmacSha256Hex(signingKey(secretKey, dateStamp, region, service), stringToSign)
        val authorization =
            "$ALGORITHM Credential=$accessKey/$scope, SignedHeaders=$signedHeaders, Signature=$signature"

        val outHeaders = mutableMapOf<String, String>()
        outHeaders["Host"] = host
        outHeaders["x-amz-date"] = amzDate
        outHeaders["x-amz-content-sha256"] = payloadHash
        if (!sessionToken.isNullOrBlank()) outHeaders["x-amz-security-token"] = sessionToken
        extraHeaders.forEach { outHeaders[it.key] = it.value }
        outHeaders["Authorization"] = authorization
        return Pair(url, outHeaders)
    }

    // ---- internals ----

    private fun signObject(
        method: String,
        accessKey: String,
        secretKey: String,
        sessionToken: String?,
        region: String,
        bucket: String,
        key: String,
        payloadHash: String,
        endpoint: String?,
        pathStyle: Boolean,
        contentType: String?
    ): Pair<String, Map<String, String>> {
        val host = host(bucket, region, endpoint, pathStyle)
        val scheme = scheme(endpoint)
        val canonicalUri = canonicalUri(bucket, key, pathStyle)
        val (amzDate, dateStamp) = timestamps(Date())
        val scope = "$dateStamp/$region/s3/aws4_request"

        val signedHeaders = if (sessionToken.isNullOrBlank()) {
            "host;x-amz-content-sha256;x-amz-date"
        } else {
            "host;x-amz-content-sha256;x-amz-date;x-amz-security-token"
        }
        val canonicalHeaders = buildString {
            append("host:$host\n")
            append("x-amz-content-sha256:$payloadHash\n")
            append("x-amz-date:$amzDate\n")
            if (!sessionToken.isNullOrBlank()) append("x-amz-security-token:$sessionToken\n")
        }
        val canonicalRequest =
            "$method\n$canonicalUri\n\n$canonicalHeaders\n$signedHeaders\n$payloadHash"
        val stringToSign = "$ALGORITHM\n$amzDate\n$scope\n${sha256Hex(canonicalRequest)}"
        val signature = hmacSha256Hex(signingKey(secretKey, dateStamp, region, "s3"), stringToSign)
        val authorization =
            "$ALGORITHM Credential=$accessKey/$scope, SignedHeaders=$signedHeaders, Signature=$signature"

        val headers = linkedMapOf(
            "Host" to host,
            "x-amz-date" to amzDate,
            "x-amz-content-sha256" to payloadHash,
            "Authorization" to authorization
        )
        if (!sessionToken.isNullOrBlank()) headers["x-amz-security-token"] = sessionToken
        if (contentType != null) headers["Content-Type"] = contentType
        return Pair("$scheme://$host$canonicalUri", headers)
    }

    private fun scheme(endpoint: String?): String =
        if (endpoint != null && endpoint.startsWith("http://")) "http" else "https"

    private fun stripScheme(endpoint: String): String =
        endpoint.removePrefix("https://").removePrefix("http://").trimEnd('/')

    private fun host(bucket: String, region: String, endpoint: String?, pathStyle: Boolean): String {
        val base = if (endpoint != null) stripScheme(endpoint) else "s3.$region.amazonaws.com"
        return if (pathStyle) base else "$bucket.$base"
    }

    private fun canonicalUri(bucket: String, key: String, pathStyle: Boolean): String {
        val encodedKey = key.split('/').joinToString("/") { uriEncode(it) }
        return if (pathStyle) "/${uriEncode(bucket)}/$encodedKey" else "/$encodedKey"
    }

    private fun timestamps(now: Date): Pair<String, String> {
        val amzDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(now)
        return Pair(amzDate, amzDate.substring(0, 8))
    }

    /**
     * SigV4 percent-encoding: unreserved characters (`A-Z a-z 0-9 - _ . ~`) pass through,
     * everything else is percent-encoded with uppercase hex. `URLEncoder` is wrong here because
     * it encodes spaces as `+` and leaves `*` alone.
     */
    internal fun uriEncode(value: String): String {
        val out = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val ch = byte.toInt() and 0xFF
            val c = ch.toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c)
            } else {
                out.append('%').append(String.format(Locale.US, "%02X", ch))
            }
        }
        return out.toString()
    }

    private fun signingKey(key: String, dateStamp: String, region: String, service: String): ByteArray {
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

    private fun hmacSha256Hex(key: ByteArray, data: String): String =
        hmacSha256(key, data).joinToString("") { "%02x".format(it) }

    private fun sha256Hex(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

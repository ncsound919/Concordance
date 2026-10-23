package com.example

import com.example.concordance.integration.aws.S3SigV4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * The signer is compared against botocore's reference implementation: the expected signatures
 * below were produced by `botocore.auth.SigV4QueryAuth` for the same credentials, bucket, key,
 * region, expiry and timestamp (2026-10-21T12:00:00Z). If this test fails, a presigned link the
 * app hands to a reviewer will not open the object.
 */
class S3SigV4Test {

    private val accessKey = "AKIAIOSFODNN7EXAMPLE"
    private val secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
    private val fixedNow = Date(1792584000000L) // 2026-10-21T12:00:00Z
    private val key = "vault/2026/cap-1/capsule.json"

    @Test
    fun signPutUsesVirtualHostedStyleByDefault() {
        val (url, headers) = S3SigV4.signPut(
            accessKey = accessKey,
            secretKey = secretKey,
            region = "us-east-2",
            bucket = "test-bucket",
            key = key,
            body = "{\"x\":1}"
        )
        assertEquals("https://test-bucket.s3.us-east-2.amazonaws.com/$key", url)
        assertTrue(headers["Authorization"]!!.startsWith("AWS4-HMAC-SHA256"))
        assertTrue(headers["Authorization"]!!.contains("Credential=$accessKey/"))
        assertTrue(headers["Authorization"]!!.contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date"))
        assertTrue(headers.containsKey("x-amz-date"))
        assertFalse("no session token unless one is supplied", headers.containsKey("x-amz-security-token"))
    }

    @Test
    fun signPutSupportsCustomEndpointsWithPathStyle() {
        val (url, headers) = S3SigV4.signPut(
            accessKey = accessKey,
            secretKey = secretKey,
            region = "us-east-2",
            bucket = "test-bucket",
            key = key,
            body = "{}",
            endpoint = "http://localhost:8080",
            pathStyle = true
        )
        assertEquals("http://localhost:8080/test-bucket/$key", url)
        assertTrue(headers["Authorization"]!!.contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date"))
    }

    @Test
    fun sessionTokenIsSigned() {
        val (_, headers) = S3SigV4.signPut(
            accessKey = accessKey,
            secretKey = secretKey,
            region = "us-east-2",
            bucket = "test-bucket",
            key = key,
            body = "{}",
            sessionToken = "FQoGZXIvYXdzEJr//////////wEaDP"
        )
        assertEquals("FQoGZXIvYXdzEJr//////////wEaDP", headers["x-amz-security-token"])
        assertTrue(headers["Authorization"]!!.contains("x-amz-security-token"))
    }

    @Test
    fun signGetHashesAnEmptyBody() {
        val (_, headers) = S3SigV4.signGet(
            accessKey = accessKey,
            secretKey = secretKey,
            region = "us-east-2",
            bucket = "test-bucket",
            key = key
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            headers["x-amz-content-sha256"]
        )
    }

    @Test
    fun presignedUrlMatchesBotocoreReference() {
        val url = S3SigV4.presignGet(
            accessKey = accessKey,
            secretKey = secretKey,
            region = "us-east-2",
            bucket = "test-bucket",
            key = key,
            expiresSeconds = 3600,
            now = fixedNow
        )
        assertEquals(
            "https://test-bucket.s3.us-east-2.amazonaws.com/vault/2026/cap-1/capsule.json" +
                "?X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                "&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20261021%2Fus-east-2%2Fs3%2Faws4_request" +
                "&X-Amz-Date=20261021T120000Z" +
                "&X-Amz-Expires=3600" +
                "&X-Amz-SignedHeaders=host" +
                "&X-Amz-Signature=b966532bd48fe4012377333573a22661454fa7e56225c88ea4f8b10927fe5727",
            url
        )
    }

    @Test
    fun presignedUrlMatchesBotocoreForPathStyleEndpoint() {
        val url = S3SigV4.presignGet(
            accessKey = accessKey,
            secretKey = secretKey,
            region = "us-east-2",
            bucket = "test-bucket",
            key = key,
            endpoint = "https://localhost:8080",
            pathStyle = true,
            expiresSeconds = 3600,
            now = fixedNow
        )
        assertTrue(url.startsWith("https://localhost:8080/test-bucket/$key?"))
        assertTrue(
            url.endsWith("X-Amz-Signature=e54c3ae5d5bf2bdcce5e22da3e832755737a4e9eea4c76fdf71f2f3213c8eb0d")
        )
    }

    @Test
    fun presignedUrlEncodesKeySegments() {
        val url = S3SigV4.presignGet(
            accessKey = accessKey,
            secretKey = secretKey,
            region = "us-east-2",
            bucket = "test-bucket",
            key = "vault/2026/cap 1/capsule.json",
            expiresSeconds = 900,
            now = fixedNow
        )
        assertTrue(url.contains("/vault/2026/cap%201/capsule.json"))
        assertFalse("a raw space would make the URL unusable", url.contains("cap 1"))
        assertTrue(
            url.endsWith("X-Amz-Signature=24f5fa976d7877d153cfa28387239f29e331e0c4f01f8e4bc6dd1243ba090991")
        )
    }

    @Test
    fun presignedUrlCarriesTheSessionToken() {
        val url = S3SigV4.presignGet(
            accessKey = accessKey,
            secretKey = secretKey,
            region = "us-east-2",
            bucket = "test-bucket",
            key = key,
            sessionToken = "FQoGZXIvYXdzEJr//////////wEaDP",
            expiresSeconds = 3600,
            now = fixedNow
        )
        assertTrue(url.contains("X-Amz-Security-Token="))
        assertTrue(url.contains("X-Amz-Signature="))
    }

    @Test
    fun expiryIsClampedToSevenDays() {
        val url = S3SigV4.presignGet(
            accessKey = accessKey,
            secretKey = secretKey,
            region = "us-east-2",
            bucket = "test-bucket",
            key = key,
            expiresSeconds = 999_999_999,
            now = fixedNow
        )
        assertTrue(url.contains("X-Amz-Expires=604800"))
    }

    @Test
    fun uriEncodingFollowsTheSigV4Rules() {
        assertEquals("a%20b", S3SigV4.uriEncode("a b"))
        assertEquals("%2F", S3SigV4.uriEncode("/"))
        assertEquals("a%2Ab", S3SigV4.uriEncode("a*b"))
        assertEquals("~a-b_c.d", S3SigV4.uriEncode("~a-b_c.d"))
    }
}

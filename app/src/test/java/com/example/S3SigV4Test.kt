package com.example

import com.example.concordance.integration.aws.S3SigV4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class S3SigV4Test {

    @Test
    fun signPutProducesAuthorizationHeader() {
        val (url, headers) = S3SigV4.signPut(
            accessKey = "AKIAIOSFODNN7EXAMPLE",
            secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-2",
            bucket = "test-bucket",
            key = "vault/2026/cap-1/capsule.json",
            body = "{\"x\":1}"
        )
        assertTrue(url.contains("test-bucket"))
        assertTrue(url.contains("vault/2026/cap-1/capsule.json"))
        assertTrue(headers["Authorization"]!!.startsWith("AWS4-HMAC-SHA256"))
        assertTrue(headers["Authorization"]!!.contains("Credential=AKIAIOSFODNN7EXAMPLE/"))
        assertTrue(headers["Authorization"]!!.contains("SignedHeaders="))
        assertTrue(headers["Authorization"]!!.contains("Signature="))
        assertTrue(headers.containsKey("x-amz-content-sha256"))
        assertTrue(headers.containsKey("x-amz-date"))
    }

    @Test
    fun signGetProducesAuthorizationHeader() {
        val (url, headers) = S3SigV4.signGet(
            accessKey = "AKIAIOSFODNN7EXAMPLE",
            secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-2",
            bucket = "test-bucket",
            key = "vault/2026/cap-1/capsule.json"
        )
        assertTrue(url.contains("test-bucket"))
        assertTrue(headers["Authorization"]!!.startsWith("AWS4-HMAC-SHA256"))
        // Empty body SHA-256
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            headers["x-amz-content-sha256"])
    }
}

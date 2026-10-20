package com.example.concordance.integration.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/**
 * Per-provider token bucket. Callers suspend until a token is available, so we
 * never exceed published limits (e.g. NCBI 3 rps without a key, PubChem 5/sec).
 */
object ProviderRateLimiter {

    private class Bucket(val ratePerSecond: Double, val capacity: Int) {
        var tokens: Double = capacity.toDouble()
        var lastRefillNanos: Long = System.nanoTime()
    }

    private val buckets = mutableMapOf<String, Bucket>()
    private val mutex = Mutex()

    /** Suspends until a request against [providerId] is permitted. */
    suspend fun acquire(providerId: String, spec: RateLimitSpec) {
        while (true) {
            val waitMs = mutex.withLock {
                val bucket = buckets.getOrPut(providerId) {
                    Bucket(spec.requestsPerSecond.coerceAtLeast(0.1), spec.burst.coerceAtLeast(1))
                }
                val now = System.nanoTime()
                val elapsedSec = (now - bucket.lastRefillNanos) / 1_000_000_000.0
                bucket.tokens = min(spec.burst.toDouble(), bucket.tokens + elapsedSec * spec.requestsPerSecond)
                bucket.lastRefillNanos = now

                if (bucket.tokens >= 1.0) {
                    bucket.tokens -= 1.0
                    0L
                } else {
                    val deficit = 1.0 - bucket.tokens
                    ((deficit / spec.requestsPerSecond.coerceAtLeast(0.1)) * 1000.0)
                        .toLong()
                        .coerceAtLeast(25L)
                }
            }
            if (waitMs == 0L) return
            delay(waitMs)
        }
    }
}

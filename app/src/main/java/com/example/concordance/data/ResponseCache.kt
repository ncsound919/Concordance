package com.example.concordance.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simple TTL-based response cache backed by Room. Used by HttpJsonClient to
 * avoid re-fetching identical requests within the TTL window.
 */
class ResponseCache(private val dao: CachedResponseDao) {
    private val defaultTtlMs = 10 * 60 * 1000L // 10 minutes

    suspend fun get(key: String): CachedResponseEntity? = withContext(Dispatchers.IO) {
        val row = dao.get(key) ?: return@withContext null
        val now = System.currentTimeMillis()
        if (now - row.createdAt > row.ttlMs) {
            dao.delete(key)
            null
        } else {
            row
        }
    }

    suspend fun put(key: String, body: String, sha256: String, ttlMs: Long = defaultTtlMs) =
        withContext(Dispatchers.IO) {
            dao.upsert(CachedResponseEntity(key, body, sha256, System.currentTimeMillis(), ttlMs))
            // Opportunistic housekeeping so expired rows don't accumulate.
            dao.deleteExpired(System.currentTimeMillis())
        }

    suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }
}

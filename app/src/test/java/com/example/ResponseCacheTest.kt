package com.example

import com.example.concordance.data.CachedResponseDao
import com.example.concordance.data.CachedResponseEntity
import com.example.concordance.data.ResponseCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The TTL response cache.
 *
 * The cache is what keeps the app from re-fetching the same literature or
 * cohort record on every screen. A stale entry is worse than no entry, so the
 * expiry behaviour is worth pinning exactly.
 */
class ResponseCacheTest {

    /** A fake DAO that records what the cache asked it to do. */
    private class FakeDao : CachedResponseDao {
        val rows = mutableMapOf<String, CachedResponseEntity>()
        var deleted = mutableListOf<String>()
        var deleteExpiredCalls = 0
        var cleared = false

        override suspend fun get(key: String): CachedResponseEntity? = rows[key]

        override suspend fun upsert(row: CachedResponseEntity) {
            rows[row.cacheKey] = row
        }

        override suspend fun delete(key: String) {
            deleted.add(key)
            rows.remove(key)
        }

        override suspend fun deleteExpired(now: Long) {
            deleteExpiredCalls++
        }

        override suspend fun clear() {
            cleared = true
            rows.clear()
        }
    }

    @Test
    fun `a missing key is a miss`() = runBlocking {
        val cache = ResponseCache(FakeDao())
        assertNull(cache.get("absent"))
    }

    @Test
    fun `a stored response is returned`() = runBlocking {
        val dao = FakeDao()
        val cache = ResponseCache(dao)
        cache.put("key", "body", "sha")
        val found = cache.get("key")
        assertNotNull(found)
        assertEquals("body", found!!.body)
        assertEquals("sha", found.sha256)
        assertEquals("key", found.cacheKey)
    }

    @Test
    fun `an expired response is deleted and reported as a miss`() = runBlocking {
        val dao = FakeDao()
        val cache = ResponseCache(dao)
        cache.put("key", "body", "sha", ttlMs = 1L)
        Thread.sleep(20)
        assertNull(cache.get("key"))
        assertTrue(dao.deleted.contains("key"))
    }

    @Test
    fun `a response inside its ttl is not deleted`() = runBlocking {
        val dao = FakeDao()
        val cache = ResponseCache(dao)
        cache.put("key", "body", "sha", ttlMs = 60_000L)
        assertNotNull(cache.get("key"))
        assertTrue(dao.deleted.isEmpty())
    }

    @Test
    fun `storing a response also runs the housekeeping sweep`() = runBlocking {
        val dao = FakeDao()
        val cache = ResponseCache(dao)
        cache.put("key", "body", "sha")
        assertEquals(1, dao.deleteExpiredCalls)
    }

    @Test
    fun `storing the same key twice replaces the row`() = runBlocking {
        val dao = FakeDao()
        val cache = ResponseCache(dao)
        cache.put("key", "first", "sha1")
        cache.put("key", "second", "sha2")
        assertEquals(1, dao.rows.size)
        assertEquals("second", cache.get("key")!!.body)
    }

    @Test
    fun `clearing empties the store`() = runBlocking {
        val dao = FakeDao()
        val cache = ResponseCache(dao)
        cache.put("key", "body", "sha")
        cache.clear()
        assertTrue(dao.cleared)
        assertNull(cache.get("key"))
    }

    @Test
    fun `the default ttl is ten minutes`() = runBlocking {
        val dao = FakeDao()
        val cache = ResponseCache(dao)
        cache.put("key", "body", "sha")
        assertEquals(10 * 60 * 1000L, dao.rows["key"]!!.ttlMs)
    }
}

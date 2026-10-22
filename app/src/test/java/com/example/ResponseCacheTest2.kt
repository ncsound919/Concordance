package com.example

import androidx.room.Room
import com.example.concordance.data.ConcordanceDatabase
import com.example.concordance.data.ResponseCache
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResponseCacheTest2 {

    private lateinit var db: ConcordanceDatabase
    private lateinit var cache: ResponseCache

    @Before
    fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, ConcordanceDatabase::class.java)
            .allowMainThreadQueries().build()
        cache = ResponseCache(db.cachedResponseDao())
    }

    @After
    fun teardown() = db.close()

    @Test
    fun putAndGet() = runBlocking {
        cache.put("k", "body", "h")
        val row = cache.get("k")
        assertEquals("body", row!!.body)
        assertEquals("h", row.sha256)
    }

    @Test
    fun getMissingReturnsNull() = runBlocking {
        assertNull(cache.get("nope"))
    }

    @Test
    fun expiredEntryIsDeletedAndReturnsNull() = runBlocking {
        cache.put("k", "body", "h", 1L)
        Thread.sleep(5)
        assertNull(cache.get("k"))
    }

    @Test
    fun clearRemovesAll() = runBlocking {
        cache.put("k1", "b1", "h1")
        cache.put("k2", "b2", "h2")
        cache.clear()
        assertNull(cache.get("k1"))
        assertNull(cache.get("k2"))
    }
}

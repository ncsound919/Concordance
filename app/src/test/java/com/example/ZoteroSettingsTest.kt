package com.example

import com.example.concordance.integration.zotero.ZoteroSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Zotero user and group identifiers. They are not secrets - they only
 * pair with a BYOK key - but a blank or whitespace-only identifier would
 * produce a confusing API error later, so they are normalised on write and
 * treated as absent on read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ZoteroSettingsTest {

    @Before
    fun setUp() {
        val ctx = RuntimeEnvironment.getApplication()
        ZoteroSettings.setUserId(ctx, null)
        ZoteroSettings.setGroupId(ctx, null)
    }

    @Test
    fun `an unset user id is absent`() {
        assertNull(ZoteroSettings.userId(RuntimeEnvironment.getApplication()))
    }

    @Test
    fun `a user id round trips`() {
        val ctx = RuntimeEnvironment.getApplication()
        ZoteroSettings.setUserId(ctx, "12345")
        assertEquals("12345", ZoteroSettings.userId(ctx))
    }

    @Test
    fun `a user id is trimmed on write`() {
        val ctx = RuntimeEnvironment.getApplication()
        ZoteroSettings.setUserId(ctx, "  12345  ")
        assertEquals("12345", ZoteroSettings.userId(ctx))
    }

    @Test
    fun `a blank user id is treated as absent`() {
        val ctx = RuntimeEnvironment.getApplication()
        ZoteroSettings.setUserId(ctx, "12345")
        ZoteroSettings.setUserId(ctx, "   ")
        assertNull(ZoteroSettings.userId(ctx))
    }

    @Test
    fun `a null user id removes the stored value`() {
        val ctx = RuntimeEnvironment.getApplication()
        ZoteroSettings.setUserId(ctx, "12345")
        ZoteroSettings.setUserId(ctx, null)
        assertNull(ZoteroSettings.userId(ctx))
    }

    @Test
    fun `an unset group id is absent`() {
        assertNull(ZoteroSettings.groupId(RuntimeEnvironment.getApplication()))
    }

    @Test
    fun `a group id round trips`() {
        val ctx = RuntimeEnvironment.getApplication()
        ZoteroSettings.setGroupId(ctx, "g-99")
        assertEquals("g-99", ZoteroSettings.groupId(ctx))
    }

    @Test
    fun `a blank group id is treated as absent`() {
        val ctx = RuntimeEnvironment.getApplication()
        ZoteroSettings.setGroupId(ctx, "g-99")
        ZoteroSettings.setGroupId(ctx, "")
        assertNull(ZoteroSettings.groupId(ctx))
    }

    @Test
    fun `the user id and the group id are stored independently`() {
        val ctx = RuntimeEnvironment.getApplication()
        ZoteroSettings.setUserId(ctx, "u1")
        ZoteroSettings.setGroupId(ctx, "g1")
        assertEquals("u1", ZoteroSettings.userId(ctx))
        assertEquals("g1", ZoteroSettings.groupId(ctx))
        ZoteroSettings.setUserId(ctx, null)
        assertNull(ZoteroSettings.userId(ctx))
        assertEquals("g1", ZoteroSettings.groupId(ctx))
    }
}

package com.example

import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.Skin
import com.example.ui.theme.createSkinPreferenceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chosen skin must survive a restart.
 *
 * The skin lives in the same preferences file as the light/dark mode, so the
 * two settings cannot fight each other and a user who picks the website look
 * does not lose it on relaunch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkinPreferenceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun defaultsToClinical() {
        val store = createSkinPreferenceStore(context)
        assertEquals(Skin.CLINICAL, store.load())
    }

    @Test
    fun savesAndReloadsEverySkin() {
        val store = createSkinPreferenceStore(context)
        for (skin in Skin.entries) {
            store.save(skin)
            assertEquals(skin, store.load())
            // A fresh store over the same preferences must see it too.
            assertEquals(skin, createSkinPreferenceStore(context).load())
        }
    }

    @Test
    fun unknownStoredValueFallsBackToClinical() {
        val prefs = context.getSharedPreferences("concordance_theme", android.content.Context.MODE_PRIVATE)
        prefs.edit { putString("skin", "NOT_A_SKIN") }
        assertEquals(Skin.CLINICAL, createSkinPreferenceStore(context).load())
    }

    @Test
    fun skinAndThemeModeAreIndependent() {
        val skinStore = createSkinPreferenceStore(context)
        skinStore.save(Skin.SITE)

        val themeStore = com.example.ui.theme.createThemePreferenceStore(context)
        themeStore.save(com.example.ui.theme.ThemeMode.DARK)

        assertEquals(Skin.SITE, skinStore.load())
        assertEquals(com.example.ui.theme.ThemeMode.DARK, themeStore.load())

        skinStore.save(Skin.CLINICAL)
        assertEquals(com.example.ui.theme.ThemeMode.DARK, themeStore.load())
    }

    @Test
    fun everySkinHasALabelAndBlurb() {
        for (skin in Skin.entries) {
            assertTrue("${skin.name} needs a label", skin.label.isNotBlank())
            assertTrue("${skin.name} needs a blurb", skin.blurb.isNotBlank())
        }
    }
}

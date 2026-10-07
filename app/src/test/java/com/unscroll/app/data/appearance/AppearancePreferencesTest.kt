package com.unscroll.app.data.appearance

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.unscroll.app.domain.fox.FoxSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppearancePreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.createPreferences() = AppearancePreferences(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.root.resolve("test.preferences_pb")
        },
    )

    @Test
    fun brandPaletteByDefault_dynamicColorIsOptIn() = runTest {
        val preferences = createPreferences()
        assertFalse(preferences.dynamicColor.first())

        preferences.setDynamicColor(true)
        assertTrue(preferences.dynamicColor.first())

        preferences.setDynamicColor(false)
        assertFalse(preferences.dynamicColor.first())
    }

    @Test
    fun fox_onByDefault_eachSwitchSavedOnItsOwn() = runTest {
        val preferences = createPreferences()
        assertEquals(FoxSettings(showFox = true, messages = true), preferences.fox.first())

        preferences.setFoxMessages(false)
        assertEquals(FoxSettings(showFox = true, messages = false), preferences.fox.first())

        preferences.setShowFox(false)
        preferences.setFoxMessages(true)
        assertEquals(FoxSettings(showFox = false, messages = true), preferences.fox.first())
    }
}

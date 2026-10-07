package com.unscroll.app.data.appearance

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
}

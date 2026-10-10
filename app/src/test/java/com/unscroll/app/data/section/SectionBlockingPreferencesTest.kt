package com.unscroll.app.data.section

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.section.SectionBlockMode
import com.unscroll.app.domain.section.SectionBlockingSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SectionBlockingPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val app = "com.example.social"

    private fun TestScope.createDataStore() = PreferenceDataStoreFactory.create(scope = backgroundScope) {
        tempFolder.root.resolve("test.preferences_pb")
    }

    private fun TestScope.createPreferences() = SectionBlockingPreferences(createDataStore())

    @Test
    fun offByDefault() = runTest {
        assertEquals(SectionBlockingSettings(), createPreferences().settings.first())
    }

    @Test
    fun consent_killSwitch_andAgreeingAgainTurnsItBackOn() = runTest {
        val preferences = createPreferences()
        preferences.setConsent(ScrollConsent.AGREED, now = 1L)
        preferences.setTurnedOff(true)
        assertTrue(preferences.settings.first().turnedOff)
        assertEquals(ScrollConsent.AGREED, preferences.settings.first().consent)

        preferences.setConsent(ScrollConsent.DECLINED, now = 2L)
        assertTrue(preferences.settings.first().turnedOff)
        preferences.setConsent(ScrollConsent.AGREED, now = 3L)
        assertFalse(preferences.settings.first().turnedOff)
    }

    @Test
    fun connectedSinceConsent_onlyAfterAgreeing_andResetByANewAnswer() = runTest {
        val preferences = createPreferences()
        preferences.onServiceConnected()
        assertFalse(preferences.connectedSinceConsent.first())
        preferences.setConsent(ScrollConsent.AGREED, now = 1L)
        preferences.onServiceConnected()
        assertTrue(preferences.connectedSinceConsent.first())
        preferences.setConsent(ScrollConsent.DECLINED, now = 2L)
        assertFalse(preferences.connectedSinceConsent.first())
    }

    @Test
    fun perAppMode() = runTest {
        val preferences = createPreferences()
        preferences.setMode(app, SectionBlockMode.AFTER_LIMIT)
        assertEquals(SectionBlockMode.AFTER_LIMIT, preferences.settings.first().modeFor(app))

        preferences.setMode(app, SectionBlockMode.ALWAYS)
        assertEquals(SectionBlockMode.ALWAYS, preferences.settings.first().modeFor(app))
    }

    @Test
    fun legacyBlockedApps_areReadUntilCleared() = runTest {
        val dataStore = createDataStore()
        val preferences = SectionBlockingPreferences(dataStore)
        // The old per-app "Block Reels" switch, as stored before the quick toggles.
        dataStore.edit { it[stringSetPreferencesKey("section_blocked_apps")] = setOf(app) }
        assertEquals(setOf(app), preferences.settings.first().legacyBlockedApps)

        preferences.clearLegacyBlockedApps()
        assertEquals(emptySet<String>(), preferences.settings.first().legacyBlockedApps)
    }

    @Test
    fun inspectorSwitch() = runTest {
        val preferences = createPreferences()
        preferences.setInspector(true)
        assertTrue(preferences.settings.first().inspector)
    }
}

package com.unscroll.app.data.section

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

    private fun TestScope.createPreferences() = SectionBlockingPreferences(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.root.resolve("test.preferences_pb")
        },
    )

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
    fun perAppSwitchAndMode() = runTest {
        val preferences = createPreferences()
        preferences.setBlocked(app, true)
        preferences.setMode(app, SectionBlockMode.AFTER_LIMIT)
        var settings = preferences.settings.first()
        assertTrue(settings.isBlocked(app))
        assertEquals(SectionBlockMode.AFTER_LIMIT, settings.modeFor(app))

        preferences.setMode(app, SectionBlockMode.ALWAYS)
        preferences.setBlocked(app, false)
        settings = preferences.settings.first()
        assertFalse(settings.isBlocked(app))
        assertEquals(SectionBlockMode.ALWAYS, settings.modeFor(app))
    }

    @Test
    fun inspectorSwitch() = runTest {
        val preferences = createPreferences()
        preferences.setInspector(true)
        assertTrue(preferences.settings.first().inspector)
    }
}

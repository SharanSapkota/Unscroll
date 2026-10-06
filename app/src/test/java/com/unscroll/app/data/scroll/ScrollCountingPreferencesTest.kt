package com.unscroll.app.data.scroll

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.unscroll.app.domain.scroll.ScrollConsent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScrollCountingPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.createPreferences() = ScrollCountingPreferences(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.root.resolve("test.preferences_pb")
        },
    )

    @Test
    fun defaults_notAsked_neverEnabled() = runTest {
        val record = createPreferences().record.first()
        assertEquals(ScrollConsent.NOT_ASKED, record.consent)
        assertNull(record.consentAt)
        assertFalse(record.connectedSinceConsent)
        assertNull(record.countingSince)
    }

    @Test
    fun consent_isSavedWithItsTime() = runTest {
        val preferences = createPreferences()
        preferences.setConsent(ScrollConsent.DECLINED, now = 1_000)
        assertEquals(ScrollConsent.DECLINED, preferences.record.first().consent)
        assertEquals(1_000L, preferences.record.first().consentAt)

        preferences.setConsent(ScrollConsent.AGREED, now = 2_000)
        assertEquals(ScrollConsent.AGREED, preferences.record.first().consent)
    }

    @Test
    fun connectingWithoutConsent_recordsNothing() = runTest {
        val preferences = createPreferences()
        preferences.onServiceConnected(now = 5_000)

        val record = preferences.record.first()
        assertFalse(record.connectedSinceConsent)
        assertNull(record.countingSince)
    }

    @Test
    fun connectingAfterAgreeing_startsTheHistoryOnce() = runTest {
        val preferences = createPreferences()
        preferences.setConsent(ScrollConsent.AGREED, now = 1_000)
        preferences.onServiceConnected(now = 5_000)
        preferences.onServiceConnected(now = 9_000)

        val record = preferences.record.first()
        assertTrue(record.connectedSinceConsent)
        assertEquals(5_000L, record.countingSince)
    }

    @Test
    fun turningOff_clearsTheConnectedFlag_butKeepsHistoryForTheDashboard() = runTest {
        val preferences = createPreferences()
        preferences.setConsent(ScrollConsent.AGREED, now = 1_000)
        preferences.onServiceConnected(now = 5_000)

        preferences.setConsent(ScrollConsent.DECLINED, now = 6_000)
        assertFalse(preferences.record.first().connectedSinceConsent)
        assertEquals(5_000L, preferences.record.first().countingSince)

        // Agreeing again starts "waiting for the service", not "the system switched it off".
        preferences.setConsent(ScrollConsent.AGREED, now = 7_000)
        assertFalse(preferences.record.first().connectedSinceConsent)
    }
}

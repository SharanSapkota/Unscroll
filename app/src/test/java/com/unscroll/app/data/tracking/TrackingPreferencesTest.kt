package com.unscroll.app.data.tracking

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

class TrackingPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.createPreferences() = TrackingPreferences(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.root.resolve("test.preferences_pb")
        },
    )

    @Test
    fun trackingIsOffByDefault_andCanBeTurnedOn() = runTest {
        val preferences = createPreferences()
        assertFalse(preferences.trackingEnabled.first())

        preferences.setTrackingEnabled(true)

        assertTrue(preferences.trackingEnabled.first())
    }

    @Test
    fun heartbeat_isNullUntilSaved_thenReturnsLatest() = runTest {
        val preferences = createPreferences()
        assertNull(preferences.lastHeartbeat())

        preferences.saveHeartbeat(5_000)
        preferences.saveHeartbeat(10_000)

        assertEquals(10_000L, preferences.lastHeartbeat())
    }
}

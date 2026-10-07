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
    fun freshInstall_trackingIsOn() = runTest {
        assertTrue(createPreferences().trackingEnabled.first())
    }

    @Test
    fun explicitOff_staysOff_andCanBeTurnedBackOn() = runTest {
        val preferences = createPreferences()

        preferences.setTrackingEnabled(false)
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

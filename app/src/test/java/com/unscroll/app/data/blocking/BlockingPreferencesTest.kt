package com.unscroll.app.data.blocking

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.unscroll.app.domain.blocking.BlockingSettings
import com.unscroll.app.domain.blocking.FrictionMode
import com.unscroll.app.domain.blocking.PendingFriction
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BlockingPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.createPreferences() = BlockingPreferences(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.root.resolve("test.preferences_pb")
        },
    )

    @Test
    fun defaults_waitTenMinutes() = runTest {
        assertEquals(BlockingSettings(FrictionMode.WAIT, 10, null), createPreferences().current(now = 0))
    }

    @Test
    fun weakerFriction_isPendingThenApplied() = runTest {
        val preferences = createPreferences()
        preferences.request(FrictionMode.TYPE_PHRASE, 5, now = 0)

        assertEquals(
            BlockingSettings(FrictionMode.WAIT, 10, PendingFriction(FrictionMode.TYPE_PHRASE, 5, 600_000)),
            preferences.current(now = 599_999),
        )
        assertEquals(BlockingSettings(FrictionMode.TYPE_PHRASE, 5, null), preferences.current(now = 600_000))
    }

    @Test
    fun strongerFriction_appliesNow_andCancelClearsPending() = runTest {
        val preferences = createPreferences()
        preferences.request(FrictionMode.WAIT, 60, now = 0)
        assertEquals(BlockingSettings(FrictionMode.WAIT, 60, null), preferences.current(now = 1))

        preferences.request(FrictionMode.WAIT, 5, now = 1)
        preferences.cancelPending(now = 2)
        assertEquals(BlockingSettings(FrictionMode.WAIT, 60, null), preferences.current(now = 10_000_000))
    }

    @Test
    fun debugShortCooldown_offByDefault_thenTenSeconds() = runTest {
        val preferences = createPreferences()
        assertEquals(false, preferences.current(now = 0).debugShortCooldown)

        // Unit tests run the debug variant, where the switch is available.
        preferences.setDebugShortCooldown(true)
        assertEquals(10_000L, preferences.current(now = 0).cooldownMillis)

        preferences.setDebugShortCooldown(false)
        assertEquals(10 * 60_000L, preferences.current(now = 0).cooldownMillis)
    }
}

package com.unscroll.app.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovedPreferencesMigrationTest {

    private val kept = booleanPreferencesKey("tracking_enabled")

    @Test
    fun removesTheOldCooldownAndFrictionKeys_keepsEverythingElse() = runTest {
        val old = mutablePreferencesOf(
            stringPreferencesKey("blocking_friction_mode") to "TYPE_PHRASE",
            intPreferencesKey("blocking_cooldown_minutes") to 30,
            stringPreferencesKey("blocking_pending_friction_mode") to "WAIT",
            intPreferencesKey("blocking_pending_cooldown_minutes") to 5,
            longPreferencesKey("blocking_pending_applies_at") to 123L,
            booleanPreferencesKey("debug_short_cooldown") to true,
            kept to true,
        )
        assertTrue(RemovedPreferencesMigration.shouldMigrate(old))

        val migrated = RemovedPreferencesMigration.migrate(old)

        assertEquals(mapOf<Preferences.Key<*>, Any>(Pair(kept, true)), migrated.asMap())
        assertFalse(RemovedPreferencesMigration.shouldMigrate(migrated))
    }

    @Test
    fun removesTheOldPerSessionPillThresholds_keepsTheDailyOnes() = runTest {
        val daily = intPreferencesKey("overlay_daily_warning_minutes")
        val old = mutablePreferencesOf(
            booleanPreferencesKey("overlay_show_today_total") to true,
            intPreferencesKey("overlay_warning_minutes") to 10,
            intPreferencesKey("overlay_danger_minutes") to 20,
            intPreferencesKey("overlay_swipe_warning") to 50,
            intPreferencesKey("overlay_swipe_danger") to 100,
            daily to 45,
        )
        assertTrue(RemovedPreferencesMigration.shouldMigrate(old))

        val migrated = RemovedPreferencesMigration.migrate(old)

        assertEquals(mapOf<Preferences.Key<*>, Any>(Pair(daily, 45)), migrated.asMap())
    }

    @Test
    fun nothingToRemove_doesNotMigrate() = runTest {
        assertFalse(RemovedPreferencesMigration.shouldMigrate(mutablePreferencesOf(kept to false)))
    }
}

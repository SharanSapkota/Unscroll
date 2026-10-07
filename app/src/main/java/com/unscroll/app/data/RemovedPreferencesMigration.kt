package com.unscroll.app.data

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Deletes preferences of features that were removed, so nothing stale stays on the device: the
 * cooldown and friction settings (wait vs typed phrase, cooldown length, a pending friction change
 * and the debug 10-second cooldown; changes apply immediately now), and the pill's per-session
 * color thresholds and "today's total" switch (the pill shows today's total and colors by it now,
 * with new daily threshold keys). Runs once, when the DataStore is first read and one of the keys
 * is still there.
 */
object RemovedPreferencesMigration : DataMigration<Preferences> {

    internal val REMOVED_KEYS: List<Preferences.Key<*>> = listOf(
        stringPreferencesKey("blocking_friction_mode"),
        intPreferencesKey("blocking_cooldown_minutes"),
        stringPreferencesKey("blocking_pending_friction_mode"),
        intPreferencesKey("blocking_pending_cooldown_minutes"),
        longPreferencesKey("blocking_pending_applies_at"),
        booleanPreferencesKey("debug_short_cooldown"),
        booleanPreferencesKey("overlay_show_today_total"),
        intPreferencesKey("overlay_warning_minutes"),
        intPreferencesKey("overlay_danger_minutes"),
        intPreferencesKey("overlay_swipe_warning"),
        intPreferencesKey("overlay_swipe_danger"),
    )

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        REMOVED_KEYS.any { it in currentData }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply { REMOVED_KEYS.forEach { remove(it) } }.toPreferences()

    override suspend fun cleanUp() = Unit
}

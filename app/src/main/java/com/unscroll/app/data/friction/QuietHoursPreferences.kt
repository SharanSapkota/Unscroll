package com.unscroll.app.data.friction

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.unscroll.app.domain.friction.QuietHours
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Global quiet hours (off by default) in the preferences DataStore. */
@Singleton
class QuietHoursPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val quietHours: Flow<QuietHours> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val defaults = QuietHours()
            QuietHours(
                enabled = prefs[ENABLED] ?: defaults.enabled,
                startMinute = prefs[START] ?: defaults.startMinute,
                endMinute = prefs[END] ?: defaults.endMinute,
            )
        }
        .distinctUntilChanged()

    suspend fun current(): QuietHours = quietHours.first()

    suspend fun save(quietHours: QuietHours) {
        dataStore.edit {
            it[ENABLED] = quietHours.enabled
            it[START] = quietHours.startMinute.coerceIn(0, MINUTES_PER_DAY - 1)
            it[END] = quietHours.endMinute.coerceIn(0, MINUTES_PER_DAY - 1)
        }
    }

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
        val ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val START = intPreferencesKey("quiet_hours_start_minute")
        val END = intPreferencesKey("quiet_hours_end_minute")
    }
}

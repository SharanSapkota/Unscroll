package com.unscroll.app.data.apps

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Whether the default apps were seeded into the tracked list. Cleared with "Delete all my data",
 * so the defaults come back like on a fresh install; never re-seeded otherwise, so a default the
 * user removed stays removed.
 */
@Singleton
class TrackedAppsPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val seeded: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[SEEDED] ?: false }
        .distinctUntilChanged()

    suspend fun setSeeded() {
        dataStore.edit { it[SEEDED] = true }
    }

    private companion object {
        val SEEDED = booleanPreferencesKey("tracked_apps_seeded")
    }
}

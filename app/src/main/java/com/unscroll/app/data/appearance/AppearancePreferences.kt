package com.unscroll.app.data.appearance

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

/** How the app looks: the brand palette (default) or Material You dynamic color (Android 12+). */
@Singleton
class AppearancePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val dynamicColor: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[DYNAMIC_COLOR] ?: false }
        .distinctUntilChanged()

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    private companion object {
        val DYNAMIC_COLOR = booleanPreferencesKey("appearance_dynamic_color")
    }
}

package com.unscroll.app.data.appearance

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.unscroll.app.domain.fox.FoxSettings
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** How the app looks: brand palette or dynamic color (Android 12+), and the fox mascot. */
@Singleton
class AppearancePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val preferences: Flow<Preferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val dynamicColor: Flow<Boolean> = preferences
        .map { it[DYNAMIC_COLOR] ?: false }
        .distinctUntilChanged()

    /** The fox mascot and its speech bubbles. Both on by default. */
    val fox: Flow<FoxSettings> = preferences
        .map { FoxSettings(showFox = it[SHOW_FOX] ?: true, messages = it[FOX_MESSAGES] ?: true) }
        .distinctUntilChanged()

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setShowFox(show: Boolean) {
        dataStore.edit { it[SHOW_FOX] = show }
    }

    suspend fun setFoxMessages(enabled: Boolean) {
        dataStore.edit { it[FOX_MESSAGES] = enabled }
    }

    private companion object {
        val DYNAMIC_COLOR = booleanPreferencesKey("appearance_dynamic_color")
        val SHOW_FOX = booleanPreferencesKey("appearance_show_fox")
        val FOX_MESSAGES = booleanPreferencesKey("appearance_fox_messages")
    }
}

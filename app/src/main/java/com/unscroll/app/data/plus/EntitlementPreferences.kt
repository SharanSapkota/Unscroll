package com.unscroll.app.data.plus

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Plus state kept on the phone: the last state Play Billing reported (so Plus works offline), the
 * "Plus ended" notice, the free apps the user picked and, in debug builds only, a forced state.
 */
@Singleton
class EntitlementPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val preferences: Flow<Preferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    /** The last state Billing reported, or null if it never answered on this install. */
    val cachedPlus: Flow<Boolean?> = preferences.map { it[CACHED_PLUS] }.distinctUntilChanged()

    val plusEnded: Flow<Boolean> = preferences.map { it[PLUS_ENDED] ?: false }.distinctUntilChanged()

    /** Debug builds only (EntitlementRepository ignores it in release): null follows Billing. */
    val debugOverride: Flow<Boolean?> = preferences.map { it[DEBUG_OVERRIDE] }.distinctUntilChanged()

    val pickedApps: Flow<Set<String>> = preferences.map { it[PICKED_APPS] ?: emptySet() }.distinctUntilChanged()

    /**
     * Saves Billing's answer, and the "Plus ended" notice with it ([ended] null: unchanged), in one
     * edit so nobody sees Plus off before the notice (which decides which apps stay active).
     */
    suspend fun saveCachedPlus(plus: Boolean, ended: Boolean? = null) {
        dataStore.edit {
            it[CACHED_PLUS] = plus
            if (ended != null) it[PLUS_ENDED] = ended
        }
    }

    suspend fun setPlusEnded(ended: Boolean) {
        dataStore.edit { it[PLUS_ENDED] = ended }
    }

    /** Debug builds only. Same single edit as [saveCachedPlus]. */
    suspend fun setDebugOverride(plus: Boolean?, ended: Boolean? = null) {
        dataStore.edit {
            if (plus == null) it.remove(DEBUG_OVERRIDE) else it[DEBUG_OVERRIDE] = plus
            if (ended != null) it[PLUS_ENDED] = ended
        }
    }

    suspend fun savePickedApps(packages: Set<String>) {
        dataStore.edit { it[PICKED_APPS] = packages }
    }

    private companion object {
        val CACHED_PLUS = booleanPreferencesKey("plus_cached")
        val PLUS_ENDED = booleanPreferencesKey("plus_ended_notice")
        val DEBUG_OVERRIDE = booleanPreferencesKey("plus_debug_override")
        val PICKED_APPS = stringSetPreferencesKey("plus_picked_apps")
    }
}

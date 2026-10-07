package com.unscroll.app.data.tracking

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.unscroll.app.domain.session.HeartbeatStore
import com.unscroll.app.domain.tracking.TrackingStartRules
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Tracking on/off switch and the session heartbeat, stored in the preferences DataStore. */
@Singleton
class TrackingPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : HeartbeatStore {

    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    /**
     * On by default: only a stored `false` (the user turned it off on purpose) keeps it off. The
     * key is written only when the user flips the switch, so "never set" and "off" stay apart.
     */
    val trackingEnabled: Flow<Boolean> = preferences
        .map { TrackingStartRules.enabled(it[TRACKING_ENABLED]) }
        .distinctUntilChanged()

    /** Saves the user's explicit choice. */
    suspend fun setTrackingEnabled(enabled: Boolean) {
        dataStore.edit { it[TRACKING_ENABLED] = enabled }
    }

    override suspend fun saveHeartbeat(timestamp: Long) {
        dataStore.edit { it[HEARTBEAT] = timestamp }
    }

    override suspend fun lastHeartbeat(): Long? = preferences.first()[HEARTBEAT]

    private companion object {
        val TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
        val HEARTBEAT = longPreferencesKey("session_heartbeat")
    }
}

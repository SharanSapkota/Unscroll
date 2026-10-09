package com.unscroll.app.data.section

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.section.SectionBlockMode
import com.unscroll.app.domain.section.SectionBlockingSettings
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Section blocking's settings, in the preferences DataStore. Settings only: the consent, the kill
 * switch, which apps and when, and the debug inspector switch. Nothing about what was on screen
 * is ever stored.
 */
@Singleton
class SectionBlockingPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val data: Flow<Preferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val settings: Flow<SectionBlockingSettings> = data
        .map { prefs ->
            SectionBlockingSettings(
                consent = prefs[CONSENT]?.let { name -> ScrollConsent.entries.firstOrNull { it.name == name } }
                    ?: ScrollConsent.NOT_ASKED,
                turnedOff = prefs[TURNED_OFF] ?: false,
                blockedApps = prefs[BLOCKED_APPS] ?: emptySet(),
                afterLimitApps = prefs[AFTER_LIMIT_APPS] ?: emptySet(),
                inspector = prefs[INSPECTOR] ?: false,
            )
        }
        .distinctUntilChanged()

    /** The service connected at least once since the last "I agree" (for the re-enable banner). */
    val connectedSinceConsent: Flow<Boolean> = data.map { it[CONNECTED_SINCE_CONSENT] ?: false }.distinctUntilChanged()

    suspend fun setConsent(consent: ScrollConsent, now: Long) {
        dataStore.edit {
            if (it[CONSENT] != consent.name) it[CONNECTED_SINCE_CONSENT] = false
            it[CONSENT] = consent.name
            it[CONSENT_AT] = now
            // Agreeing again starts with section blocking on.
            if (consent == ScrollConsent.AGREED) it[TURNED_OFF] = false
        }
    }

    suspend fun onServiceConnected() {
        dataStore.edit {
            if (it[CONSENT] == ScrollConsent.AGREED.name) it[CONNECTED_SINCE_CONSENT] = true
        }
    }

    suspend fun setTurnedOff(turnedOff: Boolean) = dataStore.edit { it[TURNED_OFF] = turnedOff }

    suspend fun setBlocked(packageName: String, blocked: Boolean) = dataStore.edit {
        it.toggle(BLOCKED_APPS, packageName, on = blocked)
    }

    suspend fun setMode(packageName: String, mode: SectionBlockMode) = dataStore.edit {
        it.toggle(AFTER_LIMIT_APPS, packageName, on = mode == SectionBlockMode.AFTER_LIMIT)
    }

    suspend fun setInspector(on: Boolean) = dataStore.edit { it[INSPECTOR] = on }

    private fun MutablePreferences.toggle(key: Preferences.Key<Set<String>>, packageName: String, on: Boolean) {
        val current = this[key] ?: emptySet()
        this[key] = if (on) current + packageName else current - packageName
    }

    private companion object {
        val CONSENT = stringPreferencesKey("section_consent")
        val CONSENT_AT = longPreferencesKey("section_consent_at")
        val CONNECTED_SINCE_CONSENT = booleanPreferencesKey("section_connected_since_consent")
        val TURNED_OFF = booleanPreferencesKey("section_turned_off")
        val BLOCKED_APPS = stringSetPreferencesKey("section_blocked_apps")
        val AFTER_LIMIT_APPS = stringSetPreferencesKey("section_after_limit_apps")
        val INSPECTOR = booleanPreferencesKey("section_inspector")
    }
}

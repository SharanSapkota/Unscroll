package com.unscroll.app.data.scroll

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.unscroll.app.domain.scroll.ScrollConsent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What the scroll-counting state is built from, as stored. */
data class ScrollCountingRecord(
    val consent: ScrollConsent = ScrollConsent.NOT_ASKED,
    /** When the user last answered the disclosure, or null. */
    val consentAt: Long? = null,
    /** The service connected at least once since the last "I agree". */
    val connectedSinceConsent: Boolean = false,
    /** First time the service ever connected: swipe stats start here. Null = never enabled. */
    val countingSince: Long? = null,
)

/** Consent and history for the optional accessibility service, in the preferences DataStore. */
@Singleton
class ScrollCountingPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val record: Flow<ScrollCountingRecord> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            ScrollCountingRecord(
                consent = prefs[CONSENT]?.let { name -> ScrollConsent.entries.firstOrNull { it.name == name } }
                    ?: ScrollConsent.NOT_ASKED,
                consentAt = prefs[CONSENT_AT],
                connectedSinceConsent = prefs[CONNECTED_SINCE_CONSENT] ?: false,
                countingSince = prefs[COUNTING_SINCE],
            )
        }
        .distinctUntilChanged()

    /** Saves the answer from the disclosure screen (or "Turn off", which is [ScrollConsent.DECLINED]). */
    suspend fun setConsent(consent: ScrollConsent, now: Long) {
        dataStore.edit {
            if (it[CONSENT] != consent.name) it[CONNECTED_SINCE_CONSENT] = false
            it[CONSENT] = consent.name
            it[CONSENT_AT] = now
        }
    }

    /** The service connected. Only counts as history once the user has agreed. */
    suspend fun onServiceConnected(now: Long) {
        dataStore.edit {
            if (it[CONSENT] != ScrollConsent.AGREED.name) return@edit
            it[CONNECTED_SINCE_CONSENT] = true
            if (it[COUNTING_SINCE] == null) it[COUNTING_SINCE] = now
        }
    }

    /**
     * "Delete usage history": swipe stats start again from [now] if the service is counting right
     * now, otherwise they are hidden until it connects again.
     */
    suspend fun resetHistory(now: Long, countingNow: Boolean) {
        dataStore.edit {
            if (countingNow) it[COUNTING_SINCE] = now else it.remove(COUNTING_SINCE)
        }
    }

    private companion object {
        val CONSENT = stringPreferencesKey("scroll_consent")
        val CONSENT_AT = longPreferencesKey("scroll_consent_at")
        val CONNECTED_SINCE_CONSENT = booleanPreferencesKey("scroll_connected_since_consent")
        val COUNTING_SINCE = longPreferencesKey("scroll_counting_since")
    }
}

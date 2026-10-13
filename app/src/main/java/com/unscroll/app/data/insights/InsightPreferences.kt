package com.unscroll.app.data.insights

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.unscroll.app.domain.insights.Insight
import com.unscroll.app.domain.insights.InsightCategory
import com.unscroll.app.domain.insights.InsightHistory
import com.unscroll.app.domain.insights.InsightTone
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Settings › Insights. On by default, Honest tone, no age, weekly notification off. */
data class InsightSettings(
    val enabled: Boolean = true,
    val tone: InsightTone = InsightTone.DEFAULT,
    /** The user's age for the life projection, or null (then 50 more years). */
    val age: Int? = null,
    val weeklyNotification: Boolean = false,
) {
    companion object {
        val AGE_RANGE = 10..99
    }
}

/** Which messages were shown, so the rotation rules hold across app restarts. */
data class InsightRotationState(
    val history: InsightHistory = InsightHistory(),
    /** The Home card's message and the local day (epoch day) it was picked on. */
    val currentId: String? = null,
    val currentDay: Long? = null,
    /** How many messages the Home card showed on [shownDay] (at most 10 a day). */
    val shownDay: Long? = null,
    val shownCount: Int = 0,
    /** The weekly life projection card and its day. */
    val weeklyId: String? = null,
    val weeklyDay: Long? = null,
    /** The day the weekly notification was last posted. */
    val notifiedDay: Long? = null,
) {
    fun shownOn(day: Long): Int = if (shownDay == day) shownCount else 0
}

/**
 * Insights settings and the rotation history in the preferences DataStore. Only template ids,
 * days and counts are stored: never a message's numbers.
 */
@Singleton
class InsightPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val preferences: Flow<Preferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val settings: Flow<InsightSettings> = preferences
        .map { prefs ->
            InsightSettings(
                enabled = prefs[ENABLED] ?: true,
                tone = InsightTone.entries.firstOrNull { it.name == prefs[TONE] } ?: InsightTone.DEFAULT,
                age = prefs[AGE]?.takeIf { it in InsightSettings.AGE_RANGE },
                weeklyNotification = prefs[WEEKLY_NOTIFICATION] ?: false,
            )
        }
        .distinctUntilChanged()

    val rotation: Flow<InsightRotationState> = preferences.map { it.rotation() }.distinctUntilChanged()

    suspend fun currentSettings(): InsightSettings = settings.first()

    suspend fun currentRotation(): InsightRotationState = rotation.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setTone(tone: InsightTone) {
        dataStore.edit { it[TONE] = tone.name }
    }

    /** Null (or anything outside 10–99) clears it. */
    suspend fun setAge(age: Int?) {
        dataStore.edit { if (age == null || age !in InsightSettings.AGE_RANGE) it.remove(AGE) else it[AGE] = age }
    }

    suspend fun setWeeklyNotification(enabled: Boolean) {
        dataStore.edit { it[WEEKLY_NOTIFICATION] = enabled }
    }

    /** The Home card shows [insight] on [day]: one more of the day's messages, recorded in the history. */
    suspend fun recordShown(insight: Insight, day: Long, now: Long) {
        dataStore.edit { prefs ->
            val state = prefs.rotation()
            prefs.writeHistory(state.history.record(insight, now))
            prefs[CURRENT_ID] = insight.templateId
            prefs[CURRENT_DAY] = day
            prefs[SHOWN_DAY] = day
            prefs[SHOWN_COUNT] = state.shownOn(day) + 1
        }
    }

    /** The weekly card shows [insight] on [day]. */
    suspend fun recordWeekly(insight: Insight, day: Long, now: Long) {
        dataStore.edit { prefs ->
            prefs.writeHistory(prefs.rotation().history.record(insight, now))
            prefs[WEEKLY_ID] = insight.templateId
            prefs[WEEKLY_DAY] = day
        }
    }

    suspend fun recordNotified(day: Long) {
        dataStore.edit { it[NOTIFIED_DAY] = day }
    }

    private fun Preferences.rotation() = InsightRotationState(
        history = InsightHistory(
            recentIds = this[RECENT]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
            lastCategory = InsightCategory.entries.firstOrNull { it.name == this[LAST_CATEGORY] },
            lastLifeAt = this[LAST_LIFE_AT],
        ),
        currentId = this[CURRENT_ID],
        currentDay = this[CURRENT_DAY],
        shownDay = this[SHOWN_DAY],
        shownCount = this[SHOWN_COUNT] ?: 0,
        weeklyId = this[WEEKLY_ID],
        weeklyDay = this[WEEKLY_DAY],
        notifiedDay = this[NOTIFIED_DAY],
    )

    private fun MutablePreferences.writeHistory(history: InsightHistory) {
        this[RECENT] = history.recentIds.joinToString(SEPARATOR)
        history.lastCategory?.let { this[LAST_CATEGORY] = it.name } ?: remove(LAST_CATEGORY)
        history.lastLifeAt?.let { this[LAST_LIFE_AT] = it } ?: remove(LAST_LIFE_AT)
    }

    private companion object {
        const val SEPARATOR = ","
        val ENABLED = booleanPreferencesKey("insights_enabled")
        val TONE = stringPreferencesKey("insights_tone")
        val AGE = intPreferencesKey("insights_age")
        val WEEKLY_NOTIFICATION = booleanPreferencesKey("insights_weekly_notification")
        val RECENT = stringPreferencesKey("insights_recent_ids")
        val LAST_CATEGORY = stringPreferencesKey("insights_last_category")
        val LAST_LIFE_AT = longPreferencesKey("insights_last_life_at")
        val CURRENT_ID = stringPreferencesKey("insights_current_id")
        val CURRENT_DAY = longPreferencesKey("insights_current_day")
        val SHOWN_DAY = longPreferencesKey("insights_shown_day")
        val SHOWN_COUNT = intPreferencesKey("insights_shown_count")
        val WEEKLY_ID = stringPreferencesKey("insights_weekly_id")
        val WEEKLY_DAY = longPreferencesKey("insights_weekly_day")
        val NOTIFIED_DAY = longPreferencesKey("insights_notified_day")
    }
}

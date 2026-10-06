package com.unscroll.app.data.goals

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The daily time goal across tracked apps (M8), in the preferences DataStore. Off by default. */
@Singleton
class GoalPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Minutes per day, or null when no goal is set. */
    val dailyGoalMinutes: Flow<Int?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[DAILY_GOAL]?.takeIf { it > 0 } }
        .distinctUntilChanged()

    suspend fun setDailyGoal(minutes: Int?) {
        dataStore.edit {
            if (minutes == null || minutes <= 0) it.remove(DAILY_GOAL) else it[DAILY_GOAL] = minutes
        }
    }

    private companion object {
        val DAILY_GOAL = intPreferencesKey("daily_goal_minutes")
    }
}

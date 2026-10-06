package com.unscroll.app.data.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DataStoreOnboardingRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OnboardingRepository {

    override val onboardingCompleted: Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[ONBOARDING_COMPLETED] ?: false }
        .distinctUntilChanged()

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences -> preferences[ONBOARDING_COMPLETED] = completed }
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}

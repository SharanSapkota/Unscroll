package com.unscroll.app.data.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.unscroll.app.data.db.UnscrollDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Empties everything Unscroll stores: every Room table (sessions, limits, logs) and the preferences
 * DataStore (settings, goal, consent, the onboarding flag). The app is then like a fresh install.
 */
internal suspend fun wipeAllStorage(database: UnscrollDatabase, dataStore: DataStore<Preferences>) {
    withContext(Dispatchers.IO) { database.clearAllTables() }
    dataStore.edit { it.clear() }
}

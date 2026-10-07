package com.unscroll.app.data.history

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unscroll.app.data.db.AppLimitEntity
import com.unscroll.app.data.db.SessionEntity
import com.unscroll.app.data.db.UnscrollDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** "Delete all my data" empties every table and every preference. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class StorageWipeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: UnscrollDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            UnscrollDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun wipe_emptiesRoomAndDataStore() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.root.resolve("wipe.preferences_pb")
        }
        val onboarded = booleanPreferencesKey("onboarding_completed")
        dataStore.edit { it[onboarded] = true }
        database.sessionDao().insert(SessionEntity(packageName = "com.instagram.android", startTime = 1_000))
        database.blockingDao().upsertLimit(
            AppLimitEntity(
                packageName = "com.instagram.android",
                dailyLimitMinutes = 30,
                blockedAlways = false,
                scheduleEnabled = false,
                scheduleDays = 0,
                scheduleStartMinute = 0,
                scheduleEndMinute = 0,
            ),
        )

        wipeAllStorage(database, dataStore)

        assertEquals(emptyList<SessionEntity>(), database.sessionDao().getAll())
        assertNull(database.blockingDao().getLimit("com.instagram.android"))
        assertTrue(dataStore.data.first().asMap().isEmpty())
    }
}

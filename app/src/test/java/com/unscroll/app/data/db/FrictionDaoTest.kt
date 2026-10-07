package com.unscroll.app.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unscroll.app.data.friction.FrictionRepository
import com.unscroll.app.data.friction.NudgeKind
import com.unscroll.app.domain.friction.FrictionSettings
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class FrictionDaoTest {

    private lateinit var database: UnscrollDatabase
    private lateinit var repository: FrictionRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            UnscrollDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = FrictionRepository(database.frictionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun settings_defaultUntilSaved_thenResetDeletesTheRow() = runTest {
        assertEquals(FrictionSettings.DEFAULT, repository.getSettings(INSTAGRAM))

        val custom = FrictionSettings(breakIntervalMinutes = 20, nudgeThresholds = listOf(3, 30), tintEnabled = true)
        repository.saveSettings(INSTAGRAM, custom)
        assertEquals(custom, repository.getSettings(INSTAGRAM))
        assertEquals(mapOf(INSTAGRAM to custom), repository.observeSettings().first())

        repository.resetToDefaults(INSTAGRAM)
        assertEquals(FrictionSettings.DEFAULT, repository.getSettings(INSTAGRAM))
        assertEquals(emptyMap<String, FrictionSettings>(), repository.observeSettings().first())
    }

    @Test
    fun settings_areNormalizedOnSave() = runTest {
        repository.saveSettings(INSTAGRAM, FrictionSettings(breakIntervalMinutes = 0, nudgeThresholds = listOf(20, 5, 5)))
        val stored = repository.getSettings(INSTAGRAM)
        assertEquals(1, stored.breakIntervalMinutes)
        assertEquals(listOf(5, 20), stored.nudgeThresholds)
    }

    @Test
    fun swipeBreak_isOffByDefault_andRoundTrips() = runTest {
        assertEquals(null, repository.getSettings(INSTAGRAM).swipeBreakAfter)

        repository.saveSettings(INSTAGRAM, FrictionSettings(swipeBreakAfter = 100))
        assertEquals(100, repository.getSettings(INSTAGRAM).swipeBreakAfter)

        repository.saveSettings(INSTAGRAM, FrictionSettings(swipeBreakAfter = 0))
        assertEquals(null, repository.getSettings(INSTAGRAM).swipeBreakAfter)
    }

    @Test
    fun deletingHistory_clearsNudges_butKeepsSettings() = runTest {
        val today = LocalDate.of(2026, 10, 6)
        repository.saveSettings(INSTAGRAM, FrictionSettings(breakIntervalMinutes = 20))
        repository.recordNudges(INSTAGRAM, today, NudgeKind.OPENS, listOf(5), now = 1)

        database.frictionDao().deleteAllNudges()

        assertEquals(emptySet<Int>(), repository.sentNudges(INSTAGRAM, today, NudgeKind.OPENS))
        assertEquals(20, repository.getSettings(INSTAGRAM).breakIntervalMinutes)
    }

    @Test
    fun nudgeLog_perAppDayAndKind_neverDuplicated() = runTest {
        val today = LocalDate.of(2026, 10, 6)
        repository.recordNudges(INSTAGRAM, today, NudgeKind.OPENS, listOf(5, 10), now = 1)
        repository.recordNudges(INSTAGRAM, today, NudgeKind.OPENS, listOf(10), now = 2) // ignored duplicate
        repository.recordNudges(INSTAGRAM, today, NudgeKind.LIMIT, listOf(80), now = 3)
        repository.recordNudges(TIKTOK, today, NudgeKind.OPENS, listOf(5), now = 4)

        assertEquals(setOf(5, 10), repository.sentNudges(INSTAGRAM, today, NudgeKind.OPENS))
        assertEquals(setOf(80), repository.sentNudges(INSTAGRAM, today, NudgeKind.LIMIT))
        // A new day starts empty.
        assertEquals(emptySet<Int>(), repository.sentNudges(INSTAGRAM, today.plusDays(1), NudgeKind.OPENS))
    }

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val TIKTOK = "com.zhiliaoapp.musically"
    }
}

package com.unscroll.app.data.overlay

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unscroll.app.data.db.SessionEntity
import com.unscroll.app.data.db.UnscrollDatabase
import com.unscroll.app.domain.overlay.PillRules
import com.unscroll.app.domain.overlay.PillToday
import com.unscroll.app.domain.session.ActiveSession
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The pill's base from real sessions: earlier ones today, clipped to midnight, open one left out. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PillTotalsRepositoryTest {

    private val zone: ZoneId = ZoneId.of("Europe/Helsinki")
    private val app = "com.instagram.android"
    private val minute = 60_000L

    private lateinit var database: UnscrollDatabase
    private lateinit var repository: PillTotalsRepository

    private fun at(hour: Int, minute: Int, second: Int = 0, day: Int = 7): Long =
        LocalDateTime.of(2026, 10, day, hour, minute, second).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            UnscrollDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = PillTotalsRepository(database.sessionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun session(start: Long, end: Long?, swipes: Int = 0, pkg: String = app): Long =
        database.sessionDao().insert(SessionEntity(packageName = pkg, startTime = start, endTime = end, scrollCount = swipes))

    @Test
    fun base_sumsEarlierSessionsToday_clipsTheOneFromYesterday_leavesOutTheOpenOne() = runTest {
        session(at(23, 50, day = 6), at(0, 10), swipes = 40) // 10 min after midnight count
        session(at(9, 0), at(9, 20), swipes = 100) // 20 min
        session(at(11, 0), at(11, 12, 30), swipes = 40) // 12.5 min
        session(at(10, 0), at(10, 30), pkg = "com.zhiliaoapp.musically") // another app
        val openStart = at(14, 0)
        val openId = session(openStart, end = null, swipes = 7)

        val base = repository.base(ActiveSession(openId, app, openStart), now = at(14, 15, 12), zone = zone)

        assertEquals(42 * minute + 30_000, base.completedMillis)
        // Swipes: sessions that started today before this one (yesterday's isn't today's).
        assertEquals(140, base.completedSwipes)
        assertEquals(at(0, 0), base.dayStart)
        // Live: + this visit's 15:12, and its own swipes come from the session counter.
        assertEquals("57:42", PillRules.formatTime(PillToday.totalMillis(base, at(14, 15, 12), zone)))
        assertEquals(147, PillToday.swipes(base, sessionSwipes = 7, now = at(14, 15, 12), zone = zone))
    }

    @Test
    fun base_openSessionFromBeforeMidnight_startsFromZero() = runTest {
        session(at(22, 0, day = 6), at(22, 30, day = 6), swipes = 20)
        val openStart = at(23, 55, day = 6)
        val openId = session(openStart, end = null)

        val base = repository.base(ActiveSession(openId, app, openStart), now = at(0, 2), zone = zone)

        assertEquals(0L, base.completedMillis)
        assertEquals(0, base.completedSwipes)
        assertEquals(2 * minute, PillToday.totalMillis(base, at(0, 2), zone))
    }

    @Test
    fun base_firstVisitToday_isZero_andCarriesTheFloor() = runTest {
        val openStart = at(8, 0)
        val openId = session(openStart, end = null)

        val base = repository.base(ActiveSession(openId, app, openStart), now = openStart, zone = zone, floorMillis = 0)

        assertEquals(0L, base.completedMillis)
        assertEquals(0L, PillToday.totalMillis(base, openStart, zone))
    }

    @Test
    fun swipesBetween_countsSessionsThatStartedInTheRange() = runTest {
        session(at(9, 0), at(9, 5), swipes = 3)
        session(at(10, 0), at(10, 5), swipes = 5)
        session(at(12, 0), null, swipes = 50)
        session(at(9, 30), at(9, 40), swipes = 70, pkg = "com.facebook.katana")

        assertEquals(8, database.sessionDao().swipesBetween(app, at(0, 0), at(12, 0)))
        assertEquals(5, database.sessionDao().swipesBetween(app, at(9, 1), at(12, 0)))
        assertEquals(0, database.sessionDao().swipesBetween(app, at(13, 0), at(14, 0)))
    }
}

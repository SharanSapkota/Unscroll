package com.unscroll.app.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unscroll.app.data.reels.ReelsToday
import com.unscroll.app.data.reels.ReelsUsageRepository
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Reels-only timer's segments: totals clipped like sessions, swipes, orphans, deletion. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SectionSegmentDaoTest {

    private val zone: ZoneId = ZoneId.of("Europe/Helsinki")
    private val ig = "com.instagram.android"
    private val tt = "com.zhiliaoapp.musically"
    private val minute = 60_000L

    private lateinit var database: UnscrollDatabase
    private lateinit var dao: SectionSegmentDao

    private fun at(hour: Int, minute: Int, day: Int = 7): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            UnscrollDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.sectionSegmentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insert(start: Long, end: Long?, section: String = "REELS", swipes: Int = 0, pkg: String = ig): Long =
        dao.insert(SectionSegmentEntity(sessionId = 1, packageName = pkg, startTime = start, endTime = end, section = section, swipes = swipes))

    @Test
    fun totals_clipAtMidnight_runOpenSegmentsUntilNow_andFilterTheSection() = runTest {
        insert(at(23, 50, day = 6), at(0, 20), swipes = 10) // 20 min today, swipes belong to yesterday
        insert(at(9, 0), at(9, 15), swipes = 25)
        insert(at(9, 15), at(9, 45), section = "OTHER", swipes = 0)
        insert(at(10, 0), null, swipes = 5) // open: until now
        insert(at(9, 0), at(9, 30), swipes = 7, pkg = tt)

        val now = at(10, 10)
        val rows = dao.totals("REELS", rangeStart = at(0, 0), rangeEnd = now, now = now).associateBy { it.packageName }

        assertEquals(45 * minute, rows.getValue(ig).totalMillis)
        assertEquals(30, rows.getValue(ig).swipes)
        assertEquals(30 * minute, rows.getValue(tt).totalMillis)
        assertEquals(30 * minute, dao.totals("OTHER", at(0, 0), now, now).single().totalMillis)

        // Yesterday: only the part before midnight, with its swipes.
        val yesterday = dao.totals("REELS", at(0, 0, day = 6), at(0, 0), now).single()
        assertEquals(10 * minute, yesterday.totalMillis)
        assertEquals(10, yesterday.swipes)
    }

    @Test
    fun updatesAndOrphans() = runTest {
        val id = insert(at(8, 0), null)
        dao.updateSwipes(id, 12)
        insert(at(9, 0), null)
        // A killed process: open segments end at the last heartbeat, never before their start.
        assertEquals(2, dao.closeAllOpen(at(8, 30)))
        val all = dao.getAll()
        assertEquals(listOf(at(8, 30), at(9, 0)), all.map { it.endTime })
        assertEquals(12, all.first().swipes)

        val closed = insert(at(11, 0), null)
        dao.close(closed, at(10, 0))
        assertEquals(at(11, 0), dao.getAll().last().endTime)

        assertEquals(3, dao.deleteAll())
    }

    @Test
    fun reelsUsage_today() = runTest {
        insert(at(23, 0, day = 6), at(23, 30, day = 6), swipes = 99)
        insert(at(9, 0), at(9, 12), swipes = 40)
        insert(at(9, 12), at(9, 20), section = "OTHER")
        val today = ReelsUsageRepository(dao).today(now = at(12, 0), zone = zone)
        assertEquals(mapOf(ig to ReelsToday(12 * minute, 40)), today)
    }
}

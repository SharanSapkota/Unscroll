package com.unscroll.app.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * In-memory Room tests for the dashboard aggregation queries. Times are minutes after a fixed
 * "midnight" so the expected numbers are easy to read.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SessionDaoUsageQueriesTest {

    private lateinit var database: UnscrollDatabase
    private lateinit var dao: SessionDao

    private val day = 24 * 60 * MINUTE
    private val midnight = 100 * day // Start of "today".
    private val yesterday = midnight - day
    private val tomorrow = midnight + day

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            UnscrollDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.sessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insert(packageName: String, start: Long, end: Long?) =
        dao.insert(SessionEntity(packageName = packageName, startTime = start, endTime = end))

    private fun minutes(millis: Long) = millis / MINUTE

    @Test
    fun appTotals_splitsMidnightSpanningSessionBetweenDays() = runTest {
        // 23:50 yesterday to 00:20 today.
        insert(INSTAGRAM, midnight - 10 * MINUTE, midnight + 20 * MINUTE)
        val now = midnight + 12 * 60 * MINUTE

        val today = dao.appTotals(midnight, tomorrow, now)
        val before = dao.appTotals(yesterday, midnight, now)

        assertEquals(listOf(AppTotalRow(INSTAGRAM, 20 * MINUTE)), today)
        assertEquals(listOf(AppTotalRow(INSTAGRAM, 10 * MINUTE)), before)
    }

    @Test
    fun appTotals_openSessionRunsUntilNow() = runTest {
        insert(TIKTOK, midnight + 60 * MINUTE, null)
        val now = midnight + 105 * MINUTE

        assertEquals(
            listOf(AppTotalRow(TIKTOK, 45 * MINUTE)),
            dao.appTotals(midnight, tomorrow, now),
        )
    }

    @Test
    fun appTotals_openSessionInPastPeriod_isClippedToPeriodEnd() = runTest {
        // Opened yesterday at 23:00 and still running.
        insert(TIKTOK, midnight - 60 * MINUTE, null)
        val now = midnight + 30 * MINUTE

        assertEquals(
            listOf(AppTotalRow(TIKTOK, 60 * MINUTE)),
            dao.appTotals(yesterday, midnight, now),
        )
        assertEquals(
            listOf(AppTotalRow(TIKTOK, 30 * MINUTE)),
            dao.appTotals(midnight, tomorrow, now),
        )
    }

    @Test
    fun appTotals_periodBoundaries_areHalfOpen() = runTest {
        // Ends exactly at midnight: belongs only to yesterday.
        insert(INSTAGRAM, midnight - 30 * MINUTE, midnight)
        // Starts exactly at midnight: belongs only to today.
        insert(FACEBOOK, midnight, midnight + 15 * MINUTE)
        // Starts exactly at the end of today: not part of today.
        insert(TIKTOK, tomorrow, tomorrow + 5 * MINUTE)
        val now = tomorrow + 10 * MINUTE

        assertEquals(
            listOf(AppTotalRow(FACEBOOK, 15 * MINUTE)),
            dao.appTotals(midnight, tomorrow, now),
        )
        assertEquals(
            listOf(AppTotalRow(INSTAGRAM, 30 * MINUTE)),
            dao.appTotals(yesterday, midnight, now),
        )
    }

    @Test
    fun appTotals_groupsPerApp() = runTest {
        insert(INSTAGRAM, midnight + 10 * MINUTE, midnight + 20 * MINUTE)
        insert(INSTAGRAM, midnight + 30 * MINUTE, midnight + 45 * MINUTE)
        insert(FACEBOOK, midnight + 50 * MINUTE, midnight + 55 * MINUTE)

        val totals = dao.appTotals(midnight, tomorrow, now = tomorrow)
            .associate { it.packageName to minutes(it.totalMillis) }

        assertEquals(mapOf(INSTAGRAM to 25L, FACEBOOK to 5L), totals)
    }

    @Test
    fun appSessionStats_countsSessionsByStartTime_withAverageAndLongest() = runTest {
        // Started yesterday: not an open of today, even though it ends today.
        insert(INSTAGRAM, midnight - 10 * MINUTE, midnight + 20 * MINUTE)
        insert(INSTAGRAM, midnight + 60 * MINUTE, midnight + 70 * MINUTE)
        insert(INSTAGRAM, midnight + 120 * MINUTE, midnight + 150 * MINUTE)
        // Open session: its duration so far counts.
        insert(INSTAGRAM, midnight + 200 * MINUTE, null)
        val now = midnight + 240 * MINUTE

        val stats = dao.appSessionStats(midnight, tomorrow, now)

        assertEquals(
            listOf(
                AppSessionStatsRow(
                    packageName = INSTAGRAM,
                    opens = 3,
                    totalDurationMillis = (10 + 30 + 40) * MINUTE,
                    longestMillis = 40 * MINUTE,
                ),
            ),
            stats,
        )
    }

    @Test
    fun appSessionStats_emptyPeriod_returnsNoRows() = runTest {
        insert(INSTAGRAM, yesterday + 60 * MINUTE, yesterday + 70 * MINUTE)

        assertEquals(emptyList<AppSessionStatsRow>(), dao.appSessionStats(midnight, tomorrow, tomorrow))
    }

    @Test
    fun sessionsOverlapping_includesMidnightSpanningAndOpenSessions_only() = runTest {
        val spanning = insert(INSTAGRAM, midnight - 10 * MINUTE, midnight + 5 * MINUTE)
        val endsAtMidnight = insert(INSTAGRAM, midnight - 30 * MINUTE, midnight)
        val open = insert(TIKTOK, midnight + 60 * MINUTE, null)
        val tomorrowSession = insert(FACEBOOK, tomorrow, tomorrow + MINUTE)

        val ids = dao.sessionsOverlapping(midnight, tomorrow).map { it.id }

        assertEquals(listOf(spanning, open), ids)
        assertEquals(false, endsAtMidnight in ids)
        assertEquals(false, tomorrowSession in ids)
    }

    @Test
    fun changeToken_changesWhenSessionIsInsertedAndClosed() = runTest {
        dao.observeChangeToken().test {
            val empty = awaitItem()
            val id = insert(INSTAGRAM, midnight, null)
            val inserted = awaitUntilChanged(empty)
            dao.close(id, midnight + MINUTE)
            val closed = awaitUntilChanged(inserted)

            assertNotEquals(empty, inserted)
            assertNotEquals(inserted, closed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun changeToken_changesWhenSwipesAreCounted() = runTest {
        val id = insert(INSTAGRAM, midnight, null)
        dao.observeChangeToken().test {
            val before = awaitItem()
            dao.updateScrollCount(id, 12)
            val after = awaitUntilChanged(before)
            assertEquals(12L, after.scrollSum)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun appScrollStats_sumsSwipesSessionsAndFullLength_forSessionsStartedInRange() = runTest {
        val first = insert(INSTAGRAM, midnight + 60 * MINUTE, midnight + 70 * MINUTE)
        val second = insert(INSTAGRAM, midnight + 120 * MINUTE, null)
        val facebook = insert(FACEBOOK, midnight + 30 * MINUTE, midnight + 35 * MINUTE)
        // Started yesterday: not counted for today, even though it ends today.
        val spanning = insert(INSTAGRAM, midnight - 10 * MINUTE, midnight + 5 * MINUTE)
        dao.updateScrollCount(first, 40)
        dao.updateScrollCount(second, 20)
        dao.updateScrollCount(facebook, 7)
        dao.updateScrollCount(spanning, 99)
        val now = midnight + 125 * MINUTE

        val rows = dao.appScrollStats(midnight, tomorrow, now).sortedBy { it.packageName }

        assertEquals(
            listOf(
                AppScrollStatsRow(FACEBOOK, swipes = 7, sessions = 1, durationMillis = 5 * MINUTE),
                AppScrollStatsRow(INSTAGRAM, swipes = 60, sessions = 2, durationMillis = 15 * MINUTE),
            ),
            rows,
        )
    }

    @Test
    fun updateScrollCount_onlyTouchesThatSession() = runTest {
        val first = insert(INSTAGRAM, midnight, midnight + MINUTE)
        val second = insert(INSTAGRAM, midnight + 2 * MINUTE, null)

        dao.updateScrollCount(second, 5)

        val sessions = dao.sessionsOverlapping(0, tomorrow).associate { it.id to it.scrollCount }
        assertEquals(mapOf(first to 0, second to 5), sessions)
    }

    /** Room may re-emit unchanged results, so skip items until the value changes. */
    private suspend fun app.cash.turbine.ReceiveTurbine<SessionsChangeToken>.awaitUntilChanged(
        previous: SessionsChangeToken,
    ): SessionsChangeToken {
        var item = awaitItem()
        while (item == previous) item = awaitItem()
        return item
    }

    private companion object {
        const val MINUTE = 60_000L
        const val INSTAGRAM = "com.instagram.android"
        const val TIKTOK = "com.zhiliaoapp.musically"
        const val FACEBOOK = "com.facebook.katana"
    }
}

package com.unscroll.app.domain.goals

import com.unscroll.app.domain.insights.UsageMath
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.session.Session
import com.unscroll.app.domain.tracking.TrackedApps.INSTAGRAM
import com.unscroll.app.testing.FakeUsageDataSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetStreakHistoryUseCaseTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private var nextId = 1L
    private fun session(start: Long, minutes: Int) =
        Session(nextId++, INSTAGRAM, start, start + minutes * 60_000L, scrollCount = 0)

    private val now = at(10, 12)

    @Test
    fun noSessions_noStreak() = runTest {
        assertEquals(StreakHistory(0, 0), GetStreakHistoryUseCase(FakeUsageDataSource())(60, now, ZoneId.of("UTC")))
    }

    @Test
    fun daysWithoutUsage_afterTrackingStarted_countAsWithinTheGoal() = runTest {
        val source = FakeUsageDataSource(
            mutableListOf(
                session(at(5, 10), minutes = 90), // Over the goal: first tracked day.
                session(at(6, 10), minutes = 20),
                // 7th: no usage at all, which is within the goal.
                session(at(8, 10), minutes = 50),
                session(at(9, 10), minutes = 59),
                session(at(10, 9), minutes = 200), // Today: not part of the history.
            ),
        )

        val history = GetStreakHistoryUseCase(source)(goalMinutes = 60, now = now, zone = zone)

        assertEquals(StreakHistory(endingYesterday = 4, bestCompleted = 4), history)
    }

    @Test
    fun aSessionAcrossMidnight_isSplitBetweenTheDays() = runTest {
        // 23:00 on the 8th to 01:00 on the 9th: 60 minutes each side.
        val source = FakeUsageDataSource(mutableListOf(session(at(8, 23), minutes = 120)))

        assertEquals(StreakHistory(2, 2), GetStreakHistoryUseCase(source)(goalMinutes = 60, now = now, zone = zone))
        assertEquals(StreakHistory(0, 0), GetStreakHistoryUseCase(source)(goalMinutes = 59, now = now, zone = zone))
    }

    @Test
    fun trackingStartedToday_noHistoryYet() = runTest {
        val source = FakeUsageDataSource(mutableListOf(session(at(10, 9), minutes = 10)))
        assertEquals(StreakHistory(0, 0), GetStreakHistoryUseCase(source)(goalMinutes = 60, now = now, zone = zone))
    }

    @Test
    fun totalsByDay_matchesDailyTotals() {
        val sessions = listOf(session(at(8, 23), minutes = 120), session(at(9, 12), minutes = 15))
        val first = LocalDate.of(2026, 10, 8)
        val range = TimeRange(at(8, 0), at(10, 0))
        val byDay = UsageMath.totalsByDay(sessions, range, zone, now)
        val slow = UsageMath.dailyTotals(sessions, first, 2, zone, now).associate { it.date to it.totalMillis }
        assertEquals(slow, byDay)
    }
}

package com.unscroll.app.domain.goals

import com.unscroll.app.domain.insights.GetWeeklyReportUseCase
import com.unscroll.app.domain.insights.TrendDirection
import com.unscroll.app.domain.session.Session
import com.unscroll.app.domain.tracking.DefaultTrackedApps.FACEBOOK
import com.unscroll.app.domain.tracking.DefaultTrackedApps.INSTAGRAM
import com.unscroll.app.testing.FakeUsageDataSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetWeeklyReportUseCaseTest {

    private val zone = ZoneId.of("Europe/Berlin")

    // 2026-10-07 is a Wednesday. Last complete week (Monday start): 28 Sep to 4 Oct.
    private fun at(month: Int, day: Int, hour: Int) =
        LocalDateTime.of(2026, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val now = at(10, 7, 12)
    private var nextId = 1L
    private fun session(pkg: String, start: Long, minutes: Int) =
        Session(nextId++, pkg, start, start + minutes * 60_000L, scrollCount = 0)

    @Test
    fun nothingLastWeek_noReport() = runTest {
        val source = FakeUsageDataSource(mutableListOf(session(INSTAGRAM, at(10, 6, 9), 30)))
        assertNull(GetWeeklyReportUseCase(source)(now, zone, DayOfWeek.MONDAY, goalMinutes = null))
    }

    @Test
    fun reportsLastCompleteWeek_againstTheWeekBefore() = runTest {
        val source = FakeUsageDataSource(
            mutableListOf(
                session(INSTAGRAM, at(9, 22, 10), 140), // The week before: 140 min.
                session(INSTAGRAM, at(9, 28, 10), 30), // Monday
                session(FACEBOOK, at(9, 28, 20), 10),
                session(INSTAGRAM, at(10, 1, 10), 60), // Thursday: busiest
                session(INSTAGRAM, at(10, 4, 10), 40), // Sunday
                session(INSTAGRAM, at(10, 6, 10), 500), // This week: not in the report.
            ),
        )

        val report = GetWeeklyReportUseCase(source)(now, zone, DayOfWeek.MONDAY, goalMinutes = 45)!!

        assertEquals(LocalDate.of(2026, 9, 28), report.weekStart)
        assertEquals(140 * 60_000L, report.totalMillis)
        assertEquals(TrendDirection.FLAT, report.trend.direction)
        assertEquals(20 * 60_000L, report.dailyAverageMillis)
        assertEquals(LocalDate.of(2026, 10, 1), report.busiestDay.date)
        assertEquals(INSTAGRAM, report.topApp)
        assertEquals(130 * 60_000L, report.topAppMillis)
        assertEquals(4, report.opens)
        // Over 45 min only on Thursday; all 7 days are after tracking started.
        assertEquals(6, report.goalDaysMet)
        assertEquals(7, report.goalDaysCounted)
    }

    @Test
    fun daysBeforeTrackingStarted_dontCountForTheGoal_andSundayStartWeeksWork() = runTest {
        // Week starting Sunday: last complete week is 27 Sep to 3 Oct. Tracking began Thursday 1 Oct.
        val source = FakeUsageDataSource(mutableListOf(session(INSTAGRAM, at(10, 1, 10), 30)))

        val report = GetWeeklyReportUseCase(source)(now, zone, DayOfWeek.SUNDAY, goalMinutes = 60)!!

        assertEquals(LocalDate.of(2026, 9, 27), report.weekStart)
        assertEquals(3, report.goalDaysCounted)
        assertEquals(3, report.goalDaysMet)
        assertEquals(TrendDirection.UP, report.trend.direction)
    }

    @Test
    fun noGoal_noGoalDays() = runTest {
        val source = FakeUsageDataSource(mutableListOf(session(INSTAGRAM, at(9, 30, 10), 30)))
        assertNull(GetWeeklyReportUseCase(source)(now, zone, DayOfWeek.MONDAY, goalMinutes = null)!!.goalDaysMet)
    }
}

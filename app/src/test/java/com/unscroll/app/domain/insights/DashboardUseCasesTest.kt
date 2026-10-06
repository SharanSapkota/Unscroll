package com.unscroll.app.domain.insights

import com.unscroll.app.testing.FakeUsageDataSource
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardUseCasesTest {

    private val instagram = "com.instagram.android"
    private val tiktok = "com.zhiliaoapp.musically"
    private val now = at("2026-10-06T20:00:00")
    private val source = FakeUsageDataSource()

    @Test
    fun periodUsage_today_perAppAndCombined() = runTest {
        source.sessions += listOf(
            // Crosses midnight: only the 15 minutes after midnight count for today, and it
            // counts as an open of yesterday.
            session("2026-10-05T23:50:00", "2026-10-06T00:15:00", instagram),
            session("2026-10-06T09:00:00", "2026-10-06T09:10:00", instagram),
            session("2026-10-06T12:00:00", "2026-10-06T12:30:00", instagram),
            session("2026-10-06T13:00:00", "2026-10-06T13:20:00", tiktok),
            // Still open: runs until now.
            session("2026-10-06T19:50:00", null, tiktok),
        )

        val usage = GetPeriodUsageUseCase(source)(UsagePeriod.TODAY, now, BERLIN)

        assertEquals(listOf(instagram, tiktok), usage.apps.map { it.packageName })
        assertEquals(
            UsageSummary(
                totalMillis = 55 * MINUTE,
                opens = 2,
                averageSessionMillis = 20 * MINUTE,
                longestSessionMillis = 30 * MINUTE,
            ),
            usage.apps[0].usage,
        )
        assertEquals(
            UsageSummary(30 * MINUTE, opens = 2, averageSessionMillis = 15 * MINUTE, longestSessionMillis = 20 * MINUTE),
            usage.apps[1].usage,
        )
        assertEquals(
            UsageSummary(85 * MINUTE, opens = 4, averageSessionMillis = 70 * MINUTE / 4, longestSessionMillis = 30 * MINUTE),
            usage.combined,
        )
        assertEquals(15 * MINUTE, usage.hourly[0])
        assertEquals(10 * MINUTE, usage.hourly[19])
        assertEquals(85 * MINUTE, usage.hourly.sum())
    }

    @Test
    fun periodUsage_week_includesYesterdaysOpen() = runTest {
        source.sessions += session("2026-10-05T23:50:00", "2026-10-06T00:15:00", instagram)

        val usage = GetPeriodUsageUseCase(source)(UsagePeriod.WEEK, now, BERLIN)

        assertEquals(UsageSummary(25 * MINUTE, 1, 25 * MINUTE, 25 * MINUTE), usage.combined)
    }

    @Test
    fun periodUsage_noData_isEmpty() = runTest {
        val usage = GetPeriodUsageUseCase(source)(UsagePeriod.MONTH, now, BERLIN)

        assertEquals(UsageSummary.EMPTY, usage.combined)
        assertEquals(emptyList<AppUsage>(), usage.apps)
        assertEquals(List(24) { 0L }, usage.hourly)
    }

    @Test
    fun todayTrend_comparesWithYesterdayUpToSameTime() = runTest {
        source.sessions += listOf(
            session("2026-10-06T10:00:00", "2026-10-06T11:00:00"),
            // Yesterday: 30 min before 20:00 counts, the late-evening hour does not.
            session("2026-10-05T09:00:00", "2026-10-05T09:30:00"),
            session("2026-10-05T21:00:00", "2026-10-05T22:00:00"),
        )

        val trend = GetTodayTrendUseCase(source)(now, BERLIN)

        assertEquals(Trend(currentMillis = 60 * MINUTE, previousMillis = 30 * MINUTE), trend)
        assertEquals(TrendDirection.UP, trend.direction)
        assertEquals(100, trend.percentChange)
    }

    @Test
    fun weekComparison_splitsIntoTwoWeeksOfDailyTotals() = runTest {
        source.sessions += listOf(
            session("2026-10-06T10:00:00", "2026-10-06T10:20:00"), // today
            session("2026-09-30T23:50:00", "2026-10-01T00:10:00"), // splits over the first two days of this week
            session("2026-09-23T12:00:00", "2026-09-23T13:00:00"), // first day of last week
            session("2026-09-22T12:00:00", "2026-09-22T13:00:00"), // too old
        )

        val comparison = GetWeekComparisonUseCase(source)(now, BERLIN)

        assertEquals(7, comparison.thisWeek.size)
        assertEquals(7, comparison.lastWeek.size)
        assertEquals(LocalDate.of(2026, 9, 30), comparison.thisWeek.first().date)
        assertEquals(LocalDate.of(2026, 10, 6), comparison.thisWeek.last().date)
        assertEquals(LocalDate.of(2026, 9, 23), comparison.lastWeek.first().date)
        assertEquals(20 * MINUTE, comparison.thisWeek.last().totalMillis)
        assertEquals(10 * MINUTE, comparison.thisWeek[0].totalMillis)
        assertEquals(10 * MINUTE, comparison.thisWeek[1].totalMillis)
        assertEquals(60 * MINUTE, comparison.lastWeek.first().totalMillis)
        assertEquals(Trend(currentMillis = 40 * MINUTE, previousMillis = 60 * MINUTE), comparison.trend)
    }
}

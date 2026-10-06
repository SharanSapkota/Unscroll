package com.unscroll.app.domain.insights

import com.unscroll.app.domain.session.Session
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageMathTest {

    private val day = TimeRange(at("2026-10-06T00:00:00"), at("2026-10-07T00:00:00"))
    private val now = at("2026-10-06T20:00:00")

    @Test
    fun overlap_clipsToRange() {
        assertEquals(30 * MINUTE, UsageMath.overlap(session("2026-10-05T23:30:00", "2026-10-06T00:30:00"), day, now))
        assertEquals(0L, UsageMath.overlap(session("2026-10-05T10:00:00", "2026-10-05T11:00:00"), day, now))
    }

    @Test
    fun overlap_openSessionRunsUntilNow() {
        assertEquals(15 * MINUTE, UsageMath.overlap(session("2026-10-06T19:45:00", null), day, now))
    }

    @Test
    fun dailyTotals_splitsSessionAcrossMidnight() {
        val sessions = listOf(
            session("2026-10-05T23:40:00", "2026-10-06T00:25:00"),
            session("2026-10-06T12:00:00", "2026-10-06T12:10:00"),
        )

        val totals = UsageMath.dailyTotals(sessions, LocalDate.of(2026, 10, 5), 2, BERLIN, now)

        assertEquals(
            listOf(
                DayUsage(LocalDate.of(2026, 10, 5), 20 * MINUTE),
                DayUsage(LocalDate.of(2026, 10, 6), 35 * MINUTE),
            ),
            totals,
        )
    }

    @Test
    fun dailyTotals_includesOpenSessionUntilNow() {
        val totals = UsageMath.dailyTotals(
            listOf(session("2026-10-06T19:00:00", null)),
            LocalDate.of(2026, 10, 6),
            1,
            BERLIN,
            now,
        )
        assertEquals(HOUR, totals.single().totalMillis)
    }

    @Test
    fun hourlyTotals_splitsAcrossHourBoundaries() {
        val hourly = UsageMath.hourlyTotals(
            listOf(session("2026-10-06T08:50:00", "2026-10-06T10:05:00")),
            day,
            BERLIN,
            now,
        )

        assertEquals(24, hourly.size)
        assertEquals(10 * MINUTE, hourly[8])
        assertEquals(60 * MINUTE, hourly[9])
        assertEquals(5 * MINUTE, hourly[10])
        assertEquals(75 * MINUTE, hourly.sum())
    }

    @Test
    fun hourlyTotals_onlyCountsPartInsideRange_acrossMidnight() {
        val hourly = UsageMath.hourlyTotals(
            listOf(session("2026-10-05T23:45:00", "2026-10-06T00:20:00")),
            day,
            BERLIN,
            now,
        )
        assertEquals(20 * MINUTE, hourly[0])
        assertEquals(0L, hourly[23])
    }

    @Test
    fun hourlyTotals_openSessionUntilNow() {
        val hourly = UsageMath.hourlyTotals(listOf(session("2026-10-06T19:30:00", null)), day, BERLIN, now)
        assertEquals(30 * MINUTE, hourly[19])
        assertEquals(0L, hourly[20])
    }

    @Test
    fun hourlyTotals_handlesDstRepeatedHour() {
        // On 2026-10-25 Berlin repeats 02:00-03:00. A session across it is 2 hours of real time.
        val dstDay = TimeRange(at("2026-10-25T00:00:00"), at("2026-10-26T00:00:00"))
        val start = at("2026-10-25T01:30:00")
        val hourly = UsageMath.hourlyTotals(
            listOf(Session(1, "a", start, start + 2 * HOUR, 0)),
            dstDay,
            BERLIN,
            start + 3 * HOUR,
        )
        // 01:30-02:00, then 02:00-03:00 summer time, then 02:00-02:30 winter time.
        assertEquals(2 * HOUR, hourly.sum())
        assertEquals(30 * MINUTE, hourly[1])
        assertEquals(90 * MINUTE, hourly[2])
    }
}
